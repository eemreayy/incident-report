package com.emreay.incidentreport.ingestion.repository;

import com.emreay.incidentreport.ingestion.domain.RawIncidentReport;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Storage for raw reports.
 *
 * <p>Only this module may use it: MongoDB belongs to ingestion (ADR-002).
 *
 * <p>Nothing here queries by analysis status, and nothing here updates a stored report. Reports
 * whose analysis failed are found on the analysis side, which is where that outcome lives
 * (ADR-021) — asking this repository for them would mean this module knowing something it does not
 * own.
 *
 * <p>{@code MongoRepository} does expose {@code save} and {@code delete}. The write-once guarantee
 * is upheld above it: no endpoint offers update or delete, the service never saves a report twice,
 * and the document itself is a record with no way to produce a modified copy.
 */
public interface RawIncidentReportRepository extends MongoRepository<RawIncidentReport, String> {
}
