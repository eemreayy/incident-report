package com.emreay.incidentreport.ingestion.domain;

/**
 * Where a raw report stands in the pipeline.
 *
 * <p>This is derived state, not something a user can set: there is no API that changes it. The
 * raw text and its submission time never change (ADR-005); only this status does, and only as a
 * result of the system analysing the report.
 */
public enum ProcessingStatus {

    /** Stored, not analysed yet. */
    RECEIVED,

    /** Analysis finished. It may still have produced warnings — see {@code warnings}. */
    ANALYZED,

    /**
     * Analysis failed. The raw text is kept regardless: persisting it and analysing it are separate
     * concerns, and a failure here must never cost us the text. Such a report is the natural
     * candidate for reprocessing (FR-15).
     */
    FAILED
}
