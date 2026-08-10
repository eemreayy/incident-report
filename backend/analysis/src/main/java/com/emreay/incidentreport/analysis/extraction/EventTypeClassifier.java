package com.emreay.incidentreport.analysis.extraction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.emreay.incidentreport.analysis.catalog.EventTypeDefinition;
import com.emreay.incidentreport.analysis.catalog.IncidentCatalog;
import com.emreay.incidentreport.analysis.domain.ClassificationStatus;
import com.emreay.incidentreport.analysis.domain.KeywordRole;
import com.emreay.incidentreport.analysis.text.NormalizedText;
import com.emreay.incidentreport.analysis.text.TurkishTextNormalizer;

/**
 * Decides what a text is about, using the catalog's trigger keywords (FR-09, TC-8).
 *
 * <p>One keyword is enough. That is not laziness about the threshold — it is what the source
 * document forces: its first example says "15 yeni vaka tespit edildi", and "vaka" is the only word
 * in it that names an event type at all. A threshold of two would fail the system's own acceptance
 * test, so the question is not where to put the bar but what to do with what clears it.
 *
 * <p>Which is: report every event type that matched, ranked, rather than crowning one. A text can
 * genuinely be about more than one thing — an earthquake and the fire after it — and the record
 * grain already allows one report to produce a record per event type (ADR-019). Choosing a single
 * winner here would throw away a record that the data model was built to hold.
 *
 * <p>When nothing matches, the answer is still an answer: {@code OTHER} / {@code UNCLASSIFIED},
 * never a refusal (ADR-006).
 */
@Component
public class EventTypeClassifier {

    private final Map<String, List<KeywordMatcher>> matchersByEventType = new LinkedHashMap<>();

    /** Catalog declaration order, precomputed: it settles ties, and a sort should not re-derive it. */
    private final Map<String, Integer> declarationOrder = new LinkedHashMap<>();

    public EventTypeClassifier(IncidentCatalog catalog, TurkishTextNormalizer normalizer) {
        for (EventTypeDefinition eventType : catalog.eventTypes()) {
            List<KeywordMatcher> matchers = eventType.keywords().stream()
                    .map(keyword -> new KeywordMatcher(normalizer.normalize(keyword).value()))
                    .toList();
            // Insertion order is the catalog's order, which is what settles a tie that nothing else
            // can settle - arbitrary, but the same arbitrary answer every time.
            matchersByEventType.put(eventType.key(), matchers);
            declarationOrder.put(eventType.key(), declarationOrder.size());
        }
    }

    /**
     * Every event type the text names, strongest first.
     *
     * <p>Ranked by how many distinct keywords matched, then by how much text those keywords
     * covered: "trafik kazası" is better evidence than "kaza" because it is more specific, and
     * length is the cheapest honest proxy for specificity. Ties fall back to the catalog's order.
     *
     * @return never empty — a text that matches nothing yields a single {@code UNCLASSIFIED} entry
     */
    public List<EventTypeMatch> classify(NormalizedText text) {
        List<EventTypeMatch> matches = new ArrayList<>();

        for (Map.Entry<String, List<KeywordMatcher>> entry : matchersByEventType.entrySet()) {
            List<ExtractedKeyword> evidence = new ArrayList<>();
            int score = 0;

            for (KeywordMatcher matcher : entry.getValue()) {
                List<KeywordMatcher.KeywordHit> hits = matcher.findIn(text, KeywordRole.EVENT_TYPE);
                if (!hits.isEmpty()) {
                    score++;
                    hits.forEach(hit -> evidence.add(hit.keyword()));
                }
            }

            if (score > 0) {
                matches.add(new EventTypeMatch(
                        entry.getKey(), ClassificationStatus.CLASSIFIED, score, evidence));
            }
        }

        if (matches.isEmpty()) {
            return List.of(unclassified());
        }

        matches.sort(Comparator
                .comparingInt(EventTypeMatch::score).reversed()
                .thenComparing(Comparator.comparingInt(EventTypeClassifier::evidenceLength).reversed())
                .thenComparingInt(match -> declarationOrder.get(match.eventType())));
        return List.copyOf(matches);
    }

    /** What an unrecognised text is filed under. Kept here so callers never have to invent it. */
    public EventTypeMatch unclassified() {
        return new EventTypeMatch(IncidentCatalog.UNCLASSIFIED_EVENT_TYPE,
                ClassificationStatus.UNCLASSIFIED, 0, List.of());
    }

    private static int evidenceLength(EventTypeMatch match) {
        return match.evidence().stream()
                .mapToInt(keyword -> keyword.keyword().length())
                .sum();
    }
}
