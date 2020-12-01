package com.hartwig.hmftools.serve.sources.vicc.extractor;

import java.util.List;
import java.util.Map;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;
import com.hartwig.hmftools.common.drivercatalog.DriverCategory;
import com.hartwig.hmftools.common.drivercatalog.panel.DriverGene;
import com.hartwig.hmftools.common.genome.region.HmfTranscriptRegion;
import com.hartwig.hmftools.common.serve.classification.MutationType;
import com.hartwig.hmftools.serve.actionability.gene.GeneLevelEvent;
import com.hartwig.hmftools.serve.sources.vicc.annotation.GeneLevelAnnotation;
import com.hartwig.hmftools.serve.sources.vicc.annotation.ImmutableGeneLevelAnnotation;
import com.hartwig.hmftools.serve.sources.vicc.check.GeneChecker;
import com.hartwig.hmftools.vicc.annotation.ViccClassificationConfig;
import com.hartwig.hmftools.vicc.datamodel.Feature;
import com.hartwig.hmftools.vicc.datamodel.ViccEntry;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GeneLevelExtractor {

    private static final Logger LOGGER = LogManager.getLogger(GeneLevelExtractor.class);

    @NotNull
    private final Map<String, HmfTranscriptRegion> transcriptPerGeneMap;
    @NotNull
    private final List<DriverGene> driverGenes;
    @NotNull
    private final GeneChecker geneChecker;

    public GeneLevelExtractor(@NotNull final Map<String, HmfTranscriptRegion> transcriptPerGeneMap,
            @NotNull final List<DriverGene> driverGenes, @NotNull final GeneChecker geneChecker) {
        this.transcriptPerGeneMap = transcriptPerGeneMap;
        this.driverGenes = driverGenes;
        this.geneChecker = geneChecker;
    }

    @NotNull
    public Map<Feature, GeneLevelAnnotation> extractGeneLevelEvents(@NotNull ViccEntry viccEntry) {
        Map<Feature, GeneLevelAnnotation> geneLevelEventsPerFeature = Maps.newHashMap();

        for (Feature feature : viccEntry.features()) {
            HmfTranscriptRegion canonicalTranscript = transcriptPerGeneMap.get(feature.geneSymbol());
            if (feature.type() == MutationType.GENE_LEVEL) {
                if (geneChecker.isValidGene(feature.geneSymbol(), canonicalTranscript, feature.name(), null)) {
                    GeneLevelEvent event = extractGeneLevelEvent(feature, driverGenes);
                    if (event != null) {
                        geneLevelEventsPerFeature.put(feature,
                                ImmutableGeneLevelAnnotation.builder()
                                        .gene(feature.geneSymbol())
                                        .event(event)
                                        .build());
                    }
                }
            } else if (feature.type() == MutationType.PROMISCUOUS_FUSION) {
                if (geneChecker.isValidGene(feature.geneSymbol(), canonicalTranscript, feature.name(), "fusion")) {
                    geneLevelEventsPerFeature.put(feature,
                            ImmutableGeneLevelAnnotation.builder().gene(feature.geneSymbol()).event(GeneLevelEvent.FUSION).build());
                }
            }
        }
        return geneLevelEventsPerFeature;
    }

    @Nullable
    @VisibleForTesting
    static GeneLevelEvent extractGeneLevelEvent(@NotNull Feature feature, @NotNull List<DriverGene> driverGenes) {
        String event = feature.name().trim();
        String gene = feature.geneSymbol();

        if (event.contains(" ")) {
            String firstWord = event.split(" ")[0];
            if (firstWord.equals(gene)) {
                event = event.split(" ", 2)[1].trim();
            }
        }

        if (ViccClassificationConfig.INACTIVATING_GENE_LEVEL_KEY_PHRASES.contains(event)) {
            return GeneLevelEvent.INACTIVATION;
        } else if (ViccClassificationConfig.ACTIVATING_GENE_LEVEL_KEY_PHRASES.contains(event)) {
            return GeneLevelEvent.ACTIVATION;
        } else if (ViccClassificationConfig.GENERIC_GENE_LEVEL_KEY_PHRASES.contains(event) || gene.equals(event.replaceAll("\\s+", ""))) {
            return determineGeneLevelEventFromDriverGenes(gene, driverGenes);
        }

        LOGGER.warn("Could not determine gene level event for '{}' on '{}'", feature.name(), gene);
        return null;
    }

    @VisibleForTesting
    @NotNull
    static GeneLevelEvent determineGeneLevelEventFromDriverGenes(@NotNull String gene, @NotNull List<DriverGene> driverGenes) {
        for (DriverGene driverGene : driverGenes) {
            if (driverGene.gene().equals(gene)) {
                if (driverGene.likelihoodType() == DriverCategory.ONCO) {
                    return GeneLevelEvent.ACTIVATION;
                } else if (driverGene.likelihoodType() == DriverCategory.TSG) {
                    return GeneLevelEvent.INACTIVATION;
                }
            }
        }
        return GeneLevelEvent.ANY_MUTATION;
    }
}
