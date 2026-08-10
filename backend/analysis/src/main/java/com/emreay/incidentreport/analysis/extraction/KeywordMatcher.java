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
     * longer ending.
     */
    private static final String INFLECTION = "(?:l[ae]r|l[ıiuü]k?|d[aeıiuü]n?|t[aeıiuü]n?"
            + "|n[ıiuü]n|n[ıiuü]|s[ıiuü]|[ıiuü]yor|m[ae]k|m[ıiuü]ş|[ae]n|y[ae]|[ıiuü]n|[aeıiuü]){0,3}";

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
        String body = Arrays.stream(keyword.trim().split("\\s+", -1))
                .map(Pattern::quote)
                .collect(Collectors.joining("\\s+"));
        this.pattern = Pattern.compile("\\b" + body + INFLECTION + "\\b", UNICODE);
    }

    /**
     * Every occurrence, positioned in the raw text rather than the normalized one — the interface
     * highlights what the user wrote, not what we made of it (C-3).
     */
    List<ExtractedKeyword> findIn(NormalizedText text, KeywordRole role) {
        List<ExtractedKeyword> found = new ArrayList<>();
        Matcher matcher = pattern.matcher(text.value());

        while (matcher.find()) {
            found.add(new ExtractedKeyword(
                    text.originalTextIn(matcher.start(), matcher.end()),
                    role,
                    text.sourceStart(matcher.start()),
                    text.sourceEnd(matcher.end() - 1)));
        }
        return found;
    }
}
