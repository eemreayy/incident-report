package com.emreay.incidentreport.shared.event;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Published when the analysis side has finished a report and the records derived from it are the
 * ones that now stand.
 *
 * <p>One event per analysed report, not one per record. A single text routinely produces several
 * records — the third sample text produces three — and announcing each separately would make a
 * listener refresh three times for one submission, or force it to invent a debounce. The unit of
 * change here is the report, so that is the unit that is announced.
 *
 * <p>It is emitted after every successful analysis, including one that produced nothing. A
 * reprocess that now extracts fewer records than before still changed what a query answers, and a
 * client showing the old rows has to be told. A <em>failed</em> analysis emits nothing: it rolls
 * back, so no record anywhere changed, and the submitter reads the failure through the query
 * endpoint (ADR-021) rather than waiting for the stream.
 *
 * <p>This travels in the direction ingestion → analysis → realtime and never back. The analysing
 * module owns these records, so it is the one that announces them; nothing in this event describes
 * data belonging to another module.
 *
 * @param rawReportId the text these records came from — the correlation key that ties a submission
 *                    to its records in logs, queries and this stream (FR-08, NFR-09)
 * @param analyzedAt  when the analysis that produced them ran
 * @param incidents   the records that now exist for this report, possibly none
 */
public record IncidentRecordsProducedEvent(String rawReportId, Instant analyzedAt, List<IncidentSignal> incidents) {

    public IncidentRecordsProducedEvent {
        Objects.requireNonNull(rawReportId, "rawReportId");
        if (rawReportId.isBlank()) {
            throw new IllegalArgumentException("rawReportId must not be blank");
        }
        Objects.requireNonNull(analyzedAt, "analyzedAt");
        incidents = List.copyOf(Objects.requireNonNull(incidents, "incidents"));
    }
}
