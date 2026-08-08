package com.emreay.incidentreport.shared.event;

import java.time.Instant;
import java.util.Objects;

/**
 * Published once a raw report has been stored, and the only channel through which its text reaches
 * the analysis side.
 *
 * <p>Carrying the text rather than just the id is deliberate. The analysing module cannot read it
 * back from MongoDB — that store belongs to ingestion (ADR-002) — so an id-only event would leave
 * the listener with nothing to work on. The event is the boundary.
 *
 * <p>{@code submittedAt} travels with it for the same reason: it is the reference date against
 * which relative expressions such as "son 24 saatte" are resolved, and against which a report with
 * no date at all is dated (ADR-014). Resolving them against the current time instead would make
 * reprocessing shift historical records.
 *
 * <p>Delivery is synchronous and in-process (ADR-003). Should this ever move onto a broker, this
 * record is the payload that would be serialised — which is why it holds plain values and nothing
 * from either module.
 *
 * @param rawReportId MongoDB ObjectId of the stored report, as a hex string
 * @param rawText     the submitted text, exactly as stored
 * @param submittedAt when the text was accepted
 */
public record RawReportSubmittedEvent(String rawReportId, String rawText, Instant submittedAt) {

    public RawReportSubmittedEvent {
        requireNotBlank(rawReportId, "rawReportId");
        requireNotBlank(rawText, "rawText");
        Objects.requireNonNull(submittedAt, "submittedAt");
    }

    private static void requireNotBlank(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
