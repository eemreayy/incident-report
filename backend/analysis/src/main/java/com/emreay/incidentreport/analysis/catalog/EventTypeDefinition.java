package com.emreay.incidentreport.analysis.catalog;

import java.util.List;
import java.util.Optional;

/**
 * One kind of incident the system recognises, and what it is expected to carry.
 *
 * @param key      stored in {@code incident.event_type}; UPPER_SNAKE, at most 48 characters
 * @param label    shown to the user; Turkish, because the user is
 * @param keywords the words that make a text this kind of incident
 * @param metrics  the numbers this kind of incident carries
 */
public record EventTypeDefinition(String key, String label, List<String> keywords,
                                  List<MetricDefinition> metrics) {

    public EventTypeDefinition {
        keywords = keywords == null ? List.of() : List.copyOf(keywords);
        metrics = metrics == null ? List.of() : List.copyOf(metrics);
    }

    public Optional<MetricDefinition> metric(String metricKey) {
        return metrics.stream().filter(metric -> metric.key().equals(metricKey)).findFirst();
    }
}
