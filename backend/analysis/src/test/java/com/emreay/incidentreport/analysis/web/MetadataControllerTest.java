package com.emreay.incidentreport.analysis.web;

import com.emreay.incidentreport.analysis.catalog.IncidentCatalogConfiguration;
import com.emreay.incidentreport.analysis.domain.Province;
import com.emreay.incidentreport.analysis.repository.ProvinceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static com.emreay.incidentreport.analysis.domain.ProvinceFixture.province;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The one call the interface builds all its choices from (FR-16).
 *
 * <p>Loads the real catalog rather than a stubbed one — what is worth checking is that the file
 * which ships with the application reaches a client in a usable shape, not that a mock can be
 * mapped.
 */
@WebMvcTest(MetadataController.class)
@Import(IncidentCatalogConfiguration.class)
class MetadataControllerTest {

    private final MockMvc mvc;

    @MockitoBean
    private ProvinceRepository provinces;

    MetadataControllerTest(@Autowired MockMvc mvc) {
        this.mvc = mvc;
    }

    @BeforeEach
    void setUp() {
        List<Province> ordered = List.of(province(6, "Ankara"), province(16, "Bursa"));
        when(provinces.findAll(any(Sort.class))).thenReturn(ordered);
    }

    @Test
    void servesEveryEventTypeWithItsLabelAndMetrics() throws Exception {
        mvc.perform(get("/api/v1/metadata"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.eventTypes.length()").value(5))
                .andExpect(jsonPath("$.eventTypes[0].key").value("EPIDEMIC"))
                .andExpect(jsonPath("$.eventTypes[0].label").value("Salgın"))
                .andExpect(jsonPath("$.eventTypes[0].metrics[0].key").value("NEW_CASE"))
                .andExpect(jsonPath("$.eventTypes[0].metrics[0].label").value("Yeni vaka"));
    }

    /**
     * Labels travel with the keys so the interface has no key-to-Turkish mapping of its own. If it
     * had one, adding an event type would mean a configuration change and a frontend release, and
     * the two would drift apart in between.
     */
    @Test
    void everyEntryCarriesSomethingShowable() throws Exception {
        String body = mvc.perform(get("/api/v1/metadata")).andReturn().getResponse().getContentAsString();

        assertThat(body).contains("Deprem", "Trafik kazası", "Sel", "Yangın");
        mvc.perform(get("/api/v1/metadata"))
                .andExpect(jsonPath("$.eventTypes[?(@.label == '')]").isEmpty())
                .andExpect(jsonPath("$.eventTypes[*].metrics[?(@.label == '')]").isEmpty());
    }

    @Test
    void servesTheProvincesInPlateCodeOrder() throws Exception {
        mvc.perform(get("/api/v1/metadata"))
                .andExpect(jsonPath("$.provinces.length()").value(2))
                .andExpect(jsonPath("$.provinces[0].code").value(6))
                .andExpect(jsonPath("$.provinces[0].name").value("Ankara"));

        verify(provinces).findAll(Sort.by(Sort.Direction.ASC, "code"));
    }

    /**
     * Trigger keywords drive extraction, not presentation. Publishing them would turn an internal
     * tuning detail into a contract somebody could start depending on.
     */
    @Test
    void doesNotPublishTheTriggerKeywords() throws Exception {
        mvc.perform(get("/api/v1/metadata"))
                .andExpect(jsonPath("$.eventTypes[0].keywords").doesNotExist())
                .andExpect(jsonPath("$.eventTypes[0].metrics[0].keywords").doesNotExist());
    }

    /** OTHER is the absence of a match, not a choice a user should be offered. */
    @Test
    void doesNotOfferTheUnclassifiedFallbackAsAChoice() throws Exception {
        mvc.perform(get("/api/v1/metadata"))
                .andExpect(jsonPath("$.eventTypes[?(@.key == 'OTHER')]").isEmpty());
    }

}
