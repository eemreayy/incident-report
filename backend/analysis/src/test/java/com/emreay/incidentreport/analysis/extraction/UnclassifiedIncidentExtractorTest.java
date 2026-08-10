package com.emreay.incidentreport.analysis.extraction;

import com.emreay.incidentreport.analysis.catalog.IncidentCatalog;
import com.emreay.incidentreport.analysis.domain.ClassificationStatus;
import com.emreay.incidentreport.analysis.domain.DateSource;
import com.emreay.incidentreport.analysis.domain.ProvinceScope;
import com.emreay.incidentreport.analysis.text.NormalizedText;
import com.emreay.incidentreport.analysis.text.SentenceSplitter;
import com.emreay.incidentreport.analysis.text.TurkishTextNormalizer;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * This extractor is not a stub standing in for the real one — it is the path every text takes when
 * nothing in the catalog matches, which today means every text. What it must not do is invent an
 * answer or throw away the report.
 */
class UnclassifiedIncidentExtractorTest {

    private static final LocalDate REFERENCE_DATE = LocalDate.of(2020, 4, 20);

    private final TurkishTextNormalizer normalizer = new TurkishTextNormalizer(new SentenceSplitter());
    private final UnclassifiedIncidentExtractor extractor =
            new UnclassifiedIncidentExtractor(new DateResolver());

    private ExtractionResult extract(String rawText) {
        NormalizedText text = normalizer.normalize(rawText);
        return extractor.extract(text, REFERENCE_DATE);
    }

    @Test
    void keepsTheReportAsUnclassifiedRatherThanDiscardingIt() {
        ExtractionResult result = extract("20.04.2020 Ankara'da 15 vaka");

        assertThat(result.incidents()).singleElement().satisfies(incident -> {
            assertThat(incident.eventType()).isEqualTo(IncidentCatalog.UNCLASSIFIED_EVENT_TYPE);
            assertThat(incident.classification()).isEqualTo(ClassificationStatus.UNCLASSIFIED);
            assertThat(incident.provinceScope()).isEqualTo(ProvinceScope.UNKNOWN);
            assertThat(incident.metrics()).isEmpty();
        });
    }

    /** Dating from the submission is a real decision, and it has to be visible as one (ADR-014). */
    @Test
    void datesTheRecordFromTheReferenceDateAndSaysThatIsWhatItDid() {
        ExtractionResult result = extract("herhangi bir metin");

        assertThat(result.incidents().get(0).occurredOn()).isEqualTo(REFERENCE_DATE);
        assertThat(result.incidents().get(0).dateSource())
                .as("assumed, not extracted - the difference must stay visible")
                .isEqualTo(DateSource.DEFAULTED);
    }

    /** A partial result the user is not told about is worse than no result (FR-09). */
    @Test
    void tellsTheUserWhyTheResultIsEmpty() {
        ExtractionResult result = extract("herhangi bir metin");

        assertThat(result.warnings()).containsExactly(
                UnclassifiedIncidentExtractor.NOT_RECOGNISED,
                UnclassifiedIncidentExtractor.DATE_ASSUMED);
    }

    /**
     * An unrecognised event type says nothing about whether the text stated a date. Reporting
     * "no date was found" on a text that opens with one would train the reader to ignore warnings.
     */
    @Test
    void datesTheRecordFromTheTextWhenTheTextGivesOne() {
        ExtractionResult result = extract("20.04.2020 tarihinde Ankara'da 15 vaka tespit edildi");

        assertThat(result.incidents().get(0).occurredOn()).isEqualTo(LocalDate.of(2020, 4, 20));
        assertThat(result.incidents().get(0).dateSource()).isEqualTo(DateSource.EXPLICIT);
        assertThat(result.warnings())
                .containsExactly(UnclassifiedIncidentExtractor.NOT_RECOGNISED)
                .doesNotContain(UnclassifiedIncidentExtractor.DATE_ASSUMED);
    }

    @Test
    void aRelativeExpressionIsAnExtractionEvenHere() {
        ExtractionResult result = extract("Son 24 saatte Bursa'da 8 trafik kazası meydana geldi");

        assertThat(result.incidents().get(0).occurredOn()).isEqualTo(REFERENCE_DATE);
        assertThat(result.incidents().get(0).dateSource()).isEqualTo(DateSource.RELATIVE);
        assertThat(result.warnings()).doesNotContain(UnclassifiedIncidentExtractor.DATE_ASSUMED);
    }

    @Test
    void neverThrowsWhateverTheTextLooksLike() {
        assertThat(extract("").incidents()).hasSize(1);
        assertThat(extract("!!! ???").incidents()).hasSize(1);
    }
}
