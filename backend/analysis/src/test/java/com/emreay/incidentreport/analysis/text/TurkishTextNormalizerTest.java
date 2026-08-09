package com.emreay.incidentreport.analysis.text;

import java.text.Normalizer;
import java.util.Locale;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class TurkishTextNormalizerTest {

    private final TurkishTextNormalizer normalizer = new TurkishTextNormalizer(new SentenceSplitter());

    static Stream<Arguments> cases() {
        return Stream.of(
                arguments("dotted capital I lowercases to a plain i", "İZMİR", "izmir"),
                arguments("dotless capital I lowercases to a dotless one", "IZMIR", "ızmır"),
                arguments("mixed case province", "İstanbul", "istanbul"),
                arguments("a province arrives suffixed", "Ankara'da", "ankara'da"),
                arguments("and with a longer suffix", "Kocaeli'nde", "kocaeli'nde"),
                arguments("typographic apostrophe folds to the plain one", "İzmir’de", "izmir'de"),
                arguments("so does an acute accent", "Bursa´da", "bursa'da"),
                arguments("so does a backtick", "Ankara`da", "ankara'da"),
                arguments("a zero-width space inside a word is dropped", "An​kara", "ankara"),
                arguments("as is a soft hyphen", "Kocae­li", "kocaeli"),
                arguments("a byte order mark does not become part of the first word", "﻿Ankara", "ankara"),
                arguments("line breaks inside a sentence become spaces", "Ankara'da\nsel\nvar", "ankara'da sel var"),
                arguments("a run of whitespace collapses to one space", "Ankara'da    sel", "ankara'da sel"),
                arguments("non-breaking space is whitespace too", "Ankara'da sel", "ankara'da sel"),
                arguments("surrounding whitespace is trimmed", "  Ankara'da sel  ", "ankara'da sel"),
                arguments("digits and punctuation are left alone", "20.04.2020 - 15 vaka!", "20.04.2020 - 15 vaka!"),
                arguments("empty text stays empty", "", ""),
                arguments("whitespace-only text collapses to nothing", "   \n  ", "")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void normalizesAsExpected(String description, String raw, String expected) {
        assertThat(normalizer.normalize(raw).value()).isEqualTo(expected);
    }

    @Test
    @DisplayName("the default locale is never consulted")
    void turkishLowercasingIsNotTheDefaultOne() {
        // The point of the exercise: under the root locale this same input produces "i̇zmi̇r" -
        // seven characters, each capital İ leaving a combining dot behind - which matches no
        // province name. If this assertion ever fails, matching has silently stopped working.
        assertThat("İZMİR".toLowerCase(Locale.ROOT)).isNotEqualTo("izmir");
        assertThat(normalizer.normalize("İZMİR").value()).isEqualTo("izmir");
    }

    @Test
    @DisplayName("decomposed input normalizes to the same thing as composed input")
    void unicodeFormDoesNotChangeTheResult() {
        String composed = "İZMİR'de Şığla Çöküntüsü";
        String decomposed = Normalizer.normalize(composed, Normalizer.Form.NFD);

        assertThat(decomposed).isNotEqualTo(composed);
        assertThat(normalizer.normalize(decomposed).value())
                .isEqualTo(normalizer.normalize(composed).value());
    }

    @Test
    @DisplayName("a match can be pointed back at the user's own spelling")
    void offsetsSurviveNormalization() {
        NormalizedText text = normalizer.normalize("Geçen hafta İZMİR’de sel oldu.");

        int start = text.value().indexOf("izmir");

        assertThat(start).isNotNegative();
        assertThat(text.originalTextIn(start, start + "izmir".length())).isEqualTo("İZMİR");
        assertThat(text.sourceStart(start)).isEqualTo(text.original().indexOf("İZMİR"));
    }

    @Test
    @DisplayName("offsets stay right even when normalization changed the length")
    void offsetsSurviveDecomposedInput() {
        String raw = "Sel " + Normalizer.normalize("İZMİR", Normalizer.Form.NFD) + " ilinde";
        NormalizedText text = normalizer.normalize(raw);

        int start = text.value().indexOf("izmir");

        assertThat(text.value()).isEqualTo("sel izmir ilinde");
        assertThat(text.originalTextIn(start, start + 5))
                .isEqualTo(Normalizer.normalize("İZMİR", Normalizer.Form.NFD));
    }

    @Test
    @DisplayName("a collapsed whitespace run maps back to the whole run")
    void offsetsCoverCollapsedWhitespace() {
        NormalizedText text = normalizer.normalize("sel\n\n  var");

        assertThat(text.value()).isEqualTo("sel var");
        assertThat(text.originalTextIn(3, 4)).isEqualTo("\n\n  ");
        assertThat(text.originalTextIn(0, 7)).isEqualTo("sel\n\n  var");
    }

    @Test
    @DisplayName("every character maps to a range that reproduces it")
    void everyOffsetIsConsistent() {
        NormalizedText text = normalizer.normalize("20.04.2020'de İZMİR’de 15 vaka. Dr. Ayşe açıkladı.");

        for (int i = 0; i < text.value().length(); i++) {
            assertThat(text.sourceStart(i))
                    .as("character %d is not mapped forward", i)
                    .isLessThan(text.sourceEnd(i));
            assertThat(text.sourceEnd(i)).isLessThanOrEqualTo(text.original().length());
        }
        assertThat(text.originalTextIn(0, text.value().length())).isEqualTo(text.original());
    }

    @Test
    @DisplayName("sentences come back split, on the normalized text")
    void sentencesAreCarried() {
        NormalizedText text = normalizer.normalize(
                "20.04.2020 tarihinde ANKARA'da 15 vaka tespit edildi. 1 kişi vefat etti.");

        assertThat(text.sentences())
                .extracting(Sentence::text)
                .containsExactly(
                        "20.04.2020 tarihinde ankara'da 15 vaka tespit edildi.",
                        "1 kişi vefat etti.");
        assertThat(text.sentences().getFirst().text())
                .isEqualTo(text.value().substring(0, text.sentences().getFirst().end()));
    }

    /**
     * The three texts from the source document, split. Extraction proper is a later task; what has
     * to hold already is that nothing gets cut in the wrong place — every one of them opens with a
     * date or a time expression, and example 3 puts two provinces and their figures in a single
     * sentence, which is the only reason those figures can be attributed at all.
     */
    static Stream<Arguments> sourceDocumentTexts() {
        return Stream.of(
                arguments("20.04.2020 tarihinde Ankara'da 15 yeni vaka tespit edildi. "
                        + "1 kişi hayatını kaybetti. 5 kişi ise iyileşerek taburcu edildi."),
                arguments("3 Mayıs 2020 günü İzmir'de meydana gelen depremde on iki bina hasar gördü. "
                        + "İki kişi hayatını kaybederken, dokuz kişi enkazdan sağ olarak kurtarıldı. "
                        + "Ayrıca 40 kişi hafif yaralı olarak tedavi altına alındı."),
                arguments("Son 24 saatte Bursa'da 8, Kocaeli'nde 6 trafik kazası meydana geldi. "
                        + "Bursa'da 1, Kocaeli'nde ise 2 kişi kazalarda hayatını kaybetti. "
                        + "Her iki ilde toplam 10 kişi yaralı olarak hastaneye kaldırıldı.")
        );
    }

    @ParameterizedTest
    @MethodSource("sourceDocumentTexts")
    @DisplayName("each source-document example is three sentences, each traceable to the raw text")
    void sourceDocumentExamplesSplitCleanly(String raw) {
        NormalizedText text = normalizer.normalize(raw);

        assertThat(text.sentences()).hasSize(3);
        for (Sentence sentence : text.sentences()) {
            assertThat(text.originalTextIn(sentence.start(), sentence.end()))
                    .isEqualToIgnoringCase(sentence.text());
        }
    }

    @Test
    @DisplayName("a leading date is not mistaken for the end of a sentence")
    void aDatedSentenceStaysWhole() {
        NormalizedText text = normalizer.normalize(
                "20.04.2020 tarihinde Ankara'da 15 yeni vaka tespit edildi. 1 kişi hayatını kaybetti.");

        assertThat(text.sentences().getFirst().text()).contains("20.04.2020").contains("15 yeni vaka");
    }

    @Test
    @DisplayName("normalization that makes the text longer does not corrupt the offset map")
    void normalizationCanGrowTheText() {
        // Composition almost always shortens, so the offset arrays are sized from the raw length.
        // Almost: U+0344 has a singleton decomposition that NFC does not recompose, so it comes out
        // as two characters. Enough of them and the arrays have to grow - this is that case, kept
        // here so the growth path is exercised rather than assumed.
        String raw = "a" + "̈́".repeat(8) + " sel";

        NormalizedText text = normalizer.normalize(raw);

        assertThat(text.value().length()).isGreaterThan(raw.length());
        assertThat(text.value()).endsWith(" sel");
        assertThat(text.originalTextIn(0, text.value().length())).isEqualTo(raw);
    }

    @Test
    void emptyTextHasNothingInIt() {
        NormalizedText text = normalizer.normalize("   ");

        assertThat(text.isEmpty()).isTrue();
        assertThat(text.sentences()).isEmpty();
        assertThat(text.originalTextIn(0, 0)).isEmpty();
    }

    @Test
    void nullIsRejectedRatherThanNormalized() {
        assertThatThrownBy(() -> normalizer.normalize(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rawText");
    }

    @Test
    void offsetsOutsideTheTextAreRejected() {
        NormalizedText text = normalizer.normalize("sel");

        assertThatThrownBy(() -> text.sourceStart(3)).isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> text.sourceEnd(-1)).isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> text.originalTextIn(0, 4)).isInstanceOf(IndexOutOfBoundsException.class);
    }
}
