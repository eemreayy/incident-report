package com.emreay.incidentreport.analysis.repository;

import com.emreay.incidentreport.analysis.domain.AnalysisResult;
import com.emreay.incidentreport.analysis.domain.AnalysisStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Storage for analysis outcomes.
 *
 * <p>This is also where "which reports need reprocessing" is answered (FR-15). It used to be a
 * query on the raw document's status; moving it here is the point of ADR-021 — the module that
 * decides a report failed is the module that can be asked about it.
 */
public interface AnalysisResultRepository extends JpaRepository<AnalysisResult, Long> {

    Optional<AnalysisResult> findByRawReportId(String rawReportId);

    /** Reports whose analysis threw — the natural input for a reprocess run. */
    Page<AnalysisResult> findByStatus(AnalysisStatus status, Pageable pageable);
}
