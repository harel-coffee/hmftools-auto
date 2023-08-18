package com.hartwig.hmftools.common.utils.file;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import com.hartwig.hmftools.common.utils.config.ConfigBuilder;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.jetbrains.annotations.NotNull;

public final class FileWriterUtils
{
    public static final String OUTPUT_DIR = "output_dir";
    public static final String OUTPUT_DIR_DESC = "Output directory";
    public static final String OUTPUT_ID = "output_id";
    public static final String OUTPUT_ID_DESC = "Output file suffix";

    public static void addOutputOptions(final ConfigBuilder configBuilder) { addOutputOptions(configBuilder, false); }

    public static void addOutputOptions(final ConfigBuilder configBuilder, boolean checkExists)
    {
        addOutputDir(configBuilder, checkExists);
        configBuilder.addConfigItem(OUTPUT_ID, OUTPUT_ID_DESC);
    }

    public static void addOutputDir(final ConfigBuilder configBuilder)
    {
        configBuilder.addConfigItem(OUTPUT_DIR, OUTPUT_DIR_DESC);
    }

    public static void addOutputDir(final ConfigBuilder configBuilder, boolean checkExists)
    {
        if(checkExists)
            configBuilder.addPath(OUTPUT_DIR, true, OUTPUT_DIR_DESC);
        else
            configBuilder.addConfigItem(OUTPUT_DIR, OUTPUT_DIR_DESC);
    }

    public static String parseOutputDir(final ConfigBuilder configBuilder)
    {
        String outputDir = configBuilder.getValue(OUTPUT_DIR);
        if(outputDir == null)
            return null;

        return checkAddDirSeparator(outputDir);
    }

    public static void addOutputDir(final Options options)
    {
        options.addOption(OUTPUT_DIR, true, OUTPUT_DIR_DESC);
    }

    public static void addOutputId(final Options options)
    {
        options.addOption(OUTPUT_ID, true, OUTPUT_ID_DESC);
    }

    public static void addOutputOptions(final Options options)
    {
        addOutputDir(options);
        addOutputId(options);
    }

    public static String parseOutputDir(final CommandLine cmd)
    {
        String outputDir = cmd.getOptionValue(OUTPUT_DIR);
        if(outputDir == null)
            return null;

        return checkAddDirSeparator(outputDir);
    }

    public static boolean checkCreateOutputDir(final String outputDirPath)
    {
        if (Files.exists(Paths.get(outputDirPath)))
            return true;

        final File outputDir = new File(outputDirPath);
        return outputDir.mkdirs();
    }

    public static String checkAddDirSeparator(final String outputDir)
    {
        if(outputDir == null || outputDir.isEmpty())
            return outputDir;

        if(outputDir.endsWith(File.separator))
            return outputDir;

        return outputDir + File.separator;
    }

    // Note: if filename ends with .gz returns a Gzipped buffered writer
    @NotNull
    public static BufferedWriter createBufferedWriter(final String outputFile, boolean appendIfExists) throws IOException
    {
        OutputStream outputStream = new FileOutputStream(outputFile, appendIfExists);
        if(outputFile.endsWith(".gz"))
        {
            outputStream = new GZIPOutputStream(outputStream);
        }
        return new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
    }

    // overwrite if file exists
    // Note: if filename ends with .gz returns a Gzipped buffered writer
    @NotNull
    public static BufferedWriter createBufferedWriter(final String outputFile) throws IOException
    {
        return createBufferedWriter(outputFile, false);
    }

    // Note: if filename ends with .gz returns a Gzipped buffered reader
    @NotNull
    public static BufferedReader createBufferedReader(final String filename) throws IOException
    {
        InputStream inputStream = new FileInputStream(filename);
        if(filename.endsWith(".gz"))
        {
            inputStream = new GZIPInputStream(inputStream);
        }
        return new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
    }

    @NotNull
    public static BufferedReader createGzipBufferedReader(final String filename) throws IOException
    {
        InputStream inputStream = new FileInputStream(filename);
        inputStream = new GZIPInputStream(inputStream);
        return new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
    }

    @NotNull
    public static BufferedWriter createGzipBufferedWriter(final String outputFile) throws IOException
    {
        OutputStream outputStream = new FileOutputStream(outputFile);
        outputStream = new GZIPOutputStream(outputStream);
        return new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
    }

    public static void closeBufferedWriter(BufferedWriter writer)
    {
        if(writer == null)
            return;
        try
        {
            writer.close();
        }
        catch (IOException e)
        {
            throw new IllegalStateException("Could not close buffered writer: " + writer + ": " + e.getMessage());
        }
    }
}
