package com.hartwig.hmftools.patientreporter.cfreport.data;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;

import com.google.common.collect.Sets;
import com.hartwig.hmftools.common.protect.ProtectEvidence;
import com.hartwig.hmftools.common.protect.ProtectSource;
import com.hartwig.hmftools.common.serve.actionability.EvidenceLevel;
import com.hartwig.hmftools.patientreporter.cfreport.ReportResources;
import com.itextpdf.kernel.pdf.action.PdfAction;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Text;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

public final class EvidenceItems {

    private static final Logger LOGGER = LogManager.getLogger(EvidenceItems.class);
    private static final String NONE = "None";

    private EvidenceItems() {
    }

    public static int uniqueEventCount(@NotNull List<ProtectEvidence> evidenceItems) {
        Set<String> events = Sets.newHashSet();
        for (ProtectEvidence evidence : evidenceItems) {
            String event = evidence.gene() != null ? evidence.gene() + " " + evidence.event() : evidence.event();
            if (evidence.level().equals(EvidenceLevel.A) || evidence.level().equals(EvidenceLevel.B)) {
                events.add(event);
            }
        }
        return events.size();
    }

    @NotNull
    public static String onLabelTreatmentString(@NotNull List<ProtectEvidence> protect) {
        return treatmentString(protect, true, false);
    }

    @NotNull
    private static String treatmentString(@NotNull List<ProtectEvidence> evidences, boolean requireOnLabel, boolean reportGermline) {
        Set<EvidenceLevel> levels = Sets.newTreeSet(Comparator.naturalOrder());
        Set<String> treatments = Sets.newHashSet();
        for (ProtectEvidence evidence : evidences) {
            if (evidence.onLabel() == requireOnLabel && (reportGermline || !evidence.germline())) {
                if (evidence.level().equals(EvidenceLevel.A) || evidence.level().equals(EvidenceLevel.B)) {
                    treatments.add(evidence.treatment());
                    levels.add(evidence.level());
                }
            }
        }

        if (treatments.isEmpty()) {
            return NONE;
        } else {
            StringJoiner joiner = new StringJoiner(", ");
            for (EvidenceLevel level : levels) {
                if (level.equals(EvidenceLevel.A) || level.equals(EvidenceLevel.B)) {
                    joiner.add(level.toString());
                }
            }

            return treatments.size() + " (" + joiner.toString() + ")";
        }
    }

    @NotNull
    public static Paragraph createLinksPublications(@NotNull ProtectEvidence evidence) {
        Paragraph paragraphPublications = new Paragraph();
        int number = 0;
        for (ProtectSource source : evidence.sources()) {
            for (String url : source.evidenceUrls()) {
                if (!url.contains("google")) {
                    //Google urls are filtered out
                    number += 1;
                    if (!paragraphPublications.isEmpty()) {
                        paragraphPublications.add(new Text(", "));
                    }

                    paragraphPublications.add(new Text(Integer.toString(number)).addStyle(ReportResources.urlStyle())
                            .setAction(PdfAction.createURI(url))).setFixedLeading(ReportResources.BODY_TEXT_LEADING);
                }
            }
        }

        return paragraphPublications;
    }
}