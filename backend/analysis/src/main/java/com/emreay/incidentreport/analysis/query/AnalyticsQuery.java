package com.emreay.incidentreport.analysis.query;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * What an aggregation is being asked for.
 *
 * <p>The filters are deliberately the same ones {@link IncidentQuery} takes, because the table and
 * the chart show one dataset seen two ways: a chart that quietly ignored the keyword filter would
 * disagree with the table beside it, and the reader would have no way of telling which one to
 * believe (FR-23).
 *
 * <p>{@code rawReportId} is the one filter that is <em>not</em> repeated here. Aggregating a single
 * submission over time answers nothing — the records of one report share one date — and what came of
 * a report is a question the record endpoint already answers (C-5).
 *
 * @param eventTypes catalog keys; empty means every type
 * @param provinces  licence-plate codes; empty means every province. A figure shared between
 *                   provinces matches if any of them is selected, and is counted once however many
 *                   are (ADR-019)
 * @param from       earliest date, inclusive
 * @param to         latest date, inclusive
 * @param keyword    matched against the keywords the extraction recorded, exactly as in the record
 *                   listing
 * @param groupBy    whether province is a dimension of the answer or only a filter (C-1)
 * @param cumulative when true, each point is itself plus every earlier point of its own series
 *                   (FR-12). The running total is per series — adding across series would sum
 *                   different questions
 */
public record AnalyticsQuery(Set<String> eventTypes,
                             Set<Short> provinces,
                             LocalDate from,
                             LocalDate to,
                             String keyword,
                             ProvinceGrouping groupBy,
                             boolean cumulative) {

    public AnalyticsQuery {
        eventTypes = eventTypes == null ? Set.of() : Set.copyOf(eventTypes);
        provinces = provinces == null ? Set.of() : Set.copyOf(provinces);
        keyword = keyword == null || keyword.isBlank() ? null : keyword.trim();
        groupBy = groupBy == null ? ProvinceGrouping.NONE : groupBy;
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("'from' is after 'to': " + from + " > " + to);
        }
    }

    public static AnalyticsQuery of(List<String> eventTypes, List<Short> provinces,
                                    LocalDate from, LocalDate to, String keyword,
                                    ProvinceGrouping groupBy, boolean cumulative) {
        return new AnalyticsQuery(
                eventTypes == null ? Set.of() : Set.copyOf(eventTypes),
                provinces == null ? Set.of() : Set.copyOf(provinces),
                from, to, keyword, groupBy, cumulative);
    }

    /** The filters only, for the summary, which always breaks province out (FR-22). */
    public static AnalyticsQuery summaryOf(List<String> eventTypes, List<Short> provinces,
                                           LocalDate from, LocalDate to, String keyword) {
        return of(eventTypes, provinces, from, to, keyword, ProvinceGrouping.PROVINCE, false);
    }

    public boolean groupsByProvince() {
        return groupBy == ProvinceGrouping.PROVINCE;
    }
}
