package com.emreay.incidentreport.analysis.extraction;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.emreay.incidentreport.analysis.catalog.EventTypeDefinition;
import com.emreay.incidentreport.analysis.catalog.IncidentCatalog;
import com.emreay.incidentreport.analysis.catalog.MetricDefinition;
import com.emreay.incidentreport.analysis.domain.ClassificationStatus;
import com.emreay.incidentreport.analysis.domain.KeywordRole;
import com.emreay.incidentreport.analysis.domain.ProvinceScope;
import com.emreay.incidentreport.analysis.text.NormalizedText;
import com.emreay.incidentreport.analysis.text.NumberToken;
import com.emreay.incidentreport.analysis.text.NumberExtractor;
import com.emreay.incidentreport.analysis.text.Sentence;
import com.emreay.incidentreport.analysis.text.TurkishTextNormalizer;

/**
 * Turns a text into incident records, by putting the other extractors together (TC-3).
 *
 * <p>Everything before this task produced a list of things a text contains: a date, some provinces,
 * some event types, some numbers. None of that is a record yet, because a record says which number
 * belongs to which metric of which event in which province — and the text only ever says that
 * through proximity.
 *
 * <p>Three rules do the attribution, and each exists because one of the source document's examples
 * breaks without it. They are described on the methods that implement them.
 */
@Component
@Primary
public class CatalogIncidentExtractor implements IncidentExtractor {

    private static final int UNICODE = Pattern.UNICODE_CHARACTER_CLASS;

    /**
     * "The figure that follows belongs to several provinces at once."
     *
     * <p>"Her iki ilde toplam 10 kişi yaralı" — ten injured across both, not ten each and not five
     * apiece. The phrase names no province, so without this marker the number would be handed to
     * whichever province was mentioned last (ADR-019).
     */
    private static final Pattern SHARED_MARKER = Pattern.compile(
            "\\b(?:(?:her\\s+)?(?:iki|üç|dört|beş|altı|\\d{1,2})\\s+(?:il|şehir)\\p{L}*"
                    + "|(?:il|şehir)ler\\p{L}*)\\b", UNICODE);

    /**
     * A metric keyword in the locative case is describing the circumstances, not the thing being
     * counted: "2 kişi <b>kazalarda</b> hayatını kaybetti" is two deaths, not two accidents.
     */
    private static final Pattern CIRCUMSTANCE_ENDING = Pattern.compile("(?:d[ae]|t[ae])$", UNICODE);

    private final DateResolver dateResolver;
    private final ProvinceExtractor provinceExtractor;
    private final EventTypeClassifier classifier;
    private final NumberExtractor numberExtractor;
    private final Map<String, List<MetricKeyword>> metricKeywordsByEventType = new LinkedHashMap<>();

    public CatalogIncidentExtractor(DateResolver dateResolver,
                                    ProvinceExtractor provinceExtractor,
                                    EventTypeClassifier classifier,
                                    NumberExtractor numberExtractor,
                                    IncidentCatalog catalog,
                                    TurkishTextNormalizer normalizer) {
        this.dateResolver = dateResolver;
        this.provinceExtractor = provinceExtractor;
        this.classifier = classifier;
        this.numberExtractor = numberExtractor;

        for (EventTypeDefinition eventType : catalog.eventTypes()) {
            List<MetricKeyword> keywords = new ArrayList<>();
            for (MetricDefinition metric : eventType.metrics()) {
                for (String keyword : metric.keywords()) {
                    String normalized = normalizer.normalize(keyword).value();
                    keywords.add(new MetricKeyword(metric.key(), normalized, new KeywordMatcher(normalized)));
                }
            }
            metricKeywordsByEventType.put(eventType.key(), List.copyOf(keywords));
        }
    }

    @Override
    public ExtractionResult extract(NormalizedText text, LocalDate referenceDate) {
        ResolvedDate date = dateResolver.resolve(text, referenceDate);
        List<ProvinceMention> provinces = provinceExtractor.mentions(text);
        List<EventTypeMatch> eventTypes = classifier.classify(text);
        List<NumberToken> numbers = countable(text, numberExtractor.extract(text), referenceDate);

        List<String> warnings = new ArrayList<>();
        if (!date.wasExtracted()) {
            warnings.add(ExtractionWarnings.DATE_ASSUMED);
        }

        if (eventTypes.size() == 1 && !eventTypes.getFirst().isClassified()) {
            warnings.add(ExtractionWarnings.NOT_RECOGNISED);
            return new ExtractionResult(
                    List.of(unclassifiedRecord(text, date, provinces)), List.copyOf(warnings));
        }

        Map<RecordKey, RecordBuilder> records = new LinkedHashMap<>();
        Set<Short> everyProvince = provinces.stream()
                .map(ProvinceMention::code)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        for (NumberToken number : numbers) {
            attribute(text, number, eventTypes, provinces, everyProvince)
                    .ifPresent(attribution -> records
                            .computeIfAbsent(attribution.key(), key -> new RecordBuilder(key))
                            .add(attribution));
        }

        // An event type that was recognised but collected no number is still something the text
        // said. "Ankara'da deprem oldu" carries no figure and is not therefore silent.
        for (EventTypeMatch eventType : eventTypes) {
            boolean covered = records.keySet().stream()
                    .anyMatch(key -> key.eventType().equals(eventType.eventType()));
            if (!covered) {
                RecordKey key = soleProvinceKey(eventType.eventType(), provinces);
                records.computeIfAbsent(key, RecordBuilder::new);
            }
        }

        List<ExtractedIncident> incidents = records.values().stream()
                .map(builder -> builder.build(text, date, eventTypes, provinces))
                .toList();

        return new ExtractionResult(incidents, List.copyOf(warnings));
    }

    /**
     * Numbers that could be a metric value.
     *
     * <p>The digits inside a date are not: "son <b>24</b> saatte" is when, not how many, and
     * {@link NumberExtractor} cannot tell — it skips {@code 20.04.2020} because that shape is
     * unambiguous, but a bare number inside a relative expression needs the resolved span to be
     * recognised as part of it.
     */
    private List<NumberToken> countable(NormalizedText text, List<NumberToken> numbers, LocalDate referenceDate) {
        List<int[]> spans = new ArrayList<>();
        for (ResolvedDate mention : dateResolver.mentions(text, referenceDate)) {
            spans.add(new int[]{mention.start(), mention.end()});
        }
        // "her iki ilde" counts provinces, not casualties. The two inside it is part of the phrase
        // that makes the following figure shared, and adding it to that figure inflates the total.
        Matcher marker = SHARED_MARKER.matcher(text.value());
        while (marker.find()) {
            spans.add(new int[]{marker.start(), marker.end()});
        }

        return numbers.stream()
                .filter(number -> spans.stream().noneMatch(span ->
                        number.start() < span[1] && span[0] < number.end()))
                .toList();
    }

    /**
     * Which metric of which event type, and which province, a number belongs to.
     *
     * <p>The metric is the nearest keyword <em>after</em> the number in the same sentence, falling
     * back to the nearest one before it. Turkish puts the counted thing after its number — "15 yeni
     * vaka", "dokuz kişi enkazdan sağ olarak kurtarıldı" — so forward is the direction that carries
     * the meaning, and the fallback only catches the inverted phrasings.
     *
     * <p>Event types are tried strongest first, so a number that could belong to two of them ends up
     * in the one the text is most clearly about, and in exactly one record.
     */
    private Optional<Attribution> attribute(NormalizedText text,
                                            NumberToken number,
                                            List<EventTypeMatch> eventTypes,
                                            List<ProvinceMention> provinces,
                                            Set<Short> everyProvince) {
        Sentence sentence = sentenceOf(text, number.start());

        for (EventTypeMatch eventType : eventTypes) {
            if (!eventType.isClassified()) {
                continue;
            }
            Optional<MetricHit> hit = nearestMetric(text, number, sentence, eventType.eventType());
            if (hit.isPresent()) {
                return Optional.of(new Attribution(
                        keyFor(eventType.eventType(), number, sentence, provinces, everyProvince),
                        hit.get(), number));
            }
        }
        return Optional.empty();
    }

    private Optional<MetricHit> nearestMetric(NormalizedText text,
                                              NumberToken number,
                                              Sentence sentence,
                                              String eventType) {
        List<MetricHit> hits = new ArrayList<>();
        for (MetricKeyword keyword : metricKeywordsByEventType.getOrDefault(eventType, List.of())) {
            for (KeywordMatcher.KeywordHit found : keyword.matcher().findIn(text, KeywordRole.METRIC)) {
                if (found.start() < sentence.start() || found.start() >= sentence.end()) {
                    continue;
                }
                if (isCircumstance(text, keyword, found.start())) {
                    continue;
                }
                hits.add(new MetricHit(keyword.metricKey(), found.keyword(), found.start()));
            }
        }

        return hits.stream()
                .filter(hit -> hit.start() >= number.end())
                .min(Comparator.comparingInt(hit -> hit.start() - number.end()))
                .or(() -> hits.stream()
                        .filter(hit -> hit.start() < number.start())
                        .max(Comparator.comparingInt(MetricHit::start)));
    }

    /** Whether the keyword at this position is inflected as a circumstance rather than a count. */
    private boolean isCircumstance(NormalizedText text, MetricKeyword keyword, int start) {
        int end = start + keyword.keyword().length();
        int wordEnd = end;
        while (wordEnd < text.value().length() && Character.isLetter(text.value().charAt(wordEnd))) {
            wordEnd++;
        }
        if (wordEnd == end) {
            return false;
        }
        return CIRCUMSTANCE_ENDING.matcher(text.value().substring(end, wordEnd)).find();
    }

    /**
     * The province a number is filed under.
     *
     * <p>A figure introduced as belonging to several provinces at once is {@code SHARED} and is
     * never split between them (ADR-019). Otherwise it belongs to the province named most recently
     * before it — "Bursa'da 8, Kocaeli'nde 6" works because each figure follows its own province.
     */
    private RecordKey keyFor(String eventType,
                             NumberToken number,
                             Sentence sentence,
                             List<ProvinceMention> provinces,
                             Set<Short> everyProvince) {
        if (everyProvince.size() > 1 && SHARED_MARKER.matcher(sentence.text()).find()) {
            return new RecordKey(eventType, ProvinceScope.SHARED, null, Set.copyOf(everyProvince));
        }

        List<ProvinceMention> inSentence = provinces.stream()
                .filter(province -> province.start() >= sentence.start() && province.start() < sentence.end())
                .toList();
        if (!inSentence.isEmpty()) {
            ProvinceMention owner = inSentence.stream()
                    .filter(province -> province.start() < number.start())
                    .max(Comparator.comparingInt(ProvinceMention::start))
                    .orElseGet(inSentence::getFirst);
            return new RecordKey(eventType, ProvinceScope.SINGLE, owner.code(), Set.of());
        }

        // The sentence names no province. Looking backwards through the text would work for the
        // examples as written and break the moment their sentences are shuffled, which FR-04 says
        // must not matter - so the only province that can be assumed is the one a report names
        // throughout. Anything less certain than that stays UNKNOWN rather than being guessed.
        return everyProvince.size() == 1
                ? new RecordKey(eventType, ProvinceScope.SINGLE, everyProvince.iterator().next(), Set.of())
                : new RecordKey(eventType, ProvinceScope.UNKNOWN, null, Set.of());
    }

    /** For an event type with no figures: the province if the text names exactly one, else none. */
    private RecordKey soleProvinceKey(String eventType, List<ProvinceMention> provinces) {
        Set<Short> distinct = provinces.stream()
                .map(ProvinceMention::code)
                .collect(java.util.stream.Collectors.toSet());
        return distinct.size() == 1
                ? new RecordKey(eventType, ProvinceScope.SINGLE, distinct.iterator().next(), Set.of())
                : new RecordKey(eventType, ProvinceScope.UNKNOWN, null, Set.of());
    }

    private ExtractedIncident unclassifiedRecord(NormalizedText text,
                                                 ResolvedDate date,
                                                 List<ProvinceMention> provinces) {
        RecordKey key = soleProvinceKey(IncidentCatalog.UNCLASSIFIED_EVENT_TYPE, provinces);
        return new ExtractedIncident(date.date(), date.source(), key.scope(), key.provinceCode(),
                key.sharedProvinceCodes(), key.eventType(), ClassificationStatus.UNCLASSIFIED,
                Map.of(), keywordsFor(text, date, List.of(), provinces, key));
    }

    private Sentence sentenceOf(NormalizedText text, int offset) {
        return text.sentences().stream()
                .filter(sentence -> offset >= sentence.start() && offset < sentence.end())
                .findFirst()
                .orElseGet(() -> new Sentence(text.value(), 0, Math.max(1, text.value().length())));
    }

    private List<ExtractedKeyword> keywordsFor(NormalizedText text,
                                               ResolvedDate date,
                                               List<ExtractedKeyword> metrics,
                                               List<ProvinceMention> provinces,
                                               RecordKey key) {
        List<ExtractedKeyword> keywords = new ArrayList<>(metrics);
        if (date.wasExtracted()) {
            keywords.add(new ExtractedKeyword(
                    text.originalTextIn(date.start(), date.end()), KeywordRole.DATE,
                    text.sourceStart(date.start()), text.sourceEnd(date.end() - 1)));
        }
        for (ProvinceMention province : provinces) {
            boolean relevant = key.scope() == ProvinceScope.SHARED
                    ? key.sharedProvinceCodes().contains(province.code())
                    : Short.valueOf(province.code()).equals(key.provinceCode());
            if (relevant) {
                keywords.add(new ExtractedKeyword(
                        text.originalTextIn(province.start(), province.end()), KeywordRole.PROVINCE,
                        text.sourceStart(province.start()), text.sourceEnd(province.end() - 1)));
            }
        }
        return List.copyOf(keywords);
    }

    private record MetricKeyword(String metricKey, String keyword, KeywordMatcher matcher) {
    }

    private record MetricHit(String metricKey, ExtractedKeyword keyword, int start) {
    }

    private record Attribution(RecordKey key, MetricHit hit, NumberToken number) {
    }

    private record RecordKey(String eventType, ProvinceScope scope,
                             Short provinceCode, Set<Short> sharedProvinceCodes) {
    }

    /** One record under construction: everything attributed to the same key adds up here. */
    private final class RecordBuilder {

        private final RecordKey key;
        private final Map<String, Integer> metrics = new LinkedHashMap<>();
        private final List<ExtractedKeyword> keywords = new ArrayList<>();

        private RecordBuilder(RecordKey key) {
            this.key = key;
        }

        private void add(Attribution attribution) {
            if (!attribution.number().fitsMetricValue()) {
                return;
            }
            metrics.merge(attribution.hit().metricKey(), (int) attribution.number().value(), Integer::sum);
            keywords.add(attribution.hit().keyword());
        }

        private ExtractedIncident build(NormalizedText text,
                                        ResolvedDate date,
                                        List<EventTypeMatch> eventTypes,
                                        List<ProvinceMention> provinces) {
            List<ExtractedKeyword> all = new ArrayList<>(
                    keywordsFor(text, date, keywords, provinces, key));
            eventTypes.stream()
                    .filter(match -> match.eventType().equals(key.eventType()))
                    .forEach(match -> all.addAll(match.evidence()));

            return new ExtractedIncident(date.date(), date.source(), key.scope(), key.provinceCode(),
                    key.sharedProvinceCodes(), key.eventType(), ClassificationStatus.CLASSIFIED,
                    Map.copyOf(metrics), List.copyOf(all));
        }
    }
}
