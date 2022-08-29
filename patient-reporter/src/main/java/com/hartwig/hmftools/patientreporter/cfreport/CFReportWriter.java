package com.hartwig.hmftools.patientreporter.cfreport;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.util.Set;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.xml.ser.ToXmlGenerator;
import com.google.common.annotations.VisibleForTesting;
import com.google.gson.GsonBuilder;
import com.hartwig.hmftools.common.purple.PurpleQCStatus;
import com.hartwig.hmftools.patientreporter.OutputFileUtil;
import com.hartwig.hmftools.patientreporter.PatientReport;
import com.hartwig.hmftools.patientreporter.ReportWriter;
import com.hartwig.hmftools.patientreporter.algo.AnalysedPatientReport;
import com.hartwig.hmftools.patientreporter.algo.AnalysedPatientReporter;
import com.hartwig.hmftools.patientreporter.algo.AnalysedReportData;
import com.hartwig.hmftools.patientreporter.cfreport.chapters.analysed.CircosChapter;
import com.hartwig.hmftools.patientreporter.cfreport.chapters.analysed.ClinicalEvidenceOffLabelChapter;
import com.hartwig.hmftools.patientreporter.cfreport.chapters.analysed.ClinicalEvidenceOnLabelChapter;
import com.hartwig.hmftools.patientreporter.cfreport.chapters.analysed.DetailsAndDisclaimerChapter;
import com.hartwig.hmftools.patientreporter.cfreport.chapters.analysed.ExplanationChapter;
import com.hartwig.hmftools.patientreporter.cfreport.chapters.analysed.GenomicAlterationsChapter;
import com.hartwig.hmftools.patientreporter.cfreport.chapters.failed.QCFailChapter;
import com.hartwig.hmftools.patientreporter.cfreport.chapters.ReportChapter;
import com.hartwig.hmftools.patientreporter.cfreport.chapters.analysed.SummaryChapter;
import com.hartwig.hmftools.patientreporter.cfreport.chapters.analysed.TumorCharacteristicsChapter;
import com.hartwig.hmftools.patientreporter.cfreport.chapters.failed.QCFailDisclaimerChapter;
import com.hartwig.hmftools.patientreporter.cfreport.chapters.failed.QCFailPGXChapter;
import com.hartwig.hmftools.patientreporter.cfreport.chapters.panel.PanelChapter;
import com.hartwig.hmftools.patientreporter.cfreport.chapters.panel.PanelExplanationChapter;
import com.hartwig.hmftools.patientreporter.cfreport.chapters.panel.PanelQCFailChapter;
import com.hartwig.hmftools.patientreporter.cfreport.chapters.panel.SampleAndDisclaimerChapter;
import com.hartwig.hmftools.patientreporter.cfreport.chapters.panel.SampleAndDisclaimerChapterFail;
import com.hartwig.hmftools.patientreporter.panel.PanelFailReport;
import com.hartwig.hmftools.patientreporter.panel.PanelReport;
import com.hartwig.hmftools.patientreporter.qcfail.QCFailReport;
import com.hartwig.hmftools.patientreporter.xml.ImmutableImportWGS;
import com.hartwig.hmftools.patientreporter.xml.ImmutableSignature;
import com.hartwig.hmftools.patientreporter.xml.ImportWGS;
import com.itextpdf.kernel.events.PdfDocumentEvent;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.AreaBreak;
import com.itextpdf.layout.property.AreaBreakType;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.util.Strings;
import org.jetbrains.annotations.NotNull;

public class CFReportWriter implements ReportWriter {

    private static final Logger LOGGER = LogManager.getLogger(CFReportWriter.class);

    private final boolean writeToFile;

    @NotNull
    public static CFReportWriter createProductionReportWriter() {
        return new CFReportWriter(true);
    }

    @VisibleForTesting
    CFReportWriter(final boolean writeToFile) {
        this.writeToFile = writeToFile;
    }

    @Override
    public void writeAnalysedPatientReport(@NotNull AnalysedPatientReport report, @NotNull String outputFilePath) throws IOException {
        ReportChapter[] chapters = new ReportChapter[] { new SummaryChapter(report), new ClinicalEvidenceOnLabelChapter(report),
                new ClinicalEvidenceOffLabelChapter(report), new GenomicAlterationsChapter(report, report.sampleReport()),
                new TumorCharacteristicsChapter(report), new CircosChapter(report), new ExplanationChapter(),
                new DetailsAndDisclaimerChapter(report) };

        writeReport(report, chapters, outputFilePath);
    }

    @Override
    public void writeQCFailReport(@NotNull QCFailReport report, @NotNull String outputFilePath) throws IOException {
        if (report.reason().isDeepWGSDataAvailable()) {
            Set<PurpleQCStatus> purpleQCStatuses = report.purpleQC();
            if (purpleQCStatuses != null && !purpleQCStatuses.contains(PurpleQCStatus.FAIL_CONTAMINATION)) {
                writeReport(report,
                        new ReportChapter[] { new QCFailChapter(report), new QCFailPGXChapter(report),
                                new QCFailDisclaimerChapter(report) },
                        outputFilePath);
            } else {
                writeReport(report, new ReportChapter[] { new QCFailChapter(report), new QCFailDisclaimerChapter(report) }, outputFilePath);
            }

        } else {
            writeReport(report, new ReportChapter[] { new QCFailChapter(report), new QCFailDisclaimerChapter(report) }, outputFilePath);
        }

    }

    @Override
    public void writePanelAnalysedReport(@NotNull PanelReport report, @NotNull String outputFilePath) throws IOException {
        ReportChapter[] chapters =
                new ReportChapter[] { new PanelChapter(report), new PanelExplanationChapter(), new SampleAndDisclaimerChapter(report) };

        writePanel(report, chapters, outputFilePath);
    }

    @Override
    public void writePanelQCFailReport(@NotNull PanelFailReport report, @NotNull String outputFilePath) throws IOException {
        ReportChapter[] chapters = new ReportChapter[] { new PanelQCFailChapter(report), new SampleAndDisclaimerChapterFail(report) };
        writePanel(report, chapters, outputFilePath);
    }

    public void writeJsonFailedFile(@NotNull QCFailReport report, @NotNull String outputFilePath) throws IOException {
        writeReportDataToJson(report, outputFilePath);
    }

    public void writeJsonAnalysedFile(@NotNull AnalysedPatientReport report, @NotNull String outputFilePath) throws IOException {
        writeReportDataToJson(report, outputFilePath);
    }

    public void writeReportDataToJson(@NotNull PatientReport report, @NotNull String outputDirData) throws IOException {
        if (writeToFile) {
            String outputFileData = outputDirData + File.separator + OutputFileUtil.generateOutputFileNameForJson(report);
            BufferedWriter writer = new BufferedWriter(new FileWriter(outputFileData));
            writer.write(convertToJson(report));
            writer.close();
            LOGGER.info(" Created report data json file at {} ", outputFileData);
        }
    }

    public void writeXMLAnalysedFile(@NotNull AnalysedPatientReport report, @NotNull String outputFilePath) throws IOException, XMLStreamException {
        ImportWGS importWGS = ImmutableImportWGS.builder()
                .refNummerWgs(Strings.EMPTY)
                .wgsRedenAanvraag(Strings.EMPTY)
                .wgsGevrOndzTher(Strings.EMPTY)
                .wgsGevrOndzTherAnd(Strings.EMPTY)
                .wgsGevrOndzDiffDiag(Strings.EMPTY)
                .wgsGevrOndzDiffDiagAnd(Strings.EMPTY)
                .wgsRefNummer(Strings.EMPTY)
                .wgsPercNeoCellenEx(Strings.EMPTY)
                .wgsPercNeoCellenBeoord(Strings.EMPTY)
                .wgsPercNeoCellen(Strings.EMPTY)
                .wgsDatasheetSeqAnaPanel(Strings.EMPTY)
                .wgsPlatform(Strings.EMPTY)
                .wgsPlatformAnd(Strings.EMPTY)
                .wgsTumorPurity(report.genomicAnalysis().impliedPurity())
                .wgsGemTuPloid(report.genomicAnalysis().averageTumorPloidy())
                .reportableVariants(report.genomicAnalysis().reportableVariants())
                .gainsAndLosses(report.genomicAnalysis().gainsAndLosses())
                .geneFusions(report.genomicAnalysis().geneFusions())
                .signature(ImmutableSignature.builder()
                        .msscore(report.genomicAnalysis().microsatelliteIndelsPerMb())
                        .msstatus(report.genomicAnalysis().microsatelliteStatus())
                        .tumuload(report.genomicAnalysis().tumorMutationalLoad())
                        .tumulosta(report.genomicAnalysis().tumorMutationalLoadStatus())
                        .tutmb(report.genomicAnalysis().tumorMutationalBurden())
                        .horesco(report.genomicAnalysis().chordHrdValue())
                        .horestu(report.genomicAnalysis().chordHrdStatus())
                        .geenpv(Strings.EMPTY)
                        .build())
                .homozygousDisruptions(report.genomicAnalysis().homozygousDisruptions())
                .reportableViruses(report.genomicAnalysis().reportableViruses())
                .wgsCupAnalyse(Strings.EMPTY)
                .wgsDisclaimerTonen(Strings.EMPTY)
                .wgsMolecInter(Strings.EMPTY)
                .wgsKlinInter(Strings.EMPTY)
                .wgsAutoKMBP(Strings.EMPTY)
                .build();
        writeReportDataToXML(importWGS, outputFilePath, report);
    }

    public void writeReportDataToXML(@NotNull ImportWGS importWGS, @NotNull String outputDirData, @NotNull PatientReport report) throws IOException,
            XMLStreamException {
        if (writeToFile) {

            XmlMapper xmlMapper = new XmlMapper();
            xmlMapper.enable(SerializationFeature.WRITE_DATE_KEYS_AS_TIMESTAMPS);
            xmlMapper.enable(SerializationFeature.WRITE_DATES_WITH_ZONE_ID);

            String outputFileData = outputDirData + File.separator + OutputFileUtil.generateOutputFileNameForXML(report);
            xmlMapper.writerWithDefaultPrettyPrinter().writeValue(new File(outputFileData), importWGS);


            LOGGER.info(" Created report data xml file at {} ", outputFileData);

        }
    }

    @VisibleForTesting
    @NotNull
    public String convertToJson(@NotNull PatientReport report) {
        return new GsonBuilder().serializeNulls()
                .serializeSpecialFloatingPointValues()
                .setPrettyPrinting()
                .disableHtmlEscaping()
                .create()
                .toJson(report);
    }

    private void writeReport(@NotNull PatientReport patientReport, @NotNull ReportChapter[] chapters, @NotNull String outputFilePath)
            throws IOException {
        Document doc = initializeReport(outputFilePath, writeToFile);
        PdfDocument pdfDocument = doc.getPdfDocument();

        PageEventHandler pageEventHandler = new PageEventHandler(patientReport);
        pdfDocument.addEventHandler(PdfDocumentEvent.START_PAGE, pageEventHandler);

        for (int i = 0; i < chapters.length; i++) {
            ReportChapter chapter = chapters[i];

            pageEventHandler.pdfTitle(chapter.pdfTitle());
            pageEventHandler.chapterTitle(chapter.name());
            pageEventHandler.resetChapterPageCounter();
            pageEventHandler.sidebarType(!chapter.isFullWidth(), chapter.hasCompleteSidebar());

            if (i > 0) {
                doc.add(new AreaBreak(AreaBreakType.NEXT_PAGE));
            }
            chapter.render(doc);
        }

        pageEventHandler.writeDynamicTextParts(doc.getPdfDocument());

        doc.close();
        pdfDocument.close();

        if (writeToFile) {
            LOGGER.info("Created patient report at {}", outputFilePath);
        } else {
            LOGGER.info("Successfully generated in-memory patient report");
        }
    }

    public void writeJsonPanelFile(@NotNull PanelReport report, @NotNull String outputFilePath) throws IOException {
        writeReportDataToJson(report, outputFilePath);
    }

    public void writeJsonPanelFailedFile(@NotNull PanelFailReport report, @NotNull String outputFilePath) throws IOException {
        writeReportDataToJson(report, outputFilePath);
    }

    public void writeReportDataToJson(@NotNull com.hartwig.hmftools.patientreporter.PanelReport report, @NotNull String outputDirData)
            throws IOException {
        if (writeToFile) {
            String outputFileData = outputDirData + File.separator + OutputFileUtil.generateOutputFileNameForJsonPanel(report);
            BufferedWriter writer = new BufferedWriter(new FileWriter(outputFileData));
            writer.write(convertToJson(report));
            writer.close();
            LOGGER.info(" Created report data json file at {} ", outputFileData);
        }
    }

    @VisibleForTesting
    @NotNull
    public String convertToJson(@NotNull com.hartwig.hmftools.patientreporter.PanelReport report) {
        return new GsonBuilder().serializeNulls()
                .serializeSpecialFloatingPointValues()
                .setPrettyPrinting()
                .disableHtmlEscaping()
                .create()
                .toJson(report);
    }

    private void writePanel(@NotNull com.hartwig.hmftools.patientreporter.PanelReport patientReport, @NotNull ReportChapter[] chapters,
            @NotNull String outputFilePath) throws IOException {
        Document doc = initializeReport(outputFilePath, writeToFile);
        PdfDocument pdfDocument = doc.getPdfDocument();

        PageEventHandlerPanel pageEventHandler = new PageEventHandlerPanel(patientReport);
        pdfDocument.addEventHandler(PdfDocumentEvent.START_PAGE, pageEventHandler);

        for (int i = 0; i < chapters.length; i++) {
            ReportChapter chapter = chapters[i];

            pageEventHandler.pdfTitle(chapter.pdfTitle());
            pageEventHandler.chapterTitle(chapter.name());
            pageEventHandler.resetChapterPageCounter();
            pageEventHandler.sidebarType(!chapter.isFullWidth(), chapter.hasCompleteSidebar());

            if (i > 0) {
                doc.add(new AreaBreak(AreaBreakType.NEXT_PAGE));
            }
            chapter.render(doc);
        }

        pageEventHandler.writeDynamicTextParts(doc.getPdfDocument());

        doc.close();
        pdfDocument.close();

        if (writeToFile) {
            LOGGER.info("Created patient report at {}", outputFilePath);
        } else {
            LOGGER.info("Successfully generated in-memory patient report");
        }
    }

    @NotNull
    private static Document initializeReport(@NotNull String outputFilePath, boolean writeToFile) throws IOException {
        PdfWriter writer;
        if (writeToFile) {
            if (Files.exists(new File(outputFilePath).toPath())) {
                throw new IOException("Could not write " + outputFilePath + " as it already exists.");
            }

            writer = new PdfWriter(outputFilePath);
        } else {
            // Write output to output stream where it is effectively ignored.
            writer = new PdfWriter(new ByteArrayOutputStream());
        }

        PdfDocument pdf = new PdfDocument(writer);
        pdf.setDefaultPageSize(PageSize.A4);
        pdf.getDocumentInfo().setTitle(ReportResources.METADATA_TITLE);
        pdf.getDocumentInfo().setAuthor(ReportResources.METADATA_AUTHOR);

        Document document = new Document(pdf);
        document.setMargins(ReportResources.PAGE_MARGIN_TOP,
                ReportResources.PAGE_MARGIN_RIGHT,
                ReportResources.PAGE_MARGIN_BOTTOM,
                ReportResources.PAGE_MARGIN_LEFT);

        return document;
    }
}
