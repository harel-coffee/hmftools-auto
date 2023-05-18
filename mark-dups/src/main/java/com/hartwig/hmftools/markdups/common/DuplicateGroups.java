package com.hartwig.hmftools.markdups.common;

import static java.lang.Math.abs;
import static java.lang.Math.max;

import static com.hartwig.hmftools.markdups.MarkDupsConfig.MD_LOGGER;
import static com.hartwig.hmftools.markdups.common.FragmentStatus.CANDIDATE;
import static com.hartwig.hmftools.markdups.common.FragmentStatus.DUPLICATE;
import static com.hartwig.hmftools.markdups.common.FragmentStatus.NONE;
import static com.hartwig.hmftools.markdups.common.FragmentStatus.PRIMARY;
import static com.hartwig.hmftools.markdups.common.FragmentUtils.calcFragmentStatus;
import static com.hartwig.hmftools.markdups.consensus.UmiUtils.buildUmiGroups;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.hartwig.hmftools.markdups.MarkDupsConfig;
import com.hartwig.hmftools.markdups.consensus.GroupIdGenerator;
import com.hartwig.hmftools.markdups.consensus.UmiConfig;
import com.hartwig.hmftools.markdups.consensus.DuplicateGroup;

import htsjdk.samtools.SAMRecord;

public class DuplicateGroups
{
    //private static final int HIGH_DEPTH_THRESHOLD = 10000;
    private final UmiConfig mUmiConfig;
    private final Statistics mStats;
    private final boolean mFormConsensus;
    private final GroupIdGenerator mIdGenerator;

    public DuplicateGroups(final MarkDupsConfig config)
    {
        mUmiConfig = config.UMIs;
        mFormConsensus = config.FormConsensus;
        mIdGenerator = config.IdGenerator;
        mStats = new Statistics();
    }

    public Statistics statistics() { return mStats; }
    public UmiConfig umiConfig() { return mUmiConfig; }

    public static void findDuplicateFragments(
            final List<Fragment> fragments, final List<Fragment> resolvedFragments,
            final List<List<Fragment>> positionDuplicateGroups, final List<CandidateDuplicates> candidateDuplicatesList)
    {
        // take all the fragments at this initial fragment position and classify them as duplicates, non-duplicates (NONE) or unclear
        // note: all fragments will be given a classification, and resolved fragments are removed from the input fragment list

        if(fragments.size() == 1)
        {
            Fragment fragment = fragments.get(0);
            fragment.setStatus(NONE);
            resolvedFragments.add(fragment);
            fragments.clear();
            return;
        }

        int fragmentCount = fragments.size();
        boolean applyHighDepthLogic = false; // fragmentCount > HIGH_DEPTH_THRESHOLD;

        Map<Fragment,List<Fragment>> possibleDuplicates = Maps.newHashMap();
        Map<Fragment,Fragment> linkedDuplicates = Maps.newHashMap();

        int i = 0;
        while(i < fragments.size())
        {
            Fragment fragment1 = fragments.get(i);

            if(i == fragments.size() - 1)
            {
                if(!possibleDuplicates.containsKey(fragment1) && !linkedDuplicates.containsKey(fragment1))
                {
                    fragment1.setStatus(NONE);
                    resolvedFragments.add(fragment1);
                    fragments.remove(i);
                }
                break;
            }

            List<Fragment> duplicateFragments = null;

            Fragment existingLinkedFragment1 = linkedDuplicates.get(fragment1);

            boolean isCandidateDup = existingLinkedFragment1 != null;

            List<Fragment> candidateFragments = existingLinkedFragment1 != null ? possibleDuplicates.get(existingLinkedFragment1) : null;

            int j = i + 1;
            while(j < fragments.size())
            {
                Fragment fragment2 = fragments.get(j);

                FragmentStatus status = calcFragmentStatus(fragment1, fragment2);

                if(applyHighDepthLogic && status == CANDIDATE && proximateFragmentSizes(fragment1, fragment2))
                    status = DUPLICATE;

                if(status == DUPLICATE)
                {
                    fragment1.setStatus(status);
                    fragment2.setStatus(status);

                    if(duplicateFragments == null)
                        duplicateFragments = Lists.newArrayList(fragment1);

                    duplicateFragments.add(fragment2);
                    fragments.remove(j);
                    continue;
                }

                if(fragment1.status() != DUPLICATE && status == CANDIDATE)
                {
                    isCandidateDup = true;

                    // the pair is a candidate for duplicates but without their mates it's unclear whether they will be
                    Fragment existingLinkedFragment2 = linkedDuplicates.get(fragment2);

                    if(existingLinkedFragment1 != null && existingLinkedFragment1 == existingLinkedFragment2)
                    {
                        // already a part of the same candidate group
                    }
                    else if(existingLinkedFragment2 != null)
                    {
                        List<Fragment> existingGroup = possibleDuplicates.get(existingLinkedFragment2);

                        if(candidateFragments == null)
                        {
                            existingGroup.add(fragment1);
                        }
                        else
                        {
                            // take this fragment's candidates and move them to the existing group
                            for(Fragment fragment : candidateFragments)
                            {
                                if(!existingGroup.contains(fragment))
                                    existingGroup.add(fragment);

                                linkedDuplicates.put(fragment, existingLinkedFragment2);
                            }

                            possibleDuplicates.remove(existingLinkedFragment1);
                        }

                        linkedDuplicates.put(fragment1, existingLinkedFragment2);
                        existingLinkedFragment1 = existingLinkedFragment2;
                        candidateFragments = existingGroup;
                    }
                    else
                    {
                        if(candidateFragments == null)
                        {
                            candidateFragments = Lists.newArrayList(fragment1);
                            possibleDuplicates.put(fragment1, candidateFragments);
                            existingLinkedFragment1 = fragment1;
                        }

                        candidateFragments.add(fragment2);
                        linkedDuplicates.put(fragment2, existingLinkedFragment1);
                    }
                }

                ++j;
            }

            if(fragment1.status().isDuplicate())
            {
                if(isCandidateDup && possibleDuplicates.containsKey(fragment1))
                {
                    // clean-up
                    candidateFragments.forEach(x -> linkedDuplicates.remove(x));
                    possibleDuplicates.remove(fragment1);
                }

                resolvedFragments.addAll(duplicateFragments);
                fragments.remove(i);

                positionDuplicateGroups.add(duplicateFragments);
            }
            else if(isCandidateDup)
            {
                ++i;
            }
            else
            {
                fragment1.setStatus(NONE);
                resolvedFragments.add(fragment1);
                fragments.remove(i);
            }
        }

        if(!possibleDuplicates.isEmpty())
        {
            for(List<Fragment> candidateFragments : possibleDuplicates.values())
            {
                List<Fragment> unresolvedFragments = candidateFragments.stream().filter(x -> !x.status().isResolved()).collect(Collectors.toList());

                if(unresolvedFragments.size() >= 2)
                {
                    CandidateDuplicates candidateDuplicates = CandidateDuplicates.from(unresolvedFragments.get(0));
                    candidateDuplicatesList.add(candidateDuplicates);

                    for(int index = 1; index < unresolvedFragments.size(); ++index)
                    {
                        candidateDuplicates.addFragment(unresolvedFragments.get(index));
                    }
                }
                else if(unresolvedFragments.size() == 1)
                {
                    Fragment fragment = unresolvedFragments.get(0);
                    fragment.setStatus(NONE);
                    resolvedFragments.add(fragment);
                }
            }
        }

        int candidateCount = 0;
        for(CandidateDuplicates candidateDuplicates : candidateDuplicatesList)
        {
            candidateDuplicates.fragments().forEach(x -> x.setStatus(CANDIDATE));
            candidateDuplicates.fragments().forEach(x -> x.setCandidateDupKey(candidateDuplicates.key()));
            candidateCount += candidateDuplicates.fragmentCount();
        }

        if(candidateCount + resolvedFragments.size() != fragmentCount)
        {
            MD_LOGGER.error("failed to classify all fragments: original({}) resolved({}) candidates({})",
                    fragmentCount, resolvedFragments.size(), candidateCount);

            for(Fragment fragment : resolvedFragments)
            {
                MD_LOGGER.error("resolved fragment: {}", fragment);
            }

            for(CandidateDuplicates candidateDuplicates : candidateDuplicatesList)
            {
                for(Fragment fragment : candidateDuplicates.fragments())
                {
                    MD_LOGGER.error("candidate dup fragment: {}", fragment);
                }
            }
        }
    }

    private static boolean proximateFragmentSizes(final Fragment first, final Fragment second)
    {
        return abs(abs(first.reads().get(0).getInferredInsertSize()) - abs(second.reads().get(0).getInferredInsertSize())) <= 10;
    }

    public List<DuplicateGroup> processDuplicateGroups(
            final List<List<Fragment>> rawDuplicateGroups, boolean captureStats, int nonDuplicateFragCount, boolean inExcludedRegion)
    {
        if(rawDuplicateGroups == null)
            return Collections.EMPTY_LIST;

        if(mUmiConfig.Enabled && !inExcludedRegion)
            return processUmiGroups(rawDuplicateGroups, captureStats, nonDuplicateFragCount);

        if(captureStats)
        {
            for(List<Fragment> fragments : rawDuplicateGroups)
            {
                ++mStats.DuplicateGroups;
                mStats.addDuplicateGroup(fragments.size());
            }
        }

        if(mFormConsensus && !inExcludedRegion)
            return processConsensusGroups(rawDuplicateGroups);

        processNonUmiGroups(rawDuplicateGroups, inExcludedRegion);
        return null;
    }

    private List<DuplicateGroup> processUmiGroups(final List<List<Fragment>> duplicateGroups, boolean captureStats, int nonDuplicateFragCount)
    {
        List<DuplicateGroup> allUmiGroups = Lists.newArrayList();

        int posGroupCount = duplicateGroups.size() + nonDuplicateFragCount; // count of unique fragments sharing the same start pos
        int uniqueFragmentCount = nonDuplicateFragCount; // count of fragments with matching coordinates (after UMI collapsing)
        int maxDuplicatePosCount = 0;

        int maxUmiFragmentCount = 0;
        DuplicateGroup maxUmiGroup = null;

        for(List<Fragment> fragments : duplicateGroups)
        {
            List<DuplicateGroup> umiGroups = buildUmiGroups(fragments, mUmiConfig);

            // collect stats including single groups
            if(captureStats)
            {
                ++mStats.DuplicateGroups;

                if(mUmiConfig.BaseDiffStats)
                    mStats.recordUmiBaseDiffs(mUmiConfig, umiGroups);

                uniqueFragmentCount += umiGroups.size();

                maxDuplicatePosCount = max(maxDuplicatePosCount, umiGroups.size());
            }

            for(DuplicateGroup umiGroup : umiGroups)
            {
                if(umiGroup.fragmentCount() == 1)
                {
                    // drop any single fragments
                    Fragment fragment = umiGroup.fragments().get(0);
                    fragment.setStatus(NONE);
                    fragment.setUmi(null);
                    continue;
                }

                umiGroup.categoriseReads();

                allUmiGroups.add(umiGroup);

                if(captureStats)
                {
                    ++mStats.UmiGroups;
                    mStats.addDuplicateGroup(umiGroup.fragmentCount());
                }

                if(umiGroup.fragmentCount() > maxUmiFragmentCount)
                {
                    maxUmiGroup = umiGroup;
                    maxUmiFragmentCount = umiGroup.fragmentCount();
                }
            }
        }

        if(captureStats)
        {
            mStats.recordFragmentPositions(posGroupCount, uniqueFragmentCount, maxDuplicatePosCount, maxUmiGroup);
        }

        return allUmiGroups;
    }

    private List<DuplicateGroup> processConsensusGroups(final List<List<Fragment>> rawDuplicateGroups)
    {
        List<DuplicateGroup> duplicateGroups = Lists.newArrayListWithCapacity(rawDuplicateGroups.size());

        for(List<Fragment> fragments : rawDuplicateGroups)
        {
            DuplicateGroup duplicateGroup = new DuplicateGroup(mIdGenerator.nextId(), fragments.get(0));

            for(int i = 1; i < fragments.size(); ++i)
            {
                duplicateGroup.fragments().add(fragments.get(i));
            }

            duplicateGroup.categoriseReads();
            duplicateGroups.add(duplicateGroup);
        }

        return duplicateGroups;
    }

    private void processNonUmiGroups(final List<List<Fragment>> duplicateGroups, boolean inExcludedRegion)
    {
        if(duplicateGroups == null)
            return;

        for(List<Fragment> fragments : duplicateGroups)
        {
            if(inExcludedRegion)
                fragments.get(0).setStatus(PRIMARY);
            else
                setPrimaryRead(fragments);
        }
    }

    private static void setPrimaryRead(final List<Fragment> duplicateFragments)
    {
        Fragment primary = findPrimaryFragment(duplicateFragments, false);
        primary.setStatus(PRIMARY);
    }

    public static Fragment findPrimaryFragment(final List<Fragment> fragments, boolean considerMarkedDups)
    {
        if(considerMarkedDups)
        {
            // take the primary (non-duplicate) group if there is (just) one already marked
            List<Fragment> nonDupGroups = fragments.stream().filter(x -> !hasDuplicates(x)).collect(Collectors.toList());

            if(nonDupGroups.size() == 1)
                return nonDupGroups.get(0);
        }

        // otherwise choose the group with the highest base quality
        Fragment maxFragment = null;
        double maxBaseQual = 0;

        for(Fragment fragment : fragments)
        {
            double avgBaseQual = calcBaseQualAverage(fragment);
            fragment.setAverageBaseQual(avgBaseQual);

            if(avgBaseQual > maxBaseQual)
            {
                maxBaseQual = avgBaseQual;
                maxFragment = fragment;
            }
        }

        return maxFragment;
    }

    private static boolean hasDuplicates(final Fragment fragment)
    {
        return fragment.reads().stream().anyMatch(x -> x.getDuplicateReadFlag());
    }

    public static double calcBaseQualAverage(final Fragment fragment)
    {
        int readBaseCount = 0;
        int readBaseQualTotal = 0;

        for(SAMRecord read : fragment.reads())
        {
            if(read.getSupplementaryAlignmentFlag())
                continue;

            for(int i = 0; i < read.getBaseQualities().length; ++i)
            {
                ++readBaseCount;
                readBaseQualTotal += read.getBaseQualities()[i];
            }
        }

        return readBaseCount > 0 ? readBaseQualTotal / (double)readBaseCount : 0;
    }

    public static double calcBaseQualAverage(final SAMRecord read)
    {
        int readBaseCount = 0;
        int readBaseQualTotal = 0;

        for(int i = 0; i < read.getBaseQualities().length; ++i)
        {
            ++readBaseCount;
            readBaseQualTotal += read.getBaseQualities()[i];
        }

        return readBaseCount > 0 ? readBaseQualTotal / (double)readBaseCount : 0;
    }

}
