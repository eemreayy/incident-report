package com.emreay.incidentreport.reprocess;

import com.jayway.jsonpath.DocumentContext;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Reading a stored text again, and sending one twice (FR-15, TC-9).
 *
 * <p>Both promises span the two stores and can only be checked with both of them running: the text
 * lives in MongoDB, the records derived from it in PostgreSQL, and what is being asserted is that
 * repeating an operation does not repeat its output. A mock of either side would be asserting the
 * mock.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class ReprocessEndToEndTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Container
    @ServiceConnection
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:8");

    /** The sample text with the most to lose: two provinces, a shared figure, a relative date. */
    private static final String TEXT = "Son 24 saatte Bursa ilinde 8, Kocaeli ilinde 6 trafik kazası "
            + "meydana geldi. Kazalarda her iki ilde toplam 10 kişi yaralandı.";

    private final MockMvc mvc;

    ReprocessEndToEndTest(@Autowired MockMvc mvc) {
        this.mvc = mvc;
    }

    /**
     * The number that must not move. Reprocessing rebuilds rather than adds, so running it twice
     * leaves exactly what one run leaves — the alternative is a report whose figures grow every
     * time somebody improves the catalog.
     */
    @Test
    @DisplayName("reprocessing twice leaves the same records, not three copies of them")
    void reprocessingIsRepeatable() throws Exception {
        String reportId = submit(TEXT, status().isCreated());

        DocumentContext afterSubmission = incidentsOf(reportId);
        int recordCount = afterSubmission.read("$.totalElements");
        List<String> dates = afterSubmission.read("$.content[*].occurredOn");
        assertThat(recordCount).as("the text produces three records to begin with").isEqualTo(3);

        reprocess(reportId);
        reprocess(reportId);

        DocumentContext afterTwoReprocesses = incidentsOf(reportId);
        assertThat((int) afterTwoReprocesses.read("$.totalElements")).isEqualTo(recordCount);
        assertThat(afterTwoReprocesses.<List<String>>read("$.content[*].occurredOn"))
                .as("the reference date is the report's own submission time, not today (ADR-014)")
                .isEqualTo(dates);
        assertThat(afterTwoReprocesses.<String>read("$.analysis.status")).isEqualTo("ANALYZED");
    }

    /** The records are rebuilt, not left alone: reprocess deletes before it writes. */
    @Test
    @DisplayName("reprocessing replaces the previous records rather than keeping them")
    void reprocessingRebuilds() throws Exception {
        String reportId = submit(TEXT + " Ek cümle.", status().isCreated());

        List<Integer> before = incidentsOf(reportId).read("$.content[*].id");
        reprocess(reportId);
        List<Integer> after = incidentsOf(reportId).read("$.content[*].id");

        assertThat(after).hasSameSizeAs(before).doesNotContainAnyElementsOf(before);
    }

    /**
     * The reason repeated submissions are recognised at all: the same text arriving twice must not
     * become two reports, or every figure it contains is counted twice (ADR-035).
     */
    @Test
    @DisplayName("the same text submitted twice stays one report and one set of records")
    void aRepeatedSubmissionChangesNothing() throws Exception {
        String text = TEXT + " İkinci gönderim denemesi.";

        String first = submit(text, status().isCreated());
        String second = submit(text, status().isOk());

        assertThat(second).as("answered with the report that already holds the text").isEqualTo(first);
        assertThat((int) incidentsOf(first).read("$.totalElements")).isEqualTo(3);
    }

    @Test
    @DisplayName("reprocessing something that was never stored is a 404")
    void reprocessingAnUnknownReportIsNotFound() throws Exception {
        mvc.perform(post("/api/v1/incident-reports/{id}/reprocess", "652f1a2b3c4d5e6f70819200"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("resource.not-found"));
    }

    private String submit(String text, org.springframework.test.web.servlet.ResultMatcher expected)
            throws Exception {

        MvcResult result = mvc.perform(post("/api/v1/incident-reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"" + text + "\"}"))
                .andExpect(expected)
                .andReturn();

        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private void reprocess(String reportId) throws Exception {
        mvc.perform(post("/api/v1/incident-reports/{id}/reprocess", reportId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(reportId));
    }

    private DocumentContext incidentsOf(String reportId) throws Exception {
        MvcResult result = mvc.perform(get("/api/v1/incidents").param("rawReportId", reportId))
                .andExpect(status().isOk())
                .andReturn();

        return JsonPath.parse(result.getResponse().getContentAsString());
    }
}
