package com.hartwig.hmftools.cup.common;

public enum CategoryType
{
    SNV,
    SV,
    SAMPLE_TRAIT,
    FEATURE,
    GENE_EXP,
    ALT_SJ,
    CLASSIFIER;

    public static boolean isDna(final CategoryType type)
    {
        return type == SNV || type == SV || type == SAMPLE_TRAIT || type == FEATURE;
    }

    public static boolean isRna(final CategoryType type)
    {
        return type == GENE_EXP || type == ALT_SJ;
    }

}
