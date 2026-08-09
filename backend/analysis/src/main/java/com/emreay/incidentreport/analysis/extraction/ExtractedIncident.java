package com.emreay.incidentreport.analysis.extraction;

import com.emreay.incidentreport.analysis.domain.ClassificationStatus;
import com.emreay.incidentreport.analysis.domain.DateSource;
import com.emreay.incidentreport.analysis.domain.ProvinceScope;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * One incident as the extractor found it, before anything is stored.
 *
 * <p>A plain value rather than the JPA entity, so extraction never needs a database: the rules for
 * reading Turkish text are hard enough to get right without a persistence context in the way, and
 * they stay testable with nothing but JUnit.
 *
 * <p>Provinces are referenced by their licence plate code rather than by entity, for the same
 * reason. The service resolves them when it maps this to an {@code Incident}.
 *
 * <p>Deliberately carries no invariant checks of its own. The rule that a province may only be
 * attached to a {@code SINGLE} record lives in {@code Incident}'s factory methods and in a check
 * constraint; stating it a third time here would mean three places to keep in step, and the mapping
 * step already fails loudly if this record contradicts itself.
 *
 * @param provinceCode         set only when the scope is {@link ProvinceScope#SINGLE}
 * @param sharedProvinceCodes  the provinces a {@link ProvinceScope#SHARED} figure spans — coverage,
 *                             never an allocation; the number is not divided among them (ADR-019)
 * @param metrics              extracted numbers, keyed by catalog metric name
 */
public record ExtractedIncident(LocalDate occurredOn,
                                DateSource dateSource,
                                ProvinceScope provinceScope,
                                Short provinceCode,
                                Set<Short> sharedProvinceCodes,
                                String eventType,
                                ClassificationStatus classification,
                                Map<String, Integer> metrics,
                                List<ExtractedKeyword> keywords) {

    public ExtractedIncident {
        Objects.requireNonNull(occurredOn, "occurredOn");
        Objects.requireNonNull(dateSource, "dateSource");
        Objects.requireNonNull(provinceScope, "provinceScope");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(classification, "classification");
        sharedProvinceCodes = sharedProvinceCodes == null ? Set.of() : Set.copyOf(sharedProvinceCodes);
        metrics = metrics == null ? Map.of() : Map.copyOf(metrics);
        keywords = keywords == null ? List.of() : List.copyOf(keywords);
    }
}
