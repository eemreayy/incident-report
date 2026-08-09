package com.emreay.incidentreport.analysis.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reads the catalog and refuses to hand back one that cannot be trusted.
 *
 * <p>The validation is the point. A catalog is data an operator edits, and the failure modes are
 * quiet ones: a duplicate key silently shadows an entry, a type without keywords can never match, a
 * key longer than its column fails much later at insert time. Each of those would show up as "the
 * system does not recognise this text" — indistinguishable from a genuine gap in the catalog.
 * Checking at startup turns all of them into one loud message before anything runs (ADR-007).
 *
 * <p>Every problem is collected rather than the first thrown, so a broken file is fixed in one pass.
 */
public class IncidentCatalogLoader {

    /**
     * Keys are written into {@code incident.event_type} and {@code incident_metric.metric_type},
     * both {@code varchar(48)}. Checking the width here turns a truncation or an insert failure —
     * which would surface hours later, in the middle of analysing something — into a startup error.
     */
    private static final int MAX_KEY_LENGTH = 48;

    private static final Pattern KEY_FORMAT = Pattern.compile("[A-Z][A-Z0-9_]*");

    private final ObjectMapper yaml = new ObjectMapper(new YAMLFactory())
            .setPropertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE);

    public IncidentCatalog load(Resource resource) {
        String source = resource.getDescription();

        String content;
        try (InputStream stream = resource.getInputStream()) {
            content = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new InvalidCatalogException(source, failure);
        }

        // An empty file is a mistake with a meaning, not a parse error. Left to the parser it comes
        // back as "no content to map due to end-of-input", which tells an operator nothing about
        // what to do; the check below says what is actually wrong with it.
        CatalogFile file = null;
        if (!content.isBlank()) {
            try {
                file = yaml.readValue(content, CatalogFile.class);
            } catch (IOException | RuntimeException failure) {
                throw new InvalidCatalogException(source, failure);
            }
        }

        List<EventTypeDefinition> eventTypes = file == null || file.eventTypes() == null
                ? List.of() : file.eventTypes();

        List<String> problems = validate(eventTypes);
        if (!problems.isEmpty()) {
            throw new InvalidCatalogException(source, problems);
        }
        return new IncidentCatalog(eventTypes);
    }

    private List<String> validate(List<EventTypeDefinition> eventTypes) {
        List<String> problems = new ArrayList<>();

        if (eventTypes.isEmpty()) {
            problems.add("it defines no event types, so the system would recognise nothing");
            return problems;
        }

        Set<String> seenEventTypes = new HashSet<>();
        // A metric key means the same thing everywhere, so its label must not change between event
        // types - a chart legend showing DEATH cannot say two different things at once.
        Map<String, String> labelByMetricKey = new HashMap<>();

        for (int i = 0; i < eventTypes.size(); i++) {
            EventTypeDefinition eventType = eventTypes.get(i);
            String where = "event type #" + (i + 1)
                    + (isBlank(eventType.key()) ? "" : " (" + eventType.key() + ")");

            validateKey(eventType.key(), where, problems);
            if (isBlank(eventType.label())) {
                problems.add(where + " has no label, so the interface would have nothing to show");
            }
            if (IncidentCatalog.UNCLASSIFIED_EVENT_TYPE.equals(eventType.key())) {
                problems.add(where + " defines the reserved key "
                        + IncidentCatalog.UNCLASSIFIED_EVENT_TYPE
                        + ", which means \"nothing matched\" and cannot be triggered by words");
            }
            if (!isBlank(eventType.key()) && !seenEventTypes.add(eventType.key())) {
                problems.add(where + " repeats a key that is already defined; one of the two would "
                        + "be silently ignored");
            }
            validateKeywords(eventType.keywords(), where, problems);

            if (eventType.metrics().isEmpty()) {
                problems.add(where + " defines no metrics, so it could be recognised but nothing "
                        + "could be extracted from it");
            }
            validateMetrics(eventType, where, labelByMetricKey, problems);
        }
        return problems;
    }

    private void validateMetrics(EventTypeDefinition eventType, String eventTypeWhere,
                                 Map<String, String> labelByMetricKey, List<String> problems) {
        Set<String> seen = new HashSet<>();
        for (MetricDefinition metric : eventType.metrics()) {
            String where = eventTypeWhere + " metric "
                    + (isBlank(metric.key()) ? "#" + (eventType.metrics().indexOf(metric) + 1) : metric.key());

            validateKey(metric.key(), where, problems);
            if (isBlank(metric.label())) {
                problems.add(where + " has no label");
            }
            if (!isBlank(metric.key()) && !seen.add(metric.key())) {
                problems.add(where + " is defined twice in the same event type");
            }
            validateKeywords(metric.keywords(), where, problems);

            if (!isBlank(metric.key()) && !isBlank(metric.label())) {
                String established = labelByMetricKey.putIfAbsent(metric.key(), metric.label());
                if (established != null && !established.equals(metric.label())) {
                    problems.add(where + " is labelled \"" + metric.label() + "\" here but \""
                            + established + "\" elsewhere; one metric key means one thing");
                }
            }
        }
    }

    private void validateKey(String key, String where, List<String> problems) {
        if (isBlank(key)) {
            problems.add(where + " has no key");
            return;
        }
        if (!KEY_FORMAT.matcher(key).matches()) {
            problems.add(where + " has the key \"" + key + "\"; keys are UPPER_SNAKE "
                    + "(letters, digits and underscores, starting with a letter)");
        }
        if (key.length() > MAX_KEY_LENGTH) {
            problems.add(where + " has a key of " + key.length() + " characters; the column storing "
                    + "it holds " + MAX_KEY_LENGTH);
        }
    }

    private void validateKeywords(List<String> keywords, String where, List<String> problems) {
        if (keywords.isEmpty()) {
            problems.add(where + " has no keywords, so nothing in a text could ever trigger it");
            return;
        }
        Set<String> seen = new HashSet<>();
        for (String keyword : keywords) {
            if (isBlank(keyword)) {
                problems.add(where + " has a blank keyword");
            } else if (!seen.add(keyword.strip())) {
                problems.add(where + " lists the keyword \"" + keyword.strip() + "\" more than once");
            }
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** The file's top level. Bound loosely on purpose: validation reports better than binding does. */
    record CatalogFile(List<EventTypeDefinition> eventTypes) {
    }
}
