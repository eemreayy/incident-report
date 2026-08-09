package com.emreay.incidentreport.analysis.domain;

/**
 * How reading one raw report went.
 *
 * <p>Lives on the analysis side because this is the analysis module's answer about its own work
 * (ADR-021). The module that stored the text has no field for it and no reason to.
 */
public enum AnalysisStatus {

    /** The text was read. It may still have produced warnings, or no records at all. */
    ANALYZED,

    /**
     * Reading it threw. The text itself is untouched and still stored — persisting it and analysing
     * it are separate concerns — so the report can be reprocessed once the cause is fixed (FR-15).
     */
    FAILED
}
