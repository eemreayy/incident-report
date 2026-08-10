package com.emreay.incidentreport.analysis.repository;

/**
 * How far an aggregated row has been rolled up.
 *
 * <p>All three levels come from one query, because the alternative is the client adding the detail
 * rows up itself — and a total the reader computed is a total nobody checked. It also keeps the
 * levels consistent by construction: a rounding or filtering difference between "the rows" and
 * "the total" cannot appear if both were produced by the same statement.
 */
public enum SummaryLevel {

    /** One event type in one province bucket. */
    BREAKDOWN,

    /** One event type, across every bucket the filters allow. */
    EVENT_TYPE,

    /** Everything the filters allow. */
    TOTAL
}
