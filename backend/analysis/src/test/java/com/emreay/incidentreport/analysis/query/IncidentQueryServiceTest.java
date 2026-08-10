package com.emreay.incidentreport.analysis.query;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.emreay.incidentreport.analysis.domain.AnalysisResult;
import com.emreay.incidentreport.analysis.domain.AnalysisStatus;
import com.emreay.incidentreport.analysis.domain.ClassificationStatus;
import com.emreay.incidentreport.analysis.domain.DateSource;
import com.emreay.incidentreport.analysis.domain.Incident;
import com.emreay.incidentreport.analysis.domain.KeywordRole;
import com.emreay.incidentreport.analysis.domain.Province;
import com.emreay.incidentreport.analysis.repository.AnalysisResultRepository;
import com.emreay.incidentreport.analysis.repository.IncidentRepository;
import com.emreay.incidentreport.analysis.repository.ProvinceRepository;
import com.emreay.incidentreport.analysis.web.IncidentResponse;
import com.emreay.incidentreport.analysis.web.ProvinceResponse;
import com.emreay.incidentreport.shared.error.DomainValidationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Filtering, against a real PostgreSQL because the interesting parts are joins.
 *
 * <p>The one worth reading is {@link #aSharedFigureIsReturnedOnce}: a figure belonging to several
 * provinces at once has to be visible when any of them is filtered for, and exactly once when
 * several are — which is a property of the query, not of the data (ADR-019).
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers
@Import(IncidentQueryService.class)
class IncidentQueryServiceTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    private static final String REPORT = "6a78ad14f6fe3fa987f9ff01";
    private static final String OTHER_REPORT = "6a78ad14f6fe3fa987f9ff02";

    private static final Pageable FIRST_PAGE =
            PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "occurredOn", "id"));

    @Autowired
    private IncidentQueryService service;

    @Autowired
    private IncidentRepository incidents;

    @Autowired
    private ProvinceRepository provinces;

    @Autowired
    private AnalysisResultRepository results;

    private Province bursa;
    private Province kocaeli;
    private Province ankara;

    @BeforeEach
    void setUp() {
        bursa = provinces.findByName("Bursa").orElseThrow();
        kocaeli = provinces.findByName("Kocaeli").orElseThrow();
        ankara = provinces.findByName("Ankara").orElseThrow();

        Incident inBursa = Incident.forProvince(REPORT, LocalDate.of(2020, 4, 20), DateSource.EXPLICIT,
                bursa, "TRAFFIC_ACCIDENT", ClassificationStatus.CLASSIFIED);
        inBursa.addMetric("ACCIDENT_COUNT", 8);
        inBursa.addKeyword("trafik kazası", KeywordRole.EVENT_TYPE, 10, 23);

        Incident inKocaeli = Incident.forProvince(REPORT, LocalDate.of(2020, 4, 20), DateSource.EXPLICIT,
                kocaeli, "TRAFFIC_ACCIDENT", ClassificationStatus.CLASSIFIED);
        inKocaeli.addMetric("ACCIDENT_COUNT", 6);

        Incident shared = Incident.sharedAcross(REPORT, LocalDate.of(2020, 4, 20), DateSource.EXPLICIT,
                Set.of(bursa, kocaeli), "TRAFFIC_ACCIDENT", ClassificationStatus.CLASSIFIED);
        shared.addMetric("INJURED", 10);

        Incident epidemic = Incident.forProvince(OTHER_REPORT, LocalDate.of(2020, 5, 3), DateSource.RELATIVE,
                ankara, "EPIDEMIC", ClassificationStatus.CLASSIFIED);
        epidemic.addMetric("NEW_CASE", 15);
        epidemic.addKeyword("vaka", KeywordRole.EVENT_TYPE, 5, 9);

        incidents.saveAll(List.of(inBursa, inKocaeli, shared, epidemic));
        incidents.flush();
    }

    private List<IncidentResponse> find(IncidentQuery query) {
        return service.find(query, FIRST_PAGE).getContent();
    }

    private IncidentQuery query(List<String> types, List<Short> provinceCodes,
                                LocalDate from, LocalDate to, String keyword, String rawReportId) {
        return IncidentQuery.of(types, provinceCodes, from, to, keyword, rawReportId);
    }

    @Test
    @DisplayName("no filters returns everything")
    void unfiltered() {
        assertThat(find(query(null, null, null, null, null, null))).hasSize(4);
    }

    @Test
    @DisplayName("by event type")
    void byEventType() {
        assertThat(find(query(List.of("EPIDEMIC"), null, null, null, null, null)))
                .singleElement()
                .satisfies(incident -> assertThat(incident.eventType()).isEqualTo("EPIDEMIC"));
    }

    @Test
    @DisplayName("by date range, inclusive at both ends")
    void byDateRange() {
        assertThat(find(query(null, null, LocalDate.of(2020, 4, 20), LocalDate.of(2020, 4, 20),
                null, null))).hasSize(3);
        assertThat(find(query(null, null, LocalDate.of(2020, 5, 3), null, null, null))).hasSize(1);
        assertThat(find(query(null, null, null, LocalDate.of(2020, 4, 21), null, null))).hasSize(3);
    }

    @Test
    @DisplayName("by keyword, matched against what the extractor recorded")
    void byKeyword() {
        assertThat(find(query(null, null, null, null, "kaza", null))).hasSize(1);
        assertThat(find(query(null, null, null, null, "vaka", null))).hasSize(1);
        assertThat(find(query(null, null, null, null, "yok böyle bir kelime", null))).isEmpty();
    }

    @Test
    @DisplayName("by keyword, ignoring case")
    void keywordIsCaseInsensitive() {
        assertThat(find(query(null, null, null, null, "TRAFİK", null)))
                .as("a filter that only matches the exact casing is a filter nobody can use")
                .hasSize(1);
    }

    @Test
    @DisplayName("a province filter also returns the figures shared with that province")
    void aProvinceFilterSeesSharedFigures() {
        List<IncidentResponse> found = find(query(null, List.of(bursa.getCode()), null, null, null, null));

        assertThat(found).hasSize(2);
        assertThat(found).filteredOn(incident -> incident.province() != null)
                .singleElement()
                .satisfies(incident -> assertThat(incident.province().code()).isEqualTo(bursa.getCode()));
        assertThat(found).filteredOn(incident -> incident.province() == null)
                .singleElement()
                .satisfies(incident -> assertThat(incident.sharedAcross())
                        .extracting(ProvinceResponse::code).contains(bursa.getCode()));
    }

    @Test
    @DisplayName("selecting both provinces returns their shared figure once, not twice")
    void aSharedFigureIsReturnedOnce() {
        // The join through the link table matches this record twice, once per selected province.
        // Without DISTINCT the figure appears twice and any total built on it is doubled.
        List<IncidentResponse> found = find(query(null, List.of(bursa.getCode(), kocaeli.getCode()),
                null, null, null, null));

        assertThat(found).hasSize(3);
        assertThat(found).filteredOn(incident -> incident.province() == null)
                .hasSize(1);
    }

    @Test
    @DisplayName("filters combine")
    void filtersCombine() {
        assertThat(find(query(List.of("TRAFFIC_ACCIDENT"), List.of(kocaeli.getCode()),
                LocalDate.of(2020, 4, 1), LocalDate.of(2020, 4, 30), null, REPORT))).hasSize(2);

        assertThat(find(query(List.of("EPIDEMIC"), List.of(kocaeli.getCode()),
                null, null, null, null)))
                .as("an event type and a province that never occur together")
                .isEmpty();
    }

    @Test
    @DisplayName("by raw report — the only way to find out what a submission produced")
    void byRawReportId() {
        assertThat(find(query(null, null, null, null, null, REPORT))).hasSize(3);
        assertThat(find(query(null, null, null, null, null, OTHER_REPORT))).hasSize(1);
        assertThat(find(query(null, null, null, null, null, "6a78ad14f6fe3fa987f9ff99"))).isEmpty();
    }

    @Test
    @DisplayName("a report's analysis outcome comes back with its records")
    void theOutcomeAccompaniesTheRecords() {
        results.save(AnalysisResult.analyzed(REPORT, Instant.parse("2020-04-20T10:00:00Z"), 3,
                List.of("bir uyarı")));
        results.flush();

        assertThat(service.outcomeFor(query(null, null, null, null, null, REPORT)))
                .hasValueSatisfying(outcome -> {
                    assertThat(outcome.status()).isEqualTo(AnalysisStatus.ANALYZED);
                    assertThat(outcome.warnings()).containsExactly("bir uyarı");
                });
    }

    @Test
    @DisplayName("a general listing carries no analysis outcome, because none would describe it")
    void aGeneralListingHasNoOutcome() {
        assertThat(service.outcomeFor(query(null, null, null, null, null, null))).isEmpty();
    }

    @Test
    @DisplayName("a failed analysis is still reported, with no records to show for it")
    void aFailedAnalysisIsVisible() {
        String failed = "6a78ad14f6fe3fa987f9ff03";
        results.save(AnalysisResult.failed(failed, Instant.parse("2020-04-20T10:00:00Z"), "boom"));
        results.flush();

        assertThat(find(query(null, null, null, null, null, failed))).isEmpty();
        assertThat(service.outcomeFor(query(null, null, null, null, null, failed)))
                .as("an empty list with no explanation is what this exists to prevent")
                .hasValueSatisfying(outcome ->
                        assertThat(outcome.status()).isEqualTo(AnalysisStatus.FAILED));
    }

    @Test
    @DisplayName("paging reports the total, so an empty page is not mistaken for no results")
    void pagingReportsTheTotal() {
        var page = service.find(query(null, null, null, null, null, null),
                PageRequest.of(1, 3, Sort.by(Sort.Direction.DESC, "occurredOn", "id")));

        assertThat(page.getTotalElements()).isEqualTo(4);
        assertThat(page.getTotalPages()).isEqualTo(2);
        assertThat(page.getContent()).hasSize(1);
    }

    @Test
    void anImpossibleDateRangeIsRejectedRatherThanReturningNothing() {
        // Rejected as the caller's mistake, not the server's: these dates come from a query string,
        // so an inverted range is something that was typed. As a plain IllegalArgumentException it
        // reached the caller as a 500 — the interface's own date pickers make it a click away.
        assertThatThrownBy(() -> query(null, null, LocalDate.of(2020, 5, 1), LocalDate.of(2020, 4, 1),
                null, null))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("is after the end date")
                .extracting(exception -> ((DomainValidationException) exception).getCode())
                .isEqualTo("query.date-range.invalid");
    }

    @Test
    void findOneReturnsTheRecordOrNothing() {
        Incident any = incidents.findAll().getFirst();

        assertThat(service.findOne(any.getId()))
                .hasValueSatisfying(found -> assertThat(found.id()).isEqualTo(any.getId()));
        assertThat(service.findOne(-1L)).isEmpty();
    }

    /**
     * The reason this service answers with responses rather than entities. With
     * {@code open-in-view: false} the session closes with the transaction, so an entity handed to a
     * controller loses its collections — which is a 500 in production and invisible in a test like
     * this one, where the transaction stays open around the assertions.
     */
    @Test
    @DisplayName("what comes back is fully loaded, with nothing left to initialise later")
    void resultsAreCompleteBeforeTheTransactionEnds() {
        IncidentResponse shared = find(query(null, null, null, null, null, REPORT)).stream()
                .filter(incident -> incident.province() == null)
                .findFirst()
                .orElseThrow();

        assertThat(shared.sharedAcross()).hasSize(2);
        assertThat(shared.metrics()).isNotEmpty();
    }
}
