package com.emreay.incidentreport.analysis.extraction;

import com.emreay.incidentreport.analysis.domain.KeywordRole;

import java.util.Objects;

/**
 * A word the extractor reacted to, with where it found it.
 *
 * <p>Offsets point into the raw text held in MongoDB, so a caller can highlight exactly what drove
 * a decision (FR-17). They are nullable because not every match can be located precisely — a date
 * assembled from several tokens has no single position.
 */
public record ExtractedKeyword(String keyword, KeywordRole role, Integer charStart, Integer charEnd) {

    public ExtractedKeyword {
        Objects.requireNonNull(keyword, "keyword");
        Objects.requireNonNull(role, "role");
    }
}
