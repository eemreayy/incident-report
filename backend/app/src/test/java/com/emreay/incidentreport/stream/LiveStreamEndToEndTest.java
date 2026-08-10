package com.emreay.incidentreport.stream;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.UnsupportedEncodingException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The journey a submission makes, from the text arriving to connected clients hearing about it
 * (FR-13, FR-25).
 *
 * <p>Only {@code app} can test this: the three modules involved cannot see each other, and the
 * chain — store the text, announce it, analyse it, commit, signal — only exists once they are
 * assembled. Each link has its own test in its own module; what is left for here is that the links
 * are actually joined, which no unit test can show.
 *
 * <p>Both databases are real. The commit is the point: the broadcast waits for PostgreSQL, so a
 * substitute that never commits would make this test pass while the real thing sent nothing.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class LiveStreamEndToEndTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Container
    @ServiceConnection
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:8");

    private static final String TEXT = """
            20.04.2020 tarihinde Ankara'da sağlık yetkilileri tarafından yapılan açıklamada, \
            salgın kapsamında yapılan testlerde 15 yeni vaka tespit edildi.""";

    private final MockMvc mvc;

    LiveStreamEndToEndTest(@Autowired MockMvc mvc) {
        this.mvc = mvc;
    }

    @Test
    @DisplayName("a submission reaches every client that was listening, and names what it produced")
    void aSubmissionReachesEveryListeningClient() throws Exception {
        MvcResult first = subscribe();
        MvcResult second = subscribe();

        String reportId = submit();

        assertThat(body(first))
                .contains("event:incidents")
                .contains("\"rawReportId\":\"" + reportId + "\"")
                .contains("\"eventType\":\"EPIDEMIC\"")
                .contains("\"provinceCodes\":[6]");
        assertThat(body(second)).contains("\"rawReportId\":\"" + reportId + "\"");
    }

    /**
     * The signal says a record exists; it does not say what is in it. A client learns the figures
     * by asking, which is what keeps the stream from being the only way to reach anything
     * (ADR-021).
     */
    @Test
    @DisplayName("the signal names the records without carrying them")
    void theSignalCarriesNoFigures() throws Exception {
        MvcResult subscription = subscribe();

        submit();

        assertThat(body(subscription))
                .contains("\"incidentId\":")
                .doesNotContain("NEW_CASE")
                .doesNotContain("metrics")
                .doesNotContain("keywords")
                .doesNotContain("vaka");
    }

    private MvcResult subscribe() throws Exception {
        return mvc.perform(get("/api/v1/stream/incidents"))
                .andExpect(request().asyncStarted())
                .andReturn();
    }

    /** @return the id of the stored raw report, as the receipt gives it */
    private String submit() throws Exception {
        MvcResult submission = mvc.perform(post("/api/v1/incident-reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"" + TEXT.replace("\"", "\\\"") + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        return JsonPath.read(submission.getResponse().getContentAsString(), "$.id");
    }

    private static String body(MvcResult subscription) throws UnsupportedEncodingException {
        return subscription.getResponse().getContentAsString();
    }
}
