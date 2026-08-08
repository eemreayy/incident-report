package com.emreay.incidentreport.ingestion.service;

import com.emreay.incidentreport.ingestion.domain.RawIncidentReport;
import com.emreay.incidentreport.ingestion.repository.RawIncidentReportRepository;
import com.emreay.incidentreport.shared.error.DomainValidationException;
import com.emreay.incidentreport.shared.event.RawReportSubmittedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Optional;

/**
 * Accepts raw incident reports, stores them unchanged, and announces them.
 *
 * <p>Deliberately offers no update and no delete. The text is an audit log: a record that can be
 * edited cannot explain anything derived from it, and a record that can be deleted breaks the
 * traceability the source document asks for (FR-02, ADR-005).
 *
 * <p>Validation lives here rather than only in the web layer, so the rules hold for every caller —
 * including the reprocessing path, which has no HTTP request behind it.
 */
@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    private final RawIncidentReportRepository repository;
    private final ApplicationEventPublisher events;
    private final IngestionProperties properties;
    private final Clock clock;

    public IngestionService(RawIncidentReportRepository repository,
                            ApplicationEventPublisher events,
                            IngestionProperties properties,
                            Clock clock) {
        this.repository = repository;
        this.events = events;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Stores the text and announces it for analysis.
     *
     * <p>The order matters and is the whole point: the text is persisted <em>before</em> anyone
     * looks at it. Analysis runs synchronously inside this call (ADR-003), so a listener blowing up
     * would otherwise take the submission down with it. Instead the failure is caught, the report
     * is marked {@code FAILED}, and the text survives to be reprocessed once the cause is fixed
     * (FR-15). Losing an incident because the parser could not cope with it would be the worst
     * possible outcome.
     *
     * @return the stored report, marked {@code FAILED} if analysis threw
     * @throws DomainValidationException if the text is blank or longer than the configured limit
     */
    public RawIncidentReport submit(String rawText) {
        validate(rawText);

        RawIncidentReport stored = repository.save(RawIncidentReport.received(rawText, clock.instant()));
        log.debug("stored raw report {} ({} chars)", stored.id(), stored.rawText().length());

        try {
            events.publishEvent(new RawReportSubmittedEvent(stored.id(), stored.rawText(), stored.submittedAt()));
            return stored;
        } catch (RuntimeException failure) {
            log.error("analysis failed for raw report {}; the text is kept for reprocessing",
                    stored.id(), failure);
            return repository.save(stored.failed(clock.instant(), describe(failure)));
        }
    }

    public Optional<RawIncidentReport> findById(String id) {
        return repository.findById(id);
    }

    public Page<RawIncidentReport> findAll(Pageable pageable) {
        return repository.findAll(pageable);
    }

    private void validate(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            throw new DomainValidationException("report.text.blank",
                    "Incident report text must not be empty.");
        }
        if (rawText.length() > properties.maxTextLength()) {
            throw new DomainValidationException("report.text.too-long",
                    "Incident report text must be at most " + properties.maxTextLength()
                            + " characters, got " + rawText.length() + ".");
        }
    }

    /**
     * Summarises a failure for storage. Type and message only — a stack trace belongs in the log,
     * not in a field that may end up in a response.
     */
    private static String describe(RuntimeException failure) {
        String message = failure.getMessage();
        return message == null ? failure.getClass().getName()
                : failure.getClass().getName() + ": " + message;
    }
}
