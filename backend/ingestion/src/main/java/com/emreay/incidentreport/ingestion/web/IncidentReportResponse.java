package com.emreay.incidentreport.ingestion.web;

import com.emreay.incidentreport.ingestion.domain.RawIncidentReport;

import java.time.Instant;

/**
 * A raw report as the API shows it: the text, and when it arrived.
 *
 * <p>A DTO rather than the document itself, so the storage shape and the published contract can
 * move independently — and so nothing internal escapes by accident.
 *
 * <p>Carries no analysis status and no warnings. Those describe how the text was read, not what was
 * submitted, and they belong to the module that produces them (ADR-021). A caller who wants them
 * asks {@code GET /incidents?rawReportId=...}, which is also how the raw report to derived records
 * direction of FR-08 is served.
 *
 * @param id          identifier, and the key that ties structured records back to this text
 * @param text        the submitted text, unchanged
 * @param submittedAt when it was accepted; also the reference date for relative dates (ADR-014)
 */
public record IncidentReportResponse(String id, String text, Instant submittedAt) {

    public static IncidentReportResponse from(RawIncidentReport report) {
        return new IncidentReportResponse(report.id(), report.rawText(), report.submittedAt());
    }
}
