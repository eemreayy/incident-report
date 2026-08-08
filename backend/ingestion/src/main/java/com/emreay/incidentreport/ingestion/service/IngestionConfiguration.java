package com.emreay.incidentreport.ingestion.service;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Wiring this module needs regardless of who assembles it.
 *
 * <p>Keeping the property binding here rather than relying on a scan in {@code app} means the
 * module carries its own configuration: a slice test can import this class and get the same
 * behaviour as the running application.
 */
@Configuration
@EnableConfigurationProperties(IngestionProperties.class)
public class IngestionConfiguration {
}
