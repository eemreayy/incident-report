package com.emreay.incidentreport.analysis.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.Objects;

/**
 * One number extracted for one incident — 15 new cases, 12 damaged buildings, 2 deaths.
 *
 * <p>A row per metric rather than a column per metric (ADR-020): the catalog of event types and
 * their metrics is configuration and grows without code changes (ADR-007), so it must also grow
 * without a migration. {@code metricType} is a catalog key, deliberately not a database enum.
 */
@Entity
@Table(name = "incident_metric")
public class IncidentMetric {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incident_id", nullable = false)
    private Incident incident;

    @Column(name = "metric_type", nullable = false, length = 48)
    private String metricType;

    @Column(name = "metric_value", nullable = false)
    private int value;

    protected IncidentMetric() {
        // for JPA
    }

    IncidentMetric(Incident incident, String metricType, int value) {
        this.incident = Objects.requireNonNull(incident, "incident");
        this.metricType = Objects.requireNonNull(metricType, "metricType");
        this.value = value;
    }

    public String getMetricType() {
        return metricType;
    }

    public int getValue() {
        return value;
    }
}
