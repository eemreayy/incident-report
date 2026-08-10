package com.emreay.incidentreport.analysis.extraction;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.emreay.incidentreport.analysis.catalog.IncidentCatalog;
import com.emreay.incidentreport.analysis.domain.ClassificationStatus;
import com.emreay.incidentreport.analysis.domain.ProvinceScope;
import com.emreay.incidentreport.analysis.text.NormalizedText;

/**
 * The extractor while there are no classification rules yet.
 *
 * <p>This is not a stub that fakes an answer. It is the genuine degenerate case of the real
 * pipeline: when nothing in the catalog matches a text, the system stores it as {@code OTHER} /
 * {@code UNCLASSIFIED} and says so in a warning (ADR-006). Today no text is classified, so every
 * text takes that path — and every text takes it for the right reason.
 *
 * <p>The date, however, is already real. Whether a text says "20.04.2020", "son 24 saatte" or
 * nothing at all is independent of whether its event type is recognised, so an unclassified record
 * is dated from the text wherever the text gives a date (ADR-014).
 */
@Component
public class UnclassifiedIncidentExtractor implements IncidentExtractor {

    static final String NOT_RECOGNISED =
            "No known event type matched this text. It was stored as OTHER and can be reprocessed "
                    + "once the catalog recognises it.";

    static final String DATE_ASSUMED =
            "No date was found in the text; the submission date was used.";

    private final DateResolver dateResolver;

    public UnclassifiedIncidentExtractor(DateResolver dateResolver) {
        this.dateResolver = dateResolver;
    }

    @Override
    public ExtractionResult extract(NormalizedText text, LocalDate referenceDate) {
        ResolvedDate date = dateResolver.resolve(text, referenceDate);

        ExtractedIncident incident = new ExtractedIncident(
                date.date(),
                date.source(),
                ProvinceScope.UNKNOWN,
                null,
                null,
                IncidentCatalog.UNCLASSIFIED_EVENT_TYPE,
                ClassificationStatus.UNCLASSIFIED,
                Map.of(),
                List.of());

        List<String> warnings = new ArrayList<>();
        warnings.add(NOT_RECOGNISED);
        // Only when it is true. Warning that a date was assumed on a text that plainly states one
        // trains the reader to ignore warnings.
        if (!date.wasExtracted()) {
            warnings.add(DATE_ASSUMED);
        }

        return new ExtractionResult(List.of(incident), List.copyOf(warnings));
    }
}
