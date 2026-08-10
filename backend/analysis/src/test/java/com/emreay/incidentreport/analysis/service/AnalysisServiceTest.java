package com.emreay.incidentreport.analysis.service;

import com.emreay.incidentreport.analysis.domain.ClassificationStatus;
import com.emreay.incidentreport.analysis.domain.DateSource;
import com.emreay.incidentreport.analysis.domain.Incident;
import com.emreay.incidentreport.analysis.domain.IncidentMetric;
import com.emreay.incidentreport.analysis.domain.KeywordRole;
import com.emreay.incidentreport.analysis.domain.Province;
import com.emreay.incidentreport.analysis.domain.ProvinceScope;

import static com.emreay.incidentreport.analysis.domain.ProvinceFixture.province;
import com.emreay.incidentreport.analysis.extraction.ExtractedIncident;
import com.emreay.incidentreport.analysis.extraction.ExtractedKeyword;
import com.emreay.incidentreport.analysis.extraction.ExtractionResult;
import com.emreay.incidentreport.analysis.extraction.IncidentExtractor;
import com.emreay.incidentreport.analysis.domain.AnalysisResult;
import com.emreay.incidentreport.analysis.domain.AnalysisStatus;
import com.emreay.incidentreport.analysis.repository.AnalysisResultRepository;
import com.emreay.incidentreport.analysis.repository.IncidentRepository;
import com.emreay.incidentreport.analysis.repository.ProvinceRepository;
import com.emreay.incidentreport.shared.event.IncidentRecordsProducedEvent;
import com.emreay.incidentreport.shared.event.IncidentSignal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.emreay.incidentreport.analysis.text.NormalizedText;
import com.emreay.incidentreport.analysis.text.SentenceSplitter;
import com.emreay.incidentreport.analysis.text.TurkishTextNormalizer;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    private static final Instant SUBMITTED_AT = Instant.parse("2020-04-20T09:30:00Z");
    private static final LocalDate REFERENCE_DATE = LocalDate.of(2020, 4, 20);

    private static final ZoneId REPORTING_ZONE = ZoneId.of("Europe/Istanbul");
    private static final Instant ANALYSED_AT = Instant.parse("2026-08-09T10:00:00Z");

    private IncidentExtractor extractor;
    private IncidentRepository incidents;
    private ProvinceRepository provinces;
    private AnalysisResultRepository results;
    private ApplicationEventPublisher events;
    private AnalysisService service;

    @BeforeEach
    void setUp() {
        extractor = mock(IncidentExtractor.class);
        incidents = mock(IncidentRepository.class);
        provinces = mock(ProvinceRepository.class);
        results = mock(AnalysisResultRepository.class);
        events = mock(ApplicationEventPublisher.class);
        when(results.findByRawReportId(any())).thenReturn(Optional.empty());
        when(incidents.saveAll(any())).thenAnswer(AnalysisServiceTest::assignIds);
        service = new AnalysisService(extractor, incidents, provinces, results, events,
                Clock.fixed(ANALYSED_AT, ZoneOffset.UTC),
                new TurkishTextNormalizer(new SentenceSplitter()),
                REPORTING_ZONE);
    }

    /**
     * Stands in for the one thing saving actually adds: an identity. A stub that handed back
     * id-less records would be modelling a repository that does not work, and the announcement
     * below has nothing to name without them.
     */
    private static List<Incident> assignIds(InvocationOnMock invocation) {
        List<Incident> saved = invocation.getArgument(0);
        long id = 1;
        for (Incident incident : saved) {
            ReflectionTestUtils.setField(incident, "id", id++);
        }
        return saved;
    }

    /**
     * The submission date is what relative and defaulted dates resolve against. Passing the current
     * date instead would quietly move every reprocessed report to today (ADR-014).
     */
    @Test
    void readsTheTextAgainstTheReportsOwnSubmissionDate() {
        when(extractor.extract(any(), any())).thenReturn(new ExtractionResult(List.of(), List.of()));

        service.analyze(REPORT_ID, "Ankara'da 15 vaka", SUBMITTED_AT);

        ArgumentCaptor<NormalizedText> text = ArgumentCaptor.forClass(NormalizedText.class);
        verify(extractor).extract(text.capture(), eq(REFERENCE_DATE));
        assertThat(text.getValue().original()).isEqualTo("Ankara'da 15 vaka");
    }

    /**
     * Which calendar day a submission belongs to is a local question (ADR-029). This report was
     * filed at 09:30 UTC, so the two zones agree; the one below is the case where they do not.
     */
    @Test
    void readsTheTextAgainstTheSubmissionDayInTheReportingZone() {
        when(extractor.extract(any(), any())).thenReturn(new ExtractionResult(List.of(), List.of()));

        // 21:30 UTC on the 20th is 00:30 on the 21st in Istanbul. UTC would file this report a day
        // early, on a day the user in Turkey had already finished.
        service.analyze(REPORT_ID, "Ankara'da 15 vaka", Instant.parse("2020-04-20T21:30:00Z"));

        verify(extractor).extract(any(), eq(LocalDate.of(2020, 4, 21)));
    }

    @Test
    void handsTheExtractorTextItCanMatchAgainstWithoutLosingTheOriginal() {
        when(extractor.extract(any(), any())).thenReturn(new ExtractionResult(List.of(), List.of()));

        service.analyze(REPORT_ID, "İZMİR'de   sel\nvar", SUBMITTED_AT);

        ArgumentCaptor<NormalizedText> text = ArgumentCaptor.forClass(NormalizedText.class);
        verify(extractor).extract(text.capture(), any());
        assertThat(text.getValue().value()).isEqualTo("izmir'de sel var");
        assertThat(text.getValue().original()).isEqualTo("İZMİR'de   sel\nvar");
    }

    @Test
    void storesWhatWasExtractedAndLinksItBackToTheReport() {
        Province ankara = province(6, "Ankara");
        when(provinces.findById((short) 6)).thenReturn(Optional.of(ankara));
        when(extractor.extract(any(), any())).thenReturn(new ExtractionResult(
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
        Province bursa = province(16, "Bursa");
        Province kocaeli = province(41, "Kocaeli");
        when(provinces.findAllById(any())).thenReturn(List.of(bursa, kocaeli));
        when(extractor.extract(any(), any())).thenReturn(new ExtractionResult(
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
        when(extractor.extract(any(), any())).thenReturn(new ExtractionResult(
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
        when(extractor.extract(any(), any()))
                .thenReturn(new ExtractionResult(List.of(unclassified()), List.of()));

        service.analyze(REPORT_ID, "metin", SUBMITTED_AT);

        verify(incidents).deleteByRawReportId(REPORT_ID);
        assertThat(captureSaved()).hasSize(1);
    }

    /** Finding nothing is a legitimate answer, not a failure. */
    @Test
    void anEmptyResultStoresNothingAndSaysSo() {
        when(extractor.extract(any(), any()))
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
        when(extractor.extract(any(), any())).thenReturn(new ExtractionResult(
                List.of(new ExtractedIncident(REFERENCE_DATE, DateSource.EXPLICIT, ProvinceScope.SINGLE,
                        (short) 99, null, "EPIDEMIC", ClassificationStatus.CLASSIFIED, Map.of(), List.of())),
                List.of()));

        assertThatThrownBy(() -> service.analyze(REPORT_ID, "metin", SUBMITTED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown province code 99");
    }

    @Test
    void refusesASharedFigureNamingAProvinceThatDoesNotExist() {
        Province bursa = province(16, "Bursa");
        when(provinces.findAllById(any())).thenReturn(List.of(bursa));
        when(extractor.extract(any(), any())).thenReturn(new ExtractionResult(
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
        when(extractor.extract(any(), any())).thenReturn(new ExtractionResult(
                List.of(new ExtractedIncident(REFERENCE_DATE, DateSource.EXPLICIT, ProvinceScope.SINGLE,
                        null, null, "EPIDEMIC", ClassificationStatus.CLASSIFIED, Map.of(), List.of())),
                List.of()));

        assertThatThrownBy(() -> service.analyze(REPORT_ID, "metin", SUBMITTED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must name a province");
    }

    /**
     * The outcome is this module's answer about its own work, so it is written here rather than
     * sent back to the module that stored the text (ADR-021).
     */
    @Test
    void writesDownHowTheRunWent() {
        when(extractor.extract(any(), any())).thenReturn(new ExtractionResult(
                List.of(unclassified()), List.of("nothing matched", "date was assumed")));

        service.analyze(REPORT_ID, "metin", SUBMITTED_AT);

        ArgumentCaptor<AnalysisResult> recorded = ArgumentCaptor.forClass(AnalysisResult.class);
        verify(results).save(recorded.capture());
        assertThat(recorded.getValue().getRawReportId()).isEqualTo(REPORT_ID);
        assertThat(recorded.getValue().getStatus()).isEqualTo(AnalysisStatus.ANALYZED);
        assertThat(recorded.getValue().getAnalyzedAt()).isEqualTo(ANALYSED_AT);
        assertThat(recorded.getValue().getIncidentCount()).isEqualTo(1);
        assertThat(recorded.getValue().getWarnings())
                .containsExactly("nothing matched", "date was assumed");
    }

    @Test
    void recordsAFailureAsThisModulesOwnAnswer() {
        service.recordFailure(REPORT_ID, "java.lang.IllegalStateException: boom");

        ArgumentCaptor<AnalysisResult> recorded = ArgumentCaptor.forClass(AnalysisResult.class);
        verify(results).save(recorded.capture());
        assertThat(recorded.getValue().getStatus()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(recorded.getValue().getFailureReason()).contains("IllegalStateException");
        assertThat(recorded.getValue().getAnalyzedAt()).isEqualTo(ANALYSED_AT);
    }

    /**
     * Reprocessing answers the same question again. Inserting a second row would leave two current
     * answers for one report and force every reader to work out which is real.
     */
    @Test
    void reprocessingOverwritesTheExistingAnswerInsteadOfAddingOne() {
        AnalysisResult existing = AnalysisResult.failed(REPORT_ID, SUBMITTED_AT, "earlier failure");
        when(results.findByRawReportId(REPORT_ID)).thenReturn(Optional.of(existing));
        when(extractor.extract(any(), any()))
                .thenReturn(new ExtractionResult(List.of(unclassified()), List.of()));

        service.analyze(REPORT_ID, "metin", SUBMITTED_AT);

        verify(results, never()).save(any());
        assertThat(existing.getStatus()).isEqualTo(AnalysisStatus.ANALYZED);
        assertThat(existing.getFailureReason()).isNull();
        assertThat(existing.getIncidentCount()).isEqualTo(1);
    }

    /**
     * The stream is told what now stands, in enough detail to judge relevance and no more (C-8).
     * The province codes are the interesting part: a filtered view has to be able to tell whether
     * the new record would show up in it.
     */
    @Test
    @DisplayName("what was stored is announced, by identity and dimension only")
    void announcesTheRecordsItProduced() {
        Province bursa = province(16, "Bursa");
        Province kocaeli = province(41, "Kocaeli");
        when(provinces.findById((short) 16)).thenReturn(Optional.of(bursa));
        when(provinces.findAllById(any())).thenReturn(List.of(bursa, kocaeli));
        when(extractor.extract(any(), any())).thenReturn(new ExtractionResult(
                List.of(new ExtractedIncident(REFERENCE_DATE, DateSource.EXPLICIT, ProvinceScope.SINGLE,
                                (short) 16, null, "TRAFFIC_ACCIDENT", ClassificationStatus.CLASSIFIED,
                                Map.of("INJURED", 8), List.of()),
                        new ExtractedIncident(REFERENCE_DATE, DateSource.EXPLICIT, ProvinceScope.SHARED,
                                null, Set.of((short) 16, (short) 41), "TRAFFIC_ACCIDENT",
                                ClassificationStatus.CLASSIFIED, Map.of("INJURED", 10), List.of()),
                        unclassified()),
                List.of()));

        service.analyze(REPORT_ID, "metin", SUBMITTED_AT);

        IncidentRecordsProducedEvent announced = captureAnnouncement();
        assertThat(announced.rawReportId()).isEqualTo(REPORT_ID);
        assertThat(announced.analyzedAt()).isEqualTo(ANALYSED_AT);
        assertThat(announced.incidents())
                .extracting(IncidentSignal::incidentId, IncidentSignal::occurredOn,
                        IncidentSignal::eventType, IncidentSignal::provinceCodes)
                .containsExactly(
                        tuple(1L, REFERENCE_DATE, "TRAFFIC_ACCIDENT", Set.of((short) 16)),
                        tuple(2L, REFERENCE_DATE, "TRAFFIC_ACCIDENT", Set.of((short) 16, (short) 41)),
                        tuple(3L, REFERENCE_DATE, "OTHER", Set.of()));
    }

    /**
     * A reprocess that now finds nothing still changed what every query answers. A client showing
     * the rows it deleted has no other way to learn they are gone.
     */
    @Test
    @DisplayName("a run that produced nothing is still announced")
    void announcesEvenWhenNothingWasExtracted() {
        when(incidents.deleteByRawReportId(REPORT_ID)).thenReturn(3L);
        when(extractor.extract(any(), any()))
                .thenReturn(new ExtractionResult(List.of(), List.of("nothing recognised")));

        service.analyze(REPORT_ID, "metin", SUBMITTED_AT);

        assertThat(captureAnnouncement().incidents()).isEmpty();
    }

    /**
     * Nothing was written, so there is nothing to look at again. The submitter learns what happened
     * from the analysis outcome, which is a query, not a broadcast (ADR-021).
     */
    @Test
    @DisplayName("a run that failed announces nothing")
    void announcesNothingWhenTheRunFailed() {
        when(provinces.findById((short) 99)).thenReturn(Optional.empty());
        when(extractor.extract(any(), any())).thenReturn(new ExtractionResult(
                List.of(new ExtractedIncident(REFERENCE_DATE, DateSource.EXPLICIT, ProvinceScope.SINGLE,
                        (short) 99, null, "EPIDEMIC", ClassificationStatus.CLASSIFIED, Map.of(), List.of())),
                List.of()));

        assertThatThrownBy(() -> service.analyze(REPORT_ID, "metin", SUBMITTED_AT))
                .isInstanceOf(IllegalArgumentException.class);

        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("recording a failure announces nothing either")
    void recordingAFailureIsNotAnnounced() {
        service.recordFailure(REPORT_ID, "java.lang.IllegalStateException: boom");

        verify(events, never()).publishEvent(any(Object.class));
    }

    private IncidentRecordsProducedEvent captureAnnouncement() {
        ArgumentCaptor<IncidentRecordsProducedEvent> announced =
                ArgumentCaptor.forClass(IncidentRecordsProducedEvent.class);
        verify(events).publishEvent(announced.capture());
        return announced.getValue();
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

}
