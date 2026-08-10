package com.emreay.incidentreport.analysis.extraction;

/**
 * What the extractor tells the user it could not do.
 *
 * <p>A partial result nobody is told about is worse than no result (FR-09), and these are the two
 * ways a record can be partial: the catalog did not recognise the event, or the text carried no
 * time expression. Each is only reported when it is actually true — warning that a date was assumed
 * on a text that plainly states one teaches the reader to skip warnings.
 */
public final class ExtractionWarnings {

    public static final String NOT_RECOGNISED =
            "No known event type matched this text. It was stored as OTHER and can be reprocessed "
                    + "once the catalog recognises it.";

    public static final String DATE_ASSUMED =
            "No date was found in the text; the submission date was used.";

    private ExtractionWarnings() {
    }
}
