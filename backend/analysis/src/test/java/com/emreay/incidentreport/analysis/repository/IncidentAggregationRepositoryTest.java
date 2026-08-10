package com.emreay.incidentreport.analysis.repository;

import com.emreay.incidentreport.analysis.domain.ClassificationStatus;
import com.emreay.incidentreport.analysis.domain.DateSource;
import com.emreay.incidentreport.analysis.domain.Incident;
import com.emreay.incidentreport.analysis.domain.KeywordRole;
import com.emreay.incidentreport.analysis.domain.Province;
import com.emreay.incidentreport.analysis.domain.ProvinceScope;
import com.emreay.incidentreport.analysis.query.AnalyticsQuery;
import com.emreay.incidentreport.analysis.query.ProvinceGrouping;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * The aggregations, against a real PostgreSQL.
 *
 * <p>Every claim here is about SQL — window functions, grouping sets, and a province filter that
 * must not multiply the rows it filters. None of it can be checked against a stand-in, and the one
 * failure that matters most is silent: a shared figure counted twice looks like a plausible number.
 *
 * <p>The data is the third sample text from the source document, because it is the case the whole
 * data model was shaped around: two provinces with their own figures, and one figure the text gives
 * for both together and for neither alone.
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(IncidentAggregationRepository.class)
@ActiveProfiles("test")
@Testcontainers
class IncidentAggregationRepositoryTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    private static final String REPORT_ID = "652f1a2b3c4d5e6f70819200";
    private static final LocalDate DAY_ONE = LocalDate.of(2020, 4, 20);
    private static final LocalDate DAY_TWO = LocalDate.of(2020, 4, 21);

    private static final short BURSA = 16;
    private static final short KOCAELI = 41;

    private final IncidentAggregationRepository aggregations;
    private final IncidentRepository incidents;
    private final ProvinceRepository provinces;

    IncidentAggregationRepositoryTest(@Autowired IncidentAggregationRepository aggregations,
                                      @Autowired IncidentRepository incidents,
                                      @Autowired ProvinceRepository provinces) {
        this.aggregations = aggregations;
        this.incidents = incidents;
        this.provinces = provinces;
    }

    /**
     * Bursa 8 injured, Kocaeli 6, and 10 more the text attributes to both at once — 24 in total,
     * of which 10 belong to no single province.
     */
    @BeforeEach
    void storeTheThirdSampleText() {
        incidents.deleteAllInBatch();

        Province bursa = province(BURSA);
        Province kocaeli = province(KOCAELI);

        incidents.saveAll(List.of(
                withMetric(Incident.forProvince(REPORT_ID, DAY_ONE, DateSource.RELATIVE, bursa,
                        "TRAFFIC_ACCIDENT", ClassificationStatus.CLASSIFIED), "INJURED", 8),
                withMetric(Incident.forProvince(REPORT_ID, DAY_ONE, DateSource.RELATIVE, kocaeli,
                        "TRAFFIC_ACCIDENT", ClassificationStatus.CLASSIFIED), "INJURED", 6),
                withMetric(Incident.sharedAcross(REPORT_ID, DAY_ONE, DateSource.RELATIVE,
                        List.of(bursa, kocaeli), "TRAFFIC_ACCIDENT", ClassificationStatus.CLASSIFIED),
                        "INJURED", 10)));
        incidents.flush();
    }

    // ---------------------------------------------------------------------
    // Time series
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("without a province breakdown, one series per event type and metric")
    void withoutGroupingEverythingIsOneSeries() {
        List<AggregatedRows.SeriesPoint> points = aggregations.timeSeries(query(ProvinceGrouping.NONE, false));

        assertThat(points)
                .extracting(AggregatedRows.SeriesPoint::date, AggregatedRows.SeriesPoint::eventType,
                        AggregatedRows.SeriesPoint::metricType, AggregatedRows.SeriesPoint::value)
                .containsExactly(tuple(DAY_ONE, "TRAFFIC_ACCIDENT", "INJURED", 24L));
        assertThat(points).allSatisfy(point ->
                assertThat(point.provinceScope())
                        .as("scope is only meaningful once province is a dimension")
                        .isNull());
    }

    /**
     * The heart of C-2. The shared figure is neither divided between the two provinces nor dropped:
     * it is a third series, labelled with the scope that says it belongs to both and to neither.
     */
    @Test
    @DisplayName("with a province breakdown, the shared figure is its own labelled series")
    void groupingByProvinceKeepsTheSharedFigureApart() {
        List<AggregatedRows.SeriesPoint> points = aggregations.timeSeries(query(ProvinceGrouping.PROVINCE, false));

        assertThat(points)
                .extracting(AggregatedRows.SeriesPoint::provinceScope, AggregatedRows.SeriesPoint::provinceName,
                        AggregatedRows.SeriesPoint::value)
                .containsExactly(
                        tuple(ProvinceScope.SINGLE, "Bursa", 8L),
                        tuple(ProvinceScope.SINGLE, "Kocaeli", 6L),
                        tuple(ProvinceScope.SHARED, null, 10L));

        assertThat(points.stream().mapToLong(AggregatedRows.SeriesPoint::value).sum())
                .as("the province series and the shared series reconcile with the overall total")
                .isEqualTo(24L);
    }

    /**
     * The failure this endpoint exists to avoid. Selecting both provinces matches the shared record
     * through the link table twice; a join would return 10 twice and report 20 injured people who
     * do not exist. No {@code DISTINCT} fixes a sum, which is why the filter is an {@code EXISTS}.
     */
    @Test
    @DisplayName("a figure shared between two provinces is counted once when both are selected")
    void aSharedFigureIsNotDoubleCounted() {
        AnalyticsQuery bothProvinces = new AnalyticsQuery(Set.of(), Set.of(BURSA, KOCAELI),
                null, null, null, ProvinceGrouping.PROVINCE, false);

        List<AggregatedRows.SeriesPoint> points = aggregations.timeSeries(bothProvinces);

        assertThat(points)
                .extracting(AggregatedRows.SeriesPoint::provinceScope, AggregatedRows.SeriesPoint::value)
                .containsExactly(
                        tuple(ProvinceScope.SINGLE, 8L),
                        tuple(ProvinceScope.SINGLE, 6L),
                        tuple(ProvinceScope.SHARED, 10L));
    }

    /** One province selected still sees the figure shared with it — it is not that province's, but
     *  hiding it would stop the province view reconciling with the overall one (ADR-033). */
    @Test
    @DisplayName("one province selected still sees the figure shared with it, separately")
    void oneProvinceSeesWhatIsSharedWithIt() {
        AnalyticsQuery onlyBursa = new AnalyticsQuery(Set.of(), Set.of(BURSA),
                null, null, null, ProvinceGrouping.PROVINCE, false);

        assertThat(aggregations.timeSeries(onlyBursa))
                .extracting(AggregatedRows.SeriesPoint::provinceScope, AggregatedRows.SeriesPoint::provinceName,
                        AggregatedRows.SeriesPoint::value)
                .containsExactly(
                        tuple(ProvinceScope.SINGLE, "Bursa", 8L),
                        tuple(ProvinceScope.SHARED, null, 10L));
    }

    @Test
    @DisplayName("cumulative mode adds each point to the ones before it, within its own series")
    void cumulativeAccumulatesPerSeries() {
        incidents.save(withMetric(Incident.forProvince("652f1a2b3c4d5e6f70819201", DAY_TWO,
                DateSource.EXPLICIT, province(BURSA), "TRAFFIC_ACCIDENT",
                ClassificationStatus.CLASSIFIED), "INJURED", 5));
        incidents.flush();

        List<AggregatedRows.SeriesPoint> plain = aggregations.timeSeries(query(ProvinceGrouping.PROVINCE, false));
        List<AggregatedRows.SeriesPoint> running = aggregations.timeSeries(query(ProvinceGrouping.PROVINCE, true));

        assertThat(plain)
                .filteredOn(point -> "Bursa".equals(point.provinceName()))
                .extracting(AggregatedRows.SeriesPoint::date, AggregatedRows.SeriesPoint::value)
                .containsExactly(tuple(DAY_ONE, 8L), tuple(DAY_TWO, 5L));

        assertThat(running)
                .filteredOn(point -> "Bursa".equals(point.provinceName()))
                .extracting(AggregatedRows.SeriesPoint::date, AggregatedRows.SeriesPoint::value)
                .as("the second point is itself plus the first")
                .containsExactly(tuple(DAY_ONE, 8L), tuple(DAY_TWO, 13L));

        assertThat(running)
                .filteredOn(point -> point.provinceScope() == ProvinceScope.SHARED)
                .extracting(AggregatedRows.SeriesPoint::value)
                .as("the running total does not leak across series")
                .containsExactly(10L);
    }

    @Test
    @DisplayName("the date range narrows what is aggregated, not just what is shown")
    void theDateRangeIsAppliedInSql() {
        incidents.save(withMetric(Incident.forProvince("652f1a2b3c4d5e6f70819201", DAY_TWO,
                DateSource.EXPLICIT, province(BURSA), "TRAFFIC_ACCIDENT",
                ClassificationStatus.CLASSIFIED), "INJURED", 5));
        incidents.flush();

        AnalyticsQuery dayTwoOnly = new AnalyticsQuery(Set.of(), Set.of(), DAY_TWO, DAY_TWO,
                null, ProvinceGrouping.NONE, false);

        assertThat(aggregations.timeSeries(dayTwoOnly))
                .extracting(AggregatedRows.SeriesPoint::date, AggregatedRows.SeriesPoint::value)
                .containsExactly(tuple(DAY_TWO, 5L));
    }

    @Test
    @DisplayName("an event type that produced nothing yields no series at all")
    void aFilterThatMatchesNothingReturnsNothing() {
        AnalyticsQuery noMatch = new AnalyticsQuery(Set.of("EPIDEMIC"), Set.of(),
                null, null, null, ProvinceGrouping.PROVINCE, false);

        assertThat(aggregations.timeSeries(noMatch)).isEmpty();
    }

    /**
     * The keyword filter has the same shape of danger as the province one: joining the keyword table
     * would return a record once per matching keyword and multiply its figures by that.
     */
    @Test
    @DisplayName("a record matching a keyword twice is still counted once")
    void theKeywordFilterDoesNotMultiplyRecords() {
        Incident twoKeywords = withMetric(Incident.forProvince("652f1a2b3c4d5e6f70819202", DAY_TWO,
                DateSource.EXPLICIT, province(BURSA), "TRAFFIC_ACCIDENT",
                ClassificationStatus.CLASSIFIED), "INJURED", 7);
        twoKeywords.addKeyword("kaza", KeywordRole.EVENT_TYPE, 0, 4);
        twoKeywords.addKeyword("kazası", KeywordRole.EVENT_TYPE, 10, 16);
        incidents.save(twoKeywords);
        incidents.flush();

        AnalyticsQuery byKeyword = new AnalyticsQuery(Set.of(), Set.of(), null, null, "kaza",
                ProvinceGrouping.NONE, false);

        assertThat(aggregations.timeSeries(byKeyword))
                .extracting(AggregatedRows.SeriesPoint::value)
                .containsExactly(7L);
    }

    // ---------------------------------------------------------------------
    // Summary
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("the summary totals the same numbers at three levels, from one filtered set")
    void theSummaryRollsUpWithoutTheClientAddingAnything() {
        List<AggregatedRows.MetricTotal> metrics = aggregations.summaryMetrics(query(ProvinceGrouping.PROVINCE, false));

        assertThat(metrics)
                .filteredOn(total -> total.level() == SummaryLevel.BREAKDOWN)
                .extracting(AggregatedRows.MetricTotal::provinceScope, AggregatedRows.MetricTotal::provinceName,
                        AggregatedRows.MetricTotal::metricType, AggregatedRows.MetricTotal::value)
                .containsExactly(
                        tuple(ProvinceScope.SINGLE, "Bursa", "INJURED", 8L),
                        tuple(ProvinceScope.SINGLE, "Kocaeli", "INJURED", 6L),
                        tuple(ProvinceScope.SHARED, null, "INJURED", 10L));

        assertThat(metrics)
                .filteredOn(total -> total.level() == SummaryLevel.EVENT_TYPE)
                .extracting(AggregatedRows.MetricTotal::eventType, AggregatedRows.MetricTotal::value)
                .containsExactly(tuple("TRAFFIC_ACCIDENT", 24L));

        assertThat(metrics)
                .filteredOn(total -> total.level() == SummaryLevel.TOTAL)
                .extracting(AggregatedRows.MetricTotal::eventType, AggregatedRows.MetricTotal::value)
                .as("the grand total belongs to no event type")
                .containsExactly(tuple(null, 24L));
    }

    @Test
    @DisplayName("record counts are counted per bucket, never per metric")
    void countsAreNotMultipliedByMetrics() {
        Incident twoMetrics = Incident.forProvince("652f1a2b3c4d5e6f70819203", DAY_ONE,
                DateSource.EXPLICIT, province(BURSA), "EPIDEMIC", ClassificationStatus.CLASSIFIED);
        twoMetrics.addMetric("NEW_CASE", 15);
        twoMetrics.addMetric("DEATH", 1);
        incidents.save(twoMetrics);
        incidents.flush();

        assertThat(aggregations.summaryCounts(query(ProvinceGrouping.PROVINCE, false)))
                .filteredOn(count -> count.level() == SummaryLevel.TOTAL)
                .extracting(AggregatedRows.IncidentCount::incidentCount)
                .as("four records, not the five a join through the metric table would report")
                .containsExactly(4L);
    }

    /**
     * A record can exist with no figures at all — an unrecognised text is stored rather than
     * rejected (ADR-006). It has to appear in the counts, or the table would say nothing happened.
     */
    @Test
    @DisplayName("a record with no metrics is still counted")
    void recordsWithoutMetricsAreStillCounted() {
        incidents.save(Incident.withoutProvince("652f1a2b3c4d5e6f70819204", DAY_ONE,
                DateSource.DEFAULTED, "OTHER", ClassificationStatus.UNCLASSIFIED));
        incidents.flush();

        List<AggregatedRows.IncidentCount> counts = aggregations.summaryCounts(query(ProvinceGrouping.PROVINCE, false));

        assertThat(counts)
                .filteredOn(count -> count.level() == SummaryLevel.BREAKDOWN
                        && count.provinceScope() == ProvinceScope.UNKNOWN)
                .extracting(AggregatedRows.IncidentCount::eventType, AggregatedRows.IncidentCount::incidentCount)
                .as("records whose text named no province are their own bucket, not hidden")
                .containsExactly(tuple("OTHER", 1L));

        assertThat(aggregations.summaryMetrics(query(ProvinceGrouping.PROVINCE, false)))
                .filteredOn(total -> "OTHER".equals(total.eventType()))
                .as("it has no figures to total")
                .isEmpty();
    }

    /**
     * "Nothing matched" is an answer with two halves, and they are not the same. There are no
     * figures to total, so there are no metric rows at all — inventing a zero for a metric nobody
     * reported would be inventing data. But the grand total still exists and is zero, because the
     * empty grouping set always produces its row; that is what lets a client draw an empty table
     * with a legible "0" instead of nothing at all.
     */
    @Test
    @DisplayName("nothing matching still answers, with a total of zero and no invented metrics")
    void anEmptyResultStillHasATotal() {
        incidents.deleteAllInBatch();

        assertThat(aggregations.summaryMetrics(query(ProvinceGrouping.PROVINCE, false))).isEmpty();
        assertThat(aggregations.summaryCounts(query(ProvinceGrouping.PROVINCE, false)))
                .extracting(AggregatedRows.IncidentCount::level, AggregatedRows.IncidentCount::incidentCount)
                .containsExactly(tuple(SummaryLevel.TOTAL, 0L));
    }

    private static AnalyticsQuery query(ProvinceGrouping groupBy, boolean cumulative) {
        return new AnalyticsQuery(Set.of(), Set.of(), null, null, null, groupBy, cumulative);
    }

    private static Incident withMetric(Incident incident, String metric, int value) {
        incident.addMetric(metric, value);
        return incident;
    }

    private Province province(short code) {
        return provinces.findById(code).orElseThrow();
    }
}
