package com.emreay.incidentreport.analysis.service;

import java.util.List;

/**
 * What analysing one report produced, as the analysis side reports it back.
 *
 * @param incidentCount structured records written; zero is a legitimate answer
 * @param warnings      what the user should know about the result being partial (FR-09)
 */
public record AnalysisOutcome(int incidentCount, List<String> warnings) {

    public AnalysisOutcome {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
