package com.hartwig.hmftools.bamtools.slice;

import static com.hartwig.hmftools.bamtools.common.CommonUtils.BT_LOGGER;
import static com.hartwig.hmftools.common.region.BaseRegion.positionsOverlap;
import static com.hartwig.hmftools.common.samtools.SamRecordUtils.SUPPLEMENTARY_ATTRIBUTE;

import java.io.File;
import java.util.concurrent.Callable;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.samtools.BamSlicer;
import com.hartwig.hmftools.common.region.ChrBaseRegion;
import com.hartwig.hmftools.common.samtools.SupplementaryReadData;

import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;

public class RegionBamSlicer implements Callable
{
    private final SliceConfig mConfig;
    private final ChrBaseRegion mRegion;

    private final SamReader mSamReader;
    private final BamSlicer mBamSlicer;

    private final ReadCache mReadCache;
    private final SliceWriter mSliceWriter;

    private int mReadsProcessed;

    public RegionBamSlicer(
            final ChrBaseRegion region, final SliceConfig config, final ReadCache readCache, final SliceWriter sliceWriter)
    {
        mConfig = config;
        mRegion = region;
        mReadCache = readCache;
        mSliceWriter = sliceWriter;

        mSamReader = !mConfig.RefGenomeFile.isEmpty() ?
                SamReaderFactory.makeDefault().referenceSequence(new File(mConfig.RefGenomeFile)).open(new File(mConfig.BamFile)) : null;

        mBamSlicer = new BamSlicer(0, true, true, false);
        mBamSlicer.setKeepHardClippedSecondaries();
        mBamSlicer.setKeepUnmapped();

        mReadsProcessed = 0;
    }

    public int totalReads() { return mReadsProcessed; }

    @Override
    public Long call()
    {
        BT_LOGGER.info("processing region({})", mRegion);

        mBamSlicer.slice(mSamReader, Lists.newArrayList(mRegion), this::processSamRecord);

        BT_LOGGER.info("region({}) complete, processed {} reads", mRegion, mReadsProcessed);

        return (long)0;
    }

    private static final int LOG_COUNT = 100_000;

    @VisibleForTesting
    public void processSamRecord(final SAMRecord read)
    {
        if(!positionsOverlap(mRegion.start(), mRegion.end(), read.getAlignmentStart(), read.getAlignmentEnd()))
            return;

        ++mReadsProcessed;

        if((mReadsProcessed % LOG_COUNT) == 0)
        {
            BT_LOGGER.debug("region({}) processed {} reads, current pos({})",
                    mRegion, mReadsProcessed, read.getAlignmentStart());
        }

        if(mConfig.MaxPartitionReads > 0 && mReadsProcessed >= mConfig.MaxPartitionReads)
        {
            BT_LOGGER.debug("region({}) halting slice after {} reads", mRegion, mReadsProcessed);
            mBamSlicer.haltProcessing();
            return;
        }

        mSliceWriter.writeRead(read);

        // register any remote reads

        // check for remote mates and supplementaries
        if(read.getReadPairedFlag() && !read.getMateUnmappedFlag())
        {
            if(!isInsideRegion(read.getMateReferenceName(), read.getMateAlignmentStart()))
            {
                mReadCache.addRemotePosition(new RemotePosition(read.getReadName(), read.getMateReferenceName(), read.getMateAlignmentStart()));
            }
        }

        if(read.hasAttribute(SUPPLEMENTARY_ATTRIBUTE))
        {
            SupplementaryReadData suppData = SupplementaryReadData.from(read);

            if(!isInsideRegion(suppData.Chromosome, suppData.Position))
            {
                mReadCache.addRemotePosition(new RemotePosition(read.getReadName(), suppData.Chromosome, suppData.Position));
            }
        }
    }

    private boolean isInsideRegion(final String chromosome, final int startPosition)
    {
        return mRegion.Chromosome.equals(chromosome)
                && startPosition >= mRegion.start() - mConfig.ReadLength && startPosition <= mRegion.end();
    }
}
