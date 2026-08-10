# PRD — Olay Bildirim Sistemi

| | |
|---|---|
| **Sürüm** | 2.0 |
| **Tarih** | 2026-08-09 |
| **Kapsam** | Tüm sistem — `backend/` (Java 21 · Spring Boot) **ve** `frontend/` (ReactJS) |
| **Durum** | Onay bekliyor |
| **Kaynak** | `TeknikDegerlendirmeProjesi.pdf` + tasarım notları |

> **v2.0 neyi değiştirdi.** v1.0 yalnızca backend'i kapsıyor ve frontend'i açıkça kapsam dışı
> bırakıyordu. Kaynak dokümanın amaç cümlesi ise bir **web uygulaması** tarif ediyor — frontend
> teslimatın parçası, opsiyonel bir ek değil. Bu sürüm frontend'i kapsama alır (FR-18…FR-28,
> NFR-12…NFR-16, TC-12…TC-18) ve mevcut backend isterlerinin (FR-01…FR-17) **hiçbirini
> değiştirmez**; yalnızca bazılarına frontend'in beklediği sözleşme ayrıntısını ekler. Bölüm
> numaraları korunmuştur, çünkü `CLAUDE.md` ve `docs/TASKS.md` §10 ve §11'e referans verir.
> Farkların tam listesi §14'tedir.

---

## 1. Amaç ve Bağlam

Açık kaynaklardan (haber, rapor, sosyal medya vb.) elde edilen **serbest metin** olay bildirimleri bugün yapılandırılmamış haldedir; bu yüzden zaman içinde ve coğrafi bölge bazında karşılaştırılamaz, toplanamaz, izlenemez.

Bu sistemin amacı, kullanıcının bir metin alanına girdiği serbest metni otomatik olarak ayrıştırıp **Tarih, İl, Olay Tipi ve sayısal metrikler**'den oluşan yapılandırılmış veriye dönüştürmek; bu veriyi **zaman içinde ve il bazında** tablo ve grafik olarak izlenebilir kılmak ve yeni bir bildirim girildiğinde grafik ile özet tabloları **sayfa yenilemeden anlık** güncellemektir.

Teslim edilen şey bir **web uygulamasıdır**: kullanıcı sistemle yalnızca tarayıcı üzerinden etkileşir. Sorumluluk ayrımı:

| Katman | Sorumluluk |
|---|---|
| **Backend** | Ham metni kaybetmeden saklamak, analiz etmek, normalize veriyi sorgulanabilir kılmak, değişiklikleri gerçek zamanlı yayınlamak. |
| **Frontend** | Metni girmek için tek bir alan sunmak, sonucu ve uyarıları anında göstermek, normalize veriyi filtrelenebilir tablo/özet/grafik olarak sunmak, canlı akışa abone olup görünümü sayfa yenilemeden tazelemek. |

### Başarı ölçütü
Kullanıcı tarayıcıda bir metin alanına kaynak dokümandaki üç örnek metni girdiğinde; metin ham haliyle saklanıyor, doğru ayrıştırılıyor, sonuç aynı ekranda anında görünüyor; veriler filtrelenebilir tablo, il kırılımlı özet ve olay tipi bazlı (opsiyonel kümülatif) grafik olarak izlenebiliyor; başka bir tarayıcı sekmesi açıkken girilen bildirim o sekmede de **sayfa yenilenmeden** belirmektedir.

---

## 2. Kapsam

### 2.1 Dahil

**Backend**
- Serbest metin olay bildirimi alma, ham haliyle kalıcı saklama (log/audit).
- Metin analizi: tarih, il, olay tipi, sayısal metrik ve anahtar kelime çıkarımı.
- Normalize verinin ilişkisel veri tabanında saklanması ve ham kayda iki yönlü bağlanması.
- Tablo (liste + filtre + sayfalama), grafik (zaman serisi + kümülatif) için sorgu API'leri.
- Yeni normalize veri üretildiğinde tek yönlü gerçek zamanlı bildirim (SSE).
- Mevcut ham bildirimlerin güncel analiz kurallarıyla yeniden işlenmesi (reprocess).

**Frontend**
- Serbest metin girişi için tek ekranlı bildirim formu; sonucun ve uyarıların anında gösterimi.
- Normalize kayıtların filtrelenebilir, sayfalanabilir tablosu.
- Olay tipi / il / metrik kırılımlı **özet tablo**.
- Olay tipi seçimine bağlı, metrik serilerine ayrılmış zaman serisi grafiği; **il kırılımı** ve **kümülatif** mod.
- SSE aboneliği ile grafik, özet ve tablonun sayfa yenilenmeden tazelenmesi; bağlantı durumunun görünür olması.
- Ham bildirim detayı: ham metin, çıkarılan anahtar kelimelerin metin üzerinde vurgulanması, türeyen kayıtlar, reprocess tetikleme.
- Filtre ve grafik seçeneklerinin katalog metadata ucundan beslenmesi (hiçbir olay tipi/metrik/il listesi arayüzde sabit yazılmaz).

**Sistem**
- Backend, frontend ve iki veri tabanının **tamamının** `docker compose up` ile tek komutta ayağa kalkması.

### 2.2 Dahil değil
| Kapsam dışı | Gerekçe |
|---|---|
| Kimlik doğrulama, yetkilendirme, kullanıcı yönetimi | Kaynak dokümanda ister olarak geçmiyor; bkz. `docs/DECISIONS.md` → "İleride" |
| Harita üzerinde görselleştirme (choropleth, GIS, poligon, koordinat) | Kaynak doküman "coğrafi bölge bazında **grafiksel**" diyor, haritasal demiyor. İster, ilin grafikte ve özet tabloda bir **kırılım boyutu** olmasıyla karşılanır (FR-24). Harita ayrıca `SHARED` kapsamlı kayıtlar için tanımsızdır: hiçbir tek ile boyanamaz, bölüştürülmesi ise veri uydurmak olur (ADR-019). Gerekçe ve ileri yol bir ADR'ye yazılır |
| Ham metin üzerinde full-text arama | Tablo ve grafik verisi yapılandırılmış katmandan (PostgreSQL) beslenir |
| Ham bildirimin güncellenmesi/silinmesi | Ham kayıt değiştirilemez log niteliğindedir (FR-02) |
| Çoklu dil metin analizi ve arayüz yerelleştirmesi (i18n) | v1 yalnızca Türkçe metin analiz eder; arayüz metinleri de Türkçedir |
| ML/NER tabanlı çıkarım | v1 kural/regex tabanlıdır; bkz. `docs/DECISIONS.md` |
| Sunucu tarafı render (SSR), çevrimdışı çalışma, mobil uygulama | Tek kullanıcılı analist arayüzü; SSR'ın getireceği karmaşıklığın karşılığı yok (NFR-12) |

---

## 3. Aktörler

| Aktör | Tanım |
|---|---|
| **Analist** | Açık kaynaktan edindiği metni tarayıcıdaki metin alanına giren ve sonuçları tablo/özet/grafik olarak izleyen kişi. Sistemdeki tek insan aktördür, kimlik doğrulaması yoktur. |
| **Frontend İstemcisi** | Analistin tarayıcısında çalışan, REST API'yi tüketen ve SSE akışına abone olan ReactJS uygulaması. Aynı anda **birden fazla istemci** bağlı olabilir; biri bildirim girdiğinde diğerleri güncellemeyi akıştan alır (FR-25). |

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
| **Kayıt Listesi** | Normalize Olay Kayıtlarının satır satır, filtrelenebilir ve sayfalanabilir tablosu. Kaynak dokümandaki "tablo halinde gösterme" isterinin karşılığı. |
| **Özet Tablo** | Aynı verinin olay tipi / il / metrik kırılımında toplanmış hali. Kayıt Listesi'nden farklıdır: satırları kayıt değil, **agrega**dır. Kaynak doküman ikisini de ("tablolar", "özet tablolar") ister. |
| **Kapsam** (Scope) | Bir Olay Kaydının il ilişkisinin türü: `SINGLE` (tek il), `SHARED` (birden fazla ile ait, ayrıştırılamayan toplam), `UNKNOWN` (metinde il yok). Bkz. ADR-019. |
| **Canlı Akış** | Frontend'in SSE bağlantısı üzerinden aldığı, yeni normalize kayıt olaylarından oluşan tek yönlü akış. |
| **Katalog Metadata Ucu** | Olay tiplerini, metriklerini ve il listesini dönen API ucu (FR-16). Frontend'deki tüm seçim kutularının **tek** kaynağıdır. |

---

## 5. Üst Seviye Mimari

Sistem iki dağıtım birimidir: tarayıcıda çalışan **ReactJS uygulaması** ve iki çekirdek modülden oluşan bir **modular monolith** backend.

```
┌───────────────────────────────────────────────────────────────────────────┐
│  frontend  (ReactJS · tarayıcı)                                           │
│  ──────────────────────────────                                           │
│  • bildirim giriş formu (tek metin alanı)   • özet tablo (il × metrik)    │
│  • filtrelenebilir kayıt listesi            • zaman serisi grafiği        │
│  • ham bildirim detayı / anahtar kelime vurgusu   • canlı akış aboneliği  │
└──────────────────────────┬───────────────────────────────────▲────────────┘
                      HTTP (REST)                       SSE (tek yönlü)
                           │                                   │
┌──────────────────────────┼───────────────────────────────────┼────────────┐
│  backend                 ▼                                   │            │
│                                                                           │
│   ┌────────────────────────────┐    Spring    ┌───────────────────────┐   │
│   │  ingestion                 │  Application │  analysis             │   │
│   │  ────────────              │    Event     │  ────────             │   │
│   │  • ham metni al            │  (senkron,   │  • metni ayrıştır     │   │
│   │  • değiştirmeden sakla ────┼──tek yön)───▶│  • sınıflandır        │   │
│   │  • oku / listele           │              │  • normalize veri üret│   │
│   │  • reprocess tetikle       │              │  • analiz sonucunu    │   │
│   │                            │              │    sahiplen           │   │
│   │                            │              │  • sorgula / agrega   │   │
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
| `frontend` | Metin girişi, listeleme/filtreleme, özet, grafik, canlı akış aboneliği | — (yalnız API tüketir) |

### Mimari kısıtlar
- Modüller arası **doğrudan sınıf/repository erişimi yoktur**; iletişim yalnızca yayımlanan event'ler ve modülün açık (public) API tipleri üzerindendir.
- `ingestion` PostgreSQL'e, `analysis` MongoDB'ye **hiçbir koşulda** erişmez.
- Modüller arası mesajlaşma bu aşamada **senkrondur**; asenkron/dayanıklı kuyruk kullanılmaz (bkz. `docs/DECISIONS.md`).
- **Frontend'in veri tabanlarına erişimi yoktur**; tek temas noktası `/api/v1` altındaki uçlar ve SSE akışıdır. İş kuralı frontend'de kopyalanmaz: kümülatif toplama, agregasyon ve filtreleme sunucuda yapılır (bkz. §5.4).
- **Her modül yalnızca ürettiği veriyi yayınlar (v2.0).** `ingestion` ham metni; `analysis` normalize kayıtları **ve analiz sonucunu** (durum, uyarılar) sahiplenir. Hiçbir modül, sahibi olmadığı veriyi kendi cevabında temsil etmez — bu yüzden `analysis`'ten `ingestion`'a **dönüş event'i yoktur**, olay akışı tek yönlüdür ve ham döküman yazıldıktan sonra güncellenmez.
- **Sonucu:** Senkronluk sözleşmenin değil, implementasyonun özelliğidir. Modüller arası taşıma ileride bir broker'a (ör. RabbitMQ) taşınırsa **hiçbir istemci sözleşmesi değişmez**.

### 5.4 Frontend uygulama mimarisi ve ekranlar

**Katmanlar.** Arayüz üç katmanda ayrışır ve bu ayrım test edilebilirliğin taşıyıcısıdır (NFR-02):

| Katman | Sorumluluk | Nasıl test edilir |
|---|---|---|
| **API istemcisi** | Uç çağrıları, hata sözleşmesinin (RFC 7807) tipli çözümlenmesi, SSE bağlantı yaşam döngüsü | Ağ katmanı taklit edilerek; Spring context gerekmez |
| **Durum / veri katmanı** | Sunucu verisinin önbelleği, filtre durumu, canlı akış olaylarının önbelleğe uygulanması | Saf fonksiyon ve hook testleri — DOM'suz |
| **Görünüm** | Form, tablo, özet, grafik, detay | Davranış testi (kullanıcı etkileşimi → beklenen çıktı), snapshot değil |

**Kural.** Türetilmiş sayı üretmek görünüm katmanının işi değildir. Kümülatif seri, il kırılımı ve
agregalar **sunucudan** gelir (FR-11, FR-12, FR-22…FR-24); aksi halde aynı iş kuralı iki dilde iki
kez yazılır ve ikisi birbirinden kayar. Frontend yalnızca gelen sayıyı çizer.

**Ekranlar.**

| Kod | Ekran | İçerik | İlgili isterler |
|---|---|---|---|
| **S-1** | Panel (ana ekran) | Bildirim giriş formu · filtre çubuğu · zaman serisi grafiği · özet tablo · kayıt listesi · canlı akış göstergesi | FR-18…FR-25, FR-27, FR-28 |
| **S-2** | Olay kaydı detayı | Metrikler, anahtar kelimeler, tarih kaynağı (`EXPLICIT`/`RELATIVE`/`DEFAULTED`), kapsam (`SINGLE`/`SHARED`/`UNKNOWN`), kaynak ham bildirim bağlantısı | FR-26, FR-17, FR-06 |
| **S-3** | Ham bildirim detayı | Ham metin (değiştirilmemiş), üzerinde vurgulanmış anahtar kelimeler, gönderim zamanı, analiz durumu, türeyen kayıtlar, reprocess eylemi | FR-26, FR-08, FR-14, FR-15 |

S-1 tek sayfadır; S-2 ve S-3 ayrı adreslenebilir görünümlerdir (bağlantı paylaşılabilir olmalı,
bkz. FR-21 kabul kriteri).

---

## 6. Fonksiyonel İsterler

> Notasyon: her ister **FR-xx** kimliği, tek cümlelik tanım ve doğrulanabilir kabul kriteri taşır.

### FR-01 — Olay bildirimi girişi
Kullanıcı serbest metin bir olay bildirimi gönderebilir.
- **Kabul:** Boş olmayan metin gönderildiğinde sistem kaydı oluşturur ve kaydın kimliğini döner. Boş/yalnızca boşluk içeren ya da tanımlı maksimum uzunluğu aşan metin, açıklayıcı bir hata ile reddedilir.
- **Kural (TC-9):** Birebir aynı metin ikinci kez gönderilirse **ikinci kayıt açılmaz**: mevcut kaydın makbuzu döner ve analiz yeniden çalışmaz. Durum kodu farkı bunu söyler — yeni kayıt `201`, zaten var olan `200`. Gerekçe ve alternatifler [ADR-035](DECISIONS.md#adr-035--yeniden-i̇şleme-ve-mükerrer-gönderim-aynı-metin-i̇kinci-kayıt-açmaz)'te; kısaca, çift tık ya da retry sonucu sayıların sessizce ikiye katlanması bu sistemin en pahalı hata biçimi.

### FR-02 — Ham metnin değiştirilmeden saklanması
Gönderilen metin, hiçbir normalizasyon uygulanmadan MongoDB'ye yazılır ve sonradan değiştirilemez.
- **Kabul:** Kaydedilen metin, gönderilen metinle bayt bayt aynıdır. Ham bildirimi güncelleyen veya silen bir API yoktur.
- **Not:** Bu kayıt log/audit niteliğindedir; analiz başarısız olsa dahi ham metin korunur.
- **Not (v2.0):** Ham döküman **yazıldıktan sonra hiç güncellenmez** — analiz sonucu (durum, uyarılar) onu üreten `analysis` modülünde yaşar. Böylece değişmezlik yalnızca metnin değil, **kaydın tamamının** özelliği olur.

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
- **Not:** Veri modeline nasıl yansıdığı [ADR-019](DECISIONS.md#adr-019--kayıt-granülaritesi)'da karara bağlandı: `(ham bildirim, tarih, il, olay tipi)` granülaritesi, ile atanamayan sayılar için `SHARED` kapsamı.

### FR-08 — Normalize verinin saklanması ve izlenebilirlik
Analizden çıkan veri PostgreSQL'e yazılır; ham MongoDB kaydı ile normalize kayıtlar **iki yönlü** ilişkilendirilebilir.
- **Kabul:** Bir olay kaydından kaynak ham metne, bir ham bildirimden ondan türeyen tüm olay kayıtlarına API üzerinden ulaşılabilir.
- **Not (v2.0):** Ham bildirim → olay kayıtları yönü `GET /incidents?rawReportId=...` ile karşılanır (§8.2/C-5); ham bildirim ucu türetilmiş veriyi temsil etmez (FR-14).

### FR-09 — Tanınmayan olay tipi davranışı
Kataloğa uymayan bir olay tipi geldiğinde sistem bildirimi reddetmez.
- **Kabul:** Ham metin her zaman MongoDB'ye yazılır; olay kaydı `OTHER` tipi ve `UNCLASSIFIED` durumu ile üretilir; çıkarılabilen tarih/il/sayılar korunur; kullanıcıyı bilgilendiren bir uyarı listesi (`warnings`) API üzerinden erişilebilir olur ve arayüzde gösterilir (FR-20).
- **Not (v2.0):** Uyarılar **gönderim cevabında değil**, analiz sonucuyla birlikte `analysis`'in ucundan döner (FR-19, §8.2/C-4). Davranış aynı — kullanıcı yine bilgilendirilir — değişen yalnızca uyarının hangi uçtan geldiğidir. Gerekçe: uyarıyı üreten modül onu sahiplenir; `ingestion` sahibi olmadığı veriyi yayınlamaz.
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
- **Kural (v2.0):** SSE bir **tazeleme tetikleyicisidir, veri kaynağı değildir.** Hiçbir veri yalnızca akış üzerinden erişilebilir olmaz; olay, istemcinin ilgisini belirlemesine yetecek kadar bilgi taşır, veriyi istemci sorgu uçlarından alır (§8.2/C-8).
- **Sonucu:** Akış tamamen çökse bile hiçbir veri kaybolmaz ve her istemci sorguyla doğru duruma ulaşır; yalnızca "anlık"lık kaybedilir.

### FR-14 — Ham bildirim okuma
Kullanıcı bir ham bildirimi kimliğiyle okuyabilir ve ham bildirimleri listeleyebilir.
- **Kabul:** Tekil okuma ham metni ve gönderim zamanını döner.
- **Not (v2.0):** Analiz durumu ve türeyen kayıtlar bu uçtan **dönmez** — ikisi de `analysis`'in verisidir. Ham bildirimden türeyen kayıtlara `GET /incidents?rawReportId=...` ile ulaşılır (§8.2/C-5); FR-08'in iki yönlü izlenebilirlik isteri bu uçla karşılanır. Sonuç: ham bildirim detay ekranı iki istek atar, karşılığında hiçbir modül diğerinin verisini temsil etmez.

### FR-15 — Yeniden işleme (reprocess)
Mevcut bir ham bildirim, güncel analiz kurallarıyla yeniden analiz edilebilir.
- **Kabul:** Reprocess sonrası ham metin değişmez; önceki normalize kayıtların yerini yeni sonuç alır ve mükerrer kayıt oluşmaz.
- **Not (v2.0):** Cevap, gönderimle aynı biçimdedir — işlemin kabul edildiğini söyler, analiz sonucunu taşımaz. Sonuç yine `GET /incidents?rawReportId=...` ile okunur ve bağlı istemciler akıştan haberdar olur.
- **Gerekçe:** Katalog/kurallar geliştikçe geçmiş veriyi kaybetmeden yeniden değerlendirebilmek gerekir.

### FR-16 — Katalog metadata'sı
Sistem, desteklediği olay tiplerini, her tipe ait metrikleri ve tanınan il listesini API üzerinden sunar.
- **Kabul:** Frontend, filtre ve grafik seçeneklerini bu uçtan besleyebilir; katalog konfigürasyonda değiştiğinde uç otomatik olarak yeni içeriği yansıtır.

### FR-17 — Çıkarılan anahtar kelimeler
Analizde tespit edilen anahtar kelimeler olay kaydıyla birlikte saklanır, kullanıcıya gösterilir ve filtre boyutu olarak kullanılabilir.
- **Kabul:** Bir olay kaydı sorgulandığında hangi kelimelerin hangi çıkarımı tetiklediği görülebilir; anahtar kelimeye göre filtreleme FR-10 kapsamında çalışır.
- **Frontend sözleşme etkisi:** Kelimenin ham metindeki **konumu** (başlangıç/bitiş offset'i) de saklanır ve API'de dönülür; FR-26'daki vurgulama bunu gerektirir. Offset olmadan istemci kelimeyi metinde yeniden aramak zorunda kalır ve Türkçe ek/apostrof toleransı yüzünden yanlış yeri işaretler. Bkz. §8.2.

---

## 6B. Fonksiyonel İsterler — Frontend

> FR-01…FR-17 sistemin davranışını ve backend sözleşmesini tanımlar. Aşağıdaki isterler bu
> davranışın kullanıcıya görünen yüzünü tanımlar; her biri yukarıdaki bir istere dayanır ve onu
> tekrar etmez.

### FR-18 — Bildirim girişi ekranı
Kullanıcı ana ekrandaki tek bir çok satırlı metin alanına serbest metni yapıştırıp gönderebilir.
- **Kabul:**
  - Boş/yalnızca boşluk içeren metinde gönderim düğmesi çalışmaz; sınırı aşan metin gönderilmeden önce kullanıcıya bildirilir (karakter sayacı, tanımlı maksimuma göre).
  - Gönderim sırasında düğme kilitlenir; **aynı metin çift gönderilemez**.
  - Sunucu RFC 7807 hatası döndüğünde `title`/`detail` alanları kullanıcıya okunur biçimde gösterilir; ham JSON veya stack trace ekrana basılmaz.
  - Başarılı gönderim sonrası metin alanı temizlenir ve sonuç görünür (FR-19).
- **Dayanak:** FR-01

### FR-19 — Sonucun anında gösterilmesi
Gönderim tamamlandığında metinden **ne çıkarıldığı** aynı ekranda, ek bir kullanıcı eylemi gerekmeden görünür.
- **Kabul:**
  - Gönderim cevabı ham kaydın **makbuzudur**: kimlik ve gönderim zamanı. Ne normalize kayıtları, ne analiz durumunu, ne de uyarıları taşır — arayüz bunları cevaptaki kimlikle `GET /incidents?rawReportId=...` üzerinden **sorgulayarak** getirir (§8.2/C-5).
  - Analiz senkron çalıştığı için gönderim cevabı döndüğünde sonuç sorguya hazırdır. İleride analiz asenkrona taşınırsa aynı sorgu "henüz analiz edilmedi" döner ve sonucu akış tetikler — **arayüz akışı değişmez**.
  - Sonuç, canlı akıştan gelecek olay **beklenmeden** gösterilir. SSE kopukken de gönderimi yapan kullanıcı kendi sonucunu görür; akış diğer istemcileri güncelleyen mekanizmadır, gönderenin sonuç kanalı değil.
  - Analiz sıfır kayıt ürettiğinde ekran boş kalmaz: bunun geçerli bir sonuç olduğu ve nedeni (uyarılar) gösterilir.
  - Sorgudan dönen `warnings[]` listesi boş değilse kullanıcıya ayrıca gösterilir (FR-20).
- **Dayanak:** FR-01, FR-03, FR-14 · **Çözer:** TC-12

### FR-20 — Uyarıların ve sınıflandırılamayan kayıtların görünürlüğü
Analizin ürettiği uyarılar ve `UNCLASSIFIED` durumu kullanıcıdan gizlenmez.
- **Kabul:**
  - `OTHER`/`UNCLASSIFIED` kayıt listede ve detayda açıkça etiketlenir; hata gibi değil, **bilgilendirme** olarak sunulur — bildirim reddedilmemiştir.
  - Tarih kaynağı `DEFAULTED` olan kayıtlarda tarihin metinden çıkarılmadığı, `RELATIVE` olanlarda göreli ifadeden çözüldüğü ayırt edilebilir (FR-06).
  - Analizi başarısız olan bildirim listede görünür ve reprocess edilebilir (FR-26); ham metnin kaybolmadığı kullanıcıya görünür olur. Bu durum bilgisi `analysis`'in analiz sonucu kaydından gelir (§8.2/C-4), ham bildirim kaydından değil.
- **Dayanak:** FR-06, FR-09

### FR-21 — Kayıt listesi ve filtreleme
Normalize kayıtlar sayfalanmış bir tabloda gösterilir ve filtrelenebilir.
- **Kabul:**
  - Olay tipi, il, tarih aralığı ve anahtar kelime filtreleri tek tek ve birlikte uygulanabilir; filtreler sunucuya iletilir, istemcide filtreleme yapılmaz.
  - Sayfalama ve sıralama sunucu tarafındadır; "tümünü çek, tarayıcıda süz" yaklaşımı kullanılmaz.
  - Aktif filtreler adres çubuğuna yansır: bağlantı paylaşıldığında/yenilendiğinde aynı görünüm açılır.
  - Sonuç boşken tabloya boş satır değil, filtreyi hatırlatan bir boş durum mesajı gösterilir (FR-28).
- **Dayanak:** FR-10, FR-17

### FR-22 — Özet tablo
Kullanıcı aynı veriyi olay tipi / il / metrik kırılımında toplanmış olarak görebilir.
- **Kabul:** Özet, agregasyon ucundan gelir (istemcide toplanmaz); aktif filtrelerle tutarlıdır; `SHARED` kapsamlı toplamlar FR-24'e göre gösterilir.
- **Dayanak:** FR-10, FR-11 · **Kaynak:** "grafik ve **özet tablolar** anlık olarak güncellenir"

### FR-23 — Olay tipi bazlı grafik ve kümülatif mod
Kullanıcı grafiği olay tipine göre görüntüler; seçilen tipin metrikleri ayrı seriler olarak çizilir ve kümülatif moda geçilebilir.
- **Kabul:**
  - Olay tipi seçimi grafikteki metrik serilerini belirler; seçenekler katalog ucundan gelir (FR-27).
  - Kümülatif anahtarı açıldığında her nokta kendisi ve öncekilerin toplamıdır; kümülatif dönüşüm **sunucudan** istenir (FR-12).
  - Tarih aralığı ve il filtreleri grafiğe de uygulanır; grafik ile tablo aynı filtre durumunu paylaşır — ikisi farklı veriyi gösteremez.
  - Metrik serileri gizlenip gösterilebilir; seri yoksa grafik yerine boş durum gösterilir.
- **Dayanak:** FR-11, FR-12

### FR-24 — Coğrafi (il) kırılımı
Veriler zaman içinde **ve il bazında** izlenebilir.
- **Kabul:**
  - Grafikte il, filtre olmanın yanı sıra bir **kırılım boyutudur**: birden fazla il seçildiğinde seriler il bazında ayrışabilir.
  - Özet tabloda il bir kırılım sütunudur; il bazlı toplamlar ve genel toplam birlikte görülebilir.
  - **`SHARED` kapsamlı kayıtlar hiçbir ile eklenmez ve düşürülmez**: ayrı, açıkça etiketlenmiş bir satır/seri olarak ("her iki ilde toplam") gösterilir; okuyucu il toplamları ile genel toplamı bu satır üzerinden uzlaştırabilir.
  - Birden fazla il seçildiğinde aynı `SHARED` kayıt **bir kez** sayılır.
  - `UNKNOWN` kapsamlı kayıtlar "il belirtilmemiş" olarak ayrı görünür; sessizce gizlenmez.
- **Dayanak:** FR-07, FR-11, ADR-019 · **Kaynak:** "zaman içinde, coğrafi bölge bazında grafiksel olarak izlenebildiği"

### FR-25 — Gerçek zamanlı güncelleme
Yeni bir bildirim girildiğinde grafik, özet tablo ve kayıt listesi sayfa yenilenmeden güncellenir.
- **Kabul:**
  - Uygulama açılışta SSE akışına abone olur; yeni normalize kayıt **sinyalinde** üç görünüm de (liste, özet, grafik) tazelenir. Veri sinyalden değil, sorgu uçlarından gelir (FR-13 kuralı).
  - Tazeleme sırasında görünüm **boşaltılmaz**: yeni veri gelene kadar mevcut veri ekranda kalır. Aksi halde her sinyalde tablo bir an boşalır ve bu, kullanıcı gözünde sayfa yenilenmesinden farksız olur.
  - Gelen olay aktif filtreye uymuyorsa listeye eklenmez; kullanıcının kurduğu görünüm akış tarafından bozulmaz.
  - Kayıtlar listede kimliğe göre tutulur; aynı kayıt hem gönderim sonrası sorgudan hem akıştan geldiğinde tabloda **tek satır** olur.
  - Bağlantı durumu (bağlı / yeniden bağlanıyor / kopuk) kullanıcıya görünür; kopma sessizce yutulmaz.
  - Bağlantı koptuğunda otomatik yeniden bağlanılır ve yeniden bağlandığında görünüm tazelenir — kopukluk süresinde kaçan olaylar normal sorgu ile telafi edilir (SSE veri kaynağı değil, tetikleyicidir).
  - Sekme arka plandayken açık kalan akış kaynak sızdırmaz; sayfa kapandığında bağlantı kapatılır.
- **Dayanak:** FR-13 · **Çözer:** TC-13

### FR-26 — İzlenebilirlik ve reprocess arayüzü
Kullanıcı bir olay kaydından kaynak ham metne, ham metinden türeyen kayıtlara ulaşabilir.
- **Kabul:**
  - Olay kaydı detayında kaynak ham bildirime bağlantı vardır; ham bildirim detayında ondan türeyen tüm kayıtlar listelenir (iki yön de gezinilebilir).
  - Ham metin **değiştirilmeden** gösterilir; çıkarılan anahtar kelimeler metin üzerinde vurgulanır (kaynak dokümandaki "bold ile işaretlenmiş kelimeler" ipucunun görünür karşılığı).
  - Reprocess arayüzden tetiklenebilir; sonuç aynı ekranda güncellenir ve mükerrer kayıt görünmez.
- **Dayanak:** FR-08, FR-14, FR-15, FR-17

### FR-27 — Katalog metadata'sının arayüzü beslemesi
Olay tipi, metrik ve il seçenekleri arayüzde sabit yazılmaz.
- **Kabul:** Tüm seçim kutuları katalog metadata ucundan (FR-16) doldurulur; YAML kataloğuna yeni bir olay tipi eklendiğinde **frontend'de kod değişikliği olmadan** seçeneklerde görünür.
- **Dayanak:** FR-16, NFR-08

### FR-28 — Yükleme, hata ve boş durum davranışı
Her veri getiren görünümün tanımlı üç durumu vardır: yükleniyor, hata, boş.
- **Kabul:**
  - Yükleme sırasında görünüm boş kalmaz; hata durumunda tekrar deneme yolu sunulur.
  - Backend erişilemezken uygulama beyaz ekrana düşmez, anlaşılır bir hata gösterir.
  - Hiçbir hata mesajı ham teknik ayrıntı (stack trace, iç sınıf adı, JDBC/URL) içermez; sunucu zaten sızdırmaz, arayüz de üretmez.

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
| `POST /incident-reports` | Ham metin gönderimi; **yalnızca kayıt makbuzu** döner (kimlik + gönderim zamanı). Analiz aynı istek içinde tetiklenir ama cevaba girmez. Birebir aynı metin ikinci kez gönderilirse yeni kayıt açılmaz; mevcut kaydın makbuzu **200** ile döner (yeni kayıt 201) — [ADR-035](DECISIONS.md#adr-035--yeniden-i̇şleme-ve-mükerrer-gönderim-aynı-metin-i̇kinci-kayıt-açmaz) | FR-01, FR-02, FR-19 |
| `GET /incident-reports` | Ham bildirimleri sayfalı listeleme | FR-14 |
| `GET /incident-reports/{id}` | Tekil ham bildirim: metin + gönderim zamanı. Analiz durumu ve türeyen kayıtlar burada **dönmez** — bkz. `GET /incidents` | FR-14 |
| `POST /incident-reports/{id}/reprocess` | Güncel kurallarla yeniden analiz; gönderimle aynı biçimde makbuz döner | FR-15 |
| `GET /incidents` | Normalize olay kayıtları; filtre (olay tipi, il, tarih aralığı, keyword, **`rawReportId`**) + sayfalama + sıralama. Analiz durumu ve uyarılar da bu uçtan döner | FR-08, FR-10, FR-17, FR-19 |
| `GET /incidents/{id}` | Tekil olay kaydı + metrikler + anahtar kelimeler + kaynak ham bildirim referansı | FR-08, FR-17 |
| `GET /analytics/time-series` | Olay tipi bazlı, metriklere ayrılmış zaman serisi; `cumulative` parametresi; **il kırılımı** (`groupBy=province`) | FR-11, FR-12, FR-24 |
| `GET /analytics/summary` | Özet tablo için agrega (olay tipi / il / metrik kırılımı) | FR-10, FR-11, FR-22 |
| `GET /metadata` | Olay tipleri, metrikleri ve il listesi | FR-16, FR-27 |
| `GET /stream/incidents` | SSE akışı; yeni normalize kayıt **sinyali** yayınlar (tek yönlü). Veri taşımaz — istemci sinyali alıp sorgu uçlarından tazeler | FR-13, FR-25 |

**Hata sözleşmesi:** Tüm hatalar RFC 7807 (`application/problem+json`) formatında döner.

### 8.2 Frontend'in sözleşmeden beklentileri

Frontend'in kapsama girmesi backend API'sine **yeni uç eklemez**, ama mevcut uçların birkaçında
alan/parametre düzeyinde netleşme gerektirir. C-4 ve C-5, §5'teki sahiplik kuralının (v2.0)
doğrudan sonucudur ve mevcut kodda **refactor** gerektirir. Bunlar burada ister olarak sabitlenir; şema ayrıntısı
task aşamasına aittir. *(Bu bölüm, backend geliştirmesi paralel yürüdüğü için ayrıca bir uyarı
listesidir: aşağıdakiler bugün sözleşmede yoktur.)*

| # | Beklenti | Neden | Etkilenen backend task'ı |
|---|---|---|---|
| **C-1** | `GET /analytics/time-series` **il kırılımı** desteklemeli — il yalnızca filtre değil, seri boyutu | FR-24 "zaman içinde, coğrafi bölge bazında" isterini karşılamak istemcide toplama yapmadan mümkün olmalı | T-17 |
| **C-2** | Agregasyon uçları `SHARED` ve `UNKNOWN` kapsamlı toplamları **ayrı ve etiketli** döndürmeli | FR-24: bölüştürme yasak, düşürme yasak; okuyucu il toplamı ile genel toplamı uzlaştırabilmeli (ADR-019) | T-17 |
| **C-3** | Anahtar kelimeler, ham metindeki **konumuyla** (offset) ve hangi çıkarımı tetiklediğiyle dönmeli | FR-26 vurgulaması; istemcide yeniden arama Türkçe ek/apostrof yüzünden yanlış eşleşir | T-14, T-16 |
| **C-4** | **`analysis` kendi analiz sonucunu saklamalı ve sorgulanabilir kılmalı** — ham bildirim başına durum (`ANALYZED`/`FAILED`), uyarılar ve analiz zamanı | Model 2'nin taşıyıcı maddesi: bu veri bugün `ingestion`'ın Mongo dökümanında ve dönüş event'iyle oraya yazılıyor. Olmazsa analiz hatası kullanıcıya görünmez olur ve reprocess'in (FR-15) hedefi kaybolur | T-07 (refactor), T-16 |
| **C-5** | `GET /incidents` bir **`rawReportId` filtresi** taşımalı | Model 2'de gönderim cevabı yalnızca kimlik döner; sonucu getirmenin **tek** yolu bu filtre. Aynı filtre FR-08'in "ham bildirimden türeyen kayıtlara ulaşma" yönünü ve ham bildirim detay ekranını da besler (FR-14, FR-26). Bugün böyle bir filtre yok | T-16 |
| **C-6** | Tarayıcı ile API arasında **aynı köken** (reverse proxy) ya da açık **CORS** yapılandırması bulunmalı | Bugün ne CORS yapılandırması ne de proxy var; frontend ayrı portta yayınlanacak. Karar TC-17'de, ADR olarak kaydedilir | T-18 / frontend dağıtım task'ı |
| **C-7** | Sayfalama cevabı **toplam kayıt sayısını** içermeli | Sayfa göstergesi ve "sonuç yok" ayrımı için | T-16 |
| **C-9** | Analiz uyarıları (`analysis.warnings`) makine tarafından okunabilir bir **kod** taşımalı | Bugün serbest metin ve **İngilizce**: *"No known event type matched this text…"*. Hata sözleşmesinde bu sorun `code` ile çözülmüş (T-06); uyarılarda karşılığı yok, dolayısıyla Türkçe arayüzde gösterilemiyorlar. T-25 şimdilik uyarıları basmıyor; kullanıcıya görünen açıklamayı makine tarafından okunan alanlardan (`classification`, `dateSource`, `status`) türetiyor. Kod gelirse uyarı metni doğrudan çevrilebilir hale gelir | T-13 / T-16 |
| **C-10** | Kabul edilen **maksimum metin uzunluğu** metadata ucundan yayınlanmalı | `max-text-length` bir sunucu ayarı (bugün 10000) ve istemci onu bilmiyor. FR-18 "sınırı aşan metin **gönderilmeden önce** bildirilir" diyor; bunu karşılamanın tek dürüst yolu sınırı sunucudan öğrenmek. Arayüze sabit yazmak, ayar değiştiğinde sessizce kayan bir sayı olurdu — T-25 bu yüzden sayacı sınırsız gösteriyor ve sunucunun `report.text.too-long` reddine güveniyor | T-08 |
| **C-8** | SSE olayı **sinyal** olmalı: istemcinin ilgisini belirlemesine yetecek kadar (olay kaydı kimliği, ham bildirim kimliği, tarih, il, olay tipi), satır çizmeye yetecek kadar değil | Veri taşıyan bir payload, tablo sütunlarını akış sözleşmesine bağlar ve veriyi tek kanala emanet eder. Sinyal modeli T-18'i küçültür ve akış çökse bile sistemi doğru tutar (FR-13 kuralı) | T-18 |

---

## 9. Fonksiyonel Olmayan İsterler

| No | İster | Kabul kriteri |
|---|---|---|
| **NFR-01** | Java 21 ve Spring Boot 3.5.x kullanılır | Build Java 21 toolchain ile geçer |
| **NFR-02** | Birim test kapsamı **≥ %80** ve tüm önemli fonksiyonları kapsar — **backend ve frontend için ayrı ayrı** | Backend: JaCoCo eşiği modül başına, `verify`'da zorunlu. Frontend: aynı eşik test koşucusunun coverage kapısıyla zorunlu. **Her iki tarafta da eşik altında build kırılır.** Kaynak dokümandaki ister backend'e daraltılmamıştır |
| **NFR-03** | Sistem `docker compose up` ile **tek komutta** ayağa kalkar | Temiz bir makinede tek komut sonrası **frontend, API ve iki veri tabanı** çalışır durumdadır; kullanıcı yalnızca tarayıcı adresini açar |
| **NFR-04** | MongoDB ve PostgreSQL **single instance** çalışır | Replica set / cluster kurulumu yoktur |
| **NFR-05** | Modüller arası bağımlılık yalnızca event ve açık API üzerindendir | Modül sınırı ihlali otomatik bir testle yakalanır |
| **NFR-06** | PostgreSQL şeması versiyonlu migration ile yönetilir | Şema, uygulama tarafından otomatik türetilmez; migration dosyaları kaynak kontrolündedir |
| **NFR-07** | API OpenAPI ile dokümante edilir | Uygulama ayaktayken makine okunur şema ve tarayıcı arayüzü erişilebilir |
| **NFR-08** | Olay tipi/metrik kataloğu konfigürasyondan yönetilir | Yeni olay tipi eklemek yalnızca konfigürasyon değişikliği gerektirir |
| **NFR-09** | Yapılandırılmış loglama; ham bildirim kimliği ile korelasyon | Bir bildirimin ingestion→analysis→SSE yolculuğu loglardan tek kimlikle izlenebilir |
| **NFR-10** | README kurulum/çalıştırma talimatlarını **ve** tasarım gerekçelerini içerir | Kaynak dokümanın açık isteri (özellikle FR-09 gerekçesi). Talimatlar **frontend'i de** kapsar |
| **NFR-11** | Kaynak kod GitHub üzerinden erişilebilirdir | Depo yayımlanmıştır |
| **NFR-12** | Frontend **ReactJS** ile, **TypeScript** ve **Vite** üzerinde geliştirilir | Kaynak dokümanın "Frontend ReactJS ile geliştirilecektir" isteri. TypeScript tercihi, backend'de kurulan "sınır ihlali derlemede patlasın" çizgisinin (ArchUnit, `ddl-auto=validate`) istemci tarafındaki karşılığıdır: API sözleşmesinden sapma çalışma zamanında değil, build'de görünür |
| **NFR-13** | API sözleşmesi frontend'de **tek** yerde tiplenir; iş kuralı çoğaltılmaz | Uç çağrıları tek bir API katmanından geçer; kümülatif toplama, agregasyon ve filtreleme istemcide **yeniden hesaplanmaz** (§5.4). Aynı kuralın iki dilde iki kopyası olmaz |
| **NFR-14** | Arayüzde hiçbir katalog verisi sabit yazılmaz | Olay tipi, metrik ve il listeleri yalnızca metadata ucundan gelir; YAML'a eklenen bir tip frontend derlemesi değişmeden görünür (FR-27, NFR-08) |
| **NFR-15** | Frontend yapılandırması **ortam değişkeninden** gelir; koda gömülmez | API adresi derleme/çalışma zamanı yapılandırmasıyla belirlenir; `localhost` gibi değerler kaynak koda gömülü değildir. Frontend'de hiçbir sır (parola, anahtar) bulunmaz — zaten kimlik doğrulama yoktur (ADR-011) |
| **NFR-16** | Arayüz masaüstü tarayıcıların güncel sürümlerinde çalışır ve temel erişilebilirlik kurallarına uyar | Form alanları etiketli, eylemler klavyeyle erişilebilir, durum bilgisi yalnızca renkle taşınmaz. Hedef: analistin masaüstü kullanımı; mobil düzen hedef değildir (§2.2) |

### Performans ve dayanıklılık (v1 hedefleri)
- Tek bildirim analizi, tipik uzunlukta (≤ 2000 karakter) bir metin için kullanıcı isteğinin içinde tamamlanır; analiz senkron olduğu için API yanıt süresine dahildir.
- Analiz hatası ham metnin kaydedilmesini **engellemez**; ham kayıt `FAILED` işaretlenir ve reprocess ile tekrar denenebilir.
- SSE kesintisi veri kaybına yol açmaz; istemci yeniden bağlandığında normal sorgu uçlarıyla güncel duruma erişir. **SSE bir veri kaynağı değil, tazeleme tetikleyicisidir**; doğruluk her zaman sorgu uçlarından gelir.
- Arayüz, canlı akış altında kararlı kalır: art arda gelen olaylar her biri için ayrı ayrı yeniden sorgu tetiklemez (toplu/gecikmeli tazeleme — TC-13).

---

## 10. Teknik Challenge'lar (PRD kapsamı dışı — task aşamasında çözülecek)

Bu maddeler bilinçli olarak PRD'de karara bağlanmamıştır; her biri implementasyon aşamasında ayrı bir teknik tasarım kararı gerektirir.

| No | Challenge | Neden zor |
|---|---|---|
| ~~TC-1~~ | ~~Kayıt granülaritesi~~ | **Çözüldü → [ADR-019](DECISIONS.md#adr-019--kayıt-granülaritesi).** Granülarite `(ham bildirim, tarih, il, olay tipi)`; ile atanamayan sayılar `SHARED` kapsamıyla ayrı kayıt, kapsadıkları iller kayıtlı |
| ~~TC-2~~ | ~~Metrik veri modeli~~ | **Çözüldü → [ADR-020](DECISIONS.md#adr-020--metrik-veri-modeli-metrik-başına-satır).** Metrik başına satır; katalog büyürken şema değişmiyor |
| **TC-3** | Sayı ↔ metrik eşleştirme | Cümle içi yakınlık kuralları; "Bursa'da 8, Kocaeli'nde 6 trafik kazası" gibi çoklu il-sayı bağlama |
| **TC-4** | Türkçe bileşik sayı sözcüğü ayrıştırma | "on iki", "kırk beş", "yüz yirmi" gibi çok kelimeli ifadeler |
| **TC-5** | Türkçe metin normalizasyonu | Locale bağımlı büyük/küçük harf (i/I/İ/ı); ek ve apostrof toleransı (`Ankara'da`, `Kocaeli'nde`) |
| **TC-6** | Tarih ayrıştırma ve göreli ifade çözümleme | Çoklu format; göreli **aralık** ifadelerinin ("son 24 saatte", "son 3 günde") tek güne mi indirgeneceği yoksa tarih aralığı olarak mı modelleneceği; zaman dilimi seçimi (`Europe/Istanbul` vs. UTC) ve gün sınırı; `DEFAULTED` kayıtların grafik/agregasyonlarda nasıl ele alınacağı (FR-06) |
| **TC-7** | İl tanıma | 81 il; çok kelimeli isimler (Afyonkarahisar, Kahramanmaraş); ilçe adlarının il sanılması |
| **TC-8** | Sınıflandırma skorlaması | Birden fazla tipin tetiklendiği metinlerde skor/eşik ve güven değeri (FR-09 ile bağlantılı) |
| ~~TC-9~~ | ~~Mükerrer gönderim~~ | **Çözüldü → [ADR-035](DECISIONS.md#adr-035--yeniden-i̇şleme-ve-mükerrer-gönderim-aynı-metin-i̇kinci-kayıt-açmaz).** Ham metnin SHA-256 özeti üzerinde unique (sparse) indeks; birebir aynı metin ikinci kayıt açmaz, mevcut kaydın makbuzu **200** ile döner (yeni kayıt 201) ve analiz yeniden çalışmaz. `POST` böylece idempotent oluyor; belirleyici olan, çift sayımın sessiz ve geri döndürülemez bir hata olması |
| ~~TC-10~~ | ~~SSE bağlantı yönetimi (sunucu)~~ | **Çözüldü → [ADR-034](DECISIONS.md#adr-034--canlı-akışın-yaşam-döngüsü-rapor-başına-sinyal-commit-sonrası-yayın-heartbeat-ile-temizlik).** Sinyalin birimi rapor; yayın analiz transaction'ı **commit ettikten sonra**; abonelik süreli, heartbeat yorumu hem bağlantıyı açık tutuyor hem ölü aboneyi ortaya çıkarıyor; akış durumsuz — tekrar oynatma yok, kaçan mesaj gecikmiş tazeleme demek |
| **TC-11** | Anlamlı %80 kapsam (backend) | Kapsam sayısını şişirmeden gerçek davranışı test etmek; Testcontainers ile mock dengesi |
| ~~TC-12~~ | ~~Gönderim sonrası sonucun getirilmesi~~ | **Karara bağlandı (v2.0).** `POST` yalnızca makbuz döner; sonuç `GET /incidents?rawReportId=...` ile okunur; SSE tetikleyicidir. Gerekçe: her modül yalnızca sahibi olduğu veriyi yayınlar (§5) ve hiçbir veri tek kanala emanet edilmez (FR-13). Bkz. FR-19. **ADR olarak `docs/DECISIONS.md`'e işlenecek** |
| ~~TC-13~~ | ~~Canlı akışta agregasyon tazeleme stratejisi~~ | **Çözüldü → [ADR-040](DECISIONS.md#adr-040--canlı-tazeleme-sinyal-geçersizleştirir-delta-uygulamaz-pencereli-birleştirme-ve-kanıtlanmış-i̇lgisizlikte-atlama).** Sinyal **yeniden sorgulatır**, delta uygulanmaz: satır eklemek filtre/sayfalama/toplam kurallarının istemcide kopyası olurdu. Gürültü, pencere başına **leading + trailing** birleştirmeyle çözülür (on gönderim iki tazeleme); düz debounce sürekli akışta hiç tazelemediği için seçilmedi. Filtreye uymayan olay yalnızca **kanıtlanmış** ilgisizlikte atlanır — ekranda o rapordan kayıt varsa (reprocess), anahtar kelime filtresi aktifse ya da sinyal okunamadıysa yine tazelenir |
| ~~TC-14~~ | ~~`SHARED` ve `UNKNOWN` kapsamın arayüzde temsili~~ | **Çözüldü → [ADR-038](DECISIONS.md#adr-038--shared-ve-unknown-kapsamın-arayüzdeki-temsili-aynı-tabloda-kendi-satırında-adıyla).** Özet tabloda **aynı tablonun içinde, kendi satırında ve kelimeyle etiketli** (`Ortak toplam`, `İl belirtilmemiş`); bölüştürülmez, düşürülmez, ayrı tabloya sürülmez. Üç seviyenin toplamı da sunucudan geldiği gibi basılır — satırları toplayan bir arayüz paylaşılan figür varken farklı ve yanlış bir sayı üretirdi. Fark, yalnızca böyle bir satır varken görünen tek cümlelik bir dipnotla açıklanır. Grafik tarafı (T-28) aynı kuralı seri düzeyinde uygular |
| ~~TC-15~~ | ~~İstemci durumu ile sunucu durumunun ayrımı~~ | **Çözüldü → [ADR-037](DECISIONS.md#adr-037--filtre-durumunun-tek-kaynağı-adres-çubuğu).** Filtre durumunun tek kopyası **adres çubuğu**; React tarafında store/context/`useState` kopyası yok, dolayısıyla senkronize edilecek iki şey de yok. Çözümleme kanonik, çünkü sorgu önbelleğinin anahtarı da o; `/incidents`'tan okunan her şey tek bir anahtar öneki altında, canlı akış (TC-13) hepsini birden geçersizleştirebilsin diye. Filtre çubuğu, liste — ve gelecekte özet ile grafik — birbirine bağlı değil, aynı URL'i okuyor |
| ~~TC-16~~ | ~~Anlamlı %80 kapsam (frontend)~~ | **Çözüldü → [ADR-042](DECISIONS.md#adr-042--frontend-kapanışı-kapsamın-ne-ölçtüğü-kapının-kırıldığının-kanıtlanması-ve-arayüzün-i̇ki-erişilebilirlik-kuralı).** Kapsam davranış testleriyle tutuluyor — kod tabanında `toMatchSnapshot` sıfır kez geçiyor — ve saf çekirdek (filtre çözümleme, özet düzeni, seri çevrimi, vurgulama, sinyal ilgi testi) DOM'suz test ediliyor. Zamanlama sahte zamanlayıcıyla, akış sürülebilir bir sahte `EventSource` ile deterministik; grafik gerçek SVG çiziyor. **Kapının kırdığı fiilen kanıtlandı:** kapsanmayan kod eklenince çıkış kodu 1 ve *"does not meet global threshold (80%)"* |
| **TC-17** | **Frontend dağıtımı ve çalışma zamanı yapılandırması** | Statik dosyaların hangi sunucuyla yayınlanacağı; API'ye **aynı köken (reverse proxy)** mı yoksa **CORS** mu ile erişileceği (proxy seçilirse SSE'nin tamponlanmaması gerekir); API adresinin build-time mı runtime mı verileceği. Tek komutla ayağa kalkma isteri (NFR-03) bu kararı bağlar |
| ~~TC-18~~ | ~~Anahtar kelime vurgulamanın hizalanması~~ | **Çözüldü → [ADR-041](DECISIONS.md#adr-041--i̇zlenebilirlik-ekranları-sunucudan-gelen-offsetlerle-vurgulama-metne-hiçbir-şey-eklememe-ve-reprocessin-yerinde-tazelenmesi).** Offset'ler kaymıyor çünkü Java ve JavaScript **aynı birimi** (UTF-16 kod birimi) sayıyor; çalışan sistemden alınan offset'lerle testle sabitlendi. Metinde arama yapılmıyor (ekli ve tekrar eden kelimeler yanlış işaretlenirdi), çakışan aralıklar rol kümesinin değiştiği sınırlardan bölünüp tek vurguya indirgeniyor, ve **metnin içine hiçbir şey eklenmiyor** — rol bilgisi gösterge, `title` ve alt çizgi biçimiyle dışarıda taşınıyor |

---

## 11. Kabul Kriterleri (Definition of Done)

1. Kaynak dokümandaki **üç örnek metin** uçtan uca doğru ayrıştırılır ve otomatik "altın (golden) test" olarak sabittir:

   | Örnek | Beklenen olay tipi | Beklenen tarih | Beklenen il(ler) | Beklenen metrikler |
   |---|---|---|---|---|
   | 1 | `EPIDEMIC` | 2020-04-20 | Ankara | `NEW_CASE`=15, `DEATH`=1, `RECOVERED`=5 |
   | 2 | `EARTHQUAKE` | 2020-05-03 | İzmir | `DAMAGED_BUILDING`=12, `DEATH`=2, `RESCUED`=9, `INJURED`=40 |
   | 3 | `TRAFFIC_ACCIDENT` | Gönderim tarihi — kaynak `RELATIVE` ("Son 24 saatte") | Bursa, Kocaeli | Bursa: `ACCIDENT_COUNT`=8, `DEATH`=1 · Kocaeli: `ACCIDENT_COUNT`=6, `DEATH`=2 · `INJURED`=10 (ile atanamaz → `SHARED` {Bursa, Kocaeli}) |

2. Örnek metinlerin cümleleri karıştırıldığında sonuç değişmez (FR-04).
3. Tanınmayan olay tipi içeren bir metin reddedilmez; `OTHER`/`UNCLASSIFIED` üretilir ve uyarılar olay kaydı sorgusunda döner (FR-09).
   - Ham bildirim kaydı, yazıldıktan sonra **hiçbir aşamada güncellenmez**; analiz sonucu `analysis` tarafında yaşar (FR-02, §5).
4. Bir olay kaydından kaynak ham metne, ham metinden türeyen kayıtlara ulaşılabilir (FR-08).
5. Filtreli listeleme, olay tipi bazlı zaman serisi ve kümülatif mod çalışır (FR-10, FR-11, FR-12).
6. Yeni bildirim girildiğinde bağlı SSE istemcisi olay alır (FR-13).
7. `docker compose up` ile sistem tek komutta ayağa kalkar (NFR-03).
8. JaCoCo raporu ≥ %80 ve eşik build'de zorunludur (NFR-02).
9. README kurulum/çalıştırma talimatlarını ve tasarım gerekçelerini içerir (NFR-10).
10. `docs/DECISIONS.md` tüm mimari kararları gerekçesi ve "İleride" notlarıyla birlikte içerir.

### Frontend kabul kriterleri

11. `docker compose up --build` sonrası **tarayıcıdan tek bir adres** açılarak sistemin tamamı kullanılabilir; ayrıca bir kurulum adımı gerekmez (NFR-03).
12. Üç örnek metin **arayüzden** girildiğinde her biri için sonuç aynı ekranda anında görünür; uyarılar okunur biçimde gösterilir (FR-18, FR-19, FR-20). Sonuç, gönderim cevabından değil, kimlikle yapılan sorgudan gelir.
13. İki tarayıcı sekmesi açıkken birinde girilen bildirim, diğerinde **sayfa yenilenmeden** listeye, özete ve grafiğe yansır. SSE bağlantısı kapatıldığında bile gönderimi yapan sekme kendi sonucunu görmeye devam eder (FR-19, FR-25).
14. Olay tipi seçilerek çizilen grafikte o tipin metrikleri ayrı seriler olarak görünür; kümülatif anahtarı açıldığında değerler birikimli hale gelir (FR-23).
15. Bursa ve Kocaeli birlikte seçildiğinde 3. örneğin `SHARED` yaralı toplamı **bir kez**, ayrı ve etiketli olarak görünür; hiçbir ile eklenmez ve kaybolmaz (FR-24, ADR-019).
16. Filtreler uygulanmış bir görünümün adresi kopyalanıp yeni sekmede açıldığında aynı görünüm gelir (FR-21).
17. Ham bildirim detayında ham metin değiştirilmeden gösterilir ve çıkarılan anahtar kelimeler metin üzerinde vurgulanır (FR-26, FR-02).
18. YAML kataloğuna yeni bir olay tipi eklenip sistem yeniden başlatıldığında, tip **frontend derlemesi değişmeden** filtre ve grafik seçeneklerinde görünür (FR-27, NFR-08, NFR-14).
19. Backend kapalıyken arayüz beyaz ekrana düşmez; anlaşılır bir hata ve tekrar deneme yolu gösterir (FR-28).
20. Frontend test kapsamı ≥ %80 ve eşik build'de zorunludur (NFR-02).

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
| Girilen veriler kullanıcıya tablo halinde gösterilir | FR-10 (veri) · **FR-21, FR-22** (arayüz) |
| Grafik olay tipine göre görüntülenir; seçilen tipin metrikleri gösterilir | FR-11 (veri) · **FR-23** (arayüz) |
| Veriler kümülatif olarak görülebilir | FR-12 (veri) · **FR-23** (arayüz) |
| Yeni bildirimde grafik ve tablolar sayfa yenilemeden güncellenir | FR-13 (yayın) · **FR-25** (arayüz) |
| Kullanıcı metni "sistemdeki bir metin alanına" girer (§1.1) | **FR-18** |
| Yeni bildirimde "grafik ve **özet tablolar** anlık olarak güncellenir" (§1.1) | **FR-22, FR-25** |
| Verilerin "zaman içinde, **coğrafi bölge bazında** grafiksel olarak izlenebilmesi" (§1.1) | **FR-24** |
| Sistemin bir **web uygulaması** olması (§1.1) | §1, §2.1, **FR-18…FR-28** |
| Örneklerde bold işaretli kelimelerin ipucu niteliği (§1.2) | FR-17 (saklama) · **FR-26** (metin üzerinde vurgulama) |

### Kaynak §1.4 — Teknik İsterler
| Kaynak maddesi | Karşılık |
|---|---|
| Backend Java (Spring Boot) ile geliştirilecektir | NFR-01 |
| Frontend ReactJS ile geliştirilecektir | **NFR-12** (`frontend/` modülü; React + TypeScript + Vite) |
| Veri katmanında hem PostgreSQL hem MongoDB kullanılacaktır | §5, FR-02, FR-08 |
| Ham metin Mongo'ya kaydedilir, log niteliğindedir | FR-02 |
| Bir kaydın hangi metinden üretildiği izlenebilmelidir | FR-08 |
| Parse sonucu veriler PostgreSQL'e kaydedilir; grafik ve listeleme bu katmandan | FR-08, FR-10, FR-11 |
| İki veri tabanındaki kayıtlar ilişkilendirilebilir olmalıdır | FR-08 |
| Birim testleri, kapsam en az %80 ve önemli fonksiyonları kapsayıcı | NFR-02 — **backend ve frontend için ayrı ayrı**; kaynak cümle backend'e daraltılmamıştır |
| Proje dockerize; `docker compose up` ile tek seferde ayağa kalkar | NFR-03 — **frontend dahil**; kök `docker-compose.yml` sistemin tamamını ayağa kaldırır |
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
| §10'daki TC-3…TC-11 ve TC-13…TC-18 | Task aşamasında karara bağlanacak; her karar `docs/DECISIONS.md`'e işlenecek (TC-1, TC-2 ve TC-12 karara bağlandı) |
| TC-12 kararının ADR'ye işlenmesi ve mevcut kodun uyarlanması | Karar verildi (§10, FR-19), ADR henüz yazılmadı. `ingestion` tarafında `RawReportAnalyzedEvent`, dinleyicisi, `ProcessingStatus` ve DTO alanları silinecek; `analysis` tarafına analiz sonucu kaydı eklenecek. **Paralel backend geliştirmesini doğrudan etkiler** |
| §8.2'deki C-1…C-7 sözleşme beklentileri | Backend task'larına (T-14, T-16, T-17, T-18) dahil edilecek. **Backend paralel geliştirildiği için erken bilinmesi gerekiyor**; bugün sözleşmede yoklar |
| Frontend ile API arasında aynı köken mi CORS mu | TC-17 kapsamında; karar ADR olarak kaydedilecek. Kök `docker-compose.yml` bugün frontend'e `VITE_API_BASE_URL` geçen (yani doğrudan çağrı → CORS gerektiren) bir taslak taşıyor; karar bu taslağı da bağlar |
| Grafik kütüphanesi ve durum/veri katmanı kütüphanesi seçimi | PRD kütüphane sabitlemez (NFR-12 yalnızca React + TypeScript + Vite'ı sabitler); seçim frontend iskelet task'ında ADR ile yapılacak |
| Göreli tarih aralıklarının modellenmesi ve zaman dilimi | TC-6 kapsamında. PRD, tarih kaynağını (`EXPLICIT`/`RELATIVE`/`DEFAULTED`) ve referans tarihin gönderim tarihi olduğunu sabitler (FR-06, ADR-014); aralık semantiği ve timezone kararı task aşamasına aittir |
| Haritalı görselleştirme | v1 kapsamı dışında (§2.2). İl bir kırılım boyutu olarak modellendiği için, ileride harita eklenmesi veri modelinde değişiklik gerektirmez |

---

## 14. Değişiklik Geçmişi

### v2.0 (2026-08-09) — Frontend kapsama alındı

**Neden.** v1.0, frontend'i "ayrı modül ve ayrı session" gerekçesiyle kapsam dışı bırakmıştı.
Kaynak dokümanın amaç cümlesi bir **web uygulaması** tarif ediyor; grafik, özet tablo, kümülatif
görünüm ve "sayfa yenilemeden güncelleme" isterlerinin tamamı ancak arayüzle doğrulanabilir.
Backend PRD'si bu isterleri *sunacak veriyi* tanımlıyordu, *isterin kendisini* değil.

**Eklenenler**

| Bölüm | Ekleme |
|---|---|
| §1, §2, §3, §4, §5 | Web uygulaması çerçevesi, frontend kapsamı, aktör tanımı, arayüz terimleri, §5.4 frontend mimarisi ve S-1…S-3 ekranları |
| §6B | **FR-18…FR-28** — giriş ekranı, anında sonuç, uyarı görünürlüğü, liste/filtre, özet tablo, grafik/kümülatif, il kırılımı, gerçek zamanlı güncelleme, izlenebilirlik/reprocess arayüzü, katalog beslemesi, durum davranışları |
| §8.2 | **C-1…C-7** — frontend'in backend sözleşmesinden beklentileri (bugün mevcut değil) |
| §9 | **NFR-12…NFR-16**; NFR-02, NFR-03 ve NFR-10 frontend'i kapsayacak şekilde genişletildi |
| §10 | **TC-12…TC-18** — gönderim sonrası sonucun getirilmesi *(karara bağlandı)*, agregasyon tazeleme, `SHARED` temsili, istemci durumu, frontend kapsamı, dağıtım/CORS, vurgulama hizalaması |
| §11 | **11…20** numaralı frontend kabul kriterleri |
| §12 | Kaynak §1.1'den daha önce matrise girmemiş dört madde (web uygulaması, metin alanı, özet tablolar, coğrafi bölge) |

**Değişenler.** §2.2'den "Frontend (ReactJS) → kapsam dışı" satırı kaldırıldı; harita satırı
gerekçelendirilerek korundu. §12'de "Frontend ReactJS ile geliştirilecektir" satırı artık NFR-12'ye
işaret ediyor.

**Modül sahipliği kararı (Model 2).** v2.0, frontend'den bağımsız olarak bir mimari kararı da
taşıyor: **her modül yalnızca sahibi olduğu veriyi yayınlar.** Analiz sonucu (durum, uyarılar)
`analysis`'e aittir; `analysis`'ten `ingestion`'a dönüş event'i kaldırılır ve ham döküman
write-once olur. `POST` yalnızca makbuz döner, sonuç sorguyla okunur, SSE tazeleme tetikleyicisi
olur. Etkilenen isterler: FR-02, FR-09, FR-13, FR-14, FR-15, FR-19, FR-20, FR-25; §5 mimari
kısıtları; §8 ve §8.2; TC-12.

*Bu karar mevcut backend kodunda geri dönüş doğuruyor:* `RawReportAnalyzedEvent`, dinleyicisi,
`IngestionService.markAnalyzed`, `ProcessingStatus` ve `IncidentReportResponse`'un iki alanı
silinecek; `analysis` tarafına analiz sonucu kaydı eklenecek. ADR-003 revize edilecek ve karar
yeni bir ADR olarak yazılacak.

**Değişmeyenler.** FR-01, FR-03…FR-08, FR-10…FR-12, FR-16, FR-17 ve ADR-001, ADR-002, ADR-004…ADR-020
aynen geçerlidir. Bölüm numaraları korunmuştur (`CLAUDE.md` ve `docs/TASKS.md` §10/§11'e referans
verdiği için); frontend isterleri bu yüzden §6'nın devamı olarak "§6B" başlığı altındadır.

### v1.0 (2026-08-08) — İlk sürüm
Yalnızca backend kapsamı; FR-01…FR-17, NFR-01…NFR-11, TC-1…TC-11.
