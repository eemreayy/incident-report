package com.emreay.incidentreport.analysis.query;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.emreay.incidentreport.analysis.repository.AnalysisResultRepository;
import com.emreay.incidentreport.analysis.repository.IncidentRepository;
import com.emreay.incidentreport.analysis.web.AnalysisSummaryResponse;
import com.emreay.incidentreport.analysis.web.IncidentResponse;

/**
 * Reads what analysis produced (FR-10, FR-08).
 *
 * <p>It answers with responses rather than entities, and that is not a style preference. This
 * application runs with {@code open-in-view: false}, so the persistence session ends with the
 * transaction that opened it. Handing an entity to a controller therefore hands it a half-loaded
 * object: reading its metrics, keywords or shared provinces afterwards throws. Mapping here, inside
 * the read transaction, is what makes the answer complete — and it fails as a 500 in production
 * rather than in a repository test, because a test transaction stays open around the assertions.
 */
@Service
@Transactional(readOnly = true)
public class IncidentQueryService {

    private final IncidentRepository incidents;
    private final AnalysisResultRepository results;

    public IncidentQueryService(IncidentRepository incidents, AnalysisResultRepository results) {
        this.incidents = incidents;
        this.results = results;
    }

    public Page<IncidentResponse> find(IncidentQuery query, Pageable pageable) {
        return incidents.findAll(IncidentSpecifications.matching(query), pageable)
                .map(IncidentResponse::of);
    }

    public Optional<IncidentResponse> findOne(long id) {
        return incidents.findById(id).map(IncidentResponse::of);
    }

    /**
     * The analysis outcome for a query about a single report, if there is one.
     *
     * <p>Absent for a general listing: records from many reports are mixed there and no single
     * outcome would describe them. Absent too when the id matches nothing, which is how a caller
     * tells "not analysed yet" from "analysed and found nothing".
     */
    public Optional<AnalysisSummaryResponse> outcomeFor(IncidentQuery query) {
        return query.isAboutOneReport()
                ? results.findByRawReportId(query.rawReportId()).map(AnalysisSummaryResponse::of)
                : Optional.empty();
    }
}
