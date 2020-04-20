package com.hartwig.hmftools.isofox.fusion;

import static com.hartwig.hmftools.common.utils.sv.StartEndIterator.SE_END;
import static com.hartwig.hmftools.common.utils.sv.StartEndIterator.SE_PAIR;
import static com.hartwig.hmftools.common.utils.sv.StartEndIterator.SE_START;
import static com.hartwig.hmftools.common.utils.sv.StartEndIterator.switchIndex;
import static com.hartwig.hmftools.isofox.common.RegionMatchType.EXON_BOUNDARY;
import static com.hartwig.hmftools.isofox.common.RegionMatchType.EXON_MATCH;
import static com.hartwig.hmftools.isofox.common.RnaUtils.impliedSvType;
import static com.hartwig.hmftools.isofox.common.RnaUtils.positionWithin;
import static com.hartwig.hmftools.isofox.fusion.FusionFragmentType.DISCORDANT;
import static com.hartwig.hmftools.isofox.fusion.FusionFragmentType.SPLICED_BOTH;
import static com.hartwig.hmftools.isofox.fusion.FusionFragmentType.SPLICED_ONE;
import static com.hartwig.hmftools.isofox.fusion.FusionFragmentType.UNKNOWN;
import static com.hartwig.hmftools.isofox.fusion.FusionReadData.formChromosomePair;
import static com.hartwig.hmftools.isofox.fusion.FusionReadData.lowerChromosome;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.hartwig.hmftools.common.variant.structural.StructuralVariantType;
import com.hartwig.hmftools.isofox.common.ReadRecord;
import com.hartwig.hmftools.isofox.common.RegionMatchType;
import com.hartwig.hmftools.isofox.common.TransExonRef;

import htsjdk.samtools.CigarOperator;

public class FusionFragment
{
    private final List<ReadRecord> mReads;

    private final int[] mGeneCollections;
    private final String[] mChromosomes;
    private final long[] mSjPositions;
    private final byte[] mSjOrientations;
    private final boolean[] mSjValid;
    private FusionFragmentType mType;

    public FusionFragment(final List<ReadRecord> reads)
    {
        mReads = reads;

        mGeneCollections = new int[SE_PAIR];
        mSjPositions = new long[] {-1, -1};
        mChromosomes = new String[]{"", ""};
        mSjOrientations = new byte[]{0, 0};
        mSjValid = new boolean[]{false, false};

        // divide reads into the 2 gene collections
        final List<String> chrGeneCollections = Lists.newArrayListWithCapacity(2);
        final List<String> chromosomes = Lists.newArrayListWithCapacity(2);
        final List<Long> positions = Lists.newArrayListWithCapacity(2);
        final Map<String,List<ReadRecord>> readGroups = Maps.newHashMap();

        for(final ReadRecord read : reads)
        {
            final String chrGeneId = read.chromosomeGeneId();

            List<ReadRecord> readGroup = readGroups.get(chrGeneId);

            if(readGroup == null)
            {
                readGroups.put(chrGeneId, Lists.newArrayList(read));

                chrGeneCollections.add(chrGeneId);
                chromosomes.add(read.Chromosome);
                positions.add(read.PosStart); // no overlap in gene collections so doesn't matter which position is used
            }
            else
            {
                readGroup.add(read);
            }
        }

        // first determine which is the start and end chromosome & position as for SVs
        int lowerIndex;

        if(chromosomes.get(0).equals(chromosomes.get(1)))
            lowerIndex = positions.get(0) < positions.get(1) ? 0 : 1;
        else
            lowerIndex = lowerChromosome(chromosomes.get(0), chromosomes.get(1)) ? 0 : 1;

        for(int se = SE_START; se <= SE_END; ++se)
        {
            int index = se == SE_START ? lowerIndex : switchIndex(lowerIndex);
            final String chrGeneId = chrGeneCollections.get(index);

            // find the outermost soft-clipped read to use for the splice junction position
            long sjPosition = 0;
            byte sjOrientation = 0;
            int maxSoftClipping = 0;

            final List<ReadRecord> readGroup = readGroups.get(chrGeneId);
            for(ReadRecord read : readGroup)
            {
                if(!read.Cigar.containsOperator(CigarOperator.S))
                    continue;

                int scLeft = read.Cigar.isLeftClipped() ? read.Cigar.getFirstCigarElement().getLength() : 0;
                int scRight = read.Cigar.isRightClipped() ? read.Cigar.getLastCigarElement().getLength() : 0;

                boolean useLeft = false;

                if(scLeft > 0 && scRight > 0)
                {
                    // should be very unlikely since implies a very short exon and even then would expect it to be mapped
                    if(scLeft >= scRight && scLeft > maxSoftClipping)
                    {
                        maxSoftClipping = scLeft;
                        useLeft = true;
                    }
                    else if(scRight > scLeft && scRight > maxSoftClipping)
                    {
                        maxSoftClipping = scRight;
                        useLeft = false;
                    }
                    else
                    {
                        continue;
                    }
                }
                else if(scLeft > maxSoftClipping)
                {
                    maxSoftClipping = scLeft;
                    useLeft = true;
                }
                else if(scRight > maxSoftClipping)
                {
                    maxSoftClipping = scRight;
                    useLeft = false;
                }
                else
                {
                    continue;
                }

                if(useLeft)
                {
                    sjPosition = read.getCoordsBoundary(true);
                    sjOrientation = -1;
                }
                else
                {
                    sjPosition = read.getCoordsBoundary(false);
                    sjOrientation = 1;
                }
            }

            if(maxSoftClipping > 0)
            {
                mChromosomes[se] = chromosomes.get(index);
                mSjPositions[se] = sjPosition;
                mSjOrientations[se] = sjOrientation;
                mSjValid[se] = true;
                mGeneCollections[se] = readGroup.get(0).getGeneCollecton();
            }
        }

        mType = UNKNOWN;

        if(mSjValid[SE_START] && mSjValid[SE_END])
        {
            mType = SPLICED_BOTH;
        }
        else if(mSjValid[SE_START] || mSjValid[SE_END])
        {
            mType = SPLICED_ONE;
        }
        else
        {
            mType = DISCORDANT;
        }
    }

    public final List<ReadRecord> getReads() { return mReads; }
    public FusionFragmentType type() { return mType; }
    public final String[] chromosomes() { return mChromosomes; }

    public final long[] splicePositions() { return mSjPositions; }
    public final byte[] spliceOrientations() { return mSjOrientations; }
    public boolean hasValidSpliceData() { return mSjValid[SE_START] && mSjValid[SE_END]; }

    public String chrPair() { return formChromosomePair(mChromosomes[SE_START], mChromosomes[SE_END]); }

    public static boolean validPositions(final long[] position) { return position[SE_START] > 0 && position[SE_END] > 0; }

    public StructuralVariantType getImpliedSvType()
    {
        return impliedSvType(mChromosomes, mSjOrientations);
    }

    public void populateGeneCandidates(final List<List<String>> spliceGeneIds)
    {
        if(spliceGeneIds.size() != 2)
            return;

        // each fragment supporting the splice junction will have the same set of candidate genes
        for(int se = SE_START; se <= SE_END; ++se)
        {
            spliceGeneIds.get(se).clear();

            for(final ReadRecord read : mReads)
            {
                if(!read.Chromosome.equals(mChromosomes[se]))
                    continue;

                for(Map.Entry<RegionMatchType,List<TransExonRef>> entry : read.getTransExonRefs().entrySet())
                {
                    if(entry.getKey() != EXON_BOUNDARY && entry.getKey() != EXON_MATCH)
                        continue;

                    if(read.getCoordsBoundary(true) == mSjPositions[se] || read.getCoordsBoundary(false) == mSjPositions[se])
                    {
                        for(TransExonRef transData : entry.getValue())
                        {
                            if(!spliceGeneIds.get(se).contains(transData.GeneId))
                                spliceGeneIds.get(se).add(transData.GeneId);
                        }
                    }
                }
            }
        }
    }
}
