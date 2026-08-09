package com.emreay.incidentreport.analysis.text;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * The splitter is fed already-normalized text, so every case here is lower case.
 *
 * <p>Two of these cases are the reason this class exists rather than a call to
 * {@code BreakIterator.getSentenceInstance}: the built-in iterator joins a sentence pair when the
 * second one opens with a digit, and breaks "dr." off into a sentence of its own.
 */
class SentenceSplitterTest {

    private final SentenceSplitter splitter = new SentenceSplitter();

    static Stream<Arguments> texts() {
        return Stream.of(
                arguments("a date is not a sentence end",
                        "20.04.2020 tarihinde ankara'da 15 vaka tespit edildi. 1 kişi vefat etti.",
                        List.of("20.04.2020 tarihinde ankara'da 15 vaka tespit edildi.", "1 kişi vefat etti.")),
                arguments("a thousands separator is not one either",
                        "sel nedeniyle 1.500 kişi tahliye edildi.",
                        List.of("sel nedeniyle 1.500 kişi tahliye edildi.")),
                arguments("abbreviation mid-sentence",
                        "sel, deprem vb. afetler görüldü.",
                        List.of("sel, deprem vb. afetler görüldü.")),
                arguments("title before a name",
                        "dr. ahmet açıklama yaptı. iki kişi kurtarıldı.",
                        List.of("dr. ahmet açıklama yaptı.", "iki kişi kurtarıldı.")),
                arguments("stacked titles",
                        "prof. dr. mehmet geldi. sonra gitti.",
                        List.of("prof. dr. mehmet geldi.", "sonra gitti.")),
                arguments("question and exclamation end sentences too",
                        "kaç kişi yaralandı? üç kişi! hepsi taburcu edildi.",
                        List.of("kaç kişi yaralandı?", "üç kişi!", "hepsi taburcu edildi.")),
                arguments("a run of terminators is one ending",
                        "deprem oldu... ardından artçılar başladı.",
                        List.of("deprem oldu...", "ardından artçılar başladı.")),
                arguments("ellipsis character",
                        "sonra ne oldu… kimse bilmiyor.",
                        List.of("sonra ne oldu…", "kimse bilmiyor.")),
                arguments("a text with no terminator is still one sentence",
                        "ankara'da sel var",
                        List.of("ankara'da sel var")),
                arguments("trailing text after the last terminator is kept",
                        "ilk cümle. ikinci cümle yarım kaldı",
                        List.of("ilk cümle.", "ikinci cümle yarım kaldı")),
                arguments("an url-like token does not split",
                        "kaynak www.ornek.gov adresinde. detaylar orada.",
                        List.of("kaynak www.ornek.gov adresinde.", "detaylar orada.")),
                arguments("decimal comma is untouched",
                        "hasar 2,5 milyon lira. çalışma sürüyor.",
                        List.of("hasar 2,5 milyon lira.", "çalışma sürüyor.")),
                arguments("extra spacing between sentences is not a sentence",
                        "ilk cümle.   ikinci cümle.",
                        List.of("ilk cümle.", "ikinci cümle."))
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("texts")
    void splitsAsExpected(String description, String text, List<String> expected) {
        assertThat(splitter.split(text))
                .extracting(Sentence::text)
                .containsExactlyElementsOf(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\n\n", "..."})
    @DisplayName("text with nothing to say produces no sentences")
    void emptyResults(String text) {
        assertThat(splitter.split(text)).isEmpty();
    }

    @Test
    @DisplayName("offsets point back into the text that was split")
    void offsetsAreUsable() {
        String text = "ilk cümle. ikinci cümle.";

        List<Sentence> sentences = splitter.split(text);

        assertThat(sentences).hasSize(2);
        for (Sentence sentence : sentences) {
            assertThat(text.substring(sentence.start(), sentence.end())).isEqualTo(sentence.text());
            assertThat(sentence.length()).isEqualTo(sentence.text().length());
        }
        assertThat(sentences.get(1).start()).isEqualTo(11);
    }

    @Test
    @DisplayName("the gap between sentences belongs to no sentence")
    void whitespaceIsNotAttributed() {
        List<Sentence> sentences = splitter.split("ilk cümle.   ikinci cümle.");

        assertThat(sentences.get(0).end()).isLessThan(sentences.get(1).start());
    }

    @Test
    void aSentenceRangeMustMakeSense() {
        assertThatThrownBy(() -> new Sentence("x", 5, 2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("[5, 2)");
    }
}
