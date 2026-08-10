package com.emreay.incidentreport.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The API documentation is generated, so what is worth testing is that it is generated at all
 * (NFR-07).
 *
 * <p>A document that fails to build is invisible: springdoc keeps quiet and the endpoint answers
 * 404, which looks exactly like a wrong URL. This test names the paths that must appear, so adding
 * a controller that springdoc cannot describe fails the build rather than dropping out of the
 * documentation unnoticed.
 *
 * <p>It boots the whole application because that is the only place every controller is visible —
 * the same reason the exception handler and the architecture rules live here.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class OpenApiDocumentTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Container
    @ServiceConnection
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:8");

    private final MockMvc mvc;

    OpenApiDocumentTest(@Autowired MockMvc mvc) {
        this.mvc = mvc;
    }

    @Test
    @DisplayName("the document is served, and describes this API")
    void theDocumentIsServed() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists())
                .andExpect(jsonPath("$.info.title").value("Incident Report API"))
                .andExpect(jsonPath("$.info.version").value("v1"));
    }

    @Test
    @DisplayName("every endpoint the README promises is in it")
    void everyEndpointIsDescribed() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['/api/v1/incident-reports'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/incident-reports'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/incident-reports/{id}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/incidents'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/incidents/{id}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/metadata'].get").exists());
    }

    @Test
    @DisplayName("the filters are documented as parameters, not left to be guessed")
    void theFiltersAreDescribed() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath(
                        "$.paths['/api/v1/incidents'].get.parameters[?(@.name == 'rawReportId')]").exists())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/incidents'].get.parameters[?(@.name == 'province')]").exists())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/incidents'].get.parameters[?(@.name == 'eventType')]").exists())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/incidents'].get.parameters[?(@.name == 'keyword')]").exists());
    }

    @Test
    @DisplayName("the response schemas come from the DTOs")
    void theSchemasAreGenerated() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.components.schemas.IncidentResponse").exists())
                .andExpect(jsonPath("$.components.schemas.IncidentPageResponse").exists())
                .andExpect(jsonPath("$.components.schemas.KeywordResponse.properties.charStart").exists());
    }

    @Test
    @DisplayName("the browsable UI is reachable at the short path")
    void theUiIsReachable() throws Exception {
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }
}
