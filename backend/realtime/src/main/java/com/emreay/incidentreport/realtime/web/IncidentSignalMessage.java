package com.emreay.incidentreport.realtime.web;

import com.emreay.incidentreport.shared.event.IncidentRecordsProducedEvent;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * What one message on the stream looks like on the wire.
 *
 * <p>It restates the internal event rather than serialising it, which is the same rule the rest of
 * the API follows: the published contract is a type this module owns, so the event modules pass
 * between themselves can change shape without breaking a browser that is connected right now.
 *
 * <p>A client reads it to answer one question — "does this concern what I am looking at?" — and
 * then refetches from the query endpoints. Everything needed to answer it is here, and nothing
 * needed to draw a row is (ADR-021, PRD §8.2/C-8).
 *
 * @param rawReportId the submission these records came from, so a client that has just submitted
 *                    can recognise its own (FR-08)
 * @param analyzedAt  when the run that produced them finished
 * @param incidents   the records that now stand for that report; empty when a reprocess left none
 */
public record IncidentSignalMessage(String rawReportId, Instant analyzedAt, List<SignalledIncident> incidents) {

    /**
     * @param provinceCodes provinces this record answers a province filter for: one code when it
     *                      belongs to a single province, several when the figure is shared between
     *                      them, none when the text named no province. Sorted, so a message is
     *                      identical whatever order the set was built in.
     */
    public record SignalledIncident(long incidentId,
                                    LocalDate occurredOn,
                                    String eventType,
                                    List<Short> provinceCodes) {
    }

    public static IncidentSignalMessage from(IncidentRecordsProducedEvent event) {
        return new IncidentSignalMessage(
                event.rawReportId(),
                event.analyzedAt(),
                event.incidents().stream()
                        .map(signal -> new SignalledIncident(
                                signal.incidentId(),
                                signal.occurredOn(),
                                signal.eventType(),
                                signal.provinceCodes().stream().sorted(Comparator.naturalOrder()).toList()))
                        .toList());
    }
}
