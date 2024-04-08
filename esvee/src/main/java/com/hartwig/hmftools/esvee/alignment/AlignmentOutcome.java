package com.hartwig.hmftools.esvee.alignment;

public enum AlignmentOutcome
{
    MATCH,
    ALT_LOC_MATCH,
    NON_SV_MATCH,
    PARTIAL,
    MULTIPLE,
    NO_MATCH,
    NO_RESULT,
    UNALIGNED;

    public boolean exactMatch() { return this == MATCH || this == ALT_LOC_MATCH || this == NON_SV_MATCH; }
}
