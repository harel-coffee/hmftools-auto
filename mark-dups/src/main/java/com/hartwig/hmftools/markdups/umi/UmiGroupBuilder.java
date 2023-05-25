package com.hartwig.hmftools.markdups.umi;

import static java.lang.Math.max;

import static com.hartwig.hmftools.markdups.common.FragmentStatus.NONE;
import static com.hartwig.hmftools.markdups.umi.UmiUtils.exceedsUmiIdDiff;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.hartwig.hmftools.markdups.common.DuplicateGroup;
import com.hartwig.hmftools.markdups.common.Fragment;

public class UmiGroupBuilder
{
    private final UmiConfig mUmiConfig;
    private final UmiStatistics mStats;

    public UmiGroupBuilder(final UmiConfig config, final UmiStatistics stats)
    {
        mUmiConfig = config;
        mStats = stats;
    }

    public static List<DuplicateGroup> buildUmiGroups(final List<Fragment> fragments, final UmiConfig config)
    {
        Map<String, DuplicateGroup> groups = Maps.newHashMap();
        boolean checkDefinedUmis = config.hasDefinedUmis();
        boolean useDefinedUmis = checkDefinedUmis;

        for(Fragment fragment : fragments)
        {
            String umiId = config.extractUmiId(fragment.id());

            if(checkDefinedUmis)
            {
                String definedUmiId = config.matchDefinedUmiId(umiId);
                if(definedUmiId == null)
                {
                    useDefinedUmis = false;
                    checkDefinedUmis = false;
                }
                else
                {
                    umiId = definedUmiId;
                }
            }

            DuplicateGroup group = groups.get(umiId);

            if(group == null)
            {
                groups.put(umiId, new DuplicateGroup(umiId, fragment));
            }
            else
            {
                group.fragments().add(fragment);
            }
        }

        if(useDefinedUmis)
        {
            return groups.values().stream().collect(Collectors.toList());
        }

        // order groups by descending number of fragments
        List<DuplicateGroup> orderedGroups = groups.values().stream().sorted(new UmiUtils.SizeComparator()).collect(Collectors.toList());

        // then apply the directional model, where smaller groups are merged into larger ones
        int i = 0;
        while(i < orderedGroups.size() - 1)
        {
            DuplicateGroup first = orderedGroups.get(i);

            List<DuplicateGroup> cluster = Lists.newArrayList(first);

            int j = i + 1;
            while(j < orderedGroups.size())
            {
                DuplicateGroup second = orderedGroups.get(j);

                boolean merged = false;

                for(DuplicateGroup existing : cluster)
                {
                    if(existing.fragmentCount() >= second.fragmentCount() && !exceedsUmiIdDiff(existing.id(), second.id(), config.PermittedBaseDiff))
                    {
                        merged = true;
                        break;
                    }
                }

                if(!merged)
                {
                    ++j;
                }
                else
                {
                    orderedGroups.remove(j);
                    cluster.add(second);

                    // restart the search since a newly added group may be close enough to a skipped one
                    j = i + 1;
                }
            }

            for(j = 1; j < cluster.size(); ++j)
            {
                first.fragments().addAll(cluster.get(j).fragments());
            }

            ++i;
        }

        // run a final check allowing collapsing of UMIs with 2-base differences
        if(orderedGroups.size() > 1)
        {
            i = 0;
            while(i < orderedGroups.size())
            {
                DuplicateGroup first = orderedGroups.get(i);

                int j = i + 1;
                while(j < orderedGroups.size())
                {
                    DuplicateGroup second = orderedGroups.get(j);

                    if(!exceedsUmiIdDiff(first.id(), second.id(), config.PermittedBaseDiff + 1))
                    {
                        first.fragments().addAll(second.fragments());
                        orderedGroups.remove(j);
                    }
                    else
                    {
                        ++j;
                    }
                }

                ++i;
            }
        }

        return orderedGroups;
    }

    private class CoordinateGroup
    {
        public final String CoordKey;

        // store any mix of duplicate groups or single fragments
        public List<Object> ForwardGroups;
        public List<Object> ReverseGroups;

        public CoordinateGroup(final String coordKey)
        {
            CoordKey = coordKey;
            ForwardGroups = null;
            ReverseGroups = null;
        }

        public boolean hasOpposites()
        {
            return ForwardGroups != null && ReverseGroups != null;
        }

        private void addFragmentGroup(final Object object, boolean isForward)
        {
            if(isForward)
            {
                if(ForwardGroups == null)
                    ForwardGroups = Lists.newArrayList(object);
                else
                    ForwardGroups.add(object);
            }
            else
            {
                if(ReverseGroups == null)
                    ReverseGroups = Lists.newArrayList(object);
                else
                    ReverseGroups.add(object);
            }
        }

        public void addGroup(final DuplicateGroup group)
        {
            addFragmentGroup(group, group.fragmentCoordinates().IsForward);
        }

        public void addFragment(final Fragment fragment)
        {
            addFragmentGroup(fragment, fragment.coordinates().IsForward);
        }
    }

    public List<DuplicateGroup> processUmiGroups(
            final List<List<Fragment>> duplicateGroups, final List<Fragment> singleFragments, boolean captureStats)
    {
        // organise groups by their UMIs, applying base-difference collapsing rules
        // UMI stats require evaluation of uncollapsed UMI groups with the same coordinates
        // at the same time organise UMI groups by the coordinates

        int maxDuplicatePosCount = 0;

        boolean formCoordGroups = mUmiConfig.BaseStats || (duplicateGroups.size() + singleFragments.size() > 1);

        List<CoordinateGroup> coordinateGroups = formCoordGroups ? Lists.newArrayList() : null;

        List<DuplicateGroup> allUmiGroups = Lists.newArrayList();

        for(List<Fragment> fragments : duplicateGroups)
        {
            List<DuplicateGroup> umiGroups = buildUmiGroups(fragments, mUmiConfig);

            maxDuplicatePosCount = max(maxDuplicatePosCount, umiGroups.size());

            if(formCoordGroups)
            {
                CoordinateGroup coordGroup = getOrCreateCoordGroup(coordinateGroups, fragments.get(0).coordinates().Key);

                // add in order of descending by fragment count for non-duplex collapsing
                Collections.sort(umiGroups, new UmiUtils.SizeComparator());
                umiGroups.forEach(x -> coordGroup.addGroup(x));
            }
            else
            {
                allUmiGroups.addAll(umiGroups);
            }
        }

        if(formCoordGroups)
        {
            // add in single fragments
            for(Fragment fragment : singleFragments)
            {
                CoordinateGroup coordGroup = getOrCreateCoordGroup(coordinateGroups, fragment.coordinates().Key);
                coordGroup.addFragment(fragment);
            }

            if(mUmiConfig.BaseStats)
            {
                // test UMI similarity for all fragments and groups with the same coordinates
                for(CoordinateGroup coordGroup : coordinateGroups)
                {
                    captureUmiGroupStats(coordGroup.ForwardGroups);
                    captureUmiGroupStats(coordGroup.ReverseGroups);
                }
            }

            // collapse duplex and single UMIs with opposite orientations
            for(CoordinateGroup coordGroup : coordinateGroups)
            {
                collapseCoordinateGroup(allUmiGroups, coordGroup);
            }
        }

        List<DuplicateGroup> finalUmiGroups = Lists.newArrayList();

        for(DuplicateGroup umiGroup : allUmiGroups)
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
            finalUmiGroups.add(umiGroup);
        }

        if(captureStats)
        {
            int uniqueCoordCount = 0;
            int uniqueFragmentCount = 0;

            if(formCoordGroups)
            {
                for(CoordinateGroup coordGroup : coordinateGroups)
                {
                    if(coordGroup.ForwardGroups != null)
                    {
                        uniqueFragmentCount += coordGroup.ForwardGroups.size();
                        ++uniqueCoordCount;
                    }

                    if(coordGroup.ReverseGroups != null)
                    {
                        uniqueFragmentCount += coordGroup.ReverseGroups.size();

                        if(!coordGroup.ReverseGroups.isEmpty())
                            ++uniqueCoordCount;
                    }
                }
            }
            else
            {
                uniqueCoordCount = duplicateGroups.size() + singleFragments.size();
                uniqueFragmentCount = finalUmiGroups.size() + singleFragments.size();
            }

            // int nonDuplicateFragCount = singleFragments.size();
            // int posGroupCount = duplicateGroups.size() + nonDuplicateFragCount; // count of unique fragments sharing the same start pos
            // int uniqueFragmentCount = nonDuplicateFragCount; // count of fragments with matching coordinates (after UMI collapsing)
            int maxUmiFragmentCount = 0;
            DuplicateGroup maxUmiGroup = null;

            for(DuplicateGroup umiGroup : finalUmiGroups)
            {
                ++mStats.UmiGroups;

                if(umiGroup.fragmentCount() > maxUmiFragmentCount)
                {
                    maxUmiGroup = umiGroup;
                    maxUmiFragmentCount = umiGroup.fragmentCount();
                }
            }

            mStats.recordFragmentPositions(uniqueCoordCount, uniqueFragmentCount, maxDuplicatePosCount, maxUmiGroup);
        }

        return finalUmiGroups;
    }

    private CoordinateGroup getOrCreateCoordGroup(final List<CoordinateGroup> coordinateGroups, final String coordKey)
    {
        for(CoordinateGroup coordinateGroup : coordinateGroups)
        {
            if(coordinateGroup.CoordKey.equals(coordKey))
                return coordinateGroup;
        }

        CoordinateGroup newGroup = new CoordinateGroup(coordKey);
        coordinateGroups.add(newGroup);
        return newGroup;
    }

    private void addUmiGroup(final List<DuplicateGroup> allUmiGroups, final List<Object> fragGroups)
    {
        if(fragGroups == null)
            return;

        for(Object fragGroup : fragGroups)
        {
            if(fragGroup instanceof DuplicateGroup)
            {
                allUmiGroups.add((DuplicateGroup) fragGroup);
            }
        }
    }

    private void collapseCoordinateGroup(final List<DuplicateGroup> allUmiGroups, final CoordinateGroup coordGroup)
    {
        if(!coordGroup.hasOpposites())
        {
            addUmiGroup(allUmiGroups, coordGroup.ForwardGroups);
            addUmiGroup(allUmiGroups, coordGroup.ReverseGroups);
            return;
        }

        for(Object first : coordGroup.ForwardGroups)
        {
            DuplicateGroup firstGroup = null;
            Fragment firstFragment = null;
            String firstUmi;

            if(first instanceof DuplicateGroup)
            {
                firstGroup = (DuplicateGroup) first;
                firstUmi = firstGroup.id();
            }
            else
            {
                firstFragment = (Fragment) first;
                firstUmi = mUmiConfig.extractUmiId(firstFragment.id());
            }

            int secondIndex = 0;
            while(secondIndex < coordGroup.ReverseGroups.size())
            {
                Object second = coordGroup.ReverseGroups.get(secondIndex);
                DuplicateGroup secondGroup = null;
                Fragment secondFragment = null;
                String secondUmi;

                if(second instanceof DuplicateGroup)
                {
                    secondGroup = (DuplicateGroup) second;
                    secondUmi = secondGroup.id();
                }
                else
                {
                    secondFragment = (Fragment) second;
                    secondUmi = mUmiConfig.extractUmiId(secondFragment.id());
                }

                boolean canCollapse = mUmiConfig.Duplex ?
                        hasDuplexUmiMatch(firstUmi, secondUmi, mUmiConfig.DuplexDelim, mUmiConfig.PermittedBaseDiff) : true;

                if(canCollapse)
                {
                    // merge the two opposing fragments / groups
                    coordGroup.ReverseGroups.remove(secondIndex);

                    if(firstGroup == null) // turn fragment into group
                    {
                        firstGroup = new DuplicateGroup(firstUmi, firstFragment);
                    }

                    if(secondGroup != null)
                    {
                        for(Fragment fragment : secondGroup.fragments())
                        {
                            firstGroup.addFragment(fragment);
                        }
                    }
                    else
                    {
                        firstGroup.addFragment(secondFragment);
                    }

                    // collapsing only occurs between a pair, not 1:M
                    break;
                }
                else
                {
                    ++secondIndex;
                }
            }

            if(firstGroup != null)
                allUmiGroups.add(firstGroup);
        }

        for(Object fragGroup : coordGroup.ReverseGroups)
        {
            if(fragGroup instanceof DuplicateGroup)
                allUmiGroups.add((DuplicateGroup)fragGroup);
        }
    }

    @VisibleForTesting
    public static boolean hasDuplexUmiMatch(final String first, final String second, final String duplexDelim, int permittedDiff)
    {
        String[] umiParts1 = first.split(duplexDelim, 2);
        String[] umiParts2 = second.split(duplexDelim, 2);

        if(umiParts1.length != 2 || umiParts2.length != 2)
            return false;

        return !exceedsUmiIdDiff(umiParts1[0], umiParts2[1], permittedDiff) && !exceedsUmiIdDiff(umiParts1[1], umiParts2[0], permittedDiff);
    }

    private void captureUmiGroupStats(final List<Object> fragGroups)
    {
        if(fragGroups == null)
            return;

        List<DuplicateGroup> groups = Lists.newArrayList();

        for(Object fragGroup : fragGroups)
        {
            if(fragGroup instanceof DuplicateGroup)
            {
                groups.add((DuplicateGroup) fragGroup);
            }
            else
            {
                Fragment fragment = (Fragment)fragGroup;
                groups.add(new DuplicateGroup(mUmiConfig.extractUmiId(fragment.id()), fragment));
            }
        }

        mStats.recordUmiBaseStats(mUmiConfig, groups);
    }

    /*
    private List<DuplicateGroup> processUmiGroupsOld(
            final List<List<Fragment>> duplicateGroups, boolean captureStats, final List<Fragment> singleFragments)
    {
        List<DuplicateGroup> allUmiGroups = Lists.newArrayList();

        int nonDuplicateFragCount = singleFragments.size();
        int posGroupCount = duplicateGroups.size() + nonDuplicateFragCount; // count of unique fragments sharing the same start pos
        int uniqueFragmentCount = nonDuplicateFragCount; // count of fragments with matching coordinates (after UMI collapsing)
        int maxDuplicatePosCount = 0;

        int maxUmiFragmentCount = 0;
        DuplicateGroup maxUmiGroup = null;

        Map<String,List<DuplicateGroup>> coordGroups = mUmiConfig.BaseStats ? Maps.newHashMap() : null;

        for(List<Fragment> fragments : duplicateGroups)
        {
            List<DuplicateGroup> umiGroups = buildUmiGroups(fragments, mUmiConfig);

            // collect stats including single groups
            if(captureStats)
            {
                if(mUmiConfig.BaseStats)
                {
                    coordGroups.put(fragments.get(0).coordinates().Key, umiGroups);
                }

                maxDuplicatePosCount = max(maxDuplicatePosCount, umiGroups.size());
            }

            allUmiGroups.addAll(umiGroups);
        }

        if(mUmiConfig.BaseStats)
        {
            // include single fragments for this analysis
            for(Fragment fragment : singleFragments)
            {
                List<DuplicateGroup> coordGroup = coordGroups.get(fragment.coordinates().Key);

                if(coordGroup == null)
                {
                    coordGroup = Lists.newArrayList();
                    coordGroups.put(fragment.coordinates().Key, coordGroup);
                }

                coordGroup.add(new DuplicateGroup(mUmiConfig.extractUmiId(fragment.id()), fragment));
            }

            for(List<DuplicateGroup> coordGroup : coordGroups.values())
            {
                if(coordGroup.size() == 1 && coordGroup.get(0).fragmentCount() == 1)
                    continue;

                mStats.recordUmiBaseStats(mUmiConfig, coordGroup);
            }
        }

        if(mUmiConfig.Duplex)
        {
            // include single fragments which may be the reverse of each other or a group
            for(Fragment fragment : singleFragments)
            {
                allUmiGroups.add(new DuplicateGroup(mUmiConfig.extractUmiId(fragment.id()), fragment));
            }

            // collapseOnDuplexMatches(allUmiGroups, mUmiConfig);
        }

        if(captureStats)
        {
            uniqueFragmentCount += allUmiGroups.size();
        }

        List<DuplicateGroup> finalUmiGroups = Lists.newArrayList();

        for(DuplicateGroup umiGroup : allUmiGroups)
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

            finalUmiGroups.add(umiGroup);

            if(captureStats)
            {
                ++mStats.UmiGroups;
            }

            if(umiGroup.fragmentCount() > maxUmiFragmentCount)
            {
                maxUmiGroup = umiGroup;
                maxUmiFragmentCount = umiGroup.fragmentCount();
            }
        }

        if(captureStats)
        {
            mStats.recordFragmentPositions(posGroupCount, uniqueFragmentCount, maxDuplicatePosCount, maxUmiGroup);
        }

        return finalUmiGroups;
    }
    */
}
