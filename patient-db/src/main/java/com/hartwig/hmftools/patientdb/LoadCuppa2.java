package com.hartwig.hmftools.patientdb;

import static com.hartwig.hmftools.common.utils.config.CommonConfig.SAMPLE;
import static com.hartwig.hmftools.common.utils.config.CommonConfig.SAMPLE_DESC;
import static com.hartwig.hmftools.patientdb.CommonUtils.APP_NAME;
import static com.hartwig.hmftools.patientdb.CommonUtils.LOGGER;
import static com.hartwig.hmftools.patientdb.dao.DatabaseAccess.addDatabaseCmdLineArgs;
import static com.hartwig.hmftools.patientdb.dao.DatabaseAccess.databaseAccess;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;

import com.hartwig.hmftools.common.cuppa2.CuppaPredictions;
import com.hartwig.hmftools.common.utils.config.ConfigBuilder;
import com.hartwig.hmftools.patientdb.dao.DatabaseAccess;

import org.apache.commons.cli.*;
import org.jetbrains.annotations.NotNull;

public class LoadCuppa2
{
    private static final String CUPPA_VIS_DATA_TSV = "cuppa_vis_data_tsv";

    public static void main(@NotNull String[] args) throws ParseException, SQLException, IOException
    {
        ConfigBuilder configBuilder = new ConfigBuilder(APP_NAME);

        configBuilder.addConfigItem(SAMPLE, SAMPLE_DESC);
        addDatabaseCmdLineArgs(configBuilder, true);
        configBuilder.addPath(CUPPA_VIS_DATA_TSV, true, "Path to the CUPPA vis data file");

        configBuilder.checkAndParseCommandLine(args);

        String sample = configBuilder.getValue(SAMPLE);
        String cuppaVisDataTsv = configBuilder.getValue(CUPPA_VIS_DATA_TSV);

        try (DatabaseAccess dbWriter = databaseAccess(configBuilder))
        {
            LOGGER.info("Loading CUPPA from {}", new File(cuppaVisDataTsv).getParent());
            CuppaPredictions cuppaPredictions = CuppaPredictions.fromTsv(cuppaVisDataTsv);
            LOGGER.info("Loaded {} entries from {} for sample {}", cuppaPredictions.size(), cuppaVisDataTsv, sample);

            int TOP_N_PROBS = 3;
            LOGGER.info("Writing top {} probabilities from all classifiers to database", TOP_N_PROBS);

            dbWriter.writeCuppa2(sample, cuppaPredictions, TOP_N_PROBS);
            LOGGER.info("Complete");
        }
        catch(Exception e)
        {
            LOGGER.error("Failed to load CUPPA data", e);
            System.exit(1);
        }
    }
}