package com.emreay.incidentreport.analysis.web;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.emreay.incidentreport.analysis.domain.AnalysisResult;
import com.emreay.incidentreport.analysis.domain.ClassificationStatus;
import com.emreay.incidentreport.analysis.domain.DateSource;
import com.emreay.incidentreport.analysis.domain.Incident;
import com.emreay.incidentreport.analysis.domain.KeywordRole;
import com.emreay.incidentreport.analysis.domain.Province;
import com.emreay.incidentreport.analysis.domain.ProvinceFixture;
import com.emreay.incidentreport.analysis.query.IncidentQuery;
import com.emreay.incidentreport.analysis.query.IncidentQueryService;
import com.emreay.incidentreport.shared.error.ResourceNotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The HTTP surface: what the query parameters mean, and what the response promises. */
@WebMvcTest(IncidentController.class)
class IncidentControllerTest {

    private static final String REPORT = "6a78ad14f6fe3fa987f9ff01";

    private final MockMvc mvc;

    @MockitoBean
    private IncidentQueryService incidents;

    IncidentControllerTest(@Autowired MockMvc mvc) {
        this.mvc = mvc;
    }

    private static Incident singleProvinceIncident() {
        Province bursa = ProvinceFixture.province(16, "Bursa");
        Incident incident = Incident.forProvince(REPORT, LocalDate.of(2020, 4, 20),
                DateSource.EXPLICIT, bursa, "TRAFFIC_ACCIDENT", ClassificationStatus.CLASSIFIED);
        incident.addMetric("ACCIDENT_COUNT", 8);
        incident.addKeyword("trafik kazası", KeywordRole.EVENT_TYPE, 14, 27);
        return incident;
    }

    private static Incident sharedIncident() {
        Incident incident = Incident.sharedAcross(REPORT, LocalDate.of(2020, 4, 20),
                DateSource.EXPLICIT,
                Set.of(ProvinceFixture.province(16, "Bursa"), ProvinceFixture.province(41, "Kocaeli")),
                "TRAFFIC_ACCIDENT", ClassificationStatus.CLASSIFIED);
        incident.addMetric("INJURED", 10);
        return incident;
    }

    private void returning(Incident... found) {
        Page<IncidentResponse> page = new PageImpl<>(
                java.util.Arrays.stream(found).map(IncidentResponse::of).toList(),
                PageRequest.of(0, 20), found.length);
        when(incidents.find(any(), any())).thenReturn(page);
        when(incidents.outcomeFor(any())).thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("a record comes back with its province, metrics and keyword positions")
    void aRecordIsFullyDescribed() throws Exception {
        returning(singleProvinceIncident());

        mvc.perform(get("/api/v1/incidents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].eventType").value("TRAFFIC_ACCIDENT"))
                .andExpect(jsonPath("$.content[0].dateSource").value("EXPLICIT"))
                .andExpect(jsonPath("$.content[0].provinceScope").value("SINGLE"))
                .andExpect(jsonPath("$.content[0].province.code").value(16))
                .andExpect(jsonPath("$.content[0].province.name").value("Bursa"))
                .andExpect(jsonPath("$.content[0].metrics[0].metricType").value("ACCIDENT_COUNT"))
                .andExpect(jsonPath("$.content[0].metrics[0].value").value(8))
                .andExpect(jsonPath("$.content[0].keywords[0].keyword").value("trafik kazası"))
                .andExpect(jsonPath("$.content[0].keywords[0].role").value("EVENT_TYPE"))
                .andExpect(jsonPath("$.content[0].keywords[0].charStart").value(14))
                .andExpect(jsonPath("$.content[0].keywords[0].charEnd").value(27));
    }

    @Test
    @DisplayName("a shared record names the provinces it covers and belongs to none of them")
    void aSharedRecordIsLabelledAsSuch() throws Exception {
        returning(sharedIncident());

        mvc.perform(get("/api/v1/incidents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].provinceScope").value("SHARED"))
                .andExpect(jsonPath("$.content[0].province").doesNotExist())
                .andExpect(jsonPath("$.content[0].sharedAcross.length()").value(2))
                .andExpect(jsonPath("$.content[0].sharedAcross[0].name").value("Bursa"));
    }

    @Test
    @DisplayName("the page reports its total, so an empty page is not read as no results")
    void theTotalIsReported() throws Exception {
        when(incidents.find(any(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(2, 20), 45));
        when(incidents.outcomeFor(any())).thenReturn(Optional.empty());

        mvc.perform(get("/api/v1/incidents?page=2"))
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(45))
                .andExpect(jsonPath("$.totalPages").value(3));
    }

    @Test
    @DisplayName("every filter reaches the query")
    void filtersArePassedThrough() throws Exception {
        returning();

        mvc.perform(get("/api/v1/incidents")
                        .param("eventType", "EPIDEMIC", "FLOOD")
                        .param("province", "16", "41")
                        .param("from", "2020-04-01")
                        .param("to", "2020-04-30")
                        .param("keyword", "vaka")
                        .param("rawReportId", REPORT))
                .andExpect(status().isOk());

        var query = forClass(IncidentQuery.class);
        verify(incidents).find(query.capture(), any());
        assertThat(query.getValue().eventTypes()).containsExactlyInAnyOrder("EPIDEMIC", "FLOOD");
        assertThat(query.getValue().provinces()).containsExactlyInAnyOrder((short) 16, (short) 41);
        assertThat(query.getValue().from()).isEqualTo(LocalDate.of(2020, 4, 1));
        assertThat(query.getValue().to()).isEqualTo(LocalDate.of(2020, 4, 30));
        assertThat(query.getValue().keyword()).isEqualTo("vaka");
        assertThat(query.getValue().rawReportId()).isEqualTo(REPORT);
    }

    @Test
    @DisplayName("asking about one report also answers how its analysis went")
    void oneReportCarriesItsOutcome() throws Exception {
        returning(singleProvinceIncident());
        when(incidents.outcomeFor(any())).thenReturn(Optional.of(AnalysisSummaryResponse.of(
                AnalysisResult.analyzed(REPORT, Instant.parse("2020-04-20T10:00:00Z"), 1,
                        List.of("bir uyarı")))));

        mvc.perform(get("/api/v1/incidents?rawReportId=" + REPORT))
                .andExpect(jsonPath("$.analysis.status").value("ANALYZED"))
                .andExpect(jsonPath("$.analysis.incidentCount").value(1))
                .andExpect(jsonPath("$.analysis.warnings[0]").value("bir uyarı"));
    }

    @Test
    @DisplayName("a failed analysis is explained even though it produced nothing")
    void aFailedAnalysisIsExplained() throws Exception {
        when(incidents.find(any(), any())).thenReturn(new PageImpl<>(List.of()));
        when(incidents.outcomeFor(any())).thenReturn(Optional.of(AnalysisSummaryResponse.of(
                AnalysisResult.failed(REPORT, Instant.parse("2020-04-20T10:00:00Z"),
                        "sunucu tarafı ayrıntı"))));

        mvc.perform(get("/api/v1/incidents?rawReportId=" + REPORT))
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.analysis.status").value("FAILED"))
                .andExpect(jsonPath("$.analysis.failureReason").doesNotExist());
    }

    @Test
    @DisplayName("a general listing carries no analysis outcome")
    void aGeneralListingHasNoOutcome() throws Exception {
        returning(singleProvinceIncident());

        mvc.perform(get("/api/v1/incidents"))
                .andExpect(jsonPath("$.analysis").doesNotExist());
    }

    @Test
    @DisplayName("one record by id")
    void findOne() throws Exception {
        when(incidents.findOne(7L)).thenReturn(Optional.of(IncidentResponse.of(singleProvinceIncident())));

        mvc.perform(get("/api/v1/incidents/7"))
                .andExpect(status().isOk())
                .andExpect(content -> assertThat(content.getResponse().getContentType())
                        .startsWith(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(jsonPath("$.rawReportId").value(REPORT));
    }

    /**
     * The 404 itself is not asserted here. Turning this exception into RFC 7807 is the handler's
     * job, and the handler lives in {@code app} — the only module that sees every controller — so
     * a slice test of this module would be testing something it does not contain. What is this
     * controller's job, and is asserted, is refusing to answer with an empty body.
     */
    @ParameterizedTest
    @ValueSource(longs = {999, -1})
    @DisplayName("an unknown record raises not-found rather than returning nothing")
    void unknownRecord(long id) {
        when(incidents.findOne(anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> mvc.perform(get("/api/v1/incidents/" + id)))
                .hasRootCauseInstanceOf(ResourceNotFoundException.class);
    }
}
