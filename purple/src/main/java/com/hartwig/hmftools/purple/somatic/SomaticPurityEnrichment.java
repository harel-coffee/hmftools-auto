package com.hartwig.hmftools.purple.somatic;

import static java.lang.Math.exp;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Math.pow;
import static java.lang.Math.round;

import static com.hartwig.hmftools.common.variant.PurpleVcfTags.PURPLE_AF;
import static com.hartwig.hmftools.common.variant.PurpleVcfTags.PURPLE_BIALLELIC_PROB;
import static com.hartwig.hmftools.common.variant.PurpleVcfTags.PURPLE_CN;
import static com.hartwig.hmftools.common.variant.PurpleVcfTags.PURPLE_GERMLINE_INFO;
import static com.hartwig.hmftools.common.variant.PurpleVcfTags.PURPLE_MINOR_ALLELE_CN_INFO;
import static com.hartwig.hmftools.common.variant.PurpleVcfTags.PURPLE_VARIANT_CN;
import static com.hartwig.hmftools.purple.config.PurpleConstants.BIALLELIC_ASYMPTOTE_BEHAVIOUR_NEAR_MAX_GROWTH;
import static com.hartwig.hmftools.purple.config.PurpleConstants.BIALLELIC_BASE_LOH_ERROR_RATE;
import static com.hartwig.hmftools.purple.config.PurpleConstants.BIALLELIC_GROWTH_FACTOR;
import static com.hartwig.hmftools.purple.config.PurpleConstants.BIALLELIC_LEFT_HORIZONTAL_ASYMPTOTE;

import java.util.List;
import java.util.Optional;

import com.hartwig.hmftools.common.genome.chromosome.HumanChromosome;
import com.hartwig.hmftools.common.genome.region.GenomeRegionSelector;
import com.hartwig.hmftools.common.genome.region.GenomeRegionSelectorFactory;
import com.hartwig.hmftools.common.purple.GermlineStatus;
import com.hartwig.hmftools.common.utils.collection.Multimaps;
import com.hartwig.hmftools.purple.purity.PurityAdjuster;
import com.hartwig.hmftools.common.purple.PurpleCopyNumber;
import com.hartwig.hmftools.purple.region.ObservedRegion;

import org.apache.commons.math3.distribution.PoissonDistribution;

import htsjdk.variant.variantcontext.VariantContext;

public class SomaticPurityEnrichment
{
    private final PurityAdjuster mPurityAdjuster;
    private final GenomeRegionSelector<PurpleCopyNumber> mCopyNumberSelector;
    private final GenomeRegionSelector<ObservedRegion> mObservedRegionSelector;

    public SomaticPurityEnrichment(
            final PurityAdjuster purityAdjuster, final List<PurpleCopyNumber> copyNumbers, final List<ObservedRegion> fittedRegions)
    {
        mPurityAdjuster = purityAdjuster;
        mCopyNumberSelector = GenomeRegionSelectorFactory.createImproved(Multimaps.fromRegions(copyNumbers));
        mObservedRegionSelector = GenomeRegionSelectorFactory.createImproved(Multimaps.fromRegions(fittedRegions));
    }

    public void processVariant(final SomaticVariant variant)
    {
        if(!HumanChromosome.contains(variant.chromosome()))
            return;

        Optional<ObservedRegion> observedRegion = mObservedRegionSelector.select(variant);
        GermlineStatus germlineStatus = GermlineStatus.UNKNOWN;

        if(observedRegion.isPresent())
            germlineStatus = observedRegion.get().germlineStatus();

        variant.context().getCommonInfo().putAttribute(PURPLE_GERMLINE_INFO, germlineStatus.toString());

        if(variant.hasTumorAlleleDepth())
        {
            Optional<PurpleCopyNumber> purpleCopyNumber = mCopyNumberSelector.select(variant);
            if(purpleCopyNumber.isPresent())
            {
                applyPurityAdjustment(variant, purpleCopyNumber.get(), germlineStatus == GermlineStatus.HET_DELETION);
            }
        }
    }

    private void applyPurityAdjustment(final SomaticVariant variant, final PurpleCopyNumber purpleCopyNumber, boolean isGermlineHetDeletion)
    {
        double copyNumber = purpleCopyNumber.averageTumorCopyNumber();

        double vaf = mPurityAdjuster.purityAdjustedVAF(
                purpleCopyNumber.chromosome(), max(0.001, copyNumber), variant.alleleFrequency(), isGermlineHetDeletion);

        double variantCopyNumber = max(0, vaf * copyNumber);
        
        double biallelicProbability = calculateBiallelic(purpleCopyNumber, variant);

        VariantContext variantContext = variant.context();

        variantContext.getCommonInfo().putAttribute(PURPLE_VARIANT_CN, variantCopyNumber);
        variantContext.getCommonInfo().putAttribute(PURPLE_CN, copyNumber);

        variantContext.getCommonInfo().putAttribute(PURPLE_AF, String.format("%.4f", vaf));
        variantContext.getCommonInfo().putAttribute(PURPLE_MINOR_ALLELE_CN_INFO, purpleCopyNumber.minorAlleleCopyNumber());
        variantContext.getCommonInfo().putAttribute(PURPLE_BIALLELIC_PROB, biallelicProbability);
    }

    // version 6.0 - New biallelic model
    private static double probabilityLoh(double minorAlleleCopyNumber)
    {
        double probabilityLoh = (1 - 1 / pow((1 + BIALLELIC_LEFT_HORIZONTAL_ASYMPTOTE * exp(-BIALLELIC_GROWTH_FACTOR * minorAlleleCopyNumber)), 1 / BIALLELIC_ASYMPTOTE_BEHAVIOUR_NEAR_MAX_GROWTH));

        return probabilityLoh;
    }

    private static double probabilityNoLoh(double probabilityLoh)
    {
        double probabilityNoLoh = 1 - probabilityLoh;

        return probabilityNoLoh;
    }

    private static double vcnThresholdForNoWildtype(double copyNumber)
    {
        double vcnThresholdForNoWildtype = min(copyNumber - 0.5, max(1.5, copyNumber - 0.8));

        return vcnThresholdForNoWildtype;
    }

    private static double variantReadCountAtThreshold(double threshold, double variantCopyNumber, double observedAlleleReadCount)
    {
        double variantReadCountAtThreshold = (threshold / variantCopyNumber) * observedAlleleReadCount;

        return variantReadCountAtThreshold;
    }

    private static double conditionalProbNoWildtypeAssumeLoh(double variantReadCountAtThreshold, double observedAlleleReadCount)
    {
        PoissonDistribution poissonDist = new PoissonDistribution(observedAlleleReadCount);

        int variantReadCountAtThresholdInteger = (int)round(variantReadCountAtThreshold);
        double conditionalProbNoWildtypeAssumeLoh = 1 - poissonDist.cumulativeProbability(variantReadCountAtThresholdInteger);

        return conditionalProbNoWildtypeAssumeLoh;
    }

    private static double conditionalProbNoWildtypeAssumeNoLoh(double conditionalProbNoWildtypeAssumeLoh, double probabilityLoh)
    {
        double conditionalProbNoWildtypeAssumeNoLOH = max(probabilityLoh, BIALLELIC_BASE_LOH_ERROR_RATE) / ((1 - conditionalProbNoWildtypeAssumeLoh) + max(probabilityLoh, BIALLELIC_BASE_LOH_ERROR_RATE));

        if(Double.isNaN(conditionalProbNoWildtypeAssumeNoLOH))
        {
            return 0.0d;
        }

        return conditionalProbNoWildtypeAssumeNoLOH;
    }

    private static double probabilityNoWildtype(double probabilityLoh, double probabilityNoLoh, double conditionalProbNoWildtypeAssumeLoh, double conditionalProbNoWildtypeAssumeNoLoh)
    {
        double probabilityNoWildtype = probabilityLoh * conditionalProbNoWildtypeAssumeLoh + probabilityNoLoh * conditionalProbNoWildtypeAssumeNoLoh;

        return probabilityNoWildtype;
    }

    public static double calculateBiallelic(final PurpleCopyNumber purpleCopyNumber, final SomaticVariant purpleSomaticVariant)
    {
        // inputs
        double minorAlleleCopyNumber = purpleCopyNumber.minorAlleleCopyNumber();
        double copyNumber = purpleCopyNumber.averageTumorCopyNumber();

        double variantCopyNumber = purpleSomaticVariant.decorator().variantCopyNumber();
        double alleleReadCount = purpleSomaticVariant.alleleReadCount();

        // part 1
        double probabilityLoh = probabilityLoh(minorAlleleCopyNumber);
        double probabilityNoLoh = probabilityNoLoh(probabilityLoh);

        // part 2
        double vcnThresholdForNoWildtype = vcnThresholdForNoWildtype(copyNumber);
        double variantReadCountAtThreshold = variantReadCountAtThreshold(vcnThresholdForNoWildtype, variantCopyNumber, alleleReadCount);

        // part 3
        double conditionalProbNoWildtypeAssumeLoh = conditionalProbNoWildtypeAssumeLoh(variantReadCountAtThreshold, alleleReadCount);
        double conditionalProbNoWildtypeAssumeNoLoh = conditionalProbNoWildtypeAssumeNoLoh(conditionalProbNoWildtypeAssumeLoh, probabilityLoh);

        // Final calculation
        double probabilityNoWildtype = probabilityNoWildtype(probabilityLoh, probabilityNoLoh, conditionalProbNoWildtypeAssumeLoh, conditionalProbNoWildtypeAssumeNoLoh);
        return probabilityNoWildtype;
    }
}
