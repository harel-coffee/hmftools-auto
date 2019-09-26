package com.hartwig.hmftools.patientdb.readers;

import java.time.LocalDate;
import java.util.Set;

import com.hartwig.hmftools.common.lims.Lims;
import com.hartwig.hmftools.patientdb.data.ImmutableSampleData;
import com.hartwig.hmftools.patientdb.data.SampleData;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.util.Strings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LimsSampleReader {

    private static final Logger LOGGER = LogManager.getLogger(LimsSampleReader.class);

    @NotNull
    private final Lims lims;
    @NotNull
    private final Set<String> sequencedSampleIds;

    public LimsSampleReader(@NotNull final Lims lims, @NotNull final Set<String> sequencedSampleIds) {
        this.lims = lims;
        this.sequencedSampleIds = sequencedSampleIds;
    }

    @Nullable
    public SampleData read(@NotNull String sampleBarcode, @NotNull String sampleId) {
        final LocalDate arrivalDate = lims.arrivalDate(sampleBarcode, sampleId);
        boolean isSequenced = sequencedSampleIds.contains(sampleId);

        if (arrivalDate == null) {
            if (isSequenced) {
                LOGGER.warn("Could not find arrival date for sequenced sample {}", sampleId);
            }
            return null;
        }

        final LocalDate samplingDate = lims.samplingDate(sampleBarcode);
        if (samplingDate == null && isSequenced && !lims.confirmedToHaveNoSamplingDate(sampleId)) {
            LOGGER.warn("Could not find sampling date for sequenced sample {}", sampleId);
        }

        return ImmutableSampleData.of(sampleId,
                isSequenced,
                arrivalDate,
                samplingDate,
                lims.dnaNanograms(sampleBarcode),
                lims.primaryTumor(sampleBarcode),
                lims.pathologyTumorPercentage(sampleBarcode));
    }

    @Nullable
    public SampleData readWithoutBarcode(@NotNull String sampleId) {
        final LocalDate arrivalDate = lims.arrivalDate(Strings.EMPTY, sampleId);
        boolean isSequenced = sequencedSampleIds.contains(sampleId);

        if (arrivalDate == null) {
            if (isSequenced) {
                LOGGER.warn("Could not find arrival date for sequenced sample {}", sampleId);
            }
            return null;
        }

        if (!lims.confirmedToHaveNoSamplingDate(sampleId)) {
            LOGGER.warn("Could not find sampling date for sequenced sample {}", sampleId);
        }

        return ImmutableSampleData.of(sampleId, isSequenced, arrivalDate, null, null, null, Lims.NOT_AVAILABLE_STRING);
    }
}
