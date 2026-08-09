package com.emreay.incidentreport.analysis.catalog;

import java.util.List;

/**
 * One number an event type carries — new cases, damaged buildings, deaths.
 *
 * <p>A metric key means the same thing wherever it appears: {@code DEATH} in an epidemic and
 * {@code DEATH} in an earthquake are the same measurement, which is why the same key carries the
 * same label everywhere (the loader enforces it). What differs per event type is which words
 * trigger it, not what it means.
 *
 * @param key      stored in {@code incident_metric.metric_type}; UPPER_SNAKE, at most 48 characters
 * @param label    shown to the user; Turkish, because the user is
 * @param keywords the words that point at this number in the text
 */
public record MetricDefinition(String key, String label, List<String> keywords) {

    public MetricDefinition {
        keywords = keywords == null ? List.of() : List.copyOf(keywords);
    }
}
