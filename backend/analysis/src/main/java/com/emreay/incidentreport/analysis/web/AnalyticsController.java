package com.emreay.incidentreport.analysis.web;

import com.emreay.incidentreport.analysis.query.AnalyticsQuery;
import com.emreay.incidentreport.analysis.query.AnalyticsService;
import com.emreay.incidentreport.analysis.query.ProvinceGrouping;
import com.emreay.incidentreport.shared.error.DomainValidationException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * The aggregations behind the chart and the summary table (FR-11, FR-12, FR-22, FR-24).
 *
 * <p>Both endpoints take the same filters as the record listing, and for a reason worth stating: the
 * chart, the summary and the table are three views of one dataset. If the chart quietly ignored a
 * filter the table applied, the two would disagree on screen and the reader would have no way of
 * telling which to believe (FR-23).
 *
 * <p>Nothing here is computed for the client's convenience — it is computed because the client must
 * not compute it. Cumulative running totals, per-province breakdowns and table totals are all
 * definitions of what the data means, and a second definition written in TypeScript would drift from
 * this one (NFR-13).
 */
@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {

    private final AnalyticsService analytics;

    public AnalyticsController(AnalyticsService analytics) {
        this.analytics = analytics;
    }

    /**
     * Metric totals over time, as the series a chart draws.
     *
     * @param groupBy    {@code province} makes province a dimension rather than only a filter: one
     *                   series per province, plus a separate labelled series for figures given
     *                   across several provinces and for records whose text named none (C-1, FR-24).
     *                   Omitted, everything the filters allow is one series per event type and
     *                   metric
     * @param cumulative each point becomes itself plus every earlier point of its own series
     *                   (FR-12). Asked of the server rather than added up on arrival, so the chart
     *                   and any other client agree on what "cumulative" means
     */
    @GetMapping("/time-series")
    public TimeSeriesResponse timeSeries(
            @RequestParam(required = false) List<String> eventType,
            @RequestParam(required = false) List<Short> province,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String groupBy,
            @RequestParam(defaultValue = "false") boolean cumulative) {

        return analytics.timeSeries(AnalyticsQuery.of(
                eventType, province, from, to, keyword, grouping(groupBy), cumulative));
    }

    /**
     * The same data totalled rather than plotted: per event type and province bucket, per event
     * type, and overall.
     *
     * <p>Province is always broken out here. That is what the summary table is for (FR-22), and the
     * three levels come back together so a reader can see why the province rows do not add up to
     * the event type total on their own — the shared figures are the difference, and they are their
     * own row rather than a discrepancy.
     */
    @GetMapping("/summary")
    public SummaryResponse summary(
            @RequestParam(required = false) List<String> eventType,
            @RequestParam(required = false) List<Short> province,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String keyword) {

        return analytics.summary(AnalyticsQuery.summaryOf(eventType, province, from, to, keyword));
    }

    /**
     * Read leniently and rejected explicitly.
     *
     * <p>Spring's own enum binding is case-sensitive, so {@code groupBy=province} — the spelling the
     * documentation uses and the one a person types — would answer with a type-mismatch error about
     * an internal enum. A value that is not understood gets a problem document naming the parameter
     * and what it accepts, rather than silently falling back to no grouping and returning a chart
     * that answers a different question.
     */
    private static ProvinceGrouping grouping(String groupBy) {
        if (groupBy == null || groupBy.isBlank()) {
            return ProvinceGrouping.NONE;
        }
        try {
            return ProvinceGrouping.valueOf(groupBy.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            throw new DomainValidationException("analytics.group-by.unknown",
                    "groupBy must be one of: province. Got '" + groupBy + "'.");
        }
    }
}
