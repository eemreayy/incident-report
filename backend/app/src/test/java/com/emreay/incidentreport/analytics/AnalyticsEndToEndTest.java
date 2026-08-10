package com.emreay.incidentreport.analytics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The chart and the summary, from submitted text to answered JSON.
 *
 * <p>Everything here goes in as Turkish prose and comes out as totals, through the real extraction
 * and the real database. That is the point: the numbers below are not fixtures somebody chose, they
 * are what the system makes of the sample texts, so a change in extraction, storage or aggregation
 * that quietly alters them fails here.
 *
 * <p>The case worth the most is the third sample text: eight accidents in Bursa and six in Kocaeli,
 * each province with its own deaths — and ten injured people the text gives for both provinces at
 * once. Those ten belong to no single province and must never be added to one, divided between
 * them, or dropped (ADR-019). Read by province alone, nobody was injured; the shared row is where
 * they are.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class AnalyticsEndToEndTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Container
    @ServiceConnection
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:8");

    private static final String EPIDEMIC_DAY_ONE =
            "20.04.2020 tarihinde Ankara'da sağlık yetkilileri tarafından yapılan açıklamada, "
                    + "salgın kapsamında yapılan testlerde 15 yeni vaka tespit edildi.";

    private static final String EPIDEMIC_DAY_TWO =
            "21.04.2020 tarihinde Ankara'da salgın kapsamında yapılan testlerde 5 yeni vaka tespit edildi.";

    /**
     * The third sample text, with its relative date replaced by an explicit one so the days these
     * tests assert on do not move with the calendar. Everything else is the source document's
     * wording, and the extraction of it is pinned as a golden test elsewhere.
     */
    private static final String TRAFFIC_TWO_PROVINCES =
            "20.04.2020 tarihinde Bursa'da 8, Kocaeli'nde 6 trafik kazası meydana geldi. "
                    + "Bursa'da 1, Kocaeli'nde ise 2 kişi kazalarda hayatını kaybetti. "
                    + "Her iki ilde toplam 10 kişi yaralı olarak hastaneye kaldırıldı.";

    private static boolean submitted;

    private final MockMvc mvc;

    AnalyticsEndToEndTest(@Autowired MockMvc mvc) {
        this.mvc = mvc;
    }

    /**
     * Submitted once for the whole class. The same text twice would be answered with the report that
     * already holds it and would analyse nothing (ADR-035), so re-submitting per test would be both
     * pointless and misleading.
     */
    @BeforeEach
    void submitTheSampleTexts() throws Exception {
        if (submitted) {
            return;
        }
        submit(EPIDEMIC_DAY_ONE);
        submit(EPIDEMIC_DAY_TWO);
        submit(TRAFFIC_TWO_PROVINCES);
        submitted = true;
    }

    /**
     * The DoD of C-1 and C-2 in one request: three series, the shared figure among them, labelled
     * as belonging to several provinces rather than to any of them.
     */
    @Test
    @DisplayName("broken down by province, the shared figure is its own labelled series")
    void theProvinceBreakdownKeepsTheSharedFigureVisibleAndSeparate() throws Exception {
        mvc.perform(get("/api/v1/analytics/time-series")
                        .param("eventType", "TRAFFIC_ACCIDENT")
                        .param("groupBy", "province"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupBy").value("PROVINCE"))
                // Accident counts and deaths belong to their provinces; the injured figure belongs
                // to both of them at once and so to neither - five series, not four.
                .andExpect(jsonPath("$.series.length()").value(5))
                .andExpect(jsonPath("$.series[0].metric").value("ACCIDENT_COUNT"))
                .andExpect(jsonPath("$.series[0].province.name").value("Bursa"))
                .andExpect(jsonPath("$.series[0].provinceScope").value("SINGLE"))
                .andExpect(jsonPath("$.series[0].points[0].value").value(8))
                .andExpect(jsonPath("$.series[1].province.name").value("Kocaeli"))
                .andExpect(jsonPath("$.series[1].points[0].value").value(6))
                .andExpect(jsonPath("$.series[4].metric").value("INJURED"))
                .andExpect(jsonPath("$.series[4].provinceScope").value("SHARED"))
                .andExpect(jsonPath("$.series[4].province").doesNotExist())
                .andExpect(jsonPath("$.series[4].points[0].value").value(10));
    }

    /**
     * The mistake that would look like a plausible number: matching the shared record once per
     * selected province and reporting twenty injured people who do not exist.
     */
    @Test
    @DisplayName("selecting both provinces counts their shared figure once")
    void selectingBothProvincesDoesNotDoubleTheSharedFigure() throws Exception {
        mvc.perform(get("/api/v1/analytics/time-series")
                        .param("eventType", "TRAFFIC_ACCIDENT")
                        .param("province", "16").param("province", "41")
                        .param("groupBy", "province"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.series.length()").value(5))
                .andExpect(jsonPath("$.series[4].metric").value("INJURED"))
                .andExpect(jsonPath("$.series[4].provinceScope").value("SHARED"))
                .andExpect(jsonPath("$.series[4].points[0].value").value(10));
    }

    /**
     * Without the breakdown there is nothing to keep apart: one line per metric, each covering
     * everything the filters allow, shared figures included. Scope is absent because it would mean
     * nothing here — no series is claiming to be a province's.
     */
    @Test
    @DisplayName("without a breakdown, one series per metric and no scope at all")
    void withoutABreakdownScopeDisappears() throws Exception {
        mvc.perform(get("/api/v1/analytics/time-series").param("eventType", "TRAFFIC_ACCIDENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupBy").value("NONE"))
                .andExpect(jsonPath("$.series.length()").value(3))
                .andExpect(jsonPath("$.series[0].metric").value("ACCIDENT_COUNT"))
                .andExpect(jsonPath("$.series[0].provinceScope").doesNotExist())
                .andExpect(jsonPath("$.series[0].points[0].value").value(14))
                .andExpect(jsonPath("$.series[1].metric").value("DEATH"))
                .andExpect(jsonPath("$.series[1].points[0].value").value(3))
                .andExpect(jsonPath("$.series[2].metric").value("INJURED"))
                .andExpect(jsonPath("$.series[2].points[0].value").value(10));
    }

    /** FR-12, over two days of real submissions: 15 on the first, 15 + 5 on the second. */
    @Test
    @DisplayName("cumulative mode adds each day to the ones before it")
    void cumulativeAccumulates() throws Exception {
        mvc.perform(get("/api/v1/analytics/time-series")
                        .param("eventType", "EPIDEMIC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cumulative").value(false))
                .andExpect(jsonPath("$.series[0].metric").value("NEW_CASE"))
                .andExpect(jsonPath("$.series[0].points[0].date").value("2020-04-20"))
                .andExpect(jsonPath("$.series[0].points[0].value").value(15))
                .andExpect(jsonPath("$.series[0].points[1].date").value("2020-04-21"))
                .andExpect(jsonPath("$.series[0].points[1].value").value(5));

        mvc.perform(get("/api/v1/analytics/time-series")
                        .param("eventType", "EPIDEMIC")
                        .param("cumulative", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cumulative").value(true))
                .andExpect(jsonPath("$.series[0].points[0].value").value(15))
                .andExpect(jsonPath("$.series[0].points[1].value").value(20));
    }

    /**
     * The summary's whole reason for returning three levels: the province rows do not add up to the
     * event type total on their own, and that is correct rather than a bug. The shared row is the
     * difference, and a reader can see it.
     */
    @Test
    @DisplayName("the summary's province rows and totals reconcile through the shared row")
    void theSummaryReconciles() throws Exception {
        mvc.perform(get("/api/v1/analytics/summary").param("eventType", "TRAFFIC_ACCIDENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows.length()").value(3))
                .andExpect(jsonPath("$.rows[0].province.name").value("Bursa"))
                .andExpect(jsonPath("$.rows[0].incidentCount").value(1))
                .andExpect(jsonPath("$.rows[0].metrics.ACCIDENT_COUNT").value(8))
                .andExpect(jsonPath("$.rows[0].metrics.DEATH").value(1))
                .andExpect(jsonPath("$.rows[1].province.name").value("Kocaeli"))
                .andExpect(jsonPath("$.rows[1].metrics.ACCIDENT_COUNT").value(6))
                .andExpect(jsonPath("$.rows[1].metrics.DEATH").value(2))
                // The injured figure appears in no province row at all. Reading the province rows
                // alone, nobody was hurt; the shared row is where those ten people are, and it is
                // the difference between the rows and the total.
                .andExpect(jsonPath("$.rows[2].provinceScope").value("SHARED"))
                .andExpect(jsonPath("$.rows[2].metrics.INJURED").value(10))
                .andExpect(jsonPath("$.rows[2].metrics.ACCIDENT_COUNT").doesNotExist())
                .andExpect(jsonPath("$.eventTypeTotals[0].eventType").value("TRAFFIC_ACCIDENT"))
                .andExpect(jsonPath("$.eventTypeTotals[0].metrics.ACCIDENT_COUNT").value(14))
                .andExpect(jsonPath("$.eventTypeTotals[0].metrics.DEATH").value(3))
                .andExpect(jsonPath("$.eventTypeTotals[0].metrics.INJURED").value(10))
                .andExpect(jsonPath("$.total.incidentCount").value(3));
    }

    @Test
    @DisplayName("unfiltered, the summary totals every event type together")
    void theGrandTotalCoversEveryEventType() throws Exception {
        mvc.perform(get("/api/v1/analytics/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventTypeTotals.length()").value(2))
                .andExpect(jsonPath("$.total.eventType").doesNotExist())
                .andExpect(jsonPath("$.total.metrics.INJURED").value(10))
                .andExpect(jsonPath("$.total.metrics.ACCIDENT_COUNT").value(14))
                .andExpect(jsonPath("$.total.metrics.NEW_CASE").value(20));
    }

    /** Through the real exception handler: a parameter nobody supports is a problem document. */
    @Test
    @DisplayName("an unsupported groupBy answers 400 with the error contract")
    void anUnsupportedGroupingIsRefused() throws Exception {
        mvc.perform(get("/api/v1/analytics/time-series").param("groupBy", "district"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("analytics.group-by.unknown"))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("a filter matching nothing answers with an empty chart and a zero total")
    void nothingMatchingIsStillAnAnswer() throws Exception {
        mvc.perform(get("/api/v1/analytics/time-series")
                        .param("from", "1999-01-01").param("to", "1999-12-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.series").isEmpty());

        mvc.perform(get("/api/v1/analytics/summary")
                        .param("from", "1999-01-01").param("to", "1999-12-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows").isEmpty())
                .andExpect(jsonPath("$.total.incidentCount").value(0));
    }

    private void submit(String text) throws Exception {
        mvc.perform(post("/api/v1/incident-reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"" + text + "\"}"))
                .andExpect(status().isCreated());
    }
}
