package com.emreay.incidentreport.analysis.repository;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.emreay.incidentreport.analysis.catalog.IncidentCatalog;
import com.emreay.incidentreport.analysis.catalog.IncidentCatalogLoader;
import com.emreay.incidentreport.analysis.domain.DateSource;
import com.emreay.incidentreport.analysis.domain.Incident;
import com.emreay.incidentreport.analysis.domain.Province;
import com.emreay.incidentreport.analysis.domain.ProvinceScope;
import com.emreay.incidentreport.analysis.extraction.CatalogIncidentExtractor;
import com.emreay.incidentreport.analysis.extraction.DateResolver;
import com.emreay.incidentreport.analysis.extraction.EventTypeClassifier;
import com.emreay.incidentreport.analysis.extraction.ProvinceExtractor;
import com.emreay.incidentreport.analysis.service.AnalysisOutcome;
import com.emreay.incidentreport.analysis.service.AnalysisService;
import com.emreay.incidentreport.analysis.text.NumberExtractor;
import com.emreay.incidentreport.analysis.text.SentenceSplitter;
import com.emreay.incidentreport.analysis.text.TurkishTextNormalizer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The third source example, all the way to the tables (PRD §11).
 *
 * <p>{@link com.emreay.incidentreport.analysis.extraction.GoldenExampleTest} proves the extraction;
 * this proves it survives being stored, which is a separate claim. The grain from ADR-019 only
 * really exists if the database holds it: three rows for one report, a province on two of them and
 * none on the third, the shared provinces in their own table, and the metrics as rows rather than
 * columns (ADR-020).
 *
 * <p>The third example is the one worth taking this far. The other two are a single record each.
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers
class GoldenPersistenceTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    private static final String REPORT_ID = "6a78ad14f6fe3fa987f9ffc5";

    /** 12:00 in Istanbul, so the calendar day is not in question here (ADR-029). */
    private static final Instant SUBMITTED_AT = Instant.parse("2020-06-15T09:00:00Z");
    private static final LocalDate SUBMITTED_ON = LocalDate.of(2020, 6, 15);

    private static final String TEXT =
            "Son 24 saatte Bursa'da 8, Kocaeli'nde 6 trafik kazası meydana geldi. "
                    + "Bursa'da 1, Kocaeli'nde ise 2 kişi kazalarda hayatını kaybetti. "
                    + "Her iki ilde toplam 10 kişi yaralı olarak hastaneye kaldırıldı.";

    @Autowired
    private IncidentRepository incidents;

    @Autowired
    private ProvinceRepository provinces;

    @Autowired
    private AnalysisResultRepository results;

    private AnalysisService service;

    @BeforeEach
    void setUp() {
        TurkishTextNormalizer normalizer = new TurkishTextNormalizer(new SentenceSplitter());
        IncidentCatalog catalog = new IncidentCatalogLoader().load(new ClassPathResource("incident-catalog.yml"));
        CatalogIncidentExtractor extractor = new CatalogIncidentExtractor(
                new DateResolver(),
                new ProvinceExtractor(provinces.findAll().stream()
                        .collect(Collectors.toMap(Province::getCode, Province::getName)), normalizer),
                new EventTypeClassifier(catalog, normalizer),
                new NumberExtractor(),
                catalog,
                normalizer);

        // Announcements go nowhere here: what this test is about is what reaches PostgreSQL. The
        // stream has its own tests, in the module that owns it.
        service = new AnalysisService(extractor, incidents, provinces, results, event -> { },
                Clock.fixed(Instant.parse("2026-08-10T10:00:00Z"), ZoneOffset.UTC),
                normalizer, ZoneId.of("Europe/Istanbul"));
    }

    private List<Incident> analyseAndRead() {
        service.analyze(REPORT_ID, TEXT, SUBMITTED_AT);
        incidents.flush();
        return incidents.findAll();
    }

    @Test
    @DisplayName("one report becomes three records, one per province plus the shared total")
    void theGrainSurvivesStorage() {
        List<Incident> stored = analyseAndRead();

        assertThat(stored).hasSize(3);
        assertThat(stored).allSatisfy(incident -> {
            assertThat(incident.getRawReportId()).isEqualTo(REPORT_ID);
            assertThat(incident.getEventType()).isEqualTo("TRAFFIC_ACCIDENT");
            assertThat(incident.getOccurredOn()).isEqualTo(SUBMITTED_ON);
            assertThat(incident.getDateSource()).isEqualTo(DateSource.RELATIVE);
        });
    }

    @Test
    @DisplayName("each province keeps its own figures")
    void eachProvinceKeepsItsOwnFigures() {
        List<Incident> stored = analyseAndRead();

        assertThat(stored).filteredOn(incident -> incident.getProvince() != null
                        && "Bursa".equals(incident.getProvince().getName()))
                .singleElement()
                .satisfies(incident -> assertThat(metricsOf(incident))
                        .containsEntry("ACCIDENT_COUNT", 8)
                        .containsEntry("DEATH", 1));

        assertThat(stored).filteredOn(incident -> incident.getProvince() != null
                        && "Kocaeli".equals(incident.getProvince().getName()))
                .singleElement()
                .satisfies(incident -> assertThat(metricsOf(incident))
                        .containsEntry("ACCIDENT_COUNT", 6)
                        .containsEntry("DEATH", 2));
    }

    @Test
    @DisplayName("the shared total is stored against both provinces and neither's own total")
    void theSharedTotalIsStoredWithoutBeingSplit() {
        List<Incident> stored = analyseAndRead();

        assertThat(stored).filteredOn(incident -> incident.getProvinceScope() == ProvinceScope.SHARED)
                .singleElement()
                .satisfies(incident -> {
                    assertThat(incident.getProvince())
                            .as("a shared record may not carry a single province (ADR-019)")
                            .isNull();
                    assertThat(incident.getSharedProvinces()).extracting(Province::getName)
                            .containsExactlyInAnyOrder("Bursa", "Kocaeli");
                    assertThat(metricsOf(incident)).containsEntry("INJURED", 10);
                });

        assertThat(stored).filteredOn(incident -> incident.getProvinceScope() == ProvinceScope.SINGLE)
                .allSatisfy(incident -> assertThat(metricsOf(incident)).doesNotContainKey("INJURED"));
    }

    @Test
    @DisplayName("the injured are counted once across the whole report, not once per province")
    void theSharedTotalIsNotDoubleCounted() {
        int injured = analyseAndRead().stream()
                .mapToInt(incident -> metricsOf(incident).getOrDefault("INJURED", 0))
                .sum();

        assertThat(injured).isEqualTo(10);
    }

    @Test
    @DisplayName("re-analysing the same report replaces its records rather than doubling them")
    void reprocessingDoesNotAccumulate() {
        analyseAndRead();

        AnalysisOutcome second = service.analyze(REPORT_ID, TEXT, SUBMITTED_AT);
        incidents.flush();

        assertThat(second.incidentCount()).isEqualTo(3);
        assertThat(incidents.findAll()).hasSize(3);
        assertThat(incidents.findAll())
                .allSatisfy(incident -> assertThat(incident.getOccurredOn()).isEqualTo(SUBMITTED_ON));
    }

    private java.util.Map<String, Integer> metricsOf(Incident incident) {
        return incident.getMetrics().stream()
                .collect(Collectors.toMap(metric -> metric.getMetricType(), metric -> metric.getValue()));
    }
}
