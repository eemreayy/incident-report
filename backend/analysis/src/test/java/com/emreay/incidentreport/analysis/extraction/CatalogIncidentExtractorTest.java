package com.emreay.incidentreport.analysis.extraction;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import com.emreay.incidentreport.analysis.catalog.IncidentCatalog;
import com.emreay.incidentreport.analysis.catalog.IncidentCatalogLoader;
import com.emreay.incidentreport.analysis.domain.ClassificationStatus;
import com.emreay.incidentreport.analysis.domain.KeywordRole;
import com.emreay.incidentreport.analysis.domain.ProvinceScope;
import com.emreay.incidentreport.analysis.text.NumberExtractor;
import com.emreay.incidentreport.analysis.text.SentenceSplitter;
import com.emreay.incidentreport.analysis.text.TurkishTextNormalizer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The assembly, against the real catalog and the source document's own examples.
 *
 * <p>Everything up to here produced lists of things a text contains. This is where they become
 * records, and where the attribution rules either hold or quietly produce plausible nonsense.
 */
class CatalogIncidentExtractorTest {

    private static final short ANKARA = 6;
    private static final short IZMIR = 35;
    private static final short BURSA = 16;
    private static final short KOCAELI = 41;

    private static final Map<Short, String> PROVINCES = Map.of(
            ANKARA, "Ankara", IZMIR, "İzmir", BURSA, "Bursa", KOCAELI, "Kocaeli");

    private static final LocalDate SUBMITTED_ON = LocalDate.of(2020, 6, 15);

    private final TurkishTextNormalizer normalizer = new TurkishTextNormalizer(new SentenceSplitter());
    private final IncidentCatalog catalog =
            new IncidentCatalogLoader().load(new ClassPathResource("incident-catalog.yml"));
    private final CatalogIncidentExtractor extractor = new CatalogIncidentExtractor(
            new DateResolver(),
            new ProvinceExtractor(PROVINCES, normalizer),
            new EventTypeClassifier(catalog, normalizer),
            new NumberExtractor(),
            catalog,
            normalizer);

    private ExtractionResult extract(String raw) {
        return extractor.extract(normalizer.normalize(raw), SUBMITTED_ON);
    }

    @Test
    @DisplayName("source example 1 — an epidemic in Ankara, three metrics, one record")
    void firstExample() {
        ExtractionResult result = extract(
                "20.04.2020 tarihinde Ankara'da 15 yeni vaka tespit edildi. "
                        + "1 kişi hayatını kaybetti. 5 kişi ise iyileşerek taburcu edildi.");

        assertThat(result.incidents()).singleElement().satisfies(incident -> {
            assertThat(incident.eventType()).isEqualTo("EPIDEMIC");
            assertThat(incident.occurredOn()).isEqualTo(LocalDate.of(2020, 4, 20));
            assertThat(incident.provinceScope()).isEqualTo(ProvinceScope.SINGLE);
            assertThat(incident.provinceCode()).isEqualTo(ANKARA);
            assertThat(incident.metrics())
                    .containsEntry("NEW_CASE", 15)
                    .containsEntry("DEATH", 1)
                    .containsEntry("RECOVERED", 5);
        });
    }

    @Test
    @DisplayName("source example 2 — an earthquake in İzmir, numbers written as words")
    void secondExample() {
        ExtractionResult result = extract(
                "3 Mayıs 2020 günü İzmir'de meydana gelen depremde on iki bina hasar gördü. "
                        + "İki kişi hayatını kaybederken, dokuz kişi enkazdan sağ olarak kurtarıldı. "
                        + "Ayrıca 40 kişi hafif yaralı olarak tedavi altına alındı.");

        assertThat(result.incidents()).singleElement().satisfies(incident -> {
            assertThat(incident.eventType()).isEqualTo("EARTHQUAKE");
            assertThat(incident.occurredOn()).isEqualTo(LocalDate.of(2020, 5, 3));
            assertThat(incident.provinceCode()).isEqualTo(IZMIR);
            assertThat(incident.metrics())
                    .containsEntry("DAMAGED_BUILDING", 12)
                    .containsEntry("DEATH", 2)
                    .containsEntry("RESCUED", 9)
                    .containsEntry("INJURED", 40);
        });
    }

    @Test
    @DisplayName("source example 3 — two provinces with their own figures, plus one shared total")
    void thirdExample() {
        ExtractionResult result = extract(
                "Son 24 saatte Bursa'da 8, Kocaeli'nde 6 trafik kazası meydana geldi. "
                        + "Bursa'da 1, Kocaeli'nde ise 2 kişi kazalarda hayatını kaybetti. "
                        + "Her iki ilde toplam 10 kişi yaralı olarak hastaneye kaldırıldı.");

        assertThat(result.incidents()).hasSize(3);

        assertThat(result.incidents()).filteredOn(i -> Short.valueOf(BURSA).equals(i.provinceCode()))
                .singleElement().satisfies(incident -> {
                    assertThat(incident.provinceScope()).isEqualTo(ProvinceScope.SINGLE);
                    assertThat(incident.metrics())
                            .containsEntry("ACCIDENT_COUNT", 8)
                            .containsEntry("DEATH", 1);
                });

        assertThat(result.incidents()).filteredOn(i -> Short.valueOf(KOCAELI).equals(i.provinceCode()))
                .singleElement().satisfies(incident -> assertThat(incident.metrics())
                        .containsEntry("ACCIDENT_COUNT", 6)
                        .containsEntry("DEATH", 2));

        assertThat(result.incidents()).filteredOn(i -> i.provinceScope() == ProvinceScope.SHARED)
                .singleElement().satisfies(incident -> {
                    assertThat(incident.provinceCode()).isNull();
                    assertThat(incident.sharedProvinceCodes()).containsExactlyInAnyOrder(BURSA, KOCAELI);
                    assertThat(incident.metrics()).containsEntry("INJURED", 10);
                });
    }

    @Test
    @DisplayName("the shared total is added to neither province's own figures")
    void aSharedTotalIsNeverSplit() {
        ExtractionResult result = extract(
                "Bursa'da 8, Kocaeli'nde 6 trafik kazası meydana geldi. "
                        + "Her iki ilde toplam 10 kişi yaralı olarak hastaneye kaldırıldı.");

        assertThat(result.incidents()).filteredOn(i -> i.provinceScope() == ProvinceScope.SINGLE)
                .allSatisfy(incident -> assertThat(incident.metrics()).doesNotContainKey("INJURED"));
        assertThat(result.incidents()).filteredOn(i -> i.provinceScope() == ProvinceScope.SHARED)
                .singleElement()
                .satisfies(incident -> assertThat(incident.metrics()).containsEntry("INJURED", 10));
    }

    @Test
    @DisplayName("a date's own digits are not a metric value")
    void theDigitsInsideADateAreNotCounted() {
        ExtractionResult result = extract("Son 24 saatte Bursa'da 8 trafik kazası meydana geldi.");

        assertThat(result.incidents()).singleElement().satisfies(incident ->
                assertThat(incident.metrics()).containsEntry("ACCIDENT_COUNT", 8)
                        .doesNotContainValue(24));
    }

    @Test
    @DisplayName("a metric keyword in the locative is a circumstance, not the thing counted")
    void circumstancesAreNotCounted() {
        // "2 kişi kazalarda hayatını kaybetti" — two deaths. "kazalarda" is nearer to the number
        // than "hayatını kaybetti" is, so nearest-keyword alone gets this wrong.
        ExtractionResult result = extract("Kocaeli'nde 2 kişi kazalarda hayatını kaybetti.");

        assertThat(result.incidents()).singleElement().satisfies(incident ->
                assertThat(incident.metrics())
                        .containsEntry("DEATH", 2)
                        .doesNotContainKey("ACCIDENT_COUNT"));
    }

    @Test
    @DisplayName("a recognised event with no figures still produces a record")
    void anEventWithoutNumbersIsStillRecorded() {
        ExtractionResult result = extract("Ankara'da deprem oldu.");

        assertThat(result.incidents()).singleElement().satisfies(incident -> {
            assertThat(incident.eventType()).isEqualTo("EARTHQUAKE");
            assertThat(incident.provinceCode()).isEqualTo(ANKARA);
            assertThat(incident.metrics()).isEmpty();
            assertThat(incident.classification()).isEqualTo(ClassificationStatus.CLASSIFIED);
        });
    }

    @Test
    @DisplayName("an unrecognised text is filed, not refused")
    void unrecognisedTextStillProducesARecord() {
        ExtractionResult result = extract("Ankara'da belediye park çalışmalarını tamamladı.");

        assertThat(result.incidents()).singleElement().satisfies(incident -> {
            assertThat(incident.eventType()).isEqualTo(IncidentCatalog.UNCLASSIFIED_EVENT_TYPE);
            assertThat(incident.classification()).isEqualTo(ClassificationStatus.UNCLASSIFIED);
            assertThat(incident.provinceCode()).isEqualTo(ANKARA);
        });
        assertThat(result.warnings()).contains(ExtractionWarnings.NOT_RECOGNISED);
    }

    @Test
    @DisplayName("a text with no province at all is scoped UNKNOWN, not dropped")
    void noProvinceMeansUnknownScope() {
        ExtractionResult result = extract("Depremde 3 bina hasar gördü.");

        assertThat(result.incidents()).singleElement().satisfies(incident -> {
            assertThat(incident.provinceScope()).isEqualTo(ProvinceScope.UNKNOWN);
            assertThat(incident.provinceCode()).isNull();
            assertThat(incident.metrics()).containsEntry("DAMAGED_BUILDING", 3);
        });
    }

    @Test
    @DisplayName("keywords come back positioned in the raw text, by role")
    void keywordsAreReportedWithTheirPositions() {
        String raw = "20.04.2020 tarihinde ANKARA'da 15 yeni vaka tespit edildi.";

        ExtractedIncident incident = extract(raw).incidents().getFirst();

        assertThat(incident.keywords()).extracting(ExtractedKeyword::role)
                .contains(KeywordRole.DATE, KeywordRole.PROVINCE, KeywordRole.METRIC, KeywordRole.EVENT_TYPE);
        assertThat(incident.keywords()).allSatisfy(keyword ->
                assertThat(raw.substring(keyword.charStart(), keyword.charEnd()))
                        .isEqualTo(keyword.keyword()));
        assertThat(incident.keywords()).filteredOn(k -> k.role() == KeywordRole.PROVINCE)
                .singleElement()
                .satisfies(keyword -> assertThat(keyword.keyword()).isEqualTo("ANKARA'da"));
    }

    @Test
    @DisplayName("a number with no metric beside it is left alone rather than guessed at")
    void anUnattributableNumberIsDropped() {
        ExtractionResult result = extract("Ankara'da deprem oldu. 500 metre yol çöktü.");

        assertThat(result.incidents()).singleElement().satisfies(incident -> {
            assertThat(incident.eventType()).isEqualTo("EARTHQUAKE");
            assertThat(incident.metrics()).isEmpty();
        });
    }

    @Test
    @DisplayName("a metric named before its number is still that number's metric")
    void aMetricBeforeTheNumberStillCounts() {
        // Turkish usually puts the counted thing after the figure, but not always: "yaralı sayısı
        // 12" inverts it, and forward-only matching would lose the figure entirely.
        ExtractionResult result = extract("Bursa'da selde yaralı sayısı 12 olarak açıklandı.");

        assertThat(result.incidents()).singleElement().satisfies(incident ->
                assertThat(incident.metrics()).containsEntry("INJURED", 12));
    }

    @Test
    @DisplayName("two provinces and a sentence naming neither leaves the scope UNKNOWN")
    void ambiguousProvinceIsNotGuessed() {
        ExtractionResult result = extract(
                "Bursa'da ve Kocaeli'nde deprem oldu. 5 kişi hayatını kaybetti.");

        assertThat(result.incidents()).filteredOn(i -> i.provinceScope() == ProvinceScope.UNKNOWN)
                .singleElement()
                .satisfies(incident -> assertThat(incident.metrics()).containsEntry("DEATH", 5));
    }

    @Test
    @DisplayName("a figure too large for a metric is dropped rather than wrapped")
    void anOversizedFigureIsNotStored() {
        ExtractionResult result = extract("Ankara'da depremde dokuz milyar bina hasar gördü.");

        assertThat(result.incidents()).singleElement().satisfies(incident ->
                assertThat(incident.metrics()).isEmpty());
    }

    @Test
    @DisplayName("shuffling the sentences does not change the result (FR-04)")
    void sentenceOrderDoesNotMatterForTheFirstExample() {
        Map<String, Integer> inOrder = extract(
                "20.04.2020 tarihinde Ankara'da 15 yeni vaka tespit edildi. "
                        + "1 kişi hayatını kaybetti. 5 kişi ise iyileşerek taburcu edildi.")
                .incidents().getFirst().metrics();

        Map<String, Integer> shuffled = extract(
                "5 kişi ise iyileşerek taburcu edildi. 1 kişi hayatını kaybetti. "
                        + "20.04.2020 tarihinde Ankara'da 15 yeni vaka tespit edildi.")
                .incidents().getFirst().metrics();

        assertThat(shuffled).isEqualTo(inOrder);
    }

    @Test
    @DisplayName("shuffling the third example keeps every province's own figures with it")
    void sentenceOrderDoesNotMatterForTheThirdExample() {
        ExtractionResult result = extract(
                "Her iki ilde toplam 10 kişi yaralı olarak hastaneye kaldırıldı. "
                        + "Bursa'da 1, Kocaeli'nde ise 2 kişi kazalarda hayatını kaybetti. "
                        + "Son 24 saatte Bursa'da 8, Kocaeli'nde 6 trafik kazası meydana geldi.");

        assertThat(result.incidents()).hasSize(3);
        assertThat(result.incidents()).filteredOn(i -> Short.valueOf(BURSA).equals(i.provinceCode()))
                .singleElement().satisfies(incident -> assertThat(incident.metrics())
                        .containsEntry("ACCIDENT_COUNT", 8).containsEntry("DEATH", 1));
        assertThat(result.incidents()).filteredOn(i -> i.provinceScope() == ProvinceScope.SHARED)
                .singleElement().satisfies(incident -> assertThat(incident.metrics())
                        .containsEntry("INJURED", 10));
    }
}
