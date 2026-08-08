package com.emreay.incidentreport.analysis.repository;

import com.emreay.incidentreport.analysis.domain.Province;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Read access to the province reference data.
 *
 * <p>The rows are written by a Flyway migration, never at runtime. This repository serves the
 * metadata endpoint (FR-16) and the province extractor (T-12).
 */
public interface ProvinceRepository extends JpaRepository<Province, Short> {

    Optional<Province> findByName(String name);
}
