package com.emreay.incidentreport.realtime.service;

import com.emreay.incidentreport.realtime.config.RealtimeProperties;
import com.emreay.incidentreport.realtime.web.IncidentSignalMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Every client currently listening, and the one way to reach them all (FR-13, FR-25).
 *
 * <p>Holds no data and asks no one for any. A subscription is an open response and nothing else; if
 * this class lost every one of them the system would still answer every question correctly, because
 * the stream is a refresh trigger rather than a source (ADR-004, ADR-021).
 *
 * <p><strong>Connections are removed, never left to accumulate</strong> — the part of TC-10 that
 * actually costs something. A subscription leaves the set through four doors: the client closed it
 * ({@code onCompletion}), it outlived {@link RealtimeProperties#streamTimeout()}
 * ({@code onTimeout}), the container reported a failure ({@code onError}), or a write to it threw.
 * The last one matters most: a browser tab closed abruptly leaves a socket that looks perfectly
 * healthy until something is written to it, which is why the heartbeat exists as much for the
 * server as for the client.
 *
 * <p>The set is concurrent because it is read by the request thread that publishes a signal, the
 * scheduler thread that beats, and any container thread that reports a broken connection.
 */
@Service
public class IncidentStream {

    /**
     * The SSE event name clients subscribe to. Named rather than anonymous so a second kind of
     * message can be added later without every existing listener having to tell them apart.
     */
    static final String EVENT_NAME = "incidents";

    private static final Logger log = LoggerFactory.getLogger(IncidentStream.class);

    private final Set<SseEmitter> subscribers = ConcurrentHashMap.newKeySet();
    private final RealtimeProperties properties;

    public IncidentStream(RealtimeProperties properties) {
        this.properties = properties;
    }

    /**
     * Opens a subscription.
     *
     * <p>A comment is written straight away, before this returns. It carries nothing, and that is
     * the point: it commits the response, so the client's connection is established now rather than
     * whenever the first incident happens to be reported. Without it a quiet system is
     * indistinguishable from a broken one, both at the browser and at any proxy in between.
     */
    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(properties.streamTimeout().toMillis());
        register(emitter);
        send(emitter, SseEmitter.event().comment("subscribed"));

        log.debug("client subscribed to the incident stream; {} now connected", subscribers.size());
        return emitter;
    }

    /**
     * Sends one signal to every open subscription.
     *
     * <p>A client that cannot be written to is dropped and the rest still receive the message. One
     * dead connection must not cost the others their update — and there is no retry, because a
     * missed signal costs a client its liveness for a moment, not its data.
     */
    public void broadcast(IncidentSignalMessage message) {
        if (subscribers.isEmpty()) {
            return;
        }

        SseEmitter.SseEventBuilder event = SseEmitter.event()
                .name(EVENT_NAME)
                .data(message, MediaType.APPLICATION_JSON);

        subscribers.forEach(subscriber -> send(subscriber, event));

        log.debug("signalled raw report {} to {} subscriber(s): {} record(s)",
                message.rawReportId(), subscribers.size(), message.incidents().size());
    }

    /**
     * Writes a comment to every subscription, which both keeps the connection alive through idle
     * proxies and reveals the ones that are already gone.
     *
     * <p>A comment rather than an event on purpose: {@code EventSource} discards it without waking
     * any listener, so a heartbeat can never be mistaken for something having happened.
     */
    @Scheduled(fixedDelayString = "${incident-report.realtime.heartbeat-interval:20s}")
    void heartbeat() {
        subscribers.forEach(subscriber -> send(subscriber, SseEmitter.event().comment("heartbeat")));
    }

    /** How many clients are connected. Exposed for tests and for the log line above. */
    public int subscriberCount() {
        return subscribers.size();
    }

    /**
     * Registers an emitter and arranges for it to be forgotten however it ends.
     *
     * <p>Package-private and separate from {@link #subscribe()} so a test can register a connection
     * that behaves badly — the failure this class exists to survive is one that cannot be provoked
     * through a healthy emitter.
     */
    void register(SseEmitter emitter) {
        subscribers.add(emitter);
        emitter.onCompletion(() -> forget(emitter, "closed by the client"));
        emitter.onTimeout(() -> {
            forget(emitter, "timed out");
            emitter.complete();
        });
        emitter.onError(cause -> forget(emitter, "failed: " + cause));
    }

    private void send(SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (IOException | IllegalStateException unreachable) {
            // Both mean the same thing here: there is no longer a client on the other end. Neither
            // is worth a stack trace - a client leaving is normal, not an error.
            forget(emitter, "unreachable: " + unreachable);
            complete(emitter);
        }
    }

    private void forget(SseEmitter emitter, String reason) {
        if (subscribers.remove(emitter)) {
            log.debug("dropped a subscriber ({}); {} still connected", reason, subscribers.size());
        }
    }

    /**
     * Releases the server side of a connection that is already gone. It may well throw — completing
     * a response the container has torn down is exactly the case this handles — and by then the
     * subscription has been forgotten, so there is nothing left to do about it.
     */
    private void complete(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (RuntimeException alreadyGone) {
            log.trace("completing an already-broken subscription threw", alreadyGone);
        }
    }
}
