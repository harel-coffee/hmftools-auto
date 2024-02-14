package com.hartwig.hmftools.peach;

import static com.hartwig.hmftools.peach.TestUtils.getTestResourcePath;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.hartwig.hmftools.common.utils.config.ConfigBuilder;

import org.junit.Test;

public class PeachConfigTest
{
    @Test
    public void testMinimalCommandLineArguments()
    {
        String vcfFile = getTestResourcePath("variants.vcf.gz");
        String haplotypesFile = getTestResourcePath("haplotypes.complicated.37.tsv");
        String sampleName = "FAKENAME";
        String outputDir = "/path/to/output";

        String[] args = {
                "-vcf_file", vcfFile,
                "-haplotypes_file", haplotypesFile,
                "-sample_name", sampleName,
                "-output_dir", outputDir
        };

        PeachConfig config = constructPeachConfigFromArgs(args);

        assertEquals(vcfFile, config.vcfFile);
        assertEquals(haplotypesFile, config.haplotypesFile);
        assertEquals(sampleName, config.sampleName);
        assertEquals(outputDir + "/", config.outputDir);

        assertTrue(config.isValid());

        assertEquals("/path/to/output/FAKENAME.peach.events.tsv", config.getEventsOutputPath());
        assertEquals("/path/to/output/FAKENAME.peach.gene.events.tsv", config.getEventsPerGeneOutputPath());
        assertEquals("/path/to/output/FAKENAME.peach.haplotypes.all.tsv", config.getAllHaplotypeCombinationsOutputPath());
        assertEquals("/path/to/output/FAKENAME.peach.haplotypes.best.tsv", config.getBestHaplotypeCombinationsOutputPath());
        assertEquals("/path/to/output/FAKENAME.peach.qc.tsv", config.getQcStatusOutputPath());
    }

    @Test
    public void testMaximalCommandLineArguments()
    {
        String vcfFile = getTestResourcePath("variants.vcf.gz");
        String haplotypesFile = getTestResourcePath("haplotypes.complicated.37.tsv");
        String drugsFile = getTestResourcePath("drugs.tsv");
        String functionalityFile = getTestResourcePath("functionality.tsv");
        String sampleName = "FAKENAME";
        String outputDir = "/path/to/output";

        String[] args = {
                "-vcf_file", vcfFile,
                "-haplotypes_file", haplotypesFile,
                "-sample_name", sampleName,
                "-output_dir", outputDir,
                "-drugs_file", drugsFile,
                "-functionality_file", functionalityFile
        };

        PeachConfig config = constructPeachConfigFromArgs(args);

        assertEquals(vcfFile, config.vcfFile);
        assertEquals(haplotypesFile, config.haplotypesFile);
        assertEquals(sampleName, config.sampleName);
        assertEquals(outputDir + "/", config.outputDir);
        assertEquals(drugsFile, config.drugsFile);
        assertEquals(functionalityFile, config.functionalityFile);

        assertTrue(config.isValid());

        assertEquals("/path/to/output/FAKENAME.peach.events.tsv", config.getEventsOutputPath());
        assertEquals("/path/to/output/FAKENAME.peach.gene.events.tsv", config.getEventsPerGeneOutputPath());
        assertEquals("/path/to/output/FAKENAME.peach.haplotypes.all.tsv", config.getAllHaplotypeCombinationsOutputPath());
        assertEquals("/path/to/output/FAKENAME.peach.haplotypes.best.tsv", config.getBestHaplotypeCombinationsOutputPath());
        assertEquals("/path/to/output/FAKENAME.peach.qc.tsv", config.getQcStatusOutputPath());
    }

    private static PeachConfig constructPeachConfigFromArgs(final String[] args)
    {
        ConfigBuilder configBuilder = new ConfigBuilder("Peach");
        PeachConfig.addOptions(configBuilder);
        configBuilder.checkAndParseCommandLine(args);
        return new PeachConfig(configBuilder);
    }
}
