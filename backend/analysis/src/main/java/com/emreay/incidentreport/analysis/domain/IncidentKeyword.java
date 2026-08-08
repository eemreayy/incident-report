package com.emreay.incidentreport.analysis.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.Objects;

/**
 * A word from the raw text that triggered part of an extraction.
 *
 * <p>Shown to the user and usable as a filter (FR-17). The character offsets point into the raw
 * text held in MongoDB, so a caller can highlight exactly what the system reacted to — the kind of
 * explanation a rule-based pipeline can give and a model cannot (ADR-008).
 */
@Entity
@Table(name = "incident_keyword")
public class IncidentKeyword {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incident_id", nullable = false)
    private Incident incident;

    @Column(name = "keyword", nullable = false, length = 128)
    private String keyword;

    @Enumerated(EnumType.STRING)
    @Column(name = "keyword_role", nullable = false, length = 24)
    private KeywordRole role;

    /** Offset into the raw text, or {@code null} when the match cannot be located precisely. */
    @Column(name = "char_start")
    private Integer charStart;

    @Column(name = "char_end")
    private Integer charEnd;

    protected IncidentKeyword() {
        // for JPA
    }

    IncidentKeyword(Incident incident, String keyword, KeywordRole role, Integer charStart, Integer charEnd) {
        this.incident = Objects.requireNonNull(incident, "incident");
        this.keyword = Objects.requireNonNull(keyword, "keyword");
        this.role = Objects.requireNonNull(role, "role");
        this.charStart = charStart;
        this.charEnd = charEnd;
    }

    public String getKeyword() {
        return keyword;
    }

    public KeywordRole getRole() {
        return role;
    }

    public Integer getCharStart() {
        return charStart;
    }

    public Integer getCharEnd() {
        return charEnd;
    }
}
