package com.emreay.incidentreport.analysis.text;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * Finds the numbers in a normalized text, however they are written (FR-05, TC-4).
 *
 * <p>Turkish writes counts three ways and the source document uses all of them: digits
 * ({@code 15 vaka}), words ({@code on iki bina}), and the mixture news text is full of
 * ({@code 2 bin 500 kişi}). They have to produce the same value, because a metric does not care how
 * its number was spelled.
 *
 * <p>Every token keeps its offset. Metric matching (TC-3) decides which metric a number belongs to
 * by how close they are in the sentence, so a value without a position cannot be attributed at all.
 */
@Component
public class NumberExtractor {

    /**
     * Dates, so their parts are not mistaken for counts.
     *
     * <p>{@code 20.04.2020} is one date, not the numbers 20, 4 and 2020 — and those three would be
     * three plausible-looking metric values sitting right next to a keyword. Dates written in words
     * ({@code 3 Mayıs 2020}) cannot be recognised here without a calendar, so the numbers in them
     * are still emitted; excluding those is the job of whoever holds the resolved date span.
     */
    private static final Pattern DATE_LIKE = Pattern.compile(
            "\\d{1,2}[./-]\\d{1,2}[./-]\\d{2,4}|\\d{4}[./-]\\d{1,2}[./-]\\d{1,2}");

    /**
     * A road designator, so its digits are not mistaken for a count: {@code D-100} is the state
     * highway D-100, not one hundred of anything. Turkish road codes are a short letter prefix and
     * digits joined by a hyphen with no space ({@code D-100}, {@code E-80}, {@code O-4}).
     */
    private static final Pattern ROUTE_CODE = Pattern.compile("\\b\\p{L}{1,3}-\\d+\\b");

    /** A digit group, with Turkish thousands separators or a decimal part. */
    private static final Pattern DIGIT_GROUP = Pattern.compile("\\d{1,3}(?:\\.\\d{3})+|\\d+(?:,\\d+)?");

    private static final Pattern ATOM = Pattern.compile(DIGIT_GROUP.pattern() + "|\\p{L}+");

    private static final Map<String, Integer> UNITS = Map.of(
            "sıfır", 0, "bir", 1, "iki", 2, "üç", 3, "dört", 4,
            "beş", 5, "altı", 6, "yedi", 7, "sekiz", 8, "dokuz", 9);

    private static final Map<String, Integer> TENS = Map.of(
            "on", 10, "yirmi", 20, "otuz", 30, "kırk", 40, "elli", 50,
            "altmış", 60, "yetmiş", 70, "seksen", 80, "doksan", 90);

    /**
     * Multipliers. {@code yüz} scales what is being built; the rest close it off and start the next
     * part, which is why they are ordered and {@code yüz} is not.
     */
    private static final Map<String, Long> SCALES = new LinkedHashMap<>(Map.of(
            "yüz", 100L, "bin", 1_000L, "milyon", 1_000_000L, "milyar", 1_000_000_000L));

    private static final int UNIT_RANK = 0;
    private static final int TENS_RANK = 1;

    /** Digits do not combine with number words on their left, only with a scale on their right. */
    private static final int DIGIT_RANK = -1;

    private static final int NO_ADDITIVE_YET = Integer.MAX_VALUE;

    /**
     * Finds every number in the normalized text. Offsets point into {@link NormalizedText#value()},
     * so a caller holding a {@link Sentence} can keep only the tokens inside it.
     */
    public List<NumberToken> extract(NormalizedText text) {
        return extract(text.value());
    }

    List<NumberToken> extract(String text) {
        List<Atom> atoms = atomsOf(text);
        List<NumberToken> tokens = new ArrayList<>();

        int i = 0;
        while (i < atoms.size()) {
            if (atoms.get(i).rejected()) {
                i = skipRejected(text, atoms, i);
                continue;
            }
            Group group = new Group();
            int j = i;
            while (j < atoms.size()
                    && (j == i || onlySpaceBetween(text, atoms.get(j - 1).end(), atoms.get(j).start()))
                    && group.accept(atoms.get(j))) {
                j++;
            }
            if (j == i) {
                // A fresh group accepts anything, so this cannot happen today. It is here so that a
                // future rule cannot turn into an empty token or a loop that never advances.
                i++;
                continue;
            }
            tokens.add(group.toToken());
            i = j;
        }
        return List.copyOf(tokens);
    }

    /**
     * Steps past a figure that is not a count, taking its multiplier with it.
     *
     * <p>"2,5 milyar lira" is the case. Dropping only the "2,5" would leave "milyar" standing alone,
     * and a bare "milyar" reads as one billion — so refusing to read a figure would end up inventing
     * a different one.
     */
    private int skipRejected(String text, List<Atom> atoms, int index) {
        int next = index + 1;
        while (next < atoms.size()
                && atoms.get(next).scale()
                && onlySpaceBetween(text, atoms.get(next - 1).end(), atoms.get(next).start())) {
            next++;
        }
        return next;
    }

    /** The pieces a number can be built from, in the order they appear. */
    private List<Atom> atomsOf(String text) {
        List<int[]> excluded = new ArrayList<>();
        Matcher dateMatcher = DATE_LIKE.matcher(text);
        while (dateMatcher.find()) {
            excluded.add(new int[]{dateMatcher.start(), dateMatcher.end()});
        }
        Matcher routeMatcher = ROUTE_CODE.matcher(text);
        while (routeMatcher.find()) {
            excluded.add(new int[]{routeMatcher.start(), routeMatcher.end()});
        }

        List<Atom> atoms = new ArrayList<>();
        Matcher matcher = ATOM.matcher(text);
        while (matcher.find()) {
            if (overlapsAny(excluded, matcher.start(), matcher.end())) {
                continue;
            }
            Atom atom = atomOf(matcher.group(), matcher.start(), matcher.end());
            if (atom != null) {
                atoms.add(atom);
            }
        }
        return atoms;
    }

    private Atom atomOf(String token, int start, int end) {
        if (Character.isDigit(token.charAt(0))) {
            // A figure with a decimal part is not a count of anything, and splitting it would
            // invent two numbers the text never gave.
            if (token.indexOf(',') >= 0) {
                return rejected(start, end);
            }
            try {
                long value = Long.parseLong(token.replace(".", ""));
                return new Atom(value, start, end, DIGIT_RANK, false, true, false);
            } catch (NumberFormatException tooLongToBeACount) {
                return rejected(start, end);
            }
        }

        Integer unit = UNITS.get(token);
        if (unit != null) {
            return new Atom(unit, start, end, UNIT_RANK, false, false, false);
        }
        Integer tens = TENS.get(token);
        if (tens != null) {
            return new Atom(tens, start, end, TENS_RANK, false, false, false);
        }
        Long scale = SCALES.get(token);
        if (scale != null) {
            return new Atom(scale, start, end, NO_ADDITIVE_YET, true, false, false);
        }
        // Everything else, including "onlarca" and "yüzlerce": vague quantities, not counts.
        return null;
    }

    private Atom rejected(int start, int end) {
        return new Atom(0, start, end, NO_ADDITIVE_YET, false, true, true);
    }

    private boolean overlapsAny(List<int[]> spans, int start, int end) {
        return spans.stream().anyMatch(span -> start < span[1] && span[0] < end);
    }

    private boolean onlySpaceBetween(String text, int from, int to) {
        for (int i = from; i < to; i++) {
            if (!Character.isWhitespace(text.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * @param rejected a figure that is present in the text but is not a count - see
     *                 {@link #atomOf}. It is kept rather than dropped because it still occupies its
     *                 place: the words around it must not be read as though it were absent.
     */
    private record Atom(long value, int start, int end, int rank,
                        boolean scale, boolean digits, boolean rejected) {
    }

    /**
     * One number being assembled from consecutive atoms.
     *
     * <p>Refusing an atom is how a group ends, and the refusals are what keep the reading honest:
     * "bir iki kişi" means "a couple of people", not three, so two units in a row start a new
     * number rather than adding up.
     */
    private static final class Group {

        private long total;
        private long current;
        private int lastAdditiveRank = NO_ADDITIVE_YET;
        private long lastOrderedScale = Long.MAX_VALUE;
        private boolean hundredUsed;
        private boolean sawDigits;
        private boolean sawWords;
        private int start;
        private int end;

        /** @return whether the atom extended this group; {@code false} closes it. */
        boolean accept(Atom atom) {
            boolean empty = start == end;
            if (!canAccept(atom)) {
                return false;
            }
            try {
                apply(atom);
            } catch (ArithmeticException tooBigToBeReal) {
                return false;
            }
            if (empty) {
                start = atom.start();
            }
            end = atom.end();
            if (atom.digits()) {
                sawDigits = true;
            } else {
                sawWords = true;
            }
            return true;
        }

        private boolean canAccept(Atom atom) {
            if (atom.scale()) {
                if (atom.value() == 100) {
                    return !hundredUsed;
                }
                return atom.value() < lastOrderedScale;
            }
            if (atom.digits()) {
                // Digits open a number; they never continue one that already has a value in hand.
                return current == 0 && lastAdditiveRank == NO_ADDITIVE_YET;
            }
            // "on iki" is 12 because 2 is smaller than 10. "iki on" is not a number.
            return atom.rank() < lastAdditiveRank;
        }

        private void apply(Atom atom) {
            if (!atom.scale()) {
                current = Math.addExact(current, atom.value());
                lastAdditiveRank = atom.rank();
                return;
            }
            long multiplied = Math.multiplyExact(Math.max(current, 1), atom.value());
            if (atom.value() == 100) {
                current = multiplied;
                hundredUsed = true;
            } else {
                total = Math.addExact(total, multiplied);
                current = 0;
                hundredUsed = false;
                lastOrderedScale = atom.value();
            }
            lastAdditiveRank = NO_ADDITIVE_YET;
        }

        NumberToken toToken() {
            return new NumberToken(total + current, start, end, notation());
        }

        private NumberNotation notation() {
            if (sawDigits && sawWords) {
                return NumberNotation.MIXED;
            }
            return sawDigits ? NumberNotation.DIGITS : NumberNotation.WORDS;
        }
    }
}
