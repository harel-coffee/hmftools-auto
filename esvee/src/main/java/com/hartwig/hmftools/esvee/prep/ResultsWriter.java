package com.hartwig.hmftools.esvee.prep;

import static java.lang.String.format;

import static com.hartwig.hmftools.common.utils.file.FileDelimiters.ITEM_DELIM;
import static com.hartwig.hmftools.common.utils.file.FileWriterUtils.closeBufferedWriter;
import static com.hartwig.hmftools.common.utils.file.FileWriterUtils.createBufferedWriter;
import static com.hartwig.hmftools.common.utils.sv.SvCommonUtils.NEG_ORIENT;
import static com.hartwig.hmftools.esvee.AssemblyConfig.SV_LOGGER;
import static com.hartwig.hmftools.esvee.prep.types.WriteType.JUNCTIONS;
import static com.hartwig.hmftools.esvee.prep.types.WriteType.READS;

import static htsjdk.samtools.SAMFlag.DUPLICATE_READ;
import static htsjdk.samtools.SAMFlag.PROPER_PAIR;
import static htsjdk.samtools.SAMFlag.READ_UNMAPPED;
import static htsjdk.samtools.SAMFlag.SUPPLEMENTARY_ALIGNMENT;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.StringJoiner;

import com.hartwig.hmftools.common.bam.CigarUtils;
import com.hartwig.hmftools.common.bam.SupplementaryReadData;
import com.hartwig.hmftools.esvee.prep.types.JunctionData;
import com.hartwig.hmftools.esvee.prep.types.ReadFilterType;
import com.hartwig.hmftools.esvee.prep.types.ReadGroup;
import com.hartwig.hmftools.esvee.prep.types.ReadGroupStatus;
import com.hartwig.hmftools.esvee.prep.types.PrepRead;
import com.hartwig.hmftools.esvee.prep.types.ReadType;
import com.hartwig.hmftools.esvee.prep.types.RemoteJunction;

public class ResultsWriter
{
    private final PrepConfig mConfig;

    private final BufferedWriter mReadWriter;
    private final BufferedWriter mJunctionWriter;
    private final BamWriter mBamWriter;

    public ResultsWriter(final PrepConfig config)
    {
        mConfig = config;

        if(mConfig.OutputDir == null)
        {
            mReadWriter = null;
            mJunctionWriter = null;
            mBamWriter = null;
            return;
        }

        mJunctionWriter = initialiseJunctionWriter();
        mReadWriter = initialiseReadWriter();
        mBamWriter = new BamWriter(config);
    }

    public void close()
    {
        closeBufferedWriter(mReadWriter);
        closeBufferedWriter(mJunctionWriter);
        mBamWriter.close();
    }

    private BufferedWriter initialiseReadWriter()
    {
        if(!mConfig.WriteTypes.contains(READS))
            return null;

        try
        {
            String filename = mConfig.formFilename(READS);
            BufferedWriter writer = createBufferedWriter(filename, false);

            writer.write("ReadId\tGroupCount\tExpectedCount\tGroupStatus\tHasExternal\tReadType\tChromosome\tPosStart\tPosEnd\tCigar");
            writer.write("\tFragLength\tMateChr\tMatePosStart\tMapQual\tSuppData\tFlags\tFilters");
            writer.write("\tFirstInPair\tReadReversed\tProper\tUnmapped\tMateUnmapped\tSupplementary\tDuplicate\tJunctionPositions");

            writer.newLine();

            return writer;
        }
        catch(IOException e)
        {
            SV_LOGGER.error(" failed to create read writer: {}", e.toString());
        }

        return null;
    }

    public synchronized void writeReadGroup(final List<ReadGroup> readGroups)
    {
        for(ReadGroup readGroup : readGroups)
        {
            if(filterReadGroup(readGroup))
                continue;

            writeBamRecords(readGroup);

            String junctionPosStr = readGroup.junctionPositionsStr();

            for(PrepRead read : readGroup.reads())
            {
                if(read.written())
                    continue;

                writeReadData(
                        read, readGroup.size(), readGroup.expectedReadCount(), readGroup.groupStatus(), readGroup.spansPartitions(),
                        junctionPosStr);
            }

            readGroup.reads().forEach(x -> x.setWritten());
        }
    }

    private static boolean filterReadGroup(final ReadGroup readGroup)
    {
        if(readGroup.conditionalOnRemoteReads() && !readGroup.hasRemoteJunctionReads())
            return true;

        return false;
    }

    private void writeReadData(
            final PrepRead read, int readCount, int expectedReadCount, final ReadGroupStatus status, boolean spansPartitions,
            final String junctionPositions)
    {
        if(mReadWriter == null)
            return;

        try
        {
            mReadWriter.write(String.format("%s\t%d\t%d\t%s\t%s", read.id(), readCount, expectedReadCount, status, spansPartitions));

            mReadWriter.write(String.format("\t%s\t%s\t%d\t%d\t%s",
                    read.readType(), read.Chromosome, read.start(), read.end(), read.cigar().toString()));

            SupplementaryReadData suppData = read.supplementaryAlignment();

            mReadWriter.write(String.format("\t%d\t%s\t%d\t%d\t%s\t%d\t%d",
                    read.fragmentInsertSize(), read.MateChromosome, read.MatePosStart, read.mapQuality(),
                    suppData != null ? suppData.asCsv() : "N/A", read.flags(), read.filters()));

            mReadWriter.write(String.format("\t%s\t%s\t%s\t%s\t%s\t%s\t%s",
                    read.isFirstOfPair(), read.isReadReversed(), read.hasFlag(PROPER_PAIR), read.hasFlag(READ_UNMAPPED),
                    read.hasMate() && read.isMateUnmapped(), read.hasFlag(SUPPLEMENTARY_ALIGNMENT), read.hasFlag(DUPLICATE_READ)));

            mReadWriter.write(format("\t%s", junctionPositions));

            mReadWriter.newLine();
        }
        catch(IOException e)
        {
            SV_LOGGER.error(" failed to write read data: {}", e.toString());
        }
    }

    private BufferedWriter initialiseJunctionWriter()
    {
        if(!mConfig.WriteTypes.contains(JUNCTIONS))
            return null;

        try
        {
            String filename = mConfig.formFilename(JUNCTIONS);
            BufferedWriter writer = createBufferedWriter(filename, false);

            writer.write("Chromosome\tPosition\tOrientation\tJunctionFrags\tSupportFrags\tDiscordantFrags\tLowMapQualFrags\tMaxQual");
            writer.write("\tMaxSoftClip\tBaseDepth\tHasPolyAT\tIndel\tHotspot\tSoftClipBases\tInitialReadId");

            if(mConfig.TrackRemotes)
                writer.write("\tRemoteJunctionCount\tRemoteJunctions");

            writer.newLine();

            return writer;
        }
        catch(IOException e)
        {
            SV_LOGGER.error("failed to create junction writer: {}", e.toString());
        }

        return null;
    }

    public synchronized void writeJunctionData(final String chromosome, final List<JunctionData> junctions)
    {
        if(mJunctionWriter == null)
            return;

        try
        {
            for(JunctionData junctionData : junctions)
            {
                int maxMapQual = 0;
                int lowMapQualFrags = 0;
                int maxSoftClip = 0;
                String softClipBases = "";
                boolean hasPloyAT = false;
                boolean expectLeftClipped = junctionData.Orientation == NEG_ORIENT;

                for(PrepRead read : junctionData.ReadTypeReads.get(ReadType.JUNCTION))
                {
                    // check the read supports this junction (it can only support another junction)
                    boolean supportsJunction =
                            (expectLeftClipped && read.start() == junctionData.Position && CigarUtils.leftSoftClipped(read.cigar()))
                            || (!expectLeftClipped && read.end() == junctionData.Position && CigarUtils.rightSoftClipped(read.cigar()));

                    if(!supportsJunction)
                        continue;

                    if(ReadFilterType.isSet(read.filters(), ReadFilterType.MIN_MAP_QUAL))
                        ++lowMapQualFrags;

                    maxMapQual = Math.max(maxMapQual, read.mapQuality());

                    if(!junctionData.internalIndel())
                    {
                        if(!hasPloyAT)
                            hasPloyAT = PrepRead.hasPolyATSoftClip(read, expectLeftClipped);

                        int scLength = expectLeftClipped ?
                                read.cigar().getFirstCigarElement().getLength() : read.cigar().getLastCigarElement().getLength();

                        if(scLength > maxSoftClip)
                        {
                            maxSoftClip = scLength;
                            softClipBases = PrepRead.getSoftClippedBases(read.record(), expectLeftClipped);
                        }
                    }
                }

                int exactSupportFrags = junctionData.ExactSupportGroups.size();
                int discordantFrags = junctionData.SupportingGroups.size();

                for(PrepRead read : junctionData.ReadTypeReads.get(ReadType.EXACT_SUPPORT))
                {
                    maxMapQual = Math.max(maxMapQual, read.mapQuality());

                    if(ReadFilterType.isSet(read.filters(), ReadFilterType.MIN_MAP_QUAL))
                        ++lowMapQualFrags;
                }

                mJunctionWriter.write(String.format("%s\t%d\t%d\t%d\t%d\t%d\t%d\t%d",
                        chromosome, junctionData.Position, junctionData.Orientation, junctionData.junctionFragmentCount(),
                        exactSupportFrags, discordantFrags, lowMapQualFrags, maxMapQual));

                mJunctionWriter.write(String.format("\t%d\t%d\t%s\t%s\t%s\t%s\t%s",
                        maxSoftClip, junctionData.depth(), hasPloyAT, junctionData.internalIndel(), junctionData.hotspot(),
                        softClipBases, junctionData.topJunctionRead() != null ? junctionData.topJunctionRead().id() : "EXISTING"));

                if(mConfig.TrackRemotes)
                {
                    // RemoteChromosome:RemotePosition:RemoteOrientation;Fragments then separated by ';'
                    String remoteJunctionsStr = "";

                    if(!junctionData.RemoteJunctions.isEmpty())
                    {
                        Collections.sort(junctionData.RemoteJunctions, new RemoteJunction.RemoteJunctionSorter());

                        StringJoiner sj = new StringJoiner(ITEM_DELIM);

                        for(int i = 0; i < Math.min(junctionData.RemoteJunctions.size(), 10); ++i)
                        {
                            RemoteJunction remoteJunction = junctionData.RemoteJunctions.get(i);
                            sj.add(String.format("%s:%d:%d:%d",
                                    remoteJunction.Chromosome, remoteJunction.Position, remoteJunction.Orientation, remoteJunction.Fragments));
                            // junctionData.RemoteJunctions.forEach(x -> sj.add(format("%s:%d:%d", x.Chromosome, x.Position, x.Orientation)));
                        }
                        remoteJunctionsStr = sj.toString();
                    }

                    mJunctionWriter.write(String.format("\t%d\t%s", junctionData.RemoteJunctions.size(), remoteJunctionsStr));
                }

                mJunctionWriter.newLine();
            }
        }
        catch(IOException e)
        {
            SV_LOGGER.error(" failed to write junction data: {}", e.toString());
        }
    }

    private void writeBamRecords(final ReadGroup readGroup)
    {
        if(mBamWriter == null)
            return;

        // note additional filters for a read to be written to the BAM
        // - excessive low qual soft-clip bases
        // - above the poly-G(C) threshold
        // - cannot be a group of only supplementaries (in case the group is an unmarked duplicate)
        for(PrepRead read : readGroup.reads())
        {
            if(filterBamRecord(read))
                continue;

            if(read.written())
                continue;

            mBamWriter.writeRecord(read.record());
        }
    }

    private boolean filterBamRecord(final PrepRead read)
    {
        if(ReadFilterType.isSet(read.filters(), ReadFilterType.POLY_G_SC))
            return true;

        return false;
    }
}