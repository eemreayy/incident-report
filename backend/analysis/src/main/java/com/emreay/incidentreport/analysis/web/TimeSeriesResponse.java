package com.emreay.incidentreport.analysis.web;

import com.emreay.incidentreport.analysis.domain.ProvinceScope;
import com.emreay.incidentreport.analysis.query.ProvinceGrouping;

import java.time.LocalDate;
import java.util.List;

/**
 * Incidents over time, already divided into the lines a chart draws (FR-11, FR-12, FR-24).
 *
 * <p>Series rather than a flat table of rows, because a series is what carries the guarantees. A
 * cumulative point only means anything inside one, and the client must not be the one deciding
 * which rows belong together — that decision is what keeps a shared figure out of a province's
 * line.
 *
 * <p>{@code cumulative} and {@code groupBy} come back with the answer on purpose: a cumulative chart
 * and a plain one look alike and read completely differently, so the response says which one this is
 * rather than leaving the client to remember what it asked for.
 *
 * @param series ordered: single provinces by name, then shared figures, then records whose text
 *               named no province. Empty when nothing matched — an empty chart is an answer
 */
public record TimeSeriesResponse(boolean cumulative, ProvinceGrouping groupBy, List<Series> series) {

    /**
     * One line.
     *
     * @param eventType     catalog key; the label for it comes from the metadata endpoint (ADR-007)
     * @param metric        catalog key of the metric this line counts
     * @param provinceScope absent unless the query broke province out. {@code SHARED} means the
     *                      figures were given for several provinces at once and belong to none of
     *                      them alone; {@code UNKNOWN} means the text named no province. Neither is
     *                      folded into a province's line, and neither is dropped (ADR-019, FR-24)
     * @param province      set only for {@code SINGLE}
     * @param points        one per date that has data, in date order. Dates with nothing are absent
     *                      rather than zero: the system does not know that nothing happened, only
     *                      that nothing was reported
     */
    public record Series(String eventType,
                         String metric,
                         ProvinceScope provinceScope,
                         ProvinceResponse province,
                         List<Point> points) {
    }

    /** @param value already cumulative when the query asked for it */
    public record Point(LocalDate date, long value) {
    }
}
