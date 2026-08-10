package com.emreay.incidentreport.analysis.query;

import com.emreay.incidentreport.analysis.domain.ProvinceScope;
import com.emreay.incidentreport.analysis.repository.AggregatedRows;
import com.emreay.incidentreport.analysis.repository.IncidentAggregationRepository;
import com.emreay.incidentreport.analysis.repository.SummaryLevel;
import com.emreay.incidentreport.analysis.web.SummaryResponse;
import com.emreay.incidentreport.analysis.web.TimeSeriesResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * How aggregated rows become series and tables.
 *
 * <p>The database is mocked here on purpose: what the SQL computes is verified against a real
 * PostgreSQL elsewhere, and what is left for this class is the grouping — which rows belong to the
 * same line, and which level of the table a row belongs on. Those are the mistakes that would put a
 * shared figure inside a province's series without any number being wrong.
 */
class AnalyticsServiceTest {

    private static final LocalDate DAY_ONE = LocalDate.of(2020, 4, 20);
    private static final LocalDate DAY_TWO = LocalDate.of(2020, 4, 21);

    private IncidentAggregationRepository aggregations;
    private AnalyticsService service;

    @BeforeEach
    void setUp() {
        aggregations = mock(IncidentAggregationRepository.class);
        service = new AnalyticsService(aggregations);
    }

    @Test
    @DisplayName("points of one line are collected into one series, in date order")
    void pointsBecomeSeries() {
        when(aggregations.timeSeries(any())).thenReturn(List.of(
                point(DAY_ONE, "TRAFFIC_ACCIDENT", "INJURED", null, null, null, 8),
                point(DAY_TWO, "TRAFFIC_ACCIDENT", "INJURED", null, null, null, 5)));

        TimeSeriesResponse response = service.timeSeries(query(ProvinceGrouping.NONE, false));

        assertThat(response.series()).singleElement().satisfies(series -> {
            assertThat(series.eventType()).isEqualTo("TRAFFIC_ACCIDENT");
            assertThat(series.metric()).isEqualTo("INJURED");
            assertThat(series.province()).isNull();
            assertThat(series.provinceScope()).isNull();
            assertThat(series.points())
                    .extracting(TimeSeriesResponse.Point::date, TimeSeriesResponse.Point::value)
                    .containsExactly(tuple(DAY_ONE, 8L), tuple(DAY_TWO, 5L));
        });
    }

    /**
     * Two provinces and a figure belonging to both are three lines, not one line with three
     * numbers in it — and the shared one carries no province, because it is not any province's.
     */
    @Test
    @DisplayName("each province is its own series, and the shared figure is a fourth kind")
    void provinceBreakdownSplitsIntoSeries() {
        when(aggregations.timeSeries(any())).thenReturn(List.of(
                point(DAY_ONE, "TRAFFIC_ACCIDENT", "INJURED", ProvinceScope.SINGLE, (short) 16, "Bursa", 8),
                point(DAY_ONE, "TRAFFIC_ACCIDENT", "INJURED", ProvinceScope.SINGLE, (short) 41, "Kocaeli", 6),
                point(DAY_ONE, "TRAFFIC_ACCIDENT", "INJURED", ProvinceScope.SHARED, null, null, 10),
                point(DAY_ONE, "TRAFFIC_ACCIDENT", "INJURED", ProvinceScope.UNKNOWN, null, null, 2)));

        TimeSeriesResponse response = service.timeSeries(query(ProvinceGrouping.PROVINCE, false));

        assertThat(response.series()).hasSize(4);
        assertThat(response.series())
                .extracting(series -> series.provinceScope(),
                        series -> series.province() == null ? null : series.province().name(),
                        series -> series.points().getFirst().value())
                .containsExactly(
                        tuple(ProvinceScope.SINGLE, "Bursa", 8L),
                        tuple(ProvinceScope.SINGLE, "Kocaeli", 6L),
                        tuple(ProvinceScope.SHARED, null, 10L),
                        tuple(ProvinceScope.UNKNOWN, null, 2L));
    }

    /**
     * Same province, same metric, different event type: still two lines. Collapsing them would add
     * epidemics to traffic accidents, which is the one thing a chart split by event type must not do.
     */
    @Test
    @DisplayName("the event type is part of what makes a series a series")
    void seriesAreKeyedByEventTypeToo() {
        when(aggregations.timeSeries(any())).thenReturn(List.of(
                point(DAY_ONE, "EPIDEMIC", "DEATH", ProvinceScope.SINGLE, (short) 6, "Ankara", 1),
                point(DAY_ONE, "TRAFFIC_ACCIDENT", "DEATH", ProvinceScope.SINGLE, (short) 6, "Ankara", 3)));

        assertThat(service.timeSeries(query(ProvinceGrouping.PROVINCE, false)).series())
                .extracting(TimeSeriesResponse.Series::eventType)
                .containsExactly("EPIDEMIC", "TRAFFIC_ACCIDENT");
    }

    /** The answer says which question it answered; a cumulative chart is unreadable without it. */
    @Test
    @DisplayName("the response repeats back the mode it was asked for")
    void theResponseStatesItsOwnMode() {
        when(aggregations.timeSeries(any())).thenReturn(List.of());

        TimeSeriesResponse response = service.timeSeries(query(ProvinceGrouping.PROVINCE, true));

        assertThat(response.cumulative()).isTrue();
        assertThat(response.groupBy()).isEqualTo(ProvinceGrouping.PROVINCE);
        assertThat(response.series()).isEmpty();
    }

    // ---------------------------------------------------------------------
    // Summary
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("the three levels land in three places, with their metrics attached")
    void summaryRowsAreSplitByLevel() {
        when(aggregations.summaryCounts(any())).thenReturn(List.of(
                count(SummaryLevel.BREAKDOWN, "TRAFFIC_ACCIDENT", ProvinceScope.SINGLE, (short) 16, "Bursa", 1),
                count(SummaryLevel.BREAKDOWN, "TRAFFIC_ACCIDENT", ProvinceScope.SHARED, null, null, 1),
                count(SummaryLevel.EVENT_TYPE, "TRAFFIC_ACCIDENT", null, null, null, 2),
                count(SummaryLevel.TOTAL, null, null, null, null, 2)));
        when(aggregations.summaryMetrics(any())).thenReturn(List.of(
                metric(SummaryLevel.BREAKDOWN, "TRAFFIC_ACCIDENT", ProvinceScope.SINGLE, (short) 16, "Bursa", "INJURED", 8),
                metric(SummaryLevel.BREAKDOWN, "TRAFFIC_ACCIDENT", ProvinceScope.SHARED, null, null, "INJURED", 10),
                metric(SummaryLevel.EVENT_TYPE, "TRAFFIC_ACCIDENT", null, null, null, "INJURED", 18),
                metric(SummaryLevel.TOTAL, null, null, null, null, "INJURED", 18)));

        SummaryResponse summary = service.summary(query(ProvinceGrouping.PROVINCE, false));

        assertThat(summary.rows())
                .extracting(SummaryResponse.Row::provinceScope, row -> row.metrics().get("INJURED"))
                .containsExactly(tuple(ProvinceScope.SINGLE, 8L), tuple(ProvinceScope.SHARED, 10L));
        assertThat(summary.eventTypeTotals()).singleElement().satisfies(row -> {
            assertThat(row.eventType()).isEqualTo("TRAFFIC_ACCIDENT");
            assertThat(row.province()).as("a total across buckets belongs to no bucket").isNull();
            assertThat(row.metrics()).containsExactly(entry("INJURED", 18L));
        });
        assertThat(summary.total().eventType()).isNull();
        assertThat(summary.total().incidentCount()).isEqualTo(2);
        assertThat(summary.total().metrics()).containsExactly(entry("INJURED", 18L));
    }

    /**
     * A record that produced no figures still has to appear, or the table would say nothing
     * happened when something did — it was just not understood (ADR-006).
     */
    @Test
    @DisplayName("a bucket with records and no metrics comes back with an empty metric map")
    void bucketsWithoutMetricsSurvive() {
        when(aggregations.summaryCounts(any())).thenReturn(List.of(
                count(SummaryLevel.BREAKDOWN, "OTHER", ProvinceScope.UNKNOWN, null, null, 3)));
        when(aggregations.summaryMetrics(any())).thenReturn(List.of());

        assertThat(service.summary(query(ProvinceGrouping.PROVINCE, false)).rows())
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.incidentCount()).isEqualTo(3);
                    assertThat(row.metrics()).isEmpty();
                });
    }

    @Test
    @DisplayName("nothing at all still answers with a total")
    void anEmptySummaryStillHasATotal() {
        when(aggregations.summaryCounts(any())).thenReturn(List.of());
        when(aggregations.summaryMetrics(any())).thenReturn(List.of());

        SummaryResponse summary = service.summary(query(ProvinceGrouping.PROVINCE, false));

        assertThat(summary.rows()).isEmpty();
        assertThat(summary.eventTypeTotals()).isEmpty();
        assertThat(summary.total()).isNotNull();
        assertThat(summary.total().incidentCount()).isZero();
        assertThat(summary.total().metrics()).isEmpty();
    }

    private static AnalyticsQuery query(ProvinceGrouping groupBy, boolean cumulative) {
        return new AnalyticsQuery(Set.of(), Set.of(), null, null, null, groupBy, cumulative);
    }

    private static AggregatedRows.SeriesPoint point(LocalDate date, String eventType, String metric,
                                                    ProvinceScope scope, Short code, String name, long value) {
        return new AggregatedRows.SeriesPoint(date, eventType, metric, scope, code, name, value);
    }

    private static AggregatedRows.IncidentCount count(SummaryLevel level, String eventType,
                                                      ProvinceScope scope, Short code, String name, long records) {
        return new AggregatedRows.IncidentCount(level, eventType, scope, code, name, records);
    }

    private static AggregatedRows.MetricTotal metric(SummaryLevel level, String eventType,
                                                     ProvinceScope scope, Short code, String name,
                                                     String metricType, long value) {
        return new AggregatedRows.MetricTotal(level, eventType, scope, code, name, metricType, value);
    }

    /** Nothing here is allowed to invent a number; the maps only ever hold what the rows carried. */
    @Test
    @DisplayName("the service adds nothing up of its own")
    void nothingIsComputedHere() {
        when(aggregations.summaryCounts(any())).thenReturn(List.of(
                count(SummaryLevel.BREAKDOWN, "TRAFFIC_ACCIDENT", ProvinceScope.SINGLE, (short) 16, "Bursa", 1),
                count(SummaryLevel.TOTAL, null, null, null, null, 1)));
        when(aggregations.summaryMetrics(any())).thenReturn(List.of(
                metric(SummaryLevel.BREAKDOWN, "TRAFFIC_ACCIDENT", ProvinceScope.SINGLE, (short) 16, "Bursa", "INJURED", 8),
                // Deliberately inconsistent with the row above: if this class were doing arithmetic
                // of its own, it would "correct" this to 8.
                metric(SummaryLevel.TOTAL, null, null, null, null, "INJURED", 99)));

        SummaryResponse summary = service.summary(query(ProvinceGrouping.PROVINCE, false));

        assertThat(summary.total().metrics())
                .as("what the database said, not what the rows would add up to")
                .containsExactly(entry("INJURED", 99L));
        assertThat(summary.rows()).singleElement()
                .extracting(row -> row.metrics().get("INJURED")).isEqualTo(8L);
    }

    /** Sanity: the map handed to a client is the one built here, not a live view of internals. */
    @Test
    @DisplayName("an absent metric map is empty rather than null")
    void metricsAreNeverNull() {
        when(aggregations.summaryCounts(any())).thenReturn(List.of(
                count(SummaryLevel.TOTAL, null, null, null, null, 0)));
        when(aggregations.summaryMetrics(any())).thenReturn(List.of());

        assertThat(service.summary(query(ProvinceGrouping.PROVINCE, false)).total().metrics())
                .isNotNull()
                .isEqualTo(Map.of());
    }
}
