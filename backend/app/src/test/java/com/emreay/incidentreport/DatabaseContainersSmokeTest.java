package com.emreay.incidentreport;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the Testcontainers setup works before anything depends on it.
 *
 * <p>Repository and integration tests from T-04 onwards run against real databases rather than
 * in-memory substitutes, because the things worth testing here — Flyway migrations, Postgres
 * aggregation queries, Mongo document mapping — are exactly the things a substitute gets wrong.
 * This test exists so that a broken container setup surfaces here, with a one-line failure, instead
 * of inside the first repository test where it would look like a mapping bug.
 *
 * <p>Readiness is checked by asking the database itself from inside the container, the same way
 * {@code docker-compose.yml} does. Connecting over the wire would need the JDBC and Mongo drivers,
 * which arrive with the code that uses them in T-04; this test deliberately does not pull them in
 * early just to assert that a container starts.
 *
 * <p>Image tags are pinned to the same versions {@code docker-compose.yml} runs, so tests and the
 * running system do not silently drift apart.
 *
 * <p>Consequence worth knowing: {@code ./mvnw verify} needs a running Docker daemon. The image
 * build does not — it packages with {@code -DskipTests}.
 */
@Testcontainers
class DatabaseContainersSmokeTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"));

    @Container
    private static final MongoDBContainer MONGO =
            new MongoDBContainer(DockerImageName.parse("mongo:8"));

    @Test
    void postgresIsAcceptingConnections() throws Exception {
        var result = POSTGRES.execInContainer(
                "pg_isready", "-U", POSTGRES.getUsername(), "-d", POSTGRES.getDatabaseName());

        assertThat(result.getExitCode())
                .as("pg_isready exit code, stdout was: %s", result.getStdout())
                .isZero();
        assertThat(POSTGRES.getJdbcUrl()).startsWith("jdbc:postgresql://");
    }

    @Test
    void mongoAnswersPing() throws Exception {
        var result = MONGO.execInContainer(
                "mongosh", "--quiet", "--eval", "db.adminCommand('ping').ok");

        assertThat(result.getExitCode())
                .as("mongosh exit code, stderr was: %s", result.getStderr())
                .isZero();
        assertThat(result.getStdout().trim()).isEqualTo("1");
        assertThat(MONGO.getConnectionString()).startsWith("mongodb://");
    }
}
