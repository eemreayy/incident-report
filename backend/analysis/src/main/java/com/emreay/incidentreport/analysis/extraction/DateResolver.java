package com.emreay.incidentreport.analysis.extraction;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.Period;
import java.time.temporal.TemporalAmount;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.emreay.incidentreport.analysis.domain.DateSource;
import com.emreay.incidentreport.analysis.text.NormalizedText;

/**
 * Works out which day an incident belongs to (FR-06, TC-6).
 *
 * <p>Three ways a date can be arrived at, and the difference between them is kept (ADR-014): the
 * text names a calendar day, the text names a day relative to when the report was filed, or the text
 * says nothing about time at all. The second is an extraction, not a fallback — "son 24 saatte"
 * carries real information, and filing it as {@code DEFAULTED} would throw that information away.
 *
 * <p>Everything relative is measured against the <em>submission date of the raw report</em>, never
 * against now. The raw record never changes (ADR-005), so that anchor never moves, and reprocessing
 * a year-old report produces the same day it produced originally rather than dragging its history
 * forward.
 *
 * <p>The text handed in is already normalized: lower case, composed, apostrophes folded. The
 * patterns below rely on that and so are written in lower case only.
 */
@Component
public class DateResolver {

    /**
     * Without this, {@code \b} and {@code \w} stay ASCII: the boundary in "geçen" would be found
     * between "e" and "ç", and half the Turkish expressions here would match inside longer words.
     */
    private static final int UNICODE = Pattern.UNICODE_CHARACTER_CLASS;

    private static final Map<String, Integer> MONTHS = Map.ofEntries(
            Map.entry("ocak", 1), Map.entry("şubat", 2), Map.entry("mart", 3),
            Map.entry("nisan", 4), Map.entry("mayıs", 5), Map.entry("haziran", 6),
            Map.entry("temmuz", 7), Map.entry("ağustos", 8), Map.entry("eylül", 9),
            Map.entry("ekim", 10), Map.entry("kasım", 11), Map.entry("aralık", 12));

    private static final Pattern ISO_DATE =
            Pattern.compile("\\b(\\d{4})-(\\d{1,2})-(\\d{1,2})\\b", UNICODE);

    /** {@code 20.04.2020}, {@code 20/04/2020}, {@code 20-04-20} — day first, as Turkish writes it. */
    private static final Pattern NUMERIC_DATE =
            Pattern.compile("\\b(\\d{1,2})[./-](\\d{1,2})[./-](\\d{2,4})\\b", UNICODE);

    /** {@code 3 mayıs 2020}, and {@code 3 mayıs'ta} where the year is left to context. */
    private static final Pattern NAMED_MONTH_DATE = Pattern.compile(
            "\\b(\\d{1,2})\\s+(" + String.join("|", MONTHS.keySet()) + ")(?:'\\p{L}+)?(?:\\s+(\\d{4}))?",
            UNICODE);

    /**
     * Turkish case endings, spelled out rather than allowed as "any letters".
     *
     * <p>An open-ended suffix is how this goes wrong: {@code ay\\p{L}*} matches "ayrı", so
     * "son iki ayrı olayda" — two separate events — would be read as a two-month window, and
     * {@code dün\\p{L}*} matches "dünya". Both are silent misreadings of ordinary sentences.
     */
    private static final String SUFFIX = "(?:l[ıi]k|l[ae]r[ıi]nd[ae]|t[ae]|d[ae]|k[iıuü]|[ıiuü]n)?";

    /**
     * A window looking back from the moment of writing: "son 24 saatte", "son 3 günde", "son bir
     * haftada". The width is deliberately not captured — see {@link #resolve}.
     */
    private static final Pattern WINDOW = Pattern.compile(
            "\\bson\\s+(?:\\d{1,3}|\\p{L}+(?:\\s+\\p{L}+)?)\\s+(?:saat|gün|hafta|ay)" + SUFFIX + "\\b",
            UNICODE);

    /** Expressions that point at a different day rather than at a window ending today. */
    private static final List<Displacement> DISPLACEMENTS = List.of(
            new Displacement(Pattern.compile("\\b(?:önceki|evvelki)\\s+gün\\b", UNICODE), Period.ofDays(-2)),
            new Displacement(Pattern.compile("\\b(?:geçen|geçtiğimiz)\\s+hafta" + SUFFIX + "\\b", UNICODE), Period.ofDays(-7)),
            new Displacement(Pattern.compile("\\b(?:geçen|geçtiğimiz)\\s+ay" + SUFFIX + "\\b", UNICODE), Period.ofMonths(-1)),
            new Displacement(Pattern.compile("\\bdün(?:kü|den|e)?\\b", UNICODE), Period.ofDays(-1)),
            new Displacement(Pattern.compile("\\bbugün(?:kü|den|e|lerde)?\\b", UNICODE), Period.ZERO),
            new Displacement(Pattern.compile("\\bbu\\s+(?:hafta|sabah|akşam|gece)" + SUFFIX + "\\b", UNICODE), Period.ZERO));

    /**
     * The day the incident is filed under.
     *
     * <p>An explicit calendar date wins over a relative expression wherever both appear: naming a
     * day is more specific than pointing at one. Among equals, the first in the text wins.
     *
     * <p>A window is reduced to a single day — the reference day, where the window ends. Spreading
     * "son 3 günde" over three days would invent a distribution the text never gave, the same reason
     * a figure shared across provinces is never split between them (ADR-019). What is lost is the
     * width, not the day.
     *
     * @param referenceDate the raw report's submission date, in the configured zone
     */
    public ResolvedDate resolve(NormalizedText text, LocalDate referenceDate) {
        List<ResolvedDate> mentions = mentions(text, referenceDate);

        return mentions.stream()
                .filter(mention -> mention.source() == DateSource.EXPLICIT)
                .findFirst()
                .or(() -> mentions.stream().findFirst())
                .orElseGet(() -> ResolvedDate.defaulted(referenceDate));
    }

    /**
     * Every date expression in the text, in the order they appear. Offsets point into
     * {@link NormalizedText#value()}, so a caller working sentence by sentence can keep only the
     * ones inside the sentence it is looking at.
     */
    public List<ResolvedDate> mentions(NormalizedText text, LocalDate referenceDate) {
        List<ResolvedDate> mentions = new ArrayList<>();
        String value = text.value();

        addAll(mentions, ISO_DATE.matcher(value), m ->
                dateOf(number(m, 1), number(m, 2), number(m, 3)));
        addAll(mentions, NUMERIC_DATE.matcher(value), m ->
                dateOf(fullYear(number(m, 3)), number(m, 2), number(m, 1)));
        addAll(mentions, NAMED_MONTH_DATE.matcher(value), m ->
                namedMonthDate(m, referenceDate));

        for (Displacement displacement : DISPLACEMENTS) {
            Matcher matcher = displacement.pattern().matcher(value);
            while (matcher.find()) {
                mentions.add(ResolvedDate.found(referenceDate.plus(displacement.shift()),
                        DateSource.RELATIVE, matcher.start(), matcher.end()));
            }
        }

        Matcher window = WINDOW.matcher(value);
        while (window.find()) {
            mentions.add(ResolvedDate.found(referenceDate, DateSource.RELATIVE,
                    window.start(), window.end()));
        }

        mentions.sort(Comparator.comparingInt(ResolvedDate::start));
        return List.copyOf(mentions);
    }

    private void addAll(List<ResolvedDate> mentions, Matcher matcher, DateOf dateOf) {
        while (matcher.find()) {
            LocalDate date = dateOf.apply(matcher);
            if (date != null) {
                mentions.add(ResolvedDate.found(date, DateSource.EXPLICIT, matcher.start(), matcher.end()));
            }
        }
    }

    private LocalDate namedMonthDate(Matcher matcher, LocalDate referenceDate) {
        int day = number(matcher, 1);
        int month = MONTHS.get(matcher.group(2));

        if (matcher.group(3) != null) {
            return dateOf(number(matcher, 3), month, day);
        }
        // No year given. The reference year is the only sensible guess, except that it would put
        // "3 aralık" in the future for a report filed in January - so a date that has not happened
        // yet belongs to the year before.
        LocalDate inReferenceYear = dateOf(referenceDate.getYear(), month, day);
        if (inReferenceYear == null) {
            return null;
        }
        return inReferenceYear.isAfter(referenceDate)
                ? dateOf(referenceDate.getYear() - 1, month, day)
                : inReferenceYear;
    }

    /** {@code null} rather than an exception: "31.02.2020" is not a date, it is just three numbers. */
    private LocalDate dateOf(int year, int month, int day) {
        try {
            return LocalDate.of(year, month, day);
        } catch (DateTimeException notACalendarDay) {
            return null;
        }
    }

    private int fullYear(int year) {
        return year < 100 ? 2000 + year : year;
    }

    private int number(Matcher matcher, int group) {
        return Integer.parseInt(matcher.group(group));
    }

    private record Displacement(Pattern pattern, TemporalAmount shift) {
    }

    @FunctionalInterface
    private interface DateOf {
        LocalDate apply(Matcher matcher);
    }
}
