package com.emreay.incidentreport.analysis.web;

import com.emreay.incidentreport.analysis.domain.ProvinceScope;
import com.emreay.incidentreport.analysis.query.AnalyticsQuery;
import com.emreay.incidentreport.analysis.query.AnalyticsService;
import com.emreay.incidentreport.analysis.query.ProvinceGrouping;
import com.emreay.incidentreport.shared.error.DomainValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The HTTP contract of the aggregation endpoints: what the parameters mean, and what the JSON does
 * and does not say. The arithmetic behind them is verified against a real database elsewhere.
 */
@WebMvcTest(AnalyticsController.class)
class AnalyticsControllerTest {

    private static final LocalDate DAY = LocalDate.of(2020, 4, 20);

    private final MockMvc mvc;

    @MockitoBean
    private AnalyticsService analytics;

    AnalyticsControllerTest(@Autowired MockMvc mvc) {
        this.mvc = mvc;
    }

    @Test
    @DisplayName("every filter reaches the query, so the chart cannot show something else than the table")
    void allFiltersArePassedThrough() throws Exception {
        when(analytics.timeSeries(any())).thenReturn(empty(false, ProvinceGrouping.NONE));

        mvc.perform(get("/api/v1/analytics/time-series")
                        .param("eventType", "TRAFFIC_ACCIDENT")
                        .param("province", "16").param("province", "41")
                        .param("from", "2020-04-01").param("to", "2020-04-30")
                        .param("keyword", "kaza"))
                .andExpect(status().isOk());

        ArgumentCaptor<AnalyticsQuery> asked = ArgumentCaptor.forClass(AnalyticsQuery.class);
        verify(analytics).timeSeries(asked.capture());
        assertThat(asked.getValue().eventTypes()).containsExactly("TRAFFIC_ACCIDENT");
        assertThat(asked.getValue().provinces()).containsExactlyInAnyOrder((short) 16, (short) 41);
        assertThat(asked.getValue().from()).isEqualTo(LocalDate.of(2020, 4, 1));
        assertThat(asked.getValue().to()).isEqualTo(LocalDate.of(2020, 4, 30));
        assertThat(asked.getValue().keyword()).isEqualTo("kaza");
    }

    @Test
    @DisplayName("without groupBy, province is only a filter")
    void groupingIsOffByDefault() throws Exception {
        when(analytics.timeSeries(any())).thenReturn(empty(false, ProvinceGrouping.NONE));

        mvc.perform(get("/api/v1/analytics/time-series")).andExpect(status().isOk());

        ArgumentCaptor<AnalyticsQuery> asked = ArgumentCaptor.forClass(AnalyticsQuery.class);
        verify(analytics).timeSeries(asked.capture());
        assertThat(asked.getValue().groupBy()).isEqualTo(ProvinceGrouping.NONE);
        assertThat(asked.getValue().cumulative()).isFalse();
    }

    /** The spelling in the documentation is lower case, and that is the spelling people type. */
    @Test
    @DisplayName("groupBy=province is understood as written")
    void groupByIsReadCaseInsensitively() throws Exception {
        when(analytics.timeSeries(any())).thenReturn(empty(true, ProvinceGrouping.PROVINCE));

        mvc.perform(get("/api/v1/analytics/time-series")
                        .param("groupBy", "province")
                        .param("cumulative", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupBy").value("PROVINCE"))
                .andExpect(jsonPath("$.cumulative").value(true));

        ArgumentCaptor<AnalyticsQuery> asked = ArgumentCaptor.forClass(AnalyticsQuery.class);
        verify(analytics).timeSeries(asked.capture());
        assertThat(asked.getValue().groupsByProvince()).isTrue();
        assertThat(asked.getValue().cumulative()).isTrue();
    }

    /**
     * Falling back to no grouping would answer a different question with a 200, and the reader
     * would have no way of noticing.
     *
     * <p>Asserted as the refusal itself rather than as a 400: the handler that turns it into a
     * problem document answers for every module and lives in {@code app}, which is where the status
     * code and the body are verified.
     */
    @Test
    @DisplayName("a groupBy nobody supports is refused, not ignored")
    void anUnknownGroupingIsRefused() {
        assertThatThrownBy(() -> mvc.perform(get("/api/v1/analytics/time-series")
                .param("groupBy", "district")))
                .rootCause()
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("groupBy");

        verifyNoInteractions(analytics);
    }

    @Test
    @DisplayName("a date range that runs backwards is refused")
    void anImpossibleRangeIsRefused() {
        assertThatThrownBy(() -> mvc.perform(get("/api/v1/analytics/time-series")
                .param("from", "2020-05-01").param("to", "2020-04-01")))
                .rootCause()
                // The caller's mistake, not the server's: as a plain IllegalArgumentException this
                // came back as a 500, which tells the caller to report a bug instead of fixing
                // their dates. Same treatment as an unknown groupBy, just above.
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("is after the end date");

        verifyNoInteractions(analytics);
    }

    @Test
    @DisplayName("a series carries its keys and its points, and nothing about records")
    void theSeriesShape() throws Exception {
        when(analytics.timeSeries(any())).thenReturn(new TimeSeriesResponse(false, ProvinceGrouping.PROVINCE,
                List.of(new TimeSeriesResponse.Series("TRAFFIC_ACCIDENT", "INJURED",
                                ProvinceScope.SINGLE, new ProvinceResponse((short) 16, "Bursa"),
                                List.of(new TimeSeriesResponse.Point(DAY, 8))),
                        new TimeSeriesResponse.Series("TRAFFIC_ACCIDENT", "INJURED",
                                ProvinceScope.SHARED, null,
                                List.of(new TimeSeriesResponse.Point(DAY, 10))))));

        mvc.perform(get("/api/v1/analytics/time-series").param("groupBy", "province"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.series[0].eventType").value("TRAFFIC_ACCIDENT"))
                .andExpect(jsonPath("$.series[0].metric").value("INJURED"))
                .andExpect(jsonPath("$.series[0].provinceScope").value("SINGLE"))
                .andExpect(jsonPath("$.series[0].province.name").value("Bursa"))
                .andExpect(jsonPath("$.series[0].points[0].date").value("2020-04-20"))
                .andExpect(jsonPath("$.series[0].points[0].value").value(8))
                // The shared figure is a series of its own, labelled, with no province attached -
                // it belongs to both of them and to neither (ADR-019).
                .andExpect(jsonPath("$.series[1].provinceScope").value("SHARED"))
                .andExpect(jsonPath("$.series[1].province").doesNotExist())
                .andExpect(jsonPath("$.series[1].points[0].value").value(10));
    }

    @Test
    @DisplayName("the summary answers with its rows and both roll-ups")
    void theSummaryShape() throws Exception {
        when(analytics.summary(any())).thenReturn(new SummaryResponse(
                List.of(new SummaryResponse.Row("TRAFFIC_ACCIDENT", ProvinceScope.SINGLE,
                                new ProvinceResponse((short) 16, "Bursa"), 1, Map.of("INJURED", 8L)),
                        new SummaryResponse.Row("TRAFFIC_ACCIDENT", ProvinceScope.SHARED,
                                null, 1, Map.of("INJURED", 10L))),
                List.of(new SummaryResponse.Row("TRAFFIC_ACCIDENT", null, null, 2, Map.of("INJURED", 18L))),
                new SummaryResponse.Row(null, null, null, 2, Map.of("INJURED", 18L))));

        mvc.perform(get("/api/v1/analytics/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].province.code").value(16))
                .andExpect(jsonPath("$.rows[0].metrics.INJURED").value(8))
                .andExpect(jsonPath("$.rows[1].provinceScope").value("SHARED"))
                .andExpect(jsonPath("$.rows[1].metrics.INJURED").value(10))
                .andExpect(jsonPath("$.eventTypeTotals[0].eventType").value("TRAFFIC_ACCIDENT"))
                .andExpect(jsonPath("$.eventTypeTotals[0].metrics.INJURED").value(18))
                // The grand total is about everything, so it names neither an event type nor a
                // bucket; both fields are absent rather than null.
                .andExpect(jsonPath("$.total.eventType").doesNotExist())
                .andExpect(jsonPath("$.total.provinceScope").doesNotExist())
                .andExpect(jsonPath("$.total.incidentCount").value(2))
                .andExpect(jsonPath("$.total.metrics.INJURED").value(18));
    }

    /** The summary always breaks province out; that is what the table is (FR-22). */
    @Test
    @DisplayName("the summary needs no groupBy - it always breaks province out")
    void theSummaryAlwaysGroupsByProvince() throws Exception {
        when(analytics.summary(any())).thenReturn(
                new SummaryResponse(List.of(), List.of(), new SummaryResponse.Row(null, null, null, 0, Map.of())));

        mvc.perform(get("/api/v1/analytics/summary")).andExpect(status().isOk());

        ArgumentCaptor<AnalyticsQuery> asked = ArgumentCaptor.forClass(AnalyticsQuery.class);
        verify(analytics).summary(asked.capture());
        assertThat(asked.getValue().groupsByProvince()).isTrue();
        assertThat(asked.getValue().cumulative()).isFalse();
    }

    private static TimeSeriesResponse empty(boolean cumulative, ProvinceGrouping groupBy) {
        return new TimeSeriesResponse(cumulative, groupBy, List.of());
    }
}
