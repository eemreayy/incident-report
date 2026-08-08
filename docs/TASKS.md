# Task Kırılımı

[`docs/PRD.md`](PRD.md) v1.0 onaylandıktan sonra üretilmiştir. Her task; kapsamını, bağımlılıklarını,
karşıladığı isterleri (FR/NFR) ve çözdüğü teknik challenge'ları (TC) taşır.

**Sıralama stratejisi:** Önce iskelet ve kalite kapısı, sonra veri modeli kararı, ardından **uçtan uca
çalışan ince bir dikey dilim**. Analiz motorunun asıl zorluğuna ancak boru hattı baştan sona
çalıştıktan sonra giriliyor — böylece iki veri tabanı, event akışı ve persist entegrasyonu, en riskli
iş olan Türkçe metin çıkarımıyla aynı anda hata ayıklanmak zorunda kalınmıyor.

**Çalışma akışı:** Depo baştan kuruldu (T-21 öne alındı), bu yüzden **her task kendi commit'i olarak**
GitHub'a gidiyor. Git geçmişi projenin nasıl inşa edildiğini gösteriyor; tek büyük commit'e göre
hem incelemesi hem geri alması kolay. Bir task'ın mimari kararı varsa `docs/DECISIONS.md`
güncellemesi de aynı commit'te yer alır.

**Depo:** https://github.com/eemreayy/incident-report-be

**Durum lejantı:** ☐ bekliyor · ◐ devam ediyor · ☑ tamamlandı

---

## Faz 0 — İskelet ve Kalite Kapısı

### ☑ T-01 · Maven çok modüllü proje iskeleti
Java 21 + Spring Boot 3.5.x, Maven wrapper. **Her modül ayrı bir Maven modülü**, kendi `pom.xml`'i ile:
`shared`, `ingestion`, `analysis`, `realtime` ve tek deploy edilebilir `app`. Ortak sürüm/plugin
yönetimi parent'ta; modüle özgü kütüphaneler ilgili modülün pom'unda. Profil bazlı konfigürasyon
(`local`, `docker`, `test`) `app` modülünde.
- **Bağımlılık:** —
- **Karşılar:** NFR-01, NFR-05 (kısmen)
- **DoD:** `./mvnw verify` geçer; uygulama ayağa kalkar.
- **Sonuç:** Spring Boot **3.5.16**, JDK **21.0.12**. Reactor 6 modülü sırayla derliyor,
  `/actuator/health` → `UP`, `app/target/incident-report-be.jar` içinde dört modül jar'ı var.
  `maven-compiler-plugin` `<release>21</release>` ile API yüzeyini 21'e sabitliyor;
  `maven-enforcer-plugin` JDK tabanını (≥21) ve Maven sürümünü (≥3.9) zorunlu kılıyor.
- **Modül sınırı doğrulandı:** `ingestion` içinden `analysis` sınıfına erişim denendi, build
  `package com.emreay.incidentreport.analysis does not exist` ile kırıldı. Sınır artık konvansiyon
  değil, derleme garantisi.
- **Not:** Veri tabanı starter'ları bilinçli olarak eklenmedi — şema kararı T-04'te veriliyor ve
  JPA starter'ını şimdi eklemek uygulamanın veri tabanı olmadan ayağa kalkmasını engellerdi.
- **Ortam notu:** Makinede JDK 21 yoktu (varsayılan 17). `brew install openjdk@21` ile kuruldu;
  `JAVA_HOME` export edilmeden Maven komutları enforcer kuralına takılır (bkz. `CLAUDE.md` → Commands).

### ☐ T-02 · Dockerize ve tek komutla ayağa kalkma
Uygulama için multi-stage `Dockerfile`; `docker-compose.yml` ile app + MongoDB + PostgreSQL (tek instance).
Health check'ler, servis bağımlılık sırası, named volume'lar, `.env.example`.
- **Bağımlılık:** T-01
- **Karşılar:** NFR-03, NFR-04 · **İlgili karar:** ADR-010
- **DoD:** Temiz makinede `docker compose up --build` sonrası API ve iki veri tabanı sağlıklı; uygulama iki veri tabanına da bağlanabiliyor.

### ☐ T-03 · Kalite kapısı: coverage, kalan sınır kuralları, Testcontainers
JaCoCo `verify` fazına bağlanır ve **%80** altında build kırılır. Çok modüllü yapıda toplam oranın
nasıl hesaplanacağı (modül bazlı eşik mi, birleşik rapor mu) bu task'ta karara bağlanır.
Testcontainers ile Mongo ve Postgres için ortak test altyapısı.

**Kapsam notu:** Modüller arası erişim yasağı T-01'de **build seviyesinde** çözüldü (bağımlılık
grafiğinde kenar yok → derleme hatası). Bu task'a kalan, derleyicinin göremediği kurallar:
controller'ın entity/document sızdırmaması, `analysis` içinde Mongo tipi kullanılmaması,
katman yönü (repository → service → controller). Bunun için ArchUnit yeterli; Spring Modulith'e
gerek olup olmadığı burada değerlendirilip ADR'ye yazılacak.
- **Bağımlılık:** T-01
- **Karşılar:** NFR-02, NFR-05
- **DoD:** Kasten yazılmış bir katman/sızıntı ihlali testi kırar; coverage eşiğinin altına düşünce build kırılır.

---

## Faz 1 — Veri Modeli Kararı *(bloke edici)*

### ☐ T-04 · TC-1 ve TC-2'yi karara bağla, şemayı kur
PRD'nin bilinçli olarak açık bıraktığı iki karar burada veriliyor:
- **TC-1 — Kayıt granülaritesi:** Bir ham metinden kaç normalize kayıt üretilecek (il × tarih × olay tipi?).
  Örnek 3'teki "her iki ilde toplam 10 kişi yaralı" gibi **ile atanamayan** metriklerin çift sayıma yol
  açmadan nasıl temsil edileceği.
- **TC-2 — Metrik veri modeli:** Normalize tablo / JSONB / geniş tablo.

Ardından MongoDB döküman şekli, PostgreSQL şeması ve Flyway migration'ları yazılır. İki veri tabanı
arasındaki **iki yönlü** referans bu task'ta kurulur.
- **Bağımlılık:** T-01
- **Karşılar:** FR-07, FR-08, NFR-06 · **Çözer:** TC-1, TC-2
- **DoD:** Kararlar ADR olarak `docs/DECISIONS.md`'de ("İleride" bölümü dahil); migration'lar temiz veri tabanında çalışıyor; iki yönlü izlenebilirlik şema seviyesinde mümkün.
- **Not:** Bu task sonraki her şeyi bloke ediyor; kararlar netleşmeden ingestion ve analysis persist edilemez.

---

## Faz 2 — Uçtan Uca Dikey Dilim

Amaç: gerçek çıkarım mantığı olmadan, boru hattının baştan sona çalıştığını kanıtlamak.

### ☐ T-05 · Ingestion: ham metnin değişmez saklanması
Mongo document + repository + `IngestionService`. Create, tekil read, sayfalı list. Metin **bayt bayt**
gönderildiği gibi, hiçbir normalizasyon uygulanmadan yazılır. Update/delete yok. Kayıt sonrası domain
event yayınlanır.
- **Bağımlılık:** T-04
- **Karşılar:** FR-01, FR-02, FR-14 · **İlgili karar:** ADR-005
- **DoD:** Kaydedilen metin girdiyle birebir aynı; update/delete API'si yok; event yayınlanıyor.

### ☐ T-06 · REST katmanı ve hata sözleşmesi
`POST /api/v1/incident-reports`, `GET /incident-reports`, `GET /incident-reports/{id}`. Girdi doğrulama
(boş metin, maksimum uzunluk). `@RestControllerAdvice` ile RFC 7807 `application/problem+json`.
Cevap modelinde `warnings[]` alanı.
- **Bağımlılık:** T-05
- **Karşılar:** FR-01, FR-14
- **DoD:** Geçersiz girdi açıklayıcı problem+json döner; stack trace sızmaz.

### ☐ T-07 · Analiz boru hattı iskeleti ve kalıcılaştırma
`analysis` modülü event'i **senkron** dinler, (bu aşamada naif/geçici çıkarımla) normalize kayıt üretir,
Postgres'e yazar ve ham kayda iki yönlü bağlar. Analiz hatası ham metnin kaydını geri almaz — ham kayıt
`FAILED` işaretlenir.
- **Bağımlılık:** T-04, T-05
- **Karşılar:** FR-03, FR-08 · **İlgili kararlar:** ADR-002, ADR-003
- **DoD:** Metin gönderildiğinde Mongo'ya ve Postgres'e kayıt düşüyor; her iki yönde de izlenebilir; analiz hatası ham kaydı silmiyor.

---

## Faz 3 — Analiz Motoru *(projenin asıl zorluğu)*

### ☐ T-08 · Olay tipi kataloğu (YAML) ve metadata ucu
Olay tipleri, tetikleyici anahtar kelimeler ve metrik tanımları YAML'dan yüklenir; **uygulama
başlangıcında doğrulanır** ve katalog hatalıysa uygulama ayağa kalkmaz. `GET /api/v1/metadata`
kataloğu ve il listesini sunar.
- **Bağımlılık:** T-01
- **Karşılar:** FR-16, NFR-08 · **İlgili karar:** ADR-007
- **DoD:** Yeni olay tipi eklemek yalnızca YAML değişikliği; bozuk katalogda uygulama açık hata mesajıyla ayağa kalkmıyor.

### ☐ T-09 · Türkçe metin normalizasyonu ve cümle bölme
Locale duyarlı büyük/küçük harf (i/İ/ı/I), Unicode normalizasyonu, noktalama ve apostrof işleme,
cümle segmentasyonu (kısaltmalar ve `20.04.2020` gibi noktalı tarihler cümle sonu sanılmamalı).
- **Bağımlılık:** T-01
- **Çözer:** TC-5
- **DoD:** `"İZMİR"` doğru küçültülüyor; tarih içeren cümle yanlış bölünmüyor; tablo bazlı testler mevcut.

### ☐ T-10 · Sayı ayrıştırma (rakam + Türkçe sözcük)
Rakamla yazılmış sayılar ve Türkçe sayı sözcükleri; bileşikler dahil (`on iki`=12, `kırk beş`=45,
`yüz yirmi`=120). Sayının metindeki konumu (offset) korunur — metrik eşleştirmesi buna dayanacak.
- **Bağımlılık:** T-09
- **Çözer:** TC-4 · **Karşılar:** FR-05
- **DoD:** `"on iki bina"` ile `"12 bina"` aynı değeri üretiyor; sınır ve olumsuz vakalar test edilmiş.

### ☐ T-11 · Tarih çözümleme (EXPLICIT / RELATIVE / DEFAULTED)
Çoklu format (`20.04.2020`, `3 Mayıs 2020`, `2020-04-20`); göreli ifadeler (`Son 24 saatte`, `dün`,
`bugün`) **referans tarihe** göre çözülür. Referans tarih ham bildirimin **gönderim tarihidir**,
`now()` değil. Çözüm kaynağı kayıtta saklanır. Bu task'ta ayrıca TC-6'nın açık kalan kısmı
(aralık semantiği, zaman dilimi ve gün sınırı) karara bağlanır.
- **Bağımlılık:** T-09, T-10
- **Karşılar:** FR-06 · **Çözer:** TC-6 · **İlgili karar:** ADR-014
- **DoD:** Örnek 3 `RELATIVE` olarak çözülüyor (`DEFAULTED` değil); aynı bildirim reprocess edildiğinde tarih değişmiyor; timezone kararı ADR'ye yazılmış.

### ☐ T-12 · İl çıkarımı
81 il sözlüğü; ek ve apostrof toleransı (`Ankara'da`, `Kocaeli'nde`, `İzmir'de`, apostrofsuz yazımlar);
çok kelimeli il isimleri (Afyonkarahisar, Kahramanmaraş); ilçe adlarının il sanılmaması. Konum offset'i korunur.
- **Bağımlılık:** T-09
- **Çözer:** TC-7
- **DoD:** Üç örnekteki iller doğru; yaygın ilçe adları yanlış eşleşme üretmiyor.

### ☐ T-13 · Olay tipi sınıflandırma ve UNCLASSIFIED davranışı
Katalog anahtar kelimelerinden skorlama; eşik ve güven değeri; birden fazla tip tetiklendiğinde çözüm.
Eşik altında kalan metin **reddedilmez** — `OTHER` / `UNCLASSIFIED` üretilir, çıkarılabilen tarih/il/sayılar
korunur, cevaba uyarı eklenir.
- **Bağımlılık:** T-08, T-09
- **Karşılar:** FR-09 · **Çözer:** TC-8 · **İlgili karar:** ADR-006
- **DoD:** Tanınmayan tip içeren metin 4xx almıyor; `UNCLASSIFIED` kayıt üretiliyor ve `warnings[]` dolu geliyor.

### ☐ T-14 · Metrik eşleştirme ve il kapsamı
Sayı ↔ metrik eşleştirmesi (cümle içi yakınlık kuralları). Cümlede birden fazla il varsa sayıların doğru
ile bağlanması (`"Bursa'da 8, Kocaeli'nde 6 trafik kazası"`). İle atanamayan toplamların
(`"her iki ilde toplam 10 kişi"`) T-04'te kararlaştırılan modele göre temsili — **çift sayım yok**.
- **Bağımlılık:** T-04, T-10, T-12, T-13
- **Karşılar:** FR-03, FR-07 · **Çözer:** TC-3
- **DoD:** Örnek 3'ün Bursa/Kocaeli kırılımı doğru; toplam yaralı hiçbir ile iki kez yazılmıyor.

### ☐ T-15 · Golden testler
PRD §11'deki üç örnek metin uçtan uca doğrulanır. Her örnek ayrıca **cümleleri karıştırılmış** halleriyle
de test edilir; çıktı değişmemelidir.
- **Bağımlılık:** T-11, T-12, T-13, T-14
- **Karşılar:** FR-03, FR-04 · Kabul kriteri §11
- **DoD:** Üç örnek ve karıştırılmış varyantları beklenen tarih/il/tip/metrikleri üretiyor.

---

## Faz 4 — Sorgu ve Analitik

### ☐ T-16 · Olay kayıtlarının listelenmesi ve filtrelenmesi
`GET /incidents` — olay tipi, il, tarih aralığı ve anahtar kelime filtreleri (birlikte uygulanabilir),
sayfalama ve sıralama. `GET /incidents/{id}` metrikleri, anahtar kelimeleri ve kaynak ham bildirim
referansını döner.
- **Bağımlılık:** T-14
- **Karşılar:** FR-10, FR-17, FR-08
- **DoD:** Filtreler tekil ve kombine çalışıyor; anahtar kelimeler hangi çıkarımı tetiklediğiyle birlikte görünüyor.

### ☐ T-17 · Zaman serisi, özet ve kümülatif
`GET /analytics/time-series` — olay tipi bazlı, metriklere ayrılmış seriler; opsiyonel il ve tarih aralığı;
`cumulative` parametresi. `GET /analytics/summary` — özet tablo agregasyonu.
- **Bağımlılık:** T-16
- **Karşılar:** FR-11, FR-12
- **DoD:** Kümülatif modda her nokta kendisi ve öncekilerin toplamı; agregasyon SQL üzerinde yapılıyor (bellekte değil).

---

## Faz 5 — Gerçek Zamanlı ve Yeniden İşleme

### ☐ T-18 · SSE yayını
`GET /stream/incidents` — yeni normalize kayıt üretildiğinde bağlı tüm istemcilere tek yönlü yayın.
Bağlantı yaşam döngüsü: timeout, heartbeat, kopma/temizlik, çok istemcili yayın.
- **Bağımlılık:** T-07, T-14
- **Karşılar:** FR-13 · **Çözer:** TC-10 · **İlgili karar:** ADR-004
- **DoD:** İki istemci bağlıyken gönderilen bildirim ikisine de ulaşıyor; kopan bağlantı sunucuda kaynak sızdırmıyor.

### ☐ T-19 · Reprocess ve mükerrer gönderim
`POST /incident-reports/{id}/reprocess` — güncel kurallarla yeniden analiz. Ham metin değişmez; önceki
normalize kayıtların yerini yeni sonuç alır, mükerrer kayıt oluşmaz. Aynı metnin tekrar gönderilmesi
(TC-9) bu task'ta karara bağlanır.
- **Bağımlılık:** T-14
- **Karşılar:** FR-15 · **Çözer:** TC-9 · **İlgili karar:** ADR-012
- **DoD:** İki kez reprocess sonrası kayıt sayısı sabit; çözülen tarih kaymıyor (T-11 ile bağlantılı).

---

## Faz 6 — Kapanış

### ☐ T-20 · OpenAPI, README ve coverage doğrulaması
springdoc-openapi devreye alınır. README'deki `TODO` bölümleri doldurulur: gereksinimler, tek komutla
çalıştırma, yapılandırma/ortam değişkenleri, yerel geliştirme, durdurma, API örnekleri, test ve kapsam
raporu. Gerçek JaCoCo oranı ölçülür ve %80 eşiği doğrulanır.
- **Bağımlılık:** T-17, T-18, T-19
- **Karşılar:** NFR-07, NFR-10, NFR-02
- **DoD:** README'de `TODO` kalmadı; temiz makinede talimatlar birebir izlenerek sistem ayağa kalkıyor; coverage ≥ %80.

### ☑ T-21 · Git deposu ve GitHub'a ilk push  *(T-01 sonrasına alındı)*
`git init`, ilk commit, GitHub deposu ve push.
- **Bağımlılık:** — *(planda T-20'ye bağlıydı; kalan task'ların ayrı commit'ler halinde
  gidebilmesi için öne alındı)*
- **Karşılar:** NFR-11
- **DoD:** Kaynak kod GitHub üzerinden erişilebilir.
- **Sonuç:** https://github.com/eemreayy/incident-report-be — public, default branch `main`,
  ilk commit `e497e84` (25 dosya). Yerel ve uzak HEAD aynı.
- **Yetkilendirme:** GitHub CLI (`gh`) kuruldu; kullanıcı `gh auth login --git-protocol https --web`
  ile kendi tarayıcısından giriş yaptı. Token macOS keychain'de; sohbete hiçbir kimlik bilgisi
  girilmedi. Remote **HTTPS**, böylece makinedeki mevcut SSH anahtarı (başka bir projeye ait)
  hiçbir aşamada devreye girmiyor.
- **Commit kimliği:** Repo-local olarak `eemreayy <12291082+eemreayy@users.noreply.github.com>`.
  Global git ayarları değiştirilmedi; noreply adresi e-postayı public geçmişten uzak tutuyor.

---

## Bağımlılık Özeti

```
T-01 ✔ ─┬─ T-21 ✔  (depo hazır; her task ayrı commit)
        ├─ T-02
        ├─ T-03
        ├─ T-08 ──────────────┐
        ├─ T-09 ─┬─ T-10 ─┐   │
        │        ├─ T-12 ─┤   │
        │        └────────┼───┤
        └─ T-04 ─┬─ T-05 ─┴───┼── T-13 ─┐
                 │     └─ T-06│         │
                 ├─ T-07 ─────┼─────────┤
                 └────────────┴── T-14 ─┴─ T-11
                                   │
                                   ├─ T-15 (golden)
                                   ├─ T-16 ─ T-17 ─┐
                                   ├─ T-18 ────────┤
                                   └─ T-19 ────────┴─ T-20
```

**Kritik yol:** ~~T-01~~ → T-04 → T-05/T-07 → T-14 → T-20
**Paralel çalışılabilir:** T-02, T-03, T-08, T-09 (ve T-09'a bağlı T-10/T-12) erken aşamada birbirinden bağımsız ilerleyebilir.

---

## Teknik Challenge → Task Eşlemesi

| TC | Konu | Çözüldüğü task |
|---|---|---|
| TC-1 | Kayıt granülaritesi | T-04 |
| TC-2 | Metrik veri modeli | T-04 |
| TC-3 | Sayı ↔ metrik eşleştirme | T-14 |
| TC-4 | Türkçe bileşik sayı sözcükleri | T-10 |
| TC-5 | Türkçe normalizasyon | T-09 |
| TC-6 | Tarih ayrıştırma ve göreli ifadeler | T-11 |
| TC-7 | İl tanıma | T-12 |
| TC-8 | Sınıflandırma skorlaması ve eşik | T-13 |
| TC-9 | Mükerrer gönderim | T-19 |
| TC-10 | SSE bağlantı yönetimi | T-18 |
| TC-11 | Anlamlı %80 kapsam | T-03 (altyapı) + her task'ın kendi testleri |
