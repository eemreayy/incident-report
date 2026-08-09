package com.emreay.incidentreport.analysis.event;

import com.emreay.incidentreport.analysis.service.AnalysisOutcome;
import com.emreay.incidentreport.analysis.service.AnalysisService;
import com.emreay.incidentreport.shared.event.RawReportAnalyzedEvent;
import com.emreay.incidentreport.shared.event.RawReportSubmittedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Where a stored report enters the analysis side.
 *
 * <p>This module never calls the one that stored the text, and never reads its database. It learns
 * that a report exists only because an event said so, and answers the same way (ADR-002, ADR-003).
 *
 * <p>Handling is synchronous, so the analysis finishes before the submission returns and the caller
 * sees the outcome in the same response. It also means a failure here surfaces to the submitter,
 * which is deliberate: the ingestion side catches it, marks the report {@code FAILED} and keeps the
 * text. Swallowing the exception here would leave the report looking analysed when it was not.
 */
@Component
public class RawReportSubmittedEventListener {

    private final AnalysisService analysisService;
    private final ApplicationEventPublisher events;

    public RawReportSubmittedEventListener(AnalysisService analysisService, ApplicationEventPublisher events) {
        this.analysisService = analysisService;
        this.events = events;
    }

    @EventListener
    public void onRawReportSubmitted(RawReportSubmittedEvent event) {
        AnalysisOutcome outcome =
                analysisService.analyze(event.rawReportId(), event.rawText(), event.submittedAt());

        // Announced after the transaction that wrote the records has committed, so nobody is told
        // about data that could still be rolled back.
        events.publishEvent(new RawReportAnalyzedEvent(
                event.rawReportId(), outcome.incidentCount(), outcome.warnings()));
    }
}
