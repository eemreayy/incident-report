package com.emreay.incidentreport.analysis.domain;

/**
 * What an extracted keyword was evidence for (FR-17).
 *
 * <p>Stored so the user can see why the system decided what it decided — the explainability that
 * a rule-based pipeline buys us over a model (ADR-008).
 */
public enum KeywordRole {
    EVENT_TYPE,
    METRIC,
    PROVINCE,
    DATE
}
