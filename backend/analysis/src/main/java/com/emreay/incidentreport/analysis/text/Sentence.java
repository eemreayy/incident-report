package com.emreay.incidentreport.analysis.text;

/**
 * One sentence of a normalized text, and where it sits in it.
 *
 * <p>Sentences matter because proximity does. "Bursa'da 8, Kocaeli'nde 6 trafik kazası" only means
 * what it means because those numbers and those provinces share a sentence; a number in the next
 * sentence belongs to something else. Every rule that pairs a number with a metric or a province
 * works inside these boundaries (TC-3).
 *
 * @param text  the sentence itself, already normalized
 * @param start index of its first character in {@link NormalizedText#value()}
 * @param end   index just past its last character
 */
public record Sentence(String text, int start, int end) {

    public Sentence {
        if (start < 0 || end < start) {
            throw new IllegalArgumentException("invalid sentence range [" + start + ", " + end + ")");
        }
    }

    public int length() {
        return end - start;
    }
}
