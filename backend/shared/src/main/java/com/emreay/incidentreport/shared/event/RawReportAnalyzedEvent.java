package com.emreay.incidentreport.shared.event;

import java.util.List;
import java.util.Objects;

/**
 * Published once a raw report has been analysed, so the side that stored the text learns how it
 * went.
 *
 * <p>The two modules answer each other through the shared kernel rather than calling across: this
 * event travels back the way {@link RawReportSubmittedEvent} travelled out. Neither module knows
 * the other exists, which is what keeps them separately deployable (ADR-001, ADR-003).
 *
 * <p>Delivery is synchronous, so by the time the submission returns the report already carries its
 * outcome — the caller sees warnings in the same response rather than having to poll.
 *
 * @param rawReportId   the report that was analysed
 * @param incidentCount how many structured records it produced; zero is a legitimate answer
 * @param warnings      what the user should know about a partial result — an unrecognised event
 *                      type, a date that had to be assumed (FR-09)
 */
public record RawReportAnalyzedEvent(String rawReportId, int incidentCount, List<String> warnings) {

    public RawReportAnalyzedEvent {
        Objects.requireNonNull(rawReportId, "rawReportId");
        if (rawReportId.isBlank()) {
            throw new IllegalArgumentException("rawReportId must not be blank");
        }
        if (incidentCount < 0) {
            throw new IllegalArgumentException("incidentCount must not be negative, got " + incidentCount);
        }
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
