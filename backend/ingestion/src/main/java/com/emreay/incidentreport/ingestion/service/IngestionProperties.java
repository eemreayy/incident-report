package com.emreay.incidentreport.ingestion.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Tunables for accepting raw reports.
 *
 * @param maxTextLength longest text the system accepts, in characters. A limit exists so a single
 *                      submission cannot exhaust memory during analysis; the value is a
 *                      configuration decision, not a domain rule, which is why it lives here and
 *                      not in the service.
 */
@ConfigurationProperties(prefix = "incident-report.ingestion")
public record IngestionProperties(@DefaultValue("10000") int maxTextLength) {
}
