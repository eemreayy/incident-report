package com.emreay.incidentreport.analysis.extraction;

import com.emreay.incidentreport.analysis.domain.ClassificationStatus;
import com.emreay.incidentreport.analysis.domain.DateSource;
import com.emreay.incidentreport.analysis.domain.ProvinceScope;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * The extractor while there is no catalog and no rules yet.
 *
 * <p>This is not a stub that fakes an answer. It is the genuine degenerate case of the real
 * pipeline: when nothing in the catalog matches a text, the system stores it as {@code OTHER} /
 * {@code UNCLASSIFIED}, dates it from the submission, and says so in a warning (ADR-006). Today the
 * catalog is empty, so every text takes that path — and every text takes it for the right reason.
 *
 * <p>Which means the record it produces is correct, not provisional. When the real extractors land
 * (T-09 to T-14) they will answer for the texts they recognise and leave this behaviour in place
 * for the rest.
 */
@Component
public class UnclassifiedIncidentExtractor implements IncidentExtractor {

    /** Catalog key for "we could not tell what this is" (ADR-006). */
    public static final String OTHER = "OTHER";

    static final String NOT_RECOGNISED =
            "No known event type matched this text. It was stored as OTHER and can be reprocessed "
                    + "once the catalog recognises it.";

    static final String DATE_ASSUMED =
            "No date was found in the text; the submission date was used.";

    @Override
    public ExtractionResult extract(String rawText, LocalDate referenceDate) {
        ExtractedIncident incident = new ExtractedIncident(
                referenceDate,
                DateSource.DEFAULTED,
                ProvinceScope.UNKNOWN,
                null,
                null,
                OTHER,
                ClassificationStatus.UNCLASSIFIED,
                Map.of(),
                List.of());

        return new ExtractionResult(List.of(incident), List.of(NOT_RECOGNISED, DATE_ASSUMED));
    }
}
