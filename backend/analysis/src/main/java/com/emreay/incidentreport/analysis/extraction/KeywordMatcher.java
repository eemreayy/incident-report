package com.emreay.incidentreport.analysis.extraction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.emreay.incidentreport.analysis.domain.KeywordRole;
import com.emreay.incidentreport.analysis.text.NormalizedText;

/**
 * Finds one catalog keyword in a text, through whatever Turkish has attached to it.
 *
 * <p>The catalog writes stems on purpose — {@code hayatını kaybet}, {@code kurtarıl} — because the
 * ending changes with the sentence. So a keyword has to match its inflected forms, and the endings
 * are enumerated rather than allowed as "any letters" for the third time in this pipeline: an
 * open-ended suffix reads "testere" as the keyword "test" the same way it read "vanilya" as the
 * province Van (ADR-030) and "son iki ayrı olayda" as a two-month window (ADR-029).
 *
 * <p>A keyword the catalog cannot inflect this way is not a bug to fix here — the catalog is
 * configuration, and the extra form can simply be added to it (ADR-007).
 */
final class KeywordMatcher {

    private static final int UNICODE = Pattern.UNICODE_CHARACTER_CLASS;

    /**
     * Turkish endings, stacked up to three deep: "kazalarda" is kaza + lar + da, and
     * "kaybettiler" is kaybet + ti + ler.
     *
     * <p>The bare vowel comes last: it matches the most and would otherwise swallow the start of a
     * longer ending. The converb endings — "-erek", "-erken", "-ince" — are here because the source
     * document uses one: "hayatını kaybederken" is a death, and without "-erken" the sentence
     * hands its figure to the next keyword along instead.
     */
    private static final String INFLECTION = "(?:l[ae]r|l[ıiuü]k?|d[aeıiuü]n?|t[aeıiuü]n?"
            + "|[ae]rken|[ae]r[ae]k|[ıiuü]nc[ae]|n[ıiuü]n|n[ıiuü]|s[ıiuü]|[ıiuü]yor|m[ae]k"
            + "|m[ıiuü]ş|[ae]n|y[ae]|[ıiuü]p|[ıiuü]n|[aeıiuü]){0,3}";

    private final Pattern pattern;

    /**
     * @param keyword the keyword as the catalog spells it, already normalized the same way the text
     *                is, so the two are reduced by one set of rules rather than two similar ones
     */
    KeywordMatcher(String keyword) {
        // Whitespace in a multi-word keyword is matched loosely: the text has been collapsed to
        // single spaces, but the catalog is hand-written and need not be. The words themselves are
        // quoted - the catalog is edited by hand, and a stray character in it should not become a
        // regex that matches something else entirely.
        String[] words = keyword.trim().split("\\s+", -1);
        words[words.length - 1] = softenable(words[words.length - 1]);
        String body = String.join("\\s+", words);
        this.pattern = Pattern.compile("\\b" + body + INFLECTION + "\\b", UNICODE);
    }

    /**
     * Every occurrence, carrying both positions.
     *
     * <p>The keyword itself is positioned in the <em>raw</em> text, because the interface highlights
     * what the user wrote rather than what we made of it (C-3). The normalized position comes along
     * because attribution reasons in normalized space — which sentence a hit is in, how far it is
     * from a number — and deriving one from the other after the fact is not possible: collapsed
     * whitespace means several normalized characters can share a raw offset.
     */
    List<KeywordHit> findIn(NormalizedText text, KeywordRole role) {
        List<KeywordHit> found = new ArrayList<>();
        Matcher matcher = pattern.matcher(text.value());

        while (matcher.find()) {
            ExtractedKeyword keyword = new ExtractedKeyword(
                    text.originalTextIn(matcher.start(), matcher.end()),
                    role,
                    text.sourceStart(matcher.start()),
                    text.sourceEnd(matcher.end() - 1));
            found.add(new KeywordHit(keyword, matcher.start(), matcher.end()));
        }
        return found;
    }

    /**
     * A stem ending in a hard consonant softens before a vowel: "kaybet" becomes "kaybed" in
     * "hayatını kaybederken". Without this the catalog would have to list both spellings of every
     * verb it uses, and the one it lists would be the one that loses.
     */
    private String softenable(String word) {
        int last = word.length() - 1;
        String soft = switch (word.charAt(last)) {
            case 'p' -> "b";
            case 'ç' -> "c";
            case 't' -> "d";
            case 'k' -> "ğg";
            default -> "";
        };
        if (soft.isEmpty()) {
            return Pattern.quote(word);
        }
        return Pattern.quote(word.substring(0, last)) + "[" + word.charAt(last) + soft + "]";
    }

    /** One occurrence: what to show the user, and where it sits in the text we reason about. */
    record KeywordHit(ExtractedKeyword keyword, int start, int end) {
    }
}
