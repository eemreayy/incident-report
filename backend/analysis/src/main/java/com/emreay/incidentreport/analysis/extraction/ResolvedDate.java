package com.emreay.incidentreport.analysis.extraction;

import java.time.LocalDate;
import java.util.Objects;

import com.emreay.incidentreport.analysis.domain.DateSource;

/**
 * A date an incident is filed under, together with how it was arrived at (ADR-014).
 *
 * <p>The source travels with the date because the two are read together: a point on a chart means
 * something different when the text stated the day than when the system fell back on the submission
 * date. Losing the distinction would let defaulted records pile up on one day invisibly.
 *
 * @param start where the expression begins in the normalized text, or {@code null} when nothing was
 *              found and the reference date was used
 * @param end   index just past the expression, or {@code null} for the same reason
 */
public record ResolvedDate(LocalDate date, DateSource source, Integer start, Integer end) {

    public ResolvedDate {
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(source, "source");
        boolean located = start != null && end != null;
        if (source == DateSource.DEFAULTED && located) {
            throw new IllegalArgumentException("a defaulted date cannot point at an expression");
        }
        if (source != DateSource.DEFAULTED && !located) {
            throw new IllegalArgumentException(source + " must say where it was read from");
        }
        if (located && (start < 0 || end <= start)) {
            throw new IllegalArgumentException("invalid date range [" + start + ", " + end + ")");
        }
    }

    static ResolvedDate found(LocalDate date, DateSource source, int start, int end) {
        return new ResolvedDate(date, source, start, end);
    }

    /** No time expression at all — the report's submission date stands in. */
    public static ResolvedDate defaulted(LocalDate referenceDate) {
        return new ResolvedDate(referenceDate, DateSource.DEFAULTED, null, null);
    }

    /** Whether the date was read out of the text rather than assumed. */
    public boolean wasExtracted() {
        return source != DateSource.DEFAULTED;
    }
}
