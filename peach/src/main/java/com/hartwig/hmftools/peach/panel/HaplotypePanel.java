package com.hartwig.hmftools.peach.panel;

import com.google.common.collect.Sets;
import com.hartwig.hmftools.common.genome.chromosome.Chromosome;
import com.hartwig.hmftools.peach.event.HaplotypeEvent;
import com.hartwig.hmftools.peach.event.HaplotypeEventFactory;
import com.hartwig.hmftools.peach.haplotype.NonDefaultHaplotype;
import com.hartwig.hmftools.peach.haplotype.DefaultHaplotype;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class HaplotypePanel
{
    @NotNull
    private final Map<String, GeneHaplotypePanel> geneToGeneHaplotypePanel;

    public HaplotypePanel(@NotNull Map<String, GeneHaplotypePanel> geneToGeneHaplotypePanel)
    {
        this.geneToGeneHaplotypePanel = geneToGeneHaplotypePanel;
    }

    public Map<Chromosome, Set<Integer>> getRelevantVariantPositions()
    {
        return geneToGeneHaplotypePanel.values().stream()
                .map(GeneHaplotypePanel::getRelevantVariantPositions)
                .flatMap(m -> m.entrySet().stream())
                .collect(
                        Collectors.groupingBy(
                                Map.Entry::getKey,
                                Collectors.mapping(Map.Entry::getValue, Collectors.flatMapping(Collection::stream, Collectors.toSet()))
                        )
                );
    }

    public boolean isRelevantFor(String eventId, String gene)
    {
        return isRelevantFor(HaplotypeEventFactory.fromId(eventId), gene);
    }

    public boolean isRelevantFor(HaplotypeEvent event, String gene)
    {
        return geneToGeneHaplotypePanel.get(gene).isRelevantFor(event);
    }

    public List<NonDefaultHaplotype> getNonDefaultHaplotypes(String gene)
    {
        return geneToGeneHaplotypePanel.get(gene).nonDefaultHaplotypes;
    }
    public DefaultHaplotype getDefaultHaplotype(String gene)
    {
        return geneToGeneHaplotypePanel.get(gene).defaultHaplotype;
    }

    public String getWildTypeHaplotypeName(String gene)
    {
        return geneToGeneHaplotypePanel.get(gene).wildTypeHaplotypeName;
    }

    public Set<String> getGenes()
    {
        return Sets.newHashSet(geneToGeneHaplotypePanel.keySet());
    }

    public int getHaplotypeCount()
    {
        return geneToGeneHaplotypePanel.values().stream()
                .mapToInt(GeneHaplotypePanel::getHaplotypeCount)
                .sum();
    }
}
