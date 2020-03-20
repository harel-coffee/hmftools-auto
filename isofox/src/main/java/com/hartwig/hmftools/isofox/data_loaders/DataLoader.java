package com.hartwig.hmftools.isofox.data_loaders;

import static com.hartwig.hmftools.common.utils.io.FileWriterUtils.closeBufferedWriter;
import static com.hartwig.hmftools.common.utils.io.FileWriterUtils.createBufferedWriter;
import static com.hartwig.hmftools.isofox.IsofoxConfig.ISF_LOGGER;
import static com.hartwig.hmftools.isofox.IsofoxConfig.LOG_DEBUG;
import static com.hartwig.hmftools.isofox.data_loaders.DataLoadType.SUMMARY;
import static com.hartwig.hmftools.isofox.data_loaders.DataLoadType.getFileId;
import static com.hartwig.hmftools.isofox.results.ResultsWriter.SUMMARY_FILE;
import static com.hartwig.hmftools.isofox.results.SummaryStats.loadFile;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.isofox.results.SummaryStats;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.jetbrains.annotations.NotNull;

public class DataLoader
{
    private final DataLoaderConfig mConfig;

    public DataLoader(final DataLoaderConfig config)
    {
        mConfig = config;
    }

    public boolean load()
    {
        for(DataLoadType type : mConfig.LoadTypes)
        {
            switch(type)
            {
                case SUMMARY:
                    loadSummaryData();
                    break;

                default:
                    break;
            }
        }

        return true;
    }

    private void loadSummaryData()
    {
        final List<Path> filenames = Lists.newArrayList();

        if(!formSampleFilenames(SUMMARY, filenames))
            return;

        try
        {
            // for now write out a single consolidated file
            final List<SummaryStats> summaryStats = filenames.stream().map(x -> loadFile(x))
                    .filter(x -> x != null)
                    .collect(Collectors.toList());

            if(summaryStats.size() != mConfig.SampleIds.size())
                return;

            final String outputFileName = mConfig.formCohortFilename(SUMMARY_FILE);
            final BufferedWriter writer = createBufferedWriter(outputFileName, false);

            writer.write(SummaryStats.csvHeader());
            writer.newLine();

            for(int i = 0; i < summaryStats.size(); ++i)
            {
                final SummaryStats stats = summaryStats.get(i);
                writer.write(stats.toCsv(mConfig.SampleIds.get(i)));
                writer.newLine();
            }

            closeBufferedWriter(writer);
        }
        catch(IOException e)
        {
            ISF_LOGGER.error("failed to write cohort summary data file: {}", e.toString());
        }
    }

    public boolean formSampleFilenames(final DataLoadType dataType, final List<Path> filenames)
    {
        String rootDir = mConfig.RootDataDir;

        if(!rootDir.endsWith(File.separator))
            rootDir += File.separator;

        for(final String sampleId : mConfig.SampleIds)
        {
            String filename = rootDir;

            if(mConfig.UseSampleDirectories)
                filename += File.separator + sampleId + File.separator;

            filename += sampleId + ".isf.";
            filename += getFileId(dataType);

            final Path path = Paths.get(filename);

            if (!Files.exists(path))
            {
                ISF_LOGGER.error("sampleId({}) no file({}) found", sampleId, filename);
                filenames.clear();
                return false;
            }

            filenames.add(path);
        }

        return true;
    }

    public static void main(@NotNull final String[] args) throws ParseException
    {
        final Options options = DataLoaderConfig.createCmdLineOptions();
        final CommandLineParser parser = new DefaultParser();
        final CommandLine cmd = parser.parse(options, args);

        if (cmd.hasOption(LOG_DEBUG))
        {
            Configurator.setRootLevel(Level.DEBUG);
        }

        DataLoaderConfig config = new DataLoaderConfig(cmd);

        /*
        if(!config.isValid())
        {
            ISF_LOGGER.error("missing config options, exiting");
            return;
        }

         */

        DataLoader dataLoader = new DataLoader(config);

        if(!dataLoader.load())
        {
            ISF_LOGGER.info("Isofox data loader failed");
            return;
        }

        ISF_LOGGER.info("Isofox data loading complete");
    }

}
