package com.emreay.incidentreport.analysis.text;

/**
 * A number found in the text, and where it was found.
 *
 * <p>The offsets are what make this useful: metric matching (TC-3) decides which metric a number
 * belongs to by how close the two are, so a number that has lost its position cannot be attributed
 * to anything.
 *
 * <p>The value is a {@code long} although metrics are stored as {@code int}. The text decides how
 * big the number is, not the column — "iki milyar" is a number the language can express, and
 * silently wrapping it into an {@code int} would turn an implausible figure into a plausible wrong
 * one. Whoever maps a token onto a metric is the one that has to reject it.
 *
 * @param start index of the first character in the normalized text, inclusive
 * @param end   index just past the last character
 */
public record NumberToken(long value, int start, int end, NumberNotation notation) {

    public NumberToken {
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("invalid number range [" + start + ", " + end + ")");
        }
    }

    /** Whether this number fits the {@code int} a metric value is stored in. */
    public boolean fitsMetricValue() {
        return value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE;
    }
}
