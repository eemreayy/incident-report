package com.emreay.incidentreport.realtime.web;

import com.emreay.incidentreport.realtime.config.RealtimeProperties;
import com.emreay.incidentreport.realtime.service.IncidentStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.UnsupportedEncodingException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The stream as a client meets it: a GET that stays open and then receives what is published.
 *
 * <p>Run through the web layer rather than against the registry directly, because the two things
 * worth proving here only exist once there is a real response behind the emitter — that the
 * connection is established before anything is reported, and that a second client connected at the
 * same time receives the same message (FR-25).
 */
@WebMvcTest(IncidentStreamController.class)
@Import(IncidentStreamControllerTest.StreamUnderTest.class)
class IncidentStreamControllerTest {

    private static final IncidentSignalMessage MESSAGE = new IncidentSignalMessage(
            "652f1a2b3c4d5e6f70819200",
            Instant.parse("2026-08-10T09:30:00Z"),
            List.of(new IncidentSignalMessage.SignalledIncident(
                    7L, LocalDate.of(2020, 4, 20), "EPIDEMIC", List.of((short) 6))));

    private final MockMvc mvc;
    private final IncidentStream stream;

    IncidentStreamControllerTest(@Autowired MockMvc mvc, @Autowired IncidentStream stream) {
        this.mvc = mvc;
        this.stream = stream;
    }

    @Test
    @DisplayName("subscribing answers an open event stream straight away")
    void subscribingOpensTheConnectionImmediately() throws Exception {
        MvcResult subscription = subscribe();

        assertThat(body(subscription))
                .as("something is written at once, so a quiet system is not mistaken for a broken one")
                .startsWith(":");
    }

    @Test
    @DisplayName("two clients connected at once both receive the signal")
    void everyConnectedClientReceivesTheSignal() throws Exception {
        MvcResult first = subscribe();
        MvcResult second = subscribe();

        stream.broadcast(MESSAGE);

        assertThat(body(first)).contains("event:incidents").contains("\"incidentId\":7");
        assertThat(body(second)).contains("event:incidents").contains("\"incidentId\":7");
    }

    /**
     * The contract in one assertion: the message names the records and their dimensions, and
     * carries nothing a table could be drawn from (ADR-021, C-8).
     */
    @Test
    @DisplayName("the signal carries identifiers and dimensions, not data")
    void theSignalCarriesNoData() throws Exception {
        MvcResult subscription = subscribe();

        stream.broadcast(MESSAGE);

        assertThat(body(subscription))
                .contains("\"rawReportId\":\"652f1a2b3c4d5e6f70819200\"")
                .contains("\"occurredOn\":\"2020-04-20\"")
                .contains("\"eventType\":\"EPIDEMIC\"")
                .contains("\"provinceCodes\":[6]")
                .doesNotContain("metrics")
                .doesNotContain("keywords")
                .doesNotContain("classification");
    }

    private MvcResult subscribe() throws Exception {
        return mvc.perform(get("/api/v1/stream/incidents"))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andReturn();
    }

    private static String body(MvcResult subscription) throws UnsupportedEncodingException {
        return subscription.getResponse().getContentAsString();
    }

    /**
     * The real registry with the real defaults — mocking it would leave nothing under test but the
     * mapping annotation.
     */
    @TestConfiguration
    static class StreamUnderTest {

        @Bean
        IncidentStream incidentStream() {
            return new IncidentStream(new RealtimeProperties(Duration.ofMinutes(30), Duration.ofSeconds(20)));
        }
    }
}
