package com.emreay.incidentreport.analysis.web;

import java.time.Instant;
import java.util.List;

import com.emreay.incidentreport.analysis.domain.AnalysisResult;
import com.emreay.incidentreport.analysis.domain.AnalysisStatus;

/**
 * How the analysis of one raw report went (C-4).
 *
 * <p>Returned beside the records rather than on them, because the case that matters most has no
 * records: when analysis fails there is nothing to hang a status on, and a caller asking "what came
 * of my report" would otherwise get an empty list and no explanation.
 *
 * <p>{@code failureReason} is deliberately absent. It is a server-side diagnostic — a stack trace's
 * message by another name — and the contract says no internals in responses. What the caller can
 * act on is the status and the warnings.
 */
public record AnalysisSummaryResponse(AnalysisStatus status,
                                      Instant analyzedAt,
                                      int incidentCount,
                                      List<String> warnings) {

    public static AnalysisSummaryResponse of(AnalysisResult result) {
        return new AnalysisSummaryResponse(result.getStatus(), result.getAnalyzedAt(),
                result.getIncidentCount(), result.getWarnings());
    }
}
