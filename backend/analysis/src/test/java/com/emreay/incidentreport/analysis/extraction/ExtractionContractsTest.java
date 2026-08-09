package com.emreay.incidentreport.analysis.extraction;

import com.emreay.incidentreport.analysis.domain.ClassificationStatus;
import com.emreay.incidentreport.analysis.domain.DateSource;
import com.emreay.incidentreport.analysis.domain.KeywordRole;
import com.emreay.incidentreport.analysis.domain.ProvinceScope;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The values that travel from extraction to persistence. They are built by rule code and consumed
 * by mapping code, so they have to be safe to hand over: no nulls where a collection is expected,
 * and no shared mutable state.
 */
class ExtractionContractsTest {

    private static final LocalDate WHEN = LocalDate.of(2020, 4, 20);

    @Test
    void anExtractedIncidentNeverHandsBackNullCollections() {
        ExtractedIncident incident = new ExtractedIncident(WHEN, DateSource.DEFAULTED,
                ProvinceScope.UNKNOWN, null, null, "OTHER", ClassificationStatus.UNCLASSIFIED,
                null, null);

        assertThat(incident.sharedProvinceCodes()).isEmpty();
        assertThat(incident.metrics()).isEmpty();
        assertThat(incident.keywords()).isEmpty();
    }

    @Test
    void anExtractedIncidentDoesNotKeepTheBuildersCollections() {
        Map<String, Integer> metrics = new HashMap<>(Map.of("NEW_CASE", 15));

        ExtractedIncident incident = new ExtractedIncident(WHEN, DateSource.EXPLICIT,
                ProvinceScope.UNKNOWN, null, null, "EPIDEMIC", ClassificationStatus.CLASSIFIED,
                metrics, List.of());
        metrics.put("DEATH", 1);

        assertThat(incident.metrics()).containsExactly(Map.entry("NEW_CASE", 15));
    }

    @Test
    void anExtractedIncidentInsistsOnTheFieldsThatIdentifyIt() {
        assertThatThrownBy(() -> new ExtractedIncident(null, DateSource.EXPLICIT, ProvinceScope.UNKNOWN,
                null, null, "OTHER", ClassificationStatus.UNCLASSIFIED, Map.of(), List.of()))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("occurredOn");
        assertThatThrownBy(() -> new ExtractedIncident(WHEN, DateSource.EXPLICIT, ProvinceScope.UNKNOWN,
                null, null, null, ClassificationStatus.UNCLASSIFIED, Map.of(), List.of()))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("eventType");
    }

    @Test
    void aKeywordAlwaysSaysWhatItWasEvidenceFor() {
        ExtractedKeyword keyword = new ExtractedKeyword("deprem", KeywordRole.EVENT_TYPE, 12, 18);

        assertThat(keyword.role()).isEqualTo(KeywordRole.EVENT_TYPE);
        assertThatThrownBy(() -> new ExtractedKeyword("deprem", null, 0, 6))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("role");
    }

    /** Offsets are optional: a date assembled from several tokens has no single position. */
    @Test
    void aKeywordMayHaveNoPosition() {
        assertThat(new ExtractedKeyword("3 Mayıs 2020", KeywordRole.DATE, null, null).charStart()).isNull();
    }

    @Test
    void anExtractionResultTreatsMissingListsAsEmpty() {
        ExtractionResult result = new ExtractionResult(null, null);

        assertThat(result.incidents()).isEmpty();
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void anExtractionResultDoesNotKeepTheBuildersLists() {
        List<String> warnings = new ArrayList<>(List.of("bir uyarı"));

        ExtractionResult result = new ExtractionResult(List.of(), warnings);
        warnings.add("sonradan eklendi");

        assertThat(result.warnings()).containsExactly("bir uyarı");
    }
}
