package com.emreay.incidentreport.analysis.web;

/**
 * One extracted figure, by its catalog key.
 *
 * <p>No label, for the same reason event types carry none: the catalog is configuration, and the
 * metadata endpoint is the one place its labels come from (ADR-007).
 */
public record MetricResponse(String metricType, int value) {
}
