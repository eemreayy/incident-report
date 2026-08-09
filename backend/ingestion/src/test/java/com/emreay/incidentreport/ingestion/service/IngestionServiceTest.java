package com.emreay.incidentreport.ingestion.service;

import com.emreay.incidentreport.ingestion.domain.RawIncidentReport;
import com.emreay.incidentreport.ingestion.repository.RawIncidentReportRepository;
import com.emreay.incidentreport.shared.error.DomainValidationException;
import com.emreay.incidentreport.shared.event.RawReportSubmittedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Plain JUnit and Mockito, no Spring context: everything worth checking here is the service's own
 * decision-making, and a context would only slow the feedback down.
 */
class IngestionServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-09T09:30:00Z");
    private static final String TEXT = "20.04.2020 tarihinde Ankara'da 15 yeni vaka tespit edildi.";
    private static final String STORED_ID = "652f1a2b3c4d5e6f70819200";

    private RawIncidentReportRepository repository;
    private ApplicationEventPublisher events;
    private IngestionService service;

    @BeforeEach
    void setUp() {
        repository = mock(RawIncidentReportRepository.class);
        events = mock(ApplicationEventPublisher.class);
        service = new IngestionService(repository, events, new IngestionProperties(10_000),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void storesTheTextUntouchedAndStampsItWithTheSubmissionTime() {
        savesAssignIds();

        RawIncidentReport submitted = service.submit(TEXT);

        ArgumentCaptor<RawIncidentReport> saved = ArgumentCaptor.forClass(RawIncidentReport.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().rawText()).isEqualTo(TEXT);
        assertThat(saved.getValue().submittedAt()).isEqualTo(NOW);
        assertThat(submitted.id()).isEqualTo(STORED_ID);
    }

    /**
     * The guarantee the whole design rests on: a report is written once and never again — not even
     * to record how analysis went, which belongs to the module that does the analysing (ADR-021).
     * Asserting the count is the closest a unit test gets to "write-once".
     */
    @Test
    void writesTheReportExactlyOnce() {
        savesAssignIds();

        service.submit(TEXT);

        verify(repository, times(1)).save(any());
    }

    /**
     * The event is the only way the text reaches the analysing module, which cannot read MongoDB
     * itself. If it went out with an id alone, the listener would have nothing to work on.
     */
    @Test
    void announcesTheReportWithEverythingTheAnalyserNeeds() {
        savesAssignIds();

        service.submit(TEXT);

        ArgumentCaptor<RawReportSubmittedEvent> event = ArgumentCaptor.forClass(RawReportSubmittedEvent.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue().rawReportId()).isEqualTo(STORED_ID);
        assertThat(event.getValue().rawText()).isEqualTo(TEXT);
        assertThat(event.getValue().submittedAt())
                .as("the reference date for relative and defaulted dates travels with the text")
                .isEqualTo(NOW);
    }

    /**
     * Nothing about how the text was read comes back from here. The submission answers for the
     * submission; what the analysis found is a separate question with a separate answer (ADR-021).
     */
    @Test
    void answersWithTheStoredReportAndNothingAboutAnalysis() {
        savesAssignIds();

        RawIncidentReport submitted = service.submit(TEXT);

        assertThat(submitted.rawText()).isEqualTo(TEXT);
        assertThat(submitted.submittedAt()).isEqualTo(NOW);
        assertThat(RawIncidentReport.class.getRecordComponents())
                .as("the document carries only what this module owns")
                .extracting(java.lang.reflect.RecordComponent::getName)
                .containsExactlyInAnyOrder("id", "rawText", "submittedAt");
    }

    /**
     * The submission is finished once the text is stored. Reading it back afterwards would mean
     * expecting somebody to have changed it — which is exactly what no longer happens.
     */
    @Test
    void doesNotReadTheReportBackAfterAnnouncingIt() {
        savesAssignIds();

        service.submit(TEXT);

        verify(repository, never()).findById(any());
    }

    /**
     * Recovering from a failed analysis is the analysing module's job, so an exception from a
     * listener is not caught here any more — there is nothing this service could honestly do with
     * it. What still must hold is that the text was stored before anyone looked at it.
     */
    @Test
    void theTextIsStoredBeforeAnalysisIsEvenTold() {
        savesAssignIds();
        doThrow(new IllegalStateException("province extractor exploded"))
                .when(events).publishEvent(any(RawReportSubmittedEvent.class));

        assertThatThrownBy(() -> service.submit(TEXT)).isInstanceOf(IllegalStateException.class);

        verify(repository, times(1)).save(any());
    }

    @ParameterizedTest(name = "[{index}] blank text is rejected")
    @NullSource
    @ValueSource(strings = {"", "   ", "\n\t  \n"})
    void rejectsTextWithNothingInIt(String rawText) {
        assertThatThrownBy(() -> service.submit(rawText))
                .isInstanceOf(DomainValidationException.class)
                .extracting("code").isEqualTo("report.text.blank");

        verifyNoInteractions(repository, events);
    }

    @Test
    void rejectsTextLongerThanTheConfiguredLimit() {
        service = new IngestionService(repository, events, new IngestionProperties(20),
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> service.submit("x".repeat(21)))
                .isInstanceOf(DomainValidationException.class)
                .extracting("code").isEqualTo("report.text.too-long");

        verifyNoInteractions(repository, events);
    }

    @Test
    void textExactlyAtTheLimitIsAccepted() {
        service = new IngestionService(repository, events, new IngestionProperties(20),
                Clock.fixed(NOW, ZoneOffset.UTC));
        savesAssignIds();

        assertThat(service.submit("x".repeat(20))).isNotNull();
    }

    /** Nothing is announced for a report that was never stored. */
    @Test
    void rejectedSubmissionsAreNeverAnnounced() {
        assertThatThrownBy(() -> service.submit("  ")).isInstanceOf(DomainValidationException.class);

        verify(events, never()).publishEvent(any(RawReportSubmittedEvent.class));
    }

    @Test
    void readsASingleReportById() {
        RawIncidentReport stored = withId(RawIncidentReport.of(TEXT, NOW));
        when(repository.findById(STORED_ID)).thenReturn(Optional.of(stored));

        assertThat(service.findById(STORED_ID)).contains(stored);
        assertThat(service.findById("does-not-exist")).isEmpty();
    }

    @Test
    void listsReportsPage() {
        Page<RawIncidentReport> page = new PageImpl<>(List.of(withId(RawIncidentReport.of(TEXT, NOW))));
        when(repository.findAll(any(PageRequest.class))).thenReturn(page);

        assertThat(service.findAll(PageRequest.of(0, 20))).isSameAs(page);
    }

    private void savesAssignIds() {
        when(repository.save(any())).thenAnswer(invocation -> withId(invocation.getArgument(0)));
    }

    /** Mimics MongoDB assigning an id on insert. */
    private static RawIncidentReport withId(RawIncidentReport report) {
        return report.id() != null ? report
                : new RawIncidentReport(STORED_ID, report.rawText(), report.submittedAt());
    }
}
