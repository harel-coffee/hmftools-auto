package com.hartwig.hmftools.ckb;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

import com.hartwig.hmftools.ckb.dao.CkbDAO;
import com.hartwig.hmftools.ckb.datamodel.CkbJsonDatabase;
import com.hartwig.hmftools.ckb.interpretation.CkbEntryInterpretation;
import com.hartwig.hmftools.ckb.interpretation.InterpretationFactory;
import com.hartwig.hmftools.ckb.reader.CkbJsonReader;

import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

public class CkbImporterApplication {

    private static final Logger LOGGER = LogManager.getLogger(CkbImporterApplication.class);
    public static final String VERSION = CkbImporterApplication.class.getPackage().getImplementationVersion();

    public static void main(String[] args) throws IOException, SQLException, java.text.ParseException {
        LOGGER.info("Running CKB importer v{}", VERSION);

        Options options = CkbImporterConfig.createOptions();

        CkbImporterConfig config = null;
        try {
            config = CkbImporterConfig.createConfig(new DefaultParser().parse(options, args));
        } catch (ParseException exception) {
            LOGGER.warn(exception);
            new HelpFormatter().printHelp("CKB Importer", options);
            System.exit(1);
        }

        CkbJsonDatabase ckbDatabase = CkbJsonReader.read(config.cbkDir());
        List<CkbEntryInterpretation> ckbEntryInterpretation = InterpretationFactory.interpretationCkbDataModel(ckbDatabase);
        LOGGER.info(ckbEntryInterpretation.get(1).molecularProfile());
        LOGGER.info(ckbEntryInterpretation.get(1).variantTreeInterpretations());

        if (config.skipDatabaseWriting()) {
            LOGGER.info("Skipping DB writing.");
        } else {
            CkbDAO ckbDAO = connect(config);
            LOGGER.info("Deleting all data from CKB db");
            ckbDAO.deleteAll();
            LOGGER.info("Starting insertion of CKB interpretation data model");
            ckbDAO.writeCkb(ckbDatabase);
        }

        LOGGER.info("Complete!");
    }

    @NotNull
    private static CkbDAO connect(@NotNull CkbImporterConfig config) throws SQLException {
        return CkbDAO.connectToCkbDAO(config.dbUser(), config.dbPass(), "jdbc:" + config.dbUrl());
    }
}
