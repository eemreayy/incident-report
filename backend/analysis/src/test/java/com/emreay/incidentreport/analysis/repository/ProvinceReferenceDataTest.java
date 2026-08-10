package com.emreay.incidentreport.analysis.repository;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.emreay.incidentreport.analysis.domain.Province;
import com.emreay.incidentreport.analysis.extraction.ProvinceExtractor;
import com.emreay.incidentreport.analysis.extraction.ProvinceMention;
import com.emreay.incidentreport.analysis.text.SentenceSplitter;
import com.emreay.incidentreport.analysis.text.TurkishTextNormalizer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The province extractor against the <em>real</em> reference data, all 81 of them.
 *
 * <p>{@link com.emreay.incidentreport.analysis.extraction.ProvinceExtractorTest} works from a
 * hand-picked slice, which is where the reasoning is easiest to follow but also where a name nobody
 * thought about cannot show up. The risk this test covers is a province whose name is an ordinary
 * Turkish word — and the list is not going to be read carefully every time it changes.
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers
class ProvinceReferenceDataTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private ProvinceRepository provinces;

    private ProvinceExtractor extractor;
    private TurkishTextNormalizer normalizer;

    private ProvinceExtractor extractor() {
        if (extractor == null) {
            normalizer = new TurkishTextNormalizer(new SentenceSplitter());
            extractor = new ProvinceExtractor(
                    provinces.findAll().stream().map(Province::getName).toList(), normalizer);
        }
        return extractor;
    }

    private List<String> namesIn(String raw) {
        return extractor().mentions(normalizer.normalize(raw)).stream()
                .map(ProvinceMention::name)
                .toList();
    }

    @Test
    @DisplayName("the migration seeds all 81 provinces")
    void theReferenceDataIsComplete() {
        assertThat(provinces.findAll()).hasSize(81);
    }

    /**
     * Ordinary incident prose with no province in it. Every sentence here contains a word that
     * starts with, or looks like, one of the 81 names — "ordular", "vanilya", "olmuş", "hata".
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "Yangın sonucu ordular bölgeye sevk edildi.",
            "Vanilya ve tarçın kokusu her yeri sarmıştı.",
            "Bu bir hata olmuş, düzeltilmesi gerekiyor.",
            "Sağlık ekipleri olay yerine sevk edildi ve yaralılar hastaneye kaldırıldı.",
            "Kar yağışı nedeniyle okullar tatil edildi.",
            "Arama kurtarma çalışmaları sürüyor, enkaz altında kalanlar için umut var.",
            "Vali bölgede incelemelerde bulundu ve gerekli tedbirlerin alındığını söyledi.",
            "Rüzgarın etkisiyle çatılar uçtu, ağaçlar devrildi.",
            "Toplam on iki bina ağır hasar gördü ve tahliye edildi.",
            "Ulaşıma kapanan yollar ekiplerce yeniden açıldı.",
            "Sel sularının çekilmesiyle temizlik çalışmalarına başlandı.",
            "Deprem anında bir çok kişi kendini dışarı attı.",
            "Bugün itibariyle vaka sayısında düşüş gözlendi.",
            "Kaza, sürücünün direksiyon hakimiyetini kaybetmesi sonucu meydana geldi."
    })
    @DisplayName("no province is found in a sentence that names none")
    void ordinaryProseNamesNoProvince(String text) {
        assertThat(namesIn(text)).isEmpty();
    }

    @ParameterizedTest(name = "\"{0}\" -> {1}")
    @CsvSource({
            "'Ankara''da 15 vaka',        Ankara",
            "'İzmir''de deprem',          İzmir",
            "'Afyonkarahisar''da kar',    Afyonkarahisar",
            "'Kahramanmaraş''ta artçı',   Kahramanmaraş",
            "'Şanlıurfa''dan haber',      Şanlıurfa",
            "'Van''da soğuk',             Van",
            "'Ordu''da sel',              Ordu",
            "'Muş''ta tipi',              Muş",
            "'Iğdır''da don',             Iğdır",
            "'Çanakkale''de fırtına',     Çanakkale"
    })
    @DisplayName("every one of them is still recognised when it really is named")
    void realMentionsAreFound(String text, String expected) {
        assertThat(namesIn(text)).containsExactly(expected);
    }

    @Test
    @DisplayName("the third source example, against the real list")
    void theSourceDocumentExample() {
        assertThat(namesIn("Son 24 saatte Bursa'da 8, Kocaeli'nde 6 trafik kazası meydana geldi. "
                + "Bursa'da 1, Kocaeli'nde ise 2 kişi kazalarda hayatını kaybetti. "
                + "Her iki ilde toplam 10 kişi yaralı olarak hastaneye kaldırıldı."))
                .containsExactly("Bursa", "Kocaeli", "Bursa", "Kocaeli");
    }
}
