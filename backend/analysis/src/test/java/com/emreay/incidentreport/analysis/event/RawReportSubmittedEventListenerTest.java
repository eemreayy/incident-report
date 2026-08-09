package com.emreay.incidentreport.analysis.event;

import com.emreay.incidentreport.analysis.service.AnalysisOutcome;
import com.emreay.incidentreport.analysis.service.AnalysisService;
import com.emreay.incidentreport.shared.event.RawReportSubmittedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * This listener is where the rule "raw text is stored even when analysis fails" is actually kept.
 *
 * <p>It used to be kept on the ingestion side, which caught the exception and marked its own
 * document {@code FAILED}. That put the outcome in the wrong module (ADR-021), so the guarantee
 * moved here — and these tests moved with it.
 */
class RawReportSubmittedEventListenerTest {

    private static final RawReportSubmittedEvent EVENT = new RawReportSubmittedEvent(
            "652f1a2b3c4d5e6f70819200", "Ankara'da 15 vaka", Instant.parse("2020-04-20T21:30:00Z"));

    private AnalysisService analysisService;
    private RawReportSubmittedEventListener listener;

    @BeforeEach
    void setUp() {
        analysisService = mock(AnalysisService.class);
        listener = new RawReportSubmittedEventListener(analysisService);
    }

    @Test
    void handsTheTextAndItsReferenceDateToTheAnalyser() {
        when(analysisService.analyze(anyString(), anyString(), any()))
                .thenReturn(new AnalysisOutcome(1, List.of()));

        listener.onRawReportSubmitted(EVENT);

        verify(analysisService).analyze(EVENT.rawReportId(), EVENT.rawText(), EVENT.submittedAt());
        verify(analysisService, never()).recordFailure(anyString(), anyString());
    }

    /**
     * The guarantee the whole ordering exists for. Analysis is invoked inside the submitter's
     * request, so letting an exception escape would reject a submission that had already succeeded
     * — the text was accepted and stored before this ran. Losing an incident because the parser
     * could not cope with it is the one outcome the design refuses.
     */
    @Test
    void aFailingAnalysisNeverReachesTheSubmitter() {
        when(analysisService.analyze(anyString(), anyString(), any()))
                .thenThrow(new IllegalStateException("province extractor exploded"));

        assertThatCode(() -> listener.onRawReportSubmitted(EVENT)).doesNotThrowAnyException();
    }

    /**
     * Swallowing the failure would be worse than throwing it: the report would look untouched and
     * nothing would ever tell anyone to look at it again. Writing it down is what makes
     * reprocessing possible (FR-15).
     */
    @Test
    void theFailureIsWrittenDownWithItsTypeAndMessage() {
        when(analysisService.analyze(anyString(), anyString(), any()))
                .thenThrow(new IllegalStateException("province extractor exploded"));

        listener.onRawReportSubmitted(EVENT);

        ArgumentCaptor<String> reason = ArgumentCaptor.forClass(String.class);
        verify(analysisService).recordFailure(org.mockito.ArgumentMatchers.eq(EVENT.rawReportId()),
                reason.capture());
        assertThat(reason.getValue())
                .contains("IllegalStateException")
                .contains("province extractor exploded");
    }

    @Test
    void aFailureWithNoMessageStillIdentifiesItself() {
        when(analysisService.analyze(anyString(), anyString(), any()))
                .thenThrow(new IllegalStateException());

        listener.onRawReportSubmitted(EVENT);

        ArgumentCaptor<String> reason = ArgumentCaptor.forClass(String.class);
        verify(analysisService).recordFailure(anyString(), reason.capture());
        assertThat(reason.getValue()).isEqualTo(IllegalStateException.class.getName());
    }

    /** The column holds 1024 characters; a longer message must not turn into a write failure. */
    @Test
    void anEnormousMessageIsCutToFit() {
        when(analysisService.analyze(anyString(), anyString(), any()))
                .thenThrow(new IllegalStateException("x".repeat(5000)));

        listener.onRawReportSubmitted(EVENT);

        ArgumentCaptor<String> reason = ArgumentCaptor.forClass(String.class);
        verify(analysisService).recordFailure(anyString(), reason.capture());
        assertThat(reason.getValue()).hasSize(1024);
    }

    /**
     * If PostgreSQL is the thing that is broken, writing down the failure fails too. That must not
     * escape either — the text is safe in the other store, and turning a database outage into
     * rejected submissions would lose incidents for as long as the outage lasts.
     */
    @Test
    void failingToRecordTheFailureIsAlsoContained() {
        when(analysisService.analyze(anyString(), anyString(), any()))
                .thenThrow(new IllegalStateException("extractor exploded"));
        doThrow(new IllegalStateException("database is down"))
                .when(analysisService).recordFailure(anyString(), anyString());

        assertThatCode(() -> listener.onRawReportSubmitted(EVENT)).doesNotThrowAnyException();
    }
}
