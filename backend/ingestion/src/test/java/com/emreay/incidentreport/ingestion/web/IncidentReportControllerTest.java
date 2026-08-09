package com.emreay.incidentreport.ingestion.web;

import com.emreay.incidentreport.ingestion.domain.RawIncidentReport;
import com.emreay.incidentreport.ingestion.service.IngestionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The HTTP contract of the ingestion endpoints.
 *
 * <p>A web slice with the service mocked: what is under test here is the shape of the exchange —
 * status codes, the Location header, what the JSON does and does not contain — not the domain rules
 * behind it, which have their own tests without a Spring context.
 *
 * <p>The error contract is verified separately in the {@code app} module, since the handler that
 * produces it answers for every module and lives there.
 */
@WebMvcTest(IncidentReportController.class)
class IncidentReportControllerTest {

    private static final Instant SUBMITTED_AT = Instant.parse("2026-08-09T09:30:00Z");
    private static final String ID = "652f1a2b3c4d5e6f70819200";
    private static final String TEXT = "20.04.2020 tarihinde Ankara'da 15 yeni vaka tespit edildi.";

    private final MockMvc mvc;

    @MockitoBean
    private IngestionService ingestionService;

    IncidentReportControllerTest(@Autowired MockMvc mvc) {
        this.mvc = mvc;
    }

    @Test
    void submittingAReportAnswersCreatedAndPointsAtIt() throws Exception {
        when(ingestionService.submit(TEXT)).thenReturn(stored());

        mvc.perform(post("/api/v1/incident-reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(TEXT)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/incident-reports/" + ID))
                .andExpect(jsonPath("$.id").value(ID))
                .andExpect(jsonPath("$.submittedAt").exists());
    }

    /**
     * A receipt, not a result (ADR-021). What the analysis found is read separately, so none of its
     * vocabulary may appear here — and neither may the text, which the caller already has.
     */
    @Test
    void theReceiptSaysNothingAboutTheAnalysis() throws Exception {
        when(ingestionService.submit(TEXT)).thenReturn(stored());

        mvc.perform(post("/api/v1/incident-reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(TEXT)))
                .andExpect(jsonPath("$.status").doesNotExist())
                .andExpect(jsonPath("$.warnings").doesNotExist())
                .andExpect(jsonPath("$.analyzedAt").doesNotExist())
                .andExpect(jsonPath("$.failureReason").doesNotExist())
                .andExpect(jsonPath("$.text").doesNotExist());
    }

    /** The text must reach the service exactly as it arrived — no trimming on the way in. */
    @Test
    void passesTheTextThroughUntouched() throws Exception {
        String padded = "  Ankara'da 15 vaka.  ";
        when(ingestionService.submit(padded)).thenReturn(stored());

        mvc.perform(post("/api/v1/incident-reports")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(padded)));

        verify(ingestionService).submit(padded);
    }

    @Test
    void readsASingleReport() throws Exception {
        when(ingestionService.findById(ID)).thenReturn(Optional.of(stored()));

        mvc.perform(get("/api/v1/incident-reports/{id}", ID))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(ID))
                .andExpect(jsonPath("$.text").value(TEXT));
    }

    @Test
    void listsReportsNewestFirstByDefault() throws Exception {
        when(ingestionService.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(stored()), PageRequest.of(0, 20), 1));

        mvc.perform(get("/api/v1/incident-reports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));

        verify(ingestionService).findAll(argThat(pageable -> {
            Sort.Order order = pageable.getSort().getOrderFor("submittedAt");
            return order != null && order.isDescending();
        }));
    }

    @Test
    void honoursAnExplicitPageRequest() throws Exception {
        when(ingestionService.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(2, 5), 40));

        mvc.perform(get("/api/v1/incident-reports").param("page", "2").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalPages").value(8));

        verify(ingestionService).findAll(eq(
                PageRequest.of(2, 5, Sort.by(Sort.Direction.DESC, "submittedAt"))));
    }

    private static String body(String text) {
        return "{\"text\":" + quote(text) + "}";
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static RawIncidentReport stored() {
        return new RawIncidentReport(ID, TEXT, SUBMITTED_AT);
    }
}
