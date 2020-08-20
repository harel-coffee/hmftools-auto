package com.hartwig.hmftools.cup.svs;

import static com.hartwig.hmftools.common.sigs.Percentiles.getPercentile;
import static com.hartwig.hmftools.cup.common.CategoryType.SAMPLE_TRAIT;
import static com.hartwig.hmftools.cup.common.CategoryType.SV;
import static com.hartwig.hmftools.cup.common.CupCalcs.calcPercentilePrevalence;
import static com.hartwig.hmftools.cup.common.ResultType.LIKELIHOOD;
import static com.hartwig.hmftools.cup.common.ResultType.PERCENTILE;
import static com.hartwig.hmftools.cup.svs.SvDataLoader.loadRefPercentileData;
import static com.hartwig.hmftools.cup.svs.SvDataLoader.loadSvDataFromCohortFile;
import static com.hartwig.hmftools.cup.svs.SvDataLoader.loadSvDataFromDatabase;
import static com.hartwig.hmftools.cup.svs.SvDataType.LINE;
import static com.hartwig.hmftools.cup.svs.SvDataType.MAX_COMPLEX_SIZE;
import static com.hartwig.hmftools.cup.svs.SvDataType.SIMPLE_DEL_20KB_1MB;
import static com.hartwig.hmftools.cup.svs.SvDataType.SIMPLE_DUP_100KB_5MB;
import static com.hartwig.hmftools.cup.svs.SvDataType.SIMPLE_DUP_32B_200B;
import static com.hartwig.hmftools.cup.svs.SvDataType.TELOMERIC_SGL;

import java.util.List;
import java.util.Map;

import com.google.common.collect.Maps;
import com.hartwig.hmftools.cup.SampleAnalyserConfig;
import com.hartwig.hmftools.cup.common.ResultType;
import com.hartwig.hmftools.cup.common.SampleData;
import com.hartwig.hmftools.cup.common.SampleDataCache;
import com.hartwig.hmftools.cup.common.SampleResult;

import org.apache.commons.compress.utils.Lists;

public class SvAnnotation
{
    private final SampleAnalyserConfig mConfig;
    private final SampleDataCache mSampleDataCache;

    private final Map<String,SvData> mSampleSvData;

    private final Map<SvDataType,Map<String,double[]>> mRefSvTypePercentiles;

    private boolean mIsValid;

    public SvAnnotation(final SampleAnalyserConfig config, final SampleDataCache sampleDataCache)
    {
        mConfig = config;
        mSampleDataCache = sampleDataCache;

        mSampleSvData = Maps.newHashMap();
        mRefSvTypePercentiles = Maps.newHashMap();
        mIsValid = true;

        mIsValid &= loadRefPercentileData(mConfig.RefSvPercFile, mRefSvTypePercentiles);
        mIsValid &= loadSampleSvData();
    }

    public boolean isValid() { return mIsValid; }

    public List<SampleResult> processSample(final SampleData sample)
    {
        final List<SampleResult> results = Lists.newArrayList();

        if(!mConfig.runCategory(SV))
            return results;

        final SvData svData = mSampleSvData.get(sample.Id);

        if(svData == null)
            return results;

        for(Map.Entry<SvDataType,Map<String,double[]>> entry : mRefSvTypePercentiles.entrySet())
        {
            final SvDataType svDataType = entry.getKey();
            double svCount = svData.getCount(svDataType);

            final Map<String,Double> cancerTypeValues = Maps.newHashMap();

            for(Map.Entry<String,double[]> cancerPercentiles : entry.getValue().entrySet())
            {
                final String cancerType = cancerPercentiles.getKey();
                double percentile = getPercentile(cancerPercentiles.getValue(), svCount, true);
                cancerTypeValues.put(cancerType, percentile);
            }

            SampleResult result = new SampleResult(sample.Id, SV, PERCENTILE, svDataType.toString(), svCount, cancerTypeValues);
            results.add(result);
        }

        // calculate prevalence for specific SV values
        results.add(calcPrevalenceResult(sample, svData, LINE, true));
        results.add(calcPrevalenceResult(sample, svData, LINE, false));
        results.add(calcPrevalenceResult(sample, svData, TELOMERIC_SGL, false));
        results.add(calcPrevalenceResult(sample, svData, SIMPLE_DUP_32B_200B, false));
        results.add(calcPrevalenceResult(sample, svData, SIMPLE_DUP_100KB_5MB, false));
        results.add(calcPrevalenceResult(sample, svData, SIMPLE_DEL_20KB_1MB, false));
        results.add(calcPrevalenceResult(sample, svData, MAX_COMPLEX_SIZE, false));

        return results;
    }

    private SampleResult calcPrevalenceResult(final SampleData sample, final SvData svData, final SvDataType type, boolean useLowThreshold)
    {
        double svValue = svData.getCount(type);
        int cancerTypeCount = mSampleDataCache.RefCancerSampleData.size();

        final Map<String,Double> cancerPrevs = calcPercentilePrevalence(
                mRefSvTypePercentiles.get(type), svValue, cancerTypeCount, useLowThreshold);

        final String dataType = String.format("%s_%s", type, useLowThreshold ? "LOW" : "HIGH");
        return new SampleResult(sample.Id, SV, LIKELIHOOD, dataType, svValue, cancerPrevs);
    }

    private boolean loadSampleSvData()
    {
        if(!mConfig.SampleSvFile.isEmpty())
        {
            if(!loadSvDataFromCohortFile(mConfig.SampleSvFile, mSampleSvData))
                return false;
        }
        else if(mConfig.DbAccess != null)
        {
            if(!loadSvDataFromDatabase(mConfig.DbAccess, mSampleDataCache.SampleIds, mSampleSvData))
                return false;
        }

        return true;
    }
}
