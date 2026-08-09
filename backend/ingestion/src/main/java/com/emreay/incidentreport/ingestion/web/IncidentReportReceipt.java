package com.emreay.incidentreport.ingestion.web;

import com.emreay.incidentreport.ingestion.domain.RawIncidentReport;

import java.time.Instant;

/**
 * The answer to a submission: proof the text was stored, and nothing else (ADR-021).
 *
 * <p>Not the analysis result. What was extracted is read separately, through
 * {@code GET /incidents?rawReportId=...}, because that data belongs to the module that produces it.
 * Returning it here would tie this contract to a shape this module does not own — and would make
 * the submission look like it must wait for the analysis, which is exactly the coupling being
 * avoided.
 *
 * <p>One extra request is the price. It buys the freedom to move analysis off the request thread,
 * or onto a broker, without a single client having to change: the query simply answers "not
 * analysed yet" for a while, and the live stream says when that changes.
 *
 * @param id          identifier to fetch this report again, and the key that ties structured
 *                    records back to it (FR-08)
 * @param submittedAt when it was accepted; also the reference date for relative dates (ADR-014)
 */
public record IncidentReportReceipt(String id, Instant submittedAt) {

    public static IncidentReportReceipt from(RawIncidentReport report) {
        return new IncidentReportReceipt(report.id(), report.submittedAt());
    }
}
