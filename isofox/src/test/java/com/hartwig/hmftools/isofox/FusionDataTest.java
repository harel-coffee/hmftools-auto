package com.hartwig.hmftools.isofox;

import static com.hartwig.hmftools.common.ensemblcache.GeneTestUtils.createGeneDataCache;
import static com.hartwig.hmftools.isofox.FusionFragmentsTest.CHR_1;
import static com.hartwig.hmftools.isofox.FusionFragmentsTest.GENE_ID_1;
import static com.hartwig.hmftools.isofox.FusionFragmentsTest.GENE_ID_2;
import static com.hartwig.hmftools.isofox.FusionFragmentsTest.addTestGenes;
import static com.hartwig.hmftools.isofox.FusionFragmentsTest.addTestTranscripts;
import static com.hartwig.hmftools.isofox.FusionFragmentsTest.createGeneCollection;
import static com.hartwig.hmftools.isofox.FusionFragmentsTest.createMappedRead;
import static com.hartwig.hmftools.isofox.ReadCountsTest.createCigar;

import static junit.framework.TestCase.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.hartwig.hmftools.common.ensemblcache.EnsemblDataCache;
import com.hartwig.hmftools.common.ensemblcache.EnsemblGeneData;
import com.hartwig.hmftools.isofox.common.GeneCollection;
import com.hartwig.hmftools.isofox.common.ReadRecord;
import com.hartwig.hmftools.isofox.fusion.FusionFinder;
import com.hartwig.hmftools.isofox.fusion.FusionFragment;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.junit.Test;

public class FusionDataTest
{
    @Test
    public void testInvalidFusions()
    {
        Configurator.setRootLevel(Level.DEBUG);

        final EnsemblDataCache geneTransCache = createGeneDataCache();

        addTestGenes(geneTransCache);
        addTestTranscripts(geneTransCache);

        IsofoxConfig config = new IsofoxConfig();

        FusionFinder finder = new FusionFinder(config, geneTransCache);

        int gcId = 0;

        final GeneCollection gc1 = createGeneCollection(geneTransCache, gcId++, Lists.newArrayList(geneTransCache.getGeneDataById(GENE_ID_1)));
        final GeneCollection gc2 = createGeneCollection(geneTransCache, gcId++, Lists.newArrayList(geneTransCache.getGeneDataById(GENE_ID_2)));

        final Map<Integer,List<EnsemblGeneData>> gcMap = Maps.newHashMap();

        gcMap.put(gc1.id(), gc1.genes().stream().map(x -> x.GeneData).collect(Collectors.toList()));
        gcMap.put(gc2.id(), gc2.genes().stream().map(x -> x.GeneData).collect(Collectors.toList()));

        finder.addChromosomeGeneCollections(CHR_1, gcMap);

        // a DEL within the same gene collection is invalid
        ReadRecord read1 = createMappedRead(1, gc1, 1081, 1100, createCigar(0, 20, 20));
        ReadRecord read2 = createMappedRead(1, gc1, 1200, 1219, createCigar(20, 20, 0));

        final Map<String,List<ReadRecord>> chimericReadMap = Maps.newHashMap();
        chimericReadMap.put(read1.Id, Lists.newArrayList(read1, read2));
        finder.addChimericReads(chimericReadMap);

        finder.findFusions();

        assertTrue(finder.getFusionCandidates().isEmpty());

    }



}
