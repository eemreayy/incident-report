package com.emreay.incidentreport.analysis.domain;

/**
 * How an incident's numbers relate to geography (ADR-019).
 *
 * <p>The system exists to track incidents by geographic region, so "which province do these numbers
 * belong to" has to have an honest answer — including when that answer is "we cannot say".
 */
public enum ProvinceScope {

    /** The numbers belong to one named province. */
    SINGLE,

    /**
     * The text gives a total across several provinces without splitting it — "her iki ilde toplam
     * 10 kişi". The covered provinces are recorded, but the figure is never divided among them and
     * never added to any single province's total. Splitting it evenly would invent data the text
     * does not contain.
     */
    SHARED,

    /** No province appears in the text. The numbers are real, their location is not known. */
    UNKNOWN
}
