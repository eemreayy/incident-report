package com.emreay.incidentreport.shared.event;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;

/**
 * Enough about one produced record for a listener to judge whether anyone cares — and no more.
 *
 * <p>It names the record rather than describing it: an id to fetch it with, and the three
 * dimensions every view is filtered by. Metrics, keywords, classification and the source text are
 * all absent on purpose. A signal that carried them would let a client draw a row from the stream
 * alone, and from that moment the stream would be a data source: the table's columns would be part
 * of its contract, and a missed event would mean missing data rather than a late refresh
 * (ADR-021, PRD §8.2/C-8).
 *
 * <p>{@code provinceCodes} answers the province question for all three scopes without naming the
 * scope. A record belonging to one province carries that one code; a figure the text gives across
 * several carries all of them, because a province filter returns it too (ADR-033); a record whose
 * text named no province carries none. So "is this in the provinces I am looking at?" is a set
 * intersection either way — which is the only question a listener asks here.
 *
 * @param incidentId   identifier of the stored record, as {@code GET /incidents/{id}} takes it
 * @param occurredOn   the date the record is filed under, however it was resolved
 * @param eventType    catalog key, not a label; labels come from the metadata endpoint (ADR-007)
 * @param provinceCodes provinces this record would answer a filter for; empty when the text named
 *                      none
 */
public record IncidentSignal(long incidentId,
                             LocalDate occurredOn,
                             String eventType,
                             Set<Short> provinceCodes) {

    public IncidentSignal {
        Objects.requireNonNull(occurredOn, "occurredOn");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(provinceCodes, "provinceCodes");
        // Copied rather than referenced: the caller holds a mutable collection of its own, and a
        // signal that changed after publication would describe something other than what was stored.
        provinceCodes = Set.copyOf(provinceCodes);
    }
}
