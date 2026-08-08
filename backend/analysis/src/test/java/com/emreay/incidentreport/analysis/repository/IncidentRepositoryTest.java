package com.emreay.incidentreport.analysis.repository;

import com.emreay.incidentreport.analysis.domain.ClassificationStatus;
import com.emreay.incidentreport.analysis.domain.DateSource;
import com.emreay.incidentreport.analysis.domain.Incident;
import com.emreay.incidentreport.analysis.domain.IncidentMetric;
import com.emreay.incidentreport.analysis.domain.KeywordRole;
import com.emreay.incidentreport.analysis.domain.Province;
import com.emreay.incidentreport.analysis.domain.ProvinceScope;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the record grain decided in ADR-019 against a real PostgreSQL.
 *
 * <p>A real database rather than an in-memory stand-in, because what is worth verifying here is
 * exactly what a stand-in gets wrong: that the Flyway migrations produce a schema the entities
 * validate against, and that the check constraints hold.
 *
 * <p>{@code ddl-auto=validate} is deliberate — if an entity and the migration ever disagree, this
 * test fails at startup rather than at runtime in production.
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers
class IncidentRepositoryTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    private static final String REPORT_ID = "652f1a2b3c4d5e6f70819200";
    private static final LocalDate WHEN = LocalDate.of(2020, 4, 20);

    private final IncidentRepository incidents;
    private final ProvinceRepository provinces;

    IncidentRepositoryTest(@Autowired IncidentRepository incidents, @Autowired ProvinceRepository provinces) {
        this.incidents = incidents;
        this.provinces = provinces;
    }

    @Test
    void migrationsLoadAllProvinces() {
        assertThat(provinces.count()).isEqualTo(81);
        assertThat(provinces.findByName("Bursa")).get().extracting(Province::getCode).isEqualTo((short) 16);
        assertThat(provinces.findByName("Kocaeli")).get().extracting(Province::getCode).isEqualTo((short) 41);
        // Dotted-i provinces are the ones a careless locale conversion mangles first.
        assertThat(provinces.findByName("İstanbul")).isPresent();
        assertThat(provinces.findByName("İzmir")).isPresent();
    }

    /**
     * The third sample text from the source document, which is the reason the grain looks the way
     * it does: two provinces with their own numbers, plus one figure the text gives for both
     * together.
     */
    @Test
    void oneReportWithTwoProvincesAndASharedFigureBecomesThreeRecords() {
        Province bursa = province("Bursa");
        Province kocaeli = province("Kocaeli");

        incidents.saveAll(List.of(
                Incident.forProvince(REPORT_ID, WHEN, DateSource.RELATIVE, bursa,
                                "TRAFFIC_ACCIDENT", ClassificationStatus.CLASSIFIED)
                        .addMetric("ACCIDENT_COUNT", 8)
                        .addMetric("DEATH", 1),
                Incident.forProvince(REPORT_ID, WHEN, DateSource.RELATIVE, kocaeli,
                                "TRAFFIC_ACCIDENT", ClassificationStatus.CLASSIFIED)
                        .addMetric("ACCIDENT_COUNT", 6)
                        .addMetric("DEATH", 2),
                Incident.sharedAcross(REPORT_ID, WHEN, DateSource.RELATIVE, List.of(bursa, kocaeli),
                                "TRAFFIC_ACCIDENT", ClassificationStatus.CLASSIFIED)
                        .addMetric("INJURED", 10)));

        List<Incident> stored = incidents.findByRawReportIdOrderByIdAsc(REPORT_ID);
        assertThat(stored).hasSize(3);

        assertThat(metricsOf(stored.get(0))).containsExactlyInAnyOrderEntriesOf(
                Map.of("ACCIDENT_COUNT", 8, "DEATH", 1));
        assertThat(stored.get(0).getProvince().getName()).isEqualTo("Bursa");
        assertThat(stored.get(0).getProvinceScope()).isEqualTo(ProvinceScope.SINGLE);

        assertThat(metricsOf(stored.get(1))).containsExactlyInAnyOrderEntriesOf(
                Map.of("ACCIDENT_COUNT", 6, "DEATH", 2));
        assertThat(stored.get(1).getProvince().getName()).isEqualTo("Kocaeli");

        // The link back to the source text (FR-08). Without it a record cannot be explained, and
        // reprocessing has no way to find what it must rebuild.
        assertThat(stored).allSatisfy(incident -> {
            assertThat(incident.getRawReportId()).isEqualTo(REPORT_ID);
            assertThat(incident.getEventType()).isEqualTo("TRAFFIC_ACCIDENT");
            assertThat(incident.getId()).isNotNull();
            assertThat(incident.getCreatedAt()).isNotNull();
        });

        Incident shared = stored.get(2);
        assertThat(shared.getProvinceScope()).isEqualTo(ProvinceScope.SHARED);
        assertThat(shared.getProvince())
                .as("a shared figure belongs to no single province, or it would be counted as that province's own")
                .isNull();
        assertThat(shared.getSharedProvinces()).extracting(Province::getName)
                .containsExactlyInAnyOrder("Bursa", "Kocaeli");
        assertThat(metricsOf(shared)).containsExactlyEntriesOf(Map.of("INJURED", 10));
    }

    /**
     * The property the whole scope model exists to protect: totals add up, and the shared figure is
     * counted once rather than once per province it covers.
     */
    @Test
    void sharedFiguresAreNeitherDroppedNorDoubleCounted() {
        Province bursa = province("Bursa");
        Province kocaeli = province("Kocaeli");
        incidents.saveAll(List.of(
                Incident.forProvince(REPORT_ID, WHEN, DateSource.RELATIVE, bursa,
                        "TRAFFIC_ACCIDENT", ClassificationStatus.CLASSIFIED).addMetric("ACCIDENT_COUNT", 8),
                Incident.forProvince(REPORT_ID, WHEN, DateSource.RELATIVE, kocaeli,
                        "TRAFFIC_ACCIDENT", ClassificationStatus.CLASSIFIED).addMetric("ACCIDENT_COUNT", 6),
                Incident.sharedAcross(REPORT_ID, WHEN, DateSource.RELATIVE, List.of(bursa, kocaeli),
                        "TRAFFIC_ACCIDENT", ClassificationStatus.CLASSIFIED).addMetric("INJURED", 10)));

        List<Incident> all = incidents.findByRawReportIdOrderByIdAsc(REPORT_ID);

        assertThat(sum(all, "ACCIDENT_COUNT")).isEqualTo(14);
        assertThat(sum(all, "INJURED")).as("counted once in total, not once per covered province").isEqualTo(10);

        List<Incident> bursaOwn = all.stream()
                .filter(i -> i.getProvinceScope() == ProvinceScope.SINGLE)
                .filter(i -> i.getProvince().getName().equals("Bursa"))
                .toList();
        assertThat(sum(bursaOwn, "INJURED"))
                .as("the shared 10 must never land in a single province's own figures")
                .isZero();
    }

    @Test
    void aFigureSharedWithNobodyIsNotShared() {
        assertThatThrownBy(() -> Incident.sharedAcross(REPORT_ID, WHEN, DateSource.RELATIVE,
                List.of(province("Bursa")), "TRAFFIC_ACCIDENT", ClassificationStatus.CLASSIFIED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least two provinces");
    }

    @Test
    void unclassifiedReportsKeepWhateverCouldStillBeExtracted() {
        Incident saved = incidents.save(
                Incident.forProvince(REPORT_ID, WHEN, DateSource.EXPLICIT, province("Ankara"),
                                "OTHER", ClassificationStatus.UNCLASSIFIED)
                        .addKeyword("kar yağışı", KeywordRole.EVENT_TYPE, 12, 22));

        assertThat(saved.getClassification()).isEqualTo(ClassificationStatus.UNCLASSIFIED);
        assertThat(saved.getOccurredOn()).isEqualTo(WHEN);
        assertThat(saved.getProvince().getName()).isEqualTo("Ankara");

        // FR-17: the user has to be able to see *why* the system decided what it decided, and
        // where in the text it looked.
        assertThat(saved.getKeywords()).singleElement().satisfies(keyword -> {
            assertThat(keyword.getKeyword()).isEqualTo("kar yağışı");
            assertThat(keyword.getRole()).isEqualTo(KeywordRole.EVENT_TYPE);
            assertThat(keyword.getCharStart()).isEqualTo(12);
            assertThat(keyword.getCharEnd()).isEqualTo(22);
        });
    }

    /**
     * Logging an entity is the single most common way a lazy association explodes, and it does so
     * from inside the logging call — the worst place to be debugging. {@code toString} must stay
     * safe outside a session, so it is not allowed to touch province or shared provinces.
     */
    @Test
    void toStringDoesNotReachIntoLazyAssociations() {
        Incident single = Incident.forProvince(REPORT_ID, WHEN, DateSource.EXPLICIT, province("Bursa"),
                "TRAFFIC_ACCIDENT", ClassificationStatus.CLASSIFIED);
        Incident shared = Incident.sharedAcross(REPORT_ID, WHEN, DateSource.RELATIVE,
                List.of(province("Bursa"), province("Kocaeli")), "TRAFFIC_ACCIDENT",
                ClassificationStatus.CLASSIFIED);
        Incident unknown = Incident.withoutProvince(REPORT_ID, WHEN, DateSource.DEFAULTED,
                "EPIDEMIC", ClassificationStatus.CLASSIFIED);

        assertThat(single.toString()).contains("TRAFFIC_ACCIDENT", "SINGLE").doesNotContain("Bursa");
        assertThat(shared.toString()).contains("SHARED").doesNotContain("Kocaeli");
        assertThat(unknown.toString()).contains("EPIDEMIC", "UNKNOWN");
    }

    @Test
    void aReportWithNoProvinceIsStillRecorded() {
        Incident saved = incidents.save(
                Incident.withoutProvince(REPORT_ID, WHEN, DateSource.DEFAULTED,
                        "EPIDEMIC", ClassificationStatus.CLASSIFIED).addMetric("NEW_CASE", 15));

        assertThat(saved.getProvinceScope()).isEqualTo(ProvinceScope.UNKNOWN);
        assertThat(saved.getProvince()).isNull();
        assertThat(saved.getSharedProvinces()).isEmpty();
        assertThat(saved.getDateSource()).isEqualTo(DateSource.DEFAULTED);
    }

    /** Reprocessing rebuilds from the immutable raw text, so a second run must not duplicate rows. */
    @Test
    void deletingByReportIdClearsEverythingDerivedFromIt() {
        incidents.save(Incident.forProvince(REPORT_ID, WHEN, DateSource.EXPLICIT, province("İzmir"),
                "EARTHQUAKE", ClassificationStatus.CLASSIFIED).addMetric("DAMAGED_BUILDING", 12));
        incidents.save(Incident.forProvince("652f1a2b3c4d5e6f70819299", WHEN, DateSource.EXPLICIT,
                province("Ankara"), "EPIDEMIC", ClassificationStatus.CLASSIFIED).addMetric("NEW_CASE", 15));

        long removed = incidents.deleteByRawReportId(REPORT_ID);

        assertThat(removed).isEqualTo(1);
        assertThat(incidents.findByRawReportIdOrderByIdAsc(REPORT_ID)).isEmpty();
        assertThat(incidents.findByRawReportIdOrderByIdAsc("652f1a2b3c4d5e6f70819299"))
                .as("other reports are untouched")
                .hasSize(1);
    }

    private Province province(String name) {
        return provinces.findByName(name).orElseThrow(() -> new AssertionError("missing province " + name));
    }

    private static Map<String, Integer> metricsOf(Incident incident) {
        return incident.getMetrics().stream()
                .collect(Collectors.toMap(IncidentMetric::getMetricType, IncidentMetric::getValue));
    }

    private static int sum(List<Incident> incidents, String metricType) {
        return incidents.stream()
                .flatMap(i -> i.getMetrics().stream())
                .filter(m -> m.getMetricType().equals(metricType))
                .mapToInt(IncidentMetric::getValue)
                .sum();
    }
}
