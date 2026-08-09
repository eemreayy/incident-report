package com.emreay.incidentreport.analysis.event;

import com.emreay.incidentreport.analysis.service.AnalysisService;
import com.emreay.incidentreport.shared.event.RawReportSubmittedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Where a stored report enters the analysis side.
 *
 * <p>This module never calls the one that stored the text, never reads its database, and never
 * answers it back. It learns that a report exists because an event said so, and that is the only
 * edge between them — events flow one way (ADR-002, ADR-021).
 *
 * <p>Failures stop here. A listener that let an exception escape would fail the submitter's
 * request, and the submitter did nothing wrong: their text was accepted and stored before this ran.
 * So the failure is caught, recorded as this module's own answer about its own work, and the
 * request returns normally. The report is then discoverable as one that needs reprocessing
 * (FR-15) — which is only possible because the failure was written down rather than swallowed.
 */
@Component
public class RawReportSubmittedEventListener {

    private static final Logger log = LoggerFactory.getLogger(RawReportSubmittedEventListener.class);

    private final AnalysisService analysisService;

    public RawReportSubmittedEventListener(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @EventListener
    public void onRawReportSubmitted(RawReportSubmittedEvent event) {
        try {
            analysisService.analyze(event.rawReportId(), event.rawText(), event.submittedAt());
        } catch (RuntimeException failure) {
            log.error("analysis failed for raw report {}; the text is kept and can be reprocessed",
                    event.rawReportId(), failure);
            recordFailure(event.rawReportId(), failure);
        }
    }

    /**
     * Even writing down the failure can fail — if PostgreSQL is the thing that is broken, it will.
     * That must not escape either: the text is safe in the other store, and turning a database
     * outage into a rejected submission would lose incidents for the duration of the outage.
     */
    private void recordFailure(String rawReportId, RuntimeException failure) {
        try {
            analysisService.recordFailure(rawReportId, describe(failure));
        } catch (RuntimeException whileRecording) {
            log.error("could not record the analysis failure for raw report {}", rawReportId, whileRecording);
        }
    }

    /**
     * Summarises a failure for storage. Type and message only — a stack trace belongs in the log,
     * and this value never reaches a response in any case.
     */
    private static String describe(RuntimeException failure) {
        String message = failure.getMessage();
        String described = message == null ? failure.getClass().getName()
                : failure.getClass().getName() + ": " + message;
        return described.length() <= 1024 ? described : described.substring(0, 1024);
    }
}
