package com.emreay.incidentreport.analysis.query;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * What a caller is asking for. Every filter is optional and they combine (FR-10).
 *
 * @param eventTypes  catalog keys; empty means every type
 * @param provinces   licence-plate codes; empty means every province. A record whose figures are
 *                    shared between provinces matches if <em>any</em> of them is selected, and is
 *                    still returned once (ADR-019)
 * @param from        earliest date, inclusive
 * @param to          latest date, inclusive
 * @param keyword     matched against the keywords the extractor recorded, not against the raw text:
 *                    full-text search over submissions is deliberately out of scope (PRD §2.3)
 * @param rawReportId the one filter that is not a convenience — with submission answering only with
 *                    an id (ADR-021), this is how a caller finds out what came of a report (C-5)
 */
public record IncidentQuery(Set<String> eventTypes,
                            Set<Short> provinces,
                            LocalDate from,
                            LocalDate to,
                            String keyword,
                            String rawReportId) {

    public IncidentQuery {
        eventTypes = eventTypes == null ? Set.of() : Set.copyOf(eventTypes);
        provinces = provinces == null ? Set.of() : Set.copyOf(provinces);
        keyword = blankToNull(keyword);
        rawReportId = blankToNull(rawReportId);
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("'from' is after 'to': " + from + " > " + to);
        }
    }

    public static IncidentQuery of(List<String> eventTypes, List<Short> provinces,
                                   LocalDate from, LocalDate to, String keyword, String rawReportId) {
        return new IncidentQuery(
                eventTypes == null ? Set.of() : Set.copyOf(eventTypes),
                provinces == null ? Set.of() : Set.copyOf(provinces),
                from, to, keyword, rawReportId);
    }

    /** Whether this query is asking "what came of one report", which changes what the answer owes. */
    public boolean isAboutOneReport() {
        return rawReportId != null;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
