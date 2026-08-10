package com.emreay.incidentreport.analysis.web;

import com.emreay.incidentreport.analysis.domain.ProvinceScope;

import java.util.List;
import java.util.Map;

/**
 * The summary table, totalled by the database (FR-22).
 *
 * <p>Three levels come back together, and the client adds nothing up. That is not convenience: a
 * total the reader computed is a total nobody checked, and a shared figure makes the arithmetic
 * genuinely counter-intuitive — the province rows do <em>not</em> add up to the event type total on
 * their own, and are not supposed to. Handing back both, from one query over one filtered set, is
 * what lets a reader reconcile them instead of suspecting a bug (ADR-019, FR-24).
 *
 * @param rows            one per event type and province bucket, ordered: single provinces by name,
 *                        then the shared figures, then the records whose text named no province
 * @param eventTypeTotals one per event type, across every bucket
 * @param total           everything the filters allow
 */
public record SummaryResponse(List<Row> rows, List<Row> eventTypeTotals, Row total) {

    /**
     * One cell of the table, at whichever level it belongs to.
     *
     * <p>The same shape serves all three: a row about one bucket carries both an event type and a
     * bucket, a per-event-type total carries no bucket, and the grand total carries neither. Absent
     * fields are omitted from the JSON, so what a reader sees is exactly what the number is about.
     *
     * @param metrics keyed by catalog metric name. A bucket that produced no numbers at all comes
     *                back with an empty map rather than being left out — that records exist and
     *                carry no figures is itself worth seeing (ADR-006)
     */
    public record Row(String eventType,
                      ProvinceScope provinceScope,
                      ProvinceResponse province,
                      long incidentCount,
                      Map<String, Long> metrics) {
    }
}
