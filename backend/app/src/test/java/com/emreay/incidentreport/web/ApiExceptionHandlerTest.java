package com.emreay.incidentreport.web;

import com.emreay.incidentreport.ingestion.service.IngestionService;
import com.emreay.incidentreport.ingestion.web.IncidentReportController;
import com.emreay.incidentreport.shared.error.DomainValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The error contract, exercised through a real endpoint rather than by calling the handler.
 *
 * <p>It lives in {@code app} because that is where the handler lives, and the handler lives there
 * because it answers for every module: there should be exactly one description of what a failure
 * looks like on the wire.
 *
 * <p>What each test is really guarding is that a caller gets something it can act on — a stable
 * code, the right status — and nothing it should never see.
 */
@WebMvcTest(controllers = IncidentReportController.class)
class ApiExceptionHandlerTest {

    private final MockMvc mvc;

    @MockitoBean
    private IngestionService ingestionService;

    ApiExceptionHandlerTest(@Autowired MockMvc mvc) {
        this.mvc = mvc;
    }

    @Test
    void aDomainValidationFailureBecomesA400ProblemWithItsCode() throws Exception {
        when(ingestionService.submit(anyString())).thenThrow(
                new DomainValidationException("report.text.blank", "Incident report text must not be empty."));

        mvc.perform(post("/api/v1/incident-reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Invalid request"))
                .andExpect(jsonPath("$.detail").value("Incident report text must not be empty."))
                .andExpect(jsonPath("$.code").value("report.text.blank"))
                .andExpect(jsonPath("$.type").value("https://incident-report/problems/report.text.blank"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void anUnknownIdBecomesA404Problem() throws Exception {
        when(ingestionService.findById("missing")).thenReturn(Optional.empty());

        mvc.perform(get("/api/v1/incident-reports/{id}", "missing"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("resource.not-found"))
                .andExpect(jsonPath("$.detail").value("Incident report missing was not found."));
    }

    /** Spring's own failures have to come out in the same shape, not as a different body. */
    @Test
    void malformedJsonBecomesAProblemToo() throws Exception {
        mvc.perform(post("/api/v1/incident-reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\": "))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("request.bad-request"))
                .andExpect(jsonPath("$.type").value("https://incident-report/problems/request.bad-request"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    /**
     * There is no delete endpoint and there will not be one — the raw text is an audit log. The
     * point here is that asking for one is answered in the standard shape, and with a code that
     * says what actually happened: calling this "malformed" would tell a client its JSON was
     * broken when the request body was never the problem.
     */
    @Test
    void anUnsupportedMethodSaysSoRatherThanBlamingTheBody() throws Exception {
        mvc.perform(delete("/api/v1/incident-reports/{id}", "652f1a2b3c4d5e6f70819200"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("request.method-not-allowed"));
    }

    /**
     * The one that matters most: an unexpected failure must tell the caller nothing about what
     * broke. Echoing an exception message back is how table names and library internals end up in
     * front of whoever asked.
     */
    @Test
    void anUnexpectedFailureLeaksNothingAndGivesAReference() throws Exception {
        when(ingestionService.submit(anyString()))
                .thenThrow(new IllegalStateException("connection to jdbc:postgresql://secret-host/db lost"));

        String response = mvc.perform(post("/api/v1/incident-reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Ankara'da 15 vaka\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("internal"))
                .andExpect(jsonPath("$.reference").exists())
                .andReturn().getResponse().getContentAsString();

        assertThat(response)
                .doesNotContain("IllegalStateException")
                .doesNotContain("secret-host")
                .doesNotContain("jdbc:postgresql");
    }
}
