package com.emreay.incidentreport.analysis.extraction;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;

import com.emreay.incidentreport.analysis.catalog.IncidentCatalog;
import com.emreay.incidentreport.analysis.catalog.IncidentCatalogLoader;
import com.emreay.incidentreport.analysis.domain.ClassificationStatus;
import com.emreay.incidentreport.analysis.domain.KeywordRole;
import com.emreay.incidentreport.analysis.text.NormalizedText;
import com.emreay.incidentreport.analysis.text.SentenceSplitter;
import com.emreay.incidentreport.analysis.text.TurkishTextNormalizer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Against the real catalog rather than a fixture: what is worth checking is that the shipped
 * keywords actually recognise the shipped examples, which a made-up catalog cannot tell us.
 */
class EventTypeClassifierTest {

    private static final IncidentCatalog CATALOG =
            new IncidentCatalogLoader().load(new ClassPathResource("incident-catalog.yml"));

    private final TurkishTextNormalizer normalizer = new TurkishTextNormalizer(new SentenceSplitter());
    private final EventTypeClassifier classifier = new EventTypeClassifier(CATALOG, normalizer);

    private List<EventTypeMatch> classify(String raw) {
        return classifier.classify(normalizer.normalize(raw));
    }

    private String topTypeOf(String raw) {
        return classify(raw).getFirst().eventType();
    }

    @Test
    @DisplayName("one keyword is enough, because the first source example only gives one")
    void aSingleKeywordClassifies() {
        // "vaka" is the only word in this text that names an event type at all. A threshold of two
        // would make the system fail its own acceptance test (PRD §11).
        List<EventTypeMatch> matches = classify(
                "20.04.2020 tarihinde Ankara'da 15 yeni vaka tespit edildi. "
                        + "1 kişi hayatını kaybetti. 5 kişi ise iyileşerek taburcu edildi.");

        assertThat(matches).singleElement().satisfies(match -> {
            assertThat(match.eventType()).isEqualTo("EPIDEMIC");
            assertThat(match.status()).isEqualTo(ClassificationStatus.CLASSIFIED);
            assertThat(match.score()).isEqualTo(1);
        });
    }

    @Test
    @DisplayName("the second source example is an earthquake, on three separate words")
    void theEarthquakeExample() {
        List<EventTypeMatch> matches = classify(
                "3 Mayıs 2020 günü İzmir'de meydana gelen depremde on iki bina hasar gördü. "
                        + "İki kişi hayatını kaybederken, dokuz kişi enkazdan sağ olarak kurtarıldı. "
                        + "Ayrıca 40 kişi hafif yaralı olarak tedavi altına alındı.");

        assertThat(matches.getFirst().eventType()).isEqualTo("EARTHQUAKE");
        assertThat(matches.getFirst().evidence())
                .extracting(ExtractedKeyword::keyword)
                .contains("depremde", "hasar", "enkazdan");
    }

    @Test
    @DisplayName("the third source example is a traffic accident")
    void theTrafficExample() {
        assertThat(topTypeOf("Son 24 saatte Bursa'da 8, Kocaeli'nde 6 trafik kazası meydana geldi. "
                + "Bursa'da 1, Kocaeli'nde ise 2 kişi kazalarda hayatını kaybetti. "
                + "Her iki ilde toplam 10 kişi yaralı olarak hastaneye kaldırıldı."))
                .isEqualTo("TRAFFIC_ACCIDENT");
    }

    @ParameterizedTest(name = "\"{0}\" -> {1}")
    @CsvSource({
            "'Sel nedeniyle 200 kişi tahliye edildi',          FLOOD",
            "'Su baskını sonucu yollar kapandı',               FLOOD",
            "'Yangında 3 bina kül oldu',                       FIRE",
            "'İtfaiye ekipleri alevlere müdahale etti',        FIRE",
            "'Şiddetli sarsıntı hissedildi',                   EARTHQUAKE",
            "'Karantina süresi uzatıldı',                      EPIDEMIC",
            "'Zincirleme çarpışmada 4 araç hasar gördü',       TRAFFIC_ACCIDENT"
    })
    @DisplayName("the catalog's own keywords recognise their own event types")
    void catalogKeywordsWork(String text, String expected) {
        assertThat(topTypeOf(text)).isEqualTo(expected);
    }

    @Test
    @DisplayName("keywords are matched through their Turkish endings")
    void keywordsAreMatchedThroughInflection() {
        assertThat(topTypeOf("Depremin ardından artçılar sürdü")).isEqualTo("EARTHQUAKE");
        assertThat(topTypeOf("Vakaların sayısı arttı")).isEqualTo("EPIDEMIC");
        assertThat(topTypeOf("Kazalarda 3 kişi yaralandı")).isEqualTo("TRAFFIC_ACCIDENT");
        assertThat(topTypeOf("Seller nedeniyle yollar kapandı")).isEqualTo("FLOOD");
    }

    @Test
    @DisplayName("a text about two things is reported as being about two things")
    void severalEventTypesAreAllReported() {
        // The record grain already allows one report to produce a record per event type (ADR-019).
        // Crowning a single winner here would throw away a record the data model was built to hold.
        List<EventTypeMatch> matches = classify("Depremin ardından çıkan yangında 3 bina kül oldu.");

        assertThat(matches).extracting(EventTypeMatch::eventType)
                .containsExactlyInAnyOrder("EARTHQUAKE", "FIRE");
        assertThat(matches).allMatch(EventTypeMatch::isClassified);
    }

    @Test
    @DisplayName("more distinct keywords ranks higher")
    void rankingFollowsTheEvidence() {
        List<EventTypeMatch> matches = classify(
                "Depremde enkaz altında kalanlar için çalışma sürüyor, ayrıca bir yangın çıktı.");

        assertThat(matches.getFirst().eventType()).isEqualTo("EARTHQUAKE");
        assertThat(matches.getFirst().score()).isGreaterThan(matches.get(1).score());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Belediye bugün park çalışmalarını tamamladı.",
            "Marangoz testere ile çalışırken elini kesti.",
            "Bakan açıklamasında son durumu değerlendirdi.",
            ""
    })
    @DisplayName("a text the catalog does not recognise is filed, not refused")
    void unrecognisedTextIsStillAnswered(String text) {
        List<EventTypeMatch> matches = classify(text);

        assertThat(matches).singleElement().satisfies(match -> {
            assertThat(match.eventType()).isEqualTo(IncidentCatalog.UNCLASSIFIED_EVENT_TYPE);
            assertThat(match.status()).isEqualTo(ClassificationStatus.UNCLASSIFIED);
            assertThat(match.score()).isZero();
            assertThat(match.evidence()).isEmpty();
        });
    }

    @Test
    @DisplayName("\"testere\" is not the keyword \"test\"")
    void anOpenEndedSuffixWouldHaveMatchedThis() {
        // The third time this trap appears in the pipeline, after ADR-029 and ADR-030.
        assertThat(classify("Marangoz testere ile çalışıyordu").getFirst().status())
                .isEqualTo(ClassificationStatus.UNCLASSIFIED);
        // But the keyword itself, inflected, still matches.
        assertThat(topTypeOf("Günlük test sayısı açıklandı")).isEqualTo("EPIDEMIC");
        assertThat(topTypeOf("Testler tamamlandı")).isEqualTo("EPIDEMIC");
    }

    @Test
    @DisplayName("evidence points into the raw text, spelled as the user spelled it")
    void evidenceIsPositionedInTheRawText() {
        NormalizedText text = normalizer.normalize("İZMİR'de   DEPREM oldu.");

        ExtractedKeyword keyword = classifier.classify(text).getFirst().evidence().getFirst();

        assertThat(keyword.role()).isEqualTo(KeywordRole.EVENT_TYPE);
        assertThat(keyword.keyword()).isEqualTo("DEPREM");
        assertThat(text.original().substring(keyword.charStart(), keyword.charEnd()))
                .isEqualTo("DEPREM");
    }

    @Test
    @DisplayName("every occurrence is evidence, not just the first")
    void repeatedKeywordsAreAllRecorded() {
        EventTypeMatch match = classify("Deprem oldu. Deprem sürüyor.").getFirst();

        assertThat(match.evidence()).hasSize(2);
        assertThat(match.score()).as("score counts distinct keywords, not occurrences").isEqualTo(1);
    }

    @Test
    void aMatchMustBeConsistentWithItsEvidence() {
        assertThatThrownBy(() -> new EventTypeMatch("FLOOD", ClassificationStatus.CLASSIFIED, 1, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must say what matched");

        assertThatThrownBy(() -> new EventTypeMatch("OTHER", ClassificationStatus.UNCLASSIFIED, 0,
                List.of(new ExtractedKeyword("sel", KeywordRole.EVENT_TYPE, 0, 3))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nothing to show");

        assertThatThrownBy(() -> new EventTypeMatch("FLOOD", ClassificationStatus.CLASSIFIED, -1, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negative");
    }
}
