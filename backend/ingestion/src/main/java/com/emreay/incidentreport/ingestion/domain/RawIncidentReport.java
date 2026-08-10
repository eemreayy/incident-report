package com.emreay.incidentreport.ingestion.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Objects;

/**
 * A raw incident report exactly as the user submitted it.
 *
 * <p>This is the audit log the source document asks for, and it is <strong>write-once</strong>:
 * inserted, then never written to again — not even to record how analysis went (ADR-005, ADR-021).
 * Every field on it is something this module owns: the text, when it arrived, and a digest of the
 * text itself.
 *
 * <p>How the analysis went — its status, warnings and timing — belongs to the module that produces
 * it and is stored there. An earlier version kept those fields here, and the cost was not visible
 * in the dependency graph: this module's document schema carried the other module's vocabulary, and
 * a write was not considered finished until the other module had answered. That kind of coupling
 * only presents its bill when the transport changes.
 *
 * <p>Being a record is what makes the guarantee structural rather than a promise: there is no
 * setter, and no method that returns a modified copy, so "updating" a stored report is not
 * expressible.
 *
 * <p>{@code submittedAt} carries more weight than it appears to: it is the reference date for
 * relative expressions such as "son 24 saatte" and for reports with no date at all (ADR-014).
 * Because it never changes, reprocessing a report years later still resolves the same calendar day.
 *
 * <p>The link to the structured records derived from this report is not stored here either. It
 * lives on the PostgreSQL side as {@code incident.raw_report_id}, and the reverse direction is a
 * query on that column (FR-08).
 */
@Document(collection = "raw_incident_reports")
public record RawIncidentReport(

        @Id String id,

        /** Verbatim submission. Never normalised, never edited. */
        String rawText,

        /**
         * SHA-256 of {@link #rawText}, hex encoded — the identity of the text itself.
         *
         * <p>Derived from what this module already owns, and the only reason it is stored rather
         * than computed on demand: a unique index needs a field. It is what makes a repeated
         * submission recognisable, and therefore what stops a double-clicked form from counting the
         * same casualties twice.
         *
         * <p>Over the exact bytes, with no trimming and no normalisation. Two texts that differ by
         * a space are two texts; deciding otherwise would mean this module judging what counts as
         * "the same report", which is precisely the judgement an audit log must not make.
         */
        @Indexed(unique = true, sparse = true) String textHash,

        /** When the text was accepted. Also the reference date for date resolution (ADR-014). */
        Instant submittedAt) {

    public RawIncidentReport {
        Objects.requireNonNull(rawText, "rawText");
        Objects.requireNonNull(textHash, "textHash");
        Objects.requireNonNull(submittedAt, "submittedAt");
    }

    /**
     * A freshly submitted report. The id is assigned by MongoDB on insert.
     *
     * <p>Truncated to milliseconds so the value that comes back from MongoDB is the value that went
     * in — the driver stores millisecond precision, and a submission time that changes on a round
     * trip would be a poor reference date.
     */
    public static RawIncidentReport of(String rawText, Instant submittedAt) {
        return new RawIncidentReport(null, rawText, hashOf(rawText),
                Objects.requireNonNull(submittedAt, "submittedAt").truncatedTo(ChronoUnit.MILLIS));
    }

    /**
     * Hashes the text the way stored reports are hashed.
     *
     * <p>SHA-256 rather than {@code String.hashCode()}: a 32-bit hash collides by accident often
     * enough to matter, and a collision here would silently drop somebody's report as a duplicate
     * of an unrelated one.
     */
    private static String hashOf(String rawText) {
        Objects.requireNonNull(rawText, "rawText");
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawText.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            // Every JVM is required to provide SHA-256.
            throw new IllegalStateException("SHA-256 is not available", impossible);
        }
    }
}
