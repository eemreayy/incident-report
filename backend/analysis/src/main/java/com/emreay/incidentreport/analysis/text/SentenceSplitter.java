package com.emreay.incidentreport.analysis.text;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

/**
 * Splits normalized Turkish text into sentences.
 *
 * <p>Written by hand rather than delegating to {@code BreakIterator}, because measuring showed the
 * built-in one gets both of the cases that matter here wrong. Given
 * "20.04.2020 tarihinde ... tespit edildi. 1 kişi vefat etti." it returns a <em>single</em>
 * sentence — it declines to break before a digit — which would let a number from one statement be
 * matched against a metric from another. And it breaks after "Dr.", producing a one-word sentence.
 * Both failures are silent, and both corrupt everything downstream.
 *
 * <p>The rules here are few and each exists for a case the source document actually contains.
 */
@Component
public class SentenceSplitter {

    /**
     * Words that end in a full stop without ending a sentence.
     *
     * <p>Deliberately biased towards <em>not</em> splitting. A sentence wrongly joined still holds
     * every number next to its own keyword; a sentence wrongly split separates them, and the number
     * is then either lost or attached to the wrong thing. The first mistake costs precision, the
     * second costs correctness.
     */
    private static final Set<String> ABBREVIATIONS = Set.of(
            "dr", "doç", "prof", "op", "av", "sn", "sy", "no", "nu",
            "vb", "vs", "bkz", "örn", "yy", "sf", "md", "krş",
            "alb", "gen", "yzb", "ütğm", "tğm",
            "mah", "cad", "sok", "apt", "blv", "tel", "st");

    private static final String TERMINATORS = ".!?…";

    public List<Sentence> split(String text) {
        List<Sentence> sentences = new ArrayList<>();
        int sentenceStart = 0;

        for (int i = 0; i < text.length(); i++) {
            if (TERMINATORS.indexOf(text.charAt(i)) < 0) {
                continue;
            }
            if (text.charAt(i) == '.' && !endsSentence(text, i)) {
                continue;
            }

            // "..." and "!?" are one ending, not several.
            int end = i + 1;
            while (end < text.length() && TERMINATORS.indexOf(text.charAt(end)) >= 0) {
                end++;
            }
            // A terminator with more text pressed against it is part of a token, not an ending.
            if (end < text.length() && !Character.isWhitespace(text.charAt(end))) {
                i = end - 1;
                continue;
            }

            addIfMeaningful(sentences, text, sentenceStart, end);
            sentenceStart = end;
            i = end - 1;
        }

        addIfMeaningful(sentences, text, sentenceStart, text.length());
        return List.copyOf(sentences);
    }

    /**
     * Whether a full stop at {@code index} ends a sentence.
     *
     * <p>Two reasons it might not: it sits inside a number — {@code 20.04.2020}, {@code 1.500} —
     * or the word before it is an abbreviation.
     */
    private boolean endsSentence(String text, int index) {
        boolean digitBefore = index > 0 && Character.isDigit(text.charAt(index - 1));
        boolean digitAfter = index + 1 < text.length() && Character.isDigit(text.charAt(index + 1));
        if (digitBefore && digitAfter) {
            return false;
        }
        return !ABBREVIATIONS.contains(wordBefore(text, index));
    }

    private String wordBefore(String text, int index) {
        int start = index;
        while (start > 0 && Character.isLetter(text.charAt(start - 1))) {
            start--;
        }
        return text.substring(start, index);
    }

    /**
     * Trims the range before recording it, so offsets point at the sentence rather than the gap,
     * and drops what has nothing in it to extract - a stretch of bare punctuation is not a sentence.
     */
    private void addIfMeaningful(List<Sentence> sentences, String text, int start, int end) {
        int from = start;
        int to = end;
        while (from < to && Character.isWhitespace(text.charAt(from))) {
            from++;
        }
        while (to > from && Character.isWhitespace(text.charAt(to - 1))) {
            to--;
        }
        if (from < to && hasContent(text, from, to)) {
            sentences.add(new Sentence(text.substring(from, to), from, to));
        }
    }

    private boolean hasContent(String text, int from, int to) {
        for (int i = from; i < to; i++) {
            if (Character.isLetterOrDigit(text.charAt(i))) {
                return true;
            }
        }
        return false;
    }
}
