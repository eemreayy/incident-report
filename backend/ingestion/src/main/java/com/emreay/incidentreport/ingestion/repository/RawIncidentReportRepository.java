package com.emreay.incidentreport.ingestion.repository;

import com.emreay.incidentreport.ingestion.domain.ProcessingStatus;
import com.emreay.incidentreport.ingestion.domain.RawIncidentReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Storage for raw reports.
 *
 * <p>Only this module may use it: MongoDB belongs to ingestion (ADR-002). Note that
 * {@code MongoRepository} does expose {@code delete} and {@code save}; the immutability guarantee
 * of FR-02 is upheld at the API level — no endpoint offers update or delete — and by the document
 * being a record, so a "modification" can only ever produce a new instance carrying the same text.
 */
public interface RawIncidentReportRepository extends MongoRepository<RawIncidentReport, String> {

    /** Reports whose analysis failed — the natural input for a reprocess run (FR-15). */
    Page<RawIncidentReport> findByStatus(ProcessingStatus status, Pageable pageable);
}
