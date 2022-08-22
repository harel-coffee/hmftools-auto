package com.hartwig.hmftools.purple.somatic;

import static com.hartwig.hmftools.common.variant.enrich.SomaticRefContextEnrichment.REPEAT_COUNT_FLAG;
import static com.hartwig.hmftools.common.variant.enrich.SomaticRefContextEnrichment.REPEAT_SEQUENCE_FLAG;
import static com.hartwig.hmftools.purple.config.PurpleConstants.TARGET_REGIONS_MSI_2_3_BASE_AF;
import static com.hartwig.hmftools.purple.config.PurpleConstants.TARGET_REGIONS_MSI_4_BASE_AF;

import java.util.stream.Collectors;

import com.google.common.annotations.VisibleForTesting;
import com.hartwig.hmftools.common.variant.VariantType;
import com.hartwig.hmftools.purple.config.TargetRegionsData;

import org.apache.logging.log4j.util.Strings;
import org.jetbrains.annotations.NotNull;

import htsjdk.variant.variantcontext.Allele;
import htsjdk.variant.variantcontext.VariantContext;

public class MicrosatelliteIndels
{
    private final TargetRegionsData mTargetRegions;
    private int mIndelCount;

    private static final int MIN_SEQUENCE_LENGTH_FOR_LONG_REPEATS = 2;
    private static final int MAX_SEQUENCE_LENGTH_FOR_LONG_REPEATS = 4;
    private static final int MIN_REPEAT_COUNT_FOR_LONG_REPEATS = 4;
    private static final int MIN_REPEAT_COUNT_FOR_SHORT_REPEATS = 5;

    private static final int MAX_REF_ALT_LENGTH = 50;

    public MicrosatelliteIndels(final TargetRegionsData referenceData)
    {
        mTargetRegions = referenceData;
        mIndelCount = 0;
    }

    public int msiIndelCount()
    {
        return mIndelCount;
    }

    public double msiIndelsPerMb()
    {
        return mTargetRegions.calcMsiIndels(mIndelCount);
    }

    public void processVariant(final SomaticVariant variant)
    {
        if(variant.type() != VariantType.INDEL)
            return;

        int altLength = variant.decorator().alt().length();
        int refLength = variant.decorator().ref().length();

        if(refLength >= MAX_REF_ALT_LENGTH || altLength >= MAX_REF_ALT_LENGTH)
            return;

        if(mTargetRegions.hasTargetRegions())
        {
            if(!mTargetRegions.isTargetRegionsMsiIndel(variant.chromosome(), variant.position()))
                return;

            if(altLength > refLength)
            {
                if(variant.alleleFrequency() < TARGET_REGIONS_MSI_4_BASE_AF)
                    return;
            }
            else
            {
                if(refLength == 2)
                    return;

                if(refLength <= 4)
                {
                    if(variant.alleleFrequency() < TARGET_REGIONS_MSI_2_3_BASE_AF)
                        return;
                }
                else
                {
                    if(variant.alleleFrequency() < TARGET_REGIONS_MSI_4_BASE_AF)
                        return;
                }
            }
        }

        final VariantContext context = variant.context();

        int repeatCount = context.getAttributeAsInt(REPEAT_COUNT_FLAG, 0);
        int repeatSequenceLength = context.getAttributeAsString(REPEAT_SEQUENCE_FLAG, Strings.EMPTY).length();

        if(repeatContextIsRelevant(repeatCount, repeatSequenceLength))
        {
            mIndelCount++;
        }
    }

    private static boolean isValidIndel(final VariantContext context)
    {
        int altLength = alt(context).length();
        int refLength = context.getReference().getBaseString().length();

        return context.isIndel() && refLength < MAX_REF_ALT_LENGTH && altLength < MAX_REF_ALT_LENGTH;
    }

    private static boolean repeatContextIsRelevant(int repeatCount, int repeatSequenceLength)
    {
        final boolean longRepeatRelevant =
                repeatSequenceLength >= MIN_SEQUENCE_LENGTH_FOR_LONG_REPEATS && repeatSequenceLength <= MAX_SEQUENCE_LENGTH_FOR_LONG_REPEATS
                        && repeatCount >= MIN_REPEAT_COUNT_FOR_LONG_REPEATS;
        final boolean shortRepeatRelevant = repeatSequenceLength == 1 && repeatCount >= MIN_REPEAT_COUNT_FOR_SHORT_REPEATS;
        return longRepeatRelevant | shortRepeatRelevant;
    }

    @VisibleForTesting
    static boolean repeatContextIsRelevant(int repeatCount, String sequence)
    {
        return repeatContextIsRelevant(repeatCount, sequence.length());
    }

    @NotNull
    private static String alt(final VariantContext context)
    {
        return String.join(",", context.getAlternateAlleles().stream().map(Allele::toString).collect(Collectors.toList()));
    }
}
