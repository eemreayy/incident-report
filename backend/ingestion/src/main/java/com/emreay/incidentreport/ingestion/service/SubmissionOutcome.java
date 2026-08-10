package com.emreay.incidentreport.ingestion.service;

import com.emreay.incidentreport.ingestion.domain.RawIncidentReport;

/**
 * What happened to a submission: the report that now holds the text, and whether this call is what
 * put it there.
 *
 * <p>The distinction exists for one reason — the caller's answer differs. A stored text is
 * <em>created</em>; a text that was already stored is not created a second time, and saying so is
 * how a client can tell its user that this report is not new (ADR-035). It is not an error either
 * way: in both cases the text is safely recorded, which is what the caller asked for.
 *
 * @param report      the report holding the text, either way
 * @param newlyStored {@code true} when this call wrote it, {@code false} when it was already there
 */
public record SubmissionOutcome(RawIncidentReport report, boolean newlyStored) {

    static SubmissionOutcome stored(RawIncidentReport report) {
        return new SubmissionOutcome(report, true);
    }

    static SubmissionOutcome alreadyStored(RawIncidentReport report) {
        return new SubmissionOutcome(report, false);
    }
}
