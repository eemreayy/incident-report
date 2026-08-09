package com.emreay.incidentreport.analysis.extraction;

import java.time.LocalDate;

/**
 * Turns one free-form Turkish text into structured incidents.
 *
 * <p>The seam the whole analysis side is built around. Everything behind it — normalising Turkish
 * text, reading numbers written as words, resolving dates, recognising provinces, matching numbers
 * to metrics — plugs in here, and none of it needs a database or a Spring context to be tested.
 *
 * <p>An implementation must not throw for text it cannot read. Refusing to answer would cost the
 * report its structured record entirely; saying "I found nothing, and here is why" keeps whatever
 * could be salvaged and makes the gap measurable (ADR-006).
 */
public interface IncidentExtractor {

    /**
     * @param rawText       the submitted text, exactly as stored
     * @param referenceDate the date relative expressions resolve against, and the date used when
     *                      the text carries none. This is the report's <em>submission</em> date,
     *                      never today's — otherwise reprocessing an old report would move it to
     *                      the present (ADR-014).
     */
    ExtractionResult extract(String rawText, LocalDate referenceDate);
}
