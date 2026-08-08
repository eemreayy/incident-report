package com.emreay.incidentreport.analysis.domain;

/**
 * Where an incident's date came from (ADR-014).
 *
 * <p>Kept alongside the date itself so a reader can tell a figure read out of the text from one the
 * system assumed. Without it, defaulted records pile up on the submission day and the distortion is
 * invisible.
 */
public enum DateSource {

    /** The text carried an explicit calendar date: "20.04.2020", "3 Mayıs 2020". */
    EXPLICIT,

    /**
     * The text carried a relative expression — "son 24 saatte", "dün" — resolved against the
     * report's submission date. This is an extraction, not a fallback: the information was in the
     * text. Never collapse it into {@link #DEFAULTED}.
     */
    RELATIVE,

    /** No time expression at all; the submission date was used. */
    DEFAULTED
}
