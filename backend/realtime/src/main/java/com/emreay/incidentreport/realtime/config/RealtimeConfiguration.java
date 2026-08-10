package com.emreay.incidentreport.realtime.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Wiring this module needs regardless of who assembles it.
 *
 * <p>Scheduling is switched on here, and this is the only module that asks for it. The heartbeat is
 * the reason: something has to write to every open connection periodically, and the alternative —
 * an executor this module starts and stops itself — would mean hand-managing a thread pool and its
 * shutdown to avoid one annotation. Nothing else in the application schedules anything, so enabling
 * it has no other effect than making {@code @Scheduled} work.
 */
@Configuration
@EnableConfigurationProperties(RealtimeProperties.class)
@EnableScheduling
public class RealtimeConfiguration {
}
