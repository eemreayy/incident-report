package com.emreay.incidentreport.analysis.extraction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.emreay.incidentreport.analysis.text.NormalizedText;
import com.emreay.incidentreport.analysis.text.TurkishTextNormalizer;

/**
 * Finds the provinces a text names (TC-7).
 *
 * <p>The list comes from the reference data, not from a constant in here: the migration that seeds
 * the 81 provinces is the single source of truth for both storage and recognition, so a province
 * cannot be recognisable without being storable or the other way round.
 *
 * <p>Turkish attaches its case endings to the noun, and proper nouns take an apostrophe first —
 * {@code Ankara'da}, {@code Kocaeli'nde}, {@code İzmir'de}. In practice the apostrophe is dropped
 * as often as not, so both spellings have to be read. The endings themselves are enumerated rather
 * than allowed as "any letters", which matters more than it looks: "Ordu" is also the word for an
 * army, "Van" is also a vehicle, and an open-ended suffix turns "vanilya" into a province.
 */
public class ProvinceExtractor {

    private static final int UNICODE = Pattern.UNICODE_CHARACTER_CLASS;

    /**
     * Case endings a place name takes, with or without the apostrophe that should precede them.
     *
     * <p>Longer alternatives come first: regex alternation takes the first that matches, so "dan"
     * has to be offered before "da" or "Ankara'dan" would be read as "Ankara'da" plus a stray "n".
     */
    private static final String SUFFIX =
            "(?:'?(?:nd[ae]n|nd[ae]|d[ae]n|t[ae]n|n[ıiuü]n|[ıiuü]nd[ae]|d[ae]|t[ae]|l[ıi]|y[ae]|[ae]))?";

    /**
     * Words that turn the name before them into a district, a neighbourhood or a village.
     *
     * <p>"İstanbul'un Aksaray semtinde" names one province, not two — Aksaray is a province as well
     * as a district of İstanbul, and without this the sentence would produce a record for a city
     * 200 km away.
     */
    private static final Pattern DISTRICT_MARKER = Pattern.compile(
            "\\s*(?:ilçe|semt|mahalle|belde|köy|bucak|beldesi)\\p{L}*\\b", UNICODE);

    private final Pattern pattern;
    private final Map<String, Province> byNormalizedName;

    /**
     * @param provinces  the reference data: licence-plate code to canonical name
     * @param normalizer the same normalizer the text goes through, so a name and its mention are
     *                   reduced by identical rules rather than by two similar-looking ones
     */
    public ProvinceExtractor(Map<Short, String> provinces, TurkishTextNormalizer normalizer) {
        if (provinces.isEmpty()) {
            throw new IllegalArgumentException("no provinces to recognise; is the reference data loaded?");
        }

        this.byNormalizedName = new HashMap<>();
        provinces.forEach((code, name) ->
                byNormalizedName.put(normalizer.normalize(name).value(), new Province(code, name)));

        String alternatives = byNormalizedName.keySet().stream()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .map(Pattern::quote)
                .reduce((a, b) -> a + "|" + b)
                .orElseThrow();

        this.pattern = Pattern.compile("\\b(" + alternatives + ")" + SUFFIX + "\\b", UNICODE);
    }

    /**
     * Every province named in the text, in the order they appear, one entry per mention.
     *
     * <p>Repeats are kept. "Bursa'da 8 kaza … Bursa'da 1 kişi" names Bursa twice and means two
     * different things by it; collapsing the two would take the second figure's anchor away.
     */
    public List<ProvinceMention> mentions(NormalizedText text) {
        List<ProvinceMention> mentions = new ArrayList<>();
        String value = text.value();
        Matcher matcher = pattern.matcher(value);

        while (matcher.find()) {
            if (isDistrict(value, matcher.end())) {
                continue;
            }
            Province province = byNormalizedName.get(matcher.group(1));
            mentions.add(new ProvinceMention(
                    province.code(), province.name(), matcher.start(), matcher.end()));
        }
        return List.copyOf(mentions);
    }

    /** Reference data as this class needs it, without dragging the JPA entity into extraction. */
    private record Province(short code, String name) {
    }

    private boolean isDistrict(String value, int from) {
        return DISTRICT_MARKER.matcher(value).region(from, value.length()).lookingAt();
    }
}
