package com.emreay.incidentreport.analysis.domain;

/**
 * Whether the event type was recognised (ADR-006).
 *
 * <p>An unrecognised report is stored rather than rejected, and this flag is what makes the gap
 * measurable: "what is the system failing to recognise" becomes a query instead of a guess.
 */
public enum ClassificationStatus {

    /** The text matched a catalog entry. */
    CLASSIFIED,

    /**
     * No catalog entry matched; the incident is filed under {@code OTHER}. Whatever could still be
     * extracted — date, province, numbers — is kept. Reprocessing after the catalog grows is what
     * turns these into classified records later (FR-15).
     */
    UNCLASSIFIED
}
