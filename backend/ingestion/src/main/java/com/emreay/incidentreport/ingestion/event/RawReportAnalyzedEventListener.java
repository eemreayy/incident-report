package com.emreay.incidentreport.ingestion.event;

import com.emreay.incidentreport.ingestion.service.IngestionService;
import com.emreay.incidentreport.shared.event.RawReportAnalyzedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Brings the analysis outcome back to the report it belongs to.
 *
 * <p>Without this, every report would sit at {@code RECEIVED} for ever — a status that would be
 * saying "nobody has looked at this yet" about a report that had already been analysed.
 *
 * <p>The warnings arrive the same way. They are produced on the analysis side, but it is the raw
 * report a caller fetches, so this is where they have to end up (FR-09).
 */
@Component
public class RawReportAnalyzedEventListener {

    private final IngestionService ingestionService;

    public RawReportAnalyzedEventListener(IngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @EventListener
    public void onRawReportAnalyzed(RawReportAnalyzedEvent event) {
        ingestionService.markAnalyzed(event.rawReportId(), event.warnings());
    }
}
