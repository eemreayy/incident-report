package com.emreay.incidentreport;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke test: the Spring context must start with every module on the classpath.
 *
 * <p>Cheap, but it catches the whole class of failures where a bean definition, a configuration
 * property or a component scan is broken — the kind that only shows up at startup. In a
 * multi-module build it also proves that component scanning actually reaches the other modules'
 * packages, which ship as separate jars.
 */
@SpringBootTest
@ActiveProfiles("test")
class IncidentReportApplicationTests {

    @Test
    void contextLoads() {
        // Fails if the application context cannot be built.
    }
}
