package com.emreay.incidentreport.analysis.web;

import com.emreay.incidentreport.analysis.catalog.IncidentCatalog;
import com.emreay.incidentreport.analysis.domain.Province;

import java.util.List;

/**
 * Everything the interface needs to build its choices, in one call (FR-16).
 *
 * <p>It exists so the frontend has no catalog of its own. If it held one, adding an event type
 * would mean a configuration change <em>and</em> a frontend release, and the two would drift apart
 * in between — which is exactly what ADR-007 set out to avoid.
 *
 * <p>Labels are included for the same reason. Sending keys alone would push the key-to-Turkish
 * mapping into the interface, and that mapping is the part that grows with the catalog.
 *
 * <p>What is deliberately absent: trigger keywords. They drive extraction, not presentation, and
 * publishing them would turn an internal tuning detail into a contract. Also absent are the
 * structural enums — province scope, date source, classification — which change only when the code
 * changes, so they belong in the typed client contract rather than in data that grows on its own.
 */
public record MetadataResponse(List<EventTypeMetadata> eventTypes, List<ProvinceMetadata> provinces) {

    public record EventTypeMetadata(String key, String label, List<MetricMetadata> metrics) {
    }

    public record MetricMetadata(String key, String label) {
    }

    public record ProvinceMetadata(short code, String name) {
    }

    public static MetadataResponse of(IncidentCatalog catalog, List<Province> provinces) {
        return new MetadataResponse(
                catalog.eventTypes().stream()
                        .map(eventType -> new EventTypeMetadata(eventType.key(), eventType.label(),
                                eventType.metrics().stream()
                                        .map(metric -> new MetricMetadata(metric.key(), metric.label()))
                                        .toList()))
                        .toList(),
                provinces.stream()
                        .map(province -> new ProvinceMetadata(province.getCode(), province.getName()))
                        .toList());
    }
}
