package com.emreay.incidentreport.analysis.text;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class NumberExtractorTest {

    private final TurkishTextNormalizer normalizer = new TurkishTextNormalizer(new SentenceSplitter());
    private final NumberExtractor extractor = new NumberExtractor();

    private List<Long> valuesIn(String raw) {
        return extractor.extract(normalizer.normalize(raw)).stream().map(NumberToken::value).toList();
    }

    @ParameterizedTest(name = "\"{0}\" = {1}")
    @CsvSource({
            "bir,           1",
            "iki,           2",
            "dokuz,         9",
            "on,            10",
            "on iki,        12",
            "on dokuz,      19",
            "yirmi,         20",
            "kırk beş,      45",
            "doksan dokuz,  99",
            "yüz,           100",
            "yüz yirmi,     120",
            "yüz kırk beş,  145",
            "iki yüz,       200",
            "bin,           1000",
            "bin beş yüz,   1500",
            "iki bin,       2000",
            "iki bin üç yüz kırk beş, 2345",
            "iki yüz bin,   200000",
            "iki milyon üç yüz bin,   2300000",
            "bir milyar,    1000000000",
            "sıfır,         0"
    })
    @DisplayName("Turkish number words, including compounds")
    void wordsAreParsed(String text, long expected) {
        assertThat(valuesIn(text)).containsExactly(expected);
    }

    @ParameterizedTest(name = "\"{0}\" = {1}")
    @CsvSource({
            "15,        15",
            "40,        40",
            "1.500,     1500",
            "12.345,    12345",
            "1.234.567, 1234567",
            "0,         0"
    })
    @DisplayName("digits, with Turkish thousands separators")
    void digitsAreParsed(String text, long expected) {
        assertThat(valuesIn(text)).containsExactly(expected);
    }

    @ParameterizedTest(name = "\"{0}\" = {1}")
    @CsvSource({
            "15 bin,        15000",
            "2 bin 500,     2500",
            "3 milyon,      3000000",
            "12 bin 345,    12345"
    })
    @DisplayName("the mixture news text actually uses")
    void digitsCombineWithScaleWords(String text, long expected) {
        assertThat(valuesIn(text)).containsExactly(expected);
    }

    @Test
    @DisplayName("the same count written two ways produces the same value")
    void wordsAndDigitsAgree() {
        assertThat(valuesIn("on iki bina hasar gördü")).isEqualTo(valuesIn("12 bina hasar gördü"));
        assertThat(valuesIn("on iki bina hasar gördü")).containsExactly(12L);
    }

    static Stream<Arguments> notCounts() {
        return Stream.of(
                arguments("vague quantities are not numbers", "onlarca kişi yaralandı"),
                arguments("nor are they in the hundreds", "yüzlerce bina hasar gördü"),
                arguments("nor in the thousands", "binlerce kişi tahliye edildi"),
                arguments("\"birkaç\" is one word and not \"bir\"", "birkaç kişi hafif yaralandı"),
                arguments("a suffixed numeral is not matched as a bare word", "yaralılardan ikisi taburcu oldu"),
                arguments("a figure with a decimal part is not a count", "hasar 2,5 milyar lira"),
                arguments("no digits and no number words at all", "sel nedeniyle yollar kapandı")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("notCounts")
    void nothingIsInvented(String description, String text) {
        assertThat(valuesIn(text)).isEmpty();
    }

    @ParameterizedTest(name = "\"{0}\" has no numbers")
    @ValueSource(strings = {
            "Kaza, D-100 karayolunun İnegöl mevkiinde meydana geldi",
            "E-80 karayolunda kaza oldu",
            "O-4 otoyolunda trafik kazası"
    })
    @DisplayName("a road designator is not a count")
    void routeCodesAreNotCounts(String text) {
        assertThat(valuesIn(text)).isEmpty();
    }

    @Test
    @DisplayName("a route code next to a real count still yields only the count")
    void routeCodeDoesNotSwallowANearbyCount() {
        assertThat(valuesIn("D-100 karayolunda 3 kişi yaralandı")).containsExactly(3L);
    }

    @Test
    @DisplayName("a date is one date, not three numbers")
    void datesAreNotShreddedIntoNumbers() {
        assertThat(valuesIn("20.04.2020 tarihinde Ankara'da 15 yeni vaka tespit edildi"))
                .containsExactly(15L);
        assertThat(valuesIn("2020-04-20 tarihinde 15 vaka")).containsExactly(15L);
        assertThat(valuesIn("20/04/2020 tarihinde 15 vaka")).containsExactly(15L);
    }

    @Test
    @DisplayName("a date written in words still yields its numbers - excluding them needs a calendar")
    void wordDatesAreNotHandledHere() {
        // 3 and 2020 are emitted because nothing here knows "mayıs" is a month. Whoever resolves
        // the date (T-11) holds the span that lets these be discarded; this test records the limit
        // rather than pretending it is not there.
        assertThat(valuesIn("3 Mayıs 2020 günü İzmir'de deprem oldu")).containsExactly(3L, 2020L);
    }

    @ParameterizedTest(name = "\"{0}\" is two numbers, not one")
    @ValueSource(strings = {"bir iki kişi", "beş üç", "on 15", "15 20"})
    @DisplayName("consecutive numbers that are not a compound stay separate")
    void unrelatedNumbersDoNotAddUp(String text) {
        assertThat(valuesIn(text)).hasSize(2);
    }

    @Test
    @DisplayName("\"bir iki kişi\" means a couple of people, not three")
    void twoUnitsInARowDoNotAddUp() {
        assertThat(valuesIn("bir iki kişi yaralandı")).containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("punctuation between two numbers keeps them apart")
    void punctuationSeparatesNumbers() {
        assertThat(valuesIn("Bursa'da 8, Kocaeli'nde 6 trafik kazası")).containsExactly(8L, 6L);
    }

    @Test
    @DisplayName("offsets point at the number as written")
    void offsetsAreUsable() {
        NormalizedText text = normalizer.normalize("Depremde on iki bina hasar gördü.");

        List<NumberToken> tokens = extractor.extract(text);

        assertThat(tokens).hasSize(1);
        NumberToken token = tokens.getFirst();
        assertThat(text.value().substring(token.start(), token.end())).isEqualTo("on iki");
        assertThat(text.originalTextIn(token.start(), token.end())).isEqualTo("on iki");
    }

    @Test
    @DisplayName("an offset survives normalization that changed the text")
    void offsetsMapBackToTheRawText() {
        NormalizedText text = normalizer.normalize("İZMİR'de\n\n15 bina yıkıldı.");

        NumberToken token = extractor.extract(text).getFirst();

        assertThat(token.value()).isEqualTo(15L);
        assertThat(text.originalTextIn(token.start(), token.end())).isEqualTo("15");
    }

    @ParameterizedTest
    @CsvSource({"on iki, WORDS", "15, DIGITS", "15 bin, MIXED"})
    void notationIsReported(String text, NumberNotation expected) {
        assertThat(extractor.extract(normalizer.normalize(text)).getFirst().notation())
                .isEqualTo(expected);
    }

    @Test
    @DisplayName("a number too large for a metric column is reported, not wrapped")
    void oversizedValuesAreFlaggedRatherThanTruncated() {
        NumberToken token = extractor.extract(normalizer.normalize("dokuz milyar")).getFirst();

        assertThat(token.value()).isEqualTo(9_000_000_000L);
        assertThat(token.fitsMetricValue()).isFalse();
        assertThat(extractor.extract(normalizer.normalize("15")).getFirst().fitsMetricValue()).isTrue();
    }

    @Test
    @DisplayName("all three source-document examples yield exactly the figures they state")
    void sourceDocumentExamples() {
        assertThat(valuesIn("20.04.2020 tarihinde Ankara'da 15 yeni vaka tespit edildi. "
                + "1 kişi hayatını kaybetti. 5 kişi ise iyileşerek taburcu edildi."))
                .containsExactly(15L, 1L, 5L);

        assertThat(valuesIn("Son 24 saatte Bursa'da 8, Kocaeli'nde 6 trafik kazası meydana geldi. "
                + "Bursa'da 1, Kocaeli'nde ise 2 kişi kazalarda hayatını kaybetti. "
                + "Her iki ilde toplam 10 kişi yaralı olarak hastaneye kaldırıldı."))
                .containsExactly(24L, 8L, 6L, 1L, 2L, 2L, 10L);
    }

    @ParameterizedTest(name = "\"{0}\" does not combine into one number")
    @ValueSource(strings = {"yüz yüz kişi", "iki bin milyon", "bin milyon", "milyar milyar"})
    @DisplayName("multipliers that cannot follow one another split instead of combining")
    void invalidScaleSequencesDoNotCombine(String text) {
        // "bin milyon" is not how a billion is written. Reading it as one number would produce a
        // figure the text never stated; two separate numbers at least say only what is there.
        assertThat(valuesIn(text)).hasSize(2);
    }

    @Test
    @DisplayName("a figure too long to be a count is discarded, not truncated")
    void absurdlyLongFiguresAreDiscarded() {
        assertThat(valuesIn("99999999999999999999999 kişi yaralandı")).isEmpty();
    }

    @Test
    @DisplayName("a multiplication that would overflow ends the number instead of wrapping it")
    void overflowDoesNotWrapAround() {
        // Pathological rather than realistic, but the alternative to stopping here is a negative
        // casualty count.
        assertThat(valuesIn("9223372036854775807 milyar"))
                .containsExactly(9223372036854775807L, 1_000_000_000L);
    }

    @Test
    void anEmptyTextHasNoNumbers() {
        assertThat(valuesIn("")).isEmpty();
    }

    @Test
    void aNumberRangeMustMakeSense() {
        assertThatThrownBy(() -> new NumberToken(1, 4, 4, NumberNotation.DIGITS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("[4, 4)");
    }
}
