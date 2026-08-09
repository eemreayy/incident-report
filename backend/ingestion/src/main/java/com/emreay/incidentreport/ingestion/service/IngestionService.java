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
 * <p>Deliberately offers no update and no delete — and does not update a report itself either. The
 * text is an audit log: a record that can be edited cannot explain anything derived from it, and
 * one that keeps being written to is not really write-once (FR-02, ADR-005, ADR-021).
 *
 * <p>What this service does <em>not</em> know is just as deliberate. It does not know whether
 * analysis succeeded, what it warned about, or how long it took. That belongs to the module that
 * produces it. Nothing here waits for an answer, and nothing here has a field to put one in.
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
     * looks at it. Analysis listens synchronously today (ADR-003), but that is an implementation
     * detail of the transport, not part of what this method promises — it returns once the text is
     * safely stored and the announcement has gone out.
     *
     * <p>A listener that fails must not take the submission down with it. Recovering from that is
     * the listener's job, not this method's: the failure is recorded where the analysis outcome
     * lives, and this report stays exactly as it was written. Losing an incident because the parser
     * could not cope with it is the one outcome the design refuses.
     *
     * @return the stored report — its identity and submission time, nothing about how it was read
     * @throws DomainValidationException if the text is blank or longer than the configured limit
     */
    public RawIncidentReport submit(String rawText) {
        validate(rawText);

        RawIncidentReport stored = repository.save(RawIncidentReport.of(rawText, clock.instant()));
        log.debug("stored raw report {} ({} chars)", stored.id(), stored.rawText().length());

        events.publishEvent(new RawReportSubmittedEvent(stored.id(), stored.rawText(), stored.submittedAt()));
        return stored;
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
}
