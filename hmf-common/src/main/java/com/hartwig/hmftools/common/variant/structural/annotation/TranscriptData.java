package com.hartwig.hmftools.common.variant.structural.annotation;

import java.util.List;

import com.google.common.collect.Lists;

public class TranscriptData
{
    public final int TransId;
    public final String TransName;
    public final String GeneId;
    public final boolean IsCanonical;
    public final byte Strand;
    public final long TransStart;
    public final long TransEnd;
    public final Long CodingStart;
    public final Long CodingEnd;
    public final String BioType;

    private List<ExonData> mExons;

    public TranscriptData(final int transId, final String transName, final String geneId, final boolean isCanonical, final byte strand,
            long transStart, long transEnd, Long codingStart, Long codingEnd, String bioType)
    {
        TransId = transId;
        TransName = transName;
        GeneId = geneId;
        IsCanonical = isCanonical;
        Strand = strand;
        TransStart = transStart;
        TransEnd = transEnd;
        CodingStart = codingStart;
        CodingEnd = codingEnd;
        BioType = bioType;
        mExons = Lists.newArrayList();
    }

    public void setExons(final List<ExonData> exons) { mExons = exons; }
    public List<ExonData> exons() { return mExons; }

}
