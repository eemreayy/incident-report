package com.emreay.incidentreport.analysis.extraction;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.emreay.incidentreport.analysis.domain.KeywordRole;
import com.emreay.incidentreport.analysis.text.SentenceSplitter;
import com.emreay.incidentreport.analysis.text.TurkishTextNormalizer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Catalog keywords are written as stems, so the matcher carries the whole burden of Turkish
 * morphology. These are the endings the source document and ordinary reports actually use.
 */
class KeywordMatcherTest {

    private final TurkishTextNormalizer normalizer = new TurkishTextNormalizer(new SentenceSplitter());

    private boolean matches(String keyword, String text) {
        return !new KeywordMatcher(normalizer.normalize(keyword).value())
                .findIn(normalizer.normalize(text), KeywordRole.METRIC)
                .isEmpty();
    }

    @ParameterizedTest(name = "\"{0}\" matches \"{1}\": {2}")
    @CsvSource({
            "vaka,             '15 yeni vaka',                 true",
            "vaka,             'vakaların sayısı',             true",
            "vaka,             'vakalarda artış',              true",
            "hayatını kaybet,  'hayatını kaybetti',            true",
            "hayatını kaybet,  'hayatını kaybederken',         true",
            "hayatını kaybet,  'hayatını kaybedenler',         true",
            "kurtarıl,         'enkazdan kurtarıldı',          true",
            "yaralı,           'hafif yaralı olarak',          true",
            "tahliye,          'tahliye edildi',               true",
            "test,             'testler tamamlandı',           true",
            "test,             'marangoz testere kullandı',    false",
            "kaza,             'kazan devrildi',               false",
            "sel,              'selam verdi',                  false"
    })
    @DisplayName("a stem matches its inflected forms, and only those")
    void inflection(String keyword, String text, boolean expected) {
        assertThat(matches(keyword, text)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "\"{0}\" matches \"{1}\": {2}")
    @CsvSource({
            "'trafik kazası',  'trafik kazasında',             true",
            "'trafik kazası',  'trafik kazasından',            true",
            "'trafik kazası',  'trafik kazasının',             true",
            "'trafik kazası',  'trafik kazasını',              true",
            "kaza,             'kazasında',                    true",
            "kaza,             'kazasından',                   true"
    })
    @DisplayName("a vowel-final stem plus possessive takes the buffer-n before a case ending")
    void bufferConsonantBeforeCase(String keyword, String text, boolean expected) {
        // "kazası" already ends in a vowel (the 3rd-person possessive -sı), so locative/ablative
        // attach with an inserted "n": kazası + nda = kazasında. Missing that buffer-n form used to
        // make "trafik kazasında" fall through to whatever bare keyword matched elsewhere in the
        // text instead of the specific phrase actually present.
        assertThat(matches(keyword, text)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "\"{0}\" softens in \"{1}\"")
    @CsvSource({
            "kaybet, 'hayatını kaybeden kişi'",
            "yasak,  'yasağa uymadı'",
            "ağaç,   'ağaca çarptı'",
            "kitap,  'kitabı okudu'"
    })
    @DisplayName("a stem ending in a hard consonant is still itself when it softens")
    void finalConsonantSoftening(String keyword, String text) {
        // p→b, ç→c, t→d, k→ğ before a vowel. Without this the catalog would have to list both
        // spellings of every verb, and the one it lists would be the one that loses.
        assertThat(matches(keyword, text)).isTrue();
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "'bina hasar',   'on iki bina hasar gördü',   true",
            "'bina hasar',   'binada hasar yok',          false",
            "'su baskını',   'su baskını nedeniyle',      true"
    })
    @DisplayName("a multi-word keyword is matched as a phrase")
    void multiWordKeywords(String keyword, String text, boolean expected) {
        assertThat(matches(keyword, text)).isEqualTo(expected);
    }
}
