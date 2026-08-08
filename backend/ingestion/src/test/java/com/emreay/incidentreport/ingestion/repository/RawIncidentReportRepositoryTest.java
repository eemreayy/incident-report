package com.emreay.incidentreport.ingestion.repository;

import com.emreay.incidentreport.ingestion.domain.ProcessingStatus;
import com.emreay.incidentreport.ingestion.domain.RawIncidentReport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the one guarantee this module exists to make: the submitted text comes back unchanged.
 *
 * <p>Run against a real MongoDB, because the failure mode worth catching here is the driver or the
 * mapping quietly normalising something — trimming, re-encoding, collapsing whitespace. An
 * in-memory substitute would not reproduce that.
 */
@DataMongoTest
@ActiveProfiles("test")
@Testcontainers
class RawIncidentReportRepositoryTest {

    @Container
    @ServiceConnection
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:8");

    /**
     * Turkish characters, an apostrophe suffix, mixed newlines and padding — everything a helpful
     * normalisation step would be tempted to touch.
     */
    private static final String RAW_TEXT = """
              20.04.2020 tarihinde Ankara'da sağlık yetkilileri tarafından yapılan açıklamada,
            salgın kapsamında yapılan testlerde 15 yeni vaka tespit edildi.   1 kişi vefat etti.
            İZMİR'de ise 5 kişi tedavi sonrası taburcu edildi.\s
            """;

    private final RawIncidentReportRepository repository;

    RawIncidentReportRepositoryTest(@Autowired RawIncidentReportRepository repository) {
        this.repository = repository;
    }

    @Test
    void storedTextComesBackByteForByte() {
        Instant submittedAt = Instant.parse("2026-08-09T07:15:30Z");

        RawIncidentReport saved = repository.save(RawIncidentReport.received(RAW_TEXT, submittedAt));
        RawIncidentReport reloaded = repository.findById(saved.id()).orElseThrow();

        assertThat(reloaded.rawText()).isEqualTo(RAW_TEXT);
        assertThat(reloaded.rawText().getBytes(StandardCharsets.UTF_8))
                .as("no re-encoding on the way through the driver")
                .isEqualTo(RAW_TEXT.getBytes(StandardCharsets.UTF_8));
        assertThat(reloaded.status()).isEqualTo(ProcessingStatus.RECEIVED);
        assertThat(reloaded.warnings()).isEmpty();
        assertThat(reloaded.analyzedAt()).isNull();
    }

    /**
     * The submission time is the reference date for relative and defaulted dates (ADR-014), so it
     * has to survive the round trip precisely enough to identify a day.
     */
    @Test
    void submissionTimeSurvivesTheRoundTrip() {
        Instant submittedAt = Instant.parse("2026-08-09T07:15:30Z");

        RawIncidentReport saved = repository.save(RawIncidentReport.received("kısa metin", submittedAt));

        assertThat(repository.findById(saved.id()).orElseThrow().submittedAt())
                .isEqualTo(submittedAt.truncatedTo(ChronoUnit.MILLIS));
    }

    @Test
    void markingAnalysedLeavesTheTextAndSubmissionTimeAlone() {
        RawIncidentReport stored = repository.save(
                RawIncidentReport.received(RAW_TEXT, Instant.parse("2026-08-09T07:15:30Z")));

        RawIncidentReport analysed = repository.save(
                stored.analyzed(Instant.parse("2026-08-09T07:15:31Z"), List.of("event type not recognised")));

        assertThat(analysed.id()).isEqualTo(stored.id());
        assertThat(analysed.rawText()).isEqualTo(stored.rawText());
        assertThat(analysed.submittedAt()).isEqualTo(stored.submittedAt());
        assertThat(analysed.status()).isEqualTo(ProcessingStatus.ANALYZED);
        assertThat(analysed.warnings()).containsExactly("event type not recognised");
    }

    /** A failed analysis must never cost us the text — that is the point of storing it first. */
    @Test
    void markingFailedKeepsTheText() {
        RawIncidentReport stored = repository.save(
                RawIncidentReport.received(RAW_TEXT, Instant.parse("2026-08-09T07:15:30Z")));

        repository.save(stored.failed(Instant.parse("2026-08-09T07:15:31Z"), "province extractor blew up"));

        RawIncidentReport reloaded = repository.findById(stored.id()).orElseThrow();
        assertThat(reloaded.rawText()).isEqualTo(RAW_TEXT);
        assertThat(reloaded.status()).isEqualTo(ProcessingStatus.FAILED);
        assertThat(reloaded.failureReason()).isEqualTo("province extractor blew up");
    }

    /** Failed reports are what a reprocess run picks up (FR-15). */
    @Test
    void failedReportsCanBeListed() {
        Instant now = Instant.parse("2026-08-09T07:15:30Z");
        RawIncidentReport ok = repository.save(RawIncidentReport.received("iyi metin", now));
        repository.save(ok.analyzed(now, List.of()));
        RawIncidentReport broken = repository.save(RawIncidentReport.received("bozuk metin", now));
        repository.save(broken.failed(now, "nedeni önemli değil"));

        assertThat(repository.findByStatus(ProcessingStatus.FAILED, PageRequest.of(0, 10)))
                .singleElement()
                .satisfies(report -> assertThat(report.rawText()).isEqualTo("bozuk metin"));
    }

    /** A record has no setters, so "changing" a report can only ever produce a separate instance. */
    @Test
    void transitionsProduceNewInstancesRatherThanMutating() {
        RawIncidentReport received = RawIncidentReport.received(RAW_TEXT, Instant.parse("2026-08-09T07:15:30Z"));

        RawIncidentReport analysed = received.analyzed(Instant.parse("2026-08-09T07:15:31Z"), List.of());

        assertThat(received.status()).isEqualTo(ProcessingStatus.RECEIVED);
        assertThat(analysed).isNotSameAs(received);
        assertThat(analysed.rawText()).isSameAs(received.rawText());
    }
}
