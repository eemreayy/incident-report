package com.emreay.incidentreport.analysis.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * How one raw report was read: the outcome, when, and what the reader could not do.
 *
 * <p>One row per raw report, not per attempt. Reprocessing overwrites it rather than adding a
 * second row, because the question this answers — "what does the system currently know about this
 * text" — has exactly one current answer (FR-15).
 *
 * <p>It lives here, on the analysis side, because this module produces it. Keeping it on the raw
 * document instead meant the ingestion module publishing data it did not own, and a raw record that
 * was still being written to after it was stored (ADR-021).
 *
 * <p>{@code failureReason} is for whoever operates the system. It holds an exception type and
 * message, so it stays server-side and must never be mapped into a response — the same rule that
 * keeps stack traces out of the error contract.
 */
@Entity
@Table(name = "analysis_result")
public class AnalysisResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** MongoDB ObjectId of the report this describes. Unique: one current answer per report. */
    @Column(name = "raw_report_id", nullable = false, unique = true, length = 24)
    private String rawReportId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private AnalysisStatus status;

    @Column(name = "analyzed_at", nullable = false)
    private Instant analyzedAt;

    /** How many structured records the text produced. Zero is a legitimate answer. */
    @Column(name = "incident_count", nullable = false)
    private int incidentCount;

    /** Server-side only — never mapped into a response. */
    @Column(name = "failure_reason", length = 1024)
    private String failureReason;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "analysis_warning", joinColumns = @JoinColumn(name = "analysis_result_id"))
    @Column(name = "warning", nullable = false, length = 512)
    private List<String> warnings = new ArrayList<>();

    protected AnalysisResult() {
        // for JPA
    }

    private AnalysisResult(String rawReportId, AnalysisStatus status, Instant analyzedAt) {
        this.rawReportId = Objects.requireNonNull(rawReportId, "rawReportId");
        this.status = status;
        this.analyzedAt = Objects.requireNonNull(analyzedAt, "analyzedAt");
    }

    public static AnalysisResult analyzed(String rawReportId, Instant analyzedAt,
                                          int incidentCount, Collection<String> warnings) {
        if (incidentCount < 0) {
            throw new IllegalArgumentException("incidentCount must not be negative, got " + incidentCount);
        }
        AnalysisResult result = new AnalysisResult(rawReportId, AnalysisStatus.ANALYZED, analyzedAt);
        result.incidentCount = incidentCount;
        if (warnings != null) {
            result.warnings.addAll(warnings);
        }
        return result;
    }

    public static AnalysisResult failed(String rawReportId, Instant analyzedAt, String failureReason) {
        AnalysisResult result = new AnalysisResult(rawReportId, AnalysisStatus.FAILED, analyzedAt);
        result.failureReason = Objects.requireNonNull(failureReason, "failureReason");
        return result;
    }

    /**
     * Replaces this result with a newer one, keeping the row.
     *
     * <p>Reprocessing answers the same question again; it does not ask a new one. Inserting instead
     * would leave two current answers and force every reader to work out which is the real one.
     */
    public void replaceWith(AnalysisResult newer) {
        if (!rawReportId.equals(newer.rawReportId)) {
            throw new IllegalArgumentException(
                    "cannot replace the result for " + rawReportId + " with one for " + newer.rawReportId);
        }
        this.status = newer.status;
        this.analyzedAt = newer.analyzedAt;
        this.incidentCount = newer.incidentCount;
        this.failureReason = newer.failureReason;
        this.warnings.clear();
        this.warnings.addAll(newer.warnings);
    }

    public Long getId() {
        return id;
    }

    public String getRawReportId() {
        return rawReportId;
    }

    public AnalysisStatus getStatus() {
        return status;
    }

    public Instant getAnalyzedAt() {
        return analyzedAt;
    }

    public int getIncidentCount() {
        return incidentCount;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public List<String> getWarnings() {
        return List.copyOf(warnings);
    }

    /** Touches no lazy association — see the note on {@code Incident.toString()}. */
    @Override
    public String toString() {
        return "AnalysisResult[" + rawReportId + ", " + status + ", " + incidentCount + " records]";
    }
}
