package com.emreay.incidentreport.analysis.query;

import java.time.LocalDate;

import com.emreay.incidentreport.shared.error.DomainValidationException;

/**
 * The one rule both query records share: a range that ends before it starts is a question with no
 * possible answer, and is refused rather than answered with an empty result.
 *
 * <p>It is refused as a <em>validation</em> failure, not as a programming error. The two endpoints
 * take these dates straight from a query string, so an impossible range is something a caller typed
 * — and an {@link IllegalArgumentException} would reach the caller as a 500, telling them the
 * server broke when in fact their request did. A date picker makes this trivially reachable from the
 * interface (FR-21), which is what turned it from a theoretical difference into a visible one.
 */
final class InvalidDateRange {

    /** Stable and machine-readable; the interface translates this, never the English message. */
    static final String CODE = "query.date-range.invalid";

    private InvalidDateRange() {
    }

    static void reject(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new DomainValidationException(CODE,
                    "The start date is after the end date: " + from + " > " + to);
        }
    }
}
