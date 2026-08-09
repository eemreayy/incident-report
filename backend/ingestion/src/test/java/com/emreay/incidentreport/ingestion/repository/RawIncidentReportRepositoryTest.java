package com.emreay.incidentreport.ingestion.repository;

import com.emreay.incidentreport.ingestion.domain.RawIncidentReport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the two guarantees this module exists to make: the submitted text comes back unchanged,
 * and the stored document holds nothing but what this module owns.
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
    private final MongoTemplate mongo;

    RawIncidentReportRepositoryTest(@Autowired RawIncidentReportRepository repository,
                                    @Autowired MongoTemplate mongo) {
        this.repository = repository;
        this.mongo = mongo;
    }

    @Test
    void storedTextComesBackByteForByte() {
        Instant submittedAt = Instant.parse("2026-08-09T07:15:30Z");

        RawIncidentReport saved = repository.save(RawIncidentReport.of(RAW_TEXT, submittedAt));
        RawIncidentReport reloaded = repository.findById(saved.id()).orElseThrow();

        assertThat(reloaded.rawText()).isEqualTo(RAW_TEXT);
        assertThat(reloaded.rawText().getBytes(StandardCharsets.UTF_8))
                .as("no re-encoding on the way through the driver")
                .isEqualTo(RAW_TEXT.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * The submission time is the reference date for relative and defaulted dates (ADR-014), so it
     * has to survive the round trip exactly — which is why it is truncated to the precision MongoDB
     * actually stores before it is written.
     */
    @Test
    void submissionTimeSurvivesTheRoundTripExactly() {
        Instant submittedAt = Instant.parse("2026-08-09T07:15:30.123456789Z");

        RawIncidentReport saved = repository.save(RawIncidentReport.of("kısa metin", submittedAt));

        assertThat(repository.findById(saved.id()).orElseThrow().submittedAt())
                .isEqualTo(saved.submittedAt());
    }

    /**
     * Nothing about how the text was read may end up here. An earlier version stored the analysis
     * status, its warnings and its timing on this document, which meant this module publishing data
     * another module owned — and a record that kept being written to after it was stored (ADR-021).
     */
    @Test
    void theStoredDocumentHoldsNothingButTheTextAndWhenItArrived() {
        RawIncidentReport saved = repository.save(
                RawIncidentReport.of(RAW_TEXT, Instant.parse("2026-08-09T07:15:30Z")));

        org.bson.Document stored = mongo.findOne(
                Query.query(Criteria.where("_id").is(new org.bson.types.ObjectId(saved.id()))),
                org.bson.Document.class, "raw_incident_reports");

        assertThat(stored).isNotNull();
        assertThat(stored.keySet())
                .as("no status, no warnings, no analyzedAt, no failureReason")
                .containsExactlyInAnyOrder("_id", "rawText", "submittedAt", "_class");
    }
}
