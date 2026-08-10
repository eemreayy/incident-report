package com.emreay.incidentreport.analysis.repository;

import com.emreay.incidentreport.analysis.domain.ProvinceScope;

import java.time.LocalDate;

/**
 * What the database answers with, before anything is shaped for the wire.
 *
 * <p>Flat rows on purpose: this is what {@code GROUP BY} produces, and turning them into series and
 * tables is presentation, which belongs a layer up. Nothing here adds anything together — every
 * number in these rows was computed by PostgreSQL.
 */
public final class AggregatedRows {

    private AggregatedRows() {
    }

    /**
     * One point of one series.
     *
     * @param provinceScope null when the query did not break province out; otherwise the scope this
     *                      point belongs to, which is what keeps a shared figure from being read as
     *                      one province's own (ADR-019)
     * @param provinceCode  set only for {@code SINGLE}
     * @param value         already cumulative when the query asked for it — the running total is a
     *                      window function, not a loop in Java
     */
    public record SeriesPoint(LocalDate date,
                              String eventType,
                              String metricType,
                              ProvinceScope provinceScope,
                              Short provinceCode,
                              String provinceName,
                              long value) {
    }

    /** One metric total, at one of the three roll-up levels. */
    public record MetricTotal(SummaryLevel level,
                              String eventType,
                              ProvinceScope provinceScope,
                              Short provinceCode,
                              String provinceName,
                              String metricType,
                              long value) {
    }

    /**
     * How many records are behind a bucket, at one of the three roll-up levels.
     *
     * <p>Counted without touching the metric table. A record with no metrics at all still exists and
     * still says something happened, and joining metrics to count records would both lose those and
     * count the others once per metric they carry.
     */
    public record IncidentCount(SummaryLevel level,
                                String eventType,
                                ProvinceScope provinceScope,
                                Short provinceCode,
                                String provinceName,
                                long incidentCount) {
    }
}
