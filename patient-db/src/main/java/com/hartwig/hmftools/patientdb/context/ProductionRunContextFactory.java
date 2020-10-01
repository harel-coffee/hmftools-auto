package com.hartwig.hmftools.patientdb.context;

import java.io.IOException;

import org.jetbrains.annotations.NotNull;

public final class ProductionRunContextFactory {

    private ProductionRunContextFactory() {
    }

    @NotNull
    public static RunContext fromRunDirectory(@NotNull String runDirectory) throws IOException {
        RunContext runContextFromMetaData = MetaDataResolver.fromMetaDataFile(runDirectory);
        if (runContextFromMetaData == null) {
            throw new IOException("Could not resolve run context from meta data for " + runDirectory);
        }
        return runContextFromMetaData;
    }
}
