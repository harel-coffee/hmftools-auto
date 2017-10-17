package com.hartwig.hmftools.strelka;

import java.io.File;
import java.io.IOException;
import java.util.List;

import javax.xml.stream.XMLStreamException;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.hartwig.hmftools.common.exception.HartwigException;
import com.hartwig.hmftools.common.slicing.Slicer;
import com.hartwig.hmftools.common.slicing.SlicerFactory;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.variantcontext.writer.VariantContextWriter;
import htsjdk.variant.variantcontext.writer.VariantContextWriterBuilder;
import htsjdk.variant.vcf.VCFConstants;
import htsjdk.variant.vcf.VCFFileReader;
import htsjdk.variant.vcf.VCFFormatHeaderLine;
import htsjdk.variant.vcf.VCFHeader;
import htsjdk.variant.vcf.VCFHeaderLine;
import htsjdk.variant.vcf.VCFHeaderLineCount;
import htsjdk.variant.vcf.VCFHeaderLineType;

public class StrelkaPostProcessApplication {
    private static final Logger LOGGER = LogManager.getLogger(StrelkaPostProcessApplication.class);

    private static final String HIGH_CONFIDENCE_BED = "hc_bed";
    private static final String INPUT_VCF = "v";
    private static final String OUTPUT_VCF = "o";
    private static final String SAMPLE_NAME = "t";
    private static final String TUMOR_BAM = "b";

    public static void main(final String... args) throws ParseException, IOException, XMLStreamException, HartwigException {
        final Options options = createOptions();
        final CommandLine cmd = createCommandLine(options, args);

        final String highConfidenceBed = cmd.getOptionValue(HIGH_CONFIDENCE_BED);
        final String inputVcf = cmd.getOptionValue(INPUT_VCF);
        final String outputVcf = cmd.getOptionValue(OUTPUT_VCF);
        final String sampleName = cmd.getOptionValue(SAMPLE_NAME);
        final String tumorBam = cmd.getOptionValue(TUMOR_BAM);

        if (highConfidenceBed == null || inputVcf == null || outputVcf == null || sampleName == null || tumorBam == null) {
            final HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("Strelka Post Process", options);
            System.exit(1);
        }
        final Slicer highConfidenceSlicer = SlicerFactory.fromBedFile(highConfidenceBed);
        LOGGER.info("Starting strelka post process on " + inputVcf);
        processVariants(inputVcf, highConfidenceSlicer, outputVcf, sampleName, tumorBam);
    }

    @NotNull
    private static Options createOptions() {
        final Options options = new Options();
        options.addOption(HIGH_CONFIDENCE_BED, true, "Path towards the high confidence bed");
        options.addOption(INPUT_VCF, true, "Path towards the input VCF");
        options.addOption(OUTPUT_VCF, true, "Path towards the output VCF");
        options.addOption(SAMPLE_NAME, true, "Name of the sample");
        options.addOption(TUMOR_BAM, true, "Path towards the tumor bam");
        return options;
    }

    @NotNull
    private static CommandLine createCommandLine(@NotNull final Options options, @NotNull final String... args) throws ParseException {
        final CommandLineParser parser = new DefaultParser();
        return parser.parse(options, args);
    }

    private static void processVariants(@NotNull final String filePath, @NotNull final Slicer highConfidenceSlicer,
            @NotNull final String outputVcf, @NotNull final String sampleName, @NotNull final String tumorBam)
            throws IOException, HartwigException {
        final VCFFileReader vcfReader = new VCFFileReader(new File(filePath), false);
        final VCFHeader outputHeader = generateOutputHeader(vcfReader.getFileHeader(), sampleName);
        final VariantContextWriter writer = new VariantContextWriterBuilder().setOutputFile(outputVcf)
                .setReferenceDictionary(outputHeader.getSequenceDictionary())
                .build();
        writer.writeHeader(outputHeader);
        final MNVDetector detector = ImmutableMNVDetector.of(tumorBam);
        Pair<PotentialMNV, List<VariantContext>> outputPair = ImmutablePair.of(PotentialMNV.empty(), Lists.newArrayList());
        for (final VariantContext variantContext : vcfReader) {
            if (StrelkaPostProcess.checkVariant(variantContext, highConfidenceSlicer)) {
                final VariantContext simplifiedVariant = StrelkaPostProcess.simplifyVariant(variantContext, sampleName);
                final PotentialMNV potentialMNV = outputPair.getLeft();
                outputPair = detector.checkMNV(potentialMNV, simplifiedVariant);
                outputPair.getRight().forEach(writer::add);
            }
        }
        detector.mergeVariants(outputPair.getLeft()).forEach(writer::add);
        writer.close();
        vcfReader.close();
        LOGGER.info("Written output variants to " + outputVcf);
    }

    private static VCFHeader generateOutputHeader(@NotNull final VCFHeader header, @NotNull final String sampleName) {
        final VCFHeader outputVCFHeader = new VCFHeader(header.getMetaDataInInputOrder(), Sets.newHashSet(sampleName));
        outputVCFHeader.addMetaDataLine(
                new VCFFormatHeaderLine(VCFConstants.GENOTYPE_ALLELE_DEPTHS, VCFHeaderLineCount.R, VCFHeaderLineType.Integer,
                        "Allelic depths for the ref and alt alleles in the order listed"));
        outputVCFHeader.addMetaDataLine(new VCFFormatHeaderLine(VCFConstants.GENOTYPE_KEY, 1, VCFHeaderLineType.String, "Genotype"));
        outputVCFHeader.addMetaDataLine(
                new VCFHeaderLine("StrelkaGATKCompatibility", "Added GT fields to strelka calls for gatk compatibility."));
        return outputVCFHeader;
    }
}
