package com.emreay.incidentreport.analysis.extraction;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.io.ClassPathResource;

import com.emreay.incidentreport.analysis.catalog.IncidentCatalog;
import com.emreay.incidentreport.analysis.catalog.IncidentCatalogLoader;
import com.emreay.incidentreport.analysis.domain.DateSource;
import com.emreay.incidentreport.analysis.domain.ProvinceScope;
import com.emreay.incidentreport.analysis.text.NumberExtractor;
import com.emreay.incidentreport.analysis.text.SentenceSplitter;
import com.emreay.incidentreport.analysis.text.TurkishTextNormalizer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * The three texts from the source document, and the table they must reproduce (PRD §11).
 *
 * <p>These are the acceptance criteria, not examples of them. Everything else in this package tests
 * one rule at a time; this file says what the whole thing is for, and it is the test that should be
 * read first when a change to any extractor breaks something.
 *
 * <p>Every example is run in <em>every</em> sentence order, not one shuffled variant. FR-04 says the
 * result must not depend on the order, and the province rule that looked correct in T-14 was correct
 * only for the order the document happens to use — one arbitrary shuffle might well have missed it.
 */
class GoldenExampleTest {

    private static final short ANKARA = 6;
    private static final short IZMIR = 35;
    private static final short BURSA = 16;
    private static final short KOCAELI = 41;

    private static final Map<Short, String> PROVINCES = Map.of(
            ANKARA, "Ankara", IZMIR, "İzmir", BURSA, "Bursa", KOCAELI, "Kocaeli");

    /** The reference date, so the third example's "son 24 saatte" has something to resolve against. */
    private static final LocalDate SUBMITTED_ON = LocalDate.of(2020, 6, 15);

    private static final List<String> EPIDEMIC_SENTENCES = List.of(
            "20.04.2020 tarihinde Ankara'da 15 yeni vaka tespit edildi.",
            "1 kişi hayatını kaybetti.",
            "5 kişi ise iyileşerek taburcu edildi.");

    private static final List<String> EARTHQUAKE_SENTENCES = List.of(
            "3 Mayıs 2020 günü İzmir'de meydana gelen depremde on iki bina hasar gördü.",
            "İki kişi hayatını kaybederken, dokuz kişi enkazdan sağ olarak kurtarıldı.",
            "Ayrıca 40 kişi hafif yaralı olarak tedavi altına alındı.");

    private static final List<String> TRAFFIC_SENTENCES = List.of(
            "Son 24 saatte Bursa'da 8, Kocaeli'nde 6 trafik kazası meydana geldi.",
            "Bursa'da 1, Kocaeli'nde ise 2 kişi kazalarda hayatını kaybetti.",
            "Her iki ilde toplam 10 kişi yaralı olarak hastaneye kaldırıldı.");

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

    /** One row of the §11 table: what a record must say, regardless of how the text was ordered. */
    private record Expected(String eventType,
                            LocalDate occurredOn,
                            DateSource dateSource,
                            ProvinceScope scope,
                            Short province,
                            List<Short> shared,
                            Map<String, Integer> metrics) {
    }

    private static List<Expected> epidemic() {
        return List.of(new Expected("EPIDEMIC", LocalDate.of(2020, 4, 20), DateSource.EXPLICIT,
                ProvinceScope.SINGLE, ANKARA, List.of(),
                Map.of("NEW_CASE", 15, "DEATH", 1, "RECOVERED", 5)));
    }

    private static List<Expected> earthquake() {
        return List.of(new Expected("EARTHQUAKE", LocalDate.of(2020, 5, 3), DateSource.EXPLICIT,
                ProvinceScope.SINGLE, IZMIR, List.of(),
                Map.of("DAMAGED_BUILDING", 12, "DEATH", 2, "RESCUED", 9, "INJURED", 40)));
    }

    private static List<Expected> traffic() {
        return List.of(
                new Expected("TRAFFIC_ACCIDENT", SUBMITTED_ON, DateSource.RELATIVE,
                        ProvinceScope.SINGLE, BURSA, List.of(),
                        Map.of("ACCIDENT_COUNT", 8, "DEATH", 1)),
                new Expected("TRAFFIC_ACCIDENT", SUBMITTED_ON, DateSource.RELATIVE,
                        ProvinceScope.SINGLE, KOCAELI, List.of(),
                        Map.of("ACCIDENT_COUNT", 6, "DEATH", 2)),
                // Ten injured across both provinces. Never five each, never added to either total,
                // and never dropped — the row that lets the two above be reconciled (ADR-019).
                new Expected("TRAFFIC_ACCIDENT", SUBMITTED_ON, DateSource.RELATIVE,
                        ProvinceScope.SHARED, null, List.of(BURSA, KOCAELI),
                        Map.of("INJURED", 10)));
    }

    static Stream<Arguments> examples() {
        return Stream.of(
                arguments("örnek 1 — salgın", EPIDEMIC_SENTENCES, epidemic()),
                arguments("örnek 2 — deprem", EARTHQUAKE_SENTENCES, earthquake()),
                arguments("örnek 3 — trafik kazası", TRAFFIC_SENTENCES, traffic()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("examples")
    @DisplayName("each source example reproduces its row of the acceptance table")
    void theSourceExamples(String name, List<String> sentences, List<Expected> expected) {
        assertMatches(String.join(" ", sentences), expected);
    }

    static Stream<Arguments> everyOrdering() {
        List<Arguments> orderings = new ArrayList<>();
        examples().forEach(example -> {
            Object[] parts = example.get();
            @SuppressWarnings("unchecked")
            List<String> sentences = (List<String>) parts[1];
            @SuppressWarnings("unchecked")
            List<Expected> expected = (List<Expected>) parts[2];
            for (List<String> ordering : permutations(sentences)) {
                orderings.add(arguments(parts[0] + " · " + orderKey(sentences, ordering),
                        String.join(" ", ordering), expected));
            }
        });
        return orderings.stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyOrdering")
    @DisplayName("and reproduces it in every sentence order (FR-04)")
    void sentenceOrderNeverMatters(String name, String text, List<Expected> expected) {
        assertMatches(text, expected);
    }

    @Test
    @DisplayName("the shared total is counted once, not once per province")
    void theSharedTotalIsNotDoubleCounted() {
        List<ExtractedIncident> incidents =
                extractor.extract(normalizer.normalize(String.join(" ", TRAFFIC_SENTENCES)), SUBMITTED_ON)
                        .incidents();

        int injuredEverywhere = incidents.stream()
                .mapToInt(incident -> incident.metrics().getOrDefault("INJURED", 0))
                .sum();

        assertThat(injuredEverywhere)
                .as("10 appears in exactly one record; adding it to a province would make it 20")
                .isEqualTo(10);
    }

    private void assertMatches(String text, List<Expected> expected) {
        List<ExtractedIncident> incidents =
                extractor.extract(normalizer.normalize(text), SUBMITTED_ON).incidents();

        assertThat(incidents).hasSize(expected.size());
        for (Expected row : expected) {
            assertThat(incidents)
                    .filteredOn(incident -> incident.provinceScope() == row.scope()
                            && java.util.Objects.equals(incident.provinceCode(), row.province()))
                    .as("record for %s %s", row.scope(), row.province())
                    .singleElement()
                    .satisfies(incident -> {
                        assertThat(incident.eventType()).isEqualTo(row.eventType());
                        assertThat(incident.occurredOn()).isEqualTo(row.occurredOn());
                        assertThat(incident.dateSource()).isEqualTo(row.dateSource());
                        assertThat(incident.metrics()).isEqualTo(row.metrics());
                        assertThat(incident.sharedProvinceCodes())
                                .containsExactlyInAnyOrderElementsOf(row.shared());
                    });
        }
    }

    /** Every ordering, so no arrangement of the sentences goes untried. */
    private static List<List<String>> permutations(List<String> items) {
        if (items.size() <= 1) {
            return List.of(List.copyOf(items));
        }
        List<List<String>> all = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            List<String> rest = new ArrayList<>(items);
            String head = rest.remove(i);
            for (List<String> tail : permutations(rest)) {
                List<String> ordering = new ArrayList<>();
                ordering.add(head);
                ordering.addAll(tail);
                all.add(List.copyOf(ordering));
            }
        }
        return all;
    }

    private static String orderKey(List<String> original, List<String> ordering) {
        StringBuilder key = new StringBuilder();
        ordering.forEach(sentence -> key.append(original.indexOf(sentence) + 1));
        return "sıra " + key;
    }
}
