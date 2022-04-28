package com.hartwig.hmftools.isofox.fusion;

import static com.hartwig.hmftools.common.fusion.KnownFusionType.KNOWN_PAIR;
import static com.hartwig.hmftools.common.fusion.KnownFusionType.PROMISCUOUS_3;
import static com.hartwig.hmftools.common.fusion.KnownFusionType.PROMISCUOUS_5;
import static com.hartwig.hmftools.common.utils.sv.BaseRegion.positionsOverlap;
import static com.hartwig.hmftools.common.utils.sv.StartEndIterator.SE_END;
import static com.hartwig.hmftools.common.utils.sv.StartEndIterator.SE_PAIR;
import static com.hartwig.hmftools.common.utils.sv.StartEndIterator.SE_START;
import static com.hartwig.hmftools.common.utils.sv.BaseRegion.positionWithin;
import static com.hartwig.hmftools.common.utils.sv.SvCommonUtils.NEG_ORIENT;
import static com.hartwig.hmftools.common.utils.sv.SvCommonUtils.POS_ORIENT;
import static com.hartwig.hmftools.isofox.IsofoxConfig.ISF_LOGGER;
import static com.hartwig.hmftools.isofox.IsofoxConstants.MAX_NOVEL_SJ_DISTANCE;
import static com.hartwig.hmftools.isofox.IsofoxFunction.FUSIONS;
import static com.hartwig.hmftools.isofox.IsofoxFunction.ALT_SPLICE_JUNCTIONS;
import static com.hartwig.hmftools.isofox.common.FragmentType.CHIMERIC;
import static com.hartwig.hmftools.isofox.common.FragmentType.DUPLICATE;
import static com.hartwig.hmftools.isofox.common.FragmentType.TOTAL;
import static com.hartwig.hmftools.isofox.common.RegionMatchType.EXON_BOUNDARY;
import static com.hartwig.hmftools.isofox.fusion.FusionConstants.REALIGN_MIN_SOFT_CLIP_BASE_LENGTH;
import static com.hartwig.hmftools.isofox.fusion.FusionConstants.SOFT_CLIP_JUNC_BUFFER;
import static com.hartwig.hmftools.isofox.fusion.FusionReadData.softClippedReadSupportsJunction;
import static com.hartwig.hmftools.isofox.fusion.FusionUtils.addChimericReads;
import static com.hartwig.hmftools.isofox.fusion.FusionUtils.findSplitRead;
import static com.hartwig.hmftools.isofox.fusion.FusionUtils.findSplitReadJunction;
import static com.hartwig.hmftools.isofox.fusion.FusionUtils.hasRealignableSoftClip;
import static com.hartwig.hmftools.isofox.fusion.FusionUtils.isInversion;
import static com.hartwig.hmftools.isofox.fusion.FusionUtils.setHasMultipleKnownSpliceGenes;
import static com.hartwig.hmftools.isofox.fusion.LocalJunctionData.setMaxSplitMappedLength;
import static com.hartwig.hmftools.isofox.fusion.ReadGroup.hasSuppAlignment;

import static htsjdk.samtools.CigarOperator.M;
import static htsjdk.samtools.CigarOperator.N;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.hartwig.hmftools.common.ensemblcache.EnsemblDataCache;
import com.hartwig.hmftools.common.fusion.KnownFusionData;
import com.hartwig.hmftools.common.gene.GeneData;
import com.hartwig.hmftools.isofox.IsofoxConfig;
import com.hartwig.hmftools.isofox.common.BaseDepth;
import com.hartwig.hmftools.isofox.common.CommonUtils;
import com.hartwig.hmftools.isofox.common.FragmentTracker;
import com.hartwig.hmftools.isofox.common.GeneCollection;
import com.hartwig.hmftools.isofox.common.ReadRecord;

import htsjdk.samtools.Cigar;

public class ChimericReadTracker
{
    private final IsofoxConfig mConfig;
    private final boolean mRunFusions;
    private final boolean mEnabled; // required for alt splice junctions

    private final List<String[]> mKnownPairGeneIds;

    private GeneCollection mGeneCollection; // the current collection being processed
    private final Map<String,ReadGroup> mChimericReadMap;
    private final List<ReadGroup> mLocalCompleteGroups; // 2-read same-gene-collection groups with a split junction

    // junction position from fusion junction candidate reads are cached to identify candidate realignable reads
    private final Set<Integer> mJunctionPositions;
    private final JunctionRacGroups mJunctionRacGroups; // keyed by orientation then junction position

    private final List<List<ReadRecord>> mLocalChimericReads; // fragments to re-evaluate as alternate splice sites
    private final List<ReadGroup> mCandidateRealignedGroups;

    // map of candidate junction groups with supp data
    private final Map<Integer,List<SupplementaryJunctionData>> mSupplementaryJunctions;
    private final Set<String> mKnownGeneIds;
    private final Map<String,Set<String>> mHardFilteredReadGroups;

    // to avoid double-processing reads falling after a gene collection
    private final Map<String,List<ReadRecord>> mPostGeneReadMap;
    private final Map<String,List<ReadRecord>> mPreviousPostGeneReadMap;
    private final ChimericStats mChimericStats;

    public ChimericReadTracker(final IsofoxConfig config)
    {
        mConfig = config;
        mRunFusions = mConfig.Functions.contains(FUSIONS);
        mEnabled = mRunFusions || mConfig.Functions.contains(ALT_SPLICE_JUNCTIONS);

        mKnownPairGeneIds = Lists.newArrayList();
        mKnownGeneIds = Sets.newHashSet();
        mChimericStats = new ChimericStats();
        mChimericReadMap = Maps.newHashMap();
        mJunctionPositions = Sets.newHashSet();
        mJunctionRacGroups = new JunctionRacGroups();
        mLocalChimericReads = Lists.newArrayList();
        mLocalCompleteGroups = Lists.newArrayList();
        mCandidateRealignedGroups = Lists.newArrayList();
        mPostGeneReadMap = Maps.newHashMap();
        mPreviousPostGeneReadMap = Maps.newHashMap();
        mSupplementaryJunctions = Maps.newHashMap();
        mHardFilteredReadGroups = Maps.newHashMap();
        mGeneCollection = null;
    }

    public boolean enabled() { return mEnabled; }

    public Map<String,ReadGroup> getReadMap() { return mChimericReadMap; }
    public Set<Integer> getJunctionPositions() { return mJunctionPositions; }
    public JunctionRacGroups getJunctionRacGroups() { return mJunctionRacGroups; }
    public List<List<ReadRecord>> getLocalChimericReads() { return mLocalChimericReads; }
    public Map<String,Set<String>> getHardFilteredReadGroups() { return mHardFilteredReadGroups; }
    public ChimericStats getStats() { return mChimericStats; }

    public boolean isChimeric(final ReadRecord read1, final ReadRecord read2, boolean isDuplicate, boolean isMultiMapped)
    {
        if(read1.isChimeric() || read2.isChimeric() || !read1.withinGeneCollection() || !read2.withinGeneCollection())
            return true;

        if(!isDuplicate && !isMultiMapped && enabled() && (read1.containsSplit() || read2.containsSplit()))
        {
            return setHasMultipleKnownSpliceGenes(Lists.newArrayList(read1, read2), mKnownPairGeneIds);
        }

        return false;
    }

    public void registerKnownFusionPairs(final EnsemblDataCache geneTransCache)
    {
        for(final KnownFusionData knownPair : mConfig.Fusions.KnownFusions.getDataByType(KNOWN_PAIR))
        {
            final GeneData upGene = geneTransCache.getGeneDataByName(knownPair.FiveGene);
            final GeneData downGene = geneTransCache.getGeneDataByName(knownPair.ThreeGene);

            if(upGene != null && downGene != null)
                mKnownPairGeneIds.add(new String[] { upGene.GeneId, downGene.GeneId });

            mKnownGeneIds.add(upGene.GeneId);
            mKnownGeneIds.add(downGene.GeneId);
        }

        mConfig.Fusions.KnownFusions.getDataByType(PROMISCUOUS_5)
                .forEach(x -> mKnownGeneIds.add(geneTransCache.getGeneDataByName(x.FiveGene).GeneId));

        mConfig.Fusions.KnownFusions.getDataByType(PROMISCUOUS_3)
                .forEach(x -> mKnownGeneIds.add(geneTransCache.getGeneDataByName(x.ThreeGene).GeneId));
    }

    public void initialise(final GeneCollection geneCollection)
    {
        mGeneCollection = geneCollection;

        mPreviousPostGeneReadMap.clear();
        mPreviousPostGeneReadMap.putAll(mPostGeneReadMap);
        mPostGeneReadMap.clear();

        // only purge junction positions which are now outside the regions to be processed
        Set<Integer> pastJuncPositions = mJunctionPositions.stream()
                .filter(x -> x < geneCollection.getNonGenicPositions()[SE_START]).collect(Collectors.toSet());

        pastJuncPositions.forEach(x -> mJunctionPositions.remove(x));

        mJunctionRacGroups.purgeGroups(geneCollection.getNonGenicPositions()[SE_START]);
    }

    public void clear() { clear(false); }
    public void clearAll() { clear(true); }

    private void clear(boolean full)
    {
        mChimericReadMap.clear();
        mLocalCompleteGroups.clear();
        mCandidateRealignedGroups.clear();
        mChimericStats.clear();
        mLocalChimericReads.clear();
        mSupplementaryJunctions.clear();

        if(full)
        {
            mPreviousPostGeneReadMap.clear();
            mPostGeneReadMap.clear();
            mJunctionPositions.clear();
            mJunctionRacGroups.clear();
            mHardFilteredReadGroups.clear();
        }
    }

    public void addRealignmentCandidates(final ReadRecord read1, final ReadRecord read2)
    {
        if(read1.isDuplicate() || read2.isDuplicate()) // group complete so drop these
            return;

        mCandidateRealignedGroups.add(new ReadGroup(read1, read2));
    }

    public void addChimericReadPair(final ReadRecord read1, final ReadRecord read2)
    {
        if(inExcludedRegion(read1, false) || inExcludedRegion(read2, false))
            return;

        if(!read1.isDuplicate() && !read2.isDuplicate())
        {
            // populate transcript info for intronic reads since it will be used in fusion matching
            addIntronicTranscriptData(read1);
            addIntronicTranscriptData(read2);
        }

        // add the pair when it's clear there aren't others with the same ID in the map
        if(mConfig.RunValidations && mChimericReadMap.containsKey(read1.Id))
        {
            // shouldn't occur
            ISF_LOGGER.error("overriding chimeric read({})", read1.Id);

            final ReadGroup existingGroup = mChimericReadMap.get(read1.Id);

            for(ReadRecord read : existingGroup.Reads)
            {
                ISF_LOGGER.error("existing read: {}", read);
            }

            ISF_LOGGER.error("new read: {}", read1);
            ISF_LOGGER.error("new read: {}", read2);

            existingGroup.Reads.add(read1);
            existingGroup.Reads.add(read2);
        }
        else
        {
            // cache info about any local dual-junction group
            ReadGroup group = new ReadGroup(read1, read2);

            if(group.size() == 2 && group.isComplete() && !group.hasDuplicateRead())
            {
                final int[] junctPositions = findCandidateJunctions(group.Reads, false);
                if(junctPositions[SE_START] > 0 && junctPositions[SE_END] > 0)
                {
                    final byte[] junctOrientations = {1, -1};
                    matchOrAddLocalJunctionGroup(group, junctPositions, junctOrientations);
                    return;
                }
            }

            mChimericReadMap.put(read1.Id, group);
        }
    }

    private void matchOrAddLocalJunctionGroup(final ReadGroup group, final int[] junctPositions, final byte[] junctOrientations)
    {
        LocalJunctionData matchData = null;
        for(ReadGroup readGroup : mLocalCompleteGroups)
        {
            if(!Arrays.equals(readGroup.localJunctionData().JunctionPositions, junctPositions))
                continue;

            if(!Arrays.equals(readGroup.localJunctionData().JunctionOrientations, junctOrientations))
                continue;

            matchData = readGroup.localJunctionData();
            ++matchData.MatchCount;
            ++mChimericStats.MatchedJunctions;
            break;
        }

        if(matchData == null)
        {
            matchData = new LocalJunctionData(junctPositions, junctOrientations);
            group.setLocalJunctionData(matchData);
            mLocalCompleteGroups.add(group);
            mChimericReadMap.put(group.id(), group);
        }

        for(int se = SE_START; se <= SE_END; ++se)
        {
            setMaxSplitMappedLength(
                    se, group.Reads, junctPositions, junctOrientations, matchData.MaxSplitLengths);
        }
    }

    private void addIntronicTranscriptData(final ReadRecord read)
    {
        if(read.overlapsGeneCollection() && read.getMappedRegions().isEmpty())
            read.addIntronicTranscriptRefs(mGeneCollection.getTranscripts());
    }

    public void postProcessChimericReads(final BaseDepth baseDepth, final FragmentTracker fragmentTracker)
    {
        // check any lone reads - this cannot be one of a pair of non-genic reads since they will have already been dismissed
        // so will either be a supplementary or a read linked to another gene collection
        for(Object object : fragmentTracker.getValues())
        {
            final ReadRecord read = (ReadRecord)object;

            if(read.isMateUnmapped() || inExcludedRegion(read, true) || read.isSecondaryAlignment())
                continue;

            if(!read.isDuplicate())
            {
                baseDepth.processRead(read.getMappedRegionCoords());
                addIntronicTranscriptData(read);
            }

            addChimericReads(mChimericReadMap, read);
        }

        // migrate any local chimeric fragments for analysis as alternate splice junctions
        final List<String> fragsToRemove = Lists.newArrayList();

        for(final ReadGroup readGroup : mChimericReadMap.values())
        {
            // skip reads if all will be processed later or have been already
            final List<ReadRecord> reads = readGroup.Reads;
            final String readId = reads.get(0).Id;

            /*
            if(readId.equals(""))
            {
                ISF_LOGGER.debug("specific read: {}", readId);
            }
            */

            int readCount = reads.size();
            boolean readGroupComplete = readGroup.isComplete();

            // duplicates are kept until a group is complete, since not all reads are marked as duplicates and those would otherwise
            // be orphaned later on

            if(reads.stream().anyMatch(x -> x.isDuplicate()) && reads.size() >= 2)
            {
                // chimeric read groups with duplicates will be dropped later on once the group is complete
                mGeneCollection.addCount(DUPLICATE, 1);
            }

            if(mRunFusions && skipNonGenicReads(reads))
            {
                fragsToRemove.add(readId);
                continue;
            }

            boolean readsRemoved = reads.size() < readCount;

            if(!readsRemoved && !keepChimericGroup(reads, readGroupComplete))
            {
                fragsToRemove.add(readId);
                continue;
            }

            if(mRunFusions)
                collectCandidateJunctions(readGroup);

            cacheSupplementaryJunctionCandidate(readGroup);
        }

        if(!fragsToRemove.isEmpty())
            fragsToRemove.forEach(x -> mChimericReadMap.remove(x));

        applyHardFilter();

        mChimericStats.ChimericJunctions += mJunctionPositions.size();

        int chimericCount = mChimericReadMap.size();
        mGeneCollection.addCount(TOTAL, chimericCount);
        mGeneCollection.addCount(CHIMERIC, chimericCount);

        if(mRunFusions)
        {
            addRealignCandidates();

            // chimeric reads will be processed by the fusion-finding routine, so need to capture transcript and exon data
            // and free up other gene & region read data (to avoid retaining large numbers of references/memory)
            for(final ReadGroup readGroup : mChimericReadMap.values())
            {
                readGroup.Reads.forEach(x -> x.captureGeneInfo(true));
                readGroup.Reads.forEach(x -> x.setReadJunctionDepth(baseDepth));
            }
        }
        else
        {
            // clear other chimeric state except for local junction information
            mChimericReadMap.clear();
            mLocalCompleteGroups.clear();
            mCandidateRealignedGroups.clear();
            mChimericStats.clear();
        }
    }

    private boolean inExcludedRegion(final ReadRecord read, boolean checkMate)
    {
        // check the read and its supplementary data if present
        if(mConfig.Filters.skipRead(read.Chromosome, read.PosStart))
            return true;

        if(checkMate && mConfig.Filters.skipRead(read.mateChromosome(), read.mateStartPosition()))
            return true;

        // only skip fragments in immune regions if both junction positions are in one
        boolean inImmuneRegion = mConfig.Filters.ImmuneGeneRegions.stream()
                .anyMatch(x -> x.Chromosome.equals(read.Chromosome) && positionsOverlap(read.PosStart, read.PosEnd, x.start(), x.end()));

        if(inImmuneRegion
        && mConfig.Filters.ImmuneGeneRegions.stream().anyMatch(x -> x.containsPosition(read.mateChromosome(), read.mateStartPosition())))
        {
            return true;
        }

        if(read.hasSuppAlignment())
        {
            SupplementaryReadData suppData = SupplementaryReadData.from(read.getSuppAlignment());

            if(suppData != null && mConfig.Filters.skipRead(suppData.Chromosome, suppData.Position))
                return true;

            if(inImmuneRegion && suppData != null
            && mConfig.Filters.ImmuneGeneRegions.stream().anyMatch(x -> x.containsPosition(suppData.Chromosome, suppData.Position)))
            {
                return true;
            }
        }

        return false;
    }

    private boolean keepChimericGroup(final List<ReadRecord> reads, boolean readGroupComplete)
    {
        if(reads.stream().anyMatch(x -> x.isTranslocation()))
        {
            ++mChimericStats.Translocations;
            return true;
        }

        if(readGroupComplete && isInversion(reads))
        {
            ++mChimericStats.Inversions;
            return true;
        }

        boolean spanGeneSpliceSites = reads.stream().anyMatch(x -> x.hasInterGeneSplit()) ?
                true : setHasMultipleKnownSpliceGenes(reads, mKnownPairGeneIds);

        if(reads.stream().anyMatch(x -> x.spansGeneCollections()))
        {
            // may turn out to just end in the next pre-gene section but cannot say at this time
            ++mChimericStats.LocalInterGeneFrags;
            return true;
        }

        if(!readGroupComplete)
            return true;

        if(reads.stream().anyMatch(x -> !x.withinGeneCollection()))
        {
            // some reads are non-genic in full or part
            if(reads.stream().filter(x -> x.fullyNonGenic()).count() == reads.size())
            {
                // all reads non-genic - drop these entirely
                return false;
            }

            int minPosition = reads.stream().mapToInt(x -> x.getCoordsBoundary(SE_START)).min().orElse(0);
            int maxPosition = reads.stream().mapToInt(x -> x.getCoordsBoundary(SE_END)).max().orElse(0);

            if(mGeneCollection.regionBounds()[SE_START] - minPosition > MAX_NOVEL_SJ_DISTANCE
            || maxPosition - mGeneCollection.regionBounds()[SE_END] > MAX_NOVEL_SJ_DISTANCE)
            {
                // too far from the gene boundaries so consider these chimeric
                return true;
            }
        }

        // check whether 2 genes must be involved, or whether just one gene can explain the junction
        // NOTE: since not all chimeric reads may be available at this point, this test is repeated in the fusion routine
        if(spanGeneSpliceSites)
        {
            ++mChimericStats.LocalInterGeneFrags;
            return true;
        }

        // all reads within the gene - treat as alternative SJ candidates
        mLocalChimericReads.add(reads);
        return false;
    }

    private boolean skipNonGenicReads(final List<ReadRecord> reads)
    {
        // any set of entirely post-gene read(s) will be skipped and then picked up by the next gene collection's processing
        // otherwise record that they were processed to avoid double-processing them in the next gene collection
        List<ReadRecord> postGeneReads = !mGeneCollection.isEndOfChromosome() ? reads.stream()
                .filter(x -> x.PosStart > mGeneCollection.regionBounds()[SE_END])
                .collect(Collectors.toList()) : Lists.newArrayList();

        if(postGeneReads.size() == reads.size())
            return true;

        List<ReadRecord> preGeneReads = reads.stream()
                .filter(x -> x.PosStart < mGeneCollection.regionBounds()[SE_START])
                .collect(Collectors.toList());

        if(!preGeneReads.isEmpty())
        {
            // remove any previously processed reads
            final String readId = preGeneReads.get(0).Id;
            List<ReadRecord> prevPostGeneReads = mPreviousPostGeneReadMap.get(readId);

            if(prevPostGeneReads != null)
            {
                preGeneReads.stream().filter(x -> prevPostGeneReads.stream().anyMatch(y -> y.matches(x))).forEach(x -> reads.remove(x));

                if(reads.isEmpty())
                    return true;
            }
        }

        // cache and stop processing this group
        if(!postGeneReads.isEmpty())
            mPostGeneReadMap.put(reads.get(0).Id, postGeneReads);

        return false;
    }

    private void addRacGroups()
    {
        mCandidateRealignedGroups.forEach(x -> mJunctionRacGroups.checkAddCandidateGroup(x));
    }

    private void addRealignCandidates()
    {
        addRacGroups();

        // in addition to the group having a least one read with the required soft-clipping, the other read cannot extend past this
        // possible point of junction support
        Set<Integer>[] supportedJunctions = new Set[SE_PAIR];
        supportedJunctions[SE_START] = Sets.newHashSetWithExpectedSize(2); // from start boundaries, orientation -1
        supportedJunctions[SE_END] = Sets.newHashSetWithExpectedSize(2); // from end boundaries, orientation +1

        for(ReadGroup readGroup : mCandidateRealignedGroups)
        {
            supportedJunctions[SE_START].clear();
            supportedJunctions[SE_END].clear();

            for(ReadRecord read : readGroup.Reads)
            {
                for(int se = SE_START; se <= SE_END; ++se)
                {
                    final int seIndex = se;
                    if(!read.isSoftClipped(se))
                        continue;

                    int readBoundary = read.getCoordsBoundary(seIndex);

                    if(mJunctionPositions.stream().anyMatch(x -> positionWithin(readBoundary,
                            x - SOFT_CLIP_JUNC_BUFFER, x + SOFT_CLIP_JUNC_BUFFER)))
                    {
                        supportedJunctions[se].add(readBoundary);
                    }
                }
            }

            if(supportedJunctions[SE_START].isEmpty() && supportedJunctions[SE_END].isEmpty())
                continue;

            boolean validGroup = true;

            if(!supportedJunctions[SE_START].isEmpty()
            && readGroup.Reads.stream().anyMatch(x -> supportedJunctions[SE_START].stream().anyMatch(y -> x.PosStart < y - SOFT_CLIP_JUNC_BUFFER)))
            {
                validGroup = false;
            }
            else if(!supportedJunctions[SE_END].isEmpty()
            && readGroup.Reads.stream().anyMatch(x -> supportedJunctions[SE_END].stream().anyMatch(y -> x.PosEnd > y + SOFT_CLIP_JUNC_BUFFER)))
            {
                validGroup = false;
            }

            if(validGroup)
            {
                mChimericReadMap.put(readGroup.id(), readGroup);
                ++mChimericStats.CandidateRealignFrags;
            }
        }
    }

    private void collectCandidateJunctions(final ReadGroup readGroup)
    {
        if(readGroup.localJunctionData() != null)
        {
            final LocalJunctionData localJunctionData = readGroup.localJunctionData();
            addJunction(localJunctionData.JunctionPositions[SE_START], localJunctionData.JunctionOrientations[SE_START]);
            addJunction(localJunctionData.JunctionPositions[SE_END], localJunctionData.JunctionOrientations[SE_END]);
            return;
        }

        findCandidateJunctions(readGroup.Reads, true);
    }

    private void addJunction(int juncPosition, byte juncOrientation)
    {
        if(juncPosition > 0)
        {
            mJunctionPositions.add(juncPosition);
            mJunctionRacGroups.addJunction(juncPosition, juncOrientation);
        }
    }

    private int[] findCandidateJunctions(final List<ReadRecord> reads, boolean addJunction)
    {
        final ReadRecord splitRead = findSplitRead(reads);

        if(splitRead != null)
        {
            int[] splitJunction = findSplitReadJunction(splitRead);

            if(addJunction)
            {
                addJunction(splitJunction[SE_START], POS_ORIENT);
                addJunction(splitJunction[SE_END], NEG_ORIENT);
            }

            return splitJunction;
        }

        final int[] junctionPositions = new int[SE_PAIR];

        final ReadRecord suppRead = reads.stream().filter(x -> x.hasSuppAlignment()).findFirst().orElse(null);
        if(suppRead != null)
        {
            SoftClipSide scSide = SoftClipSide.fromRead(suppRead.Cigar);

            if(scSide != null && scSide.Length >= REALIGN_MIN_SOFT_CLIP_BASE_LENGTH)
            {
                junctionPositions[scSide.Side] = suppRead.getCoordsBoundary(scSide.Side);

                if(addJunction)
                    addJunction(junctionPositions[scSide.Side], scSide.Side == SE_START ? NEG_ORIENT : POS_ORIENT);
            }

            return junctionPositions;
        }

        // otherwise must either have a junction supported by 2 facing soft-clipped reads or a supplementary read
        // logic needs to match the type and junction assignment in FusionFragmentBuilder
        if(reads.size() == 1)
        {
            /*
            final ReadRecord read = reads.get(0);

            if(hasRealignableSoftClip(read, SE_START, false))
                junctionPositions[SE_START] = read.getCoordsBoundary(SE_START);

            if(hasRealignableSoftClip(read, SE_END, false))
                junctionPositions[SE_END] = read.getCoordsBoundary(SE_END);
            */
        }
        else
        {
            int[] scPositions = {-1, -1};

            for(ReadRecord read : reads)
            {
                if(hasRealignableSoftClip(read, SE_START, false))
                    scPositions[SE_END] = read.getCoordsBoundary(SE_START);
                else if(hasRealignableSoftClip(read, SE_END, false))
                    scPositions[SE_START] = read.getCoordsBoundary(SE_END);
            }

            if(scPositions[SE_START] > 0 && scPositions[SE_END] > 0 && scPositions[SE_START] < scPositions[SE_END])
            {
                if(addJunction)
                {
                    addJunction(scPositions[SE_START], POS_ORIENT);
                    addJunction(scPositions[SE_END], NEG_ORIENT);
                }

                return scPositions;
            }
        }

        return junctionPositions;
    }

    private void applyHardFilter()
    {
        if(mConfig.Fusions.MinHardFilterFrags <= 1)
            return;

        // hard-filter chimeric split fragments which aren't in known fusion genes or with known splice sites
        boolean hasKnownGene = mGeneCollection.geneIds().stream().anyMatch(x -> mKnownGeneIds.contains(x));
        final int minSplitFrags = mConfig.Fusions.MinHardFilterFrags;

        for(ReadGroup readGroup : mLocalCompleteGroups)
        {
            if(readGroup.localJunctionData().MatchCount + 1 >= minSplitFrags)
                continue;

            if(readGroup.Reads.stream().filter(x -> x.withinGeneCollection())
                    .anyMatch(x -> x.getMappedRegions().values().stream().anyMatch(y -> y == EXON_BOUNDARY)))
            {
                continue;
            }

            if(mChimericReadMap.containsKey(readGroup.id()))
            {
                mChimericReadMap.remove(readGroup.id());
                ++mChimericStats.HardFiltered;
            }
        }

        for(List<SupplementaryJunctionData> juncsByPos : mSupplementaryJunctions.values())
        {
            for(SupplementaryJunctionData suppJuncData : juncsByPos)
            {
                if(suppJuncData.MatchCount + 1 >= minSplitFrags)
                    continue;

                ReadGroup readGroup = mChimericReadMap.get(suppJuncData.ReadIds.get(0));

                if(readGroup == null)
                    continue;

                if(suppJuncData.IsGenic)
                {
                    if(suppJuncData.KnownSpliceSite)
                    {
                        continue;
                    }
                    else
                    {
                        if(hasKnownGene)
                            continue;
                    }
                }

                for(String readId : suppJuncData.ReadIds)
                {
                    if(mChimericReadMap.containsKey(readId))
                    {
                        mChimericReadMap.remove(readId);
                        ++mChimericStats.HardFiltered;

                        Set<String> chrReadIds = mHardFilteredReadGroups.get(suppJuncData.RemoteChromosome);
                        if(chrReadIds == null)
                        {
                            chrReadIds = Sets.newHashSet();
                            mHardFilteredReadGroups.put(suppJuncData.RemoteChromosome, chrReadIds);
                        }

                        chrReadIds.add(readId);
                    }
                }
            }
        }
    }

    private void cacheSupplementaryJunctionCandidate(final ReadGroup readGroup)
    {
        if(mConfig.Fusions.MinHardFilterFrags <= 1)
            return;

        ReadRecord read = readGroup.Reads.stream().filter(x -> x.hasSuppAlignment()).findFirst().orElse(null);
        if(read == null)
            return;

        SupplementaryReadData suppData = SupplementaryReadData.from(read.getSuppAlignment());

        if(suppData == null)
            return;

        // find the junction from this read's SC and same for the supp mapping data
        SoftClipSide scSide = SoftClipSide.fromRead(read.Cigar);

        boolean knownSpliceSite = read.getMappedRegions().values().stream().anyMatch(x -> x == EXON_BOUNDARY);
        SupplementaryJunctionData suppJuncData = new SupplementaryJunctionData(
                read.Id, read.withinGeneCollection(), knownSpliceSite);

        if(scSide.isLeft())
        {
            suppJuncData.LocalJunctionPos = read.getCoordsBoundary(SE_START);
            suppJuncData.LocalJunctionOrient = NEG_ORIENT;
        }
        else
        {
            suppJuncData.LocalJunctionPos = read.getCoordsBoundary(SE_END);
            suppJuncData.LocalJunctionOrient = POS_ORIENT;
        }

        suppJuncData.RemoteChromosome = suppData.Chromosome;
        Cigar remoteCigar = CommonUtils.cigarFromStr(suppData.Cigar);
        scSide = SoftClipSide.fromRead(remoteCigar);

        if(scSide.isLeft())
        {
            suppJuncData.RemoteJunctionPos = suppData.Position;
        }
        else
        {
            int skippedBases = remoteCigar.getCigarElements().stream()
                    .filter(x -> x.getOperator() == N || x.getOperator() == M)
                    .mapToInt(x -> x.getLength()).sum();

            suppJuncData.RemoteJunctionPos = suppData.Position + skippedBases - 1;
        }

        List<SupplementaryJunctionData> juncsByPos = mSupplementaryJunctions.get(suppJuncData.LocalJunctionPos);
        if(juncsByPos == null)
        {
            mSupplementaryJunctions.put(suppJuncData.LocalJunctionPos, Lists.newArrayList(suppJuncData));
            return;
        }

        SupplementaryJunctionData matchedData = juncsByPos.stream()
                .filter(x -> x.matches(suppJuncData)).findFirst().orElse(null);

        if(matchedData != null)
        {
            ++matchedData.MatchCount;
        }
        else
        {
            juncsByPos.add(suppJuncData);
        }
    }

    private class SupplementaryJunctionData
    {
        public final List<String> ReadIds;
        public final boolean IsGenic;
        public final boolean KnownSpliceSite;

        public int LocalJunctionPos;
        public byte LocalJunctionOrient;

        public String RemoteChromosome;
        public int RemoteJunctionPos;
        public byte RemoteJunctionOrient;

        public int MatchCount;

        public SupplementaryJunctionData(final String readId, boolean isGenic, boolean knownSpliceSite)
        {
            ReadIds = Lists.newArrayList(readId);
            IsGenic = isGenic;
            KnownSpliceSite = knownSpliceSite;

            LocalJunctionPos = 0;
            LocalJunctionOrient = 0;
            RemoteChromosome = "";
            RemoteJunctionPos = 0;
            RemoteJunctionOrient = 0;
            MatchCount = 0;
        }

        public boolean matches(final SupplementaryJunctionData other)
        {
            return LocalJunctionPos == other.LocalJunctionPos
                && RemoteChromosome.equals(other.RemoteChromosome)
                && RemoteJunctionPos == other.RemoteJunctionPos
                && LocalJunctionOrient == other.LocalJunctionOrient;
        }

        public String toString()
        {
            return String.format("local(%d:%d) remote(%s:%d:%d) matched(%d)",
                    LocalJunctionPos, LocalJunctionOrient, RemoteChromosome, RemoteJunctionPos, RemoteJunctionOrient, MatchCount);
        }

    }

}
