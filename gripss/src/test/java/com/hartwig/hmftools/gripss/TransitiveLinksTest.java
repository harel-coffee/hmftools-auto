package com.hartwig.hmftools.gripss;

import static com.hartwig.hmftools.common.utils.sv.SvCommonUtils.NEG_ORIENT;
import static com.hartwig.hmftools.common.utils.sv.SvCommonUtils.POS_ORIENT;
import static com.hartwig.hmftools.gripss.GripssTestUtils.CHR_1;
import static com.hartwig.hmftools.gripss.GripssTestUtils.buildLinkAttributes;
import static com.hartwig.hmftools.gripss.GripssTestUtils.createSgl;
import static com.hartwig.hmftools.gripss.GripssTestUtils.createSv;
import static com.hartwig.hmftools.gripss.common.VcfUtils.VT_CIPOS;
import static com.hartwig.hmftools.gripss.common.VcfUtils.VT_IMPRECISE;
import static com.hartwig.hmftools.gripss.common.VcfUtils.VT_QUAL;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;

import java.util.List;
import java.util.Map;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.hartwig.hmftools.gripss.common.SvData;
import com.hartwig.hmftools.gripss.links.AssemblyLinks;
import com.hartwig.hmftools.gripss.links.Link;
import com.hartwig.hmftools.gripss.links.LinkStore;
import com.hartwig.hmftools.gripss.links.TransitiveLinkFinder;

import org.junit.Test;

public class TransitiveLinksTest
{
    private final GripssTestApp mGripss;

    public TransitiveLinksTest()
    {
        mGripss = new GripssTestApp();
    }

    @Test
    public void testSortByQual()
    {
        Map<String, Object> tumorOverrides = Maps.newHashMap();
        tumorOverrides.put(VT_QUAL, 1);

        Map<String, Object> attributeOverrides = Maps.newHashMap();
        attributeOverrides.put(VT_CIPOS, new int[] {-10,10});
        attributeOverrides.put(VT_IMPRECISE, "true");

        SvData var1 = createSv(
                mGripss.IdGen.nextEventId(), CHR_1, CHR_1, 1000, 2000, POS_ORIENT, NEG_ORIENT, "",
                mGripss.GenotypeIds, attributeOverrides, null, tumorOverrides);

        tumorOverrides.put(VT_QUAL, 100);

        SvData var2 = createSv(
                mGripss.IdGen.nextEventId(), CHR_1, CHR_1, 1001, 2001, POS_ORIENT, NEG_ORIENT, "",
                mGripss.GenotypeIds, null, null, tumorOverrides);

        tumorOverrides.put(VT_QUAL, 1000);

        SvData var3 = createSv(
                mGripss.IdGen.nextEventId(), CHR_1, CHR_1, 1002, 2002, POS_ORIENT, NEG_ORIENT, "",
                mGripss.GenotypeIds, null, null, tumorOverrides);

        SvDataCache dataCache = new SvDataCache();

        GripssTestUtils.loadSvDataCache(dataCache, Lists.newArrayList(var1, var2, var3));

        TransitiveLinkFinder transLinkFinder = new TransitiveLinkFinder(dataCache, new LinkStore());
        List<Link> links = transLinkFinder.findTransitiveLinks(var1.breakendStart());
        assertEquals(1, links.size());
        assertEquals(var3.breakendStart(), links.get(0).breakendStart());
        assertEquals(var3.breakendEnd(), links.get(0).breakendEnd());

        //List<AlternatePath> alternatePaths = AlternatePathFinder.findPaths(dataCache, new LinkStore());
        //LinkStore transitiveLinkStore = AlternatePathFinder.createLinkStore(alternatePaths);
        // assertTrue(!transitiveLinkStore.getBreakendLinksMap().isEmpty());
    }

    @Test
    public void testDupVsInsert()
    {
        // an INS and DUP are linked as transitive candidates
        Map<String, Object> attributeOverrides = Maps.newHashMap();
        attributeOverrides.put(VT_CIPOS, new int[] { -1, 1 });

        SvData dup = createSv(
                mGripss.IdGen.nextEventId(), CHR_1, CHR_1, 1002, 1024, NEG_ORIENT, POS_ORIENT, "",
                mGripss.GenotypeIds, attributeOverrides, null, null);

        SvData ins = createSv(
                mGripss.IdGen.nextEventId(), CHR_1, CHR_1, 1000, 1001, POS_ORIENT, NEG_ORIENT, "AGAGGAAGAGTATTATTAGACAG",
                mGripss.GenotypeIds, attributeOverrides, null, null);

        SvDataCache dataCache = new SvDataCache();

        GripssTestUtils.loadSvDataCache(dataCache, Lists.newArrayList(dup, ins));

        TransitiveLinkFinder transLinkFinder = new TransitiveLinkFinder(dataCache, new LinkStore());
        assertEquals(1, transLinkFinder.findTransitiveLinks(dup.breakendStart()).size());
        assertEquals(1, transLinkFinder.findTransitiveLinks(ins.breakendStart()).size());
        assertEquals(1, transLinkFinder.findTransitiveLinks(dup.breakendEnd()).size());
        assertEquals(1, transLinkFinder.findTransitiveLinks(ins.breakendEnd()).size());
    }

    @Test
    public void testTransitiveLinksShouldNotBreakAssemblies()
    {
        Map<String, Object> attributeOverrides = Maps.newHashMap();
        attributeOverrides.put(VT_IMPRECISE, "true");

        SvData var1 = createSv(
                mGripss.IdGen.nextEventId(), CHR_1, CHR_1, 1000, 2000, POS_ORIENT, NEG_ORIENT, "",
                mGripss.GenotypeIds, attributeOverrides, null);

        attributeOverrides = buildLinkAttributes("asm1", "1");

        SvData var2 = createSv(
                mGripss.IdGen.nextEventId(), CHR_1, CHR_1, 1000, 1400, POS_ORIENT, NEG_ORIENT, "",
                mGripss.GenotypeIds, null, attributeOverrides);

        SvData var3 = createSv(
                mGripss.IdGen.nextEventId(), CHR_1, CHR_1, 1600, 2000, POS_ORIENT, NEG_ORIENT, "",
                mGripss.GenotypeIds, attributeOverrides, null);

        SvData var4 = createSgl(
                mGripss.IdGen.nextEventId(), CHR_1, 1500, POS_ORIENT, "",
                mGripss.GenotypeIds, null, null, null);

        SvDataCache dataCache = new SvDataCache();

        GripssTestUtils.loadSvDataCache(dataCache, Lists.newArrayList(var1, var2, var3, var4));

        // first without any assembled links
        TransitiveLinkFinder transLinkFinder = new TransitiveLinkFinder(dataCache, new LinkStore());
        List<Link> links = transLinkFinder.findTransitiveLinks(var1.breakendStart());
        assertEquals(3, links.size());

        LinkStore assemblyLinks = AssemblyLinks.buildAssembledLinks(Lists.newArrayList(var1, var2, var3, var4));

        transLinkFinder = new TransitiveLinkFinder(dataCache, assemblyLinks);
        assertTrue(transLinkFinder.findTransitiveLinks(var1.breakendStart()).isEmpty());
    }

    @Test
    public void testMinTransitiveLinkDistance()
    {
        Map<String, Object> attributeOverrides = Maps.newHashMap();
        attributeOverrides.put(VT_IMPRECISE, "true");

        SvData var1 = createSv(
                mGripss.IdGen.nextEventId(), CHR_1, CHR_1, 1000, 2000, POS_ORIENT, NEG_ORIENT, "",
                mGripss.GenotypeIds, attributeOverrides, null, null);

        SvData var2 = createSv(
                mGripss.IdGen.nextEventId(), CHR_1, CHR_1, 1000, 1490, POS_ORIENT, NEG_ORIENT, "",
                mGripss.GenotypeIds, null, null);

        SvData var3 = createSv(
                mGripss.IdGen.nextEventId(), CHR_1, CHR_1, 1510, 2000, POS_ORIENT, NEG_ORIENT, "",
                mGripss.GenotypeIds, attributeOverrides, null);

        SvDataCache dataCache = new SvDataCache();

        GripssTestUtils.loadSvDataCache(dataCache, Lists.newArrayList(var1, var2, var3));

        TransitiveLinkFinder transLinkFinder = new TransitiveLinkFinder(dataCache, new LinkStore());
        assertTrue(transLinkFinder.findTransitiveLinks(var1.breakendStart()).isEmpty());
    }

    @Test
    public void testAllowALittleBuffer()
    {
        Map<String, Object> attributeOverrides = Maps.newHashMap();
        attributeOverrides.put(VT_IMPRECISE, "true");
        attributeOverrides.put(VT_CIPOS, new int[] {-270,270});

        SvData var1 = createSv(
                mGripss.IdGen.nextEventId(), CHR_1, CHR_1, 565345, 580840, NEG_ORIENT, POS_ORIENT, "",
                mGripss.GenotypeIds, attributeOverrides, null, null);

        SvData var2 = createSv(
                mGripss.IdGen.nextEventId(), CHR_1, CHR_1, 565616, 567359, NEG_ORIENT, POS_ORIENT, "",
                mGripss.GenotypeIds, null, null);

        SvData var3 = createSv(
                mGripss.IdGen.nextEventId(), CHR_1, CHR_1, 567170, 580634, NEG_ORIENT, POS_ORIENT, "TAGTAGAGTTGCTTGTACTTGG",
                mGripss.GenotypeIds, null, null);

        SvDataCache dataCache = new SvDataCache();

        GripssTestUtils.loadSvDataCache(dataCache, Lists.newArrayList(var1, var2, var3));

        TransitiveLinkFinder transLinkFinder = new TransitiveLinkFinder(dataCache, new LinkStore());
        List<Link> links = transLinkFinder.findTransitiveLinks(var1.breakendStart());
        assertEquals(3, links.size());
    }

    @Test
    public void testBreathFirstSearch()
    {
        Map<String, Object> attributeOverrides = Maps.newHashMap();
        attributeOverrides.put(VT_IMPRECISE, "true");

        Map<String, Object> tumorOverrides = Maps.newHashMap();
        tumorOverrides.put(VT_QUAL, 1);

        SvData var1 = createSv(
                mGripss.IdGen.nextEventId(), CHR_1, CHR_1, 1000, 2000, NEG_ORIENT, POS_ORIENT, "",
                mGripss.GenotypeIds, attributeOverrides, null, null);

        SvData var2 = createSv(
                mGripss.IdGen.nextEventId(), CHR_1, CHR_1, 1000, 2000, NEG_ORIENT, POS_ORIENT, "",
                mGripss.GenotypeIds, null, null, tumorOverrides);

        SvData var3 = createSv(
                mGripss.IdGen.nextEventId(), CHR_1, CHR_1, 1000, 1400, NEG_ORIENT, POS_ORIENT, "",
                mGripss.GenotypeIds, null, null);

        SvData var4 = createSv(
                mGripss.IdGen.nextEventId(), CHR_1, CHR_1, 1600, 2000, NEG_ORIENT, POS_ORIENT, "",
                mGripss.GenotypeIds, null, null);

        SvData var5 = createSgl(
                mGripss.IdGen.nextEventId(), CHR_1, 1500, POS_ORIENT, "",
                mGripss.GenotypeIds, null, null, null);

        SvDataCache dataCache = new SvDataCache();

        GripssTestUtils.loadSvDataCache(dataCache, Lists.newArrayList(var1, var2, var3, var4, var5));

        // first without any assembled links
        TransitiveLinkFinder transLinkFinder = new TransitiveLinkFinder(dataCache, new LinkStore());
        List<Link> links = transLinkFinder.findTransitiveLinks(var1.breakendStart());
        assertEquals(1, links.size());
    }

    @Test
    public void testHightestQualFirst()
    {
        Map<String, Object> attributeOverrides = Maps.newHashMap();
        attributeOverrides.put(VT_IMPRECISE, "true");

        Map<String, Object> tumorOverrides = Maps.newHashMap();
        tumorOverrides.put(VT_QUAL, 1);

        SvData var1 = createSv(
                mGripss.IdGen.nextEventId(), CHR_1, CHR_1, 1000, 2000, NEG_ORIENT, POS_ORIENT, "",
                mGripss.GenotypeIds, attributeOverrides, null, null);

        SvData var2 = createSv(
                mGripss.IdGen.nextEventId(), CHR_1, CHR_1, 1000, 2000, NEG_ORIENT, POS_ORIENT, "",
                mGripss.GenotypeIds, null, null, tumorOverrides);

        SvData var3 = createSv(
                mGripss.IdGen.nextEventId(), CHR_1, CHR_1, 1000, 2000, NEG_ORIENT, POS_ORIENT, "",
                mGripss.GenotypeIds, null, null);

        SvDataCache dataCache = new SvDataCache();

        GripssTestUtils.loadSvDataCache(dataCache, Lists.newArrayList(var1, var2, var3));

        TransitiveLinkFinder transLinkFinder = new TransitiveLinkFinder(dataCache, new LinkStore());
        List<Link> links = transLinkFinder.findTransitiveLinks(var1.breakendStart());
        assertEquals(1, links.size());
        assertEquals(var3.breakendStart(), links.get(0).breakendStart());
        assertEquals(var3.breakendEnd(), links.get(0).breakendEnd());
    }

    @Test
    public void testOneTransitiveLinkOnly()
    {
        Map<String, Object> attributeOverrides = Maps.newHashMap();
        attributeOverrides.put(VT_IMPRECISE, "true");

        SvData var1 = createSv(
                mGripss.IdGen.nextEventId(), CHR_1, CHR_1, 1000, 5000, POS_ORIENT, NEG_ORIENT, "",
                mGripss.GenotypeIds, attributeOverrides, null, null);

        SvData var2 = createSv(
                mGripss.IdGen.nextEventId(), CHR_1, CHR_1, 1000, 2000, POS_ORIENT, NEG_ORIENT, "",
                mGripss.GenotypeIds, null, null);

        SvData var3 = createSv(
                mGripss.IdGen.nextEventId(), CHR_1, CHR_1, 4000, 5000, POS_ORIENT, NEG_ORIENT, "",
                mGripss.GenotypeIds, null, null);

        SvData var4 = createSv(
                mGripss.IdGen.nextEventId(), CHR_1, CHR_1, 2500, 3500, POS_ORIENT, NEG_ORIENT, "",
                mGripss.GenotypeIds, null, null);

        SvData var5 = createSv(
                mGripss.IdGen.nextEventId(), CHR_1, CHR_1, 2500, 3500, POS_ORIENT, NEG_ORIENT, "",
                mGripss.GenotypeIds, null, null);

        SvDataCache dataCache = new SvDataCache();

        GripssTestUtils.loadSvDataCache(dataCache, Lists.newArrayList(var1, var2, var3, var4));

        TransitiveLinkFinder transLinkFinder = new TransitiveLinkFinder(dataCache, new LinkStore());
        assertTrue(!transLinkFinder.findTransitiveLinks(var1.breakendStart()).isEmpty());

        GripssTestUtils.loadSvDataCache(dataCache, Lists.newArrayList(var1, var2, var3, var5));
        assertTrue(!transLinkFinder.findTransitiveLinks(var1.breakendStart()).isEmpty());

        GripssTestUtils.loadSvDataCache(dataCache, Lists.newArrayList(var1, var2, var3, var4, var5));
        assertTrue(transLinkFinder.findTransitiveLinks(var1.breakendStart()).isEmpty());
    }
}
