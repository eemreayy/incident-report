package com.emreay.incidentreport.analysis.query;

/**
 * Whether province is a dimension of the answer or only a filter on it (ADR-023, FR-24).
 *
 * <p>The difference is the whole of C-1. "Incidents by geographic region" is not one province at a
 * time; it is provinces next to each other. Without this the client would have to issue one request
 * per province and add them up, which is the client re-deriving what the server knows — the thing
 * NFR-13 exists to prevent.
 */
public enum ProvinceGrouping {

    /** One series per event type and metric, covering everything the filters allow. */
    NONE,

    /**
     * Province becomes part of the series key, and with it the scope: figures the text gave for
     * several provinces at once, and figures whose text named no province, come back as their own
     * labelled series rather than being divided between provinces or dropped (ADR-019).
     */
    PROVINCE
}
