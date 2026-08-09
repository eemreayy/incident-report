package com.emreay.incidentreport.analysis.catalog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

/**
 * Loads the catalog once, at startup.
 *
 * <p>The location is a property so a test — or an operator trying a different catalog — can point
 * it elsewhere without repackaging. The default lives in this module's own resources, because the
 * catalog belongs to the module that reads texts with it, not to the module that assembles the
 * application.
 */
@Configuration
public class IncidentCatalogConfiguration {

    private static final Logger log = LoggerFactory.getLogger(IncidentCatalogConfiguration.class);

    @Bean
    public IncidentCatalog incidentCatalog(
            @Value("${incident-report.analysis.catalog:classpath:incident-catalog.yml}") Resource catalog) {

        IncidentCatalog loaded = new IncidentCatalogLoader().load(catalog);
        log.info("loaded event type catalog from {}: {} event types {}",
                catalog.getDescription(), loaded.size(),
                loaded.eventTypes().stream().map(EventTypeDefinition::key).toList());
        return loaded;
    }
}
