package com.hartwig.hmftools.purple.somatic;

import static java.lang.Math.pow;

import static com.hartwig.hmftools.common.variant.CodingEffect.NONE;
import static com.hartwig.hmftools.common.variant.CodingEffect.UNDEFINED;
import static com.hartwig.hmftools.common.variant.PanelSomaticLikelihood.HIGH;
import static com.hartwig.hmftools.common.variant.PanelSomaticLikelihood.LOW;
import static com.hartwig.hmftools.common.variant.PanelSomaticLikelihood.MEDIUM;
import static com.hartwig.hmftools.common.variant.PaveVcfTags.GNOMAD_FREQ;
import static com.hartwig.hmftools.common.variant.PurpleVcfTags.PANEL_SOMATIC_LIKELIHOOD;
import static com.hartwig.hmftools.common.variant.VariantType.SNP;
import static com.hartwig.hmftools.purple.config.PurpleConstants.CODING_BASES_PER_GENOME;
import static com.hartwig.hmftools.purple.config.TargetRegionsData.TMB_GENE_EXCLUSIONS;

import com.hartwig.hmftools.common.variant.CodingEffect;
import com.hartwig.hmftools.common.variant.PanelSomaticLikelihood;
import com.hartwig.hmftools.common.variant.impact.VariantImpact;
import com.hartwig.hmftools.purple.config.TargetRegionsData;

public class TumorMutationalLoad
{
    private final TargetRegionsData mTargetRegions;
    private double mLoad;
    private double mBurden;
    private int mUnclearVariants;

    public TumorMutationalLoad(final TargetRegionsData targetRegions)
    {
        mTargetRegions = targetRegions;
        mLoad = 0;
        mBurden = 0;
        mUnclearVariants = 0;
    }

    public double load() { return mLoad; }
    public double burden() { return mBurden; }

    public double calcTml()
    {
        if(!mTargetRegions.hasTargetRegions())
            return mLoad;

        double adjustedLoad = mBurden;

        if(mUnclearVariants > 0)
        {
            double unclearFactor = mTargetRegions.codingBases() / mTargetRegions.codingBaseFactor();
            double unclearVariants = pow(mUnclearVariants,2) / (mUnclearVariants + unclearFactor);
            adjustedLoad += unclearVariants;
        }

        double calcTml = adjustedLoad * mTargetRegions.tmlRatio() * CODING_BASES_PER_GENOME / mTargetRegions.codingBases();
        return calcTml;
    }

    public void processVariant(final SomaticVariant variant)
    {
        if(!mTargetRegions.hasTargetRegions())
            return;

        // test criteria to count a variant towards TMB
        final VariantImpact variantImpact = variant.variantImpact();

        if(!mTargetRegions.inTargetRegions(variant.chromosome(), variant.position()))
            return;

        if(variant.isHotspot())
            return;

        if(variant.type() != SNP)
            return;

        if(variantImpact.WorstCodingEffect == NONE || variantImpact.WorstCodingEffect == UNDEFINED)
            return;

        if(TMB_GENE_EXCLUSIONS.contains(variantImpact.CanonicalGeneName))
            return;

        double gnomadFreq = variant.context().getAttributeAsDouble(GNOMAD_FREQ, 0);
        if(gnomadFreq > 0)
            return;

        PanelSomaticLikelihood somaticLikelihood = variant.context().hasAttribute(PANEL_SOMATIC_LIKELIHOOD) ?
                PanelSomaticLikelihood.valueOf(variant.context().getAttributeAsString(PANEL_SOMATIC_LIKELIHOOD, "")) : LOW;

        if(somaticLikelihood == HIGH)
        {
            ++mBurden;
        }
        else if(somaticLikelihood == MEDIUM)
        {
            ++mUnclearVariants;
        }

        if(variantImpact.WorstCodingEffect.equals(CodingEffect.MISSENSE))
            mLoad++;
    }
}
