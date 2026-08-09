package com.emreay.incidentreport.ingestion.web;

import com.emreay.incidentreport.ingestion.domain.ProcessingStatus;
import com.emreay.incidentreport.ingestion.domain.RawIncidentReport;

import java.time.Instant;
import java.util.List;

/**
 * A raw report as the API shows it.
 *
 * <p>A DTO rather than the document itself, so the storage shape and the published contract can
 * move independently — and so nothing internal escapes by accident.
 *
 * <p>Two fields of the document are deliberately not here. {@code failureReason} holds an exception
 * type and message; that belongs in the log and in the store for whoever operates the system, not
 * in a response where it would leak internals to any caller. {@code analyzedAt} is omitted for the
 * same reason it would be noise: what a client can act on is the status.
 *
 * @param id          identifier to fetch this report again, and the key that ties structured
 *                    records back to it
 * @param text        the submitted text, unchanged
 * @param submittedAt when it was accepted; also the reference date for relative dates (ADR-014)
 * @param status      where the report stands in the pipeline
 * @param warnings    what the caller should know about a partial result — an unrecognised event
 *                    type, an analysis that could not run (FR-09)
 */
public record IncidentReportResponse(String id,
                                     String text,
                                     Instant submittedAt,
                                     ProcessingStatus status,
                                     List<String> warnings) {

    /**
     * A report whose analysis failed still has to say so. Returning it with an empty warning list
     * would leave the caller staring at a stored report with no structured data and no explanation.
     */
    private static final String ANALYSIS_FAILED =
            "The report was stored, but analysis failed. It can be reprocessed once the cause is fixed.";

    public static IncidentReportResponse from(RawIncidentReport report) {
        List<String> warnings = report.status() == ProcessingStatus.FAILED
                ? List.of(ANALYSIS_FAILED)
                : report.warnings();
        return new IncidentReportResponse(report.id(), report.rawText(), report.submittedAt(),
                report.status(), warnings);
    }
}
