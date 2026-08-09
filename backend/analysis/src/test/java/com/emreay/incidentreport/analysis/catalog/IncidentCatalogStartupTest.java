package com.emreay.incidentreport.analysis.catalog;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A catalog that cannot be trusted must stop the application, not degrade it.
 *
 * <p>{@link IncidentCatalogLoaderTest} proves the rules reject a bad file; this proves the rejection
 * actually reaches startup rather than being caught and logged somewhere. The distinction matters:
 * an application that starts with half a catalog recognises less than it is configured to, silently,
 * and every report it fails to classify looks like a genuine gap in the catalog.
 *
 * <p>Needs no database and no containers — the catalog is loaded from configuration, so this runs
 * in milliseconds and keeps working when Docker does not.
 */
class IncidentCatalogStartupTest {

    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of())
            .withUserConfiguration(IncidentCatalogConfiguration.class);

    @Test
    void theApplicationStartsWithTheCatalogItShipsWith() {
        context.run(loaded -> {
            assertThat(loaded).hasNotFailed();
            assertThat(loaded).hasSingleBean(IncidentCatalog.class);
            assertThat(loaded.getBean(IncidentCatalog.class).eventTypes())
                    .extracting(EventTypeDefinition::key)
                    .contains("EPIDEMIC", "EARTHQUAKE", "TRAFFIC_ACCIDENT");
        });
    }

    @Test
    void aBrokenCatalogStopsStartupAndSaysWhat() {
        context.withPropertyValues(
                        "incident-report.analysis.catalog=classpath:catalog/broken-duplicate-key.yml")
                // Asserted on the whole chain rather than the root cause: whether the loader's own
                // exception is the deepest one depends on what it wrapped, and that is not the
                // point being made here.
                .run(loaded -> assertThat(loaded)
                        .hasFailed()
                        .getFailure()
                        .hasStackTraceContaining(InvalidCatalogException.class.getName())
                        .hasStackTraceContaining("repeats a key")
                        .hasStackTraceContaining("silently ignored"));
    }

    /**
     * Pointing at a file that is not there is a deployment mistake, and it has to be as loud as a
     * malformed one — starting with no catalog at all would classify nothing and blame the texts.
     */
    @Test
    void aMissingCatalogStopsStartupToo() {
        context.withPropertyValues("incident-report.analysis.catalog=classpath:no-such-catalog.yml")
                .run(loaded -> assertThat(loaded)
                        .hasFailed()
                        .getFailure()
                        .hasStackTraceContaining(InvalidCatalogException.class.getName())
                        .hasStackTraceContaining("could not be read"));
    }

    /** The location is a property so an operator can try a different catalog without repackaging. */
    @Test
    void theCatalogLocationCanBePointedElsewhere() {
        context.withPropertyValues("incident-report.analysis.catalog=classpath:catalog/minimal.yml")
                .run(loaded -> {
                    assertThat(loaded).hasNotFailed();
                    assertThat(loaded.getBean(IncidentCatalog.class).eventTypes())
                            .extracting(EventTypeDefinition::key).containsExactly("EPIDEMIC");
                });
    }
}
