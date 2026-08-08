# Olay Bildirim Sistemi — Backend

Açık kaynaklardan (haber, rapor, sosyal medya vb.) elde edilen **serbest metin** olay bildirimlerini
otomatik olarak ayrıştırıp **Tarih, İl, Olay Tipi ve sayısal metrikler**'den oluşan yapılandırılmış
veriye dönüştüren; bu veriyi filtrelenebilir tablo ve olay tipi bazlı grafiklerle sunan ve yeni
bildirim girildiğinde bağlı istemcileri gerçek zamanlı bilgilendiren backend servisi.

> Bu depo projenin **backend** bacağıdır. Frontend (ReactJS) ayrı bir depoda geliştirilmektedir.

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
Kullanıcı serbest metin girer
        │
        ▼
1. Ham metin, hiç değiştirilmeden MongoDB'ye yazılır  (log / audit — silinemez, güncellenemez)
        │
        ▼
2. Analiz: tarih · il · olay tipi · sayısal metrikler · anahtar kelimeler çıkarılır
        │
        ▼
3. Normalize veri PostgreSQL'e yazılır ve ham kayda iki yönlü bağlanır
        │
        ▼
4. Yeni kayıt SSE ile bağlı istemcilere yayınlanır → tablo ve grafik sayfa yenilenmeden güncellenir
```

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

Backend, tek deploy edilebilir bir **modular monolith**'tir.

```
                      HTTP (REST)                       SSE (tek yönlü)
                           │                                   ▲
┌──────────────────────────┼───────────────────────────────────┼────────────┐
│  incident-report-be      ▼                                   │            │
│                                                                           │
│   ┌────────────────────────────┐    Spring    ┌───────────────────────┐   │
│   │  ingestion                 │  Application │  analysis             │   │
│   │  • ham metni al            │    Event     │  • metni ayrıştır     │   │
│   │  • değiştirmeden sakla ────┼─────────────▶│  • sınıflandır        │   │
│   │  • oku / listele           │  (senkron)   │  • normalize veri üret│   │
│   │  • reprocess tetikle       │◀─────────────┤  • sorgula / agrega   │   │
│   └─────────────┬──────────────┘              └───────────┬───────────┘   │
└─────────────────┼─────────────────────────────────────────┼───────────────┘
                  ▼                                         ▼
           ┌─────────────┐                          ┌──────────────┐
           │  MongoDB    │◀───── iki yönlü ref ────▶│  PostgreSQL  │
           │  ham metin  │                          │  olay kaydı  │
           └─────────────┘                          └──────────────┘
```

Her modül **ayrı bir Maven modülüdür** ve kendi `pom.xml`'ine sahiptir:

```
incident-report-be   (parent, packaging=pom — ortak sürüm ve plugin yönetimi)
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
| `realtime` | Yeni normalize kaydı SSE ile yayınlamak | — |
| `app` | Modülleri birbirine bağlayan bootstrap; `application*.yml` burada | — |

`ingestion` ile `analysis` arasında **bilinçli olarak bağımlılık yoktur** — iletişim yalnızca
`shared`'daki domain event'ler üzerindendir. Bu bir konvansiyon değil, build garantisi:
`ingestion` içinden `analysis` sınıflarına erişmeye çalışmak `package ... does not exist` ile
derlemede kırılır. Modüle özgü kütüphaneler de o modülün pom'unda durur, yani `analysis`'in
Mongo sürücüsüne fiilen erişimi yoktur.

**Teknoloji:** Java 21 · Spring Boot 3.5.x · MongoDB · PostgreSQL · Flyway · Maven · Docker Compose

---

## Olay Tipi ve Metrik Kataloğu

Katalog **konfigürasyondan (YAML)** yönetilir — yeni bir olay tipi eklemek kod değişikliği
gerektirmez.

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

---

## Tasarım Tercihleri ve Gerekçeleri

Aşağıda öne çıkan tercihler özetlenmiştir. Tüm kararların ayrıntılı gerekçesi, elenen alternatifleri
ve "İleride" notlarıyla birlikte [`docs/DECISIONS.md`](docs/DECISIONS.md) dosyasındadır.

### Tanınmayan olay tipi geldiğinde ne oluyor?

> Kaynak doküman bu davranışı tasarım tercihimize bırakıyor ve gerekçesini burada açıklamamızı istiyor.

**Tercih:** Bildirim **reddedilmez**. Ham metin her koşulda MongoDB'ye yazılır, olay kaydı `OTHER`
tipi ve `UNCLASSIFIED` durumuyla üretilir, çıkarılabilen tarih/il/sayılar korunur ve API cevabında
kullanıcıyı bilgilendiren bir uyarı listesi döner.

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
| Senkron Spring Event | Modüller arası gevşek bağ + kullanıcının sonucu ve uyarıları anında görmesi; ek altyapı yok | [ADR-003](docs/DECISIONS.md#adr-003--modüller-arası-senkron-spring-application-event) |
| SSE (WebSocket yerine) | İhtiyaç tek yönlü; tarayıcıda yerleşik `EventSource`, otomatik yeniden bağlanma, düşük altyapı sürtünmesi | [ADR-004](docs/DECISIONS.md#adr-004--gerçek-zamanlı-bildirim-için-sse) |
| Ham kayıt değiştirilemez | İzlenebilirlik ancak kaynak değişmezse anlamlı; reprocess'i güvenli kılar | [ADR-005](docs/DECISIONS.md#adr-005--ham-kaydın-değiştirilemez-olması) |
| Tarih kaynağı kayıtta saklanır | Göreli ifade (`Son 24 saatte`) bir çıkarımdır, varsayım değil; referans gönderim tarihidir ki reprocess geçmiş tarihleri kaydırmasın | [ADR-014](docs/DECISIONS.md#adr-014--tarih-çözümleme-ve-referans-tarih) |
| Katalog YAML'dan yönetilir | Yeni olay tipi = konfigürasyon değişikliği; veri ile algoritma ayrışır | [ADR-007](docs/DECISIONS.md#adr-007--konfigürasyondan-yönetilen-olay-kataloğu) |
| Kural/regex tabanlı çıkarım (ML yerine) | Deterministik, açıklanabilir, test edilebilir; ağır bağımlılık yok | [ADR-008](docs/DECISIONS.md#adr-008--kuralregex-tabanlı-çıkarım-ml-yerine) |
| Auth kapsam dışı | Kaynak dokümanda ister değil; efor asıl teknik zorluk olan metin analizine ayrıldı | [ADR-011](docs/DECISIONS.md#adr-011--kimlik-doğrulamanın-kapsam-dışı-bırakılması) |

---

## Kurulum ve Çalıştırma

<!-- TODO: Docker ve Maven yapılandırması tamamlandığında doldurulacak. -->

### Gereksinimler
<!-- TODO: Docker / Docker Compose sürümleri; kaynak koddan derleme için JDK 21 -->

### Tek komutla çalıştırma
<!-- TODO: docker compose up --build; ayağa kalkan servisler ve portları; sağlık kontrolü -->

### Yapılandırma
<!-- TODO: ortam değişkenleri, .env örneği, veri tabanı bağlantı ayarları -->

### Yerelde geliştirme
<!-- TODO: yalnızca veri tabanlarını compose ile ayağa kaldırıp uygulamayı IDE'den çalıştırma -->

### Durdurma ve temizlik
<!-- TODO: docker compose down -v -->

---

## API

<!-- TODO: OpenAPI arayüz adresi ve örnek istek/cevaplar eklenecek. -->

Uçlar `/api/v1` altındadır:

| Uç | Açıklama |
|---|---|
| `POST /incident-reports` | Ham metin gönderimi; kayıt + analiz + uyarılarla birlikte sonuç özeti |
| `GET /incident-reports` | Ham bildirimleri sayfalı listeleme |
| `GET /incident-reports/{id}` | Tekil ham bildirim + türeyen olay kayıtları |
| `POST /incident-reports/{id}/reprocess` | Güncel kurallarla yeniden analiz |
| `GET /incidents` | Normalize kayıtlar; olay tipi / il / tarih aralığı / keyword filtreleri + sayfalama |
| `GET /incidents/{id}` | Tekil olay kaydı + metrikler + anahtar kelimeler + kaynak referansı |
| `GET /analytics/time-series` | Olay tipi bazlı zaman serisi; `cumulative` parametresi ile kümülatif |
| `GET /analytics/summary` | Özet tablo agregasyonu |
| `GET /metadata` | Desteklenen olay tipleri, metrikleri ve il listesi |
| `GET /stream/incidents` | SSE akışı (tek yönlü) |

Hatalar RFC 7807 (`application/problem+json`) formatında döner.

---

## Testler ve Kapsam

<!-- TODO: test çalıştırma komutları ve kapsam raporunun konumu eklenecek. -->

- Birim test kapsamı **en az %80**; eşik build'de zorunludur ve altına düşüldüğünde build kırılır.
- Kaynak dokümandaki üç örnek metin **altın (golden) test** olarak sabittir; cümleleri karıştırılmış
  halleriyle de doğrulanır.
- Veri tabanına dokunan testler Testcontainers ile gerçek MongoDB ve PostgreSQL üzerinde çalışır.
- Modül sınırı ihlallerini yakalayan otomatik bir doğrulama testi bulunur.

---

## Dokümantasyon

| Dosya | İçerik |
|---|---|
| [`docs/PRD.md`](docs/PRD.md) | Ürün gereksinim dokümanı: kapsam, fonksiyonel/fonksiyonel olmayan isterler, kabul kriterleri, ister izlenebilirlik matrisi, açık teknik challenge'lar |
| [`docs/DECISIONS.md`](docs/DECISIONS.md) | Tüm mimari kararlar; gerekçeleri, elenen alternatifleri, trade-off'ları ve **"İleride"** notları |
| [`CLAUDE.md`](CLAUDE.md) | Projenin çalışma sözleşmesi: mimari kısıtlar, kodlama ve test kuralları, Türkçe metin işleme tuzakları |
