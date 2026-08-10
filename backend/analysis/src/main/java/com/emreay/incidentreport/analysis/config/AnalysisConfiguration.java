package com.emreay.incidentreport.analysis.config;

import java.time.ZoneId;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.emreay.incidentreport.analysis.domain.Province;
import com.emreay.incidentreport.analysis.extraction.ProvinceExtractor;
import com.emreay.incidentreport.analysis.repository.ProvinceRepository;
import com.emreay.incidentreport.analysis.text.TurkishTextNormalizer;

/** Module-wide beans for {@code analysis}. */
@Configuration
public class AnalysisConfiguration {

    /**
     * The zone that decides which calendar day a submission belongs to (ADR-029).
     *
     * <p>Not the same question as what time it is. Timestamps stay UTC instants; this only governs
     * the day a report is filed under, which is the day a user sees on a chart. A report filed at
     * 01:30 in Istanbul is read here as that day, not the one before, which is what UTC would have
     * made of it.
     *
     * <p>Configurable, but with a default, so a fresh clone behaves correctly with no configuration
     * — and so an invalid zone fails at startup rather than on the first report.
     */
    @Bean
    ZoneId reportingZone(@Value("${incident-report.analysis.reporting-zone:Europe/Istanbul}") String zone) {
        return ZoneId.of(zone);
    }

    /**
     * The province recogniser, built from the reference data rather than from a list in the code.
     *
     * <p>Read once at startup: the 81 provinces only change through a migration, which means a
     * deployment anyway. Building it here also means an empty province table stops the application
     * immediately, instead of every report quietly coming back with no province at all.
     */
    @Bean
    ProvinceExtractor provinceExtractor(ProvinceRepository provinces, TurkishTextNormalizer normalizer) {
        Map<Short, String> reference = provinces.findAll().stream()
                .collect(Collectors.toMap(Province::getCode, Province::getName));
        return new ProvinceExtractor(reference, normalizer);
    }
}
