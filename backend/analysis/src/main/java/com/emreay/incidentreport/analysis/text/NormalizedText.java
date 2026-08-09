package com.emreay.incidentreport.analysis.text;

import java.util.List;
import java.util.Objects;

/**
 * A text prepared for matching, that still knows where it came from.
 *
 * <p>Extraction runs against {@link #value()} — normalized, lower-cased, predictable. But the
 * contract owes the user the keyword's position in the text <em>they</em> submitted (PRD §8.2, C-3),
 * and normalization does not preserve positions: NFC composition alone turns a 21-character decomposed
 * string into a 15-character one. So every character of the normalized value carries the range of the
 * original it was produced from, and a match can always be pointed back at the raw text.
 *
 * <p>Not a record: it holds arrays, and a record's generated {@code equals} would compare them by
 * identity — quietly wrong.
 */
public final class NormalizedText {

    private final String original;
    private final String value;
    private final int[] sourceStart;
    private final int[] sourceEnd;
    private final List<Sentence> sentences;

    NormalizedText(String original, String value, int[] sourceStart, int[] sourceEnd, List<Sentence> sentences) {
        this.original = original;
        this.value = value;
        this.sourceStart = sourceStart;
        this.sourceEnd = sourceEnd;
        this.sentences = List.copyOf(sentences);
    }

    /** The text exactly as submitted. */
    public String original() {
        return original;
    }

    /** The text to run extraction against: NFC, Turkish lower case, folded punctuation. */
    public String value() {
        return value;
    }

    /** Sentences of {@link #value()}, in order. Offsets are into {@code value()}. */
    public List<Sentence> sentences() {
        return sentences;
    }

    public boolean isEmpty() {
        return value.isEmpty();
    }

    /** Where in {@link #original()} the character at {@code index} of {@link #value()} began. */
    public int sourceStart(int index) {
        return sourceStart[checked(index)];
    }

    /** Where in {@link #original()} the character at {@code index} of {@link #value()} ended. */
    public int sourceEnd(int index) {
        return sourceEnd[checked(index)];
    }

    /**
     * The original text that produced {@code value().substring(start, end)} — what C-3 has to
     * report back, spelled the way the user spelled it.
     *
     * @param start index into {@link #value()}, inclusive
     * @param end   index into {@link #value()}, exclusive
     */
    public String originalTextIn(int start, int end) {
        Objects.checkFromToIndex(start, end, value.length());
        if (start == end) {
            return "";
        }
        return original.substring(sourceStart(start), sourceEnd(end - 1));
    }

    private int checked(int index) {
        Objects.checkIndex(index, value.length());
        return index;
    }
}
