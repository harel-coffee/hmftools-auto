package com.hartwig.hmftools.esvee.filters;

import java.util.HashSet;
import java.util.Set;

import com.hartwig.hmftools.esvee.SvConstants;
import com.hartwig.hmftools.esvee.common.VariantCall;

public final class SoftFilters
{
    public static Set<String> applyFilters(final VariantCall variantCall)
    {
        final Set<String> filters = new HashSet<>();

        if(variantCall.overhang() < SvConstants.VCFLOWOVERHANGTHRESHOLD)
            filters.add(FilterType.MIN_OVERHANG.filterName());

        if(variantCall.associatedAssemblies().size() > 1)
            filters.add(FilterType.MULTIPLE_ASSEMBLIES.filterName());

        if(variantCall.quality() < SvConstants.VCFLOWQUALITYTHRESHOLD)
            filters.add(FilterType.MIN_QUALITY.filterName());

        if(variantCall.supportingFragments().size() < SvConstants.MINREADSTOSUPPORTASSEMBLY)
            filters.add(FilterType.MIN_SUPPORT.filterName());

        // final boolean isLikelyFalse = isLowSupport || (isLowOverhang && call.discordantSupport() == 0) || isLowQuality;

        return filters;
    }
}
