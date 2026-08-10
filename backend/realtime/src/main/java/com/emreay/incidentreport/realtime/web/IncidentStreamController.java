package com.emreay.incidentreport.realtime.web;

import com.emreay.incidentreport.realtime.service.IncidentStream;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * The live stream: one GET that does not end (FR-13).
 *
 * <p>Server to client only. There is no endpoint here that accepts anything, and the subscription
 * carries no parameters — with no authentication there are no per-client views to filter for
 * (ADR-011), and a filter would not help anyway, since the client decides relevance from the signal
 * and refetches with the filters it is actually showing.
 *
 * <p>Nothing is reachable only through this stream. A client that never connects, or whose
 * connection dies and stays dead, sees exactly the same data through the query endpoints — it just
 * has to ask (ADR-021).
 */
@RestController
@RequestMapping("/api/v1/stream")
public class IncidentStreamController {

    private final IncidentStream stream;

    public IncidentStreamController(IncidentStream stream) {
        this.stream = stream;
    }

    /**
     * Subscribes to new-record signals.
     *
     * <p>Answers immediately with an open {@code text/event-stream} response and then writes to it
     * as reports are analysed. The response is committed before this method returns, so a client
     * knows it is connected without waiting for the first incident.
     *
     * <p>Comments are written periodically to hold the connection open; {@code EventSource}
     * discards them. The server closes a subscription after a configured period and the browser
     * reconnects by itself — nothing is lost in between, because the data is fetched, not streamed.
     */
    @GetMapping(path = "/incidents", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter incidents() {
        return stream.subscribe();
    }
}
