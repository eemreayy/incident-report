package com.emreay.incidentreport.analysis.query;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;

import com.emreay.incidentreport.analysis.domain.Incident;
import com.emreay.incidentreport.analysis.domain.IncidentKeyword;
import com.emreay.incidentreport.analysis.domain.Province;

/** Turns an {@link IncidentQuery} into the predicates that answer it. */
public final class IncidentSpecifications {

    private IncidentSpecifications() {
    }

    public static Specification<Incident> matching(IncidentQuery query) {
        return (root, criteria, builder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (query.rawReportId() != null) {
                predicates.add(builder.equal(root.get("rawReportId"), query.rawReportId()));
            }
            if (!query.eventTypes().isEmpty()) {
                predicates.add(root.get("eventType").in(query.eventTypes()));
            }
            if (query.from() != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("occurredOn"), query.from()));
            }
            if (query.to() != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("occurredOn"), query.to()));
            }
            if (!query.provinces().isEmpty()) {
                predicates.add(province(root, criteria, builder, query));
            }
            if (query.keyword() != null) {
                predicates.add(keyword(root, criteria, builder, query.keyword()));
            }

            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * A province filter has to see two kinds of record.
     *
     * <p>The obvious one is a record filed against that province. The other is a figure the text
     * gave for several provinces at once: it belongs to none of them individually, so it cannot be
     * matched on {@code province}, but dropping it would make the province totals stop reconciling
     * with the overall one (ADR-019). It is matched through the link table instead — and because
     * that join multiplies rows when more than one selected province covers the same record, the
     * query is distinct. Selecting Bursa and Kocaeli must not return their shared total twice.
     */
    private static Predicate province(jakarta.persistence.criteria.Root<Incident> root,
                                      jakarta.persistence.criteria.CriteriaQuery<?> criteria,
                                      jakarta.persistence.criteria.CriteriaBuilder builder,
                                      IncidentQuery query) {
        criteria.distinct(true);

        Join<Incident, Province> shared = root.join("sharedProvinces", JoinType.LEFT);
        return builder.or(
                root.get("province").get("code").in(query.provinces()),
                shared.get("code").in(query.provinces()));
    }

    /**
     * Matched against what the extractor recorded, not against the submitted text. Searching the
     * raw text is out of scope (PRD §2.3), and the keywords are the reason a record exists at all.
     */
    private static Predicate keyword(jakarta.persistence.criteria.Root<Incident> root,
                                     jakarta.persistence.criteria.CriteriaQuery<?> criteria,
                                     jakarta.persistence.criteria.CriteriaBuilder builder,
                                     String keyword) {
        criteria.distinct(true);

        Join<Incident, IncidentKeyword> keywords = root.join("keywords", JoinType.INNER);
        // Turkish lower case cannot be done in SQL, so both sides are lowered by the database's
        // rules and the pattern is prepared the same way: consistent, if not linguistically perfect.
        return builder.like(
                builder.lower(keywords.get("keyword")),
                "%" + keyword.toLowerCase(Locale.of("tr")) + "%");
    }
}
