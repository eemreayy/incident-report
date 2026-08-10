package com.emreay.incidentreport.analysis.query;

import com.emreay.incidentreport.analysis.domain.ProvinceScope;
import com.emreay.incidentreport.analysis.repository.AggregatedRows;
import com.emreay.incidentreport.analysis.repository.IncidentAggregationRepository;
import com.emreay.incidentreport.analysis.repository.SummaryLevel;
import com.emreay.incidentreport.analysis.web.ProvinceResponse;
import com.emreay.incidentreport.analysis.web.SummaryResponse;
import com.emreay.incidentreport.analysis.web.TimeSeriesResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns aggregated rows into the shapes the API publishes.
 *
 * <p>It shapes and it does not compute. Every figure below came out of a {@code SUM} or a
 * {@code COUNT}; the work here is grouping rows that already belong together into series and table
 * levels. The moment this class started adding numbers up, there would be two definitions of a
 * total — one in SQL and one in Java — and they would drift.
 *
 * <p>Like the record listing, it answers with DTOs and does the mapping inside the read transaction.
 * Nothing lazy is touched afterwards, because nothing here is an entity at all (ADR-033).
 */
@Service
public class AnalyticsService {

    private final IncidentAggregationRepository aggregations;

    public AnalyticsService(IncidentAggregationRepository aggregations) {
        this.aggregations = aggregations;
    }

    /**
     * Groups the points into series, in the order the database returned them.
     *
     * <p>The order is not decoration: it is what makes a chart's legend stable between requests, and
     * it puts the provinces first so the shared and unnamed figures read as the exceptions they are.
     */
    @Transactional(readOnly = true)
    public TimeSeriesResponse timeSeries(AnalyticsQuery query) {
        Map<SeriesKey, List<TimeSeriesResponse.Point>> points = new LinkedHashMap<>();
        Map<SeriesKey, String> provinceNames = new LinkedHashMap<>();

        for (AggregatedRows.SeriesPoint point : aggregations.timeSeries(query)) {
            SeriesKey key = new SeriesKey(point.eventType(), point.metricType(),
                    point.provinceScope(), point.provinceCode());
            points.computeIfAbsent(key, unused -> new ArrayList<>())
                    .add(new TimeSeriesResponse.Point(point.date(), point.value()));
            provinceNames.putIfAbsent(key, point.provinceName());
        }

        List<TimeSeriesResponse.Series> series = points.entrySet().stream()
                .map(entry -> new TimeSeriesResponse.Series(
                        entry.getKey().eventType(),
                        entry.getKey().metric(),
                        entry.getKey().provinceScope(),
                        province(entry.getKey().provinceCode(), provinceNames.get(entry.getKey())),
                        entry.getValue()))
                .toList();

        return new TimeSeriesResponse(query.cumulative(), query.groupBy(), series);
    }

    /**
     * Joins the metric totals to the record counts, bucket by bucket.
     *
     * <p>Two queries because they answer two different questions: how many records a bucket holds
     * cannot be asked of the metric table, which knows nothing about a record that produced no
     * figures and would count every other record once per metric it carries. Both are grouped
     * identically, so pairing them is a lookup rather than a computation.
     */
    @Transactional(readOnly = true)
    public SummaryResponse summary(AnalyticsQuery query) {
        Map<BucketKey, Map<String, Long>> metrics = new LinkedHashMap<>();
        for (AggregatedRows.MetricTotal total : aggregations.summaryMetrics(query)) {
            metrics.computeIfAbsent(BucketKey.of(total), unused -> new LinkedHashMap<>())
                    .put(total.metricType(), total.value());
        }

        List<SummaryResponse.Row> rows = new ArrayList<>();
        List<SummaryResponse.Row> eventTypeTotals = new ArrayList<>();
        SummaryResponse.Row total = EMPTY_TOTAL;

        for (AggregatedRows.IncidentCount count : aggregations.summaryCounts(query)) {
            SummaryResponse.Row row = new SummaryResponse.Row(
                    count.eventType(),
                    count.provinceScope(),
                    province(count.provinceCode(), count.provinceName()),
                    count.incidentCount(),
                    metrics.getOrDefault(BucketKey.of(count), Map.of()));

            switch (count.level()) {
                case BREAKDOWN -> rows.add(row);
                case EVENT_TYPE -> eventTypeTotals.add(row);
                case TOTAL -> total = row;
            }
        }

        return new SummaryResponse(rows, eventTypeTotals, total);
    }

    /**
     * Guards the shape of the response, so {@code total} is never null.
     *
     * <p>In practice the database answers with a grand total even when nothing matched — the empty
     * grouping set always produces its row, and it reads zero. This exists so that a client can rely
     * on the field being there regardless.
     */
    private static final SummaryResponse.Row EMPTY_TOTAL =
            new SummaryResponse.Row(null, null, null, 0L, Map.of());

    private static ProvinceResponse province(Short code, String name) {
        return code == null ? null : new ProvinceResponse(code, name);
    }

    /** Identifies one line of the chart. */
    private record SeriesKey(String eventType, String metric,
                             ProvinceScope provinceScope, Short provinceCode) {
    }

    /** Identifies one cell of the summary, at its own level. */
    private record BucketKey(SummaryLevel level, String eventType,
                             ProvinceScope provinceScope, Short provinceCode) {

        static BucketKey of(AggregatedRows.MetricTotal total) {
            return new BucketKey(total.level(), total.eventType(),
                    total.provinceScope(), total.provinceCode());
        }

        static BucketKey of(AggregatedRows.IncidentCount count) {
            return new BucketKey(count.level(), count.eventType(),
                    count.provinceScope(), count.provinceCode());
        }
    }
}
