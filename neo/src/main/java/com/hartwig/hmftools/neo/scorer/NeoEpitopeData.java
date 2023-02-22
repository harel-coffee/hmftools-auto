package com.hartwig.hmftools.neo.scorer;

import static java.lang.String.format;

import static com.hartwig.hmftools.common.fusion.FusionCommon.FS_DOWN;
import static com.hartwig.hmftools.common.fusion.FusionCommon.FS_PAIR;
import static com.hartwig.hmftools.common.fusion.FusionCommon.FS_UP;
import static com.hartwig.hmftools.common.rna.RnaExpressionMatrix.INVALID_EXP;

import java.util.List;
import java.util.Map;

import com.hartwig.hmftools.common.neo.NeoEpitopeType;
import com.hartwig.hmftools.common.neo.RnaNeoEpitope;
import com.hartwig.hmftools.common.rna.RnaExpressionMatrix;

public class NeoEpitopeData
{
    public final int Id;
    public final NeoEpitopeType VariantType;
    public final String VariantInfo;
    public final String GeneId;
    public final String GeneName;
    public final String UpAminoAcids;
    public final String NovelAminoAcids;
    public final String DownAminoAcids;
    public final List<String>[] Transcripts;
    public final NeoRnaData RnaData;

    public NeoEpitopeData(
            final int id, final NeoEpitopeType variantType, final String variantInfo, final String geneId, final String geneName,
            final String upAminoAcids, final String novelAminoAcids, final String downAminoAcids,
            final List<String> transNamesUp, final List<String> transNamesDown, double tpmCancer, double tpmCohort)
    {
        Id = id;
        VariantType = variantType;
        VariantInfo = variantInfo;
        GeneId = geneId;
        GeneName = geneName;
        UpAminoAcids = upAminoAcids;
        NovelAminoAcids = novelAminoAcids;
        DownAminoAcids = downAminoAcids;
        Transcripts = new List[FS_PAIR];
        Transcripts[FS_UP] = transNamesUp;
        Transcripts[FS_DOWN] = transNamesDown;

        RnaData = new NeoRnaData(tpmCancer, tpmCohort);
    }

    public void setExpressionData(final String sampleId, final Map<String,Double> sampleTPMs, final RnaExpressionMatrix transExpression)
    {
        if(transExpression == null && sampleTPMs.isEmpty())
            return;

        for(int fs = FS_UP; fs <= FS_DOWN; ++fs)
        {
            RnaData.TransExpression[fs] = 0;

            for(String transName : Transcripts[fs])
            {
                double expression = 0;

                if(!sampleTPMs.isEmpty())
                {
                    expression = sampleTPMs.get(transName);
                }
                else
                {
                    expression = transExpression.getExpression(transName, sampleId);
                }

                // distinguish non-existent expression vs zero TPM
                if(expression != INVALID_EXP)
                    RnaData.TransExpression[fs] += expression;
            }
        }
    }

    public void setFusionRnaSupport(final List<RnaNeoEpitope> rnaNeoDataList)
    {
        if(!VariantType.isFusion())
            return;

        RnaNeoEpitope matchedRnaData = rnaNeoDataList.stream()
                .filter(x -> x.Id == Id && x.VariantInfo.equals(VariantInfo)).findFirst().orElse(null);

        if(matchedRnaData != null)
        {
            RnaData.FragmentSupport = matchedRnaData.FragmentCount;
            RnaData.BaseDepth[FS_UP] = matchedRnaData.BaseDepth[FS_UP];
            RnaData.BaseDepth[FS_DOWN] = matchedRnaData.BaseDepth[FS_DOWN];
        }
    }

    public void setMutationRnaSupport(final List<SomaticVariant> somaticVariants)
    {
        if(!VariantType.isPointMutation())
            return;

        SomaticVariant matchedRnaData = somaticVariants.stream()
                .filter(x -> x.variantInfo().equals(VariantInfo)).findFirst().orElse(null);

        if(matchedRnaData != null)
        {
            RnaData.FragmentSupport = matchedRnaData.RnaFragments;
            RnaData.BaseDepth[FS_UP] = matchedRnaData.RnaDepth;
            RnaData.BaseDepth[FS_DOWN] = matchedRnaData.RnaDepth;
        }
    }

    public String toString()
    {
        return format("%d: %s:%s gene(%s)", Id, VariantType, VariantInfo, GeneName);
    }
}
