package com.hartwig.hmftools.cup.common;

public class CupConstants
{
    public final static double SNV_CSS_THRESHOLD = 0.01;
    public final static double SNV_POS_FREQ_CSS_THRESHOLD = 0.01;
    public final static double RNA_GENE_EXP_CSS_THRESHOLD = 0.01;

    public final static double SNV_CSS_DIFF_EXPONENT = 8;
    public final static double SNV_POS_FREQ_DIFF_EXPONENT = 10;
    public final static double RNA_GENE_EXP_DIFF_EXPONENT = 10;

    public static final double MIN_CLASSIFIER_SCORE = 0.02;

    public final static String CANCER_TYPE_UNKNOWN = "Unknown";

    // cancer types with gender-exclusions
    public final static String CANCER_TYPE_PROSTATE = "Prostate";
    public final static String CANCER_TYPE_OVARY = "Ovary";
    public final static String CANCER_TYPE_UTERUS = "Uterus";
    public final static String CANCER_TYPE_PAN = "ALL";

    public final static double DRIVER_ZERO_PREVALENCE_ALLOCATION = 0.10;
    public final static double NON_DRIVER_ZERO_PREVALENCE_ALLOCATION = 0.02;

}
