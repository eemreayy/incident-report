package com.emreay.incidentreport.realtime.event;

import com.emreay.incidentreport.realtime.service.IncidentStream;
import com.emreay.incidentreport.realtime.web.IncidentSignalMessage;
import com.emreay.incidentreport.shared.event.IncidentRecordsProducedEvent;
import com.emreay.incidentreport.shared.event.IncidentSignal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.lang.reflect.Method;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;

/** What the module does with an event, and what it refuses to do to the request behind it. */
@ExtendWith(MockitoExtension.class)
class IncidentRecordsProducedEventListenerTest {

    private static final IncidentRecordsProducedEvent EVENT = new IncidentRecordsProducedEvent(
            "652f1a2b3c4d5e6f70819200",
            Instant.parse("2026-08-10T09:30:00Z"),
            List.of(new IncidentSignal(7L, LocalDate.of(2020, 4, 20), "TRAFFIC_ACCIDENT",
                    Set.of((short) 16, (short) 41))));

    @Mock
    private IncidentStream stream;

    @InjectMocks
    private IncidentRecordsProducedEventListener listener;

    @Test
    @DisplayName("the event becomes one message, with the province codes sorted")
    void theEventIsPublishedToTheStream() {
        listener.onIncidentRecordsProduced(EVENT);

        ArgumentCaptor<IncidentSignalMessage> published = ArgumentCaptor.forClass(IncidentSignalMessage.class);
        verify(stream).broadcast(published.capture());

        IncidentSignalMessage message = published.getValue();
        assertThat(message.rawReportId()).isEqualTo(EVENT.rawReportId());
        assertThat(message.analyzedAt()).isEqualTo(EVENT.analyzedAt());
        assertThat(message.incidents()).singleElement().satisfies(incident -> {
            assertThat(incident.incidentId()).isEqualTo(7L);
            assertThat(incident.occurredOn()).isEqualTo(LocalDate.of(2020, 4, 20));
            assertThat(incident.eventType()).isEqualTo("TRAFFIC_ACCIDENT");
            assertThat(incident.provinceCodes())
                    .as("a set has no order; the wire message must, or two identical signals differ")
                    .containsExactly((short) 16, (short) 41);
        });
    }

    /**
     * The stream is the least important thing in the request it runs inside. The text is stored and
     * the records are committed by the time this listener sees the event, so a failure here costs a
     * refresh trigger — and must not cost the submitter their 201.
     */
    @Test
    @DisplayName("a broadcast that fails does not fail the request behind it")
    void aFailingBroadcastIsSwallowed() {
        doThrow(new IllegalStateException("no")).when(stream).broadcast(any());

        listener.onIncidentRecordsProduced(EVENT);
    }

    /**
     * Not a formality. Publishing happens inside the analysing module's transaction; broadcasting
     * before it commits would send clients to refetch rows PostgreSQL has not made visible yet, and
     * since nothing is ever sent twice, that client would stay stale until an unrelated submission.
     */
    @Test
    @DisplayName("the broadcast waits for the analysing transaction to commit")
    void theListenerRunsAfterCommit() throws NoSuchMethodException {
        Method handler = IncidentRecordsProducedEventListener.class
                .getMethod("onIncidentRecordsProduced", IncidentRecordsProducedEvent.class);

        TransactionalEventListener annotation = handler.getAnnotation(TransactionalEventListener.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
    }
}
