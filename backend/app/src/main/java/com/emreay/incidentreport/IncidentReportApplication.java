package com.emreay.incidentreport;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the incident reporting backend.
 *
 * <p>The application is a modular monolith built as a Maven multi-module project. Each module is a
 * separate artifact with its own pom, and the module graph is the architecture boundary:
 *
 * <pre>
 *   shared     &lt;- depends on no other module
 *   ingestion  -&gt; shared          owns MongoDB
 *   analysis   -&gt; shared          owns PostgreSQL
 *   realtime   -&gt; shared          SSE transport
 *   app        -&gt; all of the above
 * </pre>
 *
 * <p>There is no edge between {@code ingestion} and {@code analysis}; they communicate through
 * domain events declared in {@code shared}. Because that edge does not exist in the build, reaching
 * across it is a compile error rather than a convention. See {@code CLAUDE.md}.
 *
 * <p>This class sits in the root package so component scanning reaches every module's package,
 * even though they ship as separate jars.
 */
@SpringBootApplication
public class IncidentReportApplication {

    public static void main(String[] args) {
        SpringApplication.run(IncidentReportApplication.class, args);
    }
}
