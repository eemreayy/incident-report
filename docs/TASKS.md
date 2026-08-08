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
