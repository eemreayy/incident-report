package com.emreay.incidentreport.analysis.web;

import java.util.List;

import com.emreay.incidentreport.shared.api.PageResponse;

/**
 * A page of records, and — when the caller asked about one raw report — how that report's analysis
 * went.
 *
 * <p>It repeats {@link PageResponse}'s shape instead of nesting it, so a client reads the same
 * field names here as everywhere else. The extra field is what makes the submission contract work:
 * submitting answers with an id alone (ADR-021), so this endpoint is where a caller learns both
 * what was extracted and whether extraction succeeded — in one request (C-4, C-5).
 *
 * @param analysis {@code null} for a general listing, where records from many reports are mixed and
 *                 no single analysis outcome would mean anything
 */
public record IncidentPageResponse(List<IncidentResponse> content,
                                   int page,
                                   int size,
                                   long totalElements,
                                   int totalPages,
                                   AnalysisSummaryResponse analysis) {

    static IncidentPageResponse of(PageResponse<IncidentResponse> page, AnalysisSummaryResponse analysis) {
        return new IncidentPageResponse(page.content(), page.page(), page.size(),
                page.totalElements(), page.totalPages(), analysis);
    }
}
