package com.emreay.incidentreport.analysis.text;

import java.text.BreakIterator;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

/**
 * Turns submitted Turkish text into something the extractors can match against, without losing
 * track of the text the user actually wrote (TC-5).
 *
 * <p>Four things happen, each because leaving it out breaks matching silently:
 *
 * <ul>
 *   <li><b>Turkish lower case.</b> {@code "İZMİR".toLowerCase()} under the default locale yields
 *       {@code "i̇zmi̇r"} — seven characters, with a combining dot — which matches no province in
 *       the table. With the Turkish locale it yields {@code "izmir"}. The mapping of i/İ/ı/I is the
 *       single most likely thing to go wrong here, and it fails without an error.
 *   <li><b>Unicode composition.</b> The same word typed on a different keyboard can arrive
 *       decomposed, and a decomposed string does not even lower-case to the same thing as its
 *       composed twin. NFC first makes the rest deterministic.
 *   <li><b>Punctuation folding.</b> Provinces arrive suffixed and apostrophised — {@code Ankara'da},
 *       {@code İzmir’de} — and the two apostrophes are different characters. Zero-width characters
 *       survive copy-paste from the web and split a keyword invisibly.
 *   <li><b>Whitespace collapsing.</b> Text pasted out of a PDF is full of line breaks mid-sentence.
 * </ul>
 *
 * <p>None of it is destructive: every character of the result remembers the range of the original
 * it came from, so {@link NormalizedText#originalTextIn} can hand back the user's own spelling.
 */
@Component
public class TurkishTextNormalizer {

    /**
     * Never {@code Locale.getDefault()}: it makes the result depend on the machine the JVM runs on,
     * so the same text analysed on a developer laptop and in the container could differ.
     */
    public static final Locale TURKISH = Locale.of("tr");

    private static final char APOSTROPHE = '\'';

    private final SentenceSplitter sentenceSplitter;

    public TurkishTextNormalizer(SentenceSplitter sentenceSplitter) {
        this.sentenceSplitter = sentenceSplitter;
    }

    public NormalizedText normalize(String rawText) {
        if (rawText == null) {
            throw new IllegalArgumentException("rawText must not be null");
        }

        StringBuilder value = new StringBuilder(rawText.length());
        int[] sourceStart = new int[rawText.length() + 1];
        int[] sourceEnd = new int[rawText.length() + 1];

        // Iterating grapheme clusters rather than chars keeps a base letter and its combining marks
        // together, so composing them can never leave half a character mapped to the wrong offset.
        BreakIterator graphemes = BreakIterator.getCharacterInstance(TURKISH);
        graphemes.setText(rawText);

        int from = graphemes.first();
        for (int to = graphemes.next(); to != BreakIterator.DONE; from = to, to = graphemes.next()) {
            String cluster = rawText.substring(from, to);

            if (isSpace(cluster)) {
                // A run of whitespace becomes one space that spans the whole run; leading whitespace
                // is dropped so offset zero is the first real character.
                if (value.isEmpty()) {
                    continue;
                }
                if (value.charAt(value.length() - 1) == ' ') {
                    sourceEnd[value.length() - 1] = to;
                } else {
                    sourceStart[value.length()] = from;
                    sourceEnd[value.length()] = to;
                    value.append(' ');
                }
                continue;
            }

            String folded = fold(Normalizer.normalize(cluster, Normalizer.Form.NFC));
            String lowered = folded.toLowerCase(TURKISH);

            if (value.length() + lowered.length() > sourceStart.length) {
                int grown = Math.max(sourceStart.length * 2, value.length() + lowered.length());
                sourceStart = Arrays.copyOf(sourceStart, grown);
                sourceEnd = Arrays.copyOf(sourceEnd, grown);
            }
            for (int i = 0; i < lowered.length(); i++) {
                sourceStart[value.length() + i] = from;
                sourceEnd[value.length() + i] = to;
            }
            value.append(lowered);
        }

        if (!value.isEmpty() && value.charAt(value.length() - 1) == ' ') {
            value.setLength(value.length() - 1);
        }

        String normalized = value.toString();
        List<Sentence> sentences = sentenceSplitter.split(normalized);
        return new NormalizedText(rawText, normalized,
                Arrays.copyOf(sourceStart, normalized.length()),
                Arrays.copyOf(sourceEnd, normalized.length()),
                sentences);
    }

    /**
     * Whether a cluster is a gap between words.
     *
     * <p>Not {@code String.isBlank()}: {@link Character#isWhitespace} deliberately excludes the
     * non-breaking space and its relatives, so a word joined by one would stay joined —
     * {@code "ankara'da sel"} would never match the keyword "sel". Text pasted out of a web
     * page is full of them.
     */
    private boolean isSpace(String cluster) {
        for (int i = 0; i < cluster.length(); i++) {
            char c = cluster.charAt(i);
            if (!Character.isWhitespace(c) && Character.getType(c) != Character.SPACE_SEPARATOR) {
                return false;
            }
        }
        return !cluster.isEmpty();
    }

    /**
     * Maps characters that mean the same thing onto one spelling, and drops the ones that mean
     * nothing at all.
     */
    private String fold(String cluster) {
        if (cluster.length() != 1) {
            return cluster;
        }
        return switch (cluster.charAt(0)) {
            // Typographic apostrophes and their lookalikes. Turkish suffixes hang off these, so a
            // province written İzmir’de must reduce to the same shape as İzmir'de.
            // U+2018/2019 quotes, U+02BC modifier apostrophe, U+00B4 acute, U+2032 prime, backtick.
            case '\u2018', '\u2019', '\u02bc', '\u00b4', '\u2032', '\u0060' -> String.valueOf(APOSTROPHE);
            // Invisible characters that survive a copy-paste and would split a keyword in two:
            // zero-width space, ZWNJ, ZWJ, BOM, soft hyphen. Written as escapes on purpose - as
            // literals they are unreadable here and a stray edit could not be spotted in review.
            case '\u200b', '\u200c', '\u200d', '\ufeff', '\u00ad' -> "";
            default -> cluster;
        };
    }
}
