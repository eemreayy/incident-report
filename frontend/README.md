# Frontend

React arayüzü. **Henüz oluşturulmadı** — iskeleti [`docs/TASKS.md`](../docs/TASKS.md) içindeki
**T-23** kuracak. Bu dosya, o task başlamadan önce verilmiş kararları ve uyulacak kısıtları taşır.

## Teknoloji

React + **TypeScript** + **Vite** — [ADR-022](../docs/DECISIONS.md#adr-022--frontend-teknoloji-tabanı-react--typescript--vite).
SSR yok. Grafik ve veri katmanı kütüphaneleri bilinçli olarak açık bırakıldı; T-23'te seçilip aynı
dosyaya ADR olarak yazılacak.

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
   düşürülmez ve çift sayılmaz; ayrı ve **etiketli** bir satır/seri olarak gösterilir
   ([ADR-019](../docs/DECISIONS.md#adr-019--kayıt-granülaritesi)).
5. **Coverage kapısı %80**, build'i kırar — backend'dekiyle simetrik
   ([ADR-024](../docs/DECISIONS.md#adr-024--frontend-coverage-kapısı)). Snapshot testi sayıyı
   ucuza şişirir; ölçülen şey davranış olmalı.
6. **Dil.** Kullanıcıya görünen arayüz metinleri Türkçedir; kod, tanımlayıcılar, yorumlar ve commit
   mesajları İngilizce. Türkçe metinler tek yerde toplanır, bileşenlerin içine serpiştirilmez.
7. **Tazeleme sırasında görünüm boşaltılmaz.** Yeni veri gelene kadar eski veri ekranda kalır;
   aksi halde her sinyalde tablo bir an boşalır ve bu, kullanıcı gözünde sayfa yenilenmesidir.

## Çalıştırma

T-23 tamamlandığında kök dizindeki `docker compose up --build` frontend'i de ayağa kaldıracak:
kök [`docker-compose.yml`](../docker-compose.yml) içinde yorum satırı olarak bekleyen `frontend`
servisi açılır ve backend'in `service_healthy` durumuna bağlanır.

Tarayıcı ile API arasında **aynı köken (reverse proxy)** mi yoksa **CORS** mu kullanılacağı henüz
karara bağlanmadı (PRD §10, TC-17) — kararı T-23 verecek ve compose'daki taslak ona göre
kesinleşecek. Bugünkü yorum satırı doğrudan çağrıyı (dolayısıyla CORS'u) varsayıyor; bu bir
taahhüt değil, yer tutucudur.

Backend API sözleşmesi için [`docs/PRD.md`](../docs/PRD.md) §8 ve §8.2'ye bakın. §8.2, frontend'in
sözleşmeden beklediği ve **bugün henüz mevcut olmayan** maddeleri (C-1…C-8) listeler.
