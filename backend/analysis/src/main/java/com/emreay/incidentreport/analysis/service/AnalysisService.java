package com.emreay.incidentreport.analysis.service;

import com.emreay.incidentreport.analysis.domain.AnalysisResult;
import com.emreay.incidentreport.analysis.domain.Incident;
import com.emreay.incidentreport.analysis.domain.Province;
import com.emreay.incidentreport.analysis.extraction.ExtractedIncident;
import com.emreay.incidentreport.analysis.extraction.ExtractedKeyword;
import com.emreay.incidentreport.analysis.extraction.ExtractionResult;
import com.emreay.incidentreport.analysis.text.TurkishTextNormalizer;
import com.emreay.incidentreport.analysis.extraction.IncidentExtractor;
import com.emreay.incidentreport.analysis.repository.AnalysisResultRepository;
import com.emreay.incidentreport.analysis.repository.IncidentRepository;
import com.emreay.incidentreport.analysis.repository.ProvinceRepository;
import com.emreay.incidentreport.shared.event.IncidentRecordsProducedEvent;
import com.emreay.incidentreport.shared.event.IncidentSignal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Reads a raw report and stores what it found.
 *
 * <p>Rebuilds rather than patches: everything previously derived from a report is deleted before
 * the new result is written. The raw text cannot change (ADR-005), so re-running the analysis is
 * always safe, and deleting first is what stops a second run from doubling the rows. Reprocessing
 * (FR-15) is therefore the same code path as a first analysis, not a special case.
 *
 * <p>The whole thing runs in one PostgreSQL transaction. If any part fails, no half-analysed report
 * is left behind — and the raw text is untouched either way, because it lives in a different store
 * that was written before this ever ran.
 *
 * <p>When a run succeeds it announces what now stands, so connected clients can refresh (FR-13).
 * The announcement carries identifiers, not data: this module owns these records and serves them
 * from its query endpoints, and the stream only says that looking again is worthwhile (ADR-021).
 */
@Service
public class AnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisService.class);

    private final IncidentExtractor extractor;
    private final IncidentRepository incidents;
    private final ProvinceRepository provinces;
    private final AnalysisResultRepository results;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final TurkishTextNormalizer normalizer;
    private final ZoneId reportingZone;

    public AnalysisService(IncidentExtractor extractor,
                           IncidentRepository incidents,
                           ProvinceRepository provinces,
                           AnalysisResultRepository results,
                           ApplicationEventPublisher events,
                           Clock clock,
                           TurkishTextNormalizer normalizer,
                           ZoneId reportingZone) {
        this.extractor = extractor;
        this.incidents = incidents;
        this.provinces = provinces;
        this.results = results;
        this.events = events;
        this.clock = clock;
        this.normalizer = normalizer;
        this.reportingZone = reportingZone;
    }

    /**
     * @param submittedAt the report's submission time, which becomes the reference date for
     *                    relative and defaulted dates. Never "now": reprocessing a two-year-old
     *                    report must still date it two years ago (ADR-014).
     */
    @Transactional
    public AnalysisOutcome analyze(String rawReportId, String rawText, Instant submittedAt) {
        // Which calendar day a submission falls on is a local question, not a UTC one - see
        // ADR-029. The instant itself stays UTC; only the day it is read as is zoned.
        LocalDate referenceDate = LocalDate.ofInstant(submittedAt, reportingZone);
        // Normalized once here rather than inside each extractor: they all need the same text, and
        // normalising it repeatedly would also recompute the offset map every time.
        ExtractionResult result = extractor.extract(normalizer.normalize(rawText), referenceDate);

        long removed = incidents.deleteByRawReportId(rawReportId);
        if (removed > 0) {
            log.debug("rebuilding raw report {}: removed {} previously derived records", rawReportId, removed);
        }

        List<Incident> stored = incidents.saveAll(result.incidents().stream()
                .map(extracted -> toEntity(rawReportId, extracted))
                .toList());

        Instant analyzedAt = clock.instant();
        record(AnalysisResult.analyzed(rawReportId, analyzedAt, stored.size(), result.warnings()));

        announce(rawReportId, analyzedAt, stored);

        log.info("analysed raw report {}: {} records, {} warnings",
                rawReportId, stored.size(), result.warnings().size());

        return new AnalysisOutcome(stored.size(), result.warnings());
    }

    /**
     * Says what now stands for this report, so anyone showing it can refresh.
     *
     * <p>Published even when nothing was extracted: a reprocess that produces fewer records than
     * the run before it still changed what every query answers, and a client left holding the old
     * rows has no other way to find out.
     *
     * <p>The event is a signal, not a payload — ids and the three dimensions a view is filtered by,
     * nothing a table could be drawn from (ADR-021). It is published inside the transaction, but a
     * listener that must not act on uncommitted work says so on its own side; deciding that here
     * would put another module's transaction policy in this method.
     */
    private void announce(String rawReportId, Instant analyzedAt, List<Incident> stored) {
        List<IncidentSignal> signals = stored.stream()
                .map(incident -> new IncidentSignal(incident.getId(), incident.getOccurredOn(),
                        incident.getEventType(), coveredProvinceCodes(incident)))
                .toList();

        events.publishEvent(new IncidentRecordsProducedEvent(rawReportId, analyzedAt, signals));
    }

    /**
     * The provinces a record answers a province filter for — one for {@code SINGLE}, all of them
     * for {@code SHARED}, none for {@code UNKNOWN} (ADR-033). Reading them here, inside the
     * transaction, is deliberate: both associations are lazy and the session closes with it.
     */
    private static Set<Short> coveredProvinceCodes(Incident incident) {
        return switch (incident.getProvinceScope()) {
            case SINGLE -> Set.of(incident.getProvince().getCode());
            case SHARED -> incident.getSharedProvinces().stream()
                    .map(Province::getCode)
                    .collect(Collectors.toUnmodifiableSet());
            case UNKNOWN -> Set.of();
        };
    }

    /**
     * Records that reading a report threw.
     *
     * <p>A separate transaction on purpose: the one {@link #analyze} opened has rolled back by the
     * time this runs, taking any half-written records with it. What must survive is the fact that
     * the attempt failed — otherwise the report would look untouched and nothing would ever tell
     * anyone to look at it again (FR-15).
     *
     * <p>The raw text is not involved either way. It was stored before analysis began and is not
     * written to again (ADR-005, ADR-021).
     */
    @Transactional
    public void recordFailure(String rawReportId, String failureReason) {
        record(AnalysisResult.failed(rawReportId, clock.instant(), failureReason));
    }

    /**
     * Writes the outcome, replacing any earlier one for the same report.
     *
     * <p>Reprocessing asks the same question again rather than a new one, so a second row would
     * leave two current answers and force every reader to work out which is real.
     */
    private void record(AnalysisResult outcome) {
        results.findByRawReportId(outcome.getRawReportId())
                .ifPresentOrElse(existing -> existing.replaceWith(outcome), () -> results.save(outcome));
    }

    /**
     * Maps one extracted incident onto the entity, choosing the factory that matches its scope.
     *
     * <p>The province rule is not restated here on purpose. {@code Incident}'s factories are the
     * one place that knows a province may only be attached to a {@code SINGLE} record, and the
     * schema enforces the same thing; if an extractor ever produces something self-contradictory,
     * this call fails loudly rather than storing it.
     */
    private Incident toEntity(String rawReportId, ExtractedIncident extracted) {
        Incident incident = switch (extracted.provinceScope()) {
            case SINGLE -> Incident.forProvince(rawReportId, extracted.occurredOn(), extracted.dateSource(),
                    province(extracted.provinceCode()), extracted.eventType(), extracted.classification());
            case SHARED -> Incident.sharedAcross(rawReportId, extracted.occurredOn(), extracted.dateSource(),
                    provinces(extracted.sharedProvinceCodes()), extracted.eventType(), extracted.classification());
            case UNKNOWN -> Incident.withoutProvince(rawReportId, extracted.occurredOn(), extracted.dateSource(),
                    extracted.eventType(), extracted.classification());
        };

        extracted.metrics().forEach(incident::addMetric);
        for (ExtractedKeyword keyword : extracted.keywords()) {
            incident.addKeyword(keyword.keyword(), keyword.role(), keyword.charStart(), keyword.charEnd());
        }
        return incident;
    }

    private Province province(Short code) {
        if (code == null) {
            throw new IllegalArgumentException("a SINGLE incident must name a province");
        }
        return provinces.findById(code)
                .orElseThrow(() -> new IllegalArgumentException("unknown province code " + code));
    }

    private List<Province> provinces(Set<Short> codes) {
        Map<Short, Province> found = provinces.findAllById(codes).stream()
                .collect(Collectors.toMap(Province::getCode, Function.identity()));

        List<Province> resolved = new ArrayList<>(codes.size());
        for (Short code : codes) {
            Province province = found.get(code);
            if (province == null) {
                throw new IllegalArgumentException("unknown province code " + code);
            }
            resolved.add(province);
        }
        return resolved;
    }
}
