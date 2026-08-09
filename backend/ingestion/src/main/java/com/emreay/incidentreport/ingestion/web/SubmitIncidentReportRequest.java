package com.emreay.incidentreport.ingestion.web;

/**
 * Body of a report submission.
 *
 * <p>Carries no bean validation annotations on purpose. The rules about what makes a text
 * acceptable — not blank, within the configured length — live in the service, because the
 * reprocessing path has no HTTP request behind it and must obey the same rules. Repeating them here
 * would create two places to change and one of them would eventually be forgotten.
 *
 * @param text the raw incident report, exactly as the user typed it
 */
public record SubmitIncidentReportRequest(String text) {
}
