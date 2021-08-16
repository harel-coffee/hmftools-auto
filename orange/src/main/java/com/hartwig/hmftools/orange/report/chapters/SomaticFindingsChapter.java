package com.hartwig.hmftools.orange.report.chapters;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.common.collect.Maps;
import com.hartwig.hmftools.common.genome.chromosome.HumanChromosome;
import com.hartwig.hmftools.common.protect.ProtectEvidence;
import com.hartwig.hmftools.common.purple.copynumber.CopyNumberInterpretation;
import com.hartwig.hmftools.common.purple.copynumber.ReportableGainLoss;
import com.hartwig.hmftools.common.sv.linx.LinxFusion;
import com.hartwig.hmftools.common.variant.ReportableVariant;
import com.hartwig.hmftools.common.variant.ReportableVariantFactory;
import com.hartwig.hmftools.common.variant.ReportableVariantSource;
import com.hartwig.hmftools.common.variant.SomaticVariant;
import com.hartwig.hmftools.orange.algo.OrangeReport;
import com.hartwig.hmftools.orange.report.ReportResources;
import com.hartwig.hmftools.orange.report.tables.FusionTable;
import com.hartwig.hmftools.orange.report.tables.GeneCopyNumberTable;
import com.hartwig.hmftools.orange.report.tables.GeneDisruptionTable;
import com.hartwig.hmftools.orange.report.tables.HomozygousDisruptionTable;
import com.hartwig.hmftools.orange.report.tables.SomaticVariantTable;
import com.hartwig.hmftools.orange.report.tables.ViralPresenceTable;
import com.hartwig.hmftools.orange.report.util.ImageUtil;
import com.hartwig.hmftools.orange.report.util.LocationKey;
import com.hartwig.hmftools.orange.report.util.TableUtil;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.property.HorizontalAlignment;

import org.apache.commons.compress.utils.Lists;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SomaticFindingsChapter implements ReportChapter {

    @NotNull
    private final OrangeReport report;

    public SomaticFindingsChapter(@NotNull final OrangeReport report) {
        this.report = report;
    }

    @NotNull
    @Override
    public String name() {
        return "Somatic Findings";
    }

    @NotNull
    @Override
    public PageSize pageSize() {
        return PageSize.A4;
    }

    @Override
    public void render(@NotNull final Document document) {
        document.add(new Paragraph(name()).addStyle(ReportResources.chapterTitleStyle()));

        addSomaticVariants(document);
        addKataegisPlot(document);
        addSomaticAmpDels(document);
        addFusions(document);
        addViralPresence(document);
        addHomozygousDisruptions(document);
        addGeneDisruptions(document);
        addLinxDriverPlots(document);
    }

    private void addSomaticVariants(@NotNull Document document) {
        String driverVariantTableTitle = "Driver variants (" + report.purple().reportableSomaticVariants().size() + ")";
        Table driverVariantTable = SomaticVariantTable.build(driverVariantTableTitle, contentWidth(), report.purple().reportableSomaticVariants());
        document.add(driverVariantTable);

        List<ReportableVariant> otherVariants = otherInterestingVariants(report.purple().unreportedSomaticVariants(), report.protect());
        String otherVariantTableTitle = "Other potentially relevant coding variants (" + otherVariants.size() + ")";
        Table otherVariantTable = SomaticVariantTable.build(otherVariantTableTitle, contentWidth(), otherVariants);
        document.add(otherVariantTable);
    }

    @NotNull
    private static List<ReportableVariant> otherInterestingVariants(@NotNull List<SomaticVariant> variants,
            @NotNull List<ProtectEvidence> evidences) {
        List<ReportableVariant> filtered = Lists.newArrayList();
        for (SomaticVariant variant : variants) {
            if (!variant.reported()) {
                if (variant.isHotspot() || hasReportedVariantWithPhase(variants, variant.localPhaseSet()) || hasEvidence(evidences,
                        variant.genomicEvent())) {
                    filtered.add(toReportable(variant));
                }
            }
        }
        return filtered;
    }

    private static boolean hasReportedVariantWithPhase(@NotNull List<SomaticVariant> variants, @Nullable Integer targetPhaseSet) {
        if (targetPhaseSet == null) {
            return false;
        }

        for (SomaticVariant variant : variants) {
            if (variant.reported() && variant.localPhaseSet() != null && variant.localPhaseSet().equals(targetPhaseSet)) {
                return true;
            }
        }

        return false;
    }

    @NotNull
    private static ReportableVariant toReportable(@NotNull SomaticVariant variant) {
        return ReportableVariantFactory.fromVariant(variant, ReportableVariantSource.SOMATIC).driverLikelihood(Double.NaN).build();
    }

    private void addKataegisPlot(@NotNull Document document) {
        document.add(new Paragraph("Kataegis plot").addStyle(ReportResources.tableTitleStyle()));
        if (report.plots().purpleKataegisPlot() != null) {
            Image image = ImageUtil.build(report.plots().purpleKataegisPlot());
            image.setMaxWidth(contentWidth());
            image.setHorizontalAlignment(HorizontalAlignment.CENTER);
            document.add(image);
        } else {
            document.add(new Paragraph("No kataegis plot could be generated for this sample").addStyle(ReportResources.tableContentStyle()));
        }
    }

    private void addSomaticAmpDels(@NotNull Document document) {
        String driverAmpsDelsTitle = "Driver amps/dels (" + report.purple().reportableGainsLosses().size() + ")";
        Table driverAmpsDelsTable = GeneCopyNumberTable.build(driverAmpsDelsTitle, contentWidth(), report.purple().reportableGainsLosses());
        document.add(driverAmpsDelsTable);

        List<ReportableGainLoss> sortedGains = otherRelevantGains(report.purple().unreportedGainsLosses());
        String sortedGainsTitle = "Other regions with amps (" + sortedGains.size() + ")";
        Table sortedGainsTable =
                GeneCopyNumberTable.build(sortedGainsTitle, contentWidth(), sortedGains.subList(0, Math.min(10, sortedGains.size())));
        document.add(sortedGainsTable);

        List<ReportableGainLoss> lossesNoAllosomes =
                otherRelevantLosses(report.purple().unreportedGainsLosses(), report.purple().reportableGainsLosses());
        String unreportedLossesTitle = "Other autosomal regions with dels (" + lossesNoAllosomes.size() + ")";
        Table unreportedLossesTable = GeneCopyNumberTable.build(unreportedLossesTitle,
                contentWidth(),
                lossesNoAllosomes.subList(0, Math.min(10, lossesNoAllosomes.size())));
        document.add(unreportedLossesTable);
    }

    @NotNull
    private static List<ReportableGainLoss> otherRelevantGains(@NotNull List<ReportableGainLoss> unreportedGainsLosses) {
        List<ReportableGainLoss> unreportedFullGains = unreportedGainsLosses.stream()
                .filter(gainLoss -> gainLoss.interpretation() == CopyNumberInterpretation.FULL_GAIN)
                .collect(Collectors.toList());

        Map<LocationKey, ReportableGainLoss> maxGainPerLocation = Maps.newHashMap();
        for (ReportableGainLoss gain : unreportedFullGains) {
            LocationKey key = new LocationKey(gain.chromosome(), gain.chromosomeBand());
            ReportableGainLoss maxGain = maxGainPerLocation.get(key);
            if (maxGain == null || gain.copies() > maxGain.copies()) {
                maxGainPerLocation.put(key, gain);
            }
        }

        return maxGainPerLocation.values().stream().sorted((o1, o2) -> (int) (o2.copies() - o1.copies())).collect(Collectors.toList());
    }

    @NotNull
    private static List<ReportableGainLoss> otherRelevantLosses(@NotNull List<ReportableGainLoss> unreportedGainsLosses,
            @NotNull List<ReportableGainLoss> reportedGainLosses) {
        List<ReportableGainLoss> lossesNoAllosomes = Lists.newArrayList();

        List<ReportableGainLoss> unreportedLosses = unreportedGainsLosses.stream()
                .filter(gainLoss -> gainLoss.interpretation() == CopyNumberInterpretation.PARTIAL_LOSS
                        || gainLoss.interpretation() == CopyNumberInterpretation.FULL_LOSS)
                .collect(Collectors.toList());

        List<ReportableGainLoss> reportedLosses = reportedGainLosses.stream()
                .filter(gainLoss -> gainLoss.interpretation() == CopyNumberInterpretation.PARTIAL_LOSS
                        || gainLoss.interpretation() == CopyNumberInterpretation.FULL_LOSS)
                .collect(Collectors.toList());

        for (ReportableGainLoss loss : unreportedLosses) {
            if (!HumanChromosome.fromString(loss.chromosome()).isAllosome() && !locusPresent(reportedLosses,
                    loss.chromosome(),
                    loss.chromosomeBand())) {
                lossesNoAllosomes.add(loss);
            }
        }

        Map<LocationKey, ReportableGainLoss> oneLossPerLocation = Maps.newHashMap();
        for (ReportableGainLoss loss : lossesNoAllosomes) {
            LocationKey key = new LocationKey(loss.chromosome(), loss.chromosomeBand());
            if (!oneLossPerLocation.containsKey(key)) {
                oneLossPerLocation.put(key, loss);
            }
        }

        return Lists.newArrayList(oneLossPerLocation.values().iterator());
    }

    private static boolean locusPresent(@NotNull List<ReportableGainLoss> gainsLosses, @NotNull String chromosome,
            @NotNull String chromosomeBand) {
        for (ReportableGainLoss gainLoss : gainsLosses) {
            if (gainLoss.chromosome().equals(chromosome) && gainLoss.chromosomeBand().equals(chromosomeBand)) {
                return true;
            }
        }

        return false;
    }

    private void addFusions(@NotNull Document document) {
        String driverFusionTableTitle = "Driver fusions (" + report.linx().reportableFusions().size() + ")";
        Table driverFusionTable = FusionTable.build(driverFusionTableTitle, contentWidth(), report.linx().reportableFusions());
        document.add(driverFusionTable);

        List<LinxFusion> otherFusions = otherInterestingFusions(report.linx().unreportedFusions(), report.protect());
        String otherFusionTableTitle = "Other potentially interesting fusions (" + otherFusions.size() + ")";
        Table otherFusionTable =
                FusionTable.build(otherFusionTableTitle, contentWidth(), otherFusions.subList(0, Math.min(10, otherFusions.size())));
        document.add(otherFusionTable);
    }

    @NotNull
    private static List<LinxFusion> otherInterestingFusions(@NotNull List<LinxFusion> fusions, @NotNull List<ProtectEvidence> evidences) {
        List<LinxFusion> filtered = Lists.newArrayList();
        for (LinxFusion fusion : fusions) {
            if (!fusion.reportedType().equals("NONE") || hasEvidence(evidences, fusion.genomicEvent())) {
                filtered.add(fusion);
            }
        }
        return filtered;
    }

    private static boolean hasEvidence(@NotNull List<ProtectEvidence> evidences, @NotNull String genomicEvent) {
        for (ProtectEvidence evidence : evidences) {
            if (evidence.genomicEvent().equals(genomicEvent)) {
                return true;
            }
        }

        return false;
    }

    private void addViralPresence(@NotNull Document document) {
        String driverVirusTitle = "Driver viruses (" + report.virusInterpreter().reportableViruses().size() + ")";
        Table driverVirusTable = ViralPresenceTable.build(driverVirusTitle, contentWidth(), report.virusInterpreter().reportableViruses());
        document.add(driverVirusTable);

        String otherVirusTitle = "Other viral presence (" + report.virusInterpreter().unreportedViruses().size() + ")";
        Table otherVirusTable = ViralPresenceTable.build(otherVirusTitle, contentWidth(), report.virusInterpreter().unreportedViruses());
        document.add(otherVirusTable);
    }

    private void addHomozygousDisruptions(@NotNull Document document) {
        String homozygousDisruptionTitle = "Homozygous disruptions (" + report.linx().homozygousDisruptions().size() + ")";
        Table homozygousDisruptionTable =
                HomozygousDisruptionTable.build(homozygousDisruptionTitle, contentWidth(), report.linx().homozygousDisruptions());
        document.add(homozygousDisruptionTable);
    }

    private void addGeneDisruptions(@NotNull Document document) {
        String geneDisruptionTitle = "Gene disruptions (" + report.linx().geneDisruptions().size() + ")";
        Table geneDisruptionTable = GeneDisruptionTable.build(geneDisruptionTitle, contentWidth(), report.linx().geneDisruptions());
        document.add(geneDisruptionTable);
    }

    private void addLinxDriverPlots(@NotNull Document document) {
        document.add(new Paragraph("Linx driver plots").addStyle(ReportResources.tableTitleStyle()));
        Table table = new Table(2);
        for (String plot : report.plots().linxDriverPlots()) {
            Image image = ImageUtil.build(plot);
            image.setMaxWidth(Math.round(contentWidth() / 2D) - 2);
            image.setHorizontalAlignment(HorizontalAlignment.CENTER);
            table.addCell(TableUtil.createImageCell(image));
        }

        if (report.plots().linxDriverPlots().size() % 2 == 1) {
            table.addCell(TableUtil.createContentCell(""));
        }

        document.add(table);
    }
}
