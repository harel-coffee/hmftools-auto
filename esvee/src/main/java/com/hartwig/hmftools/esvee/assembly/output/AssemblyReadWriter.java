package com.hartwig.hmftools.esvee.assembly.output;

import static java.lang.String.format;

import static com.hartwig.hmftools.common.utils.file.FileDelimiters.TSV_DELIM;
import static com.hartwig.hmftools.common.utils.file.FileWriterUtils.closeBufferedWriter;
import static com.hartwig.hmftools.common.utils.file.FileWriterUtils.createBufferedWriter;
import static com.hartwig.hmftools.esvee.AssemblyConfig.SV_LOGGER;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.StringJoiner;

import com.hartwig.hmftools.esvee.AssemblyConfig;
import com.hartwig.hmftools.esvee.assembly.types.AssemblySupport;
import com.hartwig.hmftools.esvee.assembly.types.JunctionAssembly;
import com.hartwig.hmftools.esvee.assembly.read.Read;

public class AssemblyReadWriter
{
    private final AssemblyConfig mConfig;

    private final BufferedWriter mWriter;

    public AssemblyReadWriter(final AssemblyConfig config)
    {
        mConfig = config;
        mWriter = initialiseWriter();
    }

    public void close() { closeBufferedWriter(mWriter);}

    private BufferedWriter initialiseWriter()
    {
        if(!mConfig.WriteTypes.contains(WriteType.READS))
            return null;

        try
        {
            BufferedWriter writer = createBufferedWriter(mConfig.outputFilename(WriteType.READS));

            StringJoiner sj = new StringJoiner(TSV_DELIM);

            sj.add("AssemblyId");
            sj.add("AssemblyInfo"); // for now junction info, in time phased assembly info

            sj.add("ReadId");
            sj.add("SupportType");
            sj.add("IsRef");
            sj.add("Chromosome");
            sj.add("PosStart");
            sj.add("PosEnd");
            sj.add("Cigar");
            sj.add("OrigCigar");
            sj.add("InsertSize");
            sj.add("MateChr");
            sj.add("MatePosStart");
            sj.add("MatePosEnd");
            sj.add("Flags");
            sj.add("FirstInPair");
            sj.add("Reversed");
            sj.add("Unmapped");
            sj.add("MateUnmapped");
            sj.add("MateReversed");
            sj.add("Supplementary");
            sj.add("SuppData");

            sj.add("AssemblyIndex");
            sj.add("Matches");
            sj.add("Mismatches");
            sj.add("TrimCount");

            writer.write(sj.toString());
            writer.newLine();

            return writer;
        }
        catch(IOException e)
        {
            SV_LOGGER.error("failed to initialise assembly writer: {}", e.toString());
            return null;
        }
    }

    public synchronized void writeAssemblyReads(final JunctionAssembly assembly)
    {
        if(mWriter == null)
            return;

        try
        {
            String assemblyInfo = format("%s", assembly.junction().toString());

            for(AssemblySupport support : assembly.support())
            {
                StringJoiner sj = new StringJoiner(TSV_DELIM);

                sj.add(String.valueOf(assembly.id()));
                sj.add(assemblyInfo);

                final Read read = support.read();

                sj.add(read.id());
                sj.add(support.type().toString());
                sj.add(String.valueOf(read.isReference()));
                sj.add(read.chromosome());
                sj.add(String.valueOf(read.alignmentStart()));
                sj.add(String.valueOf(read.alignmentEnd()));
                sj.add(read.cigarString());
                sj.add(read.originalCigarString());
                sj.add(String.valueOf(read.insertSize()));

                sj.add(read.mateChromosome());
                sj.add(String.valueOf(read.mateAlignmentStart()));
                sj.add(String.valueOf(read.mateAlignmentEnd()));

                sj.add(String.valueOf(read.getFlags()));
                sj.add(String.valueOf(read.firstInPair()));
                sj.add(String.valueOf(read.negativeStrand()));
                sj.add(String.valueOf(read.isUnmapped()));
                sj.add(String.valueOf(read.isMateMapped()));
                sj.add(String.valueOf(read.mateNegativeStrand()));

                sj.add(String.valueOf(read.bamRecord().getSupplementaryAlignmentFlag()));

                if(read.hasSupplementary())
                {
                    sj.add(format("%s:%d:%s",
                            read.supplementaryData().Chromosome, read.supplementaryData().Position, read.supplementaryData().Cigar));
                }
                else
                {
                    sj.add("");
                }
                sj.add(String.valueOf(support.assemblyIndex()));
                sj.add(String.valueOf(support.junctionMatches()));
                sj.add(String.valueOf(support.mismatchCount()));
                sj.add(String.valueOf(read.baseTrimCount()));

                mWriter.write(sj.toString());
                mWriter.newLine();
            }
        }
        catch(IOException e)
        {
            SV_LOGGER.error("failed to write assembly reads: {}", e.toString());
        }
    }
}