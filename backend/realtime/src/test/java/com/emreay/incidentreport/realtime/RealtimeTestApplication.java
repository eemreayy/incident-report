package com.emreay.incidentreport.realtime;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Bootstrap for this module's slice tests.
 *
 * <p>Slice annotations look for a {@code @SpringBootConfiguration} in the package hierarchy, and
 * the real one lives in the {@code app} module, which is not on this module's classpath — by
 * design (ADR-001). Test scope, so it never reaches production or another module.
 */
@SpringBootApplication
public class RealtimeTestApplication {
}
