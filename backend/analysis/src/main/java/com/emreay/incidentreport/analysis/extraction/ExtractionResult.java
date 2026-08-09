package com.emreay.incidentreport.analysis.extraction;

import java.util.List;

/**
 * What reading one text produced.
 *
 * <p>Warnings travel alongside the incidents rather than being thrown, because a partial result is
 * still a result: an unrecognised event type does not make the date and province worthless
 * (ADR-006). The caller decides how to present them; the extractor only says what it could not do.
 *
 * @param incidents one per distinct (date, province, event type) the text contains — ADR-019.
 *                  An empty list is a legitimate answer.
 * @param warnings  what the user should know about the result being partial
 */
public record ExtractionResult(List<ExtractedIncident> incidents, List<String> warnings) {

    public ExtractionResult {
        incidents = incidents == null ? List.of() : List.copyOf(incidents);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
