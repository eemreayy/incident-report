# Olay Bildirim Sistemi

Açık kaynaklardan (haber, rapor, sosyal medya vb.) elde edilen **serbest metin** olay bildirimlerini
otomatik olarak ayrıştırıp **Tarih, İl, Olay Tipi ve sayısal metrikler**'den oluşan yapılandırılmış
veriye dönüştüren; bu veriyi filtrelenebilir tablo ve olay tipi bazlı grafiklerle sunan ve yeni
bildirim girildiğinde arayüzü gerçek zamanlı güncelleyen bir web uygulaması.

Sistemin tamamı tek komutla ayağa kalkar:

```bash
docker compose up --build
```

## Repo yapısı

```
incident-report/
├── docker-compose.yml     tüm sistem (giriş noktası)
├── docs/                  PRD, tasarım kararları, task kırılımı
├── backend/               Java 21 · Spring Boot · MongoDB + PostgreSQL
└── frontend/              React · TypeScript · Vite · nginx
```

Backend kendi içinde bir **modular monolith**'tir; ayrıntısı aşağıda. Tek repo tercihinin
gerekçesi [ADR-016](docs/DECISIONS.md#adr-016--tek-repo-monorepo)'da.

---

## İçindekiler

- [Nasıl Çalışır](#nasıl-çalışır)
- [Mimari](#mimari)
- [Olay Tipi ve Metrik Kataloğu](#olay-tipi-ve-metrik-kataloğu)
- [Tasarım Tercihleri ve Gerekçeleri](#tasarım-tercihleri-ve-gerekçeleri)
- [Kurulum ve Çalıştırma](#kurulum-ve-çalıştırma)
- [API](#api)
- [Testler ve Kapsam](#testler-ve-kapsam)
- [Dokümantasyon](#dokümantasyon)

---

## Nasıl Çalışır

```
Kullanıcı arayüzdeki metin alanına serbest metin girer
        │
        ▼
1. Ham metin, hiç değiştirilmeden MongoDB'ye yazılır  (log / audit — yazıldıktan sonra hiç dokunulmaz)
        │
        ▼
2. Analiz: tarih · il · olay tipi · sayısal metrikler · anahtar kelimeler çıkarılır
        │
        ▼
3. Normalize veri **ve analiz sonucu** (durum, uyarılar) PostgreSQL'e yazılır,
   ham kayda iki yönlü bağlanır
        │
        ▼
4. Bağlı istemcilere SSE ile "yeni kayıt üretildi" **sinyali** gider
        │
        ▼
5. Arayüz normalize veriyi sorgular → tablo, özet ve grafik sayfa yenilenmeden güncellenir
```

Gönderim isteği yalnızca **kayıt makbuzu** döner (kimlik + gönderim zamanı); ne çıkarıldığını arayüz
o kimlikle sorgulayarak öğrenir. Böylece hiçbir modül sahibi olmadığı veriyi yayınlamaz ve hiçbir
veri tek bir kanala emanet edilmez — SSE koptuğunda bile her şey sorguyla erişilebilir kalır
([ADR-021](docs/DECISIONS.md#adr-021--analiz-sonucunun-sahipliği-ve-gönderim-cevabının-kapsamı)).

**Örnek girdi:**

> 20.04.2020 tarihinde Ankara'da sağlık yetkilileri tarafından yapılan açıklamada, salgın kapsamında
> yapılan testlerde 15 yeni vaka tespit edildi. 1 kişi vefat etti. 5 kişi tedavi sonrası taburcu edildi.

**Çıkarılan yapılandırılmış veri:**

| Tarih | İl | Olay Tipi | Metrikler |
|---|---|---|---|
| 2020-04-20 | Ankara | `EPIDEMIC` | `NEW_CASE`=15, `DEATH`=1, `RECOVERED`=5 |

Cümlelerin sırası önemli değildir; tarih ve il herhangi bir cümlede bulunabilir; sayılar rakamla
(`15`) veya yazıyla (`on iki`, `dokuz`) ifade edilebilir; tarih birden fazla formatta
(`20.04.2020`, `3 Mayıs 2020`) yazılabileceği gibi göreli olarak da (`Son 24 saatte`, `dün`)
ifade edilebilir — göreli ifadeler bildirimin gönderim tarihine göre çözülür ve her kayıt tarihinin
hangi kaynaktan geldiğini (`EXPLICIT` / `RELATIVE` / `DEFAULTED`) taşır.

---

## Mimari

Sistem iki dağıtım birimidir: tarayıcıda çalışan **React uygulaması** ve tek deploy edilebilir bir
**modular monolith** backend.

```
┌───────────────────────────────────────────────────────────────────────────┐
│  frontend (React · TypeScript)                                            │
│  • bildirim giriş formu   • kayıt listesi   • özet tablo   • grafik       │
└──────────────────────────┬───────────────────────────────────▲────────────┘
                      HTTP (REST)                       SSE (tek yönlü)
                           │                                   │
┌──────────────────────────┼───────────────────────────────────┼────────────┐
│  backend                 ▼                                   │            │
│                                                                           │
│   ┌────────────────────────────┐    Spring    ┌───────────────────────┐   │
│   │  ingestion                 │  Application │  analysis             │   │
│   │  • ham metni al            │    Event     │  • metni ayrıştır     │   │
│   │  • değiştirmeden sakla ────┼──(senkron,──▶│  • sınıflandır        │   │
│   │  • oku / listele           │   tek yön)   │  • normalize veri üret│   │
│   │  • reprocess tetikle       │              │  • analiz sonucunu    │   │
│   │                            │              │    sahiplen           │   │
│   │                            │              │  • sorgula / agrega   │   │
│   └─────────────┬──────────────┘              └───────────┬───────────┘   │
└─────────────────┼─────────────────────────────────────────┼───────────────┘
                  ▼                                         ▼
           ┌─────────────┐                          ┌──────────────┐
           │  MongoDB    │◀───── iki yönlü ref ────▶│  PostgreSQL  │
           │  ham metin  │                          │  olay kaydı  │
           └─────────────┘                          └──────────────┘
```

Her modül **ayrı bir Maven modülüdür** ve kendi `pom.xml`'ine sahiptir. Maven reactor'ın kökü
`backend/` dizinidir:

```
backend/             (parent, packaging=pom — ortak sürüm ve plugin yönetimi)
├── shared           hiçbir modüle bağımlı değil — modüller arası event'ler, hata sözleşmesi
├── ingestion        → shared
├── analysis         → shared
├── realtime         → shared
└── app              → hepsi — tek deploy edilebilir artifact
```

| Modül | Sorumluluk | Veri tabanı |
|---|---|---|
| `shared` | Modüller arası domain event'ler ve ortak hata sözleşmesi | — |
| `ingestion` | Ham metni almak, değiştirmeden saklamak, okumak, reprocess tetiklemek | MongoDB (yalnız bu modül) |
| `analysis` | Ayrıştırma, sınıflandırma, normalize veri üretimi, sorgu/agregasyon | PostgreSQL (yalnız bu modül) |
| `realtime` | Yeni normalize kayıt üretildiğini SSE ile bildirmek (sinyal; veri taşımaz) | — |
| `app` | Modülleri birbirine bağlayan bootstrap; `application*.yml` burada | — |
| `frontend` | Giriş formu, liste, özet, grafik, canlı akış aboneliği | — (yalnız API tüketir) |

`ingestion` ile `analysis` arasında **bilinçli olarak bağımlılık yoktur** — iletişim yalnızca
`shared`'daki domain event'ler üzerindendir. Bu bir konvansiyon değil, build garantisi:
`ingestion` içinden `analysis` sınıflarına erişmeye çalışmak `package ... does not exist` ile
derlemede kırılır. Modüle özgü kütüphaneler de o modülün pom'unda durur, yani `analysis`'in
Mongo sürücüsüne fiilen erişimi yoktur.

Sınır yalnızca derleme grafiğinde değil, **veri sahipliğinde** de var: her modül yalnızca ürettiği
veriyi yayınlar. Analiz sonucu (durum, uyarılar) `analysis`'e aittir; bu yüzden `analysis`'ten
`ingestion`'a dönüş event'i yoktur ve ham döküman yazıldıktan sonra hiç güncellenmez. Senkronluk
istemci sözleşmesinin parçası değildir — taşıma ileride bir broker'a taşınırsa API değişmez.

**Teknoloji:** Java 21 · Spring Boot 3.5.x · MongoDB · PostgreSQL · Flyway · Maven · Docker Compose
· React · TypeScript · Vite

---

## Olay Tipi ve Metrik Kataloğu

Katalog **konfigürasyondan (YAML)** yönetilir — yeni bir olay tipi eklemek kod değişikliği
gerektirmez. Dosya: [`backend/analysis/src/main/resources/incident-catalog.yml`](backend/analysis/src/main/resources/incident-catalog.yml)

| Olay Tipi | Örnek tetikleyici kelimeler | Metrikler |
|---|---|---|
| `EPIDEMIC` | salgın, pandemi, vaka, virüs, test, karantina | `NEW_CASE`, `DEATH`, `RECOVERED`, `TEST` |
| `EARTHQUAKE` | deprem, sarsıntı, artçı, enkaz, hasar | `DAMAGED_BUILDING`, `DEATH`, `RESCUED`, `INJURED` |
| `TRAFFIC_ACCIDENT` | trafik kazası, kaza, çarpışma, devrilme | `ACCIDENT_COUNT`, `DEATH`, `INJURED` |
| `FLOOD` | sel, su baskını, taşkın | `DEATH`, `INJURED`, `EVACUATED`, `AFFECTED_BUILDING` |
| `FIRE` | yangın, alev, itfaiye | `DEATH`, `INJURED`, `EVACUATED`, `AFFECTED_BUILDING` |
| `OTHER` | — | Sınıflandırılamayan bildirimler |

İlk üç tip kaynak dokümandaki örneklerden türetilmiştir. `FLOOD` ve `FIRE`, kataloğun kod değişmeden
genişleyebildiğini göstermek için eklenmiştir.

Katalog **uygulama başlangıcında doğrulanır**: anahtar biçimi ve uzunluğu, tekrar eden anahtarlar,
kelimesiz ya da metriksiz girdiler, aynı metrik anahtarının farklı etiket taşıması. Bir sorun varsa
uygulama ayağa kalkmaz ve **bulunan tüm problemleri birden** yazar — yarım tanıyan bir sistem,
tanımadığı metinleri kataloğun gerçek boşluğu gibi gösterirdi.

Arayüzdeki her seçenek `GET /api/v1/metadata` ucundan beslenir; frontend'in kendi kataloğu yoktur.
Kullanıcıya görünen etiketler de bu dosyada durur — arayüzde olsalardı yeni bir olay tipi eklemek
bir frontend sürümü de gerektirirdi.

---

## Tasarım Tercihleri ve Gerekçeleri

Aşağıda öne çıkan tercihler özetlenmiştir. Tüm kararların ayrıntılı gerekçesi, elenen alternatifleri
ve "İleride" notlarıyla birlikte [`docs/DECISIONS.md`](docs/DECISIONS.md) dosyasındadır.

### Tanınmayan olay tipi geldiğinde ne oluyor?

> Kaynak doküman bu davranışı tasarım tercihimize bırakıyor ve gerekçesini burada açıklamamızı istiyor.

**Tercih:** Bildirim **reddedilmez**. Ham metin her koşulda MongoDB'ye yazılır, olay kaydı `OTHER`
tipi ve `UNCLASSIFIED` durumuyla üretilir, çıkarılabilen tarih/il/sayılar korunur ve kullanıcıyı
bilgilendiren bir uyarı listesi olay kaydı sorgusuyla birlikte döner — arayüzde bu uyarılar
kullanıcıya gösterilir, sessizce yutulmaz.

**Neden:**

1. **Veri kaybı olmaz.** Reddetmek, sistemin henüz tanımadığı gerçek bir olayı tamamen kaybetmek
   demektir. Bilinmeyen tip çoğu zaman kullanıcının değil **kataloğun** eksikliğidir.
2. **Görünürlük sağlar.** `UNCLASSIFIED` kayıtlar sorgulanabilir olduğu için "sistem neyi
   tanıyamıyor" sorusu ölçülebilir hale gelir; katalog bu geri bildirimle büyütülür.
3. **Kısmi değer korunur.** Olay tipi bilinmese bile tarih ve il çıkarımı genellikle başarılıdır;
   bu bilgi çöpe atılmaz.
4. **Kullanıcı yanıltılmaz.** Uyarı listesi sonucun kısmi olduğunu açıkça söyler. Metni sessizce
   en yakın tipe zorlamak, analitik veriyi kirlettiği için en kötü seçenektir.
5. **Geriye dönük kazanç.** Katalog güncellendiğinde bu kayıtlar `reprocess` ile yeniden işlenip
   doğru tipe kavuşur — bugün saklıyor olmak ileride doğrudan değere dönüşür.

### Diğer başlıca tercihler

| Tercih | Kısa gerekçe | Ayrıntı |
|---|---|---|
| Modular monolith | Net domain sınırları + tek komutla ayağa kalkma; dağıtık sistem maliyeti olmadan mikroservise geçiş hattı hazır | [ADR-001](docs/DECISIONS.md#adr-001--modular-monolith) |
| Mongo = ham metin, Postgres = analitik | Şemasız/yalnız-yazılır kayıt ile agregasyon yükü farklı araçlar ister; ayrım modül sınırını fiziksel olarak da güçlendirir | [ADR-002](docs/DECISIONS.md#adr-002--iki-veri-tabanının-rol-ayrımı) |
| Senkron Spring Event | Modüller arası gevşek bağ, ek altyapı yok; senkronluk **sözleşmenin parçası değil**, implementasyon detayı | [ADR-003](docs/DECISIONS.md#adr-003--modüller-arası-senkron-spring-application-event) |
| Her modül yalnızca sahibi olduğu veriyi yayınlar | `ingestion` analiz sonucunu temsil etmez; dönüş event'i yok, ham kayıt write-once, gönderim cevabı makbuz | [ADR-021](docs/DECISIONS.md#adr-021--analiz-sonucunun-sahipliği-ve-gönderim-cevabının-kapsamı) |
| SSE (WebSocket yerine) — ve **tetikleyici** olarak | İhtiyaç tek yönlü; tarayıcıda yerleşik `EventSource`. Akış veri taşımaz, tazelemeyi tetikler: koptuğunda hiçbir veri erişilemez olmaz | [ADR-004](docs/DECISIONS.md#adr-004--gerçek-zamanlı-bildirim-için-sse) |
| Ham kayıt değiştirilemez | İzlenebilirlik ancak kaynak değişmezse anlamlı; reprocess'i güvenli kılar. Yalnız metin değil, **kaydın tamamı** write-once | [ADR-005](docs/DECISIONS.md#adr-005--ham-kaydın-değiştirilemez-olması) |
| Tarih kaynağı kayıtta saklanır | Göreli ifade (`Son 24 saatte`) bir çıkarımdır, varsayım değil; referans gönderim tarihidir ki reprocess geçmiş tarihleri kaydırmasın | [ADR-014](docs/DECISIONS.md#adr-014--tarih-çözümleme-ve-referans-tarih) |
| Katalog YAML'dan yönetilir | Yeni olay tipi = konfigürasyon değişikliği; veri ile algoritma ayrışır | [ADR-007](docs/DECISIONS.md#adr-007--konfigürasyondan-yönetilen-olay-kataloğu) |
| Kural/regex tabanlı çıkarım (ML yerine) | Deterministik, açıklanabilir, test edilebilir; ağır bağımlılık yok | [ADR-008](docs/DECISIONS.md#adr-008--kuralregex-tabanlı-çıkarım-ml-yerine) |
| Auth kapsam dışı | Kaynak dokümanda ister değil; efor asıl teknik zorluk olan metin analizine ayrıldı | [ADR-011](docs/DECISIONS.md#adr-011--kimlik-doğrulamanın-kapsam-dışı-bırakılması) |
| Frontend: React + TypeScript + Vite | ReactJS isteri; TypeScript, backend'deki "ihlal derlemede patlasın" çizgisinin istemci karşılığı; SSR'ın karşılığı yok | [ADR-022](docs/DECISIONS.md#adr-022--frontend-teknoloji-tabanı-react--typescript--vite) |
| İl, harita yerine grafik kırılımı | Kaynak "grafiksel" diyor, "haritasal" demiyor; `SHARED` sayılar haritada tanımsız — boyanamaz, bölüştürülemez | [ADR-023](docs/DECISIONS.md#adr-023--coğrafi-izlenebilirlik-harita-yerine-il-kırılımı) |
| Frontend'de de %80 coverage kapısı | Kaynak dokümandaki ister backend'e daraltılmamış; iki farklı standart, düşük olanın standart olması demek | [ADR-024](docs/DECISIONS.md#adr-024--frontend-coverage-kapısı) |

---

## Kurulum ve Çalıştırma

### Gereksinimler

Tek komutla çalıştırmak için **yalnızca Docker** yeterlidir:

| Araç | Sürüm | Not |
|---|---|---|
| Docker Engine | 24+ | BuildKit varsayılan olarak açık olmalı |
| Docker Compose | v2 | `docker compose` (tire yok) |

Kaynak koddan derlemek isterseniz ek olarak **JDK 21** gerekir. Maven'a gerek yok — depo kendi
wrapper'ını (`./mvnw`) taşır.

### Tek komutla çalıştırma

Repo kökünde:

```bash
docker compose up --build
```

Başka hiçbir hazırlık gerekmez; `.env` dosyası oluşturmanız da gerekmez (aşağıya bakın).
Komut tüm servisleri ayağa kaldırır ve uygulama, veri tabanları **sağlıklı** olana kadar bekler:

| Servis | Adres | Açıklama |
|---|---|---|
| `frontend` | **http://localhost:3000** | React arayüzü — sistemin giriş noktası; API'yi aynı köken üzerinden proxy'ler |
| `backend` | http://localhost:8080 | Backend API |
| `postgres` | `localhost:5432` | PostgreSQL 17 — normalize/analitik veri |
| `mongodb` | `localhost:27017` | MongoDB 8 — ham metin (log) |

Kullanıcı olarak açmanız gereken tek adres **http://localhost:3000**. API'ye giden istekler aynı
köken üzerinden geçtiği için tarayıcının backend portunu bilmesine gerek yoktur
([ADR-025](docs/DECISIONS.md#adr-025--aynı-köken-nginx-reverse-proxy-cors-yerine)); 8080 yine de
curl/Postman ve `local` profili için yayımlanmış durumda.

Sağlık kontrolü:

```bash
curl -s localhost:8080/actuator/health
```

`{"status":"UP",...}` dönmelidir. Servislerin durumunu topluca görmek için:

```bash
docker compose ps
```

### Yapılandırma

Tüm ayarların `docker-compose.yml` içinde gömülü varsayılanları vardır; bu yüzden taze bir klon
hiçbir ek dosya olmadan çalışır. Bir değeri değiştirmek isterseniz:

```bash
cp .env.example .env
```

Değiştirilebilir değişkenler: `APP_PORT`, `POSTGRES_DB/USER/PASSWORD/PORT`,
`MONGO_DB/USER/PASSWORD/PORT`. `.env` git tarafından yok sayılır; gerçek kimlik bilgileri
asla commit edilmemelidir.

### Yerelde geliştirme

Uygulamayı IDE'den veya Maven'dan çalıştırıp yalnızca veri tabanlarını Docker'da tutmak için:

```bash
docker compose up -d postgres mongodb
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
cd backend && ./mvnw -pl app spring-boot:run
```

Bu akışta `local` profili devreye girer (varsayılan) ve uygulama `localhost` üzerindeki
veri tabanlarına bağlanır.

Maven reactor'ın kökü `backend/` dizinidir; derleme ve testler oradan çalıştırılır:

```bash
cd backend
./mvnw verify                  # tüm modüller: derleme + testler
./mvnw -pl analysis -am verify # tek modül ve bağımlılıkları
```

Yalnızca backend'i ve veri tabanlarını ayağa kaldırmak isterseniz `backend/` dizininin kendi
compose dosyası vardır:

```bash
cd backend && docker compose up --build
```

Kökteki compose bu dosyayı `include` ile olduğu gibi kullanır, yani servis tanımları
tek yerdedir ve iki dosya arasında kopyalama yoktur.

### Durdurma ve temizlik

```bash
docker compose down       # container'ları durdurur, veriyi korur
docker compose down -v    # veri hacimlerini de siler (sıfırdan başlamak için)
```

---

## API

Sistem ayaktayken API dokümantasyonu şu adreslerde:

| Adres | İçerik |
|---|---|
| **http://localhost:8080/swagger-ui** | Tarayıcıda gezilebilir arayüz; istekler buradan denenebilir |
| http://localhost:8080/v3/api-docs | OpenAPI 3 dokümanı (JSON) |

Doküman **controller'lardan üretilir**, elle yazılmaz: yollar, parametreler ve şemalar kodun
kendisinden gelir, dolayısıyla koddan sapamaz. Elle yazılan tek şey kapsayıcı açıklama metni.

Uçları frontend olmadan denemek için hazır bir Postman koleksiyonu da var — aşağıya bakın.

Uçlar `/api/v1` altındadır:

| Uç | Açıklama |
|---|---|
| `POST /incident-reports` | Ham metin gönderimi; **kayıt makbuzu** döner (kimlik + gönderim zamanı) |
| `GET /incident-reports` | Ham bildirimleri sayfalı listeleme |
| `GET /incident-reports/{id}` | Tekil ham bildirim (metin + gönderim zamanı) |
| `POST /incident-reports/{id}/reprocess` | Güncel kurallarla yeniden analiz |
| `GET /incidents` | Normalize kayıtlar + analiz durumu ve uyarılar; olay tipi / il / tarih aralığı / keyword / **`rawReportId`** filtreleri + sayfalama |
| `GET /incidents/{id}` | Tekil olay kaydı + metrikler + anahtar kelimeler + kaynak referansı |
| `GET /analytics/time-series` | Olay tipi bazlı zaman serisi; `cumulative` parametresi ile kümülatif |
| `GET /analytics/summary` | Özet tablo agregasyonu |
| `GET /metadata` | Desteklenen olay tipleri, metrikleri ve il listesi |
| `GET /stream/incidents` | SSE akışı (tek yönlü); yeni kayıt **sinyali** — veri taşımaz |

Hatalar RFC 7807 (`application/problem+json`) formatında döner.

API'yi frontend olmadan denemek için [`docs/postman/`](docs/postman/) altında hazır bir Postman
koleksiyonu var: 18 istek, **çalışan sistemden yakalanmış** örnek cevaplar ve `npx newman run`
ile çalıştırılabilen 76 assertion.

---

## Testler ve Kapsam

### Çalıştırma

Backend testleri **çalışan bir Docker daemon'ı ister** — veri tabanına dokunan testler
Testcontainers ile gerçek MongoDB ve PostgreSQL başlatır.

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)   # JDK 21
cd backend
./mvnw verify                    # tüm modüller: derleme + testler + kapsam kapısı
./mvnw -pl analysis -am verify   # tek modül ve bağımlılıkları
```

Frontend için `frontend/` altında:

```bash
npm run verify                   # lint + tip kontrolü + build + kapsam kapısı
npm test
```

### Kapsam raporu

`verify` sonrası, tarayıcıda açılabilir HTML raporlar:

| Rapor | Konum |
|---|---|
| Modül bazlı | `backend/<modül>/target/site/jacoco/index.html` |
| Proje geneli (birleşik) | `backend/app/target/site/jacoco-aggregate/index.html` |
| Frontend | `frontend/coverage/index.html` |

### Ölçülen kapsam

Eşik **%80** ve **modül başına** uygulanıyor — proje geneli tek bir ortalama olsaydı, iyi test
edilmiş bir modül test edilmemiş bir modülü gizleyebilirdi ([ADR-018](docs/DECISIONS.md#adr-018)).

| Modül | Satır kapsamı |
|---|---|
| `shared` | %100 |
| `ingestion` | %98 |
| `analysis` | %99 |
| `app` | %100 |
| `realtime` | %96 |

Şu anki durum: **501 test, proje geneli %99 satır kapsamı.** Kapı bir taban, hedef değil: sayıyı
şişiren değil, gerçek davranışı ölçen testler yazılıyor.

- Birim test kapsamı **en az %80**; eşik build'de zorunludur ve altına düşüldüğünde build kırılır.
  Aynı kapı **backend ve frontend için ayrı ayrı** geçerlidir (ADR-018, ADR-024).
- Kaynak dokümandaki üç örnek metin **altın (golden) test** olarak sabittir; cümleleri karıştırılmış
  halleriyle de doğrulanır.
- Veri tabanına dokunan testler Testcontainers ile gerçek MongoDB ve PostgreSQL üzerinde çalışır.
- Modül sınırı ihlallerini yakalayan otomatik bir doğrulama testi bulunur.

---

## Dokümantasyon

| Dosya | İçerik |
|---|---|
| [`docs/PRD.md`](docs/PRD.md) | Ürün gereksinim dokümanı: kapsam, fonksiyonel/fonksiyonel olmayan isterler, kabul kriterleri, ister izlenebilirlik matrisi, açık teknik challenge'lar |
| [`docs/postman/`](docs/postman/) | Postman koleksiyonu — API'yi frontend olmadan denemek için; örnek cevaplar çalışan sistemden yakalanmıştır |
| [`docs/DECISIONS.md`](docs/DECISIONS.md) | Tüm mimari kararlar; gerekçeleri, elenen alternatifleri, trade-off'ları ve **"İleride"** notları |
| [`CLAUDE.md`](CLAUDE.md) | Projenin çalışma sözleşmesi: mimari kısıtlar, kodlama ve test kuralları, Türkçe metin işleme tuzakları |
