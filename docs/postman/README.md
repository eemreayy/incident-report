# Postman Koleksiyonu

Backend API'sini frontend olmadan denemek için hazırlanmış koleksiyon.

| Dosya | İçerik |
|---|---|
| `incident-report.postman_collection.json` | 13 istek, 4 klasör, her istekte örnek cevap ve assertion |

Koleksiyondaki **her örnek cevap çalışan sistemden yakalanmıştır**, elle yazılmamıştır.

---

## Kullanım

### 1. Sistemi ayağa kaldırın

Repo kökünde:

```bash
docker compose up --build
```

### 2. Koleksiyonu içe aktarın

Postman → **Import** → `docs/postman/incident-report.postman_collection.json`

Ayrı bir environment kurmanıza gerek yok; `baseUrl` koleksiyon değişkeni olarak
`http://localhost:8080` değerini taşıyor. `APP_PORT`'u değiştirdiyseniz yalnızca onu güncelleyin.

### 3. Deneyin

**Olay Bildirimleri → Submit** isteklerinden birini gönderin. Test script'i dönen id'yi `reportId`
koleksiyon değişkenine yazıyor; böylece **Read one** ve hata senaryoları kopyala-yapıştır olmadan
çalışıyor.

Ya da koleksiyonun tamamında **Run**'a basın — her istek assertion taşıdığı için koleksiyon aynı
zamanda API'nin duman testi olarak çalışır.

---

## Klasörler

### Health
Uygulamanın ve **her iki veri tabanının** ayrı ayrı sağlık durumu. Bir arıza olduğunda neyin
bozulduğunu gösterir.

### Catalog
`GET /api/v1/metadata` — sistemin tanıdığı olay tipleri, metrikleri ve 81 il. Arayüzdeki her
seçeneğin tek kaynağı burası; frontend'in kendi kataloğu yok. Yeni olay tipi eklemek yalnızca
`backend/analysis/src/main/resources/incident-catalog.yml` dosyasını değiştirmek demek.

### Incident Reports
Ham metin gönderme ve geri okuma. Üç Submit isteği, kaynak dokümandaki **üç örnek metni** taşır:

| İstek | Metin | Neden önemli |
|---|---|---|
| sample 1 | Ankara, salgın | En düz durum: tek il, açık tarih |
| sample 2 | İzmir, deprem | Sayılar yazıyla — `on iki`, `dokuz` |
| sample 3 | Bursa/Kocaeli, trafik | İki il + **hiçbir ile ait olmayan** bir sayı, ve göreli tarih |

Üçüncüsü veri modelinin bugünkü halini belirleyen metin. Ayrıntısı için
[ADR-019](../DECISIONS.md#adr-019--kayıt-granülaritesi).

**Update ve delete yok, olmayacak da.** Ham metin log niteliğinde; düzenlenebilen bir kayıt,
ondan türeyen veriyi açıklayamaz.

### Error contract (RFC 7807)
Altı hata senaryosu. Hepsi `application/problem+json` döner — hata ister domain'den, ister
Spring'in kendisinden gelsin.

İstemcinin dayanabileceği alan `code`; `detail` metni serbestçe değişebilir. Her istek ayrıca
**hiçbir iç detayın sızmadığını** doğruluyor: istisna tipi, stack frame, bağlantı dizesi yok.

---

## Komut satırından çalıştırma

```bash
npx newman run docs/postman/incident-report.postman_collection.json
```

Beklenen: **13 istek, 54 assertion, 0 hata.**

---

## Gönderim makbuz döner, sonuç değil

`POST /incident-reports` yalnızca kaydın kimliğini ve geliş zamanını döner:

```json
{ "id": "6a786821fbb52fd08cf46a37", "submittedAt": "2026-08-09T11:44:33.512Z" }
```

Analizin ne bulduğu burada **yok**. O veri onu üreten modüle ait ve ayrı okunuyor:
`GET /incidents?rawReportId=...` (T-16). Gerekçe [ADR-021](../DECISIONS.md#adr-021--analiz-sonucunun-sahipliği-ve-gönderim-cevabının-kapsamı)'de.

Bedeli bir ek istek. Karşılığında analiz ileride istek thread'inden çıkarılsa ya da bir broker'a
taşınsa **hiçbir istemci sözleşmesi değişmiyor**: sorgu bir süre "henüz analiz edilmedi" der, canlı
akış da ne zaman değiştiğini söyler.

Metin çıkarımının kendisi henüz yazılmadı (T-08…T-14); her bildirim şu an `OTHER` / `UNCLASSIFIED`
olarak kaydediliyor ve nedenini söyleyen uyarılar üretiliyor. Bu bir yer tutucu değil, sistemin
**tanımadığı metin için gerçek davranışı** (ADR-006). Ancak bunların hiçbiri bu cevaplarda
görünmüyor — onları sunacak uç henüz yok.

---

## Koleksiyonda henüz olmayan uçlar

Tasarlandı ama yazılmadı; eklenseler 404 dönerlerdi:

| Uç | Ne yapacak | Task |
|---|---|---|
| `GET /api/v1/incidents` | Yapılandırılmış kayıtlar, filtreli ve sayfalı | T-16 |
| `GET /api/v1/analytics/time-series` · `/summary` | Grafik verisi, kümülatif dahil | T-17 |
| `GET /api/v1/stream/incidents` | SSE akışı | T-18 |
| `POST /api/v1/incident-reports/{id}/reprocess` | Yeniden analiz | T-19 |

Ayrıntı için [`docs/TASKS.md`](../TASKS.md).

---

## Bakım notu

Uçlar eklendikçe koleksiyon da büyümeli. Örnek cevapları elle yazmak yerine çalışan sistemden
yakalamak, koleksiyonun gerçeği göstermesini garanti eder; `npx newman run` ile doğrulamak da
koleksiyonun API'den geri kalmadığını gösterir.
