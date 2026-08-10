package com.emreay.incidentreport.analysis.repository;

import com.emreay.incidentreport.analysis.domain.ProvinceScope;
import com.emreay.incidentreport.analysis.query.AnalyticsQuery;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The aggregations, computed by PostgreSQL.
 *
 * <p>Every number these methods return was summed by the database. Reading rows and adding them up
 * in Java would work on the sample data and fall over on real volumes — and, worse, would put the
 * definition of "the total" in two places once the frontend needed one too (NFR-13).
 *
 * <p><strong>Why a province filter here cannot be a join.</strong> The record listing filters
 * provinces by joining the link table and marking the query distinct, which is correct when whole
 * rows are being returned. Under {@code SUM} it would be silently wrong: a figure shared between
 * Bursa and Kocaeli matches the join twice when both are selected, and no amount of {@code DISTINCT}
 * un-doubles a sum — {@code SUM(DISTINCT value)} is a different, equally wrong number. So the filter
 * is an {@code EXISTS} subquery: it decides whether a record qualifies without ever multiplying it.
 * The same reasoning applies to the keyword filter, which would otherwise multiply a record by how
 * many of its keywords matched (ADR-036).
 *
 * <p>SQL is assembled here rather than written as a fixed string because the filters are optional
 * and the grouping is not: a query with no province filter must not carry an empty {@code IN ()},
 * and grouping by province changes the shape of the statement. Every value goes in as a named
 * parameter; nothing user-supplied is ever concatenated into the text.
 */
@Repository
public class IncidentAggregationRepository {

    private final EntityManager entityManager;

    public IncidentAggregationRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * Metric totals over time, one row per point of one series.
     *
     * <p>The series key is the event type and the metric, plus the province bucket when the caller
     * asked for the breakdown. Cumulative mode is a window function over that key: each point is
     * itself plus every earlier point <em>of its own series</em>, which is the only reading of
     * FR-12 that does not add different questions together.
     */
    @Transactional(readOnly = true)
    public List<AggregatedRows.SeriesPoint> timeSeries(AnalyticsQuery query) {
        Filters filters = filters(query);
        boolean byProvince = query.groupsByProvince();

        String seriesKey = byProvince
                ? "i.event_type, m.metric_type, i.province_scope, i.province_code"
                : "i.event_type, m.metric_type";

        String value = query.cumulative()
                // sum of a sum: the inner one aggregates the day, the outer one carries the running
                // total forward over the days of this series only.
                ? "sum(sum(m.metric_value)) over (partition by " + seriesKey
                        + " order by i.occurred_on rows between unbounded preceding and current row)"
                : "sum(m.metric_value)";

        String sql = """
                select i.occurred_on,
                       i.event_type,
                       m.metric_type,
                       %s
                       %s as total
                from incident i
                join incident_metric m on m.incident_id = i.id
                %s
                where %s
                group by i.occurred_on, i.event_type, m.metric_type%s
                order by i.event_type, m.metric_type,%s i.occurred_on
                """.formatted(
                byProvince ? "i.province_scope, i.province_code, p.name as province_name," : "",
                value,
                byProvince ? "left join province p on p.code = i.province_code" : "",
                filters.sql(),
                byProvince ? ", i.province_scope, i.province_code, p.name" : "",
                byProvince ? " " + SCOPE_ORDER + ", p.name," : "");

        return rows(sql, filters).stream()
                .map(row -> byProvince
                        ? new AggregatedRows.SeriesPoint(date(row[0]), string(row[1]), string(row[2]),
                                scope(row[3]), code(row[4]), string(row[5]), number(row[6]))
                        : new AggregatedRows.SeriesPoint(date(row[0]), string(row[1]), string(row[2]),
                                null, null, null, number(row[3])))
                .toList();
    }

    /**
     * Metric totals for the summary table, at all three roll-up levels at once.
     *
     * <p>{@code GROUPING SETS} rather than three statements: the detail rows, the per-event-type
     * totals and the grand total then come from one scan of one filtered set, and cannot disagree
     * with each other.
     */
    @Transactional(readOnly = true)
    public List<AggregatedRows.MetricTotal> summaryMetrics(AnalyticsQuery query) {
        Filters filters = filters(query);

        String sql = """
                select grouping(i.event_type) as g_event,
                       grouping(i.province_scope) as g_bucket,
                       i.event_type,
                       i.province_scope,
                       i.province_code,
                       p.name as province_name,
                       m.metric_type,
                       sum(m.metric_value) as total
                from incident i
                join incident_metric m on m.incident_id = i.id
                left join province p on p.code = i.province_code
                where %s
                group by grouping sets (
                    (i.event_type, i.province_scope, i.province_code, p.name, m.metric_type),
                    (i.event_type, m.metric_type),
                    (m.metric_type)
                )
                order by g_event, i.event_type, g_bucket, %s, p.name, m.metric_type
                """.formatted(filters.sql(), SCOPE_ORDER);

        return rows(sql, filters).stream()
                .map(row -> new AggregatedRows.MetricTotal(
                        level(row[0], row[1]), string(row[2]), scope(row[3]), code(row[4]),
                        string(row[5]), string(row[6]), number(row[7])))
                .toList();
    }

    /** How many records each bucket holds, at the same three levels, without joining metrics. */
    @Transactional(readOnly = true)
    public List<AggregatedRows.IncidentCount> summaryCounts(AnalyticsQuery query) {
        Filters filters = filters(query);

        String sql = """
                select grouping(i.event_type) as g_event,
                       grouping(i.province_scope) as g_bucket,
                       i.event_type,
                       i.province_scope,
                       i.province_code,
                       p.name as province_name,
                       count(*) as records
                from incident i
                left join province p on p.code = i.province_code
                where %s
                group by grouping sets (
                    (i.event_type, i.province_scope, i.province_code, p.name),
                    (i.event_type),
                    ()
                )
                order by g_event, i.event_type, g_bucket, %s, p.name
                """.formatted(filters.sql(), SCOPE_ORDER);

        return rows(sql, filters).stream()
                .map(row -> new AggregatedRows.IncidentCount(
                        level(row[0], row[1]), string(row[2]), scope(row[3]), code(row[4]),
                        string(row[5]), number(row[6])))
                .toList();
    }

    /**
     * Puts the single-province buckets first, in name order, then the shared figures, then the
     * records whose text named no province. Alphabetical order on the scope itself would bury
     * {@code SHARED} in the middle of the provinces and read as if it were one of them.
     */
    private static final String SCOPE_ORDER =
            "case i.province_scope when 'SINGLE' then 0 when 'SHARED' then 1 else 2 end";

    /** The filter clause and everything it needs bound to it. */
    private record Filters(String sql, Map<String, Object> parameters) {
    }

    private static Filters filters(AnalyticsQuery query) {
        List<String> clauses = new ArrayList<>();
        Map<String, Object> parameters = new LinkedHashMap<>();

        if (!query.eventTypes().isEmpty()) {
            clauses.add("i.event_type in (:eventTypes)");
            parameters.put("eventTypes", query.eventTypes());
        }
        if (query.from() != null) {
            clauses.add("i.occurred_on >= :from");
            parameters.put("from", query.from());
        }
        if (query.to() != null) {
            clauses.add("i.occurred_on <= :to");
            parameters.put("to", query.to());
        }
        if (!query.provinces().isEmpty()) {
            // Two kinds of record qualify: the ones filed against a selected province, and the ones
            // whose figure covers it without belonging to it. EXISTS asks the question without
            // producing a second row for a record covering two selected provinces.
            clauses.add("""
                    (i.province_code in (:provinces)
                     or exists (select 1
                                from incident_shared_province sp
                                where sp.incident_id = i.id and sp.province_code in (:provinces)))""");
            parameters.put("provinces", query.provinces());
        }
        if (query.keyword() != null) {
            clauses.add("""
                    exists (select 1
                            from incident_keyword k
                            where k.incident_id = i.id and lower(k.keyword) like :keyword)""");
            // Lowered here with the Turkish rules and again by the database with its own; the same
            // compromise the record listing makes, and the same one it has to be, or the two would
            // disagree about which records exist.
            parameters.put("keyword", "%" + query.keyword().toLowerCase(Locale.of("tr")) + "%");
        }

        return new Filters(clauses.isEmpty() ? "true" : String.join("\n  and ", clauses), parameters);
    }

    @SuppressWarnings("unchecked")
    private List<Object[]> rows(String sql, Filters filters) {
        Query query = entityManager.createNativeQuery(sql);
        filters.parameters().forEach(query::setParameter);
        return query.getResultList();
    }

    private static SummaryLevel level(Object groupingEventType, Object groupingBucket) {
        if (number(groupingEventType) == 1) {
            return SummaryLevel.TOTAL;
        }
        return number(groupingBucket) == 1 ? SummaryLevel.EVENT_TYPE : SummaryLevel.BREAKDOWN;
    }

    private static LocalDate date(Object value) {
        return value instanceof java.sql.Date sqlDate ? sqlDate.toLocalDate() : (LocalDate) value;
    }

    private static String string(Object value) {
        return (String) value;
    }

    private static ProvinceScope scope(Object value) {
        return value == null ? null : ProvinceScope.valueOf((String) value);
    }

    private static Short code(Object value) {
        return value == null ? null : ((Number) value).shortValue();
    }

    /** Sums come back as {@code bigint} and running totals as {@code numeric}; both are numbers. */
    private static long number(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }
}
