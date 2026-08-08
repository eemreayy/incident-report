package com.emreay.incidentreport.analysis.repository;

import com.emreay.incidentreport.analysis.domain.Incident;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Storage for structured incident records.
 *
 * <p>Only this module may use it: PostgreSQL belongs to analysis (ADR-002).
 */
public interface IncidentRepository extends JpaRepository<Incident, Long> {

    /** Everything derived from one raw report — the reverse half of the traceability link (FR-08). */
    List<Incident> findByRawReportIdOrderByIdAsc(String rawReportId);

    /**
     * Reprocessing rebuilds rather than patches: the raw text cannot change (ADR-005), so
     * re-running the analysis is safe, and deleting first is what keeps a second run from
     * producing duplicate rows (FR-15).
     */
    long deleteByRawReportId(String rawReportId);
}
