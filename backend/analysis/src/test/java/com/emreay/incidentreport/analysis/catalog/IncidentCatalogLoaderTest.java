package com.emreay.incidentreport.analysis.catalog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The catalog is data an operator edits, and its failure modes are quiet ones: a duplicate key
 * shadows an entry, a type with no keywords can never match, a key longer than its column fails
 * hours later at insert time. Each would look like "the system does not recognise this text" —
 * indistinguishable from a genuine gap.
 *
 * <p>So every rule here is checked by breaking it and confirming the application refuses to start,
 * with a message that says what to fix.
 */
class IncidentCatalogLoaderTest {

    private final IncidentCatalogLoader loader = new IncidentCatalogLoader();

    /** A minimal valid catalog, used as the base every broken variant deviates from. */
    private static final String VALID = """
            event-types:
              - key: EPIDEMIC
                label: Salgın
                keywords: [salgın, vaka]
                metrics:
                  - key: NEW_CASE
                    label: Yeni vaka
                    keywords: [yeni vaka]
            """;

    @Test
    void readsTheCatalogThatShipsWithTheApplication() {
        IncidentCatalog catalog = loader.load(new ClassPathResource("incident-catalog.yml"));

        assertThat(catalog.eventTypes()).extracting(EventTypeDefinition::key)
                .containsExactly("EPIDEMIC", "EARTHQUAKE", "TRAFFIC_ACCIDENT", "FLOOD", "FIRE");
        assertThat(catalog.recognises("EARTHQUAKE")).isTrue();
        assertThat(catalog.recognises("VOLCANO")).isFalse();
    }

    /**
     * The three sample texts in the source document are the reason the first three types exist, so
     * the metrics they need have to be there before any extractor is written.
     */
    @Test
    void carriesTheMetricsTheSampleTextsNeed() {
        IncidentCatalog catalog = loader.load(new ClassPathResource("incident-catalog.yml"));

        assertThat(metricsOf(catalog, "EPIDEMIC")).contains("NEW_CASE", "DEATH", "RECOVERED");
        assertThat(metricsOf(catalog, "EARTHQUAKE"))
                .contains("DAMAGED_BUILDING", "DEATH", "RESCUED", "INJURED");
        assertThat(metricsOf(catalog, "TRAFFIC_ACCIDENT"))
                .contains("ACCIDENT_COUNT", "DEATH", "INJURED");
    }

    /** Order is the order choices are offered in, so it belongs to whoever edits the file. */
    @Test
    void keepsTheOrderTheFileWasWrittenIn() {
        IncidentCatalog catalog = loader.load(yaml("""
                event-types:
                  - key: ZEBRA
                    label: Zebra
                    keywords: [zebra]
                    metrics: [{key: COUNT, label: Sayı, keywords: [adet]}]
                  - key: ALPHA
                    label: Alfa
                    keywords: [alfa]
                    metrics: [{key: COUNT, label: Sayı, keywords: [adet]}]
                """));

        assertThat(catalog.eventTypes()).extracting(EventTypeDefinition::key)
                .containsExactly("ZEBRA", "ALPHA");
    }

    @Test
    void aValidCatalogLoadsCleanly() {
        assertThat(loader.load(yaml(VALID)).size()).isEqualTo(1);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @CsvSource(delimiter = '|', textBlock = """
            an empty file                 | ''                                  | defines no event types
            a file with no event types    | 'event-types: []'                   | defines no event types
            """)
    void refusesACatalogThatWouldRecogniseNothing(String description, String content, String expected) {
        assertThatThrownBy(() -> loader.load(yaml(content)))
                .isInstanceOf(InvalidCatalogException.class)
                .hasMessageContaining(expected);
    }

    @Test
    void refusesADuplicateEventType() {
        assertThatThrownBy(() -> loader.load(yaml(VALID + """
                  - key: EPIDEMIC
                    label: Salgın tekrar
                    keywords: [salgın]
                    metrics: [{key: NEW_CASE, label: Yeni vaka, keywords: [vaka]}]
                """)))
                .isInstanceOf(InvalidCatalogException.class)
                .hasMessageContaining("repeats a key")
                .hasMessageContaining("silently ignored");
    }

    /** A type nothing can trigger is dead weight that looks like a working entry. */
    @Test
    void refusesAnEventTypeWithNoKeywords() {
        assertThatThrownBy(() -> loader.load(yaml("""
                event-types:
                  - key: EPIDEMIC
                    label: Salgın
                    keywords: []
                    metrics: [{key: NEW_CASE, label: Yeni vaka, keywords: [vaka]}]
                """)))
                .isInstanceOf(InvalidCatalogException.class)
                .hasMessageContaining("no keywords");
    }

    @Test
    void refusesAnEventTypeThatCouldExtractNothing() {
        assertThatThrownBy(() -> loader.load(yaml("""
                event-types:
                  - key: EPIDEMIC
                    label: Salgın
                    keywords: [salgın]
                    metrics: []
                """)))
                .isInstanceOf(InvalidCatalogException.class)
                .hasMessageContaining("no metrics");
    }

    /**
     * The columns holding these keys are {@code varchar(48)}. Catching it here turns an insert
     * failure in the middle of analysing a report into a message before anything runs.
     */
    @Test
    void refusesAKeyTooLongForTheColumnThatStoresIt() {
        assertThatThrownBy(() -> loader.load(yaml("""
                event-types:
                  - key: %s
                    label: Uzun
                    keywords: [uzun]
                    metrics: [{key: COUNT, label: Sayı, keywords: [adet]}]
                """.formatted("A".repeat(49)))))
                .isInstanceOf(InvalidCatalogException.class)
                .hasMessageContaining("49 characters")
                .hasMessageContaining("holds 48");
    }

    @ParameterizedTest
    @CsvSource({"epidemic", "'Epidemic'", "'EPI-DEMIC'", "'1EPIDEMIC'"})
    void refusesAKeyThatIsNotUpperSnake(String key) {
        assertThatThrownBy(() -> loader.load(yaml("""
                event-types:
                  - key: %s
                    label: Etiket
                    keywords: [kelime]
                    metrics: [{key: COUNT, label: Sayı, keywords: [adet]}]
                """.formatted(key))))
                .isInstanceOf(InvalidCatalogException.class)
                .hasMessageContaining("UPPER_SNAKE");
    }

    /** Without a label the interface has nothing to show, and would fall back to the key. */
    @Test
    void refusesAnEntryWithNoLabel() {
        assertThatThrownBy(() -> loader.load(yaml("""
                event-types:
                  - key: EPIDEMIC
                    keywords: [salgın]
                    metrics: [{key: NEW_CASE, label: Yeni vaka, keywords: [vaka]}]
                """)))
                .isInstanceOf(InvalidCatalogException.class)
                .hasMessageContaining("no label");
    }

    /**
     * {@code OTHER} is what an unrecognised report is filed under — the absence of a match. Defining
     * it would suggest there are words meaning "unrecognised", and would make a real event type
     * collide with the fallback.
     */
    @Test
    void refusesToLetTheCatalogDefineTheReservedKey() {
        assertThatThrownBy(() -> loader.load(yaml("""
                event-types:
                  - key: OTHER
                    label: Diğer
                    keywords: [diğer]
                    metrics: [{key: COUNT, label: Sayı, keywords: [adet]}]
                """)))
                .isInstanceOf(InvalidCatalogException.class)
                .hasMessageContaining("reserved key OTHER");
    }

    /**
     * One metric key means one thing. A chart legend showing DEATH cannot say "Can kaybı" for an
     * earthquake and something else for an epidemic.
     */
    @Test
    void refusesTheSameMetricKeyWithDifferentLabels() {
        assertThatThrownBy(() -> loader.load(yaml("""
                event-types:
                  - key: EPIDEMIC
                    label: Salgın
                    keywords: [salgın]
                    metrics: [{key: DEATH, label: Vefat, keywords: [vefat]}]
                  - key: EARTHQUAKE
                    label: Deprem
                    keywords: [deprem]
                    metrics: [{key: DEATH, label: Can kaybı, keywords: [can kaybı]}]
                """)))
                .isInstanceOf(InvalidCatalogException.class)
                .hasMessageContaining("one metric key means one thing");
    }

    /** The same key with the same label in several types is the intended way to share a metric. */
    @Test
    void allowsTheSameMetricKeyWhenItMeansTheSameThing() {
        IncidentCatalog catalog = loader.load(yaml("""
                event-types:
                  - key: EPIDEMIC
                    label: Salgın
                    keywords: [salgın]
                    metrics: [{key: DEATH, label: Can kaybı, keywords: [vefat]}]
                  - key: EARTHQUAKE
                    label: Deprem
                    keywords: [deprem]
                    metrics: [{key: DEATH, label: Can kaybı, keywords: [can kaybı]}]
                """));

        assertThat(catalog.size()).isEqualTo(2);
    }

    @Test
    void refusesADuplicateMetricWithinOneEventType() {
        assertThatThrownBy(() -> loader.load(yaml("""
                event-types:
                  - key: EPIDEMIC
                    label: Salgın
                    keywords: [salgın]
                    metrics:
                      - {key: DEATH, label: Can kaybı, keywords: [vefat]}
                      - {key: DEATH, label: Can kaybı, keywords: [ölü]}
                """)))
                .isInstanceOf(InvalidCatalogException.class)
                .hasMessageContaining("defined twice");
    }

    @Test
    void refusesARepeatedKeyword() {
        assertThatThrownBy(() -> loader.load(yaml("""
                event-types:
                  - key: EPIDEMIC
                    label: Salgın
                    keywords: [salgın, salgın]
                    metrics: [{key: NEW_CASE, label: Yeni vaka, keywords: [vaka]}]
                """)))
                .isInstanceOf(InvalidCatalogException.class)
                .hasMessageContaining("more than once");
    }

    /**
     * Reporting one problem per restart is how a five-mistake file takes five restarts to fix.
     */
    @Test
    void reportsEveryProblemAtOnce() {
        assertThatThrownBy(() -> loader.load(yaml("""
                event-types:
                  - key: lowercase
                    keywords: []
                    metrics: []
                """)))
                .isInstanceOf(InvalidCatalogException.class)
                .hasMessageContaining("UPPER_SNAKE")
                .hasMessageContaining("no label")
                .hasMessageContaining("no keywords")
                .hasMessageContaining("no metrics");
    }

    @Test
    void saysWhichFileIsBrokenWhenItCannotBeParsed() {
        assertThatThrownBy(() -> loader.load(yaml("event-types: [ this is not: valid: yaml")))
                .isInstanceOf(InvalidCatalogException.class)
                .hasMessageContaining("could not be read");
    }

    @Test
    void aMissingFileFailsLoudlyRatherThanQuietlyLoadingNothing() {
        assertThatThrownBy(() -> loader.load(new ClassPathResource("no-such-catalog.yml")))
                .isInstanceOf(InvalidCatalogException.class);
    }

    private static Resource yaml(String content) {
        return new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8), "test catalog");
    }

    private static java.util.List<String> metricsOf(IncidentCatalog catalog, String eventTypeKey) {
        return catalog.eventType(eventTypeKey).orElseThrow().metrics().stream()
                .map(MetricDefinition::key).toList();
    }
}
