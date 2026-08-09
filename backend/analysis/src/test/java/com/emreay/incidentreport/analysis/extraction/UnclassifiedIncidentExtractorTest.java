package com.emreay.incidentreport.analysis.extraction;

import com.emreay.incidentreport.analysis.domain.ClassificationStatus;
import com.emreay.incidentreport.analysis.domain.DateSource;
import com.emreay.incidentreport.analysis.domain.ProvinceScope;
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

    private final UnclassifiedIncidentExtractor extractor = new UnclassifiedIncidentExtractor();

    @Test
    void keepsTheReportAsUnclassifiedRatherThanDiscardingIt() {
        ExtractionResult result = extractor.extract("20.04.2020 Ankara'da 15 vaka", REFERENCE_DATE);

        assertThat(result.incidents()).singleElement().satisfies(incident -> {
            assertThat(incident.eventType()).isEqualTo("OTHER");
            assertThat(incident.classification()).isEqualTo(ClassificationStatus.UNCLASSIFIED);
            assertThat(incident.provinceScope()).isEqualTo(ProvinceScope.UNKNOWN);
            assertThat(incident.metrics()).isEmpty();
        });
    }

    /** Dating from the submission is a real decision, and it has to be visible as one (ADR-014). */
    @Test
    void datesTheRecordFromTheReferenceDateAndSaysThatIsWhatItDid() {
        ExtractionResult result = extractor.extract("herhangi bir metin", REFERENCE_DATE);

        assertThat(result.incidents().get(0).occurredOn()).isEqualTo(REFERENCE_DATE);
        assertThat(result.incidents().get(0).dateSource())
                .as("assumed, not extracted - the difference must stay visible")
                .isEqualTo(DateSource.DEFAULTED);
    }

    /** A partial result the user is not told about is worse than no result (FR-09). */
    @Test
    void tellsTheUserWhyTheResultIsEmpty() {
        ExtractionResult result = extractor.extract("herhangi bir metin", REFERENCE_DATE);

        assertThat(result.warnings()).containsExactly(
                UnclassifiedIncidentExtractor.NOT_RECOGNISED,
                UnclassifiedIncidentExtractor.DATE_ASSUMED);
    }

    @Test
    void neverThrowsWhateverTheTextLooksLike() {
        assertThat(extractor.extract("", REFERENCE_DATE).incidents()).hasSize(1);
        assertThat(extractor.extract("!!! ???", REFERENCE_DATE).incidents()).hasSize(1);
    }
}
