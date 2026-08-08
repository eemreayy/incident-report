package com.emreay.incidentreport.ingestion.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * A raw incident report exactly as the user submitted it.
 *
 * <p>This is the audit log the source document asks for: {@code rawText} is stored byte for byte,
 * before any normalisation, and never changes afterwards (FR-02, ADR-005). Being a record is not a
 * stylistic choice — it makes that immutability structural. A state change produces a new instance
 * rather than mutating this one, so there is no setter anyone could reach for.
 *
 * <p>{@code submittedAt} carries more weight than it appears to: it is the reference date for
 * relative expressions such as "son 24 saatte" and for reports with no date at all (ADR-014).
 * Because it is fixed, reprocessing a report years later still resolves the same calendar day.
 *
 * <p>The link to the structured records derived from this report is <em>not</em> stored here. It
 * lives on the PostgreSQL side as {@code incident.raw_report_id}, and the reverse direction is a
 * query on that column. Keeping a list of foreign ids here would mean this module knows about the
 * other module's identifiers and has to be updated whenever they change (FR-08, ADR-002).
 */
@Document(collection = "raw_incident_reports")
public record RawIncidentReport(

        @Id String id,

        /** Verbatim submission. Never normalised, never edited. */
        String rawText,

        /** When the text was accepted. Also the reference date for date resolution (ADR-014). */
        Instant submittedAt,

        ProcessingStatus status,

        /** When analysis last finished or failed; {@code null} while still {@link ProcessingStatus#RECEIVED}. */
        Instant analyzedAt,

        /** Why analysis failed, if it did. {@code null} otherwise. */
        String failureReason,

        /**
         * Messages telling the user the result is partial — an unrecognised event type, a date that
         * had to be defaulted (FR-09). Never empty-null: an analysed report with nothing to warn
         * about carries an empty list.
         */
        List<String> warnings) {

    public RawIncidentReport {
        Objects.requireNonNull(rawText, "rawText");
        Objects.requireNonNull(submittedAt, "submittedAt");
        Objects.requireNonNull(status, "status");
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    /** A freshly submitted report, not yet analysed. The id is assigned by MongoDB on insert. */
    public static RawIncidentReport received(String rawText, Instant submittedAt) {
        return new RawIncidentReport(null, rawText, submittedAt, ProcessingStatus.RECEIVED, null, null, List.of());
    }

    /** The same report, marked analysed. The text and submission time are carried over untouched. */
    public RawIncidentReport analyzed(Instant analyzedAt, List<String> warnings) {
        return new RawIncidentReport(id, rawText, submittedAt, ProcessingStatus.ANALYZED,
                Objects.requireNonNull(analyzedAt, "analyzedAt"), null, warnings);
    }

    /** The same report, marked failed. The text survives the failure — that is the whole point. */
    public RawIncidentReport failed(Instant analyzedAt, String failureReason) {
        return new RawIncidentReport(id, rawText, submittedAt, ProcessingStatus.FAILED,
                Objects.requireNonNull(analyzedAt, "analyzedAt"),
                Objects.requireNonNull(failureReason, "failureReason"), List.of());
    }
}
