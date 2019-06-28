package com.hartwig.hmftools.linx.visualiser.file;

import static java.util.stream.Collectors.toList;

import static com.hartwig.hmftools.linx.visualiser.file.VisCopyNumberFile.DELIMITER;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.StringJoiner;

import com.google.common.collect.Lists;

import org.jetbrains.annotations.NotNull;

public class VisProteinDomainFile
{
    public final String SampleId;
    public final int ClusterId;
    public final String Transcript;
    public final String Chromosome;
    public final long Start;
    public final long End;
    public final String Info;

    public VisProteinDomainFile(final String sampleId, int clusterId, final String transcript, final String chromosome, long start, long end, final String info)
    {
        SampleId = sampleId;
        ClusterId = clusterId;
        Transcript = transcript;
        Chromosome = chromosome;
        Info = info;
        Start = start;
        End = end;
    }

    private static final String FILE_EXTENSION = ".linx.vis_protein_domain.tsv";

    @NotNull
    public static String generateFilename(@NotNull final String basePath, @NotNull final String sample)
    {
        return basePath + File.separator + sample + FILE_EXTENSION;
    }

    @NotNull
    public static List<VisProteinDomainFile> read(final String filePath) throws IOException
    {
        return fromLines(Files.readAllLines(new File(filePath).toPath()));
    }

    public static void write(@NotNull final String filename, @NotNull List<VisProteinDomainFile> cnDataList) throws IOException
    {
        Files.write(new File(filename).toPath(), toLines(cnDataList));
    }

    @NotNull
    static List<String> toLines(@NotNull final List<VisProteinDomainFile> cnDataList)
    {
        final List<String> lines = Lists.newArrayList();
        lines.add(header());
        cnDataList.stream().map(x -> toString(x)).forEach(lines::add);
        return lines;
    }

    @NotNull
    static List<VisProteinDomainFile> fromLines(@NotNull List<String> lines)
    {
        return lines.stream().filter(x -> !x.startsWith("SampleId")).map(VisProteinDomainFile::fromString).collect(toList());
    }

    @NotNull
    public static String header()
    {
        return new StringJoiner(DELIMITER)
                .add("SampleId")
                .add("ClusterId")
                .add("Transcript")
                .add("Chromosome")
                .add("Start")
                .add("End")
                .add("Info")
                .toString();
    }

    @NotNull
    public static String toString(@NotNull final VisProteinDomainFile geData)
    {
        return new StringJoiner(DELIMITER)
                .add(String.valueOf(geData.SampleId))
                .add(String.valueOf(geData.ClusterId))
                .add(String.valueOf(geData.Transcript))
                .add(String.valueOf(geData.Chromosome))
                .add(String.valueOf(geData.Start))
                .add(String.valueOf(geData.End))
                .add(String.valueOf(geData.Info))
                .toString();
    }

    @NotNull
    private static VisProteinDomainFile fromString(@NotNull final String tiData)
    {
        String[] values = tiData.split(DELIMITER);

        int index = 0;

        return new VisProteinDomainFile(
                values[index++],
                Integer.valueOf(values[index++]),
                values[index++],
                values[index++],
                Long.valueOf(values[index++]),
                Long.valueOf(values[index++]),
                values[index++]);
    }
}
