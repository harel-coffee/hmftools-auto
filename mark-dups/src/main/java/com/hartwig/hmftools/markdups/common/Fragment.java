package com.hartwig.hmftools.markdups.common;

import static com.hartwig.hmftools.markdups.common.FragmentCoordinates.NO_COORDS;
import static com.hartwig.hmftools.markdups.common.FragmentStatus.SUPPLEMENTARY;
import static com.hartwig.hmftools.markdups.common.FragmentStatus.UNSET;
import static com.hartwig.hmftools.markdups.common.FragmentUtils.formChromosomePartition;
import static com.hartwig.hmftools.markdups.common.FragmentUtils.getFragmentCoordinates;
import static com.hartwig.hmftools.common.samtools.SamRecordUtils.SUPPLEMENTARY_ATTRIBUTE;

import java.util.List;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.genome.chromosome.HumanChromosome;
import com.hartwig.hmftools.common.samtools.SupplementaryReadData;

import htsjdk.samtools.SAMRecord;

public class Fragment
{
    private FragmentStatus mStatus;
    private final boolean mUnpaired;
    private boolean mAllReadsPresent;
    private boolean mAllPrimaryReadsPresent;
    private final List<SAMRecord> mReads; // consider making an array of 4 (or less for BNDs)
    private FragmentCoordinates mCoordinates;

    private boolean mReadsWritten;

    // duplicate read info
    private double mAverageBaseQual;
    private String mCandidateDupKey;
    private String mUmiId;

    public Fragment(final SAMRecord read)
    {
        mUnpaired = !read.getReadPairedFlag();

        mReads = Lists.newArrayListWithCapacity(2);
        mReads.add(read);

        if(!read.getSupplementaryAlignmentFlag())
        {
            mStatus = UNSET;

            if(mUnpaired)
            {
                mAllPrimaryReadsPresent = true;
                mAllReadsPresent = !read.hasAttribute(SUPPLEMENTARY_ATTRIBUTE);
            }
            else
            {
                mAllPrimaryReadsPresent = false;
                mAllReadsPresent = false;
            }
        }
        else
        {
            mStatus = SUPPLEMENTARY;
            mAllPrimaryReadsPresent = false;
            mAllReadsPresent = false;
        }

        mCoordinates = NO_COORDS; // unset for non primary reads

        mAverageBaseQual = 0;
        mReadsWritten = false;
        mCandidateDupKey = null;
        mUmiId = null;
    }

    public final String id() { return mReads.get(0).getReadName(); }
    public List<SAMRecord> reads() { return mReads; }

    public FragmentStatus status() { return mStatus; }
    public void setStatus(final FragmentStatus status) { mStatus = status; }

    public boolean unpaired() { return mUnpaired; }
    public boolean allReadsPresent() { return mAllReadsPresent; }
    public boolean primaryReadsPresent() { return mAllPrimaryReadsPresent; }

    public FragmentCoordinates coordinates() { return mCoordinates; }
    public int initialPosition() { return mCoordinates.InitialPosition; }

    public void intialiseCoordinates(boolean useMateCigar) { mCoordinates = getFragmentCoordinates(mReads, useMateCigar); }
    public void intialiseCoordinates() { intialiseCoordinates(true); }

    public double averageBaseQual() { return mAverageBaseQual; }
    public void setAverageBaseQual(double qual) { mAverageBaseQual = qual; }

    public String candidateDupKey() { return mCandidateDupKey; }
    public void setCandidateDupKey(final String key) { mCandidateDupKey = key; }

    public String umiId() { return mUmiId; }
    public void setUmiId(final String umiId) { mUmiId = umiId; }

    public boolean readsWritten() { return mReadsWritten; }
    public void setReadWritten() { mReadsWritten = true; }

    public void addRead(final SAMRecord read)
    {
        mReads.add(read);

        if(!read.getSupplementaryAlignmentFlag())
        {
            mAllPrimaryReadsPresent = true;

            if(mCoordinates.Incomplete)
                mCoordinates = getFragmentCoordinates(mReads, false);
        }

        checkComplete();
    }

    public static String getBasePartition(final SAMRecord read, final int partitionSize)
    {
        if(read.getSupplementaryAlignmentFlag())
        {
            SupplementaryReadData suppData = SupplementaryReadData.from(read);
            if(suppData != null)
            {
                if(!HumanChromosome.contains(suppData.Chromosome))
                    return null;

                return formChromosomePartition(suppData.Chromosome, suppData.Position, partitionSize);
            }
        }

        if(!read.getReadPairedFlag())
            return formChromosomePartition(read.getReferenceName(), read.getAlignmentStart(), partitionSize);

        if(!HumanChromosome.contains(read.getMateReferenceName()))
            return null;

        // take the lower of the read and its mate
        boolean readLowerPos;
        if(read.getReferenceIndex() == read.getMateReferenceIndex())
        {
            readLowerPos = read.getAlignmentStart() < read.getMateAlignmentStart();
        }
        else
        {
            readLowerPos = read.getReferenceIndex() < read.getMateReferenceIndex();
        }

        return readLowerPos ?
                formChromosomePartition(read.getReferenceName(), read.getAlignmentStart(), partitionSize)
                : formChromosomePartition(read.getMateReferenceName(), read.getMateAlignmentStart(), partitionSize);
    }

    private void checkComplete()
    {
        if(mAllReadsPresent)
            return;

        int suppCount = 0;
        int nonSuppCount = 0;
        int expectedSuppCount = 0;
        int expectedNonSuppCount = 1;

        for(SAMRecord read : mReads)
        {
            if(read.getReadPairedFlag() && !read.getMateUnmappedFlag())
            {
                expectedNonSuppCount = 2;
            }

            if(read.getSupplementaryAlignmentFlag())
            {
                ++suppCount;
            }
            else
            {
                ++nonSuppCount;
            }

            if(read.hasAttribute(SUPPLEMENTARY_ATTRIBUTE))
            {
                if(!read.getSupplementaryAlignmentFlag())
                {
                    ++expectedSuppCount;
                }
            }
        }

        mAllPrimaryReadsPresent = expectedNonSuppCount == nonSuppCount;
        mAllReadsPresent = (expectedNonSuppCount == nonSuppCount) && (expectedSuppCount == suppCount);
    }

    public int readCount() { return mReads.size(); }

    public String toString()
    {
        return String.format("id(%s) reads(%d) status(%s) coords(%s) present(%s)", //  mate(%s:%d)
                id(), mReads.size(), mStatus, mCoordinates.Key,
                mAllReadsPresent ? "all" : (mAllPrimaryReadsPresent ? "primary" : "incomplete"));
        // mReads.get(0).getMateReferenceName(), mReads.get(0).getMateAlignmentStart()
    }
}
