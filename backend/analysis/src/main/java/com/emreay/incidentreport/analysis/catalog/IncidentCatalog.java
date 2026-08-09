package com.emreay.incidentreport.analysis.catalog;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Everything the system knows how to recognise.
 *
 * <p>Loaded from configuration at startup and immutable afterwards (ADR-007). Classification
 * (T-13) and metric matching (T-14) read it; the metadata endpoint publishes it so the interface
 * has no catalog of its own to keep in step.
 *
 * <p>Order is preserved from the file, because it is the order the interface offers choices in and
 * whoever edits the catalog is the one who should decide it.
 */
public final class IncidentCatalog {

    /**
     * What a report is filed under when nothing matched (ADR-006).
     *
     * <p>Deliberately not a catalog entry: it is the absence of one. The loader rejects a catalog
     * that defines it, because a definition would suggest there are words that mean "unrecognised".
     */
    public static final String UNCLASSIFIED_EVENT_TYPE = "OTHER";

    private final List<EventTypeDefinition> eventTypes;
    private final Map<String, EventTypeDefinition> byKey;

    IncidentCatalog(List<EventTypeDefinition> eventTypes) {
        this.eventTypes = List.copyOf(eventTypes);
        Map<String, EventTypeDefinition> index = new LinkedHashMap<>();
        this.eventTypes.forEach(eventType -> index.put(eventType.key(), eventType));
        this.byKey = Map.copyOf(index);
    }

    public List<EventTypeDefinition> eventTypes() {
        return eventTypes;
    }

    public Optional<EventTypeDefinition> eventType(String key) {
        return Optional.ofNullable(byKey.get(key));
    }

    public boolean recognises(String eventTypeKey) {
        return byKey.containsKey(eventTypeKey);
    }

    public int size() {
        return eventTypes.size();
    }

    @Override
    public String toString() {
        return "IncidentCatalog" + byKey.keySet();
    }
}
