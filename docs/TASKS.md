# Task Kırılımı

[`docs/PRD.md`](PRD.md) v1.0 onaylandıktan sonra üretilmiş, **v2.0 ile güncellenmiştir**. Her task;
kapsamını, bağımlılıklarını, karşıladığı isterleri (FR/NFR) ve çözdüğü teknik challenge'ları (TC) taşır.

> **PRD v2.0 (2026-08-09) bu dosyaya ne getirdi.** Frontend kapsama alındı → **Faz 7** (T-23…T-31).
> Modül sahipliği kararı ([ADR-021](DECISIONS.md#adr-021--analiz-sonucunun-sahipliği-ve-gönderim-cevabının-kapsamı))
> → **Faz 2B / T-22**, tamamlanmış T-07'ye kısmi geri dönüş. Ayrıca frontend'in sözleşme
> beklentileri (PRD §8.2, C-1…C-8) mevcut T-14, T-16, T-17 ve T-18'in kapsamına yazıldı.

**Sıralama stratejisi:** Önce iskelet ve kalite kapısı, sonra veri modeli kararı, ardından **uçtan uca
çalışan ince bir dikey dilim**. Analiz motorunun asıl zorluğuna ancak boru hattı baştan sona
çalıştıktan sonra giriliyor — böylece iki veri tabanı, event akışı ve persist entegrasyonu, en riskli
iş olan Türkçe metin çıkarımıyla aynı anda hata ayıklanmak zorunda kalınmıyor.

**Çalışma akışı:** Depo baştan kuruldu (T-21 öne alındı), bu yüzden **her task kendi commit'i olarak**
GitHub'a gidiyor. Git geçmişi projenin nasıl inşa edildiğini gösteriyor; tek büyük commit'e göre
hem incelemesi hem geri alması kolay. Bir task'ın mimari kararı varsa `docs/DECISIONS.md`
güncellemesi de aynı commit'te yer alır.

**Depo:** https://github.com/eemreayy/incident-report

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
  `/actuator/health` → `UP`, `backend/app/target/incident-report.jar` içinde dört modül jar'ı var.
  `maven-compiler-plugin` `<release>21</release>` ile API yüzeyini 21'e sabitliyor;
  `maven-enforcer-plugin` JDK tabanını (≥21) ve Maven sürümünü (≥3.9) zorunlu kılıyor.
- **Modül sınırı doğrulandı:** `ingestion` içinden `analysis` sınıfına erişim denendi, build
  `package com.emreay.incidentreport.analysis does not exist` ile kırıldı. Sınır artık konvansiyon
  değil, derleme garantisi.
- **Not:** Veri tabanı starter'ları bilinçli olarak eklenmedi — şema kararı T-04'te veriliyor ve
  JPA starter'ını şimdi eklemek uygulamanın veri tabanı olmadan ayağa kalkmasını engellerdi.
- **Ortam notu:** Makinede JDK 21 yoktu (varsayılan 17). `brew install openjdk@21` ile kuruldu;
  `JAVA_HOME` export edilmeden Maven komutları enforcer kuralına takılır (bkz. `CLAUDE.md` → Commands).

### ☑ T-02 · Dockerize ve tek komutla ayağa kalkma
Uygulama için multi-stage `Dockerfile`; `docker-compose.yml` ile app + MongoDB + PostgreSQL (tek instance).
Health check'ler, servis bağımlılık sırası, named volume'lar, `.env.example`.
- **Bağımlılık:** T-01
- **Karşılar:** NFR-03, NFR-04 · **İlgili karar:** ADR-010
- **DoD:** Temiz makinede `docker compose up --build` sonrası API ve iki veri tabanı sağlıklı.
- **Sonuç:** `docker compose up --build` temiz durumdan üç servisi ayağa kaldırıyor, üçü de
  `healthy`. PostgreSQL 17.10, MongoDB 8.2.12. Uygulama `docker` profiliyle, root olmayan
  kullanıcı (uid 1001) ile çalışıyor. Host'tan 8080/5432/27017 erişilebilir; named volume'lar
  restart sonrası veriyi koruyor; SIGTERM PID 1'e ulaşıp Spring graceful shutdown yapıyor.
- **Sıfır kurulum:** Tüm ayarların compose içinde gömülü varsayılanları var, bu yüzden taze klon
  `.env` oluşturmadan çalışıyor (NFR-03'ün "tek seferde" isteri). `.env.example` yalnızca
  override içindir.
- **İmaj:** Multi-stage + Spring Boot layer ayrımı (dependencies / loader / application) +
  Alpine JRE → **336 MB**. Kod değişikliğinde yalnızca ~104 KB'lık `application` katmanı
  yeniden üretiliyor; 25 MB'lık bağımlılık katmanı cache'te kalıyor.

> **DoD kapsam notu — dürüstlük kaydı.** Bu task'ın orijinal DoD'si "uygulama iki veri tabanına da
> bağlanabiliyor" diyordu. Bu **yapılmadı ve bilinçli olarak T-04'e bırakıldı**: veri tabanı
> starter'ları henüz projede yok (T-01 kararı), çünkü JPA starter'ı datasource olmadan uygulamanın
> ayağa kalkmasını engelliyor ve Testcontainers altyapısı da henüz kurulmadı (T-03).
> Compose tarafı bağlantı için tamamen hazır: `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`,
> `SPRING_DATASOURCE_PASSWORD` ve `SPRING_DATA_MONGODB_URI` standart Spring isimleriyle
> container'a geçiyor — T-04'te starter'lar eklendiğinde compose dosyasında değişiklik gerekmeyecek
> ve `/actuator/health` her iki veri tabanını da raporlamaya başlayacak.

### ☑ T-03 · Kalite kapısı: coverage, kalan sınır kuralları, Testcontainers
JaCoCo `verify` fazına bağlanır ve **%80** altında build kırılır. Çok modüllü yapıda toplam oranın
nasıl hesaplanacağı (modül bazlı eşik mi, birleşik rapor mu) bu task'ta karara bağlanır.
Testcontainers ile Mongo ve Postgres için ortak test altyapısı.

**Kapsam notu:** Modüller arası erişim yasağı T-01'de **build seviyesinde** çözüldü (bağımlılık
grafiğinde kenar yok → derleme hatası). Bu task'a kalan, derleyicinin göremediği kurallar:
controller'ın entity/document sızdırmaması, `analysis` içinde Mongo tipi kullanılmaması,
katman yönü (repository → service → controller).
- **Bağımlılık:** T-01
- **Karşılar:** NFR-02, NFR-05
- **DoD:** Kasten yazılmış bir katman/sızıntı ihlali testi kırar; coverage eşiğinin altına düşünce build kırılır.
- **Sonuç:** JaCoCo 0.8.15 — modül başına satır eşiği %80 (`verify`), `app`'te proje geneli
  birleşik rapor (`app/target/site/jacoco-aggregate/`). ArchUnit 1.5.0 ile **12 mimari kuralı**,
  `app` modülünün testlerinde. Testcontainers 1.21.4 (sürüm Boot'tan) ile smoke test.
  Toplam 15 test geçiyor. Kararlar: **ADR-017** (ArchUnit, Spring Modulith yerine) ve
  **ADR-018** (coverage stratejisi).
- **Her iki kapı da fiilen doğrulandı:**
  - Testi olmayan sınıf eklendi → `Rule violated for bundle app: lines covered ratio is 0.00,
    but expected minimum is 0.80` → BUILD FAILURE.
  - `Repository → Controller` bağımlılığı eklendi → `Architecture Violation ... was violated
    (3 times)` → BUILD FAILURE. Probe sınıfları sonrasında silindi.
- **Testcontainers yaklaşımı:** Smoke test veri tabanlarına sürücüyle bağlanmıyor; container
  içinden `pg_isready` ve `mongosh ping` çalıştırıyor — compose'daki healthcheck'lerle aynı
  yöntem. Sürücüler onları kullanan kodla birlikte T-04'te geliyor; sadece "container ayağa
  kalkıyor mu" demek için erken bağımlılık çekilmedi. İmaj etiketleri compose ile aynı
  sürümlere sabit, böylece test ve çalışan sistem birbirinden kaymıyor.
- **Ortak test altyapısı notu:** Paylaşılan base sınıflar bu task'ta yazılmadı. Henüz tek bir
  repository yok; tüketicisi olmayan bir soyutlama kurmak yerine sürüm yönetimi merkezileştirildi
  ve mekanizmanın çalıştığı kanıtlandı. Base sınıflar ilk gerçek repository testiyle (T-04/T-05)
  gelecek.
- **Yeni kısıt:** `./mvnw verify` artık çalışan bir Docker daemon gerektiriyor. İmaj derlemesi
  gerektirmiyor (`Dockerfile` paketlemeyi `-DskipTests` ile yapıyor).

> **Bilinen boşluk — dürüstlük kaydı.** JaCoCo, `jacoco.exec` bulunmayan modülde `check` goal'ünü
> **sessizce atlıyor**. Yani **kodu olup hiç testi olmayan bir modül kapıdan geçer** — NFR-02'nin
> tam da engellemesi gereken durum. Bugün hiçbir modülün üretim kodu olmadığı için etkisi yok.
> Kapatma yolu Surefire'ın `failIfNoTests=true` ayarı; bugün açılamıyor çünkü dört modülün ne
> sınıfı ne testi var. **T-05'te açılacak** (o task'ın kapsamına yazıldı). Proje geneli birleşik
> rapor bu boşluktan etkilenmiyor: exec verisi olmayan modüllerin sınıflarını kapsanmamış sayıyor.
> Eksik olan otomatik kapı, ölçüm değil. Bkz. ADR-018.

---

## Faz 1 — Veri Modeli Kararı *(bloke edici)*

### ☑ T-04 · TC-1 ve TC-2'yi karara bağla, şemayı kur
**Ek kapsam (T-02'den devredildi):** Veri tabanı starter'ları (`spring-boot-starter-data-mongodb`,
`spring-boot-starter-data-jpa`, `postgresql`, `flyway`) ilgili modüllerin pom'una eklenir ve
uygulamanın her iki veri tabanına gerçekten bağlandığı `/actuator/health` üzerinden doğrulanır.
Compose tarafı hazır; ortam değişkenleri standart Spring isimleriyle zaten geçiyor.

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
- **Kararlar:** **ADR-019** (granülarite) ve **ADR-020** (metrik modeli). Belirleyici olan, kaynak
  dokümanın amaç cümlesindeki *"zaman içinde, coğrafi bölge bazında"* ifadesi: tarih ve il, kaydın
  kimliğinin parçası oldu. İle atanamayan sayılar (`SHARED`) bölüştürülmüyor, düşürülmüyor;
  kapsadıkları iller ayrı bir tabloda kayıtlı.
- **Sonuç:** Flyway `V1__schema.sql` (6 tablo, CHECK constraint'ler, indexler) + `V2` (81 il).
  Mongo dökümanı `record` olarak yazıldı — değişmezlik yapısal, setter yok. JPA entity'leri üç
  fabrika metoduyla (`forProvince` / `sharedAcross` / `withoutProvince`) şemadaki
  `incident_province_matches_scope` kuralını Java tarafında da imkânsız kılıyor.
  `ddl-auto=validate`, yani entity ile migration ayrışırsa uygulama ayağa kalkmıyor.
- **Doğrulandı:** 26 test geçiyor (14'ü yeni), coverage **%98**. Docker'da `db` ve `mongo`
  health bileşenlerinin ikisi de `UP`; `flyway_schema_history` iki migration'ı da başarılı
  gösteriyor; `province` tablosunda 81 satır.
- **T-02'den devredilen DoD kapandı:** "uygulama iki veri tabanına da bağlanabiliyor" artık
  gerçek. Compose dosyasında değişiklik gerekmedi — ortam değişkenleri T-02'de standart Spring
  isimleriyle bağlanmıştı.
- **Kalite kapısı işini yaptı:** ilk denemede `analysis` %77'de kaldı. Eksik satırlar test
  yazılarak değil **kod silinerek** kapatıldı: `Province`'in public constructor'ı ölü koddu
  (iller Flyway'den geliyor), çocuk entity'lerin geri-referans getter'ları kullanılmıyordu.
  Ayrıca `Incident.toString()` lazy association'a dokunuyordu — transaction dışında loglanınca
  patlayacak gerçek bir tuzak; sadeleştirildi ve testle sabitlendi.
- **Not:** Bu task sonraki her şeyi bloke ediyordu; T-05 ve T-07 artık açık.

---

## Faz 2 — Uçtan Uca Dikey Dilim

Amaç: gerçek çıkarım mantığı olmadan, boru hattının baştan sona çalıştığını kanıtlamak.

### ☑ T-05 · Ingestion: ham metnin değişmez saklanması
Mongo document + repository + `IngestionService`. Create, tekil read, sayfalı list. Metin **bayt bayt**
gönderildiği gibi, hiçbir normalizasyon uygulanmadan yazılır. Update/delete yok. Kayıt sonrası domain
event yayınlanır.
- **Bağımlılık:** T-04
- **Karşılar:** FR-01, FR-02, FR-14 · **İlgili karar:** ADR-005
- **DoD:** Kaydedilen metin girdiyle birebir aynı; update/delete API'si yok; event yayınlanıyor.
- **Ek kapsam (T-03'ten devredildi) — yapıldı:** Surefire `failIfNoTests=true` parent'a eklendi.
  ADR-018'deki boşluk kapandı: testi olmayan modül artık coverage kapısını sessizce geçemiyor.
  **Doğrulandı** — `shared`'ın testleri geçici olarak kaldırıldığında build
  `No tests to run!` ile kırıldı. `realtime` tek istisna: üretim kodu T-18'de geleceği için
  kendi pom'unda açık bir override taşıyor, kaldırılacağı yorumda yazılı.
- **Sonuç:** `IngestionService` — doğrulama, saklama, event yayını, tekil/sayfalı okuma.
  Update/delete yok. Doğrulama servis katmanında, web katmanında değil: reprocess yolunun
  arkasında HTTP isteği yok, kural yine de geçerli olmalı.
- **Kural 4 doğrulandı:** Event dinleyicisi patladığında ham metin hayatta kalıyor, kayıt
  `FAILED` işaretleniyor ve **çağırana hata dönmüyor**. Metni kaybetmek tasarımın reddettiği
  tek sonuç. Testte gerçek bir dinleyici yerine yayıncı mock'u fırlatıyor.
- **Event ham metni taşıyor, sadece id'yi değil.** `analysis` modülü Mongo'yu okuyamaz
  (ADR-002), dolayısıyla id-only bir event dinleyiciye üzerinde çalışacak hiçbir şey bırakmaz.
  `submittedAt` de aynı sebeple taşınıyor: göreli tarihlerin referansı (ADR-014).
- **`Clock` enjekte ediliyor.** Gönderim zamanı, göreli ve varsayılan tarihlerin referansı
  olduğu için testin zamanı sabitleyebilmesi gerekiyor.
- **Doğrulandı:** 51 test geçiyor (25'i yeni). Modül başına coverage eşiği tüm modüllerde
  sağlanıyor.

### ☑ T-06 · REST katmanı ve hata sözleşmesi
`POST /api/v1/incident-reports`, `GET /incident-reports`, `GET /incident-reports/{id}`. Girdi doğrulama
(boş metin, maksimum uzunluk). `@RestControllerAdvice` ile RFC 7807 `application/problem+json`.
Cevap modelinde `warnings[]` alanı.
- **Bağımlılık:** T-05
- **Karşılar:** FR-01, FR-14
- **DoD:** Geçersiz girdi açıklayıcı problem+json döner; stack trace sızmaz.
- **Sonuç:** Üç uç çalışıyor; 69 test geçiyor. `POST` **201 Created** + `Location` başlığı
  döndürüyor.
- **201'in dayanağı T-22'de değişti — sonuç aynı kaldı.** İlk gerekçe "analiz aynı çağrının
  içinde senkron tamamlanıyor (ADR-003), 202 sonradan gelecek bir sonuç vaat ederdi" idi.
  [ADR-021](DECISIONS.md#adr-021--analiz-sonucunun-sahipliği-ve-gönderim-cevabının-kapsamı)
  senkronluğu istemci sözleşmesinden çıkardığı için bu dayanak artık kullanılamaz. **Yeni
  dayanak:** bu ucun yarattığı kaynak ham bildirimdir ve cevap yazıldığında o kaynak
  **vardır** — 201 tam olarak bunu söyler. Analizin bitip bitmediği ayrı bir soru, ayrı bir
  cevabı var; bu yüzden burada cevaplanmıyor. Gerekçe değişti, kod değişmedi.
- **Bean validation kullanılmadı, bilinçli.** Doğrulama kuralları servis katmanında tek yerde:
  reprocess yolunun arkasında HTTP isteği yok ve aynı kurallara uymak zorunda. Aynı kuralı
  `@NotBlank` ile ikinci kez yazmak, ikisinden birinin unutulacağı iki kaynak yaratırdı.
- **Hata sözleşmesi `app` modülünde.** Tüm modüller adına cevap verdiği için tek bir yerde
  olmalı; `shared` framework'süz kalsın diye oraya konmadı. `ResponseEntityExceptionHandler`
  genişletildiği için Spring'in kendi hataları da (bozuk JSON, yanlış metot, yanlış content-type)
  aynı şekilde dönüyor.
- **Sızıntı yok:** Beklenmeyen hata `internal` kodu ve bir `reference` ile 500 dönüyor; istisna
  tipi, mesajı ve JDBC URL'i cevaba girmiyor — log'a gidiyor. `failureReason` alanı da DTO'ya
  hiç konmadı. İkisi de testle sabitlendi.
- **ArchUnit kuralı keskinleştirildi.** T-03'te yazdığım "controller persistence tipine
  dokunamaz" kuralı fazla genişti ve doğru mapping kodunda ateşledi. Kural kaldırılmadı,
  kastedilen şeye indirgendi: *handler bir entity/document **döndüremez**, DTO alanı
  entity/document **olamaz***. Doğrulandı — controller dökümanı döndürünce build kırılıyor.
- **Bir hata yakalandı ve düzeltildi:** 405 cevabı `code: request.malformed` diyordu; yanlış
  metot bozuk gövde değil. Kod artık status'tan türetiliyor:
  `request.method-not-allowed`, `request.unsupported-media-type`, `request.bad-request`.
  `code` sözleşmenin makine tarafından okunan yarısı — orada belirsizlik, ayrıntıdan kötü.
- **Uçtan uca doğrulandı** (Docker): 201+Location, Türkçe karakterler bozulmadan geri geliyor,
  liste en-yeni-önce sayfalanıyor, 400/404/405/415 hepsi `application/problem+json`.

### ☑ T-07 · Analiz boru hattı iskeleti ve kalıcılaştırma
`analysis` modülü event'i **senkron** dinler, normalize kayıt üretir, Postgres'e yazar ve ham kayda
iki yönlü bağlar. Analiz hatası ham metnin kaydını geri almaz — ham kayıt `FAILED` işaretlenir.
- **Bağımlılık:** T-04, T-05
- **Karşılar:** FR-03, FR-08 · **İlgili kararlar:** ADR-002, ADR-003
- **DoD:** Metin gönderildiğinde Mongo'ya ve Postgres'e kayıt düşüyor; her iki yönde de izlenebilir; analiz hatası ham kaydı silmiyor.
- **Sonuç:** Dikey dilim tamamlandı. 97 test geçiyor. Docker'da uçtan uca doğrulandı: tek `POST`
  isteği içinde Mongo'ya ham metin, Postgres'e türetilmiş kayıt düşüyor ve cevap `ANALYZED`
  durumu ile uyarıları taşıyarak dönüyor.
- **Event iki yönlü.** `RawReportSubmittedEvent` gidiyor, `RawReportAnalyzedEvent` dönüyor —
  ikisi de `shared`'da. Modüller birbirini hâlâ tanımıyor; derleme grafiğinde aralarında kenar yok
  ve ArchUnit'in çevrim kuralı da temiz. Dönüş yolu olmasaydı her kayıt sonsuza kadar `RECEIVED`
  kalırdı: analiz edilmiş bir rapor hakkında "kimse bakmadı" diyen bir durum.
- **`submit()` yayından sonra kaydı yeniden okuyor.** Analiz `publishEvent` çağrısının içinde
  çalışıp raporu güncelliyor; yayından önce yakalanan kopya artık bayat. Onu döndürmek her
  çağırana "raporunuz hâlâ RECEIVED ve uyarı yok" demek olurdu — üstelik bunu bayatlatan çağrının
  kendisi tarafından.
- **Geçici extractor bir taklit değil.** `UnclassifiedIncidentExtractor`, katalogda hiçbir şey
  eşleşmediğinde her metnin izleyeceği **gerçek yolun** ta kendisi (ADR-006): `OTHER` /
  `UNCLASSIFIED`, gönderim tarihinden `DEFAULTED` tarih ve nedenini söyleyen uyarılar. Katalog
  bugün boş olduğu için her metin bu yoldan geçiyor — ama doğru sebeple. T-09…T-14 tanıdıkları
  metinler için cevap verecek, tanımadıkları için bu davranış yerinde kalacak.
- **Seam kuruldu:** `IncidentExtractor` arayüzü + `ExtractedIncident`/`ExtractionResult` değer
  tipleri. Çıkarım kodu veri tabanı görmüyor; iller entity yerine plaka koduyla taşınıyor ve
  eşleme sırasında `AnalysisService` çözüyor. Türkçe metin kurallarını JUnit'ten başka hiçbir şey
  olmadan test edebilmek için.
- **Kapsam kuralı tek yerde.** `ExtractedIncident` kendi invariant'ını doğrulamıyor; `SINGLE`
  kaydın il taşıması kuralı `Incident`'in fabrika metotlarında ve şemadaki CHECK'te. Üçüncü kez
  yazmak, birbiriyle uyumlu tutulacak üç yer demekti; tutarsız bir çıkarım eşleme adımında
  yüksek sesle patlıyor (testle sabitlendi).
- **Reprocess yapısal olarak güvenli.** `analyze` önce o rapordan türeyen kayıtları siliyor,
  sonra yazıyor; hepsi tek Postgres transaction'ında. İkinci çalıştırma satırları ikiye
  katlamıyor ve T-19 ayrı bir kod yolu yazmak zorunda kalmayacak.
- **İki yönlü izlenebilirlik (FR-08) çalışan sistemde doğrulandı:** Postgres `raw_report_id` →
  Mongo `_id`, ve ters yönde sorgu. İki ayrı bildirim birbirine karışmıyor.

> **Kısmen geçersiz — PRD v2.0.** Bu task'ta kurulan **dönüş yolu** (`RawReportAnalyzedEvent` →
> `ingestion` dökümanını günceller) [ADR-021](DECISIONS.md#adr-021--analiz-sonucunun-sahipliği-ve-gönderim-cevabının-kapsamı)
> ile kaldırılıyor: analiz sonucu onu üreten modülde yaşayacak. Yukarıdaki "Event iki yönlü"
> maddesi **T-22 ile yerini yeni kurguya bırakır**; task'ın geri kalanı (dikey dilim, extractor
> seam'i, reprocess'in yapısal güvenliği, iki yönlü izlenebilirlik) aynen geçerli.
> Dürüstlük kaydı: burada yazılan çalışan koda bilinçli bir geri dönüş var; gerekçesi ADR-021'de.

---

## Faz 2B — Sahiplik Düzeltmesi *(PRD v2.0 ile geldi)*

### ☑ T-22 · Analiz sonucunun sahipliği
[ADR-021](DECISIONS.md#adr-021--analiz-sonucunun-sahipliği-ve-gönderim-cevabının-kapsamı)'in
uygulanması. Analiz sonucu (durum, uyarılar, analiz zamanı) onu üreten modüle taşınır; ham döküman
write-once olur; gönderim cevabı makbuza indirgenir.

**Silinecek** (`ingestion`): `RawReportAnalyzedEvent` ve `shared`'daki tanımı,
`RawReportAnalyzedEventListener`, `IngestionService.markAnalyzed`, `ProcessingStatus`,
`IncidentReportResponse`'un `status` ve `warnings` alanları — testleriyle birlikte.
Mongo dökümanından `status`, `warnings`, `analyzedAt`, `failureReason` alanları kalkar.

**Eklenecek** (`analysis`): ham bildirim başına analiz sonucu kaydı — durum (`ANALYZED`/`FAILED`),
uyarılar, analiz zamanı ve başarısızlık nedeni (yalnızca sunucu tarafında; cevaba girmez, T-06 kuralı).
Yeni Flyway migration. Reprocess aynı kaydın üzerine yazar, ikinci satır açmaz.

- **Bağımlılık:** T-07
- **Karşılar:** FR-02, FR-09, FR-13, FR-14, FR-15, FR-19 · **Çözer:** TC-12 · **Karar:** ADR-021
  *(ADR-003 revize edildi; ADR-004 ve ADR-005 not düzeyinde netleşti)*
- **DoD:**
  - `ingestion` modülünde `analysis`'e ait hiçbir kavram kalmadı; ham döküman yazıldıktan sonra
    **hiç güncellenmiyor** (testle sabitlenir: kayıt sonrası döküman sürümü değişmiyor).
  - `POST /incident-reports` yalnızca kimlik ve gönderim zamanı dönüyor; `status`/`warnings` yok.
  - Analiz patladığında ham metin yine hayatta ve çağırana hata dönmüyor (Kural 4 korunuyor);
    başarısızlık `analysis` tarafındaki kayıttan okunabiliyor.
  - `shared` içinde `analysis → ingestion` yönünde event tipi kalmadı; ArchUnit çevrim kuralı temiz.
- **Not — dürüstlük kaydı:** Bu task T-07'de yazılmış çalışan koda **bilinçli bir geri dönüştür**.
  Gerekçe ADR-021'de: derleme grafiğinde görünmeyen ama anlamsal ve zamansal olarak var olan bir
  bağımlılık, ancak taşıma katmanı değiştiğinde faturasını keserdi. Bugün ödemek ucuz.
- **Not — sıralama:** T-16 ve T-18 bu task'a bağlı. Frontend fazı da (T-24'ten itibaren) bu
  sözleşmeyi varsayıyor; erken yapılması paralel çalışmayı açıyor.
- **Sonuç:** 105 test geçiyor. Uçtan uca doğrulandı:
  - `POST` cevabı artık yalnızca `{id, submittedAt}`.
  - Mongo dökümanı yalnızca `_id`, `rawText`, `submittedAt` taşıyor — `status`, `warnings`,
    `analyzedAt`, `failureReason` gitti; testle sabitlendi (döküman anahtarları doğrudan
    kontrol ediliyor).
  - Analiz sonucu `analysis_result` + `analysis_warning` tablolarında, `V3` migration'ıyla.
  - `shared` içinde tek event tipi kaldı: `RawReportSubmittedEvent`.
- **Kural 4 taşındı, zayıflamadı.** "Analiz patlarsa ham metin hayatta kalır" garantisi artık
  `ingestion`'da değil, `RawReportSubmittedEventListener`'da tutuluyor — istisna orada yakalanıp
  `analysis` tarafına yazılıyor ve gönderene yansımıyor. Testleri de oraya taşındı; ayrıca
  "başarısızlığı yazmak da başarısız olursa" yolu eklendi: Postgres çökmüşse bu, gönderimlerin
  reddedilmesine dönüşmemeli.
- **`AnalysisResult` rapor başına tek satır.** `raw_report_id` unique; reprocess satırın üzerine
  yazıyor, ikinci satır açmıyor. "Bu metin hakkında sistem şu an ne biliyor" sorusunun tek bir
  güncel cevabı var. CHECK constraint'i de `FAILED` kaydın nedenini taşımasını zorunlu kılıyor.
- **`failureReason` sunucu tarafında kalıyor.** Entity'de var, hiçbir DTO'ya girmiyor — T-06'daki
  "istisna tipi ve mesajı cevaba sızmaz" kuralının aynısı.
- **Postman koleksiyonu yeniden üretildi.** Örnekler yine çalışan sistemden yakalandı; Submit
  testleri artık makbuzun analiz sözlüğünden **hiçbir alan taşımadığını** doğruluyor
  (12 istek, 49 assertion).
- **Bir test kırılganlığı düzeltildi:** `IncidentReportApplicationTests` migration **sayısını**
  kontrol ediyordu (`2` bekliyordu) ve `V3` eklenince kırıldı. Sayı yerine "hiçbir migration
  başarısız değil" kontrolüne çevrildi — sayı kontrolü, her migration'da testi düzeltmeyi
  öğretirdi.

---

## Faz 3 — Analiz Motoru *(projenin asıl zorluğu)*

### ☑ T-08 · Olay tipi kataloğu (YAML) ve metadata ucu
Olay tipleri, tetikleyici anahtar kelimeler ve metrik tanımları YAML'dan yüklenir; **uygulama
başlangıcında doğrulanır** ve katalog hatalıysa uygulama ayağa kalkmaz. `GET /api/v1/metadata`
kataloğu ve il listesini sunar.
- **Bağımlılık:** T-01
- **Karşılar:** FR-16, NFR-08 · **İlgili karar:** ADR-007
- **DoD:** Yeni olay tipi eklemek yalnızca YAML değişikliği; bozuk katalogda uygulama açık hata mesajıyla ayağa kalkmıyor.
- **Sonuç:** 142 test geçiyor, coverage **%98**. Katalog
  `backend/analysis/src/main/resources/incident-catalog.yml`'de; 5 olay tipi, 10 farklı metrik.
  `GET /api/v1/metadata` kataloğu ve 81 ili plaka sırasıyla sunuyor.
- **DoD fiilen doğrulandı (çalışan sistemde):**
  - *Yeni tip yalnızca YAML ile:* `AVALANCHE` (Çığ) tanımlı bir katalog dosyası gösterildi,
    uygulama kod değişmeden onu tanıdı ve metadata ucundan yayınladı.
  - *Bozuk katalog:* geçersiz bir dosyayla container `exitCode=1` ile durdu ve **dört problemi
    birden** listeledi (anahtar formatı, etiket yok, kelime yok, metrik yok).
- **Etiketler katalogda, arayüzde değil.** Aksi halde `FLOOD` eklemek bir frontend sürümü de
  gerektirirdi. Sınır: kendi kendine büyüyen veri uçtan gelir, yalnızca kod değişince değişen
  yapısal enum'lar tipli sözleşmede kalır. Gerekçe ADR-007'ye işlendi.
- **Anahtar kelimeler yayınlanmıyor** — çıkarımı besliyorlar, sunumu değil.
- **Doğrulama startup'ta ve kapsamlı:** anahtar uzunluğu `varchar(48)` kolon genişliğine karşı
  kontrol ediliyor (aksi halde hata aylar sonra insert'te patlardı); aynı metrik anahtarının
  farklı etiket taşıması reddediliyor; tüm problemler tek seferde bildiriliyor.
- **Postman:** `Catalog` klasörü eklendi (13 istek, 54 assertion).
- **Yan düzeltme:** T-05'te eklediğim `failIfNoTests`, `CLAUDE.md`'de belgelenen
  `./mvnw test -Dtest=ClassName` komutunu kırıyordu. Property'ye çevrildi ve komut düzeltildi;
  tam build'de kapı aynen açık kalıyor.
- **Test altyapısı:** `Province` mock'lamak üç kez iç içe stubbing tuzağına düşürdü.
  `ProvinceFixture` gerçek nesne kuruyor; tuzak tamamen kalktı.

### ☑ T-09 · Türkçe metin normalizasyonu ve cümle bölme
Locale duyarlı büyük/küçük harf (i/İ/ı/I), Unicode normalizasyonu, noktalama ve apostrof işleme,
cümle segmentasyonu (kısaltmalar ve `20.04.2020` gibi noktalı tarihler cümle sonu sanılmamalı).
- **Bağımlılık:** T-01
- **Çözer:** TC-5 · **İlgili karar:** ADR-027
- **DoD:** `"İZMİR"` doğru küçültülüyor; tarih içeren cümle yanlış bölünmüyor; tablo bazlı testler mevcut.
- **Sonuç:** 190 test geçiyor, `analysis` coverage **%98**, yeni `analysis.text` paketi **%99**
  (`TurkishTextNormalizer` ve `NormalizedText` %100). Dört sınıf: `TurkishTextNormalizer`,
  `NormalizedText`, `SentenceSplitter`, `Sentence`.
- **Normalizasyon ham metindeki konumu kaybetmiyor.** Sözleşme çıkarılan anahtar kelimenin ham
  metindeki offset'ini istiyor (C-3, TC-18), normalizasyon ise uzunluğu değiştiriyor — ölçüldü:
  aynı metnin NFD hâli 21, NFC hâli 15 karakter. Bu yüzden çıktı düz `String` değil; her
  karakteri hangi ham aralıktan geldiğini taşıyan `NormalizedText`. Sonradan eklenemeyecek bir
  özellik: `String`→`String` yazılsaydı bilgi geri dönüşsüz kaybolurdu.
- **`BreakIterator` ölçüldü ve elendi — iki isterde de yanılıyor:**
  - `"... tespit edildi. 1 kişi vefat etti."` → **tek** cümle sayıyor (rakamdan önce bölmüyor).
    Bir cümledeki sayının başka cümlenin metriğine yazılması demek; TC-3'ün engellemesi gereken hata.
  - `"Dr. Ahmet açıklama yaptı."` → `Dr.`'yi ayrı cümle yapıyor.
  Elle yazılan bölücü ikisini de doğru işliyor; kural sayısı az ve her biri kaynak dokümanda
  fiilen geçen bir duruma karşılık geliyor.
- **DoD fiilen doğrulandı:** `"İZMİR"` → `izmir` (root locale `i̇zmi̇r` üretiyor — 7 karakter, hiçbir
  ille eşleşmez; test bu farkı da sabitliyor). Kaynak dokümandaki **üç örnek metnin üçü de** tam üç
  cümleye bölünüyor ve her cümle `originalTextIn` ile ham metindeki karşılığına birebir dönüyor.
  53 tablo bazlı test (20 bölücü + 33 normalizasyon).
- **Ölçüm iki gerçek hata yakaladı:**
  - `String.isBlank()` **kırılmaz boşluğu (U+00A0) boşluk saymıyor** — `Character.isWhitespace`
    onu bilerek dışlıyor. Webden yapıştırılan metinde çok yaygın; kelimeyi sessizce birleşik
    bırakıyordu. Boşluk kontrolü `SPACE_SEPARATOR` kategorisini de kapsayacak şekilde düzeltildi.
  - `SentenceSplitter` bean değildi; `app` context testi bunu ilk denemede yakaladı.
- **Bölücü bilerek bölmemeye eğimli.** Yanlış birleştirilen cümle her sayıyı kendi anahtar
  kelimesinin yanında tutar; yanlış bölünen cümle ikisini ayırır ve sayı kaybolur ya da yanlış
  metriğe yazılır. Birinci hata isabeti, ikincisi doğruluğu düşürür.

### ☑ T-10 · Sayı ayrıştırma (rakam + Türkçe sözcük)
Rakamla yazılmış sayılar ve Türkçe sayı sözcükleri; bileşikler dahil (`on iki`=12, `kırk beş`=45,
`yüz yirmi`=120). Sayının metindeki konumu (offset) korunur — metrik eşleştirmesi buna dayanacak.
- **Bağımlılık:** T-09
- **Çözer:** TC-4 · **Karşılar:** FR-05 · **İlgili karar:** ADR-028
- **DoD:** `"on iki bina"` ile `"12 bina"` aynı değeri üretiyor; sınır ve olumsuz vakalar test edilmiş.
- **Sonuç:** 252 test geçiyor, `analysis` coverage **%98**, `analysis.text` paketi **%99**.
  `NumberExtractor` + `NumberToken` + `NumberNotation`; 62 tablo bazlı test.
- **DoD fiilen doğrulandı:** `"on iki bina hasar gördü"` ile `"12 bina hasar gördü"` aynı değeri
  (12) üretiyor. Rakam, sözcük ve karışık yazım (`15 bin`, `2 bin 500`) destekleniyor;
  `iki bin üç yüz kırk beş` = 2345, `iki yüz bin` = 200000.
- **Bileşimde kural toplama değil, azalan büyüklük.** `on iki` = 12 çünkü 2 < 10. Aynı kural
  `bir iki kişi` ifadesini 3 diye okumayı engelliyor — bu bir sayı değil, "birkaç" kalıbı.
  Kural olmasa yan yana her sayı sözcüğü toplanır ve metinde olmayan figür üretilirdi.
- **Tarih üç sayı değil.** `20.04.2020` ayrıştırılsaydı 20, 4 ve 2020 diye üç makul görünen değer,
  üstelik anahtar kelimenin hemen yanında çıkardı. Rakamlı tarih biçimleri atlanıyor.
  **Sınır açıkça kabul edildi:** `3 Mayıs 2020` burada tanınamaz (takvim gerekir), `3` ve `2020`
  üretilir; elemek T-11/T-14'ün işi. Gizlemek yerine teste yazıldı.
- **Test bir hata yakaladı:** `"2,5 milyar lira"` — ondalık figürü reddediyordum ama `milyar` tek
  başına kalıp **1 milyar** üretiyordu. Yani okumayı reddetmek başka bir sayı uydurmaya dönüşüyordu.
  Reddedilen figür artık kendisini takip eden çarpanı da kapsıyor.
- **T-14 için iki tuzak teste yazıldı:** sözcüklü tarihten sızan sayılar (`3`, `2020`) ve sayı olup
  metrik olmayan ifadeler — üçüncü örnekteki `her iki ilde` ifadesi `2` üretiyor.

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
- **Ek kapsam (PRD v2.0 · C-3):** Çıkarılan anahtar kelimeler **ham metindeki konumlarıyla**
  (başlangıç/bitiş offset'i) ve hangi çıkarımı tetiklediğiyle saklanır. Frontend, vurgulamayı
  metni yeniden aramadan yapmak zorunda (TC-18): Türkçe ek ve apostrof toleransı yüzünden
  istemcide arama yanlış yeri işaretler.
- **Bağımlılık:** T-04, T-10, T-12, T-13
- **Karşılar:** FR-03, FR-07, FR-17 · **Çözer:** TC-3
- **DoD:** Örnek 3'ün Bursa/Kocaeli kırılımı doğru; toplam yaralı hiçbir ile iki kez yazılmıyor;
  anahtar kelime offset'i ham metinde doğru aralığı işaret ediyor.

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
- **Ek kapsam (PRD v2.0):**
  - **C-5 · `rawReportId` filtresi.** Model 2'de gönderim cevabı yalnızca kimlik döndüğü için,
    sonucu getirmenin **tek** yolu bu filtre. Aynı filtre FR-08'in "ham bildirimden türeyen
    kayıtlara ulaşma" yönünü ve ham bildirim detay ekranını da besliyor.
  - **C-4 · analiz sonucu cevapta.** T-22'de `analysis`'e taşınan durum ve uyarılar bu uçtan döner.
  - **C-7 · toplam kayıt sayısı.** Sayfalama cevabı toplam sayıyı içerir; "sonuç yok" ile
    "sayfa boş" ayrımı istemcide ancak böyle yapılabilir.
- **Bağımlılık:** T-14, T-22
- **Karşılar:** FR-10, FR-17, FR-08, FR-19
- **DoD:** Filtreler tekil ve kombine çalışıyor; anahtar kelimeler hangi çıkarımı tetiklediğiyle
  birlikte görünüyor; `rawReportId` ile tek istekte o bildirimden türeyen tüm kayıtlar (+ analiz
  durumu ve uyarılar) dönüyor.

### ☐ T-17 · Zaman serisi, özet ve kümülatif
`GET /analytics/time-series` — olay tipi bazlı, metriklere ayrılmış seriler; opsiyonel il ve tarih aralığı;
`cumulative` parametresi. `GET /analytics/summary` — özet tablo agregasyonu.
- **Ek kapsam (PRD v2.0):**
  - **C-1 · il kırılımı.** İl yalnızca filtre değil, **seri boyutu** olmalı (`groupBy=province`).
    FR-24 ve [ADR-023](DECISIONS.md#adr-023--coğrafi-izlenebilirlik-harita-yerine-il-kırılımı):
    "coğrafi bölge bazında izlenebilirlik" tek il seçip bakmak değil, iller arası karşılaştırma.
    Bu olmadan istemci toplama yapmak zorunda kalır — NFR-13'e aykırı.
  - **C-2 · `SHARED` ve `UNKNOWN` ayrı ve etiketli.** Agregasyon uçları bu kapsamları ne
    bölüştürür ne düşürür; ayrı satır/seri olarak döner ki okuyucu il toplamı ile genel toplamı
    uzlaştırabilsin (ADR-019). Birden fazla il seçildiğinde `SHARED` kayıt link tablosu üzerinden
    `DISTINCT` ile **bir kez** sayılır.
- **Bağımlılık:** T-16
- **Karşılar:** FR-11, FR-12, FR-22, FR-24
- **DoD:** Kümülatif modda her nokta kendisi ve öncekilerin toplamı; agregasyon SQL üzerinde
  yapılıyor (bellekte değil); il kırılımlı sorguda örnek 3'ün `SHARED` yaralı toplamı ayrı ve
  etiketli dönüyor, Bursa+Kocaeli birlikte seçildiğinde iki kez sayılmıyor.

---

## Faz 5 — Gerçek Zamanlı ve Yeniden İşleme

### ☐ T-18 · SSE yayını
`GET /stream/incidents` — yeni normalize kayıt üretildiğinde bağlı tüm istemcilere tek yönlü yayın.
Bağlantı yaşam döngüsü: timeout, heartbeat, kopma/temizlik, çok istemcili yayın.
- **Kapsam daraldı (PRD v2.0 · C-8).** Olay bir **sinyaldir, veri taşıyıcısı değil**: istemcinin
  ilgisini belirlemesine yetecek kadar bilgi taşır (olay kaydı kimliği, ham bildirim kimliği,
  tarih, il, olay tipi), satır çizmeye yetecek kadar değil. Gerekçe
  [ADR-021](DECISIONS.md#adr-021--analiz-sonucunun-sahipliği-ve-gönderim-cevabının-kapsamı):
  veri taşıyan bir payload tablo sütunlarını akış sözleşmesine bağlar ve veriyi tek kanala emanet eder.
- **Sonucu:** `realtime` modülünün pom'undaki `failIfNoTests` override'ı bu task'ta kaldırılır (T-03/T-05 notu).
- **Bağımlılık:** T-14, T-22
- **Karşılar:** FR-13 · **Çözer:** TC-10 · **İlgili kararlar:** ADR-004, ADR-021
- **DoD:** İki istemci bağlıyken gönderilen bildirim ikisine de ulaşıyor; kopan bağlantı sunucuda
  kaynak sızdırmıyor; olay veri taşımıyor — istemci sinyali alıp sorgu uçlarından tazeliyor.

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
- **Ek kapsam (PRD v2.0):** README **frontend'i de** kapsar (kurulum, çalıştırma, test, kapsam);
  "Nasıl Çalışır" akışı Model 2'ye göre güncellenir (5 adım); mimari şemasındaki dönüş oku kalkar;
  API tablosunda `POST`'un daralan sorumluluğu ve `rawReportId` filtresi yazılır; "Tanınmayan olay
  tipi" bölümünde uyarıların hangi uçtan döndüğü düzeltilir. Frontend coverage oranı da ölçülür.
- **Bağımlılık:** T-17, T-19, T-31
- **Karşılar:** NFR-07, NFR-10, NFR-02
- **DoD:** README'de `TODO` kalmadı; temiz makinede talimatlar birebir izlenerek **frontend dahil**
  sistem ayağa kalkıyor; backend ve frontend coverage ≥ %80.

### ☑ T-21 · Git deposu ve GitHub'a ilk push  *(T-01 sonrasına alındı)*
`git init`, ilk commit, GitHub deposu ve push.
- **Bağımlılık:** — *(planda T-20'ye bağlıydı; kalan task'ların ayrı commit'ler halinde
  gidebilmesi için öne alındı)*
- **Karşılar:** NFR-11
- **DoD:** Kaynak kod GitHub üzerinden erişilebilir.
- **Sonuç:** https://github.com/eemreayy/incident-report — public, default branch `main`,
  ilk commit `e497e84` (25 dosya). Yerel ve uzak HEAD aynı.
- **Yetkilendirme:** GitHub CLI (`gh`) kuruldu; kullanıcı `gh auth login --git-protocol https --web`
  ile kendi tarayıcısından giriş yaptı. Token macOS keychain'de; sohbete hiçbir kimlik bilgisi
  girilmedi. Remote **HTTPS**, böylece makinedeki mevcut SSH anahtarı (başka bir projeye ait)
  hiçbir aşamada devreye girmiyor.
- **Commit kimliği:** Repo-local olarak `eemreayy <12291082+eemreayy@users.noreply.github.com>`.
  Global git ayarları değiştirilmedi; noreply adresi e-postayı public geçmişten uzak tutuyor.
- **Sonraki yapısal değişiklik:** Depo önce `incident-report-be` adıyla açıldı ve kompozisyon için
  ayrı bir `incident-report-devops` repo'su kuruldu (ADR-015). Ardından tek repo'ya geçildi:
  depo `incident-report` olarak yeniden adlandırıldı, içerik `backend/` altına taşındı, devops
  içeriği köke alındı. Yeniden adlandırma sayesinde geçmiş korundu; gerekçe **ADR-016**'da.

---

## Faz 7 — Frontend *(PRD v2.0 ile geldi)*

Sıralama mantığı backend'inkiyle aynı: önce iskelet ve kalite kapısı, sonra sözleşme katmanı,
ardından **uçtan uca çalışan ince bir dikey dilim** (metin gir → sonucu gör). Grafik ve canlı akış
gibi zor parçalara ancak boru hattı tarayıcıda baştan sona çalıştıktan sonra giriliyor.

Tüm frontend task'ları [ADR-022](DECISIONS.md#adr-022--frontend-teknoloji-tabanı-react--typescript--vite)
(React + TypeScript + Vite) ve [ADR-024](DECISIONS.md#adr-024--frontend-coverage-kapısı) (%80 kapısı)
altında çalışır; her task kendi testlerini taşır.

### ☑ T-23 · Frontend iskeleti, kalite kapısı ve Docker
Vite + React + TypeScript projesi `frontend/` altında. Test koşucusu ve **coverage eşiği %80**,
build'i kıracak şekilde. Lint/format. Çok aşamalı `Dockerfile` (build → statik sunucu). Kök
`docker-compose.yml`'de yorumda bekleyen `frontend` servisi açılır.

Bu task'ta **TC-17 karara bağlanır**: tarayıcı ile API arasında **aynı köken (reverse proxy)** mı
yoksa **CORS** mu (C-6). Proxy seçilirse SSE için tamponlamanın kapatılması gerektiği not edilir.
Ayrıca grafik ve veri katmanı kütüphaneleri seçilir. Her iki karar da `docs/DECISIONS.md`'e ADR
olarak yazılır (ADR-022 bunları bilinçli olarak açık bırakmıştı).
- **Bağımlılık:** —
- **Karşılar:** NFR-02, NFR-03, NFR-12, NFR-15 · **Çözer:** TC-17
- **DoD:** `docker compose up --build` sonrası tarayıcıdan tek adres açılıyor ve uygulama yükleniyor;
  kasten düşürülen coverage build'i kırıyor; API adresi ortam değişkeninden geliyor, koda gömülü değil.
- **Kararlar:** **ADR-025** (aynı köken, nginx reverse proxy — TC-17 çözüldü) ve **ADR-026**
  (kütüphane seti: TanStack Query · React Router · Recharts · Vitest · düz CSS).
- **Sonuç:** React 19.2 · TypeScript 6.0 · Vite 8.2 · Vitest 4.1. 10 test geçiyor, coverage %100.
  Üretim bundle'ı 264 kB (gzip 84 kB). `docker compose up --build` dört servisi ayağa kaldırıyor,
  dördü de `healthy`.
- **DoD maddeleri tek tek doğrulandı (çalışan sistem):**
  - Tarayıcıdan `http://localhost:3000` → uygulama yükleniyor, konsol temiz, "Sunucu: bağlı".
  - `fetch('/actuator/health')` → `sameOrigin: true`, 200, backend `UP` (`db` + `mongo`).
  - Derin bağlantı `/incidents/123` → 200 (SPA fallback); `/actuator/env` proxy'ye **düşmüyor**,
    `index.html` dönüyor — yalnızca `health` açık.
  - Coverage kapısı: kapsanmayan bir dosya eklendiğinde `EXIT=1` +
    `ERROR: Coverage for lines (70.58%) does not meet global threshold (80%)`; dosya silinince
    `EXIT=0`. Probe dosyası sonrasında kaldırıldı.
- **DoD'nin son maddesi karar gereği değişti.** "API adresi ortam değişkeninden gelir" yerine
  **hiçbir mutlak adres yok**: aynı köken seçilince istekler göreli oldu ve `VITE_API_BASE_URL`
  gereksizleşti (ADR-025). NFR-15 daha güçlü biçimde karşılanıyor — yapılandırılacak bir adres
  kalmadığı için yanlış yapılandırılamıyor da. Bir testle sabitlendi: probe'un `/actuator/health`
  göreli yolunu çağırdığı doğrulanıyor.
- **İki tuzak yakalandı ve düzeltildi:**
  - **Healthcheck IPv6.** `wget http://localhost:3000/` container içinde "connection refused"
    veriyordu; `/etc/hosts` `localhost`'u `::1`'e de eşliyor, nginx ise yalnız IPv4 dinliyor.
    Servis doğru çalışırken `unhealthy` görünüyordu. `127.0.0.1`'e sabitlendi.
  - **Coverage sağlayıcısı.** v8'in bileşenleri tek satır saydığını görüp istanbul'a geçildi;
    ölçüldüğünde istanbul'un da aynı sayıyı verdiği anlaşıldı — sebep sağlayıcı değil, gövdesi tek
    bir JSX `return`'ü olan bileşenin tek statement olması. v8'e (varsayılan, ek bağımlılık yok)
    geri dönüldü ve sayının ne ölçtüğü yorumda yazıldı. Bkz. ADR-024 "Sonuçlar".
- **Doğrulanamayan tek şey — dürüstlük kaydı.** `nginx.conf`'taki SSE bloğu (`proxy_buffering off`,
  `proxy_read_timeout 1h`) **çalışırken test edilmedi**, çünkü `/api/v1/stream/incidents` ucu henüz
  yok (T-18). Yapılandırma yerinde ve gerekçesi yorumda; fiilen doğrulanması T-29'a ait.
- **Not:** Recharts iskelette kullanılmıyor; ilk kullanıcısı T-28. Şimdi kurulmasının sebebi
  ADR-026'da: React 19 ile çözülüp derlendiğini bugün öğrenmek.

### ☑ T-24 · API istemcisi ve tipli sözleşme
Tek bir API katmanı: uç çağrıları, RFC 7807 (`application/problem+json`) hatalarının tipli
çözümlenmesi, sayfalama zarfı. Katalog metadata ucunun (FR-16) yüklenmesi ve tüm seçim kutularının
tek kaynağı hâline gelmesi.
- **Bağımlılık:** T-23, T-08 *(metadata ucu)*, T-22 *(cevap şekilleri)*
- **Karşılar:** FR-27, FR-28, NFR-13, NFR-14
- **DoD:** Hiçbir olay tipi/metrik/il listesi arayüzde sabit yazılı değil; sunucu hatası tek yerde
  çözümlenip kullanıcıya okunur mesaja dönüşüyor; ham JSON veya teknik ayrıntı ekrana basılmıyor.
- **Sonuç:** 35 test geçiyor, coverage %100 (71 satır — T-23'te 12'ydi; kapının dişi artık mantık
  katmanında). Katman: `api/client.ts` (tek kapı, göreli URL), `api/problem.ts` (RFC 7807),
  `api/types.ts`, `api/endpoints.ts`, `api/queries.ts` (sorgu anahtarları + hook'lar),
  `i18n/errorMessages.ts`.
- **Tipler çalışan sistemden yakalandı**, dokümandan yazılmadı — Postman koleksiyonunun kuralıyla
  aynı. Yakalanan sözleşme: metadata `{eventTypes[{key,label,metrics[{key,label}]}], provinces[{code,name}]}`;
  makbuz `{id, submittedAt}` + 201/Location; sayfalama zarfı `{content,page,size,totalElements,totalPages}`;
  problem `{type,title,status,detail,instance,code,timestamp}`.
- **Henüz olmayan uçlar için istemci yazılmadı.** `/incidents`, `/analytics/*` ve reprocess bugün
  404 dönüyor; onlara tip yazmak tahmin olurdu ve derlemeden geçtiği için yanıltırdı. T-16/T-17/T-19
  geldiğinde eklenecek.
- **Hata mesajları `code`'dan üretiliyor, `detail`'den değil.** Sunucunun `detail` alanı İngilizce
  ("Incident report text must not be empty."); Türkçe arayüzde ham gösterilemez. FR-18 "title/detail
  gösterilir" diyor — bilinçli sapma: `code` sözleşmenin makine tarafından okunan yarısı ve
  çevrilebilir olan da o. Bilinmeyen kod, İngilizce metne düşmek yerine genel bir cümleye düşüyor;
  testle sabitlendi.
- **Üç şey gerçek sistemde doğrulandı:**
  - Katalog paneli beş olay tipini metrik sayılarıyla ve 81 ili **sunucu cevabından** çiziyor;
    kaynakta hiçbir liste yok.
  - Backend tamamen durdurulduğunda arayüz beyaz ekrana düşmüyor: *"Sunucuya şu anda ulaşılamıyor"*
    ve **Tekrar dene** düğmesi görünüyor (FR-28).
  - İstekler göreli; `sameOrigin: true`.
- **Üç tuzak yakalandı:**
  - **nginx backend yokken hiç açılmıyordu.** `proxy_pass http://backend:8080` upstream'i başlangıçta
    çözüyor; bulamayınca `[emerg] host not found in upstream`. Yani T-23'ün bıraktığı halde
    **backend düşerse arayüz de komple ölüyordu** — FR-28'in tam tersi. Çözüm: `resolver 127.0.0.11`
    + `proxy_pass $backend` ile çözümlemeyi istek anına ertelemek. Artık backend hiç yokken bile
    frontend `healthy`, SPA 200, API 502.
  - **502 "beklenmeyen cevap" diyordu.** Backend düştüğünde nginx HTML gövdeli 502 döner; kullanıcı
    için anlamı "sunucu kapalı". 502/503/504 artık `gateway.unavailable` koduna ve kendi cümlesine
    eşleniyor. Bunu ancak gerçek sisteme karşı test edince gördüm.
  - **Sonsuz "yükleniyor".** TanStack Query varsayılan `networkMode: 'online'` ile, çevrimdışı
    sanıldığında yeniden denemeyi duraklatıyor ve `status` `pending`'de kalıyor — ekranda hiç bitmeyen
    bir spinner, FR-28'in yasakladığı şey. `networkMode: 'always'` yapıldı ve **davranış** testle
    sabitlendi (düzeltme geri alınınca test kırılıyor).
- **Kütüphane davranışı — bilinmesi gereken.** Yeniden denemeler belge **gizliyken** de duraklıyor:
  retryer'ın `canContinue()` fonksiyonu `focusManager.isFocused()`'ı VE ile bağlıyor, `networkMode`
  ne olursa olsun. Arka plandaki bir sekme, kendisine bakılana kadar spinner'ını korur. Kütüphanenin
  tasarımı böyle; ayarın çalışmaması değil. Otomasyon tarayıcısı `visibilityState: hidden`
  raporladığı için doğrulama sırasında bu davranış uzunca bir süre asıl hatayı maskeledi.

### ☐ T-25 · Bildirim girişi ve sonucun gösterilmesi *(dikey dilim)*
Tek metin alanı, gönderim, ardından **kimlikle sorgu** ile sonucun aynı ekranda gösterilmesi.
Uyarılar, `UNCLASSIFIED` etiketi ve tarih kaynağı (`EXPLICIT`/`RELATIVE`/`DEFAULTED`) görünür.
Gönderim sırasında düğme kilidi, boş/uzun metin kontrolü, karakter sayacı.
- **Bağımlılık:** T-24
- **Karşılar:** FR-18, FR-19, FR-20
- **DoD:** Üç örnek metin arayüzden girildiğinde sonuç anında görünüyor; SSE hiç bağlı değilken de
  çalışıyor (sonuç sorgudan geliyor); sıfır kayıt üreten metinde ekran boş kalmıyor, nedeni yazıyor.

### ☐ T-26 · Kayıt listesi, filtreler ve adres çubuğu durumu
Sayfalanmış tablo; olay tipi, il, tarih aralığı ve anahtar kelime filtreleri. Filtreleme, sıralama
ve sayfalama **sunucuda**. Aktif filtreler adres çubuğuna yansır. Grafik ve özetle paylaşılan tek
filtre kaynağı.
- **Bağımlılık:** T-25, T-16
- **Karşılar:** FR-21 · **Çözer:** TC-15
- **DoD:** Filtreli görünümün adresi kopyalanıp yeni sekmede açıldığında aynı görünüm geliyor;
  istemcide filtreleme yapılmıyor (ağ isteği ile doğrulanır); boş sonuçta boş durum mesajı çıkıyor.

### ☐ T-27 · Özet tablo ve il kırılımı
Olay tipi / il / metrik kırılımında agrega görünüm; agregasyon ucundan gelir, istemcide toplanmaz.
`SHARED` ve `UNKNOWN` kapsamlı toplamlar ayrı ve **etiketli** satır olarak gösterilir.
- **Bağımlılık:** T-26, T-17
- **Karşılar:** FR-22, FR-24 · **Çözer:** TC-14 · **Karar:** ADR-019, ADR-023
- **DoD:** Örnek 3 girildiğinde Bursa ve Kocaeli ayrı satırlarda; 10 yaralı hiçbir ile eklenmemiş,
  "her iki ilde toplam" olarak ayrı satırda; iki il birlikte seçildiğinde bir kez sayılıyor;
  il toplamları ile genel toplam okuyucu tarafından uzlaştırılabiliyor.

### ☐ T-28 · Grafik: olay tipi serileri, il kırılımı ve kümülatif
Olay tipi seçimine bağlı metrik serileri; il kırılımı; kümülatif anahtarı. Kümülatif dönüşüm ve
agregasyon **sunucudan** istenir. Seri gizle/göster; çok il seçildiğinde okunabilirlik.
- **Bağımlılık:** T-27
- **Karşılar:** FR-23, FR-24 · **Karar:** ADR-023
- **DoD:** Seçilen tipin metrikleri ayrı seriler olarak çiziliyor; kümülatif modda her nokta kendisi
  ve öncekilerin toplamı; grafik ile tablo aynı filtre durumunu gösteriyor — ikisi farklı veri
  gösteremiyor.

### ☐ T-29 · Canlı akış: SSE aboneliği ve tazeleme
`EventSource` ile akışa abonelik; sinyal geldiğinde liste, özet ve grafiğin tazelenmesi. Bağlantı
durumu göstergesi (bağlı / yeniden bağlanıyor / kopuk), otomatik yeniden bağlanma ve yeniden
bağlanınca tazeleme. Sayfa kapanınca bağlantının kapatılması.

**TC-13 burada karara bağlanır:** art arda gelen sinyaller nasıl birleştirilir (debounce/coalesce)
ve aktif filtreye uymayan olay ne yapar. Tazeleme sırasında görünüm **boşaltılmaz** — aksi halde
her sinyalde tablo bir an boşalır ve bu, kullanıcı gözünde sayfa yenilenmesinden farksızdır.
- **Bağımlılık:** T-28, T-18
- **Karşılar:** FR-25 · **Çözer:** TC-13 · **Karar:** ADR-004, ADR-021
- **DoD:** İki sekme açıkken birinde girilen bildirim diğerinde sayfa yenilenmeden listeye, özete
  ve grafiğe yansıyor; akış kapatıldığında gönderen sekme kendi sonucunu görmeye devam ediyor;
  on bildirim peş peşe girildiğinde on ayrı tazeleme yapılmıyor.

### ☐ T-30 · İzlenebilirlik ekranları ve reprocess
Olay kaydı detayı (metrikler, anahtar kelimeler, tarih kaynağı, kapsam, kaynak bildirim bağlantısı)
ve ham bildirim detayı (değiştirilmemiş metin, **anahtar kelimelerin metin üzerinde vurgulanması**,
türeyen kayıtlar, reprocess eylemi). İki yön de gezinilebilir.
- **Bağımlılık:** T-26, T-19, T-14 *(offset'ler)*
- **Karşılar:** FR-26, FR-08, FR-17 · **Çözer:** TC-18
- **DoD:** Kayıttan ham metne ve ham metinden kayıtlara gidilebiliyor; vurgulama offset'leri Türkçe
  karakterlerde kaymıyor; reprocess arayüzden tetiklenip sonuç aynı ekranda güncelleniyor ve
  mükerrer kayıt görünmüyor.

### ☐ T-31 · Frontend kapanışı: durumlar, erişilebilirlik, kapsam
Her veri getiren görünüm için yükleniyor/hata/boş durumları. Backend erişilemezken beyaz ekrana
düşmeme. Form etiketleri, klavye erişimi, durumun yalnızca renkle taşınmaması. Gerçek coverage
oranının ölçülmesi ve %80 eşiğinin doğrulanması.
- **Bağımlılık:** T-29, T-30
- **Karşılar:** FR-28, NFR-16, NFR-02 · **Çözer:** TC-16
- **DoD:** Backend kapalıyken arayüz anlaşılır hata ve tekrar deneme yolu gösteriyor; kapsam ≥ %80
  ve sayı snapshot testleriyle değil davranış testleriyle tutuluyor.

---

## Bağımlılık Özeti

**Backend**

```
T-01 ✔ ─┬─ T-21 ✔  (depo hazır; her task ayrı commit)
        ├─ T-02 ✔
        ├─ T-03 ✔
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

T-07 ─ T-22 ─┬─ T-16          (sahiplik düzeltmesi — ADR-021, PRD v2.0 ile geldi)
             ├─ T-18
             └─ Faz 7'nin tamamı
```

**Frontend** *(T-23 hiçbir backend task'ına bağlı değil — ilk günden paralel başlayabilir)*

```
T-23 ─ T-24 ─ T-25 ─ T-26 ─┬─ T-27 ─ T-28 ─ T-29 ─┬─ T-31 ─ T-20
                           └─ T-30 ───────────────┘

  bağlandığı backend uçları:  T-24←T-08 · T-25/T-26←T-16 · T-27/T-28←T-17
                              T-29←T-18 · T-30←T-14, T-19
  hepsinin varsaydığı sözleşme: T-22
```

**Kritik yol:** ~~T-01~~ → ~~T-04~~ → ~~T-05/T-07~~ → **T-22** → T-14 → T-16/T-17 → T-29 → T-31 → T-20
**Paralel çalışılabilir:** T-08, T-09 (ve T-09'a bağlı T-10/T-12) backend tarafında; **T-23 ve T-24
tamamen ayrı bir hat** — frontend iskeleti, kalite kapısı ve Docker'ı backend'den bağımsız ilerler.
**Öne alınması önerilen:** T-22, çünkü hem T-16/T-18'i hem de tüm frontend fazını bloke ediyor.

---

## Teknik Challenge → Task Eşlemesi

| TC | Konu | Çözüldüğü task |
|---|---|---|
| TC-1 | Kayıt granülaritesi | T-04 |
| TC-2 | Metrik veri modeli | T-04 |
| TC-3 | Sayı ↔ metrik eşleştirme | T-14 |
| TC-4 | Türkçe bileşik sayı sözcükleri | **Karara bağlandı → ADR-028** · uygulaması T-10 |
| TC-5 | Türkçe normalizasyon | **Karara bağlandı → ADR-027** · uygulaması T-09 |
| TC-6 | Tarih ayrıştırma ve göreli ifadeler | T-11 |
| TC-7 | İl tanıma | T-12 |
| TC-8 | Sınıflandırma skorlaması ve eşik | T-13 |
| TC-9 | Mükerrer gönderim | T-19 |
| TC-10 | SSE bağlantı yönetimi (sunucu) | T-18 |
| TC-11 | Anlamlı %80 kapsam (backend) | T-03 (altyapı) + her task'ın kendi testleri |
| TC-12 | Gönderim sonrası sonucun getirilmesi | **Karara bağlandı → ADR-021** · uygulaması T-22 |
| TC-13 | Canlı akışta agregasyon tazeleme | T-29 |
| TC-14 | `SHARED`/`UNKNOWN` kapsamın arayüzde temsili | T-27 |
| TC-15 | İstemci durumu ile sunucu durumunun ayrımı | T-26 |
| TC-16 | Anlamlı %80 kapsam (frontend) | T-23 (kapı) + T-31 (doğrulama) + her task'ın testleri |
| TC-17 | Frontend dağıtımı ve çalışma zamanı yapılandırması | **Karara bağlandı → ADR-025** (T-23) |
| TC-18 | Anahtar kelime vurgulamasının hizalanması | T-14 (offset üretimi) + T-30 (vurgulama) |
