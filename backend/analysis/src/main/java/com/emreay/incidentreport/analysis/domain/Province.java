package com.emreay.incidentreport.analysis.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Objects;

/**
 * One of the 81 provinces of Turkey, keyed by its licence plate code.
 *
 * <p>Reference data, loaded by a Flyway migration rather than written at runtime. Having it as a
 * table instead of a free-text column buys referential integrity — a typo cannot become a province
 * — and gives the metadata endpoint (FR-16) and the province extractor (T-12) one source of truth.
 */
@Entity
@Table(name = "province")
public class Province {

    @Id
    @Column(name = "code", nullable = false)
    private Short code;

    @Column(name = "name", nullable = false, unique = true)
    private String name;

    protected Province() {
        // for JPA
    }

    public Short getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof Province province && Objects.equals(code, province.code);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(code);
    }

    @Override
    public String toString() {
        return code + " " + name;
    }
}
