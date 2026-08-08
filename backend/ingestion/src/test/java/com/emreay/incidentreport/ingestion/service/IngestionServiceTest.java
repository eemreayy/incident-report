package com.emreay.incidentreport.ingestion.service;

import com.emreay.incidentreport.ingestion.domain.ProcessingStatus;
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
        when(repository.save(any())).thenAnswer(invocation -> withId(invocation.getArgument(0)));

        RawIncidentReport submitted = service.submit(TEXT);

        ArgumentCaptor<RawIncidentReport> saved = ArgumentCaptor.forClass(RawIncidentReport.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().rawText()).isEqualTo(TEXT);
        assertThat(saved.getValue().submittedAt()).isEqualTo(NOW);
        assertThat(saved.getValue().status()).isEqualTo(ProcessingStatus.RECEIVED);
        assertThat(submitted.id()).isEqualTo(STORED_ID);
    }

    /**
     * The event is the only way the text reaches the analysing module, which cannot read MongoDB
     * itself. If it went out with an id alone, the listener would have nothing to work on.
     */
    @Test
    void announcesTheReportWithEverythingTheAnalyserNeeds() {
        when(repository.save(any())).thenAnswer(invocation -> withId(invocation.getArgument(0)));

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
     * The guarantee this whole ordering exists for: analysis runs inside the submission, so a
     * listener that throws must not take the text down with it. Losing an incident because the
     * parser could not cope with it is the one outcome the design refuses.
     */
    @Test
    void keepsTheTextWhenAnalysisBlowsUp() {
        when(repository.save(any())).thenAnswer(invocation -> withId(invocation.getArgument(0)));
        doThrow(new IllegalStateException("province extractor exploded"))
                .when(events).publishEvent(any(RawReportSubmittedEvent.class));

        RawIncidentReport result = service.submit(TEXT);

        assertThat(result.status()).isEqualTo(ProcessingStatus.FAILED);
        assertThat(result.rawText()).as("the text survives the failure").isEqualTo(TEXT);
        assertThat(result.failureReason())
                .contains("IllegalStateException")
                .contains("province extractor exploded");
        assertThat(result.analyzedAt()).isEqualTo(NOW);
    }

    @Test
    void aFailureIsNotReportedBackToTheCaller() {
        when(repository.save(any())).thenAnswer(invocation -> withId(invocation.getArgument(0)));
        doThrow(new IllegalStateException("boom")).when(events).publishEvent(any(RawReportSubmittedEvent.class));

        assertThat(service.submit(TEXT)).isNotNull();
    }

    /** A failure with no message must still produce something identifiable, not "null". */
    @Test
    void recordsTheFailureTypeEvenWithoutAMessage() {
        when(repository.save(any())).thenAnswer(invocation -> withId(invocation.getArgument(0)));
        doThrow(new IllegalStateException()).when(events).publishEvent(any(RawReportSubmittedEvent.class));

        assertThat(service.submit(TEXT).failureReason())
                .isEqualTo(IllegalStateException.class.getName());
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
        when(repository.save(any())).thenAnswer(invocation -> withId(invocation.getArgument(0)));

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
        RawIncidentReport stored = withId(RawIncidentReport.received(TEXT, NOW));
        when(repository.findById(STORED_ID)).thenReturn(Optional.of(stored));

        assertThat(service.findById(STORED_ID)).contains(stored);
        assertThat(service.findById("does-not-exist")).isEmpty();
    }

    @Test
    void listsReportsPage() {
        Page<RawIncidentReport> page = new PageImpl<>(List.of(withId(RawIncidentReport.received(TEXT, NOW))));
        when(repository.findAll(any(PageRequest.class))).thenReturn(page);

        assertThat(service.findAll(PageRequest.of(0, 20))).isSameAs(page);
    }

    /** Mimics MongoDB assigning an id on insert. */
    private static RawIncidentReport withId(RawIncidentReport report) {
        return report.id() != null ? report : new RawIncidentReport(STORED_ID, report.rawText(),
                report.submittedAt(), report.status(), report.analyzedAt(), report.failureReason(),
                report.warnings());
    }
}
