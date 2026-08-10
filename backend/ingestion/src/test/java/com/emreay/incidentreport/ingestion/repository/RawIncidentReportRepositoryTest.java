package com.emreay.incidentreport.ingestion.repository;

import com.emreay.incidentreport.ingestion.domain.RawIncidentReport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the two guarantees this module exists to make: the submitted text comes back unchanged,
 * and the stored document holds nothing but what this module owns.
 *
 * <p>Run against a real MongoDB, because the failure mode worth catching here is the driver or the
 * mapping quietly normalising something — trimming, re-encoding, collapsing whitespace. An
 * in-memory substitute would not reproduce that.
 */
@DataMongoTest
@Import(RawIncidentReportIndexes.class)
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

    /**
     * MongoDB has no transaction to roll back around a test, and several of these store the same
     * text. Since the collection now refuses a second report with that text (ADR-035), leaving the
     * previous test's document behind would fail the next one for the right reason at the wrong
     * time.
     */
    @BeforeEach
    void emptyTheCollection() {
        mongo.getCollection("raw_incident_reports").deleteMany(new org.bson.Document());
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
                .containsExactlyInAnyOrder("_id", "rawText", "textHash", "submittedAt", "_class");
    }

    // ---------------------------------------------------------------------
    // Repeated submissions (TC-9, ADR-035)
    // ---------------------------------------------------------------------

    @Test
    void aStoredTextCanBeFoundAgainByItsDigest() {
        RawIncidentReport saved = repository.save(
                RawIncidentReport.of(RAW_TEXT, Instant.parse("2026-08-09T07:15:30Z")));

        // Hashed the same way a fresh submission of the same text would be - the digest is not
        // read off the stored document, or this would prove nothing.
        String digestOfTheSameText = RawIncidentReport.of(RAW_TEXT, Instant.now()).textHash();

        assertThat(repository.findByTextHash(digestOfTheSameText))
                .map(RawIncidentReport::id)
                .contains(saved.id());
    }

    @Test
    void aTextThatDiffersByOneCharacterIsADifferentText() {
        repository.save(RawIncidentReport.of(RAW_TEXT, Instant.parse("2026-08-09T07:15:30Z")));

        String otherDigest = RawIncidentReport.of(RAW_TEXT + " ", Instant.now()).textHash();

        assertThat(repository.findByTextHash(otherDigest)).isEmpty();
    }

    /**
     * The guarantee behind the lookup, and the only thing that holds when two identical submissions
     * arrive at the same moment: without it both would find nothing, both would insert, and one
     * incident would be counted twice for ever.
     */
    @Test
    void theDatabaseItselfRefusesASecondReportWithTheSameText() {
        Instant submittedAt = Instant.parse("2026-08-09T07:15:30Z");
        repository.save(RawIncidentReport.of(RAW_TEXT, submittedAt));

        assertThatThrownBy(() -> repository.save(RawIncidentReport.of(RAW_TEXT, submittedAt.plusSeconds(60))))
                .isInstanceOf(DuplicateKeyException.class);

        assertThat(repository.count()).isOne();
    }

    /**
     * Reports written before the digest existed have no such field. A plain unique index would read
     * them all as sharing the value {@code null} and refuse to build; sparse exempts them, at the
     * price of leaving those older texts out of duplicate detection.
     */
    @Test
    void reportsWrittenBeforeTheDigestExistedDoNotBlockTheIndex() {
        mongo.getCollection("raw_incident_reports").insertMany(List.of(
                new org.bson.Document("rawText", "eski kayıt").append("submittedAt", Date.from(Instant.now())),
                new org.bson.Document("rawText", "başka eski kayıt").append("submittedAt", Date.from(Instant.now()))));

        assertThatNoException().isThrownBy(() -> mongo.indexOps("raw_incident_reports")
                .ensureIndex(new Index().on("textHash", Sort.Direction.ASC).unique().sparse()));
    }
}
