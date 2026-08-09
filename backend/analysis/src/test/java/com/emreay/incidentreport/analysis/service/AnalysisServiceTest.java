package com.emreay.incidentreport.analysis.service;

import com.emreay.incidentreport.analysis.domain.ClassificationStatus;
import com.emreay.incidentreport.analysis.domain.DateSource;
import com.emreay.incidentreport.analysis.domain.Incident;
import com.emreay.incidentreport.analysis.domain.IncidentMetric;
import com.emreay.incidentreport.analysis.domain.KeywordRole;
import com.emreay.incidentreport.analysis.domain.Province;
import com.emreay.incidentreport.analysis.domain.ProvinceScope;
import com.emreay.incidentreport.analysis.extraction.ExtractedIncident;
import com.emreay.incidentreport.analysis.extraction.ExtractedKeyword;
import com.emreay.incidentreport.analysis.extraction.ExtractionResult;
import com.emreay.incidentreport.analysis.extraction.IncidentExtractor;
import com.emreay.incidentreport.analysis.repository.IncidentRepository;
import com.emreay.incidentreport.analysis.repository.ProvinceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * How an extraction result becomes stored records — mapping, province resolution and the rebuild
 * rule. Plain Mockito: the persistence itself is covered against a real PostgreSQL elsewhere.
 */
class AnalysisServiceTest {

    private static final String REPORT_ID = "652f1a2b3c4d5e6f70819200";
    private static final Instant SUBMITTED_AT = Instant.parse("2020-04-20T21:30:00Z");
    private static final LocalDate REFERENCE_DATE = LocalDate.of(2020, 4, 20);

    private IncidentExtractor extractor;
    private IncidentRepository incidents;
    private ProvinceRepository provinces;
    private AnalysisService service;

    @BeforeEach
    void setUp() {
        extractor = mock(IncidentExtractor.class);
        incidents = mock(IncidentRepository.class);
        provinces = mock(ProvinceRepository.class);
        service = new AnalysisService(extractor, incidents, provinces);
    }

    /**
     * The submission date is what relative and defaulted dates resolve against. Passing the current
     * date instead would quietly move every reprocessed report to today (ADR-014).
     */
    @Test
    void readsTheTextAgainstTheReportsOwnSubmissionDate() {
        when(extractor.extract(anyString(), any())).thenReturn(new ExtractionResult(List.of(), List.of()));

        service.analyze(REPORT_ID, "Ankara'da 15 vaka", SUBMITTED_AT);

        verify(extractor).extract("Ankara'da 15 vaka", REFERENCE_DATE);
    }

    @Test
    void storesWhatWasExtractedAndLinksItBackToTheReport() {
        Province ankara = province((short) 6, "Ankara");
        when(provinces.findById((short) 6)).thenReturn(Optional.of(ankara));
        when(extractor.extract(anyString(), any())).thenReturn(new ExtractionResult(
                List.of(new ExtractedIncident(REFERENCE_DATE, DateSource.EXPLICIT, ProvinceScope.SINGLE,
                        (short) 6, null, "EPIDEMIC", ClassificationStatus.CLASSIFIED,
                        Map.of("NEW_CASE", 15, "DEATH", 1),
                        List.of(new ExtractedKeyword("vaka", KeywordRole.METRIC, 30, 34)))),
                List.of()));

        AnalysisOutcome outcome = service.analyze(REPORT_ID, "metin", SUBMITTED_AT);

        assertThat(outcome.incidentCount()).isEqualTo(1);
        Incident stored = captureSaved().get(0);
        assertThat(stored.getRawReportId()).isEqualTo(REPORT_ID);
        assertThat(stored.getProvince()).isEqualTo(ankara);
        assertThat(stored.getProvinceScope()).isEqualTo(ProvinceScope.SINGLE);
        assertThat(stored.getMetrics()).extracting(IncidentMetric::getMetricType, IncidentMetric::getValue)
                .containsExactlyInAnyOrder(org.assertj.core.groups.Tuple.tuple("NEW_CASE", 15),
                        org.assertj.core.groups.Tuple.tuple("DEATH", 1));
        assertThat(stored.getKeywords()).singleElement()
                .satisfies(keyword -> assertThat(keyword.getKeyword()).isEqualTo("vaka"));
    }

    /** A figure the text gives for several provinces at once keeps all of them (ADR-019). */
    @Test
    void aSharedFigureKeepsEveryProvinceItCovers() {
        Province bursa = province((short) 16, "Bursa");
        Province kocaeli = province((short) 41, "Kocaeli");
        when(provinces.findAllById(any())).thenReturn(List.of(bursa, kocaeli));
        when(extractor.extract(anyString(), any())).thenReturn(new ExtractionResult(
                List.of(new ExtractedIncident(REFERENCE_DATE, DateSource.RELATIVE, ProvinceScope.SHARED,
                        null, Set.of((short) 16, (short) 41), "TRAFFIC_ACCIDENT",
                        ClassificationStatus.CLASSIFIED, Map.of("INJURED", 10), List.of())),
                List.of()));

        service.analyze(REPORT_ID, "metin", SUBMITTED_AT);

        Incident stored = captureSaved().get(0);
        assertThat(stored.getProvinceScope()).isEqualTo(ProvinceScope.SHARED);
        assertThat(stored.getProvince()).isNull();
        assertThat(stored.getSharedProvinces()).containsExactlyInAnyOrder(bursa, kocaeli);
    }

    @Test
    void aReportWithNoProvinceIsStillStored() {
        when(extractor.extract(anyString(), any())).thenReturn(new ExtractionResult(
                List.of(unclassified()), List.of("nothing matched")));

        AnalysisOutcome outcome = service.analyze(REPORT_ID, "metin", SUBMITTED_AT);

        assertThat(outcome.incidentCount()).isEqualTo(1);
        assertThat(outcome.warnings()).containsExactly("nothing matched");
        Incident stored = captureSaved().get(0);
        assertThat(stored.getProvinceScope()).isEqualTo(ProvinceScope.UNKNOWN);
        assertThat(stored.getClassification()).isEqualTo(ClassificationStatus.UNCLASSIFIED);
        verify(provinces, never()).findById(any());
    }

    /**
     * Reprocessing rebuilds rather than patches, so whatever was derived before has to go first.
     * Without this a second run would double every row.
     */
    @Test
    void clearsWhatWasDerivedBeforeWritingTheNewResult() {
        when(incidents.deleteByRawReportId(REPORT_ID)).thenReturn(3L);
        when(extractor.extract(anyString(), any()))
                .thenReturn(new ExtractionResult(List.of(unclassified()), List.of()));

        service.analyze(REPORT_ID, "metin", SUBMITTED_AT);

        verify(incidents).deleteByRawReportId(REPORT_ID);
        assertThat(captureSaved()).hasSize(1);
    }

    /** Finding nothing is a legitimate answer, not a failure. */
    @Test
    void anEmptyResultStoresNothingAndSaysSo() {
        when(extractor.extract(anyString(), any()))
                .thenReturn(new ExtractionResult(List.of(), List.of("nothing recognised")));

        AnalysisOutcome outcome = service.analyze(REPORT_ID, "metin", SUBMITTED_AT);

        assertThat(outcome.incidentCount()).isZero();
        assertThat(outcome.warnings()).containsExactly("nothing recognised");
        assertThat(captureSaved()).isEmpty();
    }

    /**
     * A province code that is not in the reference data means the extractor and the database
     * disagree. Storing the record without its province would silently turn a located incident into
     * an unlocated one, so it fails instead.
     */
    @Test
    void refusesAProvinceCodeThatDoesNotExist() {
        when(provinces.findById((short) 99)).thenReturn(Optional.empty());
        when(extractor.extract(anyString(), any())).thenReturn(new ExtractionResult(
                List.of(new ExtractedIncident(REFERENCE_DATE, DateSource.EXPLICIT, ProvinceScope.SINGLE,
                        (short) 99, null, "EPIDEMIC", ClassificationStatus.CLASSIFIED, Map.of(), List.of())),
                List.of()));

        assertThatThrownBy(() -> service.analyze(REPORT_ID, "metin", SUBMITTED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown province code 99");
    }

    @Test
    void refusesASharedFigureNamingAProvinceThatDoesNotExist() {
        // Built before stubbing: creating a mock inside when(...) leaves Mockito mid-stubbing.
        Province bursa = province((short) 16, "Bursa");
        when(provinces.findAllById(any())).thenReturn(List.of(bursa));
        when(extractor.extract(anyString(), any())).thenReturn(new ExtractionResult(
                List.of(new ExtractedIncident(REFERENCE_DATE, DateSource.RELATIVE, ProvinceScope.SHARED,
                        null, Set.of((short) 16, (short) 99), "TRAFFIC_ACCIDENT",
                        ClassificationStatus.CLASSIFIED, Map.of(), List.of())),
                List.of()));

        assertThatThrownBy(() -> service.analyze(REPORT_ID, "metin", SUBMITTED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown province code 99");
    }

    /** A SINGLE record without a province contradicts itself; it must not reach the database. */
    @Test
    void refusesASingleIncidentWithNoProvince() {
        when(extractor.extract(anyString(), any())).thenReturn(new ExtractionResult(
                List.of(new ExtractedIncident(REFERENCE_DATE, DateSource.EXPLICIT, ProvinceScope.SINGLE,
                        null, null, "EPIDEMIC", ClassificationStatus.CLASSIFIED, Map.of(), List.of())),
                List.of()));

        assertThatThrownBy(() -> service.analyze(REPORT_ID, "metin", SUBMITTED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must name a province");
    }

    @SuppressWarnings("unchecked")
    private List<Incident> captureSaved() {
        ArgumentCaptor<List<Incident>> captor = ArgumentCaptor.forClass(List.class);
        verify(incidents).saveAll(captor.capture());
        return captor.getValue();
    }

    private static ExtractedIncident unclassified() {
        return new ExtractedIncident(REFERENCE_DATE, DateSource.DEFAULTED, ProvinceScope.UNKNOWN,
                null, null, "OTHER", ClassificationStatus.UNCLASSIFIED, Map.of(), List.of());
    }

    /** Provinces come from a Flyway migration, so there is no public constructor to call. */
    private static Province province(short code, String name) {
        Province province = mock(Province.class);
        when(province.getCode()).thenReturn(code);
        when(province.getName()).thenReturn(name);
        return province;
    }
}
