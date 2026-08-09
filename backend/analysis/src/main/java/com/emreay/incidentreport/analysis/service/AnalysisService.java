package com.emreay.incidentreport.analysis.service;

import com.emreay.incidentreport.analysis.domain.Incident;
import com.emreay.incidentreport.analysis.domain.Province;
import com.emreay.incidentreport.analysis.extraction.ExtractedIncident;
import com.emreay.incidentreport.analysis.extraction.ExtractedKeyword;
import com.emreay.incidentreport.analysis.extraction.ExtractionResult;
import com.emreay.incidentreport.analysis.extraction.IncidentExtractor;
import com.emreay.incidentreport.analysis.repository.IncidentRepository;
import com.emreay.incidentreport.analysis.repository.ProvinceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
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
 */
@Service
public class AnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisService.class);

    private final IncidentExtractor extractor;
    private final IncidentRepository incidents;
    private final ProvinceRepository provinces;

    public AnalysisService(IncidentExtractor extractor,
                           IncidentRepository incidents,
                           ProvinceRepository provinces) {
        this.extractor = extractor;
        this.incidents = incidents;
        this.provinces = provinces;
    }

    /**
     * @param submittedAt the report's submission time, which becomes the reference date for
     *                    relative and defaulted dates. Never "now": reprocessing a two-year-old
     *                    report must still date it two years ago (ADR-014).
     */
    @Transactional
    public AnalysisOutcome analyze(String rawReportId, String rawText, Instant submittedAt) {
        LocalDate referenceDate = LocalDate.ofInstant(submittedAt, ZoneOffset.UTC);
        ExtractionResult result = extractor.extract(rawText, referenceDate);

        long removed = incidents.deleteByRawReportId(rawReportId);
        if (removed > 0) {
            log.debug("rebuilding raw report {}: removed {} previously derived records", rawReportId, removed);
        }

        List<Incident> toStore = result.incidents().stream()
                .map(extracted -> toEntity(rawReportId, extracted))
                .toList();
        incidents.saveAll(toStore);

        log.info("analysed raw report {}: {} records, {} warnings",
                rawReportId, toStore.size(), result.warnings().size());

        return new AnalysisOutcome(toStore.size(), result.warnings());
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
