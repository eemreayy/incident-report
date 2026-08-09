package com.emreay.incidentreport;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Starts the whole application against real databases.
 *
 * <p>This is the one test that proves the modules actually assemble: component scanning reaches
 * every module's package even though they ship as separate jars, both datasources wire up, and
 * Flyway brings the schema to a state the entities validate against
 * ({@code spring.jpa.hibernate.ddl-auto=validate}).
 *
 * <p>Image tags match the ones {@code docker-compose.yml} runs, so what the tests exercise and what
 * the system runs on cannot drift apart.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class IncidentReportApplicationTests {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Container
    @ServiceConnection
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:8");

    private final DataSource dataSource;

    IncidentReportApplicationTests(@Autowired DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Test
    void contextLoads() {
        // Fails if any module's beans cannot be built, or if the entities disagree with the schema.
    }

    @Test
    void flywayHasBuiltTheSchemaAndLoadedReferenceData() throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {

            // Counting migrations would mean editing this test every time one is added, which
            // teaches people to edit the test rather than read the failure. What matters is that
            // none of them failed.
            try (ResultSet failed = statement.executeQuery(
                    "select count(*) from flyway_schema_history where not success")) {
                assertThat(failed.next()).isTrue();
                assertThat(failed.getInt(1)).as("no migration failed").isZero();
            }

            try (ResultSet provinces = statement.executeQuery("select count(*) from province")) {
                assertThat(provinces.next()).isTrue();
                assertThat(provinces.getInt(1)).isEqualTo(81);
            }

            // The analysis outcome lives here now, not on the raw document (ADR-021).
            try (ResultSet outcomes = statement.executeQuery("select count(*) from analysis_result")) {
                assertThat(outcomes.next()).isTrue();
            }
        }
    }
}
