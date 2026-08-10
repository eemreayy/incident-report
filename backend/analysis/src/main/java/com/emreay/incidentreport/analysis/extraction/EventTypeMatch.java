package com.emreay.incidentreport.analysis.extraction;

import java.util.List;
import java.util.Objects;

import com.emreay.incidentreport.analysis.domain.ClassificationStatus;

/**
 * An event type the text was found to be about, and the words that say so.
 *
 * <p>There is no confidence number here, deliberately (ADR-031). What a reader can act on is the
 * evidence: "earthquake, because deprem and enkaz appear here and here" is checkable, while "0.72"
 * is a figure with no defined meaning that invites a threshold nobody can justify.
 *
 * @param score    how many distinct catalog keywords matched — used to rank, not to gate
 * @param evidence every match, positioned in the <em>raw</em> text so the interface can highlight
 *                 what drove the decision (FR-17, C-3)
 */
public record EventTypeMatch(String eventType,
                             ClassificationStatus status,
                             int score,
                             List<ExtractedKeyword> evidence) {

    public EventTypeMatch {
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(status, "status");
        if (score < 0) {
            throw new IllegalArgumentException("score cannot be negative: " + score);
        }
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
        if (status == ClassificationStatus.CLASSIFIED && evidence.isEmpty()) {
            throw new IllegalArgumentException("a classified event type must say what matched");
        }
        if (status == ClassificationStatus.UNCLASSIFIED && !evidence.isEmpty()) {
            throw new IllegalArgumentException("nothing matched, so there is nothing to show");
        }
    }

    public boolean isClassified() {
        return status == ClassificationStatus.CLASSIFIED;
    }
}
