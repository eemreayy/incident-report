package com.emreay.incidentreport.analysis.extraction;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.emreay.incidentreport.analysis.text.NormalizedText;
import com.emreay.incidentreport.analysis.text.SentenceSplitter;
import com.emreay.incidentreport.analysis.text.TurkishTextNormalizer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProvinceExtractorTest {

    /**
     * A slice of the reference data: the provinces the source examples use, the longest names, and
     * the short ones that are ordinary Turkish words as well.
     */
    private static final Map<Short, String> PROVINCES = Map.ofEntries(
            Map.entry((short) 6, "Ankara"), Map.entry((short) 35, "İzmir"),
            Map.entry((short) 16, "Bursa"), Map.entry((short) 41, "Kocaeli"),
            Map.entry((short) 34, "İstanbul"), Map.entry((short) 68, "Aksaray"),
            Map.entry((short) 3, "Afyonkarahisar"), Map.entry((short) 46, "Kahramanmaraş"),
            Map.entry((short) 63, "Şanlıurfa"), Map.entry((short) 17, "Çanakkale"),
            Map.entry((short) 65, "Van"), Map.entry((short) 52, "Ordu"),
            Map.entry((short) 49, "Muş"), Map.entry((short) 53, "Rize"),
            Map.entry((short) 31, "Hatay"), Map.entry((short) 33, "Mersin"),
            Map.entry((short) 1, "Adana"));

    private final TurkishTextNormalizer normalizer = new TurkishTextNormalizer(new SentenceSplitter());
    private final ProvinceExtractor extractor = new ProvinceExtractor(PROVINCES, normalizer);

    private List<String> namesIn(String raw) {
        return extractor.mentions(normalizer.normalize(raw)).stream().map(ProvinceMention::name).toList();
    }

    @ParameterizedTest(name = "\"{0}\" -> {1}")
    @CsvSource({
            "'Ankara''da 15 vaka',            Ankara",
            "'İzmir''de deprem oldu',         İzmir",
            "'Kocaeli''nde 6 kaza',           Kocaeli",
            "'Bursa''dan gelen habere göre',  Bursa",
            "'Ankara''ya gidildi',            Ankara",
            "'İstanbul''un nüfusu',           İstanbul",
            "'Çanakkale''de fırtına',         Çanakkale",
            "'Afyonkarahisar''da kaza',       Afyonkarahisar",
            "'Kahramanmaraş''ta deprem',      Kahramanmaraş",
            "'Şanlıurfa''da sel',             Şanlıurfa"
    })
    @DisplayName("a province is recognised through the suffix attached to it")
    void suffixedProvinces(String text, String expected) {
        assertThat(namesIn(text)).containsExactly(expected);
    }

    @ParameterizedTest(name = "\"{0}\" -> {1}")
    @CsvSource({
            "'Ankarada 15 vaka',      Ankara",
            "'İzmirde deprem',        İzmir",
            "'Bursaya gidildi',       Bursa",
            "'Kocaelinde 6 kaza',     Kocaeli",
            "'Ankaradan geldi',       Ankara"
    })
    @DisplayName("the apostrophe is dropped as often as not, and the name is still a name")
    void provincesWithoutTheApostrophe(String text, String expected) {
        assertThat(namesIn(text)).containsExactly(expected);
    }

    @Test
    @DisplayName("case is irrelevant, including the Turkish i")
    void caseDoesNotMatter() {
        assertThat(namesIn("İZMİR'DE VE ankara'da")).containsExactly("İzmir", "Ankara");
        assertThat(namesIn("İzmir'de")).isEqualTo(namesIn("IZMİR'de".replace("I", "İ")));
    }

    @Test
    @DisplayName("a bare name with no suffix is still a mention")
    void bareNames() {
        assertThat(namesIn("Ankara ve İzmir")).containsExactly("Ankara", "İzmir");
    }

    /**
     * The short province names are ordinary Turkish words too. An open-ended suffix — matching any
     * letters after the name — turns every one of these sentences into a province.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "vanilya kokusu vardı",
            "vantilatör çalışmıyordu",
            "olay yerine ordular sevk edildi",
            "yangın olmuş",
            "hatayı fark ettik",
            "mersinlik ağaçları"
    })
    @DisplayName("a word that merely starts with a province name is not a province")
    void lookalikeWordsAreNotProvinces(String text) {
        assertThat(namesIn(text)).isEmpty();
    }

    @Test
    @DisplayName("but the same short names are provinces when they really are used as one")
    void theShortNamesStillWork() {
        assertThat(namesIn("Van'da deprem oldu")).containsExactly("Van");
        assertThat(namesIn("Ordu'da sel var")).containsExactly("Ordu");
        assertThat(namesIn("Muş'ta kar yağdı")).containsExactly("Muş");
        assertThat(namesIn("Vanlı aileler tahliye edildi")).containsExactly("Van");
    }

    @Test
    @DisplayName("\"-lı/-li\" means \"from there\", so it names the province too")
    void theFromSuffixIsAMention() {
        // "Rizeli" and "Adanalı" name a province as surely as "Rize'de" does. Whether a figure
        // beside them belongs to that province is a different question, and not this one.
        assertThat(namesIn("Rizeli aileler yardım bekliyor")).containsExactly("Rize");
        assertThat(namesIn("Adanalı olduğunu söyledi")).containsExactly("Adana");
    }

    @Test
    @DisplayName("a district that shares its name with a province is not that province")
    void districtsAreNotProvinces() {
        // Aksaray is a province and also a neighbourhood of İstanbul. Without the marker this
        // sentence would file a record against a city 200 km away.
        assertThat(namesIn("İstanbul'un Aksaray semtinde yangın çıktı")).containsExactly("İstanbul");
        assertThat(namesIn("Aksaray ilçesinde su baskını")).isEmpty();
        assertThat(namesIn("Ordu'nun Fatsa ilçesinde")).containsExactly("Ordu");
    }

    @Test
    @DisplayName("Aksaray on its own is still the province")
    void theProvinceItselfIsUnaffected() {
        assertThat(namesIn("Aksaray'da 3 kişi yaralandı")).containsExactly("Aksaray");
    }

    @Test
    @DisplayName("every mention is kept, including repeats")
    void repeatedMentionsAreKept() {
        assertThat(namesIn("Bursa'da 8 kaza oldu. Bursa'da 1 kişi hayatını kaybetti."))
                .containsExactly("Bursa", "Bursa");
    }

    @Test
    @DisplayName("the third source example names both provinces, in order")
    void theSourceDocumentExample() {
        assertThat(namesIn("Son 24 saatte Bursa'da 8, Kocaeli'nde 6 trafik kazası meydana geldi. "
                + "Bursa'da 1, Kocaeli'nde ise 2 kişi kazalarda hayatını kaybetti. "
                + "Her iki ilde toplam 10 kişi yaralı olarak hastaneye kaldırıldı."))
                .containsExactly("Bursa", "Kocaeli", "Bursa", "Kocaeli");
    }

    @Test
    @DisplayName("the first two source examples name one province each")
    void theOtherSourceExamples() {
        assertThat(namesIn("20.04.2020 tarihinde Ankara'da 15 yeni vaka tespit edildi."))
                .containsExactly("Ankara");
        assertThat(namesIn("3 Mayıs 2020 günü İzmir'de meydana gelen depremde on iki bina hasar gördü."))
                .containsExactly("İzmir");
    }

    @Test
    @DisplayName("the name comes back spelled the way the reference data spells it")
    void namesAreCanonical() {
        assertThat(namesIn("izmir'de ve ISTANBUL'da".replace("I", "İ")))
                .containsExactly("İzmir", "İstanbul");
    }

    @Test
    @DisplayName("offsets point at the mention in the text the user wrote")
    void offsetsMapBackToTheRawText() {
        NormalizedText text = normalizer.normalize("Depremde   İZMİR'de 12 bina yıkıldı.");

        ProvinceMention mention = extractor.mentions(text).getFirst();

        assertThat(text.value().substring(mention.start(), mention.end())).isEqualTo("izmir'de");
        assertThat(text.originalTextIn(mention.start(), mention.end())).isEqualTo("İZMİR'de");
    }

    @Test
    void aTextWithNoProvinceHasNoMentions() {
        assertThat(namesIn("sel nedeniyle yollar kapandı")).isEmpty();
        assertThat(namesIn("")).isEmpty();
    }

    @Test
    @DisplayName("an empty reference table is a startup failure, not a silent no-op")
    void refusesToBeBuiltWithNoProvinces() {
        assertThatThrownBy(() -> new ProvinceExtractor(Map.of(), normalizer))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reference data");
    }

    @Test
    void aMentionRangeMustMakeSense() {
        assertThatThrownBy(() -> new ProvinceMention((short) 6, "Ankara", 4, 4))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("[4, 4)");
    }
}
