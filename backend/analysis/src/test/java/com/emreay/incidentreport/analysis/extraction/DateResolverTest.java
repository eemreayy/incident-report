package com.emreay.incidentreport.analysis.extraction;

import java.time.LocalDate;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.emreay.incidentreport.analysis.domain.DateSource;
import com.emreay.incidentreport.analysis.text.NormalizedText;
import com.emreay.incidentreport.analysis.text.SentenceSplitter;
import com.emreay.incidentreport.analysis.text.TurkishTextNormalizer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class DateResolverTest {

    /** A Monday, far enough into the year that "3 aralık" still lands in the future. */
    private static final LocalDate REFERENCE = LocalDate.of(2020, 4, 20);

    private final TurkishTextNormalizer normalizer = new TurkishTextNormalizer(new SentenceSplitter());
    private final DateResolver resolver = new DateResolver();

    private ResolvedDate resolve(String raw) {
        return resolve(raw, REFERENCE);
    }

    private ResolvedDate resolve(String raw, LocalDate reference) {
        return resolver.resolve(normalizer.normalize(raw), reference);
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "'20.04.2020 tarihinde 15 vaka',      2020-04-20",
            "'20/04/2020 tarihinde 15 vaka',      2020-04-20",
            "'20-04-2020 tarihinde 15 vaka',      2020-04-20",
            "'2020-04-20 tarihinde 15 vaka',      2020-04-20",
            "'3 Mayıs 2020 günü deprem oldu',     2020-05-03",
            "'3 Mayıs 2020',                      2020-05-03",
            "'1 Ocak 2021 gecesi',                2021-01-01",
            "'31 Aralık 2019 akşamı',             2019-12-31",
            "'9 Eylül 2020',                      2020-09-09",
            "'20.04.20 tarihinde',                2020-04-20"
    })
    @DisplayName("every supported explicit format lands on the same kind of calendar day")
    void explicitFormats(String text, LocalDate expected) {
        ResolvedDate resolved = resolve(text);

        assertThat(resolved.date()).isEqualTo(expected);
        assertThat(resolved.source()).isEqualTo(DateSource.EXPLICIT);
    }

    @Test
    @DisplayName("the same day written four ways resolves identically")
    void formatsAgreeWithOneAnother() {
        assertThat(Stream.of("20.04.2020", "20/04/2020", "2020-04-20", "20 Nisan 2020")
                .map(this::resolve)
                .map(ResolvedDate::date))
                .containsOnly(LocalDate.of(2020, 4, 20));
    }

    static Stream<Arguments> relativeExpressions() {
        return Stream.of(
                arguments("son 24 saatte 8 trafik kazası oldu", LocalDate.of(2020, 4, 20)),
                arguments("son 3 günde 8 kaza oldu", LocalDate.of(2020, 4, 20)),
                arguments("son bir haftada sel görüldü", LocalDate.of(2020, 4, 20)),
                arguments("son 48 saat içinde", LocalDate.of(2020, 4, 20)),
                arguments("bugün 15 vaka tespit edildi", LocalDate.of(2020, 4, 20)),
                arguments("dün 15 vaka tespit edildi", LocalDate.of(2020, 4, 19)),
                arguments("dünkü depremde 12 bina hasar gördü", LocalDate.of(2020, 4, 19)),
                arguments("önceki gün 2 kişi yaralandı", LocalDate.of(2020, 4, 18)),
                arguments("geçen hafta sel oldu", LocalDate.of(2020, 4, 13)),
                arguments("geçtiğimiz hafta sel oldu", LocalDate.of(2020, 4, 13)),
                arguments("geçen ay deprem oldu", LocalDate.of(2020, 3, 20)),
                arguments("bu sabah yangın çıktı", LocalDate.of(2020, 4, 20))
        );
    }

    @ParameterizedTest(name = "\"{0}\" -> {1}")
    @MethodSource("relativeExpressions")
    @DisplayName("relative expressions resolve against the submission date")
    void relativeExpressionsResolve(String text, LocalDate expected) {
        ResolvedDate resolved = resolve(text);

        assertThat(resolved.date()).isEqualTo(expected);
        assertThat(resolved.source()).isEqualTo(DateSource.RELATIVE);
    }

    @Test
    @DisplayName("\"son 24 saatte\" is an extraction, not a fallback")
    void aWindowIsRelativeNotDefaulted() {
        // The source document's third example. It resolves to the same day a defaulted record
        // would, and that is exactly why the distinction has to be kept: the text did carry a time
        // expression, and a reader must be able to tell the two apart (ADR-014).
        ResolvedDate resolved = resolve("Son 24 saatte Bursa'da 8, Kocaeli'nde 6 trafik kazası meydana geldi.");

        assertThat(resolved.date()).isEqualTo(REFERENCE);
        assertThat(resolved.source()).isEqualTo(DateSource.RELATIVE);
        assertThat(resolved.source()).isNotEqualTo(DateSource.DEFAULTED);
        assertThat(resolved.wasExtracted()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Ankara'da 15 vaka tespit edildi",
            "sel nedeniyle yollar kapandı",
            "",
            "son günlerde artış var"
    })
    @DisplayName("a text with no time expression falls back to the submission date")
    void defaultedWhenNothingIsSaid(String text) {
        ResolvedDate resolved = resolve(text);

        assertThat(resolved.date()).isEqualTo(REFERENCE);
        assertThat(resolved.source()).isEqualTo(DateSource.DEFAULTED);
        assertThat(resolved.wasExtracted()).isFalse();
        assertThat(resolved.start()).isNull();
    }

    @Test
    @DisplayName("an explicit date wins over a relative one in the same text")
    void explicitBeatsRelative() {
        ResolvedDate resolved = resolve("dün yayımlanan rapora göre 20.04.2020 tarihinde 15 vaka görüldü");

        assertThat(resolved.date()).isEqualTo(LocalDate.of(2020, 4, 20));
        assertThat(resolved.source()).isEqualTo(DateSource.EXPLICIT);
    }

    @Test
    @DisplayName("among equals the first one in the text wins")
    void theEarliestMentionWins() {
        ResolvedDate resolved = resolve("20.04.2020 ve ardından 03.05.2020 tarihinde");

        assertThat(resolved.date()).isEqualTo(LocalDate.of(2020, 4, 20));
    }

    @Test
    @DisplayName("every mention is reported, in the order they appear")
    void allMentionsAreAvailable() {
        NormalizedText text = normalizer.normalize("dün ve 20.04.2020 tarihinde, ayrıca 3 Mayıs 2020 günü");

        assertThat(resolver.mentions(text, REFERENCE))
                .extracting(ResolvedDate::date)
                .containsExactly(LocalDate.of(2020, 4, 19), LocalDate.of(2020, 4, 20), LocalDate.of(2020, 5, 3));
    }

    @ParameterizedTest
    @ValueSource(strings = {"31.02.2020 tarihinde", "2020-13-01 tarihinde", "45.45.2020",
            "30 Şubat 2020", "30 Şubat günü", "31 Nisan'da"})
    @DisplayName("something shaped like a date but impossible is not a date")
    void impossibleDatesAreNotDates(String text) {
        assertThat(resolve(text).source()).isEqualTo(DateSource.DEFAULTED);
    }

    @Test
    @DisplayName("a month name without a year takes the reference year")
    void monthNameWithoutAYear() {
        assertThat(resolve("3 Mayıs'ta deprem oldu", LocalDate.of(2020, 6, 1)).date())
                .isEqualTo(LocalDate.of(2020, 5, 3));
    }

    @Test
    @DisplayName("a month name without a year does not land in the future")
    void monthNameWithoutAYearRollsBack() {
        // Filed in January, "3 aralık" means the December that has been, not the one to come.
        assertThat(resolve("3 Aralık'ta sel oldu", LocalDate.of(2021, 1, 10)).date())
                .isEqualTo(LocalDate.of(2020, 12, 3));
    }

    @Test
    @DisplayName("offsets point at the expression that produced the date")
    void offsetsPointAtTheExpression() {
        NormalizedText text = normalizer.normalize("Geçen hafta İZMİR'de sel oldu.");

        ResolvedDate resolved = resolver.resolve(text, REFERENCE);

        assertThat(text.value().substring(resolved.start(), resolved.end())).isEqualTo("geçen hafta");
        assertThat(text.originalTextIn(resolved.start(), resolved.end())).isEqualTo("Geçen hafta");
    }

    @Test
    @DisplayName("a word that merely contains a time word is not one")
    void lookalikeWordsAreNotTimeExpressions() {
        // "dünya" starts with "dün", and Turkish letters make ASCII word boundaries unreliable -
        // this is the case that made the patterns Unicode-aware.
        assertThat(resolve("dünya genelinde vakalar arttı").source()).isEqualTo(DateSource.DEFAULTED);
        assertThat(resolve("gündüz saatlerinde sel oldu").source()).isEqualTo(DateSource.DEFAULTED);
        // "son iki ayrı olayda" - two separate events, not a two-month window. An open-ended
        // suffix on "ay" turns this ordinary sentence into a date, silently.
        assertThat(resolve("son iki ayrı olayda 3 kişi yaralandı").source()).isEqualTo(DateSource.DEFAULTED);
        assertThat(resolve("dünyada salgın sürüyor").source()).isEqualTo(DateSource.DEFAULTED);
    }

    @Test
    @DisplayName("reprocessing the same report cannot move its date")
    void reprocessingIsStable() {
        // The whole reason the reference is the submission date rather than now (ADR-014): the same
        // text analysed two years later still resolves to the day it was filed.
        String text = "Son 24 saatte Bursa'da 8 trafik kazası meydana geldi.";
        LocalDate submittedOn = LocalDate.of(2020, 4, 20);

        assertThat(resolve(text, submittedOn).date())
                .isEqualTo(resolve(text, submittedOn).date())
                .isEqualTo(submittedOn);
    }

    @Test
    void aResolvedDateMustBeConsistentAboutWhereItCameFrom() {
        assertThatThrownBy(() -> new ResolvedDate(REFERENCE, DateSource.DEFAULTED, 0, 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot point at an expression");

        assertThatThrownBy(() -> new ResolvedDate(REFERENCE, DateSource.EXPLICIT, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must say where it was read from");

        assertThatThrownBy(() -> new ResolvedDate(REFERENCE, DateSource.EXPLICIT, 5, 2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("[5, 2)");
    }
}
