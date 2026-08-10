# Frontend

React arayüzü. İskeleti **T-23** ile kuruldu; ekranlar T-25'ten itibaren geliyor
([`docs/TASKS.md`](../docs/TASKS.md) Faz 7).

## Teknoloji

React + **TypeScript** + **Vite** ([ADR-022](../docs/DECISIONS.md#adr-022--frontend-teknoloji-tabanı-react--typescript--vite)), SSR yok.
Kütüphane seti ([ADR-026](../docs/DECISIONS.md#adr-026--frontend-kütüphane-seti)):

| Amaç | Seçim | Neden |
|---|---|---|
| Sunucu verisi | TanStack Query | Önbellek + stale-while-revalidate hazır; SSE sinyali `invalidateQueries`'e iniyor |
| Yönlendirme / URL durumu | React Router | Filtreler URL'de yaşayacak (FR-21), detay ekranları adreslenebilir |
| Grafik | Recharts | **SVG çiziyor** → grafik mock'suz test edilebilir (ADR-024'ün kapısı altında belirleyici) |
| Test | Vitest + Testing Library | Vite ile aynı dönüşüm hattı |
| Stil | Düz CSS | Tek panel; bir çatı hiçbir kararı ortadan kaldırmıyor |

## Komutlar

```bash
npm install
npm run dev       # Vite dev sunucusu :3000, /api isteklerini :8080'e proxy'ler
npm run verify    # lint + tip kontrolü + build + coverage kapısı
npm test          # yalnızca testler
npm run build     # tsc --noEmit && vite build
```

`npm run verify` **coverage eşiğinin altında build'i kırar** (satır bazında %80). Kapı fiilen
doğrulandı: kapsanmayan bir dosya eklendiğinde çıkış kodu 1 oluyor.

> Sayıyı okurken: gövdesi tek bir JSX `return`'ü olan bir bileşen **tek satır** sayılıyor. Yani
> oran mantık dosyalarından oluşuyor; görünümleri tutan şey oran değil, davranış testleridir.

## Ne yapıyor

Kullanıcı tek bir metin alanına serbest metin girer; sistem tarih, il, olay tipi ve sayısal
metrikleri çıkarır. Arayüz bu veriyi filtrelenebilir tablo, il kırılımlı özet ve olay tipi bazlı
(opsiyonel kümülatif) grafik olarak sunar; yeni bildirim girildiğinde görünüm sayfa yenilenmeden
tazelenir.

Ekranlar ([`docs/PRD.md`](../docs/PRD.md) §5.4):

| Kod | Ekran |
|---|---|
| **S-1** | Panel — giriş formu · filtre çubuğu · grafik · özet tablo · kayıt listesi · akış göstergesi |
| **S-2** | Olay kaydı detayı — metrikler, anahtar kelimeler, tarih kaynağı, kapsam, kaynak bildirim |
| **S-3** | Ham bildirim detayı — değiştirilmemiş metin, vurgulanmış anahtar kelimeler, türeyen kayıtlar, reprocess |

İsterler PRD §6B'de (FR-18…FR-28), task kırılımı `docs/TASKS.md` Faz 7'de (T-23…T-31).

## Uyulacak kısıtlar

Bunlar tercih değil, karar:

1. **Veri akışı.** Gönderim isteği yalnızca **kayıt makbuzu** döner (kimlik + gönderim zamanı).
   Ne çıkarıldığı `GET /incidents?rawReportId=...` ile sorgulanır. SSE bir **tazeleme
   tetikleyicisidir, veri kaynağı değildir** — akış koptuğunda hiçbir veri erişilemez olmaz.
   Bkz. [ADR-021](../docs/DECISIONS.md#adr-021--analiz-sonucunun-sahipliği-ve-gönderim-cevabının-kapsamı).
2. **İş kuralı burada kopyalanmaz.** Kümülatif toplama, agregasyon ve filtreleme sunucuda yapılır;
   arayüz gelen sayıyı çizer. Aynı kuralın iki dilde iki kopyası zamanla birbirinden kayar.
3. **Katalog sabit yazılmaz.** Olay tipi, metrik ve il seçenekleri yalnızca metadata ucundan gelir.
   YAML'a eklenen bir tip, frontend derlemesi değişmeden seçeneklerde görünmelidir.
4. **`SHARED` kapsam.** Birden fazla ile ait, ayrıştırılamayan toplamlar hiçbir ile eklenmez,
   düşürülmez ve çift sayılmaz; il satırlarıyla **aynı tabloda**, kendi satırında ve kelimeyle
   etiketli gösterilir ([ADR-019](../docs/DECISIONS.md#adr-019--kayıt-granülaritesi),
   [ADR-038](../docs/DECISIONS.md#adr-038--shared-ve-unknown-kapsamın-arayüzdeki-temsili-aynı-tabloda-kendi-satırında-adıyla)).
   Toplamlar da sunucudan geldiği gibi basılır: satırları toplayan bir arayüz, paylaşılan figür
   varken farklı ve yanlış — ama tutarlı görünen — bir sayı üretir.
5. **Coverage kapısı %80**, build'i kırar — backend'dekiyle simetrik
   ([ADR-024](../docs/DECISIONS.md#adr-024--frontend-coverage-kapısı)). Snapshot testi sayıyı
   ucuza şişirir; ölçülen şey davranış olmalı.
6. **Dil.** Kullanıcıya görünen arayüz metinleri Türkçedir; kod, tanımlayıcılar, yorumlar ve commit
   mesajları İngilizce. Türkçe metinler tek yerde toplanır, bileşenlerin içine serpiştirilmez.
7. **Tazeleme sırasında görünüm boşaltılmaz.** Yeni veri gelene kadar eski veri ekranda kalır;
   aksi halde her sinyalde tablo bir an boşalır ve bu, kullanıcı gözünde sayfa yenilenmesidir.
   Akıştan gelen sinyal ekrana hiçbir şey yazmaz; sorguları geçersizleştirir ve onlar kendini
   yeniden getirir ([ADR-040](../docs/DECISIONS.md#adr-040--canlı-tazeleme-sinyal-geçersizleştirir-delta-uygulamaz-pencereli-birleştirme-ve-kanıtlanmış-i̇lgisizlikte-atlama)).
8. **Görünüm durumunun tek kopyası adres çubuğudur** — store, context ya da `useState` kopyası yok
   ([ADR-037](../docs/DECISIONS.md#adr-037--filtre-durumunun-tek-kaynağı-adres-çubuğu)). Filtreye
   bakan her görünüm `useIncidentFilters`'ı çağırır; birbirlerine prop geçmezler. Çözümleme
   kanoniktir, çünkü sorgu önbelleğinin anahtarı da odur. Grafik kendi ayarlarını (`chart`,
   `metric`, `breakdown`, `cumulative`) aynı adres çubuğunda **ayrı anahtarlarda** tutar
   ([ADR-039](../docs/DECISIONS.md#adr-039--grafiğin-iki-modu-grafik-ayarlarının-adres-çubuğunda-yaşaması-ve-kümülatifin-sunucudan-i̇stenmesi));
   her modül yalnızca kendi anahtarlarını yeniden yazar, yoksa biri diğerini sıfırlar.

## Çalıştırma ve API erişimi

Kök dizinde `docker compose up --build` → **http://localhost:3000**. Frontend container'ı statik
dosyaları nginx ile sunar ve `/api/*` ile `/actuator/health` isteklerini compose ağı üzerinden
`backend:8080`'e proxy'ler.

**TC-17 karara bağlandı: aynı köken** ([ADR-025](../docs/DECISIONS.md#adr-025--aynı-köken-nginx-reverse-proxy-cors-yerine)).
Sonuçları:

- Backend'de **CORS yapılandırması yok**; kaynak kodda **mutlak API adresi yok**. `VITE_API_BASE_URL`
  diye bir ayar da yok — istekler göreli. Bir ayar eklemek bu kararı geri almak olur.
- Geliştirmede aynı davranışı Vite dev proxy'si veriyor, yani `npm run dev` ile Docker aynı şekilde
  çalışıyor.
- [`nginx.conf`](nginx.conf)'taki en kritik satır **`proxy_buffering off`**: SSE için kapatılmazsa
  nginx olayları tamponlar ve akış hiçbir hata vermeden ölü görünür. **T-29'da çalışırken
  doğrulandı** — Docker'da, nginx üzerinden, başka bir istemciden girilen bildirim tarayıcıda sayfa
  yenilenmeden göründü.
- Aynı doğrulama beklenmeyen bir şey de gösterdi: backend durduğunda bağlantı reddedilmiyor, nginx
  **502** döndürüyor ve `EventSource` bunu ölümcül sayıp bir daha denemiyor. Bu yüzden yeniden
  bağlanma arayüzün kendi işi
  ([ADR-040](../docs/DECISIONS.md#adr-040--canlı-tazeleme-sinyal-geçersizleştirir-delta-uygulamaz-pencereli-birleştirme-ve-kanıtlanmış-i̇lgisizlikte-atlama)).

Backend API sözleşmesi için [`docs/PRD.md`](../docs/PRD.md) §8 ve §8.2'ye bakın. §8.2, frontend'in
sözleşmeden beklediği ve **bugün henüz mevcut olmayan** maddeleri (C-1…C-8) listeler.
