package com.emreay.incidentreport.ingestion.service;

import com.emreay.incidentreport.ingestion.domain.RawIncidentReport;
import com.emreay.incidentreport.ingestion.repository.RawIncidentReportRepository;
import com.emreay.incidentreport.shared.error.DomainValidationException;
import com.emreay.incidentreport.shared.error.ResourceNotFoundException;
import com.emreay.incidentreport.shared.event.RawReportSubmittedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
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
 * one that keeps being written to is not really write-once (FR-02, ADR-005, ADR-021). Reprocessing
 * does not break that rule; it writes nothing here, it only asks for the same text to be read
 * again.
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
     * Stores the text and announces it for analysis — unless this exact text is already stored, in
     * which case the report that holds it is answered with instead (ADR-035).
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
     * <p>Recognising a repeat is not the same as re-running it. Nothing is announced the second
     * time, because the records the first submission produced are still there and unchanged. A
     * caller who wants the current rules applied to that text asks for {@link #reprocess(String)},
     * which is the operation that means it.
     *
     * @return the report holding this text, and whether it was written now or already there
     * @throws DomainValidationException if the text is blank or longer than the configured limit
     */
    public SubmissionOutcome submit(String rawText) {
        validate(rawText);

        RawIncidentReport candidate = RawIncidentReport.of(rawText, clock.instant());

        Optional<RawIncidentReport> alreadyStored = repository.findByTextHash(candidate.textHash());
        if (alreadyStored.isPresent()) {
            log.debug("raw report {} already holds this text; not storing it again",
                    alreadyStored.get().id());
            return SubmissionOutcome.alreadyStored(alreadyStored.get());
        }

        RawIncidentReport stored;
        try {
            stored = repository.save(candidate);
        } catch (DuplicateKeyException arrivedTwiceAtOnce) {
            // The unique index caught what the lookup could not: an identical submission that was
            // committed between the two. Answering with the winner is the same answer this method
            // would have given a moment later, and the loser's text is not lost - it is the winner's.
            RawIncidentReport winner = repository.findByTextHash(candidate.textHash())
                    .orElseThrow(() -> arrivedTwiceAtOnce);
            log.debug("an identical submission won the race; answering with raw report {}", winner.id());
            return SubmissionOutcome.alreadyStored(winner);
        }

        log.debug("stored raw report {} ({} chars)", stored.id(), stored.rawText().length());

        events.publishEvent(new RawReportSubmittedEvent(stored.id(), stored.rawText(), stored.submittedAt()));
        return SubmissionOutcome.stored(stored);
    }

    /**
     * Asks for a stored report to be read again with today's rules (FR-15, ADR-012).
     *
     * <p>Republishes the very same announcement the submission made, which is what makes
     * reprocessing indistinguishable from a first analysis on the analysing side — one code path,
     * not two, and therefore no second set of rules to keep in step. Whatever was derived from this
     * text before is replaced there; nothing here is written at all.
     *
     * <p>The report's <strong>original</strong> submission time travels with it, never the current
     * one. It is the reference date for "son 24 saatte" and for a text that names no date (ADR-014),
     * so reprocessing a two-year-old report with today's clock would silently move it to today —
     * the improvement would corrupt the history it was meant to improve.
     *
     * @return the report as it was and still is; the analysis result is read separately (ADR-021)
     * @throws ResourceNotFoundException if no report has that id
     */
    public RawIncidentReport reprocess(String id) {
        RawIncidentReport report = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Incident report", id));

        log.info("reprocessing raw report {}", id);
        events.publishEvent(new RawReportSubmittedEvent(report.id(), report.rawText(), report.submittedAt()));
        return report;
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
