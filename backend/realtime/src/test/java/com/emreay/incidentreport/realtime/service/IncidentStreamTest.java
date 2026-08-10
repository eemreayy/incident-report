package com.emreay.incidentreport.realtime.service;

import com.emreay.incidentreport.realtime.config.RealtimeProperties;
import com.emreay.incidentreport.realtime.web.IncidentSignalMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * How the stream behaves when clients come and go.
 *
 * <p>No Spring context and no container: what is under test is the bookkeeping — who is still
 * connected, who is dropped, and whether one broken connection can cost the others their signal.
 * The emitters here stand in for the container, which is the only way to provoke the failure this
 * class exists to survive; a healthy {@code SseEmitter} outside a servlet simply buffers what is
 * written to it and reveals nothing.
 */
class IncidentStreamTest {

    private static final IncidentSignalMessage MESSAGE = new IncidentSignalMessage(
            "652f1a2b3c4d5e6f70819200",
            Instant.parse("2026-08-10T09:30:00Z"),
            List.of(new IncidentSignalMessage.SignalledIncident(
                    1L, LocalDate.of(2020, 4, 20), "EPIDEMIC", List.of((short) 6))));

    private IncidentStream stream;

    @BeforeEach
    void setUp() {
        stream = new IncidentStream(new RealtimeProperties(Duration.ofMinutes(30), Duration.ofSeconds(20)));
    }

    @Test
    @DisplayName("every connected client receives the same signal")
    void broadcastReachesEverySubscriber() {
        RecordingEmitter first = register(new RecordingEmitter());
        RecordingEmitter second = register(new RecordingEmitter());

        stream.broadcast(MESSAGE);

        assertThat(first.received()).containsExactly(MESSAGE);
        assertThat(second.received()).containsExactly(MESSAGE);
        assertThat(first.text()).contains("event:" + IncidentStream.EVENT_NAME);
    }

    /**
     * The reason a broadcast may not be a loop that stops at the first exception: one client having
     * closed its laptop must not stop the update reaching everyone else.
     */
    @Test
    @DisplayName("a client that cannot be written to is dropped, and the others still get the signal")
    void aBrokenSubscriberIsDroppedWithoutCostingTheOthers() {
        register(new FailingEmitter());
        RecordingEmitter healthy = register(new RecordingEmitter());

        stream.broadcast(MESSAGE);

        assertThat(healthy.received()).containsExactly(MESSAGE);
        assertThat(stream.subscriberCount()).isOne();
    }

    /**
     * The half of TC-10 that cannot be seen from the outside: a client that vanished without
     * closing its connection leaves a socket that looks healthy until something is written to it.
     * The heartbeat is what writes.
     */
    @Test
    @DisplayName("the heartbeat keeps live connections open and reveals the ones already gone")
    void heartbeatHoldsLiveConnectionsAndCollectsDeadOnes() {
        RecordingEmitter alive = register(new RecordingEmitter());
        register(new FailingEmitter());

        stream.heartbeat();

        assertThat(stream.subscriberCount()).isOne();
        assertThat(alive.received()).isEmpty();
        assertThat(alive.text())
                .as("a comment, so no client mistakes a heartbeat for something having happened")
                .contains(":heartbeat");
    }

    @Test
    @DisplayName("a client that closes its connection is forgotten")
    void closingDeregisters() {
        TestEmitter emitter = register(new RecordingEmitter());

        emitter.completion.run();

        assertThat(stream.subscriberCount()).isZero();
    }

    @Test
    @DisplayName("a connection that outlives its timeout is closed and forgotten")
    void timingOutDeregistersAndReleasesTheConnection() {
        RecordingEmitter emitter = register(new RecordingEmitter());

        emitter.timeout.run();

        assertThat(stream.subscriberCount()).isZero();
        assertThat(emitter.completed).as("the server side is released, not just forgotten").isTrue();
    }

    @Test
    @DisplayName("a connection the container reports as failed is forgotten")
    void failingDeregisters() {
        TestEmitter emitter = register(new RecordingEmitter());

        emitter.error.accept(new IOException("broken pipe"));

        assertThat(stream.subscriberCount()).isZero();
    }

    @Test
    @DisplayName("signalling with nobody listening is not an error")
    void broadcastingToNobodyIsHarmless() {
        stream.broadcast(MESSAGE);

        assertThat(stream.subscriberCount()).isZero();
    }

    private <T extends TestEmitter> T register(T emitter) {
        stream.register(emitter);
        return emitter;
    }

    /**
     * An emitter that captures the callbacks the container would normally hold, so a test can end
     * a connection the way the container ends it.
     */
    private static class TestEmitter extends SseEmitter {

        Runnable completion;
        Runnable timeout;
        Consumer<Throwable> error;
        boolean completed;

        @Override
        public synchronized void onCompletion(Runnable callback) {
            this.completion = callback;
        }

        @Override
        public synchronized void onTimeout(Runnable callback) {
            this.timeout = callback;
        }

        @Override
        public synchronized void onError(Consumer<Throwable> callback) {
            this.error = callback;
        }

        @Override
        public synchronized void complete() {
            this.completed = true;
        }
    }

    /** Keeps what was written to it, since a real emitter outside a container keeps it to itself. */
    private static final class RecordingEmitter extends TestEmitter {

        private final List<ResponseBodyEmitter.DataWithMediaType> written = new ArrayList<>();

        @Override
        public void send(SseEventBuilder builder) {
            written.addAll(builder.build());
        }

        /** The payload objects, without the SSE framing around them. */
        private List<Object> received() {
            return written.stream()
                    .map(ResponseBodyEmitter.DataWithMediaType::getData)
                    .filter(data -> !(data instanceof String))
                    .toList();
        }

        /** The framing itself: event names and comments. */
        private String text() {
            return written.stream()
                    .map(ResponseBodyEmitter.DataWithMediaType::getData)
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .reduce("", String::concat);
        }
    }

    /** A client that has gone without saying so — the write is the first anyone hears of it. */
    private static final class FailingEmitter extends TestEmitter {

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            throw new IOException("client gone");
        }
    }
}
