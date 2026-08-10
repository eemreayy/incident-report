package com.emreay.incidentreport.analysis.config;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.emreay.incidentreport.analysis.domain.ProvinceFixture;
import com.emreay.incidentreport.analysis.extraction.ProvinceExtractor;
import com.emreay.incidentreport.analysis.extraction.ProvinceMention;
import com.emreay.incidentreport.analysis.repository.ProvinceRepository;
import com.emreay.incidentreport.analysis.text.SentenceSplitter;
import com.emreay.incidentreport.analysis.text.TurkishTextNormalizer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnalysisConfigurationTest {

    private final AnalysisConfiguration configuration = new AnalysisConfiguration();

    @Test
    @DisplayName("a fresh clone files reports against Turkish local days")
    void defaultsToIstanbul() {
        assertThat(configuration.reportingZone("Europe/Istanbul")).isEqualTo(ZoneId.of("Europe/Istanbul"));
    }

    @Test
    @DisplayName("the zone is what decides which day a late-evening submission belongs to")
    void theZoneChangesTheDay() {
        // 21:30 UTC is already the next day in Istanbul. This is the whole reason the choice had to
        // be made explicitly rather than left at UTC (ADR-029).
        Instant submittedAt = Instant.parse("2020-04-20T21:30:00Z");

        assertThat(LocalDate.ofInstant(submittedAt, configuration.reportingZone("Europe/Istanbul")))
                .isEqualTo(LocalDate.of(2020, 4, 21));
        assertThat(LocalDate.ofInstant(submittedAt, ZoneId.of("UTC")))
                .isEqualTo(LocalDate.of(2020, 4, 20));
    }

    @Test
    @DisplayName("the province recogniser is built from the reference data, not from a list in code")
    void provinceExtractorReadsTheReferenceTable() {
        TurkishTextNormalizer normalizer = new TurkishTextNormalizer(new SentenceSplitter());
        ProvinceRepository provinces = mock(ProvinceRepository.class);
        when(provinces.findAll()).thenReturn(List.of(
                ProvinceFixture.province(6, "Ankara"), ProvinceFixture.province(35, "İzmir")));

        ProvinceExtractor extractor = configuration.provinceExtractor(provinces, normalizer);

        assertThat(extractor.mentions(normalizer.normalize("Ankara'da ve İzmir'de")))
                .extracting(ProvinceMention::name)
                .containsExactly("Ankara", "İzmir");
    }

    @Test
    @DisplayName("an empty province table stops startup instead of silencing every province")
    void anEmptyReferenceTableIsFatal() {
        ProvinceRepository provinces = mock(ProvinceRepository.class);
        when(provinces.findAll()).thenReturn(List.of());

        assertThatThrownBy(() -> configuration.provinceExtractor(
                provinces, new TurkishTextNormalizer(new SentenceSplitter())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reference data");
    }

    @Test
    @DisplayName("a misconfigured zone fails at startup, not on the first report")
    void anUnknownZoneIsRejected() {
        assertThatThrownBy(() -> configuration.reportingZone("Europe/Istanbulll"))
                .isInstanceOf(RuntimeException.class);
    }
}
