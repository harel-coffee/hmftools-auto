package com.hartwig.hmftools.sage.filter;

import static java.lang.String.format;

import static com.hartwig.hmftools.common.region.BaseRegion.positionWithin;
import static com.hartwig.hmftools.sage.SageCommon.SG_LOGGER;

import com.hartwig.hmftools.common.variant.hotspot.VariantHotspot;
import com.hartwig.hmftools.sage.evidence.RawContext;
import com.hartwig.hmftools.sage.sync.FragmentData;

import htsjdk.samtools.SAMRecord;

public class StrandBiasData
{
    private int mForwardCount;
    private int mReverseCount;

    public StrandBiasData()
    {
        mForwardCount = 0;
        mReverseCount = 0;
    }

    public void add(boolean isFoward)
    {
        if(isFoward)
            ++mForwardCount;
        else
            ++mReverseCount;
    }

    public int depth() { return mForwardCount + mReverseCount; }

    public double bias()
    {
        double depth = mForwardCount + mReverseCount;
        return depth > 0 ? mForwardCount / depth : 0.5;
    }

    public void registerFragment(final SAMRecord record)
    {
        boolean readIsForward = !record.getReadNegativeStrandFlag();

        if(!record.getReadPairedFlag())
        {
            add(readIsForward);
            return;
        }

        // make the distinction between F1R2 and F2R1
        boolean firstIsForward = record.getFirstOfPairFlag() ? readIsForward : !record.getMateNegativeStrandFlag();
        boolean secondIsForward = !record.getFirstOfPairFlag() ? readIsForward : !record.getMateNegativeStrandFlag();

        if(firstIsForward != secondIsForward)
        {
            add(firstIsForward);
        }
    }

    private void registerRead(final SAMRecord record)
    {
        add(!record.getReadNegativeStrandFlag());

        /*
        if(!record.getReadNegativeStrandFlag())
        {
            SG_LOGGER.debug("read({}) coords({}-{}) on forward strand",
                    record.getReadName(), record.getAlignmentStart(), record.getAlignmentEnd());
        }
        */
    }

    public void registerRead(final SAMRecord record, final FragmentData fragment, final VariantHotspot variant)
    {
        // no fragment sync - take the read's strand
        if(fragment == null)
        {
            registerRead(record);
            return;
        }

        if(fragment.First.getReadNegativeStrandFlag() == fragment.Second.getReadNegativeStrandFlag())
            return; // ignore the inverted case

        // read falls only within one or the other read's bounds - use that read's strand
        boolean withinFirst = positionWithin(variant.position(), fragment.First.getAlignmentStart(), fragment.First.getAlignmentEnd());
        boolean withinSecond = positionWithin(variant.position(), fragment.Second.getAlignmentStart(), fragment.Second.getAlignmentEnd());

        /*
        SG_LOGGER.debug("read({}) var({}) withinRead(first={} second={})",
                record.getReadName(), variant.position(), withinFirst, withinSecond);
        */

        if(withinFirst && !withinSecond)
        {
            RawContext firstRawContext = RawContext.create(variant, fragment.First);

            if(firstRawContext.AltSupport)
                registerRead(fragment.First);

            return;
        }
        else if(!withinFirst && withinSecond)
        {
            RawContext secondRawContext = RawContext.create(variant, fragment.Second);

            if(secondRawContext.AltSupport)
                registerRead(fragment.Second);

            return;
        }

        // look at the raw alt support from each read to determine which to count
        RawContext firstRawContext = RawContext.create(variant, fragment.First);
        RawContext secondRawContext = RawContext.create(variant, fragment.Second);

        if(firstRawContext.AltSupport)
            registerRead(fragment.First);

        if(secondRawContext.AltSupport)
            registerRead(fragment.Second);

        /*
        SG_LOGGER.debug("read({}) var({}) altSupport(first={} second={})",
                record.getReadName(), variant.position(), firstRawContext.AltSupport, secondRawContext.AltSupport);
        */
    }


    public String toString() { return format("fwd=%d rev=%d total=%d bias=%.3f", mForwardCount, mReverseCount, depth(), bias()); }
}