# PRD — Olay Bildirim Sistemi (Backend)

| | |
|---|---|
| **Sürüm** | 1.0 |
| **Tarih** | 2026-08-08 |
| **Kapsam** | Yalnızca backend (`incident-report-be`) |
| **Durum** | Onay bekliyor |
| **Kaynak** | `TeknikDegerlendirmeProjesi.pdf` + tasarım notları |

---

## 1. Amaç ve Bağlam

Açık kaynaklardan (haber, rapor, sosyal medya vb.) elde edilen **serbest metin** olay bildirimleri bugün yapılandırılmamış haldedir; bu yüzden zaman içinde ve coğrafi bölge bazında karşılaştırılamaz, toplanamaz, izlenemez.

Bu sistemin amacı, kullanıcının girdiği serbest metni otomatik olarak ayrıştırıp **Tarih, İl, Olay Tipi ve sayısal metrikler**'den oluşan yapılandırılmış veriye dönüştürmek; bu veriyi tablo ve grafik olarak izlenebilir kılmak ve yeni bir bildirim girildiğinde görünümü sayfa yenilemeden güncellemektir.

Backend'in sorumluluğu: **ham metni kaybetmeden saklamak, analiz etmek, normalize veriyi sorgulanabilir kılmak ve değişiklikleri gerçek zamanlı yayınlamak.**

### Başarı ölçütü
Sistem, kaynak dokümandaki üç örnek metni uçtan uca doğru ayrıştırabiliyor; sonuçlar filtrelenebilir tablo ve olay tipi bazlı grafik olarak sunulabiliyor; yeni bildirim girildiğinde bağlı istemciler anında haberdar oluyor.

---

## 2. Kapsam

### 2.1 Dahil
- Serbest metin olay bildirimi alma, ham haliyle kalıcı saklama (log/audit).
- Metin analizi: tarih, il, olay tipi, sayısal metrik ve anahtar kelime çıkarımı.
- Normalize verinin ilişkisel veri tabanında saklanması ve ham kayda iki yönlü bağlanması.
- Tablo (liste + filtre + sayfalama), grafik (zaman serisi + kümülatif) için sorgu API'leri.
- Yeni normalize veri üretildiğinde tek yönlü gerçek zamanlı bildirim (SSE).
- Mevcut ham bildirimlerin güncel analiz kurallarıyla yeniden işlenmesi (reprocess).
- Sistemin tamamının `docker compose up` ile tek komutta ayağa kalkması.

### 2.2 Dahil değil
| Kapsam dışı | Gerekçe |
|---|---|
| Frontend (ReactJS) | Ayrı repo/session; bu PRD backend sözleşmesini tanımlar |
| Kimlik doğrulama, yetkilendirme, kullanıcı yönetimi | Kaynak dokümanda ister olarak geçmiyor; bkz. `docs/DECISIONS.md` → "İleride" |
| Harita/coğrafi görselleştirme (GIS, poligon, koordinat) | İl, kod ve isim düzeyinde tutulur; harita çizimi frontend işidir |
| Ham metin üzerinde full-text arama | Tablo ve grafik verisi yapılandırılmış katmandan (PostgreSQL) beslenir |
| Ham bildirimin güncellenmesi/silinmesi | Ham kayıt değiştirilemez log niteliğindedir (FR-02) |
| Çoklu dil metin analizi | v1 yalnızca Türkçe metin analiz eder |
| ML/NER tabanlı çıkarım | v1 kural/regex tabanlıdır; bkz. `docs/DECISIONS.md` |

---

## 3. Aktörler

| Aktör | Tanım |
|---|---|
| **Analist** | Açık kaynaktan edindiği metni sisteme giren ve sonuçları tablo/grafik olarak izleyen kişi. Sistemdeki tek insan aktördür, kimlik doğrulaması yoktur. |
| **Frontend İstemcisi** | REST API'yi tüketen ve SSE akışına abone olan web uygulaması. |

---

## 4. Terimler Sözlüğü

| Terim | Tanım |
|---|---|
| **Ham Bildirim** (Raw Incident Report) | Kullanıcının girdiği, hiç değiştirilmemiş serbest metin. MongoDB'de saklanır, değiştirilemez. |
| **Olay Kaydı** (Incident) | Ham bildirimden analiz sonucu üretilen yapılandırılmış kayıt. PostgreSQL'de saklanır. |
| **Olay Tipi** (Event Type) | Metinden sınıflandırılan olay kategorisi (salgın, deprem, trafik kazası, …). |
| **Metrik** (Metric) | Olay tipine ait sayısal ölçüm (yeni vaka, vefat, taburcu, hasarlı bina, yaralı, …). |
| **Anahtar Kelime** (Keyword) | Analiz sırasında tespit edilip olay tipi ya da metrik eşleşmesini tetikleyen kelime/ifade. |
| **Katalog** (Catalog) | Olay tiplerinin, tetikleyici anahtar kelimelerinin ve metriklerinin konfigürasyonla tanımlandığı yapı. |
| **Analiz** (Analysis) | Ham metinden Olay Kaydı üretme işlemi. |
| **Reprocess** | Mevcut bir Ham Bildirimin güncel kurallarla yeniden analiz edilmesi. |
| **İzlenebilirlik** (Traceability) | Bir Olay Kaydının hangi ham metinden üretildiğinin ve bir Ham Bildirimden hangi kayıtların türediğinin her iki yönde de bulunabilmesi. |

---

## 5. Üst Seviye Mimari

Backend, iki çekirdek modülden oluşan bir **modular monolith**'tir.

```
                      HTTP (REST)                       SSE (tek yönlü)
                           │                                   ▲
┌──────────────────────────┼───────────────────────────────────┼────────────┐
│  incident-report-be      ▼                                   │            │
│                                                                           │
│   ┌────────────────────────────┐    Spring    ┌───────────────────────┐   │
│   │  ingestion                 │  Application │  analysis             │   │
│   │  ────────────              │    Event     │  ────────             │   │
│   │  • ham metni al            │  (senkron)   │  • metni ayrıştır     │   │
│   │  • değiştirmeden sakla ────┼─────────────▶│  • sınıflandır        │   │
│   │  • oku / listele           │              │  • normalize veri üret│   │
│   │  • reprocess tetikle       │◀─────────────┤  • sorgula / agrega   │   │
│   └─────────────┬──────────────┘              └───────────┬───────────┘   │
│                 │                                         │               │
└─────────────────┼─────────────────────────────────────────┼───────────────┘
                  ▼                                         ▼
           ┌─────────────┐                          ┌──────────────┐
           │  MongoDB    │                          │  PostgreSQL  │
           │  ham metin  │◀───── iki yönlü ref ────▶│  olay kaydı  │
           │  (log)      │                          │  (analitik)  │
           └─────────────┘                          └──────────────┘
```

### Modül sorumlulukları

| Modül | Sorumluluk | Veri tabanı |
|---|---|---|
| `ingestion` | Ham metni almak, doğrulamak, değiştirmeden saklamak, okumak, reprocess tetiklemek | MongoDB (yalnız bu modül erişir) |
| `analysis` | Metni ayrıştırmak, sınıflandırmak, normalize veri üretmek, sorgu/agregasyon sunmak | PostgreSQL (yalnız bu modül erişir) |
| `realtime` (ince katman) | Normalize veri üretildiğinde bağlı istemcilere SSE ile yayınlamak | — |

### Mimari kısıtlar
- Modüller arası **doğrudan sınıf/repository erişimi yoktur**; iletişim yalnızca yayımlanan event'ler ve modülün açık (public) API tipleri üzerindendir.
- `ingestion` PostgreSQL'e, `analysis` MongoDB'ye **hiçbir koşulda** erişmez.
- Modüller arası mesajlaşma bu aşamada **senkrondur**; asenkron/dayanıklı kuyruk kullanılmaz (bkz. `docs/DECISIONS.md`).

---

## 6. Fonksiyonel İsterler

> Notasyon: her ister **FR-xx** kimliği, tek cümlelik tanım ve doğrulanabilir kabul kriteri taşır.

### FR-01 — Olay bildirimi girişi
Kullanıcı serbest metin bir olay bildirimi gönderebilir.
- **Kabul:** Boş olmayan metin gönderildiğinde sistem kaydı oluşturur ve kaydın kimliğini döner. Boş/yalnızca boşluk içeren ya da tanımlı maksimum uzunluğu aşan metin, açıklayıcı bir hata ile reddedilir.

### FR-02 — Ham metnin değiştirilmeden saklanması
Gönderilen metin, hiçbir normalizasyon uygulanmadan MongoDB'ye yazılır ve sonradan değiştirilemez.
- **Kabul:** Kaydedilen metin, gönderilen metinle bayt bayt aynıdır. Ham bildirimi güncelleyen veya silen bir API yoktur.
- **Not:** Bu kayıt log/audit niteliğindedir; analiz başarısız olsa dahi ham metin korunur.

### FR-03 — Analiz ve çıkarım
Sistem ham metinden **Tarih**, **İl**, **Olay Tipi** ve **sayısal metrikler**'i otomatik olarak çıkarır.
- **Kabul:** Kaynak dokümandaki üç örnek metin için beklenen tarih, il, olay tipi ve metrik değerleri eksiksiz üretilir (bkz. §11).

### FR-04 — Cümle sırasından ve konumdan bağımsızlık
Anahtar kelimeler ayrı ayrı cümlelerde bulunabilir; cümlelerin sırası değişebilir; tarih ve il bilgisi metnin herhangi bir cümlesinde yer alabilir.
- **Kabul:** Bir örnek metnin cümleleri karıştırıldığında analiz çıktısı değişmez.

### FR-05 — Sayıların rakam veya yazıyla ifadesi
Sayısal değerler hem rakamla (`15`, `40`) hem de Türkçe yazıyla (`on iki`, `dokuz`, `iki`) ifade edilebilir.
- **Kabul:** "on iki bina" ile "12 bina" aynı metrik değerini üretir.

### FR-06 — Tarih ifadeleri ve tarih çözümleme
Sistem kaydın tarihini üç kaynaktan çözer ve **hangi kaynaktan çözüldüğünü** kayıtla birlikte saklar:

| Tarih kaynağı | Tanım | Örnek |
|---|---|---|
| `EXPLICIT` | Metinde açık takvim tarihi var; birden fazla format desteklenir | `20.04.2020`, `3 Mayıs 2020`, `2020-04-20` |
| `RELATIVE` | Metinde göreli zaman ifadesi var; **referans tarihe** göre çözümlenir | `Son 24 saatte`, `dün`, `bugün`, `geçen hafta` |
| `DEFAULTED` | Metinde hiçbir zaman ifadesi yok; referans tarih doğrudan kullanılır | — |

**Referans tarih**, ham bildirimin **gönderim (kayıt) tarihidir**. Ham kayıt değişmez olduğu için (FR-02) bu referans sabittir; reprocess (FR-15) sırasında da orijinal gönderim tarihi kullanılır — yeniden işleme geçmiş kayıtların tarihini kaydırmaz.

- **Kabul:**
  - Desteklenen her açık format aynı takvim gününü üretir.
  - "Son 24 saatte …" içeren bir metnin tarihi gönderim tarihine çözümlenir ve tarih kaynağı `RELATIVE` olur — `DEFAULTED` **değil**.
  - Hiç zaman ifadesi olmayan metinde de kayıt üretilir; tarih kaynağı `DEFAULTED` olur.
  - Aynı bildirim reprocess edildiğinde çözülen tarih değişmez.
  - Tarih kaynağı sorgu sonuçlarında görünür; kullanıcı çıkarılmış ile varsayılmış tarihi ayırt edebilir.
- **Not:** v1'de göreli **aralık** ifadeleri ("son 24 saatte", "son 3 günde") tek bir referans güne indirgenir. Aralık semantiğinin modellenip modellenmeyeceği → **TC-6**.

### FR-07 — Tek metinde birden fazla il ve metrik seti
Bir ham bildirim birden fazla ile ve/veya birden fazla metrik setine ait bilgi taşıyabilir; sistem bu bilgiyi kaybetmeden temsil eder.
- **Kabul:** Örnek 3'te hem Bursa hem Kocaeli verisi sorgu sonuçlarında ayrı ayrı görünür; hiçbir sayısal değer çift sayılmaz.
- **Not:** Bu isterin veri modeline nasıl yansıyacağı bilinçli olarak PRD dışında bırakılmıştır → **TC-1**.

### FR-08 — Normalize verinin saklanması ve izlenebilirlik
Analizden çıkan veri PostgreSQL'e yazılır; ham MongoDB kaydı ile normalize kayıtlar **iki yönlü** ilişkilendirilebilir.
- **Kabul:** Bir olay kaydından kaynak ham metne, bir ham bildirimden ondan türeyen tüm olay kayıtlarına API üzerinden ulaşılabilir.

### FR-09 — Tanınmayan olay tipi davranışı
Kataloğa uymayan bir olay tipi geldiğinde sistem bildirimi reddetmez.
- **Kabul:** Ham metin her zaman MongoDB'ye yazılır; olay kaydı `OTHER` tipi ve `UNCLASSIFIED` durumu ile üretilir; çıkarılabilen tarih/il/sayılar korunur; API cevabında kullanıcıyı bilgilendiren bir uyarı listesi (`warnings`) döner.
- **Not:** Tasarım gerekçesi README'de açıklanacaktır (kaynak dokümanın açık talebi).

### FR-10 — Özet tablo: listeleme ve filtreleme
Kullanıcı normalize verileri sayfalanmış tablo olarak görüntüleyebilir ve filtreleyebilir.
- **Kabul:** Olay tipi, il, tarih aralığı ve anahtar kelimeye göre filtreleme yapılabilir; filtreler birlikte uygulanabilir; sonuç sayfalanır ve sıralanabilir.

### FR-11 — Olay tipine göre grafik
Kullanıcı grafiği olay tipine göre görüntüleyebilir; seçilen olay tipine ait metrikler ayrı seriler olarak sunulur.
- **Kabul:** Olay tipi (ve opsiyonel il / tarih aralığı) verildiğinde, tarihe göre gruplanmış ve metrik bazında ayrılmış zaman serisi döner.

### FR-12 — Kümülatif görünüm
Kullanıcı verileri kümülatif olarak da görebilir.
- **Kabul:** Aynı zaman serisi sorgusu kümülatif modda çağrıldığında her nokta, kendisi ve kendisinden önceki tüm noktaların toplamıdır.

### FR-13 — Gerçek zamanlı güncelleme
Yeni bir olay bildirimi girildiğinde tablo ve grafikler sayfa yenilemeden güncellenebilir.
- **Kabul:** Sunucu, yeni normalize kayıt üretildiğinde bağlı tüm SSE istemcilerine olay yayınlar. Akış **tek yönlüdür** (sunucu → istemci).

### FR-14 — Ham bildirim okuma
Kullanıcı bir ham bildirimi kimliğiyle okuyabilir ve ham bildirimleri listeleyebilir.
- **Kabul:** Tekil okuma ham metni, gönderim zamanını, analiz durumunu ve türeyen olay kayıtlarının kimliklerini döner.

### FR-15 — Yeniden işleme (reprocess)
Mevcut bir ham bildirim, güncel analiz kurallarıyla yeniden analiz edilebilir.
- **Kabul:** Reprocess sonrası ham metin değişmez; önceki normalize kayıtların yerini yeni sonuç alır ve mükerrer kayıt oluşmaz.
- **Gerekçe:** Katalog/kurallar geliştikçe geçmiş veriyi kaybetmeden yeniden değerlendirebilmek gerekir.

### FR-16 — Katalog metadata'sı
Sistem, desteklediği olay tiplerini, her tipe ait metrikleri ve tanınan il listesini API üzerinden sunar.
- **Kabul:** Frontend, filtre ve grafik seçeneklerini bu uçtan besleyebilir; katalog konfigürasyonda değiştiğinde uç otomatik olarak yeni içeriği yansıtır.

### FR-17 — Çıkarılan anahtar kelimeler
Analizde tespit edilen anahtar kelimeler olay kaydıyla birlikte saklanır, kullanıcıya gösterilir ve filtre boyutu olarak kullanılabilir.
- **Kabul:** Bir olay kaydı sorgulandığında hangi kelimelerin hangi çıkarımı tetiklediği görülebilir; anahtar kelimeye göre filtreleme FR-10 kapsamında çalışır.

---

## 7. Olay Tipi ve Metrik Kataloğu (v1)

Katalog **konfigürasyondan (YAML)** yönetilir; yeni olay tipi veya metrik eklemek kod değişikliği gerektirmez.

| Olay Tipi | Örnek tetikleyici kelimeler | Metrikler |
|---|---|---|
| `EPIDEMIC` | salgın, pandemi, vaka, virüs, test, karantina | `NEW_CASE` (yeni vaka), `DEATH` (vefat), `RECOVERED` (taburcu/iyileşen), `TEST` (yapılan test) |
| `EARTHQUAKE` | deprem, sarsıntı, artçı, enkaz, hasar | `DAMAGED_BUILDING` (hasarlı bina), `DEATH` (hayatını kaybeden), `RESCUED` (enkazdan kurtarılan), `INJURED` (yaralı) |
| `TRAFFIC_ACCIDENT` | trafik kazası, kaza, çarpışma, devrilme | `ACCIDENT_COUNT` (kaza sayısı), `DEATH`, `INJURED` |
| `FLOOD` | sel, su baskını, taşkın | `DEATH`, `INJURED`, `EVACUATED` (tahliye edilen), `AFFECTED_BUILDING` |
| `FIRE` | yangın, alev, itfaiye | `DEATH`, `INJURED`, `EVACUATED`, `AFFECTED_BUILDING` |
| `OTHER` | — | Sınıflandırılamayan bildirimler; çıkarılabilen tarih/il/sayılar yine saklanır |

İlk üç tip kaynak dokümandaki örneklerden doğrudan türetilmiştir. `FLOOD` ve `FIRE`, kataloğun kod değişmeden genişleyebildiğini göstermek için eklenmiştir.

**Not:** `DEATH` gibi metriklerin birden fazla olay tipinde ortak olması bilinçlidir; metrik tanımı olay tipinden bağımsız, eşleştirme ise olay tipine bağlıdır.

---

## 8. API Yüzeyi (üst seviye)

Alan bazlı şemalar bu belgede tanımlanmaz; tasarım/task aşamasına aittir. Tüm uçlar `/api/v1` altındadır ve OpenAPI ile dokümante edilir.

| Uç | Sorumluluk | İlgili ister |
|---|---|---|
| `POST /incident-reports` | Ham metin gönderimi; kayıt + senkron analiz; uyarılarla birlikte sonuç özeti | FR-01, FR-02, FR-03, FR-09 |
| `GET /incident-reports` | Ham bildirimleri sayfalı listeleme | FR-14 |
| `GET /incident-reports/{id}` | Tekil ham bildirim + türeyen olay kayıtlarının kimlikleri | FR-08, FR-14 |
| `POST /incident-reports/{id}/reprocess` | Güncel kurallarla yeniden analiz | FR-15 |
| `GET /incidents` | Normalize olay kayıtları; filtre (olay tipi, il, tarih aralığı, keyword) + sayfalama + sıralama | FR-10, FR-17 |
| `GET /incidents/{id}` | Tekil olay kaydı + metrikler + anahtar kelimeler + kaynak ham bildirim referansı | FR-08, FR-17 |
| `GET /analytics/time-series` | Olay tipi bazlı, metriklere ayrılmış zaman serisi; `cumulative` parametresi | FR-11, FR-12 |
| `GET /analytics/summary` | Özet tablo için agrega (olay tipi / il / metrik kırılımı) | FR-10, FR-11 |
| `GET /metadata` | Olay tipleri, metrikleri ve il listesi | FR-16 |
| `GET /stream/incidents` | SSE akışı; yeni normalize kayıt olaylarını yayınlar (tek yönlü) | FR-13 |

**Hata sözleşmesi:** Tüm hatalar RFC 7807 (`application/problem+json`) formatında döner.

---

## 9. Fonksiyonel Olmayan İsterler

| No | İster | Kabul kriteri |
|---|---|---|
| **NFR-01** | Java 21 ve Spring Boot 3.5.x kullanılır | Build Java 21 toolchain ile geçer |
| **NFR-02** | Birim test kapsamı **≥ %80** ve tüm önemli fonksiyonları kapsar | JaCoCo eşiği build'de zorunlu kılınır; eşik altında build kırılır |
| **NFR-03** | Sistem `docker compose up` ile **tek komutta** ayağa kalkar | Temiz bir makinede tek komut sonrası API ve iki veri tabanı çalışır durumdadır |
| **NFR-04** | MongoDB ve PostgreSQL **single instance** çalışır | Replica set / cluster kurulumu yoktur |
| **NFR-05** | Modüller arası bağımlılık yalnızca event ve açık API üzerindendir | Modül sınırı ihlali otomatik bir testle yakalanır |
| **NFR-06** | PostgreSQL şeması versiyonlu migration ile yönetilir | Şema, uygulama tarafından otomatik türetilmez; migration dosyaları kaynak kontrolündedir |
| **NFR-07** | API OpenAPI ile dokümante edilir | Uygulama ayaktayken makine okunur şema ve tarayıcı arayüzü erişilebilir |
| **NFR-08** | Olay tipi/metrik kataloğu konfigürasyondan yönetilir | Yeni olay tipi eklemek yalnızca konfigürasyon değişikliği gerektirir |
| **NFR-09** | Yapılandırılmış loglama; ham bildirim kimliği ile korelasyon | Bir bildirimin ingestion→analysis→SSE yolculuğu loglardan tek kimlikle izlenebilir |
| **NFR-10** | README kurulum/çalıştırma talimatlarını **ve** tasarım gerekçelerini içerir | Kaynak dokümanın açık isteri (özellikle FR-09 gerekçesi) |
| **NFR-11** | Kaynak kod GitHub üzerinden erişilebilirdir | Depo yayımlanmıştır |

### Performans ve dayanıklılık (v1 hedefleri)
- Tek bildirim analizi, tipik uzunlukta (≤ 2000 karakter) bir metin için kullanıcı isteğinin içinde tamamlanır; analiz senkron olduğu için API yanıt süresine dahildir.
- Analiz hatası ham metnin kaydedilmesini **engellemez**; ham kayıt `FAILED` işaretlenir ve reprocess ile tekrar denenebilir.
- SSE kesintisi veri kaybına yol açmaz; istemci yeniden bağlandığında normal sorgu uçlarıyla güncel duruma erişir.

---

## 10. Teknik Challenge'lar (PRD kapsamı dışı — task aşamasında çözülecek)

Bu maddeler bilinçli olarak PRD'de karara bağlanmamıştır; her biri implementasyon aşamasında ayrı bir teknik tasarım kararı gerektirir.

| No | Challenge | Neden zor |
|---|---|---|
| **TC-1** | **Kayıt granülaritesi** — bir ham metinden kaç normalize kayıt üretilecek (il × tarih × olay tipi?) | "Her iki ilde toplam 10 kişi yaralı" gibi ile atanamayan metriklerde çift sayım riski var (FR-07) |
| **TC-2** | Metrik veri modeli (normalize tablo / JSONB / geniş tablo) | Olay tipine göre metrik setleri farklı; agregasyon performansı ile genişletilebilirlik çatışıyor |
| **TC-3** | Sayı ↔ metrik eşleştirme | Cümle içi yakınlık kuralları; "Bursa'da 8, Kocaeli'nde 6 trafik kazası" gibi çoklu il-sayı bağlama |
| **TC-4** | Türkçe bileşik sayı sözcüğü ayrıştırma | "on iki", "kırk beş", "yüz yirmi" gibi çok kelimeli ifadeler |
| **TC-5** | Türkçe metin normalizasyonu | Locale bağımlı büyük/küçük harf (i/I/İ/ı); ek ve apostrof toleransı (`Ankara'da`, `Kocaeli'nde`) |
| **TC-6** | Tarih ayrıştırma ve göreli ifade çözümleme | Çoklu format; göreli **aralık** ifadelerinin ("son 24 saatte", "son 3 günde") tek güne mi indirgeneceği yoksa tarih aralığı olarak mı modelleneceği; zaman dilimi seçimi (`Europe/Istanbul` vs. UTC) ve gün sınırı; `DEFAULTED` kayıtların grafik/agregasyonlarda nasıl ele alınacağı (FR-06) |
| **TC-7** | İl tanıma | 81 il; çok kelimeli isimler (Afyonkarahisar, Kahramanmaraş); ilçe adlarının il sanılması |
| **TC-8** | Sınıflandırma skorlaması | Birden fazla tipin tetiklendiği metinlerde skor/eşik ve güven değeri (FR-09 ile bağlantılı) |
| **TC-9** | Mükerrer gönderim | Aynı metnin tekrar gönderilmesi; idempotency/dedup stratejisi |
| **TC-10** | SSE bağlantı yönetimi | Timeout, koptuğunda yeniden bağlanma, çok istemcili yayın |
| **TC-11** | Anlamlı %80 kapsam | Kapsam sayısını şişirmeden gerçek davranışı test etmek; Testcontainers ile mock dengesi |

---

## 11. Kabul Kriterleri (Definition of Done)

1. Kaynak dokümandaki **üç örnek metin** uçtan uca doğru ayrıştırılır ve otomatik "altın (golden) test" olarak sabittir:

   | Örnek | Beklenen olay tipi | Beklenen tarih | Beklenen il(ler) | Beklenen metrikler |
   |---|---|---|---|---|
   | 1 | `EPIDEMIC` | 2020-04-20 | Ankara | `NEW_CASE`=15, `DEATH`=1, `RECOVERED`=5 |
   | 2 | `EARTHQUAKE` | 2020-05-03 | İzmir | `DAMAGED_BUILDING`=12, `DEATH`=2, `RESCUED`=9, `INJURED`=40 |
   | 3 | `TRAFFIC_ACCIDENT` | Gönderim tarihi — kaynak `RELATIVE` ("Son 24 saatte") | Bursa, Kocaeli | Bursa: `ACCIDENT_COUNT`=8, `DEATH`=1 · Kocaeli: `ACCIDENT_COUNT`=6, `DEATH`=2 · `INJURED`=10 (ile atanamaz → TC-1) |

2. Örnek metinlerin cümleleri karıştırıldığında sonuç değişmez (FR-04).
3. Tanınmayan olay tipi içeren bir metin reddedilmez; `OTHER`/`UNCLASSIFIED` üretilir ve uyarı döner (FR-09).
4. Bir olay kaydından kaynak ham metne, ham metinden türeyen kayıtlara ulaşılabilir (FR-08).
5. Filtreli listeleme, olay tipi bazlı zaman serisi ve kümülatif mod çalışır (FR-10, FR-11, FR-12).
6. Yeni bildirim girildiğinde bağlı SSE istemcisi olay alır (FR-13).
7. `docker compose up` ile sistem tek komutta ayağa kalkar (NFR-03).
8. JaCoCo raporu ≥ %80 ve eşik build'de zorunludur (NFR-02).
9. README kurulum/çalıştırma talimatlarını ve tasarım gerekçelerini içerir (NFR-10).
10. `docs/DECISIONS.md` tüm mimari kararları gerekçesi ve "İleride" notlarıyla birlikte içerir.

---

## 12. İster İzlenebilirlik Matrisi

Kaynak dokümanın her maddesinin bu PRD'de karşılığı:

### Kaynak §1.3 — Fonksiyonel İsterler
| Kaynak maddesi | Karşılık |
|---|---|
| Anahtar kelimeler ayrı ayrı cümlelerde bulunabilir | FR-04 |
| Kullanıcı yeni olay bildirimi girebilecektir | FR-01 |
| Cümlelerin sırası değişebilir | FR-04 |
| Tarih ve il bilgisi herhangi bir cümlede olabilir | FR-04 |
| Sayılar rakamla veya yazıyla ifade edilebilir | FR-05 |
| Tarih farklı formatlarda yazılabilir | FR-06 |
| Tarih, İl, Olay Tipi ve Sayılar ilgili haberle birlikte veri tabanına yazılır | FR-03, FR-08 |
| Girilen veriler kullanıcıya tablo halinde gösterilir | FR-10 |
| Grafik olay tipine göre görüntülenir; seçilen tipin metrikleri gösterilir | FR-11 |
| Veriler kümülatif olarak görülebilir | FR-12 |
| Yeni bildirimde grafik ve tablolar sayfa yenilemeden güncellenir | FR-13 |

### Kaynak §1.4 — Teknik İsterler
| Kaynak maddesi | Karşılık |
|---|---|
| Backend Java (Spring Boot) ile geliştirilecektir | NFR-01 |
| Frontend ReactJS ile geliştirilecektir | Kapsam dışı (§2.2) — ayrı repo |
| Veri katmanında hem PostgreSQL hem MongoDB kullanılacaktır | §5, FR-02, FR-08 |
| Ham metin Mongo'ya kaydedilir, log niteliğindedir | FR-02 |
| Bir kaydın hangi metinden üretildiği izlenebilmelidir | FR-08 |
| Parse sonucu veriler PostgreSQL'e kaydedilir; grafik ve listeleme bu katmandan | FR-08, FR-10, FR-11 |
| İki veri tabanındaki kayıtlar ilişkilendirilebilir olmalıdır | FR-08 |
| Birim testleri, kapsam en az %80 ve önemli fonksiyonları kapsayıcı | NFR-02 |
| Proje dockerize; `docker compose up` ile tek seferde ayağa kalkar | NFR-03 |
| Kaynak kodlara GitHub üzerinden erişilebilmelidir | NFR-11 |
| ReadMe kurulum/çalıştırma talimatlarını içerecektir | NFR-10 |

### Kaynak §1.2 — Kapsam
| Kaynak maddesi | Karşılık |
|---|---|
| Olay tiplerini, anahtar kelimeleri ve metrikleri örneklerden belirleme | §7 Katalog |
| Tanınmayan olay tipinde davranış tasarım tercihidir | FR-09 |
| Seçim ve tasarım gerekçesi ReadMe'de açıklanmalıdır | NFR-10, `docs/DECISIONS.md` |

---

## 13. Açık Konular

| Konu | Durum |
|---|---|
| §10'daki TC-1…TC-11 | Task aşamasında karara bağlanacak; her karar `docs/DECISIONS.md`'e işlenecek |
| Frontend sözleşmesinin son hali | Backend API'si stabilize olduktan sonra frontend session'ında netleşecek |
| Göreli tarih aralıklarının modellenmesi ve zaman dilimi | TC-6 kapsamında. PRD, tarih kaynağını (`EXPLICIT`/`RELATIVE`/`DEFAULTED`) ve referans tarihin gönderim tarihi olduğunu sabitler (FR-06, ADR-014); aralık semantiği ve timezone kararı task aşamasına aittir |
