# Tasarım Kararları ve Gerekçeleri

Bu dosya projede alınan mimari ve teknoloji kararlarını, **neden** alındıklarını, hangi alternatiflerin neden elenediğini ve katlanılan trade-off'ları kayıt altına alır. Her kararın sonunda bir **İleride** bölümü vardır: bugünkü tercihin gelecekte hangi yöne evrilebileceğini ve o evrilmenin neden bugünkü tasarımla kolaylaştığını anlatır.

**Kural:** Mimari veya teknoloji seçimi değişirse bu dosya aynı commit içinde güncellenir.

**Şablon:** Karar · Bağlam · Gerekçe · Alternatifler · Sonuçlar (trade-off) · İleride

| No | Karar | Durum |
|---|---|---|
| [ADR-001](#adr-001--modular-monolith) | Modular monolith | Kabul edildi |
| [ADR-002](#adr-002--iki-veri-tabanının-rol-ayrımı) | İki veri tabanının rol ayrımı | Kabul edildi |
| [ADR-003](#adr-003--modüller-arası-senkron-spring-application-event) | Modüller arası senkron Spring Application Event | Kabul edildi · **[ADR-021](#adr-021--analiz-sonucunun-sahipliği-ve-gönderim-cevabının-kapsamı) ile revize edildi** |
| [ADR-004](#adr-004--gerçek-zamanlı-bildirim-için-sse) | Gerçek zamanlı bildirim için SSE | Kabul edildi · v2.0'da netleştirildi |
| [ADR-005](#adr-005--ham-kaydın-değiştirilemez-olması) | Ham kaydın değiştirilemez olması | Kabul edildi · v2.0'da güçlendirildi |
| [ADR-006](#adr-006--tanınmayan-olay-tipi-davranışı) | Tanınmayan olay tipi davranışı | Kabul edildi |
| [ADR-007](#adr-007--konfigürasyondan-yönetilen-olay-kataloğu) | Konfigürasyondan yönetilen olay kataloğu | Kabul edildi |
| [ADR-008](#adr-008--kuralregex-tabanlı-çıkarım-ml-yerine) | Kural/regex tabanlı çıkarım (ML yerine) | Kabul edildi |
| [ADR-009](#adr-009--java-21--spring-boot-35x) | Java 21 + Spring Boot 3.5.x | Kabul edildi |
| [ADR-010](#adr-010--tek-instance-veri-tabanları) | Tek instance veri tabanları | Kabul edildi |
| [ADR-011](#adr-011--kimlik-doğrulamanın-kapsam-dışı-bırakılması) | Kimlik doğrulamanın kapsam dışı bırakılması | Kabul edildi |
| [ADR-012](#adr-012--reprocess-yeteneği) | Reprocess yeteneği | Kabul edildi |
| [ADR-013](#adr-013--maven--flyway--openapi) | Maven + Flyway + OpenAPI | Kabul edildi |
| [ADR-014](#adr-014--tarih-çözümleme-ve-referans-tarih) | Tarih çözümleme ve referans tarih | Kabul edildi |
| [ADR-015](#adr-015--üç-repoluk-yapı-ve-ayrı-devops-reposu) | Üç repo'luk yapı ve ayrı devops repo'su | **Geçersiz** — yerini [ADR-016](#adr-016--tek-repo-monorepo) aldı |
| [ADR-016](#adr-016--tek-repo-monorepo) | Tek repo (monorepo) | Kabul edildi |
| [ADR-017](#adr-017--mimari-kurallar-için-archunit-spring-modulith-yerine) | Mimari kurallar için ArchUnit (Spring Modulith yerine) | Kabul edildi |
| [ADR-018](#adr-018--coverage-kapısı-modül-başına-eşik--proje-geneli-rapor) | Coverage kapısı: modül başına eşik + proje geneli rapor | Kabul edildi |
| [ADR-019](#adr-019--kayıt-granülaritesi) | Kayıt granülaritesi: (ham bildirim, tarih, il, olay tipi) | Kabul edildi · **TC-1 çözüldü** |
| [ADR-020](#adr-020--metrik-veri-modeli-metrik-başına-satır) | Metrik veri modeli: metrik başına satır | Kabul edildi · **TC-2 çözüldü** |
| [ADR-021](#adr-021--analiz-sonucunun-sahipliği-ve-gönderim-cevabının-kapsamı) | Analiz sonucunun sahipliği ve gönderim cevabının kapsamı | Kabul edildi · **TC-12 çözüldü** |
| [ADR-022](#adr-022--frontend-teknoloji-tabanı-react--typescript--vite) | Frontend teknoloji tabanı: React + TypeScript + Vite | Kabul edildi |
| [ADR-023](#adr-023--coğrafi-izlenebilirlik-harita-yerine-il-kırılımı) | Coğrafi izlenebilirlik: harita yerine il kırılımı | Kabul edildi |
| [ADR-024](#adr-024--frontend-coverage-kapısı) | Frontend coverage kapısı | Kabul edildi |
| [ADR-025](#adr-025--aynı-köken-nginx-reverse-proxy-cors-yerine) | Aynı köken: nginx reverse proxy (CORS yerine) | Kabul edildi · **TC-17 çözüldü** |
| [ADR-026](#adr-026--frontend-kütüphane-seti) | Frontend kütüphane seti | Kabul edildi |
| [ADR-027](#adr-027--türkçe-normalizasyon-konum-koruyan-metin-ve-elle-yazılmış-cümle-bölücü) | Türkçe normalizasyon: konum koruyan metin, elle yazılmış cümle bölücü | Kabul edildi · **TC-5 çözüldü** |
| [ADR-028](#adr-028--sayı-ayrıştırma-bileşik-sözcükler-tarihlerin-dışlanması-ve-okunamayan-figürler) | Sayı ayrıştırma: bileşik sözcükler, tarihlerin dışlanması | Kabul edildi · **TC-4 çözüldü** |
| [ADR-029](#adr-029--zaman-dilimi-gün-sınırı-ve-göreli-aralıkların-tek-güne-indirgenmesi) | Zaman dilimi, gün sınırı ve göreli aralıklar | Kabul edildi · **TC-6 çözüldü** |
| [ADR-030](#adr-030--i̇l-tanıma-referans-veriden-beslenme-sayılı-ekler-ve-ilçe-ayrımı) | İl tanıma: referans veriden beslenme, ek ve ilçe ayrımı | Kabul edildi · **TC-7 çözüldü** |
| [ADR-031](#adr-031--sınıflandırma-tek-anahtar-kelime-eşiği-sayısal-güven-yerine-kanıt-çoklu-tip) | Sınıflandırma: tek anahtar kelime eşiği, kanıt, çoklu tip | Kabul edildi · **TC-8 çözüldü** |
| [ADR-032](#adr-032--sayı--metrik-eşleştirme-ve-il-kapsamının-belirlenmesi) | Sayı ↔ metrik eşleştirme ve il kapsamının belirlenmesi | Kabul edildi · **TC-3 çözüldü** |
| [ADR-033](#adr-033--okuma-ucunun-şekli-kapsam-filtresi-uç-seviyesinde-analiz-sonucu-ve-dto-döndüren-okuma-servisi) | Okuma ucunun şekli: kapsam filtresi, uç seviyesinde analiz sonucu | Kabul edildi |
| [ADR-034](#adr-034--canlı-akışın-yaşam-döngüsü-rapor-başına-sinyal-commit-sonrası-yayın-heartbeat-ile-temizlik) | Canlı akışın yaşam döngüsü: rapor başına sinyal, commit sonrası yayın | Kabul edildi · **TC-10 çözüldü** |
| [ADR-035](#adr-035--yeniden-i̇şleme-ve-mükerrer-gönderim-aynı-metin-i̇kinci-kayıt-açmaz) | Yeniden işleme ve mükerrer gönderim: aynı metin ikinci kayıt açmaz | Kabul edildi · **TC-9 çözüldü** |
| [ADR-036](#adr-036--agregasyon-uçlarının-şekli-seri-olarak-cevap-exists-ile-filtre-tek-sorguda-üç-seviye) | Agregasyon uçlarının şekli: seri olarak cevap, `EXISTS` ile filtre, tek sorguda üç seviye | Kabul edildi |

---

## ADR-001 — Modular Monolith

**Karar.** Backend, tek bir deploy edilebilir Spring Boot uygulaması olarak, ancak içinde net sınırları olan iki çekirdek modülle (`ingestion`, `analysis`) ve ince bir `realtime` katmanıyla geliştirilecek. Modüller **ayrı Maven modülleri** olarak, her biri kendi `pom.xml`'i ile hayata geçirilecek:

Maven reactor'ın kökü, monorepo'daki `backend/` dizinidir (bkz. ADR-016):

```
backend/             (parent, packaging=pom)
├── shared           hiçbir modüle bağımlı değil — modüller arası event'ler, hata sözleşmesi
├── ingestion        → shared        MongoDB'ye sahip
├── analysis         → shared        PostgreSQL'e sahip
├── realtime         → shared        SSE taşıma katmanı, veri tabanı yok
└── app              → hepsi         tek deploy edilebilir artifact
```

`ingestion` ile `analysis` arasında **bilinçli olarak bağımlılık yoktur**.

**Bağlam.** Sistemin iki farklı sorumluluğu var: ham metni almak/saklamak ve metni analiz edip analitik veri üretmek. Bunlar farklı veri tabanları, farklı değişim hızları ve farklı test stratejileri gerektiriyor.

**Gerekçe.**
- Sorumluluklar başından net ayrıldığı için kod tabanı büyüdükçe karışmıyor.
- Tek deploy artifact'ı: `docker compose up` ile tek komutta ayağa kalkma isterini (NFR-03) doğrudan destekliyor.
- Dağıtık sistem maliyetleri (ağ hatası, kısmi başarısızlık, dağıtık transaction, servis keşfi) bu ölçekte hiçbir fayda getirmeden karmaşıklık ekleyecekti.
- **Ayrı Maven modülleri, sınırı derleme zamanında zorunlu kılıyor.** Tek `src` altındaki paketlerde "`ingestion`, `analysis`'e dokunmasın" bir konvansiyondur; bağımlılık grafiğinde o kenar hiç yoksa, ihlal bir derleme hatasıdır. Ayrıca her modülün kütüphaneleri kendi pom'unda durduğu için, `analysis`'in Mongo sürücüsüne erişimi *fiilen* yoktur.

**Alternatifler.**
- *Mikroservisler:* İki servis + mesaj altyapısı. Bu kapsam için operasyonel maliyeti faydasından çok daha yüksek; değerlendirme projesinde tek komutla ayağa kaldırma isterini de zorlaştırırdı.
- *Katmanlı (teknik katman bazlı) monolit:* `controller/service/repository` paketleri. Domain sınırları görünmez olur, iki veri tabanının rol ayrımı kod içinde kaybolur.
- *Tek modül, paketlerle ayrılmış sınırlar:* Kurulum daha basit olurdu; ama sınır yalnızca konvansiyon olarak kalır ve ihlali ancak ek bir araçla (ArchUnit vb.) yakalanır. Proje bu yaklaşımla başladı ve bilinçli olarak çok modüllü yapıya taşındı.

**Sonuçlar.** Build daha ayrıntılı: beş `pom.xml` ve bir reactor. Modüller arası refactoring tek `src`'ye göre daha çok dokunuş gerektiriyor. Buna karşılık `ingestion`'dan `analysis`'e erişim denemesi `package ... does not exist` ile derlemede kırılıyor — NFR-05'in büyük kısmı artık test değil, build garantisi. Kalan sınır kuralları (ör. controller'ın entity sızdırmaması) için ek doğrulama testi yine gerekli (T-03).

**İleride.** Modüller kendi veri tabanına sahip, birbirine yalnızca event ve açık API ile bağlı olduğu için mikroservise geçiş kararı alınırsa bölünme hattı zaten çizilmiş olacak: `analysis` modülü kendi Postgres'i ve kendi API'siyle olduğu gibi ayrı bir servise taşınabilir. O noktada değişmesi gereken tek şey, bugün süreç içi olan event yayınının ağ üzerinden yapılması (bkz. ADR-003).

---

## ADR-002 — İki Veri Tabanının Rol Ayrımı

**Karar.** MongoDB yalnızca **ham metni** (log/audit), PostgreSQL yalnızca **normalize/analitik veriyi** tutar. `ingestion` modülü Postgres'e, `analysis` modülü Mongo'ya erişmez.

**Bağlam.** Kaynak doküman her iki veri tabanının da kullanılmasını, ham metnin Mongo'da log niteliğinde saklanmasını, parse çıktısının Postgres'te olmasını ve iki taraftaki kayıtların ilişkilendirilebilmesini istiyor.

**Gerekçe.**
- Ham metin şemasızdır, yalnızca yazılır ve kimliğiyle okunur — döküman deposu bu erişim şekline uygun.
- Normalize veri üzerinde filtreleme, gruplama, zaman serisi ve kümülatif toplam sorguları çalışacak — ilişkisel model ve SQL agregasyonları bu iş için doğru araç.
- Ayrım aynı zamanda modül sınırını fiziksel olarak da güçlendiriyor: bir modülün diğerinin verisine "kestirmeden" ulaşması mümkün değil.

**Alternatifler.**
- *Tek veri tabanı:* Daha basit olurdu ama kaynak dokümanın açık teknik isterine aykırı.
- *Ham metni de Postgres'te tutmak:* Log/audit kaydının analitik şemayla aynı yaşam döngüsüne bağlanmasına ve gereksiz şema baskısına yol açardı.

**Sonuçlar.** İki veri tabanı arasında transaction bütünlüğü yok; ham metin yazıldıktan sonra analiz başarısız olabilir. Bu bilinçli olarak kabul ediliyor — ham metin her koşulda korunuyor, başarısız kayıt işaretlenip yeniden işlenebiliyor (ADR-012). İzlenebilirlik uygulama seviyesinde, iki yönlü referansla sağlanıyor (FR-08).

**İleride.** Ham metin hacmi büyüdüğünde Mongo tarafında TTL/arşivleme politikası, Postgres tarafında ise tarih bazlı partitioning devreye alınabilir. İki depo arasındaki tutarlılık bugün uygulama seviyesinde; ihtiyaç halinde outbox pattern'e taşınabilir (bkz. ADR-003).

---

## ADR-003 — Modüller Arası Senkron Spring Application Event

> **Revize edildi (PRD v2.0 · [ADR-021](#adr-021--analiz-sonucunun-sahipliği-ve-gönderim-cevabının-kapsamı)).** Aşağıdaki gerekçenin ikinci maddesi — *"senkron olması, kullanıcının bildirimi gönderir göndermez sonucu ve uyarıları görmesini sağlıyor"* — **artık geçerli değil**: gönderim cevabı analiz sonucunu taşımıyor ve `analysis`'ten `ingestion`'a dönüş event'i kaldırıldı. Event'in senkron olması korunuyor; değişen şey, senkronluğun **istemci sözleşmesinin parçası olmaktan çıkması**. Aşağıdaki "İleride" merdiveni ancak bu değişiklikle gerçekten tırmanılabilir hale geliyor.

**Karar.** `ingestion` modülü ham metni kaydettikten sonra bir domain event yayınlar; `analysis` modülü bu event'i **senkron** olarak dinler ve analizi aynı istek içinde tamamlar. Süreç içi `ApplicationEventPublisher` kullanılır.

**Bağlam.** İki modülün birbirini doğrudan çağırmaması ama kullanıcının gönderim sonucunu (analiz özeti ve uyarılar) hemen görebilmesi isteniyor.

**Gerekçe.**
- Event, modüller arasında derleme zamanı bağımlılığı yaratmadan iletişim kurmanın en hafif yolu: `ingestion` kimin dinlediğini bilmiyor.
- Senkron olması, kullanıcının bildirimi gönderir göndermez sonucu ve uyarıları (özellikle FR-09) görmesini sağlıyor.
- Ek altyapı (broker, kuyruk) gerektirmiyor; tek komutla ayağa kalkma isterini bozmuyor.

**Alternatifler.**
- *Doğrudan servis çağrısı:* En basit, ama modüller arasında sert bağımlılık kurar ve ileride ayrıştırmayı zorlaştırır.
- *Kafka/RabbitMQ:* Dayanıklılık ve geri basınç yönetimi kazandırırdı; bu ölçekte gereksiz operasyonel yük ve `docker compose` kurulumuna fazladan servis demek.
- *Asenkron in-process event (`@Async`):* Kullanıcı sonucu anında göremezdi; hata yönetimi ve test edilebilirlik zorlaşırdı.

**Sonuçlar.** Analiz süresi API yanıt süresine dahil. Analiz hatası ham metnin kaydını iptal etmemeli — bu yüzden analiz, ham metin kaydının transaction'ından ayrı ele alınacak. Ayrıca senkron olduğu için geriye basınç (backpressure) mekanizması yok; yoğun yükte istek süresi uzar.

**İleride.** Event sözleşmesi (yayımlanan domain event tipi) zaten var olduğu için, ileride bu yapı kademeli olarak evrilebilir:
1. **Asenkron in-process** — dinleyici `@Async` ile ayrılır; API hemen 202 döner.
2. **Transactional outbox** — event, ham kayıtla aynı transaction'da bir outbox'a yazılır; ayrı bir yayıcı gerçek broker'a iter. "En az bir kez" teslim garantisi kazanılır.
3. **CDC pipeline** — Mongo change stream / Debezium ile ham kayıt değişiklikleri doğrudan akışa dönüştürülür; `ingestion` event yayınlama sorumluluğundan tamamen kurtulur.
Her üç adımda da `analysis` modülünün dinleyici kodu neredeyse aynı kalır; değişen yalnızca taşıma katmanıdır.

---

## ADR-004 — Gerçek Zamanlı Bildirim için SSE

> **Netleştirildi (PRD v2.0 · [ADR-021](#adr-021--analiz-sonucunun-sahipliği-ve-gönderim-cevabının-kapsamı)).** SSE bir **tazeleme tetikleyicisidir, veri kaynağı değildir.** Olay, istemcinin ilgisini belirlemesine yetecek kadar bilgi taşır (kimlikler, tarih, il, olay tipi); veriyi istemci sorgu uçlarından alır. Sonucu: akış tamamen çökse bile hiçbir veriye erişim kaybolmaz, yalnızca "anlık"lık kaybedilir. Bu, aşağıdaki "Sonuçlar" bölümündeki bağlantı yönetimi riskini de küçültüyor — kaçan bir olay veri kaybı değil, gecikmiş tazeleme demek.

**Karar.** Yeni normalize veri üretildiğinde istemciler **Server-Sent Events** ile bilgilendirilecek. Akış tek yönlüdür (sunucu → istemci).

**Bağlam.** İster, yeni bildirim girildiğinde tablo ve grafiklerin sayfa yenilemeden güncellenmesi (FR-13). Veri akışı yalnızca sunucudan istemciye.

**Gerekçe.**
- İhtiyaç tek yönlü; SSE tam olarak bunun için tasarlanmış ve düz HTTP üzerinde çalışıyor.
- Tarayıcıda yerleşik `EventSource` API'si var; otomatik yeniden bağlanma ve `Last-Event-ID` protokolün parçası.
- Proxy/altyapı açısından WebSocket'e göre daha az sürtünme, sunucu tarafında daha az durum yönetimi.

**Alternatifler.**
- *WebSocket / STOMP:* Çift yönlü iletişim gerekmiyor; ek protokol ve altyapı karmaşıklığı karşılığında bir fayda yok.
- *Polling:* Basit ama gereksiz yük ve gecikme; "anlık güncelleme" hissini vermez.

**Sonuçlar.** Uzun ömürlü HTTP bağlantıları sunucu thread/bağlantı bütçesini tüketir; timeout ve bağlantı yönetimi ayrıca tasarlanmalı (TC-10). Yayın tüm bağlı istemcilere gider — kimlik doğrulama olmadığı için istemci bazlı filtreleme yok (ADR-011).

**İleride.** Çift yönlü etkileşim (ör. istemcinin abone olduğu olay tipini seçmesi) gerekirse WebSocket'e; çok örnekli (multi-instance) dağıtıma geçilirse örnekler arası yayın için Redis Pub/Sub gibi bir fan-out katmanına ihtiyaç doğar. SSE olayının sözleşmesi (payload şekli) bugünden sabitlendiği için bu geçişte istemci tarafındaki değişim taşıma katmanıyla sınırlı kalır.

---

## ADR-005 — Ham Kaydın Değiştirilemez Olması

> **Güçlendirildi (PRD v2.0 · [ADR-021](#adr-021--analiz-sonucunun-sahipliği-ve-gönderim-cevabının-kapsamı)).** Bu karar başlangıçta ham **metnin** değişmezliğini garanti ediyordu; döküman ise analiz bitince `status` ve `warnings` ile güncelleniyordu. ADR-021 ile bu güncelleme kalktı: artık **kaydın tamamı write-once**. Değişmezlik bir konvansiyon olmaktan çıkıp yazma yolunun yapısal özelliği oldu.

**Karar.** Ham bildirim yazıldıktan sonra güncellenemez ve silinemez. `ingestion` modülü Create + Read + Reprocess sunar; Update/Delete uçları yoktur.

**Bağlam.** Kaynak doküman ham kaydın "log niteliğinde" olmasını ve bir kaydın hangi metinden üretildiğinin sonradan izlenebilmesini istiyor.

**Gerekçe.**
- İzlenebilirlik ancak kaynak değişmezse anlamlıdır; ham metin değişirse ondan türeyen kayıtların açıklaması geçersizleşir.
- Değişmezlik, reprocess'i güvenli kılar: aynı girdiden her zaman yeniden üretim yapılabilir.
- Silme/güncelleme olmadığı için türetilmiş verinin invalidasyonu gibi bir sınıf problem hiç doğmuyor.

**Alternatifler.**
- *Tam CRUD:* Ham metin değişince türeyen Postgres kayıtlarının geçersiz kılınması/yeniden üretilmesi de ister haline gelirdi; log semantiğiyle çelişirdi.
- *Soft delete:* Kayıt korunur ama listelerden düşer. v1 için gereksiz; ihtiyaç doğarsa eklenmesi kolay.

**Sonuçlar.** Yanlış girilen bir bildirim düzeltilemez, yalnızca yenisi girilebilir. Veri hacmi tek yönlü büyür.

**İleride.** Kullanıcı hatalarını yönetmek için ham kayda "gizlendi/geçersiz" gibi bir durum alanı (soft delete) eklenebilir — kayıt fiziksel olarak korunurken türeyen veriler görünümlerden düşürülür. Bu, mevcut değişmezlik ilkesini bozmadan yapılabilir çünkü metnin kendisi yine dokunulmaz kalır.

---

## ADR-006 — Tanınmayan Olay Tipi Davranışı

**Karar.** Kataloğa uymayan bir olay tipi geldiğinde bildirim **reddedilmez**. Ham metin her zaman Mongo'ya yazılır; olay kaydı `OTHER` tipi ve `UNCLASSIFIED` durumuyla üretilir; çıkarılabilen tarih/il/sayılar korunur; API cevabında kullanıcıyı bilgilendiren uyarı listesi döner.

**Bağlam.** Kaynak doküman bu davranışı açıkça tasarım tercihimize bırakıyor ve gerekçesini ReadMe'de açıklamamızı istiyor.

**Gerekçe.**
- **Veri kaybı olmaz.** Reddetmek, sistemin henüz tanımadığı gerçek bir olayı tamamen kaybetmek demektir. Bilinmeyen tip çoğunlukla kullanıcının değil kataloğun eksikliğidir.
- **Görünürlük sağlar.** `UNCLASSIFIED` kayıtlar sorgulanabilir olduğu için "sistem neyi tanıyamıyor" sorusu ölçülebilir hale gelir; katalog bu geri bildirimle büyütülür.
- **Kısmi değer korunur.** Olay tipi bilinmese de tarih ve il çıkarımı çoğu zaman başarılıdır; bu bilgi atılmaz.
- **Kullanıcı yanıltılmaz.** Uyarı listesi, sonucun kısmi olduğunu açıkça söyler; sessizce yanlış bir tipe zorlamaktan iyidir.

**Alternatifler.**
- *400 ile reddetme:* Kullanıcıya net ama veri kaybı yaratır ve ham kaydın "log" isteriyle çelişir.
- *Yalnızca Mongo'ya yazıp Postgres'e hiç yazmama:* Postgres temiz kalır, ama tanınmayan bildirimlerin hacmi görünmez olur ve yeniden işleme için ayrı bir keşif işi gerekir.
- *En yakın tipe zorlama:* Sessiz yanlış sınıflandırma — analitik veriyi kirletir, en kötü seçenek.

**Sonuçlar.** Postgres'te düşük değerli `OTHER` kayıtları birikir; sorgu ve grafiklerde bunların varsayılan olarak dahil mi edileceği bir tasarım detayı olarak kalır. Sınıflandırma eşiğinin belirlenmesi ayrı bir problem (TC-8).

**İleride.** `UNCLASSIFIED` kayıtlar bir "katalog boşluğu" göstergesi olarak raporlanabilir; en sık görülen sınıflandırılamayan ifadeler otomatik çıkarılıp katalog önerisine dönüştürülebilir. Katalog güncellendiğinde bu kayıtlar reprocess ile (ADR-012) geriye dönük olarak doğru tipe kavuşur — bu yüzden bugün onları saklıyor olmak ileride doğrudan kazanca dönüşüyor.

---

## ADR-007 — Konfigürasyondan Yönetilen Olay Kataloğu

**Karar.** Olay tipleri, tetikleyici anahtar kelimeleri ve metrik tanımları koda gömülmez; konfigürasyon (YAML) üzerinden tanımlanır ve uygulama başlangıcında yüklenir.

**Bağlam.** Kaynak doküman olay tiplerini ve metrikleri örneklerden bizim belirlememizi istiyor — yani katalog baştan eksik olduğu bilinen, zaman içinde büyüyecek bir şey.

**Gerekçe.**
- Yeni bir olay tipi eklemek kod değişikliği değil konfigürasyon değişikliği olur (NFR-08).
- Katalog verisi ile çıkarım algoritması birbirinden ayrılır; algoritma testleri kataloğa, katalog testleri algoritmaya bağımlı olmaz.
- Kataloğun içeriği tek bir yerde okunabilir; değerlendiren kişi sistemin neyi tanıdığını tek dosyada görebilir.

**Alternatifler.**
- *Enum + koda gömülü keyword listeleri:* Tip güvenliği kazandırır ama her genişletme kod değişikliği ve yeniden derleme gerektirir.
- *Katalogun veri tabanında tutulması:* Çalışma zamanında düzenlenebilirlik kazandırır, ama yönetim arayüzü, versiyonlama ve migration ihtiyacı doğurur; v1 kapsamı için fazla.

**Sonuçlar.** Konfigürasyon hatası çalışma zamanında ortaya çıkar; bu yüzden katalog başlangıçta doğrulanmalı ve hatalı katalogda uygulama ayağa kalkmamalıdır. Tip güvenliği enum'a göre zayıftır.

**T-08 uygulaması — iki ek karar.**

*Kullanıcıya görünen etiketler katalogda.* Katalog yalnızca anahtar taşısaydı, `EPIDEMIC → "Salgın"` eşlemesi arayüzde kalırdı ve `FLOOD` eklemek konfigürasyon değişikliği **ve** bir frontend sürümü gerektirirdi — NFR-08'in ve `CLAUDE.md` kural 10'un ("frontend'in sabit kataloğu yok") ikisini birden ihlal ederdi. Bu yüzden etiketler katalogda ve metadata ucundan yayınlanıyor. `CLAUDE.md`'nin "kullanıcıya görünen metinler Türkçe, tek yerde tutulur" carve-out'u tam olarak bu durumu tarif ediyor: etiket içerik, kod değil.

Sınır şöyle çizildi: **kendi kendine büyüyen veri** (olay tipleri, metrikler, iller) metadata ucundan gelir; **yalnızca kod değişince değişen yapısal enum'lar** (`ProvinceScope`, `DateSource`, `ClassificationStatus`) tipli istemci sözleşmesinin parçasıdır. İkincisi değiştiğinde zaten bir frontend değişikliği gerekiyor, dolayısıyla arayüzde durmaları bir maliyet yaratmıyor.

*Tetikleyici anahtar kelimeler yayınlanmıyor.* Çıkarımı besliyorlar, sunumu değil; uçtan döndürmek bir ayar detayını istemcinin dayanabileceği bir sözleşmeye çevirirdi.

**Doğrulama.** Anahtarlar `varchar(48)` kolonlara yazıldığı için uzunluk da başlangıçta kontrol ediliyor — aksi halde hata, aylar sonra bir metni analiz ederken insert sırasında patlardı. Aynı metrik anahtarının olay tipleri arasında farklı etiket taşıması reddediliyor: `DEATH` gösteren bir grafik göstergesi aynı anda iki şey söyleyemez. Bulunan **tüm** problemler tek seferde bildiriliyor; her yeniden başlatmada bir hata göstermek, beş hatalı bir dosyayı beş kez başlatmak demekti.

**İleride.** Katalog veri tabanına taşınıp yönetim arayüzüyle çalışma zamanında düzenlenebilir hale getirilebilir; her katalog sürümü versiyonlanır ve olay kayıtları hangi katalog sürümüyle üretildiklerini taşır. Bu, ADR-012'deki reprocess ile birleşince "kataloğu güncelle, geçmişi yeniden değerlendir" akışını tam olarak mümkün kılar.

---

## ADR-008 — Kural/Regex Tabanlı Çıkarım (ML Yerine)

**Karar.** Tarih, il, olay tipi ve metrik çıkarımı kural ve düzenli ifade (regex) tabanlı bir hattayla (pipeline) yapılacak: normalizasyon → cümle bölme → aday tespiti (tarih, il, sayı, anahtar kelime) → eşleştirme → sınıflandırma.

**Bağlam.** Girdi Türkçe serbest metin; sayılar yazıyla gelebiliyor, tarihler çok formatlı, iller ek alıyor.

**Gerekçe.**
- **Deterministik ve test edilebilir:** Aynı girdi her zaman aynı çıktıyı verir. %80 kapsam isterini (NFR-02) anlamlı testlerle karşılamanın en doğrudan yolu.
- **Açıklanabilir:** Hangi kelimenin hangi çıkarımı tetiklediği gösterilebilir (FR-17). ML modeliyle bu doğrudan mümkün değil.
- **Bağımlılık ve kaynak maliyeti sıfıra yakın:** Model dosyası, GPU, harici servis yok; `docker compose up` hafif kalır.
- Problem alanı dar ve kalıplı; kural tabanlı yaklaşım bu kalıpları yüksek isabetle yakalar.

**Alternatifler.**
- *NER modeli (spaCy/Zemberek/HuggingFace):* Görülmemiş ifadelerde daha esnek olurdu; karşılığında eğitim verisi, model servisi, belirsiz çıktı ve ağır bağımlılık gelirdi.
- *LLM ile çıkarım:* Esneklikte en güçlüsü; ama harici servis bağımlılığı, maliyet, gecikme ve deterministik olmayan çıktı — birim testle doğrulaması zor.

**Sonuçlar.** Kalıp dışına çıkan ifadelerde ("geçtiğimiz hafta sonu", devrik cümleler) isabet düşer. Kural seti büyüdükçe bakım maliyeti artar ve kurallar arası öncelik/çakışma yönetimi gerekir (TC-3, TC-8).

**İleride.** Hat, aşamaları ayrık olacak şekilde kurgulanıyor; bu yüzden tek bir aşama (ör. olay tipi sınıflandırıcı ya da varlık çıkarıcı) kural tabanlıdan model tabanlıya değiştirilebilir, diğerleri aynı kalır. Hibrit bir yapı da mümkün: kural hattı önce çalışır, yalnızca `UNCLASSIFIED` kalan metinler bir modele/LLM'e düşer. Bu ikinci aşama, ADR-006 sayesinde zaten sorgulanabilir bir kuyruk halinde duruyor.

---

## ADR-009 — Java 21 + Spring Boot 3.5.x

**Karar.** Java 21 (LTS) ve Spring Boot 3.5.x kullanılacak.

**Bağlam.** Java 21 bir proje kısıtı. Ağustos 2026 itibarıyla güncel Spring Boot hattı 4.1.x; 3.5.x hattı da desteklenmeye devam ediyor.

**Gerekçe.**
- Java 21 ile tam uyumlu ve olgun; record, pattern matching, sealed types gibi modern dil özellikleri kullanılabiliyor.
- Ekosistem (Testcontainers, JaCoCo, springdoc-openapi, Flyway, Spring Data) bu hatta en iyi test edilmiş durumda.
- Dokümantasyon ve örnek bolluğu en yüksek hat; değerlendirme projesinde sürpriz/kırıcı değişiklik riski en düşük seçenek.

**Alternatifler.**
- *Spring Boot 4.1.x:* Daha güncel ve Java 26'ya kadar destekli. Karşılığında kırıcı değişiklikler ve görece az topluluk örneği — bu projede kazandıracağı bir şey yok.
- *Spring Boot 3.4 ve öncesi:* Destek penceresi daha dar, yeni bir projede tercih için sebep yok.

**Sonuçlar.** Bir noktada Boot 4.x'e yükseltme gerekecek. Bu maliyeti düşük tutmak için kaldırılmış/deprecated API'lerden kaçınılacak.

**İleride.** Boot 4.x'e geçiş, özellikle sanal thread'lerin (Project Loom) yaygınlaşmasıyla SSE'nin uzun ömürlü bağlantılarında (ADR-004) doğrudan fayda sağlar: platform thread bütçesi darboğaz olmaktan çıkar.

---

## ADR-010 — Tek Instance Veri Tabanları

**Karar.** MongoDB ve PostgreSQL `docker compose` içinde birer tek instance olarak çalışacak; replica set / cluster kurulmayacak.

**Bağlam.** Sistem tek komutla ayağa kalkmalı (NFR-03) ve geliştirme/değerlendirme ortamında çalışacak.

**Gerekçe.**
- Yüksek erişilebilirlik bir ister değil; replica set kurulumu `docker compose`'u belirgin şekilde ağırlaştırır ve ilk çalıştırma süresini uzatır.
- Tek instance, kurulumu deterministik ve hata ayıklamayı basit tutar.

**Alternatifler.**
- *Mongo replica set:* Change stream ve çok belgeli transaction için gerekli olurdu. İkisi de v1 kapsamında değil.
- *Yönetilen bulut veri tabanları:* Değerlendirme senaryosunda taşınabilirliği ve tek komutla ayağa kalkmayı bozar.

**Sonuçlar.** Üretim ortamı için uygun değil: tek nokta hatası ve yatay okuma ölçeklenmesi yok. Mongo transaction ve change stream özellikleri kullanılamaz.

**İleride.** Üretime çıkış hâlinde Mongo replica set'e, Postgres ise okuma replikalarına ve tarih bazlı partitioning'e geçirilebilir. Mongo replica set'e geçmek ayrıca change stream'i açar — bu da ADR-003'te tarif edilen CDC evrimi için ön koşuldur.

---

## ADR-011 — Kimlik Doğrulamanın Kapsam Dışı Bırakılması

**Karar.** v1'de kimlik doğrulama, yetkilendirme ve kullanıcı yönetimi yok. API açık; SSE yayını bağlı tüm istemcilere gider.

**Bağlam.** Kaynak dokümanda güvenlik/kimlik ile ilgili herhangi bir ister yok. Sistem tek tip aktör (analist) etrafında tanımlı.

**Gerekçe.**
- İster olmayan bir yeteneği eklemek kapsamı, test yükünü ve `docker compose` kurulumunu gereksiz büyütür.
- Asıl teknik zorluk metin analizi tarafında; efor oraya ayrılmalı.
- Kararın bilinçli olduğunu belgelemek, sessizce atlamış olmaktan farklıdır — bu kayıt tam olarak o amaca hizmet ediyor.

**Alternatifler.**
- *Basit API key:* Düşük maliyetle "güvenlik düşünüldü" sinyali verirdi; ancak gerçek bir güvenlik sınırı olmadan yanlış güven duygusu yaratır.
- *Spring Security + JWT:* Tam çözüm; ama kullanıcı deposu, token yaşam döngüsü ve ilgili testler kapsamı ciddi büyütür.

**Sonuçlar.** Sistem herkese açık; üretimde bu haliyle çalıştırılmamalı. Kullanıcı bazlı filtreleme, oran sınırlama (rate limiting) ve denetim izi (audit trail) yok.

**İleride.** Kimlik doğrulama eklendiğinde giriş noktası nettir: REST uçları için bir güvenlik filtre zinciri, SSE için bağlantı kurulurken token doğrulaması. SSE yayını o noktada istemci bazlı filtrelenebilir hale gelir (ör. kullanıcı yalnızca ilgilendiği illeri dinler). Ham bildirim kaydına "gönderen kullanıcı" alanı eklemek, kaydın değişmezliğini (ADR-005) bozmadan denetim izi sağlar.

---

## ADR-012 — Reprocess Yeteneği

**Karar.** Mevcut bir ham bildirim, güncel analiz kurallarıyla yeniden analiz edilebilir; ham metin değişmez, önceki normalize kayıtların yerini yeni sonuç alır.

**Bağlam.** Katalog (ADR-007) ve çıkarım kuralları (ADR-008) zaman içinde gelişecek. Analiz ayrıca başarısız olabilir (ADR-002).

**Gerekçe.**
- Ham metin değişmez olduğu için (ADR-005) yeniden üretim her zaman güvenli ve tekrarlanabilir.
- Kurallar geliştikçe geçmiş verinin kalitesi de yükselir; aksi halde katalog iyileştirmeleri yalnızca yeni bildirimlere fayda sağlardı.
- Başarısız analizler için doğal bir kurtarma yolu sunar.

**Alternatifler.**
- *Reprocess olmaması:* Geçmiş veri, üretildiği andaki kural setine sonsuza kadar sıkışır; ADR-006'daki "sakla, sonra sınıflandır" stratejisi anlamsızlaşırdı.
- *Otomatik toplu yeniden işleme:* Katalog değişiminde tüm geçmişi otomatik işlemek. Kontrolsüz ve maliyetli; v1 için açık tetikleme yeterli.

**Sonuçlar.** Yeniden üretimin mükerrer kayıt oluşturmaması gerekiyor; bu, kayıt kimliği ve değiştirme stratejisiyle ilgili bir tasarım detayı olarak task aşamasına kalıyor (TC-1, TC-9 ile bağlantılı).

**İleride.** Analiz motoru versiyonlanıp her olay kaydına "hangi motor/katalog sürümüyle üretildi" bilgisi eklenebilir. Böylece yalnızca eski sürümle üretilmiş kayıtlar seçilip toplu yeniden işlenebilir ve iki sürümün çıktısı karşılaştırılarak kural değişikliğinin etkisi ölçülebilir.

---

## ADR-013 — Maven + Flyway + OpenAPI

**Karar.** Build aracı Maven (wrapper ile), PostgreSQL şema yönetimi Flyway, API dokümantasyonu springdoc-openapi.

**Bağlam.** Projenin tek komutla kurulup çalışabilmesi ve şemanın öngörülebilir olması gerekiyor.

**Gerekçe.**
- **Maven:** Spring Boot ekosisteminde en yaygın; wrapper sayesinde makinede kurulu Maven gerekmiyor, Dockerfile ve README adımları sadeleşiyor. Çok modüllü (reactor) yapı, ADR-001'deki modül sınırlarını build seviyesinde taşıyor: ortak sürüm/plugin yönetimi parent'ta, modüle özgü kütüphaneler kendi pom'unda.
- **Flyway:** Şema versiyonlu ve kaynak kontrolünde; `ddl-auto` ile şema üretmek üretim benzeri davranıştan uzaklaşır ve şema değişikliklerini görünmez kılar.
- **springdoc-openapi:** API sözleşmesi kodla birlikte üretilir ve senkron kalır; frontend entegrasyonunu kolaylaştırır (NFR-07).

**Alternatifler.**
- *Gradle:* Daha esnek ve hızlı build; bu proje ölçeğinde belirgin bir fayda getirmiyor.
- *Liquibase:* Flyway'e denk; düz SQL migration'lar bu proje için daha okunaklı.
- *Elle yazılan OpenAPI dosyası:* Kodla senkron kalmama riski yüksek.

**Sonuçlar.** Maven XML'i Gradle DSL'ine göre daha ayrıntılı. Flyway migration'ları ileri yönlüdür; geri alma senaryosu ayrıca düşünülmelidir.

**İleride.** Çok modüllü yapı zaten kurulu olduğu için, bir modülün ayrı servise çıkarılması onu kendi `app` modülüyle eşleyip reactor'dan ayırmaya indirgeniyor — kod taşımak gerekmiyor. OpenAPI şeması "contract-first" yaklaşımına çevrilerek frontend istemci kodu otomatik üretilebilir.

---

## ADR-014 — Tarih Çözümleme ve Referans Tarih

**Karar.** Bir olay kaydının tarihi üç kaynaktan çözülür ve **çözüm kaynağı kayıtla birlikte saklanır**: `EXPLICIT` (metinde açık tarih), `RELATIVE` (göreli zaman ifadesi) ve `DEFAULTED` (metinde hiç zaman ifadesi yok). Göreli ifadelerin ve varsayılan durumun **referans tarihi, ham bildirimin gönderim tarihidir**; reprocess sırasında da orijinal gönderim tarihi kullanılır.

**Bağlam.** Kaynak dokümandaki üçüncü örnek — "Son 24 saatte Bursa'da 8, Kocaeli'nde 6 trafik kazası meydana geldi." — açık takvim tarihi içermiyor, ama tarihsiz de değil: metin bir zaman ifadesi taşıyor ve bu ifade gönderim tarihine göre çözülebiliyor. Bu üçüncü örnek, "tarih var / tarih yok" şeklindeki ikili ayrımın yetersiz olduğunu gösteriyor.

**Gerekçe.**
- **Göreli ifade bir çıkarımdır, varsayım değil.** "Son 24 saatte" ifadesini "tarih bulunamadı" saymak, metinde fiilen var olan bilgiyi atmak olur. `RELATIVE` ile `DEFAULTED`'ı ayırmak, aynı takvim gününü üretseler bile aralarındaki güven farkını korur.
- **Kaynağın saklanması veriyi dürüst kılar.** Kullanıcı bir grafikte gördüğü noktanın metinden okunmuş bir tarihe mi yoksa sistemin varsayımına mı dayandığını bilmelidir. Aksi halde `DEFAULTED` kayıtlar gönderim gününde yapay bir yığılma yaratır ve bu görünmez kalır.
- **Referansın gönderim tarihi olması reprocess'i güvenli kılar.** Referans "şimdi" olsaydı, aynı bildirimin yeniden işlenmesi (ADR-012) geçmiş kayıtların tarihini kaydırır ve analiz tekrarlanabilir olmaktan çıkardı. Ham kayıt değişmez olduğu için (ADR-005) gönderim tarihi de sabit ve güvenilir bir çapa.

**Alternatifler.**
- *Göreli ifadeyi yok sayıp `DEFAULTED` saymak:* Kod basitleşirdi; ama metindeki gerçek bilgi kaybolur ve veri kalitesi ölçülemez hale gelirdi.
- *Tarih kaynağını saklamamak:* Tek alanla yetinmek. Çıkarılmış ve varsayılmış tarihler ayırt edilemez, kullanıcı yanıltılır.
- *Tarihsiz metinleri reddetmek:* ADR-006'daki "veri kaybetme" ilkesiyle çelişir.
- *Referans olarak analiz anını ("şimdi") kullanmak:* Reprocess'i bozar; aynı girdi farklı zamanlarda farklı çıktı üretir.

**Sonuçlar.** Tarih alanının yanında bir kaynak alanı taşınacak ve sorgu/grafik uçlarında görünür olacak. Göreli **aralık** ifadeleri v1'de tek bir referans güne indirgeniyor — "son 3 günde" ifadesi üç güne yayılmıyor. Zaman dilimi seçimi (`Europe/Istanbul` vs. UTC) ve gün sınırı tanımı bu kararla sabitlenmedi; TC-6 kapsamında karara bağlanacak.

**İleride.** Tarih tek bir gün yerine bir **aralık** (başlangıç–bitiş) olarak modellenebilir; böylece "son 3 günde" ifadesi gerçek yayılımıyla temsil edilir ve zaman serisi grafiklerinde daha doğru dağıtılır. Ayrıca kaynak bilgisine bir güven skoru eklenerek, kullanıcıya tarih belirsizliği grafik üzerinde görsel olarak (ör. soluk/kesikli seri) gösterilebilir. Bugün kaynağı saklıyor olmak, bu evrimin ön koşulunu şimdiden karşılıyor.

---

## ADR-015 — Üç Repo'luk Yapı ve Ayrı DevOps Repo'su

> ⚠️ **Bu karar geçersizdir; yerini [ADR-016](#adr-016--tek-repo-monorepo) almıştır.**
> Kayıt, kararın neden alındığını ve neden geri alındığını göstermek için burada bırakılmıştır.
> Aşağıdaki metin, geri alınmadan önceki haliyle korunmuştur.

**Karar.** Proje üç repo'ya bölünür: `incident-report-be` (backend), `incident-report-fe` (frontend) ve `incident-report-devops` (kompozisyon). Full-system `docker compose up`, servis repo'larını **git submodule** olarak bağlayan devops repo'sundan çalıştırılır. Her servis repo'su ayrıca **tek başına** çalıştırılabilir kalır.

| Repo | İçerik | Bağımsız çalışır |
|---|---|---|
| `incident-report-be` | Backend + kendi compose'u (app + PostgreSQL + MongoDB) | Evet |
| `incident-report-fe` | Frontend | Evet |
| `incident-report-devops` | Full-system compose, submodule pinleri, operasyon dokümanı | Sistemin giriş noktası |

**Bağlam.** Kaynak doküman "Sistem `docker-compose up` komutu ile **tek seferde** ayağa kalkmalıdır" diyor; buradaki "sistem" backend, frontend ve veri tabanlarının tamamı. Buna karşılık backend ve frontend ayrı repo'larda geliştiriliyor. Kompozisyonun bir yerde yaşaması gerekiyordu.

**Gerekçe.**
- **Sahiplik simetrisi.** Full-system compose backend repo'suna konsaydı, backend frontend'e referans verir ve onu sahiplenmiş görünürdü. Ne backend ne frontend diğerinin üstünde değil; kompozisyon üçüncü bir yere ait.
- **Tekrar yok.** Compose'un `include:` özelliği backend'in kendi compose dosyasını olduğu gibi alıyor. `backend`, `postgres`, `mongodb` tanımları **tek yerde**, onları sahiplenen repo'da duruyor; devops repo'su yalnızca frontend'i ve servisler arası bağlantıyı ekliyor. Duplikasyon gerekseydi bu ayrım maliyetli olurdu.
- **Sürüm bileşimi kayıt altında.** Submodule'ler belirli commit'lere sabit; "hangi backend hangi frontend ile çalışıyor" sorusunun cevabı devops repo'sunun git geçmişinde duruyor.
- **Doğal ev.** CI workflow'ları, k8s manifestleri, seed data ve operasyon dokümanının backend veya frontend repo'sunda yeri yok; burada var.

**Alternatifler.**
- *Monorepo:* Tek `docker compose up`, tek klon, en basit değerlendirici deneyimi. Ancak backend ve frontend repo'larını ayırma kararı zaten alınmıştı; bunu geri almak bağımsız sürümleme ve ayrı CI hattı gibi kazanımları da geri alırdı.
- *Full-system compose'u backend repo'sunda tutmak:* Bir repo eksik olurdu. Karşılığında backend'in frontend'i submodule olarak içermesi gerekirdi — yanlış sahiplik sinyali.
- *Devops repo'sunun GHCR'dan hazır imaj çekmesi:* Değerlendirici için en hızlı yol (`docker compose up`, derleme yok). Ancak GitHub Actions kurulumu ve public package gerektirir; daha önemlisi, değerlendirici kodu değiştirip yeniden çalıştıramaz. Bir değerlendirme projesinde kodun derlendiğinin görünmesi, hızdan daha değerli.

**Sonuçlar.**
- Değerlendirici üç repo görüyor ve muhtemelen önce backend'e denk geliyor; oradaki compose yalnızca backend'i ayağa kaldırıyor. **Bu gerçek bir risk.** Karşılığı: her repo'nun README'sinin en üstünde devops repo'suna yönlendiren bir kutu var.
- `git clone --recurse-submodules` unutulursa servis klasörleri boş gelir. Devops repo'sundaki `make up`, submodule checkout'unu kendisi yaptığı için bu tuzağı kapatıyor.
- Backend'e yeni commit gelmesi devops repo'sunu kendiliğinden güncellemiyor; submodule pointer'ı elle ilerletmek gerekiyor (`make update` + commit). Bu bilinçli: sürüm bileşimi otomatik değil, kayıtlı.
- Backend'in compose dosyası artık "dahil edilebilir" olmak zorunda — mutlak yol veya tek-compose varsayımı yapamaz.

**İleride.** Servisler GHCR'a imaj yayınlayan bir CI hattı kazandığında devops repo'suna ikinci bir compose dosyası (`docker-compose.images.yml`) eklenip "derlemeden çalıştır" seçeneği sunulabilir; submodule'lü kaynaktan derleme yolu geliştirme için kalır. Aynı repo, Kubernetes'e geçiş halinde Helm chart'larının ve ortam bazlı (staging/prod) değerlerin de doğal evi olur — bugün compose'un durduğu yerde.

---

## ADR-016 — Tek Repo (Monorepo)

**Karar.** Proje **tek bir repo**'da toplanır: `incident-report`. Backend ve frontend, repo kökündeki birer dizin (modül) olarak yaşar. Full-system `docker-compose.yml` **repo kökündedir**; ayrı bir `devops` modülü yoktur.

```
incident-report/
├── docker-compose.yml     full-system compose (giriş noktası)
├── .env.example
├── CLAUDE.md              tek çalışma sözleşmesi, tüm proje için
├── docs/                  PRD, DECISIONS, TASKS - proje geneli
├── backend/               Java 21 / Spring Boot, kendi Maven reactor'ı
│   ├── pom.xml            parent
│   ├── docker-compose.yml backend + veri tabanları
│   └── shared/ ingestion/ analysis/ realtime/ app/
└── frontend/              ReactJS (henüz oluşturulmadı)
```

**Bağlam.** ADR-015, backend ve frontend'in ayrı repo'larda geliştirilmesi varsayımı üzerine kuruluydu ve kompozisyonu üçüncü bir repo'ya (`incident-report-devops`) koyuyordu. Yapı kurulup çalıştırıldıktan sonra, ayrılığın kendisinin bu projede maliyeti faydasından fazla olduğu görüldü.

**Gerekçe.**
- **Değerlendirici deneyimi belirleyici oldu.** Kaynak doküman "Sistem `docker-compose up` komutu ile tek seferde ayağa kalkmalıdır" diyor. Tek repo'da bu birebir gerçekleşiyor: klonla, kökte `docker compose up --build`. Üç repo'lu yapıda değerlendiricinin önce doğru repo'yu bulması, sonra `--recurse-submodules` ile klonlaması gerekiyordu. ADR-015 bu riski zaten "gerçek risk" olarak kaydediyordu; riski README uyarısıyla yönetmek yerine ortadan kaldırmak daha doğru.
- **Atomik değişiklik.** Backend API'si değiştiğinde frontend'in uyumu aynı commit'te yapılabiliyor. Ayrı repo'larda bu iki commit ve bir sürüm koordinasyonu demekti.
- **Submodule maliyeti kayboldu.** Pointer'ı elle ilerletme, unutulan `--recurse-submodules`, "hangi backend hangi frontend ile" eşlemesi — hepsi konu olmaktan çıktı.
- **Ayrı devops modülüne gerek kalmadı.** Submodule yönetimi ortadan kalkınca `devops/` dizininin tek içeriği full-system compose olacaktı; o da isterin lafzına uymak için köke taşındı. Var olmayan bir modülü kurmamak, boş bir modül kurmaktan iyi.
- Proje ölçeği bunu kaldırıyor: iki uygulama modülü ve iki veri tabanı. Ayrı repo'ların çözdüğü problemler (bağımsız sürümleme, ayrı erişim kontrolü, ayrı release kadansı) bu projede yok.

**Alternatifler.**
- *ADR-015'te kalmak:* Servislerin bağımsız sürümlenmesini korurdu. Ancak bu projede bağımsız sürümleme bir ihtiyaç değil; karşılığında değerlendirici sürtünmesi ve submodule bakımı ödeniyordu.
- *Monorepo ama compose `devops/` altında:* Modül ayrımı daha saf olurdu; `docker compose up` kökte çalışmaz, `-f devops/docker-compose.yml` gerekirdi. İsterin lafzı köke işaret ediyor.
- *Backend ve frontend'i tek Maven reactor'ında birleştirmek:* Frontend Maven projesi değil; yapay bir sarmalayıcı gerektirirdi.

**Sonuçlar.**
- Maven reactor'ın kökü artık `backend/`. Komutlar `cd backend && ./mvnw ...` şeklinde; kökte `pom.xml` yok.
- İki compose dosyası var: kökteki (full-system) ve `backend/`deki (yalnız backend + veri tabanları). Tekrar yok — kökteki, `include:` ile backend'inkini olduğu gibi tüketiyor. Backend'in compose'u "dahil edilebilir" kalmak zorunda (ADR-015'ten devralınan kısıt).
- Repo geçmişi ADR-015'in kurulup geri alındığını gösteriyor. Bu bilinçli olarak temizlenmedi: karar günlüğünün işi, hangi yolun neden denendiğini ve neden bırakıldığını kayıt altına almak.
- Tüm modüller aynı anda klonlanıyor; repo büyüdükçe klon boyutu da büyüyecek. Bu ölçekte önemsiz.

**İleride.** Modüllerden biri bağımsız release kadansı ya da ayrı erişim kontrolü gerektirirse, `git filter-repo --subdirectory-filter backend` ile o dizin kendi geçmişiyle birlikte ayrı bir repo'ya çıkarılabilir — monorepo bu kapıyı kapatmıyor. CI tarafında yol bazlı tetikleyiciler (`paths: backend/**`) ile modüller ayrı ayrı derlenip test edilebilir; tek repo, tek pipeline demek değil.

---

## ADR-017 — Mimari Kurallar için ArchUnit (Spring Modulith Yerine)

**Karar.** Derleyicinin yakalayamadığı mimari kurallar **ArchUnit** ile, `app` modülünün test kaynaklarında doğrulanır. Spring Modulith kullanılmaz.

**Bağlam.** ADR-001, modülleri ayrı Maven modülleri yaptığı için modüller arası erişim yasağı zaten **derleme hatası**. Geriye derleyicinin göremediği kurallar kalıyor: hangi modülün hangi kütüphaneye dokunabileceği, controller'ın entity/document sızdırmaması, katman yönü, alan enjeksiyonu yasağı.

**Gerekçe.**
- **Spring Modulith'in ana katkısı bu projede zaten karşılanmış.** Modulith, paket tabanlı modül sınırlarını doğrular; bizde sınır Maven bağımlılık grafiğinde ve ihlali derlemede kırılıyor. Aynı işi ikinci kez yapan bir bağımlılık eklemek net kazanç getirmiyor.
- **Modulith'in getirdiği diğer şeyleri istemiyoruz.** `@ApplicationModuleListener` asenkron ve `REQUIRES_NEW` transaction ile çalışır; ADR-003 mesajlaşmanın senkron olmasını şart koşuyor. Event publication registry ise ek bir veri tabanı tablosu demek. Kullanmayacağımız yetenekler için kavramsal yük taşımak istemiyoruz.
- **ArchUnit tam da boşluğu dolduruyor.** Kütüphane bazlı yasaklar, isimlendirme tabanlı katman kuralları ve anotasyon tabanlı kurallar doğrudan ifade edilebiliyor; kurallar okunabilir birer cümle ve `because(...)` ile gerekçesi kodda duruyor.
- **Test kapsamında, çalışma zamanı ayak izi yok.** Üretim artifact'ına hiçbir şey eklemiyor.

**Alternatifler.**
- *Spring Modulith:* `ApplicationModules.verify()` tek satırla modül doğrulaması sunardı ve dokümantasyon üretebilirdi (PlantUML, C4). Ancak doğrulamanın büyük kısmı bizde zaten build garantisi; kalan kurallar (entity sızıntısı, katman yönü, alan enjeksiyonu) Modulith'in kapsamında değil — yine ArchUnit gerekirdi. Yani Modulith ArchUnit'in yerini almıyor, üstüne biniyor.
- *Kural koymamak, code review'a bırakmak:* İnsan disiplinine bağlı; ilk yoğun haftada kaybedilir.
- *Kuralları her modülün kendi testinde tanımlamak:* Her modül yalnızca kendi sınıflarını görür; modüller arası kurallar ifade edilemez. Bu yüzden kurallar `app`'te — tüm modülleri classpath'inde gören tek modül.

**Sonuçlar.**
- 13 kural devrede: modül sınırları, veri tabanı sahipliği, çevrim (cycle) yokluğu, entity/document'ın API'ye sızmaması, katman yönü ve alan enjeksiyonu yasağı.
- **Bir kural T-06'da keskinleştirildi.** İlk hali "controller persistence tipine bağımlı olamaz" diyordu ve doğru mapping kodunda ateşledi — controller'ın dökümanı *görmesi* DTO'ya çevirmenin ta kendisi. Kural kaldırılmadı, kastedilen şeye indirgendi: handler bir entity/document **döndüremez**, `*Request`/`*Response` alanı entity/document **olamaz**. Fazla geniş bir kural, insanları kuralı zayıflatmaya alıştırır; doğru tepki onu kaldırmak değil, ne demek istediğini tam söylemek.
- Kurallar isim tabanlı (`*Repository`, `*Controller`); isimlendirme konvansiyonundan sapmak kuralı sessizce devre dışı bırakır. Konvansiyon `CLAUDE.md`'de yazılı.
- Modüllerin çoğu henüz boş olduğu için `archRule.failOnEmptyShould=false` ayarlandı. Bu, yanlış yazılmış bir paket adının kuralı sessizce geçirmesi riskini doğuruyor; risk, "hiç sınıf import edilmediyse patla" diyen ayrı bir kontrol testiyle kapatıldı. Modüller kod kazandığında ayar tekrar açılmalı.
- Doğrulandı: `Repository → Controller` bağımlılığı eklendiğinde build `Architecture Violation ... was violated (3 times)` ile kırıldı.

**İleride.** Modüller doldukça ArchUnit'in `layeredArchitecture()` ve `onionArchitecture()` tanımlarına geçilebilir; bugünkü isim tabanlı kurallar yerine paket tabanlı katman tanımı daha güçlüdür. Mimari dokümantasyonun otomatik üretimi istenirse Spring Modulith yalnızca **doküman üreticisi** olarak (doğrulayıcı olarak değil) ayrıca değerlendirilebilir.

---

## ADR-018 — Coverage Kapısı: Modül Başına Eşik + Proje Geneli Rapor

**Karar.** JaCoCo `verify` fazına bağlanır. Eşik **her modül için ayrı ayrı** uygulanır (satır bazında ≥ %80) ve altına düşen modül build'i kırar. Ayrıca `app` modülü, bağımlı olduğu tüm modülleri birleştiren **proje geneli bir rapor** üretir.

**Bağlam.** NFR-02: "Birim testlerin kapsayıcılığı en az yüzde 80 olmalı ve tüm önemli fonksiyonları kapsayıcı olmalıdır." Çok modüllü bir build'de bu oranın nasıl hesaplanacağı belirsiz: modül başına mı, toplamda mı?

**Gerekçe.**
- **Modül başına eşik, ortalamanın arkasına saklanmayı engelliyor.** Yalnızca toplam oran ölçülseydi, iyi test edilmiş bir modül test edilmemiş bir modülü maskeleyebilirdi. İsterin "tüm önemli fonksiyonları kapsayıcı" kısmı tam olarak bunu yasaklıyor.
- **Proje geneli rapor, isterin lafzına cevap veriyor.** Değerlendiriciye gösterilecek tek bir sayı gerekiyor; `app/target/site/jacoco-aggregate/` bunu üretiyor.
- **Bootstrap sınıfı hariç tutuldu.** `IncidentReportApplication.main()` testlerle çalıştırılmıyor; yalnızca oranı yükseltmek için sarmalamak hiçbir davranışı test etmez.

**Alternatifler.**
- *Yalnızca proje geneli eşik:* Tek sayı, basit. Ancak modül maskeleme sorunu doğar ve JaCoCo'nun birleşik rapor üzerinde `check` goal'ü yok — ayrı bir birleştirme modülü kurmak gerekirdi.
- *Yalnızca modül başına eşik:* Bugünkü kapı bu; tek eksiği isterin istediği "tek sayı"yı üretmemesi. Bu yüzden birleşik rapor ayrıca ekleniyor.
- *Branch coverage eşiği de eklemek:* Daha güçlü olurdu; ancak ister satır bazında bir oran veriyor ve branch eşiği erken aşamada gereksiz sürtünme yaratır. İleride eklenebilir.

**Sonuçlar.**
- Doğrulandı: `app` modülüne testi olmayan bir sınıf eklendiğinde build `Rule violated for bundle app: lines covered ratio is 0.00, but expected minimum is 0.80` ile kırıldı.
- **Bilinen boşluk — T-05'te kapatıldı.** JaCoCo, `jacoco.exec` bulunmayan modülde `check` goal'ünü **sessizce atlıyor** (`Skipping JaCoCo execution due to missing execution data file`). Yani kodu olup hiç testi olmayan bir modül kapıdan geçiyordu. T-05'te Surefire `failIfNoTests=true` parent'a eklendi: hiç test çalışmayan modülde build `No tests to run!` ile kırılıyor, dolayısıyla exec dosyası her zaman oluşuyor ve eşik fiilen çalışıyor. Doğrulandı — `shared` modülünün testleri geçici olarak kaldırıldığında build kırıldı. Tek istisna `realtime`: üretim kodu T-18'de geleceği için kendi pom'unda açık bir override taşıyor ve override'ın o task'ta kaldırılacağı yorumda yazılı.
- Birleşik rapor, exec verisi olmayan modüllerin sınıflarını **kapsanmamış** sayarak dahil ediyor; yani proje geneli sayı bu boşluktan etkilenmiyor, dürüst kalıyor. Otomatik kapı eksik, ölçüm değil.
- `./mvnw verify` artık çalışan bir Docker daemon gerektiriyor (Testcontainers). İmaj derlemesi gerektirmiyor — `Dockerfile` paketlemeyi `-DskipTests` ile yapıyor.

**İleride.** Coverage eşiği tek bir property'den (`coverage.minimum.line`) yönetiliyor; kod tabanı olgunlaştıkça yükseltilebilir ve branch coverage eşiği eklenebilir. Mutation testing (PIT) bir sonraki doğal adım: coverage "satır çalıştırıldı mı" sorusunu, mutation testing "test gerçekten bir şey doğruluyor mu" sorusunu cevaplar — %80 çizgisinin anlamlı testlerle mi yoksa getter çağrılarıyla mı tutulduğunu ancak o gösterir.

---

## ADR-019 — Kayıt Granülaritesi

**Karar.** Bir **Olay Kaydı**'nın granülaritesi `(ham bildirim, tarih, il, olay tipi)`'dir. Bir ham metin, içerdiği her farklı kombinasyon için bir kayıt üretir. İl alanı null olabilir ve yanında bir **kapsam** bilgisi taşır:

| `province_scope` | Anlamı | `province_code` |
|---|---|---|
| `SINGLE` | Sayılar belirli bir ile ait | Dolu |
| `SHARED` | Metin sayıyı birden fazla ile **ortak** veriyor ("her iki ilde toplam") | Boş — kapsadığı iller `incident_shared_province`'de |
| `UNKNOWN` | Metinde hiç il geçmiyor | Boş |

**Bağlam.** PRD bu kararı bilinçli olarak TC-1 olarak açık bırakmıştı. Belirleyici olan, kaynak dokümanın amaç cümlesi: verilerin *"zaman içinde, coğrafi bölge bazında grafiksel olarak izlenebilmesi"*. Bu, tarih ve ilin bir sunum tercihi değil, verinin taşıması gereken boyutlar olduğunu söylüyor. Üçüncüsünü FR-11 ekliyor: grafik olay tipine göre çiziliyor.

Zor kısmı üçüncü örnek metin: *"Bursa'da 8, Kocaeli'nde 6 trafik kazası… Bursa'da 1, Kocaeli'nde ise 2 kişi hayatını kaybetti. **Her iki ilde toplam 10 kişi yaralı** olarak hastaneye kaldırıldı."* Son sayı hiçbir tek ile ait değil.

**Gerekçe.**
- **Amaç cümlesi tarih ve ili kimliğin parçası yapıyor.** Bu boyutlar kaydın içine gömülü nitelikler olsaydı, il bazında grafik ancak metin yeniden ayrıştırılarak çizilebilirdi.
- **Atanamayan sayı için ayrı bir kapsam, tek dürüst temsil.** `SHARED` kaydı sayıyı bölmez, düşürmez, gizlemez. Toplamlar doğru kalır (yaralı = 10, kaza = 14, ölü = 3) ve hiçbir il kendisine ait olmayan bir sayıyı üstlenmez.
- **Kapsadığı illerin saklanması, coğrafi izlenebilirlikteki deliği kapatıyor.** Saklanmasaydı 10 yaralı "hiçbir yere ait olmayan" bir sayıya dönüşürdü; Bursa'yı filtreleyen kullanıcıya *"ayrıca Kocaeli ile paylaşılan 10 yaralı var"* denemezdi. Üstelik bu bilgi analiz sırasında **zaten mevcut**: kaydın `SHARED` olduğuna karar verebilmek için ifadenin hangi illere işaret ettiğinin çözülmüş olması gerekiyor. Onu atıp sonradan "aynı bildirimdeki diğer iller" varsayımıyla geri üretmek, *"Ankara'da 5 vaka. İstanbul ve İzmir'de toplam 12 vaka."* gibi bir metinde tamamen yanlış sonuç verir.
- **Değişmezlik ve kapsam, hem şemada hem Java'da zorunlu.** `incident_province_matches_scope` CHECK constraint'i ve üç fabrika metodu (`forProvince`, `sharedAcross`, `withoutProvince`) aynı kuralı iki katmanda birden koruyor; `SHARED` bir kayda tek il iliştirmenin yolu yok.
- **Reprocess yapısal olarak güvenli.** Kayıtlar `raw_report_id` ile bağlı; ham metin değişmez olduğu için (ADR-005) yeniden işleme = o bildirime ait kayıtları silip yeniden üretmek. Mükerrer kayıt riski doğmuyor.

**Alternatifler.**
- *Paylaşılan sayıyı eşit bölüştürmek (5/5):* Metinde olmayan bir bilgi üretmek olurdu. Grafik "Bursa'da 5 yaralı" derdi; metin bunu hiçbir yerde söylemiyor. Uydurma sayı, analitik veriyi kirletmenin en kötü yolu.
- *Atanamayan metriği düşürmek:* 10 yaralı kaybolurdu — ADR-006'daki "veri kaybetme" ilkesiyle çelişir.
- *Metin başına tek kayıt, iller liste olarak:* Bursa'nın 8 kazası ile Kocaeli'nin 6'sı aynı satırda toplanır, il bazında grafik imkânsız hale gelirdi. Amaç cümlesinin doğrudan ihlali.
- *Header'sız düz fact tablosu (metrik başına satır, il ve tarih dahil):* Analitik için elverişli; ancak FR-10'un "tablo halinde göster"ine ve FR-13'ün SSE ile yayınlayacağı "yeni kayıt"a karşılık gelen doğal bir birim kalmazdı. PRD'nin sözlüğündeki "Olay Kaydı" kavramı karşılıksız kalırdı.

**Sonuçlar.**
- Üç örnek metin toplam **5 kayıt** üretiyor (1 + 1 + 3).
- **İl bazlı görünümde `SHARED` gizlenemez.** Bursa çubuğu 10 yaralıyı içermez — içermemeli — ama arayüz ayrıca "paylaşılan / atanamayan" dilimini göstermek zorunda. Aksi halde kullanıcı il toplamlarının genel toplamı tutmadığını görür ve nedenini bilemez. Bu, API sözleşmesine ayrı bir alan olarak yansıyacak (T-16/T-17).
- **Çoklu il seçiminde `DISTINCT` şart.** Bursa ve Kocaeli birlikte seçilirse paylaşılan kayıt bir kez sayılmalı. Bu bir maliyet; ancak link tablosu olmadan bu soru sorulamaz — problem çözülmez, görünmez olur.
- Bir bildirim birden fazla `SHARED` kaydı taşıyabilir (farklı il gruplarını kapsayan iki "toplam" ifadesi), bu yüzden doğal anahtar üzerinde unique constraint **yok**; reprocess sil-ve-yeniden-üret ile çalışıyor.

**İleride.** Kapsam modeli il düzeyinde; bölge/ülke gibi daha geniş coğrafi seviyeler gerekirse `province` tablosunun yanına bir hiyerarşi eklenip `SHARED` kaydın kapsamı o seviyeye taşınabilir. Paylaşılan sayıların dağıtımı istenirse (ör. nüfusa orantılı tahmin), bu **türetilmiş bir görünüm** olarak eklenmeli — ham kayıt bölünmemiş halde kalmalı ki tahmin ile ölçüm birbirine karışmasın.

---

## ADR-020 — Metrik Veri Modeli: Metrik Başına Satır

**Karar.** Metrikler `incident_metric(incident_id, metric_type, metric_value)` tablosunda, metrik başına bir satır olarak saklanır. `metric_type` bir katalog anahtarıdır — veri tabanı enum'u değil.

**Bağlam.** PRD bunu TC-2 olarak açık bırakmıştı. Olay tiplerinin metrik setleri farklı: salgın için vaka/vefat/taburcu, deprem için hasarlı bina/kurtarılan/yaralı. Katalog ise konfigürasyondan yönetiliyor ve büyüyecek (ADR-007).

**Gerekçe.**
- **Kataloğun kod değişmeden büyümesi, şemanın da değişmeden büyümesini gerektiriyor.** ADR-007 "yeni olay tipi eklemek yalnızca YAML değişikliği" diyor. Metrik başına kolon olsaydı her yeni metrik bir migration demek olurdu — bu iki karar birbiriyle çelişirdi.
- **Agregasyon doğrudan SQL.** FR-11'in metrik bazlı zaman serisi ve FR-12'nin kümülatif görünümü `group by metric_type` + `sum(metric_value)`'a indirgeniyor; `metric_type` indexlenebiliyor.
- **Seyreklik problemi yok.** Geniş tabloda her kayıt, ait olmadığı olay tipinin metrik kolonlarını `null` taşırdı.
- `(incident_id, metric_type)` unique: bir metrik bir kayıt için yalnızca bir kez çıkarılabilir. İkinci bir değer, çıkarımın aynı soruya iki cevap üretmesi demek olurdu.

**Alternatifler.**
- *JSONB kolon:* Esnek ve tek satırda toplu okuma sağlar. Ancak metrik bazlı agregasyon ve indexleme ifade indexleri gerektirir, tip güvenliği yoktur ve `sum` için cast şarttır. Kazandırdığı esneklik bu tabloda zaten var.
- *Geniş tablo (metrik başına kolon):* Sorgusu en basit. Ancak her yeni metrikte migration gerekir — ADR-007 ile doğrudan çelişir — ve tablo seyrekleşir.
- *Metrikleri ham metinle birlikte Mongo'da tutmak:* Agregasyon Postgres'te yapılacağı için (ADR-002) veriyi yanlış tarafa koymak olurdu.

**Sonuçlar.** Bir kaydın tüm metriklerini okumak join gerektiriyor; satır sayısı kayıt sayısının birkaç katı. Bu ölçekte önemsiz. `metric_value` `integer`: katalogdaki metriklerin tamamı sayım. `metric_type` veri tabanı tarafından doğrulanmıyor — yazım hatası kataloğun kendi başlangıç doğrulamasında yakalanmalı (ADR-007).

**İleride.** Parasal hasar gibi tam sayı olmayan bir metrik gerekirse `metric_value` `numeric`'e çevrilebilir; ileri yönlü, veri kaybı olmayan bir migration. Sorgu hacmi büyürse `(event_type, occurred_on, metric_type)` üzerinde materialized view ya da tarih bazlı partitioning devreye alınabilir — tablo şekli buna hazır.

---

## ADR-021 — Analiz Sonucunun Sahipliği ve Gönderim Cevabının Kapsamı

**Karar.** Analiz sonucu — durum (`ANALYZED` / `FAILED`), uyarılar ve analiz zamanı — **`analysis` modülüne aittir** ve orada saklanır. `analysis`'ten `ingestion`'a **dönüş event'i yoktur**: `RawReportAnalyzedEvent` ve dinleyicisi kaldırılır, ham döküman yazıldıktan sonra hiç güncellenmez. `POST /incident-reports` yalnızca **ham kaydın makbuzunu** döner (kimlik + gönderim zamanı); analiz sonucunu ve uyarıları taşımaz. İstemci sonucu `GET /incidents?rawReportId=...` ile okur. SSE ise bir **tazeleme tetikleyicisidir, veri kaynağı değildir**.

**Bağlam.** ADR-003 modüller arası iletişimi senkron Spring event'i olarak kurdu ve gerekçelerinden biri "kullanıcı gönderir göndermez sonucu ve uyarıları görsün"dü. Bunun sonucu olarak `analysis` işini bitirince `RawReportAnalyzedEvent` yayınlıyor, `ingestion` bunu dinleyip Mongo dökümanını `status` ve `warnings` ile güncelliyordu (T-07). Frontend kapsama alınırken (PRD v2.0) bu akış yeniden incelendi: gönderim cevabının ne söylemesi gerektiği, sonucun kullanıcıya hangi kanaldan ulaşacağı ve modüllerin ileride ayrıştırılabilirliği birlikte tartışıldı.

**Gerekçe.**
- **Sahiplik.** `analysis`'in ürettiği veriyi `ingestion` yayınlıyordu. Bir modülün sahibi olmadığı veriyi kendi cevabında temsil etmesi, o verinin şekli değiştiğinde **iki yerde** kırılma demektir. Sahiplik sınırı, modül sınırının veri düzeyindeki karşılığıdır.
- **Görünmeyen bağımlılık, görünenden tehlikeli.** Derleme grafiğinde `ingestion → analysis` kenarı zaten yoktu ve ArchUnit temizdi; ama bağımlılık **anlamsal ve zamansal** düzeyde vardı: `ingestion`'ın döküman şeması `analysis`'in kelimelerini taşıyordu ve yazma yolu `analysis` cevap verene kadar bitmiş sayılmıyordu. Derleyicinin göremediği bu bağ, ancak taşıma katmanı değiştiğinde faturasını keser.
- **Ham kayıt gerçekten write-once oluyor.** ADR-005 metnin değişmezliğini garanti ediyordu; döküman ise sonradan güncelleniyordu. Artık kaydın tamamı değişmez (bkz. ADR-005 notu).
- **Senkronluk sözleşmeden çıkıyor.** Event bugün senkron kalıyor, ama istemci bunu görmüyor. Taşıma ileride süreç dışına (ör. RabbitMQ) taşınırsa **hiçbir istemci sözleşmesi değişmez**; o noktada sorgu "henüz analiz edilmedi" döner ve sonucu akış tetikler. ADR-003'ün "İleride" merdiveni ancak böyle tırmanılabilir.
- **Hiçbir veri tek kanala emanet edilmiyor.** SSE tetikleyici olduğu için akış çökse bile gönderen kendi sonucunu sorguyla görür; diğer istemciler de yeniden bağlandıklarında doğru duruma yakınsar. Kırılganlık, SSE'nin kendisinde değil ona veri emanet etmekteydi.

**Alternatifler.**
- *Bugünkü model (dönüş event'i + cevapta `status`/`warnings`):* Sıfır rework. Sahiplik ihlali ve döküman mutasyonu sürer; broker'a geçişte request/reply (RabbitMQ `direct reply-to`) zorunlu hale gelir — bu da broker'ın asıl faydasını (tamponlama, backpressure, dayanıklı teslim) büyük ölçüde harcayıp yerine ağ üzerinden senkronluk koyar. Ayrıca "ham metin analiz patlasa da hayatta kalır" kuralı, timeout ve broker erişilemezliği yazma yolunun içine girdiği için zorlaşır.
- *Cevabın normalize kayıtları da taşıması:* Kullanıcıya tek istekte her şey. Ham kayıt ile türetilmiş kaydın sözleşmelerini birbirine bağlar; `ingestion` `analysis`'in DTO'suna bağımlı hale gelir — yani bugünkü sorunun daha ağır hali.
- *SSE'yi kaldırıp yalnızca sorgu:* FR-13'ü düşürürdü. Kaynak dokümanın "grafik ve özet tablolar anlık olarak güncellenir" isterini yalnızca gönderimi yapan sekme için karşılar; açık duran ikinci sekme güncellenmez. `realtime` modülü, ADR-004 ve T-18 de birlikte düşerdi.
- *`app` katmanında birleştiren bir okuma cephesi:* `app` tüm modülleri gören tek modül olduğu için `GET /incident-reports/{id}`'yi ham kayıt + analiz sonucu olarak birleştirebilirdi ve FR-14 olduğu gibi kalırdı. Yeni bir mimari eleman ve ek dolaylılık getiriyor; v1'de iki isteğe değmedi. İhtiyaç doğarsa sonradan eklenebilir — sahiplik kuralını bozmuyor.

**Sonuçlar.**
- **Silinecek kod:** `RawReportAnalyzedEvent`, `RawReportAnalyzedEventListener`, `IngestionService.markAnalyzed`, `ProcessingStatus`, `IncidentReportResponse`'un `status` ve `warnings` alanları — testleriyle birlikte. T-07'de yazılan çalışan koda geri dönüş; bedeli bilinçli ödeniyor.
- **Eklenecek kod:** `analysis` tarafında ham bildirim başına analiz sonucu kaydı ve `GET /incidents`'a `rawReportId` filtresi (T-22, T-16).
- **İstemci gönderimden sonra bir ek istek atıyor.** Analiz bugün senkron olduğu için bu sorgu yarış koşulu taşımaz: cevap döndüğünde kayıtlar Postgres'te hazırdır.
- **`GET /incident-reports/{id}` daraldı:** analiz durumu ve türeyen kayıt kimlikleri dönmüyor (FR-14 güncellendi). İzlenebilirliğin ham bildirim → olay kayıtları yönü `rawReportId` filtresiyle karşılanıyor; FR-08 korunuyor.
- **SSE payload'ı küçüldü:** olay satır çizmeye değil, ilgi belirlemeye yetecek kadar veri taşıyor. T-18'in kapsamı daraldı.
- ADR-003'ün gerekçelerinden biri geçersizleşti ve o karar revize edildi; ADR-004 ve ADR-005 not düzeyinde netleşti.

**İleride.** Asenkrona geçiş artık sözleşmeyi kırmadan yapılabilir: dinleyici `@Async` ile ayrılır ya da event bir broker'a taşınır; istemci tarafında değişen tek şey, sorgunun bir süre "henüz analiz edilmedi" dönmesi olur — ki arayüz bu durumu zaten ele almak zorunda. Gönderimi yapan istemciye özel bir akış (yalnızca kendi bildirimlerini dinleme) istenirse korelasyon anahtarı (`rawReportId`) sinyalde zaten var. Analiz sonucu kaydı, ileride analiz sürüm bilgisi (hangi katalog sürümüyle üretildi) taşıyacak doğal yerdir — reprocess'in neyi neden yeniden işlediğini de o zaman söyleyebilir.

---

## ADR-022 — Frontend Teknoloji Tabanı: React + TypeScript + Vite

**Karar.** Frontend **ReactJS** ile geliştirilecek; dil **TypeScript**, araç zinciri **Vite**. Grafik ve veri katmanı kütüphaneleri bu kararda sabitlenmiyor; iskelet task'ında (T-23) seçilip aynı dosyaya ayrıca kaydedilecek.

**Bağlam.** Kaynak doküman "Frontend ReactJS ile geliştirilecektir" diyor; dil ve araç zinciri serbest bırakılmış. PRD v2.0 frontend'i kapsama aldı (NFR-12).

**Gerekçe.**
- **TypeScript, backend'de kurulan çizginin istemci karşılığı.** Bu projede sınır ihlalleri çalışma zamanına bırakılmıyor: modül grafiği derlemede kırılıyor (ADR-001), şema uyuşmazlığı `ddl-auto=validate` ile açılışta patlıyor, mimari kurallar ArchUnit'le test ediliyor (ADR-017). API sözleşmesinden sapmanın build'de görünmesi bu tutumun devamı.
- **Vite:** statik çıktı üretiyor (çalışma zamanında Node gerekmez), çok aşamalı Docker imajıyla doğal uyum, geliştirmede yerleşik proxy — TC-17'de "aynı köken" kararı verilirse geliştirme ve üretim davranışı aynı olur.
- **SSR gereksiz:** tek kullanıcılı analist arayüzü, veri canlı, SEO ihtiyacı yok.

**Alternatifler.**
- *JavaScript:* Daha hızlı başlangıç, daha az yapılandırma. Sözleşme değişikliği çalışma zamanında ortaya çıkar; bu projede backend paralel geliştiği ve sözleşme henüz oturmadığı için maliyeti yüksek.
- *Next.js:* SSR ve routing hazır gelir. Bu arayüz için SSR'ın karşılığı yok; çalışma zamanında Node sunucusu, daha ağır imaj ve `docker compose` tarafında fazladan bir servis davranışı demek.
- *Create React App:* Bakımı durmuş; yeni proje için tercih edilmez.

**Sonuçlar.** Build'e bir Node araç zinciri giriyor (backend Maven, frontend npm — iki ayrı build). API tipleri v1'de elle yazılıyor; bu, sözleşme değiştiğinde iki yerde güncelleme demek. İmaj statik dosyalardan ve onları yayınlayan hafif bir sunucudan oluşuyor.

**İleride.** springdoc'un ürettiği OpenAPI şemasından (`NFR-07`) tip üretimi (`openapi-typescript` benzeri) devreye alınırsa elle yazılan tipler ortadan kalkar ve sözleşme sapması build'de yakalanır — bugünkü tercih bunu engellemiyor, yalnızca ertelemiş oluyor.

---

## ADR-023 — Coğrafi İzlenebilirlik: Harita Yerine İl Kırılımı

**Karar.** Kaynak dokümanın "zaman içinde, **coğrafi bölge bazında** grafiksel olarak izlenebilme" isteri, ilin grafik ve özet tabloda bir **kırılım boyutu** olmasıyla karşılanır. v1'de harita (choropleth, GIS, koordinat) yoktur.

**Bağlam.** PRD v1.0 haritayı "frontend işi" diyerek kapsam dışı bırakmıştı; frontend kapsama girince bu gerekçe düştü ve kararın kendi başına savunulması gerekti (FR-24, §2.2).

**Gerekçe.**
- **Kaynak doküman "grafiksel" diyor, "haritasal" demiyor.** Zaman ekseni + il kırılımı isteri lafzıyla ve amacıyla karşılıyor: iller arası karşılaştırma ve zaman içindeki seyir görülebiliyor.
- **`SHARED` kapsamlı kayıtlar haritada tanımsız.** ADR-019 gereği bu sayılar hiçbir tek ile ait değil; haritada boyanacak bir il yok. Bölüştürmek metinde olmayan veriyi uydurmak, gizlemek ise toplamı bozmak olur. Harita bu kayıt için ya yalan söyler ya da susar. Çubuk/çizgi grafikte ise ayrı ve etiketli bir seri olarak **dürüstçe** gösterilebilir; okuyucu il toplamları ile genel toplamı uzlaştırabilir.
- **`UNKNOWN` kapsam için de aynı sorun geçerli:** ilsiz kayıt haritada yer bulamaz, grafikte "il belirtilmemiş" olarak durabilir.
- Efor, projenin asıl zorluğu olan Türkçe metin çıkarımında kalıyor.

**Alternatifler.**
- *Choropleth harita:* Görsel etkisi yüksek. Maliyeti 81 il geometrisi, `SHARED`/`UNKNOWN` için ayrıca bir harita-dışı gösterim ve ek test yükü. Kazanılan şey isterin karşılanması değil, sunumu.
- *Harita + grafik birlikte:* En kapsamlı ve en pahalı; v1 kapsamını asıl işten uzaklaştırır.
- *Yalnızca il filtresi (kırılım yok):* İsteri karşılamaz. "İl bazında izlenebilirlik" tek il seçip bakmak değil, iller arası karşılaştırmadır.

**Sonuçlar.** Çok il seçildiğinde grafikteki seri sayısı artıyor; okunabilirlik için seçim sınırı veya yığılmış (stacked) gösterim gerekebilir — T-28'in kapsamında. Veri modelinde hiçbir değişiklik gerekmiyor.

**İleride.** Harita eklenmek istenirse veri modeli hazır: il zaten kırılım boyutu ve plaka koduyla kayıtlı (ADR-019). Eklenecek tek şey geometri ve `SHARED`/`UNKNOWN` kayıtlar için haritanın yanında duran bir gösterim — bu iki kapsam haritaya hiçbir zaman boyanmayacak.

---

## ADR-024 — Frontend Coverage Kapısı

**Karar.** Frontend'de de satır bazında **≥ %80** coverage eşiği uygulanır ve eşik altında **build kırılır**. Kapı, backend'deki JaCoCo kapısıyla (ADR-018) simetriktir.

**Bağlam.** Kaynak doküman: *"Yazılım geliştirilirken birim testleri yazılmalıdır. Birim testlerin kapsayıcılığı en az yüzde 80 oranında olmalı ve tüm önemli fonksiyonları kapsayıcı olmalıdır."* Cümle backend'e daraltılmamış. PRD v1.0 frontend'i kapsam dışı bıraktığı için NFR-02 fiilen yalnızca backend'e uygulanıyordu; v2.0 ile bu boşluk kapanıyor.

**Gerekçe.**
- **İster tek bir sistem için yazılmış.** Frontend teslimatın parçası olduğuna göre kapı da onu kapsamalı; aksi halde isterin yarısı karşılanmamış olur ve bunun README'de savunulması gerekirdi.
- **Katman ayrımı bunu mümkün kılıyor.** PRD §5.4'teki üç katmandan ikisi (API istemcisi, durum/veri katmanı) DOM'suz test edilebiliyor. Yani kapsam sayısı görünüm katmanına yaslanmak zorunda değil — anlamlı testle tutulabilir.
- **Simetri.** İki tarafta iki farklı standart, pratikte düşük olanın standart olması demektir.

**Alternatifler.**
- *Daha düşük eşik (%60–70):* İsteri kısmen karşılar; sayının neden düşürüldüğünü savunmak gerekir ve savunma zayıftır.
- *Sayısal kapı yok, yalnızca kritik akışlar:* En hızlı yol. Kaynak dokümanın açık bir isterinden bilinçli sapma olurdu ve README'de gerekçelendirilmesi gerekirdi.

**Sonuçlar.** T-23'te ölçüldü: bir bileşenin gövdesi tek bir JSX `return`'ü olduğu için **tek satır** sayılıyor (v8 ve istanbul sağlayıcıları aynı sonucu veriyor). Yani oran, görünüm katmanından değil **mantık dosyalarından** oluşuyor — kapının dişi API istemcisi ve durum katmanında. Bu, kararın gerekçesiyle uyumlu ama sayının ne ölçtüğünü bilerek okumak gerekiyor: görünümleri tutan şey oran değil, davranış testleri. Her frontend task'ı kendi test yükünü taşıyor. Snapshot testleri kapsamı ucuza şişirdiği için tercih edilmiyor; ölçülen şey davranış olmalı (TC-16). SSE, zamanlayıcı ve grafik gibi zamanlama/çizim içeren kod deterministik test için sahte zaman ve sahte akış gerektiriyor — bu, tasarımı da doğru yöne itiyor: yan etkiler enjekte edilebilir olmak zorunda.

**İleride.** Eşik tek bir yapılandırma değerinden yönetiliyor; olgunlaştıkça yükseltilebilir. Uçtan uca (E2E) testler bu kapının kapsamına girmez — kabul kriterleri (§11) onların alanı; ikisini karıştırmak coverage sayısını anlamsızlaştırır.

---

## ADR-025 — Aynı Köken: nginx Reverse Proxy (CORS Yerine)

**Karar.** Tarayıcı API'ye **kendi kökeni üzerinden** ulaşır. Frontend container'ındaki nginx statik dosyaları sunar ve `/api/*` ile `/actuator/health` isteklerini compose ağı üzerinden `backend:8080`'e proxy'ler. Backend'de **CORS yapılandırması yoktur**; frontend kaynak kodunda **mutlak API adresi yoktur**. Geliştirmede aynı davranışı Vite'ın dev proxy'si verir.

**Bağlam.** PRD bunu TC-17 olarak açık bırakmıştı. Frontend ayrı bir portta yayınlanacağı için tarayıcının `localhost:3000`'den `localhost:8080`'e yapacağı çağrılar çapraz kökenli olurdu. Kök `docker-compose.yml`'de bekleyen taslak `VITE_API_BASE_URL` geçiyordu, yani örtük olarak CORS'u varsayıyordu — ama bu bir karar değil, yer tutucuydu.

**Gerekçe.**
- **Backend'de sıfır değişiklik.** CORS bir güvenlik yüzeyidir: hangi kökene, hangi metotlara, hangi başlıklara izin verildiği yanlış yapılabilecek bir liste. Aynı köken bu yüzeyi hiç açmıyor.
- **Geliştirme ve üretim aynı şekilde davranıyor.** Vite dev proxy ile nginx aynı yolları aynı hedefe gönderiyor; "bende çalışıyordu" sınıfı bir fark kalmıyor.
- **NFR-03'ün lafzı.** Kullanıcı **tek adres** açıyor: `http://localhost:3000`. API'nin portu kullanıcının bileceği bir şey olmaktan çıkıyor.
- **Yapılandırılacak bir adres kalmıyor.** İstekler göreli olduğu için `VITE_API_BASE_URL` gereksizleşti — NFR-15'in "adres koda gömülü olmasın" isteri, adresi ortam değişkenine taşıyarak değil **ortadan kaldırarak** karşılanıyor. Bir testle sabitlendi: probe'un göreli yol çağırdığı doğrulanıyor.
- Kimlik doğrulama ileride eklenirse (ADR-011 "İleride") çerez tabanlı oturum aynı kökende sorunsuz çalışır; çapraz kökende `SameSite`/`credentials` ayrı bir iş olurdu.

**Alternatifler.**
- *CORS:* Container daha basit olurdu (yalnız statik dosya sunucusu, nginx konfigürasyonu yok). Karşılığında backend'e yeni bir güvenlik yüzeyi, her istekten önce preflight, dışarı açılan ikinci bir port ve API adresinin build ya da çalışma zamanında enjekte edilmesi gereği gelirdi.
- *Frontend'i backend'in içinden (Spring static resources) sunmak:* Tek origin'i bedavaya verirdi ama iki build'i tek artifact'a bağlar, frontend'in bağımsız dağıtımını ve Vite'ın geliştirme deneyimini kaybettirirdi.
- *Ayrı bir reverse proxy servisi (üçüncü container):* Daha "doğru" bir topoloji ama compose'a bir servis daha ekler; bu ölçekte frontend container'ının kendi nginx'i yeterli.

**Sonuçlar.**
- Bir `nginx.conf` bakım yükü doğdu. İçindeki en kritik satır `proxy_buffering off`: SSE için kapatılmazsa nginx olayları tamponlar, akış **sessizce ölü görünür** ve hiçbir yerde hata çıkmaz. Bu yüzden stream yolu kendi `location` bloğunda, `proxy_read_timeout 1h` ile birlikte duruyor.
- `/actuator` altından yalnızca `health` proxy'leniyor; gerisi operasyonel yüzey ve tarayıcıya açılmasının bir sebebi yok. Doğrulandı: `/actuator/env` proxy'ye düşmüyor, SPA fallback'i olarak `index.html` dönüyor.
- Derin bağlantılar için `try_files ... /index.html` gerekiyor, yoksa `/incidents/123` 404 olurdu (PRD §5.4'teki adreslenebilir ekranlar).
- Backend 8080'de yayımlanmaya devam ediyor — curl, Postman ve `local` profili için — ama frontend oradan geçmiyor.
- **Doğrulandı** (T-23, çalışan sistem): tarayıcıdan `http://localhost:3000` açıldığında uygulama yükleniyor ve `fetch('/actuator/health')` çağrısı `sameOrigin: true` ile 200 dönüyor; backend `UP`, `db` ve `mongo` bileşenleriyle birlikte.

**İleride.** Üretimde TLS sonlandırma gerekirse aynı nginx'e girer; frontend kodunda hiçbir değişiklik olmaz çünkü adresler zaten göreli. Backend birden fazla örneğe çıkarsa `proxy_pass` bir upstream bloğuna dönüşür — ama SSE yapışkan oturum ister, o noktada ADR-004'ün "İleride" notundaki fan-out katmanı gündeme gelir. Frontend ayrı bir CDN'den sunulmak istenirse aynı köken kaybolur ve CORS kararı yeniden açılır; o durumda bu ADR'nin yerini yeni bir karar alır.

---

## ADR-026 — Frontend Kütüphane Seti

**Karar.** Sunucu verisi için **TanStack Query**, yönlendirme ve URL durumu için **React Router**, grafik için **Recharts**. Test tarafı **Vitest + Testing Library**, stil için ek bir çatı kullanılmadan **düz CSS**. ADR-022 bu seçimleri bilinçli olarak açık bırakmıştı; T-23'te karara bağlandı.

**Bağlam.** PRD §5.4 arayüzü üç katmana ayırıyor (API istemcisi / durum / görünüm) ve şunları isteriz kılıyor: tazeleme sırasında görünümün boşaltılmaması (FR-25), filtre durumunun adres çubuğunda yaşaması (FR-21), çok serili ve kümülatif grafik (FR-23, FR-24), ve %80 coverage kapısı (ADR-024).

**Gerekçe.**
- **TanStack Query.** İhtiyaç duyulan davranışlar zaten içinde: önbellek, `keepPreviousData` ile stale-while-revalidate (FR-25'in "görünüm boşaltılmaz" isteri birebir bu), eşzamanlı isteklerin birleştirilmesi, yeniden bağlanınca tazeleme. SSE sinyali geldiğinde yapılacak iş `invalidateQueries` çağrısına iniyor (TC-13). Elle yazılsaydı bunların hepsi yazılacak **ve** %80 kapısının altında test edilecekti.
- **React Router.** Filtre durumu URL'de yaşayacak (FR-21) ve iki detay ekranı adreslenebilir olacak (PRD §5.4). İskelette tek route var; sonradan takılmak yerine baştan yerinde.
- **Recharts.** Belirleyici olan **SVG çizmesi**: grafik DOM'da gerçek düğümler olarak var, dolayısıyla "doğru seriler çizildi mi" sorusu mock'suz sorulabiliyor. Canvas tabanlı bir kütüphanede (ECharts, Chart.js) aynı soru ancak taklit katmanıyla sorulur — ADR-024'ün kapısı altında bu doğrudan maliyet demek. Bildirimsel React bileşenleri çoklu seri, yığılmış görünüm ve seri gizle/göster ihtiyaçlarını doğrudan karşılıyor.
- **Vitest.** Vite ile aynı dönüşüm hattını paylaşıyor; ayrı bir derleyici yapılandırması taşımıyor.
- **Düz CSS.** Arayüz tek bir panel; bir stil çatısı build adımı ve öğrenilecek bir sözlük ekler, ama alınması gereken hiçbir kararı ortadan kaldırmaz.

**Alternatifler.**
- *Sade hook + `fetch`:* Sıfır bağımlılık. Önbellek, stale-while-revalidate, istek birleştirme ve geçersizleştirme elle yazılırdı — yani TanStack Query'nin daha az test edilmiş bir kopyası.
- *Redux Toolkit + RTK Query:* Aynı yetenekler artı global istemci durumu. Bu ölçekte fazla tören: paylaşılan istemci durumu neredeyse yok, filtreler zaten URL'de duracak.
- *ECharts / Chart.js:* Görsel olarak daha zengin, canvas çizdikleri için test edilebilirlikleri düşük; ECharts ayrıca büyük bir bundle ve imperatif yapılandırma getiriyor.
- *Tailwind:* Geliştirmeyi hızlandırırdı; bu boyutta düz CSS'in üstüne çıkacak bir kazanç yok.

**Sonuçlar.** TanStack Query'nin iki varsayılanı T-24'te davranışsal sonuç doğurdu ve not edilmeye değer. `networkMode` varsayılanı `online`: kütüphane çevrimdışı olunduğunu düşündüğünde başarısız bir sorguyu **duraklatıyor**, durumu `pending`'de kalıyor ve ekranda hiç bitmeyen bir "yükleniyor" çıkıyor — FR-28'in yasakladığı şey. API aynı kökende olduğu için (ADR-025) modellenecek ayrı bir çevrimdışı durum yok; `always` seçildi ve davranış testle sabitlendi. İkincisi bir ayar değil, bilinmesi gereken bir tasarım: yeniden denemeler belge **gizliyken** de duraklıyor (`canContinue()` odak durumunu VE ile bağlıyor), yani arka plandaki sekme kendisine bakılana kadar spinner'ını korur. Beş üretim bağımlılığı: React, React DOM, React Router, TanStack Query, Recharts. Recharts iskelette **kullanılmıyor** — ilk kullanıcısı T-28. Şimdi kurulmasının sebebi kararın kaydedilmesi ve React 19 ile birlikte çözülüp derlendiğinin doğrulanması; bu tür bir uyumsuzluğu T-28'de değil bugün öğrenmek gerekiyordu (doğrulandı: `npm run build` temiz geçiyor, üretim bundle'ı 264 kB / gzip 84 kB). TanStack Query'nin önbellek anahtarları filtre durumuyla birebir eşleşmek zorunda; bu, T-26'da filtre durumu tasarlanırken dikkat edilecek nokta.

**İleride.** Grafik ihtiyacı Recharts'ın sınırını zorlarsa (çok büyük veri, özel etkileşim) geçiş yalnızca grafik bileşenlerini etkiler — veri sunucudan hazır geldiği için (NFR-13) dönüştürme mantığı taşınmaz. TanStack Query'nin `invalidateQueries` yüzeyi, SSE sinyalinin bağlanacağı tek nokta olduğu için ileride taşımaya (WebSocket, polling) geçilse bile değişen yer tek kalır.

---

## ADR-027 — Türkçe Normalizasyon: Konum Koruyan Metin ve Elle Yazılmış Cümle Bölücü

**Karar.** Metin, eşleştirmeden önce tek bir yerde normalize edilir (`analysis.text` paketi): NFC birleştirme, **Türkçe locale ile** küçültme, kesme işareti ve görünmez karakter katlama, boşluk sadeleştirme. Normalizasyonun çıktısı düz bir `String` değil, **ham metindeki konumu taşıyan** bir `NormalizedText`'tir. Cümle bölme `BreakIterator` ile değil, elle yazılmış bir kuralla yapılır. TC-5 böylece karara bağlanır.

**Bağlam.** Çıkarım normalize metin üzerinde çalışmak zorunda: `İZMİR`, `İzmir'de`, `izmir` aynı ile işaret ediyor. Ama sözleşme (PRD §8.2, C-3) çıkarılan anahtar kelimenin **ham metindeki offset'ini** dönmeyi, TC-18 de bu offset'in Türkçe ve Unicode karakterlerde kaymamasını istiyor. Normalizasyon ise uzunluğu değiştiriyor: ölçüldü, ayrıştırılmış (NFD) bir metin 21 karakterken NFC'si 15 karakter. İki isteri aynı anda karşılamanın tek yolu, normalize metnin her karakterinin hangi ham aralıktan üretildiğini hatırlaması.

**Gerekçe.**
- **Konum koruma, sonradan eklenemeyecek bir özellik.** Normalizasyon `String`→`String` olarak yazılırsa bilgi geri dönüşsüz kaybolur; C-3 daha sonra ancak ham metinde ikinci bir arama yaparak — yani aynı işi ikinci kez, farklı kurallarla — karşılanabilirdi. `NormalizedText` bu ikinci kopyayı en baştan gereksiz kılıyor.
- **Grafem kümesi bazında dolaşma.** Taban harf ile birleşen işaretleri birlikte tutuyor; aksi hâlde birleştirme yarım karakteri yanlış offset'e bağlayabilirdi.
- **`BreakIterator` ölçüldü ve elendi.** İki isterde de yanılıyor: `"... tespit edildi. 1 kişi vefat etti."` ifadesini **tek** cümle sayıyor (rakamdan önce bölmeyi reddediyor), `"Dr."`den sonra ise bölüyor. Birincisi bir cümledeki sayının başka bir cümlenin metriğiyle eşleşmesine yol açar — TC-3'ün tam olarak engellemesi gereken hata. İkisi de sessiz.
- **Bölücü, bölmemeye eğimli.** Yanlış birleştirilen cümle her sayıyı kendi anahtar kelimesinin yanında tutar; yanlış bölünen cümle ikisini ayırır ve sayı ya kaybolur ya yanlış metriğe yazılır. Birinci hata isabeti, ikincisi doğruluğu düşürür.

**Alternatifler.**
- *Sadece `toLowerCase(Locale.of("tr"))`, offset taşımadan:* En ucuz yol. C-3 ve TC-18 karşılanamazdı.
- *`BreakIterator.getSentenceInstance`:* Standart, bakımsız. Ölçüm iki kritik durumda da yanlış sonuç verdiğini gösterdi.
- *ICU4J:* Daha iyi bir bölücü getirirdi; ~13 MB'lık bir bağımlılığı, elle yazılmış ~40 satırın çözdüğü bir sorun için taşımak orantısız.
- *Cümleye hiç bölmemek:* Metrik eşleştirmenin (T-14) dayandığı yakınlık kavramı ortadan kalkardı.

**Sonuçlar.** `analysis.text` paketi çıkarımın tek girişi oldu; T-10…T-14 ham `String` değil `NormalizedText` görecek ve küçültme/kesme işareti sorunlarını tekrar çözmeyecek. Kısaltma listesi bakım gerektiren bir veri; eksik bir kısaltma cümlenin fazladan bölünmesine yol açar. Kaynak dokümandaki üç örnek metnin üçü de tam üç cümleye bölünüyor ve her cümle ham metindeki karşılığına birebir geri dönüyor — teste sabitlendi. Ölçüm sırasında `String.isBlank()`'in **kırılmaz boşluğu (U+00A0) boşluk saymadığı** ortaya çıktı; webden yapıştırılan metinde sık görülen bu karakter kelimeyi sessizce birleşik bırakıyordu, bu yüzden boşluk kontrolü `SPACE_SEPARATOR` kategorisini de kapsıyor.

**İleride.** Kısaltma listesi büyürse kataloğun yanında YAML'a taşınabilir — ADR-007'nin olay tipleri için kurduğu düzenin aynısı. Çıkarım kalitesi bölme hatalarına takılmaya başlarsa ICU4J yeniden değerlendirilebilir; `SentenceSplitter` tek bir arayüz arkasında olduğu için değişim tek sınıfta kalır. `NormalizedText` şu an karakter bazında offset taşıyor; arayüz vurgulamayı UTF-16 yerine kod noktası bazında isterse (TC-18) dönüşüm bu sınıfa eklenir, çağıranlara yayılmaz.

---

## ADR-028 — Sayı Ayrıştırma: Bileşik Sözcükler, Tarihlerin Dışlanması ve Okunamayan Figürler

**Karar.** Sayılar tek bir bileşende ayrıştırılır (`NumberExtractor`); rakam, Türkçe sözcük ve ikisinin karışımı (`15 bin`) aynı değeri üretir. Bileşik sözcükler **azalan büyüklük** kuralıyla birleşir. Tarih biçimindeki rakam grupları sayı sayılmaz. Değer `long` taşınır, metriğin `int` kolonu ayrıştırma sırasında dayatılmaz. TC-4 böylece karara bağlanır.

**Bağlam.** FR-05 sayıların rakamla **veya** Türkçe sözcükle gelebileceğini istiyor; kaynak dokümandaki ikinci örnek `on iki bina` derken birincisi `15 vaka` diyor. Haber metni ayrıca ikisini karıştırıyor (`2 bin 500 kişi`). Çıkan sayı daha sonra bir metriğe bağlanacak (TC-3), dolayısıyla değeri kadar **metindeki konumu** da gerekiyor.

**Gerekçe.**
- **Azalan büyüklük kuralı, toplamanın kendisinden önemli.** `on iki` = 12 çünkü 2, 10'dan küçük. Aynı kural `bir iki kişi` ifadesinin 3 diye okunmasını engelliyor — bu bir sayı değil, "birkaç" anlamında bir kalıp. Kural olmasaydı yan yana her sayı sözcüğü toplanır ve metinde olmayan bir figür üretilirdi.
- **Tarih üç sayı değildir.** `20.04.2020` ayrıştırılsaydı 20, 4 ve 2020 diye üç makul görünen değer, üstelik bir anahtar kelimenin hemen yanında ortaya çıkardı. Rakamla yazılmış tarih biçimleri bu yüzden atlanıyor. **Sınır açıkça kabul ediliyor:** `3 Mayıs 2020` gibi sözcüklü tarihler burada tanınamaz (takvim bilgisi gerekir), 3 ve 2020 üretilir; bunları elemek çözülmüş tarih aralığını elinde tutanın işi (T-11/T-14). Sınırı gizlemek yerine teste yazdık.
- **Okunamayan bir figür, çarpanını da götürür.** `2,5 milyar lira` içinde ondalık figür sayılmıyor (kişi/bina sayısı ondalık olmaz). Sadece `2,5`'i atmak `milyar`'ı tek başına bırakır ve **bir milyar** okunur — yani okumayı reddetmek, başka bir sayı uydurmaya dönüşür. Reddedilen figür, kendisini takip eden çarpanı da kapsıyor.
- **`long`, metriğin `int`'i değil.** Metnin ne kadar büyük bir sayı söylediğine kolon karar veremez. `iki milyar` dilin ifade edebildiği bir sayı; sessizce `int`'e sarmak *makul olmayan* bir figürü *makul görünen yanlış* bir figüre çevirirdi. `NumberToken.fitsMetricValue()` kararı çağırana bırakıyor.

**Alternatifler.**
- *Yalnızca rakam desteklemek:* FR-05 doğrudan karşılanmazdı; kaynak dokümandaki ikinci örnek çıkarılamazdı.
- *Sözcükleri düz bir sözlükle (`on iki` → 12) eşlemek:* Bileşimler sonsuz; `iki bin üç yüz kırk beş` sözlüğe yazılamaz.
- *Yan yana tüm sayı sözcüklerini toplamak:* `bir iki kişi` = 3 üretirdi.
- *Tarihleri de sayı olarak yayınlayıp sonra elemek:* Eleme bilgisi T-14'e taşınırdı; `20.04.2020` için hiçbir belirsizlik yokken üç yanlış aday üretmenin gerekçesi yok.
- *Ondalıkları tabana yuvarlamak:* `2,5` → 2 metinde olmayan bir kesinlik uydurur.

**Sonuçlar.** `NumberExtractor` `NormalizedText` üzerinde çalışıyor ve offset'leri onun üzerinden ham metne kadar izlenebilir (ADR-027). T-14 iki şeye dikkat etmek zorunda: (1) sözcüklü tarihlerden sızan sayılar (`3`, `2020`), (2) sayı olan ama metrik olmayan ifadeler — kaynak dokümandaki üçüncü örnekte `her iki ilde` ifadesi `2` üretiyor ve bu bir metrik değil. İkisi de teste yazıldı. `bin`/`milyon` gibi çarpanların tek başına sayı sayılması bilinçli (`bin kişi` = 1000).

**İleride.** Sayı sözcüklerinin ekli hâlleri (`ikisi`, `üçü`) şu an tanınmıyor; ihtiyaç doğarsa ek toleransı ADR-027'nin normalizasyon katmanına eklenir, ayrıştırıcıya değil. Sıra sayıları (`3. kişi`) ayrı bir tür olarak işaretlenebilir. Ondalık figürler bir gün metrik olursa (parasal hasar gibi) `NumberToken` bir ölçek/birim alanı kazanır; bugün kataloğda böyle bir metrik yok.

---

## ADR-029 — Zaman Dilimi, Gün Sınırı ve Göreli Aralıkların Tek Güne İndirgenmesi

**Karar.** Bir bildirimin hangi **takvim gününe** ait olduğu `Europe/Istanbul` saatine göre belirlenir (`incident-report.analysis.reporting-zone` ile yapılandırılabilir, varsayılan bu). Zaman damgalarının kendisi UTC `Instant` olarak kalır. Göreli **aralık** ifadeleri tek bir güne indirgenir: geriye bakan pencereler (`son 24 saatte`, `son 3 günde`) **referans günü**, yer değiştiren ifadeler (`dün`, `geçen hafta`) kaydırdıkları günü verir. Açık takvim tarihi, göreli ifadeye üstün gelir. `DEFAULTED` kayıtlar agregasyonlardan **düşürülmez**; kaynak alanı görünür kalır. TC-6 böylece karara bağlanır.

**Bağlam.** ADR-014 tarih kaynağını ve referans tarihin gönderim tarihi olduğunu sabitlemiş, ama zaman dilimini ve aralık semantiğini bilinçli olarak açık bırakmıştı. Kod bu boşluğu geçici olarak `ZoneOffset.UTC` ile doldurmuştu — bir karar değil, bir varsayılan. T-11 bunu karara bağlamak zorundaydı, çünkü çözülen gün doğrudan grafikte görünüyor.

**Gerekçe.**
- **"Şu an saat kaç" ile "hangi güne düşüyor" farklı sorular.** Anlık zaman UTC kalmalı: makineden bağımsız, tek anlamlı. Ama kullanıcı grafikte **kendi gününü** görüyor. Türkiye saatiyle 00:30'da girilen bir bildirim UTC'ye göre bir önceki güne yazılır — kullanıcının çoktan bitirdiği bir güne. `Europe/Istanbul` seçmek bu sapmayı ortadan kaldırıyor; Türkiye 2016'dan beri sabit UTC+3 olduğu için yaz saati sınır vakası da yok.
- **Yapılandırılabilir ama varsayılanlı.** Taze bir klon hiçbir ayar olmadan doğru çalışıyor; geçersiz bir zaman dilimi ise ilk bildirimde değil, uygulama açılışında patlıyor.
- **Aralığı güne yaymak veri uydurmaktır.** "Son 3 günde 9 kaza" ifadesini üç güne bölmek, metnin vermediği bir dağılım üretir — ile atanamayan bir figürü iller arasında bölüştürmemenin (ADR-019) tam olarak aynı gerekçesi. Kaybedilen şey aralığın **genişliği**, günün kendisi değil.
- **Pencere referans günde biter.** "Son 24 saatte" ifadesinin işaret ettiği en savunulabilir tek gün, pencerenin kapandığı gün — yani metnin yazıldığı gün. "Dün" ise bir pencere değil, yer değiştirme; onu referans güne çekmek metindeki bilgiyi silerdi.
- **Açık tarih göreli ifadeye üstün.** Günü adıyla söylemek, işaret etmekten daha kesin. Eşitler arasında metinde önce geçen kazanır.
- **`DEFAULTED` kayıtlar düşürülmez.** Onları agregasyondan çıkarmak veriyi sessizce yok etmek olurdu (ADR-006). Kaynak alanı görünür olduğu için kullanıcı gönderim gününde oluşan yığılmayı görebiliyor.

**Alternatifler.**
- *UTC'de kalmak:* Değişiklik gerektirmezdi. Gece yarısına yakın girilen her bildirim bir gün geriye kayardı ve bu hata yalnızca günlük grafiklerde, sessizce görünürdü.
- *Zaman dilimini kullanıcıdan/istekten almak:* Aynı ham kayıt farklı okuyucularda farklı güne düşer, reprocess belirsizleşirdi. Sistem tek bir ülke için (81 il) çalışıyor.
- *Aralığı gerçek aralık olarak modellemek (başlangıç–bitiş):* En doğrusu; ama kayıt granülaritesini (ADR-019) ve şemayı değiştirir, tüm sorgu/agregasyon yüzeyine yayılır. ADR-014'ün "İleride" bölümünde zaten bu yol açık bırakılmıştı.
- *Aralığı günlere eşit bölmek:* Metinde olmayan bir dağılım uydurur.

**Sonuçlar.** `AnalysisService` metni **bir kez** normalize ediyor ve extractor'lar artık ham `String` değil `NormalizedText` alıyor (ADR-027, ADR-028'de kayıtlı niyet) — offset haritası her extractor için yeniden hesaplanmıyor. Sınıflandırılamayan kayıtlar da artık metinden tarihleniyor: olay tipinin tanınmaması, metnin tarih söyleyip söylemediğinden bağımsız. Buna bağlı olarak "tarih bulunamadı" uyarısı yalnızca gerçekten bulunamadığında veriliyor; tarihi açıkça yazan bir metne bu uyarıyı vermek okuyucuyu uyarıları göz ardı etmeye alıştırırdı. Türkçe ek desenleri **sayılı** tutuldu: serbest bir ek (`ay\p{L}*`) "son iki **ayrı** olayda" ifadesini iki aylık bir pencere, `dün\p{L}*` ise "**dünya**"yı dün sanıyordu — ikisi de sıradan cümleleri sessizce yanlış okuyordu.

**İleride.** Aralık semantiği gerektiğinde `ResolvedDate` bir bitiş günü kazanabilir; bugün kaynağı ve offset'i taşıyor olması bu evrimin ön koşulunu karşılıyor. Zaman dilimi tek bir yapılandırma anahtarında toplandığı için çok ülkeli bir kuruluma geçiş tek noktadan yapılır. Göreli ifade sözlüğü (`dün`, `geçen hafta`, …) büyürse kataloğun yanında YAML'a taşınabilir — ADR-007'nin olay tipleri için kurduğu düzenin aynısı.

---

## ADR-030 — İl Tanıma: Referans Veriden Beslenme, Sayılı Ekler ve İlçe Ayrımı

**Karar.** İl tanıma, 81 ili tohumlayan **Flyway migration'ından** beslenir; kodda ikinci bir liste tutulmaz. Liste açılışta bir kez okunur ve tablo boşsa uygulama ayağa kalkmaz. Türkçe ekler — apostroflu ve apostrofsuz — **sayılı** bir listeyle karşılanır. Bir il adının ardından ilçe/semt/mahalle/köy belirteci geliyorsa o eşleşme il sayılmaz. Her anımsatma offset'iyle birlikte, tekrarlar korunarak döndürülür. TC-7 böylece karara bağlanır.

**Bağlam.** Metinlerdeki iller ekli geliyor (`Ankara'da`, `Kocaeli'nde`, `İzmir'de`) ve apostrof pratikte sık sık düşürülüyor (`Ankarada`). Aynı zamanda 81 adın bir kısmı sıradan Türkçe kelimelerle çakışıyor: **Ordu** (askerî birlik), **Van** (araç), **Muş** (geçmiş zaman eki), **Hatay** ("hata"nın çekimi), **Rize**, **Mersin** (bitki). Ayrıca **Aksaray** hem bir il hem de İstanbul'un bir semti.

**Gerekçe.**
- **Tek doğruluk kaynağı.** Migration hem depolamayı hem tanımayı besliyor; bir il tanınabilir olup saklanamaz (ya da tersi) duruma düşemiyor. Kodda ikinci bir liste, kaçınılmaz olarak birinciyle ayrışırdı.
- **Boş tablo sessiz kalmamalı.** Liste yüklenmemişse her bildirim "il bulunamadı" ile döner ve bu, veri kaybı olarak aylarca fark edilmeyebilirdi. Açılışta patlamak dürüst olan.
- **Serbest ek, sıradan cümleleri ile çevirir.** T-11'de aynı hata tarih ifadelerinde yakalanmıştı (ADR-029): `ay\p{L}*` "son iki **ayrı** olayda" ifadesini pencere sanıyordu. Burada `van\p{L}*` "**vanilya**"yı, `ordu\p{L}*` "**ordular**"ı il yapardı. Ekleri saymak, listeyi biraz uzatıp yanlış okumayı ortadan kaldırıyor.
- **İlçe belirteci ucuz ve kesin bir ayrım.** "İstanbul'un **Aksaray semtinde**" cümlesi tek bir il adlandırıyor; belirteç olmasa 200 km ötedeki bir şehre kayıt açılırdı. Tam bir ilçe sözlüğü taşımak yerine, ilçe olduğunu **metnin kendisinin söylediği** durumları eliyoruz.
- **Tekrarlar korunuyor.** "Bursa'da 8 kaza … Bursa'da 1 kişi" Bursa'yı iki kez, iki farklı şey için anıyor; ikisini tek anımsatmaya indirmek ikinci figürün çapasını almak olurdu.

**Alternatifler.**
- *81 ili koda gömmek:* Migration'la ayrışma riski; ayrıca il eklemek iki yerde değişiklik gerektirirdi.
- *Her analizde veritabanından okumak:* İl listesi yalnızca migration ile değişiyor, migration ise zaten yeniden dağıtım demek. Her bildirimde sorgu atmanın karşılığı yok.
- *Eki serbest bırakmak (`\p{L}*`):* Kod kısalırdı; sıradan cümleler sessizce il üretirdi.
- *Tam ilçe sözlüğü taşımak (973 ilçe):* İlçe/il çakışmasını daha geniş çözerdi, ama bakım yükü ve yeni bir referans veri kümesi getirir; kazanç bugünkü isterlerin ötesinde.
- *Bulanık eşleşme (Levenshtein):* Yazım hatalarını yakalardı, ama "Ordu"/"Bolu" gibi kısa adlarda yanlış eşleşmeyi patlatırdı.

**Sonuçlar.** `ProvinceExtractor` bir Spring bileşeni değil, `AnalysisConfiguration` içinde referans veriden kurulan bir bean; testler onu düz bir isim listesiyle kurabiliyor, veritabanı gerekmiyor. İl adları da metinle **aynı normalizasyondan** geçiriliyor (ADR-027) — iki benzer ama ayrı kural yerine tek kural. Kabul edilen sınırlar: kısa/halk arasındaki kullanımlar (`Urfa`, `Antep`, `Maraş`) tanınmıyor, çünkü referans listede yoklar; belirtme hâli (`Hatay'ı`) bilerek eşleşmiyor, zira aynı ek "hatayı" kelimesini il yapardı — kaçırmak, uydurmaktan iyi. İlin metinde **hangi kapsamla** (SINGLE / SHARED / UNKNOWN) yer aldığına burada karar verilmiyor; o T-14'ün işi (ADR-019).

**İleride.** Halk arasındaki kısa adlar ve yaygın yazım hataları, referans veriye bir **eş ad (alias)** tablosu eklenerek karşılanabilir — migration yine tek kaynak olarak kalır. İlçe çakışması büyürse ilçe listesi de referans veriye alınıp "ilçe adı + farklı il bağlamı" kuralı güçlendirilebilir. Ek listesi büyürse kataloğun yanında YAML'a taşınabilir (ADR-007 düzeni).

---

## ADR-031 — Sınıflandırma: Tek Anahtar Kelime Eşiği, Sayısal Güven Yerine Kanıt, Çoklu Tip

**Karar.** Bir olay tipini tetiklemek için **tek bir katalog anahtar kelimesi** yeterlidir. Sayısal bir **güven değeri saklanmaz**; yerine eşleşen anahtar kelimeler ham metindeki konumlarıyla birlikte kanıt olarak taşınır. Birden fazla tip tetiklendiğinde **kazanan seçilmez** — eşleşen her tip, sıralanmış olarak bildirilir. Hiçbiri eşleşmezse cevap yine bir cevaptır: `OTHER` / `UNCLASSIFIED`. TC-8 böylece karara bağlanır.

**Bağlam.** PRD §10 skor/eşik ve güven değerini açık bırakmıştı. Kaynak dokümanın **birinci örneği** bu soruyu fiilen kapatıyor: "15 yeni vaka tespit edildi" cümlesinde olay tipi adlandıran tek kelime `vaka`. İki anahtar kelime isteyen bir eşik, sistemin kendi kabul testini (PRD §11) düşürürdü.

**Gerekçe.**
- **Eşik nereye konacak sorusu aslında yok.** Kabul kriteri tek kelimelik kanıtla sınıflandırmayı zorunlu kılıyor. Dolayısıyla gerçek soru "barı nereye koyalım" değil, "barı geçenle ne yapalım".
- **Sayısal güven, kimsenin savunamayacağı bir eşik davet eder.** `0.72` tanımlı bir anlamı olmayan bir sayı; okuyucu onunla ne yapacağını bilemez. Buna karşılık "deprem olarak sınıflandı, çünkü **deprem** ve **enkaz** şu konumlarda geçiyor" doğrulanabilir bir gerekçe. Kural tabanlı bir hattın modele karşı asıl kazancı zaten bu açıklanabilirlik (ADR-008), ve kanıtı zaten saklıyoruz (FR-17, C-3). Güven kolonu ayrıca migration ve tüm sorgu/DTO yüzeyine yayılma maliyeti getirirdi.
- **Bir metin gerçekten iki şey hakkında olabilir.** "Depremin ardından çıkan yangın" hem deprem hem yangın. Kayıt granülaritesi zaten bir bildirimden olay tipi başına bir kayıt üretilmesine izin veriyor (ADR-019); burada tek kazanan seçmek, veri modelinin taşımak için kurulduğu bir kaydı atmak olurdu.
- **Sıralama kanıta bakar.** Önce kaç **farklı** anahtar kelimenin eşleştiği, sonra bu kelimelerin kapladığı toplam uzunluk — `trafik kazası`, `kaza`'dan daha spesifik ve uzunluk bunun en ucuz dürüst göstergesi. Kalan eşitlik kataloğun kendi sırasıyla çözülür: keyfi, ama her seferinde aynı keyfi cevap.
- **Skor tekrarları saymaz.** Aynı kelimenin beş kez geçmesi, beş farklı kelimenin geçmesinden daha zayıf bir kanıt; skor **farklı** anahtar kelime sayısıdır. Tekrarların hepsi yine de kanıt listesinde durur.

**Alternatifler.**
- *İki veya daha fazla anahtar kelime istemek:* Birinci örnek sınıflandırılamazdı.
- *Ağırlıklı skor + sayısal eşik (ör. TF-IDF):* Ayarlanacak bir parametre ve açıklanamayan bir sayı getirir; katalog beş tipken kalibre edilecek veri de yok.
- *Tek kazanan seçmek:* Kod basitleşirdi; iki olaydan biri sessizce kaybolurdu.
- *Eşleşmeyen metni reddetmek:* ADR-006 ile doğrudan çelişir.
- *Güven değerini saklamak:* Migration + DTO + arayüz maliyeti; karşılığında yorumlanamayan bir sayı.

**Sonuçlar.** `EventTypeClassifier` her zaman **en az bir** sonuç döndürür, dolayısıyla çağıranın "hiç sonuç yok" durumunu ayrıca ele alması gerekmez. Katalog anahtar kelimeleri bilerek gövde olarak yazılıyor (`hayatını kaybet`, `kurtarıl`), bu yüzden Türkçe ekler üzerinden eşleşiyorlar — ve ekler yine **sayılı**: serbest bir ek `testere` kelimesini `test` anahtarı sanardı; bu tuzağın hattaki üçüncü görünüşü (ADR-029, ADR-030). Kataloğun ifade edemediği bir çekim, koda değil **kataloğa** eklenir (ADR-007). Sınıflandırıcı bu task'ta boru hattına **bağlanmadı**: bir kaydın hangi metriklerle ve hangi il kapsamıyla oluşacağı T-14'ün kararı, ve extractor'ı yarım bağlamak yerine tüm parçalar hazırken birleştirmek daha az risk taşıyor.

**İleride.** Katalog büyüyüp anahtar kelimeler çakışmaya başlarsa, kelime başına bir **ağırlık** alanı kataloğa eklenebilir — karar yine yapılandırmada kalır, kodda değil. Sıralamanın "toplam kanıt uzunluğu" ölçütü, gerekirse kelime uzunluğu yerine açık bir spesifiklik alanıyla değiştirilebilir. Kanıt konumları bugünden saklandığı için, ileride bir güven göstergesi istenirse (soluk seri, uyarı rozeti) veri zaten yerinde olur.

---

## ADR-032 — Sayı ↔ Metrik Eşleştirme ve İl Kapsamının Belirlenmesi

**Karar.** Bir sayı, **aynı cümlede kendisinden sonra gelen en yakın** metrik anahtar kelimesine bağlanır; yoksa kendisinden önceki en yakına. Bulunduğu hâldeki (`-da/-de/-ta/-te`) metrik anahtar kelimeleri **durum bildirir**, sayılan şeyi değil, ve aday sayılmaz. Bir sayı **tek bir kayda** girer: en güçlü sınıflanmış olay tipine. İl ataması **cümle sırasından bağımsızdır**: paylaşım işaretçisi varsa `SHARED`, yoksa cümlenin kendi ili, yoksa metnin baştan sona tek ili, o da yoksa `UNKNOWN`. Tarih ifadelerinin ve paylaşım işaretçisinin **kendi rakamları metrik değeri değildir**. TC-3 böylece karara bağlanır.

**Bağlam.** T-09…T-13 bir metnin *içinde ne olduğunu* çıkarıyordu: bir tarih, birkaç il, birkaç olay tipi, birkaç sayı. Hiçbiri henüz bir kayıt değil — çünkü kayıt, hangi sayının hangi ilin hangi olayına ait hangi metriği olduğunu söylüyor, ve metin bunu yalnızca **yakınlık** üzerinden söylüyor. Kaynak dokümanın üçüncü örneği bu soruyu tek başına dört ayrı yerden zorluyor.

**Gerekçe.**
- **İleri yön anlamı taşır.** Türkçe sayılan şeyi sayıdan sonra koyar: "15 yeni vaka", "dokuz kişi enkazdan sağ olarak kurtarıldı". Geriye bakış yalnızca devrik kullanımları ("yaralı sayısı 12") yakalamak için var.
- **Bulunma hâli sayılan şey değildir.** "2 kişi **kazalarda** hayatını kaybetti" iki ölüdür, iki kaza değil — ama "kazalarda" sayıya "hayatını kaybetti"den daha yakın. Salt yakınlık bu cümleyi yanlış okur; ekin kendisi doğru okumayı veriyor.
- **İl ataması sıradan bağımsız olmak zorunda (FR-04).** İlk yazdığım kural "metinde en son geçen il"di ve sıralı üç örnekte de doğru çalışıyordu. Cümleler karıştırıldığında çöktü: örnek 1'in ili son cümleye taşındığında ilk iki figür ilsiz kalıyordu. Bu yüzden kural şu oldu: cümlenin kendi ili, o yoksa **metnin tek ili**. Birden fazla il varken ve cümle hiçbirini anmıyorken tahmin edilmiyor — `UNKNOWN` kalıyor. Sıralamaya bakan bir kural bu örneklerde çalışıp gerçek metinlerde sessizce kayar.
- **Paylaşılan toplam bölüştürülmüyor, düşürülmüyor.** "Her iki ilde toplam 10 kişi yaralı" ne beşer beşer dağıtılıyor ne de atılıyor; ayrı bir `SHARED` kayıt oluyor (ADR-019).
- **İşaretçinin kendi sayısı da metrik değil.** "Her **iki** ilde toplam **10**" ifadesindeki iki, ili sayar. İlk sürümde `INJURED` 12 çıkıyordu — tarih rakamlarını dışlamakla aynı sınıftan bir hata.
- **Bir sayı bir kayda.** Anahtar kelimelerin çoğu (ör. `hayatını kaybet`) birden çok olay tipinde ortak; sayı en güçlü sınıflanmış tipe gider, aksi hâlde aynı figür iki kayıtta birden sayılırdı.
- **Türkçe morfolojisi iki ek daha istedi.** `hayatını kaybet` gövdesi metinde "kaybed**erken**" olarak geçiyor: hem ulaç eki (`-erken`) hem de son ses yumuşaması (t→d). Bunlar kurala bağlandı; kataloğun her fiili iki yazımla listelemesi gerekmiyor.

**Alternatifler.**
- *Bire bir atama (her sayıya bir anahtar kelime, en küçük toplam mesafe):* "1 ve 2 kişi … hayatını kaybetti" cümlesinde ikisini farklı metriklere dağıtırdı; oysa ikisi de ölü.
- *Cümlenin son metrik anahtar kelimesini yüklem saymak:* Örnek 3'te doğru, örnek 2'de yanlış — orada iki figür iki ayrı metriğe gidiyor.
- *Paylaşılan toplamı illere bölüştürmek:* Metnin vermediği bir dağılım uydurur (ADR-019).
- *Paylaşılan toplamı atmak:* İl toplamları ile genel toplam bir daha uzlaşmazdı.
- *"En son geçen il" kuralını korumak:* Kod daha kısaydı ve üç örnekte de geçiyordu; FR-04'ü ihlal ediyordu ve bunu ancak karıştırma testi gösterdi.

**Sonuçlar.** `CatalogIncidentExtractor` boru hattının tek girişi oldu ve `UnclassifiedIncidentExtractor` **kaldırıldı** — aynı arayüzü uygulayan ikinci bir bean, sonraki okuyucuya "hangisi çalışıyor" sorusunu sordururdu; uyarı metinleri `ExtractionWarnings` altında toplandı. `KeywordMatcher` artık hem ham hem normalize konumu taşıyor: kullanıcıya gösterilen konum ham metinde (C-3), akıl yürütme ise normalize metinde yapılıyor ve boşluk sadeleşmesi yüzünden biri diğerinden **türetilemiyor**. Kaynak dokümandaki üç örneğin tamamı, çalışan sistemde PRD §11 tablosuyla birebir eşleşiyor. `analysis.extraction` %99 kapsamda.

**İleride.** Metrik anahtar kelimelerine kataloğun kendisinde bir **birim** alanı (kişi / bina / olay) eklenirse, "kazalarda" ayrımı ek tahmininden çıkıp doğrudan veriye dayanır. Türkçe ek listesi büyüdükçe bir morfoloji kütüphanesi (Zemberek) değerlendirilebilir; bugünkü liste sınırlı ve her maddesi gerçek bir metinden geliyor. Cümle başına ayrı tarih çözümü (bugün rapor düzeyinde tek tarih) granülariteyi bozmadan eklenebilir — `ResolvedDate` zaten konum taşıyor.

---

## ADR-033 — Okuma Ucunun Şekli: Kapsam Filtresi, Uç Seviyesinde Analiz Sonucu ve DTO Döndüren Okuma Servisi

**Karar.** İl filtresi, o ile ait kayıtların **yanı sıra** figürü o ili de kapsayan `SHARED` kayıtları da döndürür; birden fazla il seçildiğinde bağlantı tablosu üzerinden `DISTINCT` ile **bir kez** döner. Analiz durumu ve uyarıları kayıt başına değil, **uç seviyesinde** (`analysis` alanı) döner ve yalnızca `rawReportId` ile sorulduğunda dolar. Okuma servisi entity değil **DTO** döndürür. Anahtar kelime filtresi ham metinde değil, çıkarımın kaydettiği anahtar kelimelerde arar.

**Bağlam.** Gönderim yalnızca kimlik döndüğü için (ADR-021), bir bildirimden ne çıktığını öğrenmenin tek yolu bu uç (C-5). Aynı uç FR-10'un filtreli tablosunu da besliyor. `SHARED` kapsam ise filtrelemeye özel bir sorun çıkarıyor: bir figür hiçbir tek ile ait değil, ama il seçildiğinde görünmezse il toplamları genel toplamla uzlaşmıyor (ADR-019).

**Gerekçe.**
- **`SHARED` kayıt bağlantı tablosundan yakalanır.** `province` kolonu boş olduğu için doğrudan eşleşemez; düşürmek ise okuyucunun toplamları uzlaştırmasını imkânsız kılardı. Birden fazla il seçildiğinde aynı kayıt her il için bir kez eşleştiğinden sorgu `DISTINCT` — aksi hâlde 10 yaralı, iki il seçildiğinde 20 görünürdü.
- **Analiz sonucu kayıtların üstünde değil, yanında.** Belirleyici olan **başarısız** durum: analiz çöktüğünde hiç kayıt yoktur, dolayısıyla kayıt başına bir alan asla görünmez. Boş bir liste ve hiçbir açıklama, bu ucun tam olarak engellemek için var olduğu şey. Genel listelemede alan hiç dönmüyor: farklı raporlardan gelen kayıtlar karışıktır ve tek bir sonuç hiçbirini tarif etmez.
- **`failureReason` cevapta yok.** Sunucu tarafı bir teşhis; hata sözleşmesi yığın izlerini dışarı vermemeyi zaten şart koşuyor. Çağıranın üzerinde işlem yapabileceği şey durum ve uyarılardır.
- **Okuma servisi DTO döndürür — bu bir üslup tercihi değil.** Uygulama `open-in-view: false` ile çalışıyor, yani oturum transaction ile birlikte kapanıyor. Entity'yi controller'a vermek, yarı yüklenmiş bir nesne vermek demek: metrikleri, anahtar kelimeleri veya paylaşılan illeri sonradan okumak `LazyInitializationException` atıyor. **Bu hata fiilen yaşandı** — birim ve depo testleri geçtiği hâlde çalışan sistem 500 döndü, çünkü test transaction'ı iddiaların etrafında açık kalıyor. Eşleme artık okuma transaction'ının içinde.
- **Anahtar kelime araması çıkarıma bakar.** Ham metinde tam metin arama kapsam dışı (PRD §2.3); ayrıca bir kaydın var olma sebebi zaten o anahtar kelimeler.
- **Olay tipi etiketsiz, il adlı.** Katalog yapılandırma ve dağıtım olmadan büyüyebiliyor, bu yüzden etiketlerinin tek çalışma zamanı kaynağı metadata ucu (ADR-007). 81 il ise sabit referans veri; adı da dönmek tabloyu ikinci bir aramaya gerek kalmadan çizilebilir kılıyor.

**Alternatifler.**
- *`SHARED` kayıtları il filtresinde gizlemek:* Sorgu basitleşirdi; il toplamları ile genel toplam bir daha uzlaşmazdı.
- *`DISTINCT` yerine sonuçları bellekte tekilleştirmek:* Sayfalama toplamları yanlış çıkardı.
- *Analiz sonucunu her kayda koymak:* Başarısız analizde hiç kayıt olmadığı için asla görünmezdi.
- *Ayrı bir uçtan analiz durumu sunmak:* İki istek gerektirir; C-4 tek istekte istiyor.
- *`open-in-view`'i açmak:* Lazy yükleme çalışırdı, ama HTTP katmanında sessiz sorgular ve kapanmayan oturumlar getirirdi — kapalı olması bilinçli bir tercih.

**Sonuçlar.** `IncidentQueryService` okuma modelinin sahibi: sorguyu çalıştırıyor ve cevabı **transaction içinde** kuruyor. Cevap `IncidentPageResponse`, `PageResponse`'un alan adlarını tekrarlıyor ama nesting yapmıyor; istemci her yerde aynı alan adlarını okuyor, artı bir `analysis` alanı. FR-08'in "ham bildirimden türeyen kayıtlara ulaşma" yönü böylece kapandı — T-11'de açık bıraktığım boşluk.

**İleride.** Anahtar kelime araması bugün `LIKE` ile ve veritabanının küçültme kurallarıyla çalışıyor; Türkçe'ye tam duyarlı arama için PostgreSQL'de bir dil yapılandırması veya normalize edilmiş bir arama kolonu gerekir. Sayfalama offset tabanlı; veri büyürse anahtar tabanlı (keyset) sayfalamaya geçmek sıralama sözleşmesini korur. `IncidentPageResponse`'un `PageResponse` ile alan tekrarı, generic bir zarf tipi gerekirse tek noktada toplanabilir.

---

## ADR-034 — Canlı Akışın Yaşam Döngüsü: Rapor Başına Sinyal, Commit Sonrası Yayın, Heartbeat ile Temizlik

**Karar.** `GET /api/v1/stream/incidents` tek yönlü bir SSE akışıdır ve şu beş kararla çalışır:

1. **Sinyalin birimi kayıt değil, rapordur.** Analizi biten her ham bildirim için **bir** mesaj yayınlanır; mesaj o raporun ürettiği kayıtları listeler: `{ rawReportId, analyzedAt, incidents: [{ incidentId, occurredOn, eventType, provinceCodes[] }] }`. SSE olay adı `incidents`.
2. **Yayını, kayıtların sahibi tetikler; `realtime` yalnızca taşır.** `analysis` işini bitirince `shared`'daki `IncidentRecordsProducedEvent`'i yayınlar, `realtime` onu `@TransactionalEventListener(AFTER_COMMIT)` ile dinler. Yayın **commit'ten sonra**dır.
3. **Başarılı her analiz yayınlanır — sıfır kayıt üretse bile.** Başarısız analiz **hiçbir şey yayınlamaz**.
4. **Bağlantı yaşam döngüsü:** abonelikte anında bir yorum satırı yazılır; her `20s`'de bir yorum (heartbeat) gider; bir abonelik `30m` sonra sunucu tarafından kapatılır; abonelik dört kapıdan birinden çıkar — istemci kapattı, süre doldu, konteyner hata bildirdi, ya da yazma denemesi patladı. İkisi de yapılandırılabilir (`incident-report.realtime.*`).
5. **Akış durumsuzdur:** `Last-Event-ID` ile tekrar oynatma yoktur, istemci bazlı filtre yoktur, mesaj kuyruğa alınmaz. Kaçan bir mesaj veri kaybı değil, gecikmiş tazelemedir.

**Bağlam.** ADR-004 taşıma katmanını (SSE) ve ADR-021 sözleşmeyi (veri değil, sinyal) seçmişti; PRD §10'da TC-10 olarak duran kısım bağlantı yönetiminin kendisiydi: timeout, kopma, çok istemcili yayın. Bunlar arayüzden görünmeyen ama bedeli sunucuda ödenen sorulardır — uzun ömürlü HTTP bağlantıları ADR-004'ün "Sonuçlar" bölümünde açıkça bir risk olarak bırakılmıştı. Frontend tarafı (T-29) bu ucun ne söylediğini varsayarak yazılacağı için sözleşme bu task'ta sabitlenmek zorundaydı.

**Gerekçe.**

- **Rapor başına tek mesaj, istemciyi debounce yazmaktan kurtarır.** Bir metin rutin olarak birden fazla kayıt üretiyor (üçüncü örnek metin üç kayıt). Kayıt başına mesaj, tek bir gönderim için üç tazeleme demekti; istemci ya üç kez sorgu atacak ya da sunucunun bildiği bir gerçeği (bunlar aynı gönderimden geldi) kendi tarafında yeniden kurmak zorunda kalacaktı. Değişimin birimi rapordur, çünkü analiz raporu bir bütün olarak yeniden inşa eder.
- **`provinceCodes` bir liste, kapsam adı değil.** Sinyalin tek işi "bu bana göre mi?" sorusunu cevaplatmak. İl filtresi hem o ile ait kayıtları hem figürü o ili kapsayan `SHARED` kayıtları döndürdüğü için (ADR-033), istemcinin ihtiyacı olan şey kapsamın **adı** değil, kaydın hangi illere cevap vereceğidir. Kod listesi bu soruyu üç kapsam için de tek bir kesişim testiyle cevaplıyor; `SINGLE` bir kod, `SHARED` birkaç kod, `UNKNOWN` sıfır kod taşır.
- **Commit'ten önce yayın, sessiz bir tazelik hatasıdır.** `analysis` event'i kendi transaction'ının içinden yayınlıyor. Düz `@EventListener` ile mesaj, PostgreSQL commit etmeden istemciye ulaşırdı; istemci hemen sorgulayınca **önceki** durumu görür, ve akış hiçbir şeyi iki kez göndermediği için o istemci bir sonraki alakasız gönderime kadar eski veride kalırdı. Hata mesajsızdır — en pahalı türü. `AFTER_COMMIT` ayrıca geri alınan bir analizin hiç duyurulmamasını da sağlıyor: doğru cevap zaten "hiçbir şey değişmedi".
- **Sıfır kayıt da bir değişimdir.** Reprocess önce siler sonra yazar; kuralların daraldığı bir durumda sonuç boş olabilir. Silinen satırları gösteren istemcinin bunu öğrenmesinin başka yolu yok. Buna karşılık **başarısız** analiz hiçbir şey yazmadığı için yayınlanacak bir değişim de üretmez; gönderen sonucu zaten sorgudan okur (ADR-021).
- **Heartbeat, istemci için değil sunucu için de var.** Sekmesini aniden kapatan bir istemci arkasında **yazılana kadar sağlıklı görünen** bir soket bırakır. Periyodik yorum, bağlantıyı boşta kapatacak proxy'leri engellemenin yanı sıra ölü aboneliği ortaya çıkaran şeydir — TC-10'daki "kopma/temizlik" maddesinin gerçek cevabı budur. Yorum (`:`) seçildi, olay değil: `EventSource` yorumu hiçbir dinleyiciye iletmez, dolayısıyla heartbeat hiçbir zaman "bir şey oldu" diye okunamaz.
- **Abonelikte anında yazmak, sessiz sistemi bozuk sistemden ayırır.** İlk bayt yazılmadan yanıt commit edilmez; tarayıcı `onopen`'ı görmez, araya giren proxy bağlantıyı kurulmuş saymaz. Olaysız geçen ilk on dakika, kopuk bir bağlantıyla birebir aynı görünürdü.
- **Süreli abonelik, sızıntının tavanıdır.** Timeout istemciye bir şey kaybettirmez (`EventSource` kendisi yeniden bağlanır, akış veri taşımaz), ama sunucunun temizlemeyi kaçırdığı bir bağlantının ne kadar yaşayabileceğini sınırlar.
- **Durumsuzluk, ADR-021'in doğal sonucu.** Akış veri kaynağı olmadığı için tekrar oynatma da gerekmez: yeniden bağlanan istemci sorgu uçlarından doğru duruma yakınsar. Tampon tutmak, kaçırılmış mesajı "kurtarılması gereken veri" haline getirir — yani akışa tam da vermemeye karar verdiğimiz rolü geri verir.
- **İstemci bazlı filtre yok.** Kimlik doğrulama olmadığı için (ADR-011) kişiye özel görünüm de yok; ayrıca ilgiyi istemci zaten sinyalden belirliyor ve **gösterdiği** filtrelerle yeniden sorguluyor. Sunucuda filtre tutmak, aynı kuralın ikinci bir kopyasını akış tarafında büyütürdü.

**Alternatifler.**

- *Kayıt başına olay:* Sözleşme daha basit görünür. Tek gönderim için N tazeleme; ilişkiyi istemcide yeniden kurmak gerekir.
- *Düz `@EventListener` (commit'ten önce):* Bir anotasyon daha az. Yukarıdaki yarış koşulunu üretir; testte de görünmez, çünkü test transaction'ı iddiaların etrafında açık kalır — ADR-033'te aynı sınıftan bir hata fiilen yaşandı.
- *`realtime`'ın olayı zenginleştirmek için `analysis`'e sorması:* Mesaj daha dolu olurdu. Modül grafiğine yeni bir kenar, gönderim isteğinin içine fazladan bir sorgu ve akışa "veri kaynağı" rolü ekler; ArchUnit kuralı bunu artık derleme zamanında değil, test zamanında da kapatıyor.
- *Kuyruk + `Last-Event-ID` ile tekrar oynatma:* Kaçan mesaj telafi edilirdi. Sunucuda durum, bellekte sınırsız büyüme riski ve çok örnekli dağıtımda paylaşılan bir kuyruk gerektirir — akışın veri taşımadığı bir tasarımda karşılığı olmayan bir maliyet.
- *Heartbeat yerine yalnızca uzun timeout:* Daha az yazma. Ölü bağlantı timeout'a kadar (yani yarım saate kadar) kaynakta durur ve boştaki proxy'ler bağlantıyı kendileri keser.
- *Heartbeat'i olay olarak göndermek:* Bağlantı canlılığı istemciye de görünürdü. Her dinleyici bunu ayıklamak zorunda kalır; ayıklamayı unutan istemci 20 saniyede bir boşuna sorgu atar.

**Sonuçlar.**
- `realtime` modülü artık kodlu: `pom.xml`'deki `failIfNoTests` override'ı (T-03'ten beri duran istisna) kaldırıldı, modül genel kapıya tabi. Modül `spring-tx`'e bağlandı — yalnızca `@TransactionalEventListener` için; veri tabanı bağımlılığı yok ve ArchUnit bunu doğruluyor.
- Zamanlama (`@EnableScheduling`) uygulamada ilk kez bu modül için açıldı. Başka hiçbir yerde zamanlanmış iş yok.
- **Yayın gönderim isteğinin thread'inde kalıyor** (ADR-003). Sonucu: istemcinin kendi gönderimine ait sinyal, POST cevabından **önce** ulaşabilir. Doğru davranış — istemci zaten yeniden sorguluyor — ama frontend tarafında "önce kimliği bilirim" varsayımı yapılamaz.
- **Çok örnekli dağıtımda yayın örnek başına kalır:** bir örneğe bağlı istemci, başka bir örnekte işlenen gönderimi duymaz. Bugün tek örnek çalıştığı için (ADR-010) sorun değil; ölçeklenirse çözüm ADR-004'te yazılı fan-out katmanıdır.
- **Postman koleksiyonu bu ucu kapsamıyor.** Hiç bitmeyen bir istek otomatik koşuya (`newman`) girdiğinde koşuyu askıda bırakır. Uç, `curl -N` ile canlı doğrulandı; koleksiyonun `README`'sine bu not düşüldü.
- nginx tarafında `proxy_buffering off` zaten T-23'te yazılmıştı (ADR-025); bu karar onu bir gereklilik olarak sabitliyor — tamponlama açıkken akış çalışır görünüp sessiz kalır.

**İleride.** Kaçan mesajın da telafi edilmesi istenirse doğal adım `Last-Event-ID` + kısa bir halka tampondur; sinyal zaten kimlik taşıdığı için mesaj şekli değişmez. Gönderimi yapan istemciye özel bir akış (yalnızca kendi bildirimleri) gerekirse korelasyon anahtarı `rawReportId` sinyalde hazır. Analiz asenkrona taşınırsa bu karar aynen geçerli kalır: `AFTER_COMMIT` o zaman istek thread'i yerine dinleyicinin thread'inde çalışır, istemci sözleşmesi değişmez. Çok örnekli dağıtımda `IncidentStream` arayüzü değişmeden altına Redis Pub/Sub gibi bir fan-out konabilir.

---

## ADR-035 — Yeniden İşleme ve Mükerrer Gönderim: Aynı Metin İkinci Kayıt Açmaz

**Karar.** İki ayrı soru, tek bir uçta buluşuyor:

1. **Reprocess.** `POST /api/v1/incident-reports/{id}/reprocess`, saklanmış metnin **aynı gönderim event'ini yeniden yayınlar**. `analysis` tarafı bunu ilk analizden ayırt etmez — tek kod yolu. Ham kayda **hiçbir şey yazılmaz**; türeyen kayıtlar silinip yeniden üretilir. Cevap gönderimle **aynı makbuz**, durum kodu **200** (yaratılan bir şey yok). Bilinmeyen kimlik **404**. Event'te taşınan zaman, raporun **orijinal** gönderim zamanıdır.
2. **Mükerrer gönderim (TC-9).** Ham metnin **SHA-256** özeti dökümanda `textHash` alanında saklanır ve üzerinde **unique + sparse** bir MongoDB indeksi vardır. Aynı metin ikinci kez gönderilirse yeni kayıt açılmaz, event yayınlanmaz; mevcut kaydın makbuzu **200** ile döner (yeni kayıt **201**). Karşılaştırma **birebir bayt** üzerindedir: kırpma yok, normalizasyon yok. İndeks, `app` tarafındaki genel bir `auto-index-creation` anahtarıyla değil, `ingestion`'ın kendi başlangıç bileşeniyle (`RawIncidentReportIndexes`) kurulur.

**Bağlam.** ADR-012 reprocess yeteneğini karara bağlamış ama "mükerrer kayıt oluşmaması"nı bir tasarım detayı olarak task aşamasına bırakmıştı. Bu arada T-22'de `AnalysisService` zaten "önce sil, sonra yaz" biçiminde yazıldı, yani reprocess'in yarısı fiilen hazırdı; eksik olan tetikleyen uçtu. TC-9 ise bambaşka bir soruydu: aynı metnin iki kez **gönderilmesi**. Sistem sayı üretiyor — vaka, ölü, yaralı — ve bu sayılar kayıt sayısıyla doğrudan orantılı.

**Gerekçe.**

- **Reprocess yeni bir event tipi değil, aynı event.** `RawReportSubmittedEvent`'i yeniden yayınlamak, dinleyici tarafında "ilk analiz" ile "yeniden analiz" ayrımını hiç var etmiyor. Ayrı bir event tipi, `analysis` içinde ikinci bir kod yolu ve zamanla iki kural seti demekti; bugün ikisinin aynı davranması **yapısal**, hatırlanması gereken bir şey değil.
- **Orijinal gönderim zamanı taşınıyor, `now()` değil.** Göreli ifadelerin ve tarihsiz metinlerin referansı odur (ADR-014). Bugünün saatiyle yeniden işlemek, iki yıllık bir raporu bugüne taşırdı: iyileştirmek için yapılan işlem, iyileştirmeyi amaçladığı geçmişi bozardı.
- **200, 201 değil.** Reprocess hiçbir şey yaratmıyor. Ham metin işlemin girdisi, konusu değil; türeyen kayıtlar da ekleniyor değil, **yerine geçiyor**.
- **Cevap yine makbuz.** Sonucu cevaba koymak, ADR-021'in gönderim için çözdüğü sorunu reprocess için geri getirirdi. İstemci sonucu yine `GET /incidents?rawReportId=...` ile okuyor — **tek** okuma yolu, iki değil.
- **Mükerrer gönderimde belirleyici olan, hangi hatanın daha pahalı olduğu.** İki seçenek de bir hata biçimi taşıyor: (a) tekilleştirmemek → çift tık ya da zaman aşımı sonrası retry, yaralı/ölü sayısını **sessizce ikiye katlar**; (b) tekilleştirmek → gerçekten iki ayrı kaynaktan gelen **birebir aynı** metin tek kayıt sayılır. Serbest Türkçe metinde ikincisinin gerçekleşme ihtimali pratikte yok denecek kadar düşük, birincisi ise her gün olur. Üstelik (b)'nin sonucu görünür ve düzeltilebilir; (a)'nınki ne görünür ne de sonradan ayırt edilebilir — hangi kaydın mükerrer olduğunu artık kimse bilemez.
- **Bunun yan faydası: `POST` idempotent oluyor.** Cevabı alamayan bir istemci isteği güvenle tekrarlayabiliyor. Bu, akışın ve senkronluğun ileride değişebileceği bir sistemde küçük bir şey değil.
- **Karşılaştırma birebir bayt üzerinde.** Kırpmak ya da normalize etmek, "bu iki metin aynıdır" hükmünü sisteme verdirmek demek; bir denetim günlüğünün vermemesi gereken hüküm tam olarak budur. Bir boşlukla ayrılan iki metin iki metindir.
- **`String.hashCode()` değil, SHA-256.** 32 bitlik bir özet kazara yeterince sık çakışır; buradaki çakışma, alakasız bir raporu "mükerrer" diye sessizce düşürmek demektir.
- **Unique indeks, aramanın yetmediği yeri kapatıyor.** Önce arama (`findByTextHash`) sıradan durumu — kullanıcının iki kez basması — çözüyor. Ama aynı anda gelen iki istek de "yok" bulup ikisi de yazardı. İndeks bu yarışı, servisin cevaplayabileceği bir `DuplicateKeyException`'a çeviriyor: kaybedene kazananın kaydı dönüyor, ki bu zaten bir an sonra alacağı cevaptı.
- **`sparse`, geçmişi kilitlememek için.** Özet alanı olmayan eski kayıtlar, düz bir unique indekste "hepsi `null` değerini paylaşıyor" diye okunur ve indeks **hiç kurulamaz** — uygulama açılışta patlar. Bedeli, o eski metinlerin mükerrer tespitine katılmaması.
- **İndeksi modül kendi kuruyor.** PostgreSQL şeması `ddl-auto: update` ile değil Flyway ile yönetiliyor; buradaki indeks de bir doğruluk garantisi olduğu için başka bir modülün YAML'ındaki genel bir anahtarın yan etkisi olmamalı. Adı olan, okunabilen ve test edilebilen bir bileşen kuruyor.

**Alternatifler.**
- *Tekilleştirme yok:* Sıfır kod. Denetim günlüğü argümanı doğru ama pahalı hatayı seçiyor; ayrıca `POST`'u retry'a karşı savunmasız bırakıyor.
- *Kaydı yine aç, ama "bu metin daha önce gönderildi" uyarısı dön:* Denetim günlüğü el değmeden kalır. Makbuzun şeklini genişletir (ADR-021 onu bilinçli olarak dar tutuyor) ve çift sayımı önlemez — yalnızca görünür kılar.
- *Normalize edilmiş metin üzerinden tekilleştirme (kırpma, boşluk sadeleştirme):* Daha çok mükerreri yakalardı. "Aynı" tanımını sisteme verdirir; iki farklı metnin tek kayda düşmesi, tespit edilemez bir veri kaybıdır.
- *Zaman pencereli tekilleştirme (ör. son 5 dakikada aynı metin):* Çift tıkı yakalar, geç retry'ı kaçırır. Keyfi bir sabit ve iki farklı davranış demek.
- *`Idempotency-Key` başlığı:* HTTP'nin standart cevabı. İstemcinin anahtar üretmesini şart koşar; kimlik doğrulaması olmayan, tek sayfalık bir arayüzde metnin kendisi zaten doğal anahtar.
- *Reprocess için ayrı bir event tipi:* Dinleyici "yeniden mi işliyorum" bilirdi. Bugün bu bilgiyle yapılacak hiçbir şey yok; karşılığında ikinci bir kod yolu.
- *Reprocess'in analiz sonucunu dönmesi:* Tek istek. ADR-021'in tam olarak reddettiği şey; ayrıca senkronluğu tekrar sözleşmeye sokardı.

**Sonuçlar.**
- `RawIncidentReport` dördüncü bir alan kazandı (`textHash`). Modülün **kendi** verisi — metinden türetiliyor — dolayısıyla "her modül yalnızca sahibi olduğu veriyi yayınlar" kuralı bozulmuyor; alan makbuzda ve okuma DTO'sunda **görünmüyor**.
- `IngestionService.submit` artık `SubmissionOutcome` dönüyor (kayıt + yeni mi). Ayrım cevabın durum kodunda: **201** yeni, **200** zaten vardı. Gövde her iki durumda aynı şekilde.
- **Postman koleksiyonu ikinci kez koşturulabilir kalsın diye gevşetildi:** üç örnek gönderim artık `201`'in yanı sıra `200`'ü de kabul ediyor (temiz olmayan bir örneğe karşı ikinci koşu). Karşılığında koleksiyona iki yeni istek girdi: mükerrer gönderim (aynı metin → 200, aynı kimlik) ve reprocess.
- **Analizi başarısız olmuş bir raporun metni yeniden gönderilirse hâlâ `FAILED` görünür** — çünkü ikinci gönderim analizi yeniden çalıştırmıyor. Bunun için doğru işlem reprocess; uçlar bu yüzden ayrı.
- Mongo tarafında ilk indeks kuruldu ve bunun kurulduğu yer artık modülün kendi kodu.

**İleride.** Yakın-mükerrer (kopyala-yapıştır sırasında bir kelimesi değişmiş) metinler bugün iki ayrı kayıt; istenirse çözüm normalize edilmiş ikinci bir özet **ek** alan olarak eklenip kullanıcıya "buna benzer bir bildirim var, yine de kaydedeyim mi?" diye sormaktır — kararı sisteme değil kullanıcıya bırakan hâli. Toplu reprocess (katalog sürümü değişince tüm geçmişi yeniden işlemek) bu uç üzerinden bir döngüyle bugün de mümkün; anlamlı hâli, ADR-012'nin "İleride"sindeki analiz sürüm bilgisiyle birlikte gelir — o zaman yalnızca eski sürümle üretilmiş kayıtlar seçilebilir. `Idempotency-Key` gerekirse `textHash` yolunu bozmadan yanına eklenebilir.

---

## ADR-036 — Agregasyon Uçlarının Şekli: Seri Olarak Cevap, EXISTS ile Filtre, Tek Sorguda Üç Seviye

**Karar.** İki agregasyon ucu (`GET /analytics/time-series`, `GET /analytics/summary`) şu beş kararla çalışır:

1. **Cevap satır listesi değil, seri listesidir.** Zaman serisi `{cumulative, groupBy, series[{eventType, metric, provinceScope, province, points[{date, value}]}]}` döner. Hangi noktaların aynı çizgiye ait olduğunu sunucu söyler.
2. **İl bir kırılım boyutudur ve yalnızca istendiğinde.** `groupBy=province` verilmezse seri anahtarı (olay tipi, metrik); verilirse il kapsamı da anahtarın parçası olur. **Kapsam ancak il bir boyut olduğunda görünür**: `SHARED` ve `UNKNOWN`, kırılımsız cevapta ayrı bir seri değildir çünkü uzlaştırılacak bir il toplamı yoktur.
3. **`SHARED` tek bir kova.** Kırılımda paylaşılan figürler, kapsadıkları il kombinasyonuna göre değil, tek bir etiketli seri/satır olarak döner. Hiçbir ile eklenmez, bölüştürülmez, düşürülmez.
4. **İl ve anahtar kelime filtreleri `EXISTS` ile yazılır, `JOIN` ile değil.** Sayfalı kayıt listesinde bağlantı tablosuna `JOIN` + `DISTINCT` doğrudur; **toplam alırken değildir**.
5. **Özet üç seviyeyi tek sorguda döner** (`GROUPING SETS`): kova kırılımı, olay tipi toplamı, genel toplam. Kümülatif toplam da SQL'de, pencere fonksiyonuyla (`sum(sum(...)) over (partition by <seri> order by tarih)`) hesaplanır.

**Bağlam.** T-16 okuma ucunu, ADR-033 de onun şeklini karara bağlamıştı; grafik ve özet tablo ise ADR-019'un `SHARED` kavramıyla ilk kez **aritmetik** düzeyde karşılaşıyor. Kaynak dokümanın isteri "zaman içinde ve coğrafi bölge bazında izlenebilirlik"; ADR-023 bunu harita yerine il kırılımı olarak karara bağladı, PRD v2.0 da C-1 ve C-2 maddeleriyle bu task'a yazdı.

**Gerekçe.**

- **Seri, kümülatifin anlamlı olduğu tek birimdir.** Kümülatif bir nokta "kendisi ve kendinden öncekiler" demek; hangi noktaların "öncekiler" olduğu ise seri tanımına bağlı. Bu tanımı istemciye bırakmak, kuralın ikinci bir kopyasını TypeScript'te büyütmek olurdu (NFR-13). Sunucu zaten grupluyor; grupladığını söylemesi bedavaya geliyor.
- **`JOIN` altında `DISTINCT` bir toplamı düzeltmez.** İki il birden seçildiğinde, bağlantı tablosuna yapılan join paylaşılan kaydı iki kez getirir; `SUM` 10 yerine 20 der. `DISTINCT` satırları tekilleştirir ama toplamı düzeltmez — `SUM(DISTINCT value)` ise bambaşka (ve yine yanlış) bir sayıdır: aynı değere sahip iki gerçek kaydı tek sayar. `EXISTS` ise "bu kayıt uygun mu?" sorusunu satırı çoğaltmadan cevaplıyor. Aynı tuzak anahtar kelime filtresinde de var: iki anahtar kelimesi eşleşen bir kaydın figürleri iki katına çıkardı. **Bu, sessiz bir hata sınıfı** — 20 yaralı, 10 kadar makul görünür.
- **Kapsamı yalnızca kırılımda göstermek, dürüstlüğün ta kendisi.** Kırılımsız cevapta tek bir toplam vardır ve `SINGLE + SHARED + UNKNOWN` onun içinde zaten doğru toplanır; kapsamı ayırmak okuyucuya uzlaştıracak bir şey vermeden gürültü eklerdi. İl bir boyut olduğu anda ise ayrım zorunlu: il satırları kendi başlarına genel toplama eşit **değildir**, ve bu bir hata değil, verinin kendisidir. Ayrı ve etiketli satır, okuyucunun bunu görebilmesinin tek yolu.
- **`SHARED` kombinasyon başına değil, tek kova.** Kombinasyon başına ayırmak daha bilgilendirici olurdu ama seri anahtarları veri değiştikçe oynardı — grafiğin göstergesi her sorguda yeniden şekillenirdi. Uzlaştırma için tek kova yeterli: genel toplam = il satırları + paylaşılan + ilsiz. Hangi illeri kapsadığı zaten kayıt ucundan okunabiliyor.
- **`GROUPING SETS`, üç ayrı sorgudan iyidir.** Üç seviye tek taramadan, **aynı** filtrelenmiş kümeden çıkıyor; "satırlar ile toplam tutmuyor" sınıfı bir tutarsızlık yapısal olarak imkânsız hale geliyor.
- **Sayım ve metrik toplamı ayrı iki sorgu.** `incident_metric`'e join ederek kayıt saymak, iki metrikli bir kaydı iki kez sayar ve hiç metriği olmayan kaydı hiç saymaz — oysa tanınmayan metin de saklanıyor (ADR-006) ve tabloda görünmek zorunda. İki sorgu aynı şekilde gruplandığı için birleştirme bir hesap değil, bir eşleme.
- **SQL elde kuruluyor, sabit metin değil.** Filtreler opsiyonel; boş bir `IN ()` geçersiz SQL'dir ve gruplama cümlenin şeklini değiştiriyor. Her değer isimli parametre olarak bağlanıyor, kullanıcıdan gelen hiçbir şey metne eklenmiyor.
- **Kümülatif de SQL'de.** Java'da döngüyle toplamak aynı sonucu verirdi; ama o zaman "toplam" tanımının bir kısmı veritabanında, bir kısmı uygulamada olurdu. Pencere fonksiyonu seri sınırını `partition by` ile zaten biliyor.
- **`groupBy` esnek okunuyor, tanınmayan değer reddediliyor.** Spring'in enum bağlaması büyük/küçük harfe duyarlı; `groupBy=province` — dokümandaki ve insanın yazdığı biçim — iç enum'dan bahseden bir tip hatası dönerdi. Tanınmayan bir değerde sessizce kırılımsız cevaba düşmek ise **farklı bir soruyu 200 ile cevaplamak** olurdu.

**Alternatifler.**
- *Düz satır listesi döndürüp gruplamayı istemciye bırakmak:* Cevap daha küçük. Seri tanımı ve dolayısıyla kümülatifin anlamı istemciye geçer; iki istemci iki farklı grafik çizebilir.
- *`JOIN` + `DISTINCT` (kayıt listesindeki gibi):* Tek bir filtre kodu olurdu. Toplamları sessizce iki katına çıkarır — bu ADR'nin en pahalı maddesi.
- *`SHARED` figürü illere bölüştürmek:* Grafik "daha doğru" görünürdü. Metinde olmayan veriyi uydurmak; ADR-019 bunu açıkça yasaklıyor.
- *`SHARED` figürü kırılımda gizlemek:* Grafik sadeleşirdi. İl toplamları genel toplamla bir daha uzlaşmazdı ve on yaralı hiçbir yerde görünmezdi.
- *Kümülatifi istemcide toplamak:* Sunucuya parametre eklemezdi. Kuralın ikinci kopyası; ayrıca sayfalı/filtrelenmiş veride istemci zaten tüm noktalara sahip olmayabilir.
- *Özeti üç ayrı sorguyla üretmek:* Daha okunur SQL. Üç ayrı tarama ve seviyelerin birbirinden sapabilmesi.
- *Materialized view / önceden hesaplanmış özet tablo:* Büyük veride hızlı. Tazelik yönetimi ve şema yükü getirir; bu ölçekte indeksli `GROUP BY` fazlasıyla yeterli.

**Sonuçlar.**
- `analysis` modülü ilk kez **native SQL** kullanıyor (`IncidentAggregationRepository`). Criteria API pencere fonksiyonlarını ve `GROUPING SETS`'i taşımıyor; karşılığında bu sorgular Postgres'e bağlı — modül zaten PostgreSQL'in sahibi olduğu için (ADR-002) bu yeni bir bağ değil.
- Cevap DTO'ları `TimeSeriesResponse` ve `SummaryResponse`. Özetin üç seviyesi **aynı** satır tipini kullanıyor; boş alanlar JSON'da hiç görünmediği için (`non_null`) genel toplam ne olay tipi ne kova taşıyor.
- `AnalyticsService` hiçbir sayı üretmiyor, yalnızca satırları gruplayıp seviyelere yerleştiriyor. Bir testi bunu bilerek tutarsız satırlarla doğruluyor: servis aritmetik yapsaydı "düzeltirdi".
- Frontend tarafında T-27 ve T-28 bu şekle bağlanıyor; `SHARED`/`UNKNOWN` serilerinin arayüzde nasıl temsil edileceği hâlâ açık (TC-14).
- Postman koleksiyonuna dört yeni istek girdi; SSE dışındaki tüm uçlar artık koleksiyonda.

**İleride.** Veri büyürse `(event_type, occurred_on)` ve `(province_code, occurred_on)` üzerine bileşik indeksler ilk adım; ondan sonrası önceden hesaplanmış bir özet tablo ya da materialized view olur ve tazeleme stratejisi ayrı bir karar gerektirir. `SHARED` kovasının kapsadığı il kombinasyonuna göre ayrıştırılması istenirse seri anahtarına kombinasyon eklenir — sözleşme değişmez, yalnızca seri sayısı artar. Gün yerine hafta/ay bazında gruplama gerekirse `date_trunc` ile bir `interval` parametresi aynı yapıya oturur; bugün bilinçli olarak yok, çünkü kaynak dokümanın isteri günlük seyir.

---

## ADR-037 — Filtre Durumunun Tek Kaynağı: Adres Çubuğu

**Karar.** Filtre durumu **yalnızca adres çubuğunda** yaşar. React tarafında ikinci bir kopya yoktur: store yok, context yok, `useState` yok. Sorgu dizisi tek bir saf modül tarafından tipli bir `IncidentFilters` nesnesine çözülür (`filters/incidentFilters.ts`) ve tek bir hook (`useIncidentFilters`) üzerinden okunup yazılır. Bunun beş sonucu karar olarak sabitlenir:

1. **Sorgu anahtarı, filtrenin kendisidir.** TanStack Query önbelleği çözülmüş filtre nesnesiyle anahtarlanır; bu yüzden çözümleme **kanoniktir** — çoklu değerler sıralanır ve tekilleştirilir, varsayılanlar URL'e yazılmaz. Aynı görünümün iki yazılışı olamaz, dolayısıyla iki önbellek girdisi ve iki istek de olamaz.
2. **Okunamayan parametre düşürülür, reddedilmez.** URL elle yazılan, kesilen, yapıştırılan bir şeydir; bozuk bir karakter hata ekranını hak etmez. **Ama tanınmayan olay tipi ya da il kodu düşürülmez** — katalog sunucudadır (NFR-14) ve burada doğrulamak, YAML'a eklenen bir tipe bağlantı verilememesi demek olurdu.
3. **Sayfa numarası URL'de, ve insanın saydığı gibi.** Adres `page=2` dediğinde ikinci sayfa açılır; sunucunun sıfırdan sayan numarasına çeviren tek yer `toApiQuery`. Sayfa URL'de olmasaydı paylaşılan bir bağlantı hep ilk sayfayı açardı.
4. **Filtre değişince ilk sayfaya dönülür**, sayfa değişince dönülmez.
5. **Filtre çubuğu ile liste birbirine bağlı değildir.** Aralarında prop geçmez; ikisi de aynı URL'i okur. Özet tablo ve grafik (T-27, T-28) aynı şekilde bağlanacak.

**Bağlam.** TC-15 üç soruyu birlikte soruyordu: filtre durumunun adres çubuğuyla senkronizasyonu, sunucu verisi önbelleğinin canlı akış geldiğinde geçersizleştirilmesi, ve grafik/özet/liste arasında **tek** filtre kaynağı. FR-21 ayrıca bağlantının paylaşılabilir olmasını kabul kriteri yapıyor. ADR-026, filtre durumu tasarlanırken önbellek anahtarlarının filtreyle birebir eşleşmesi gerektiğini not düşmüştü.

**Gerekçe.**
- **"Senkronizasyon" sorusunun en iyi cevabı, senkronize edilecek iki şeyin olmaması.** Durumu React'te tutup URL'e yansıtmak iki yönlü bir eşleme demek: yazma yolu, okuma yolu, ve geri düğmesiyle gelen dış değişikliği dinleyen üçüncü bir yol. Üçü de ayrı ayrı yanlış yapılabilir ve aralarındaki fark ancak kullanıcı bağlantıyı paylaştığında görülür. Tek kopyada bu hata sınıfı yok.
- **Paylaşılabilirlik bir yan etki değil, tanım.** FR-21'in kabul kriteri (adres kopyalanıp yeni sekmede açıldığında aynı görünüm) URL tek kaynak olduğunda kanıtlanacak bir şey olmaktan çıkıyor; başka türlü davranması için fazladan kod gerekirdi.
- **Geri düğmesi bedavaya geliyor.** Router zaten geçmişi tutuyor; filtre değişikliği bir gezinme olduğu için geri alınabiliyor. Bir store ile aynı davranış elle yazılırdı.
- **Kanoniklik bir süs değil, önbellek doğruluğu.** `province=41&province=16` ile `province=16&province=41` aynı görünüm; sıralanmasaydı iki ayrı anahtar, iki ayrı istek ve iki ayrı önbellek girdisi olurlardı — ve T-29'da geçersizleştirme bunlardan yalnızca birini tazelerdi.
- **Akışın geçersizleştireceği tek bir anahtar öneki var.** `/incidents`'tan okunan her şey `['incidents', …]` altında duruyor. Sinyal "kayıtlar değişti" der, "hangi görünüm değişti" demez (ADR-021); dolayısıyla geçersizleştirmenin hepsini tek seferde adlandırabilmesi gerekiyor.
- **`keepPreviousData` tazelemenin görünümü boşaltmamasının yolu.** FR-25'in isteri bu; sayfa çevirmede de aynı davranış görülüyor, yani T-29 gelmeden önce test edilebiliyor.
- **Seçim bir karardır, yarım yazılmış kelime değildir.** Onay kutusu, il, tarih ve sıralama anında uygulanır; anahtar kelime forma gönderilince. Debounce bilinçli olarak yok: her duraklamada bir istek demek olurdu ve bu ekranın her testini saate bağlardı (TC-16).
- **Aralık doğrulaması istemcide tekrarlanmıyor.** Ters tarih aralığını sunucu reddediyor; arayüz cevabı Türkçeye çeviriyor. Kuralın ikinci bir kopyası olmuyor (NFR-13).

**Alternatifler.**
- *Durumu React'te tutup URL'e yansıtmak:* En yaygın kalıp. İki kopya ve aralarındaki üç yönlü senkronizasyon; ayrıca ilk yüklemede URL'i okumayı unutmak sessiz ve yaygın bir hata.
- *Global store (Redux/Zustand):* Grafik ve özet eklendiğinde "daha ölçekli" görünürdü. Paylaşılabilirliği kendiliğinden vermez, geri düğmesini hiç vermez ve ADR-026'nın bu ölçekte fazla tören dediği şeyi geri getirir.
- *Filtreleri en üstte tutup prop olarak geçmek:* Bugün üç bileşen için işler. Dördüncü ekranda prop zinciri uzar ve iki görünümün farklı veri göstermesi yine mümkün kalır.
- *Sayfa numarasını URL dışında tutmak:* URL sadeleşirdi. Paylaşılan bağlantı hep ilk sayfayı açardı — kabul kriterinin yarısı.
- *Sayfa numarasını sıfırdan saymak (sunucuyla aynı):* Çeviri kodu olmazdı. Adres çubuğu insanın okuduğu bir yer; `page=1` ikinci sayfa demek olurdu.
- *Anahtar kelimeyi debounce ile uygulamak:* Daha "canlı" hissettirir. Zamana bağlı testler ve tuş başına istek; ayrıca yarım yazılmış bir kelime paylaşılabilir bir görünüm değil.
- *Filtreleri istemcide uygulamak (tümünü çek, tarayıcıda süz):* İlk gün daha hızlı görünür. FR-21 bunu açıkça yasaklıyor ve veri bir sayfayı aştığı gün sessizce yanlış cevap verir.

**Sonuçlar.**
- Üç yeni modül: `filters/incidentFilters.ts` (saf, DOM'suz test edilir), `filters/useIncidentFilters.ts`, ve `incidents/` altındaki üç görünüm.
- `queryKeys` yeniden düzenlendi: `['incidents', 'list', filtreler]` ve `['incidents', 'by-raw-report', id]`. Ortak önek T-29'un geçersizleştireceği yüzey.
- **Ters tarih aralığı 500 dönüyordu.** `IncidentQuery`/`AnalyticsQuery` bunu `IllegalArgumentException` ile reddediyordu; bu, genel hata yakalayıcıya düşüp "sunucuda beklenmeyen bir hata" oluyordu — oysa hata isteği yapanındı. Tarih seçicileri bunu bir tık uzağa getirdiği için bu task'ta düzeltildi: artık `DomainValidationException` ve `query.date-range.invalid` koduyla **400**. Uçlar T-16/T-17'den beri böyleydi; görünür kılan arayüz oldu.
- Filtre çubuğundaki hiçbir seçenek kaynak kodda yazılı değil; olay tipleri ve iller `/metadata`'dan geliyor (NFR-14).
- **`CatalogPanel` kaldırıldı.** T-23'te "PRD'de olmayan, kuralı görünür kılan geçici panel" olarak eklenmişti; aynı kuralı artık gerçek bir ekran olan filtre çubuğu gösteriyor.

**İleride.** Özet ve grafik (T-27, T-28) aynı hook'u çağıracak; filtre durumu için yapılacak yeni bir iş yok. Akış geldiğinde (T-29) yapılacak tek şey `['incidents']` önekini geçersizleştirmek — hangi görünümlerin açık olduğunu bilmesi gerekmeyecek. Filtre sayısı artarsa (ör. tarih kaynağı, kapsam) aynı çözümleme fonksiyonuna bir alan eklenir; kanoniklik kuralı gereği yeni alanın da varsayılanı URL'e yazılmamalı. Sayfa boyutu bugün sabit 20; kullanıcıya bırakılırsa o da URL'e girer ve aynı çözümlemeden geçer. Anahtar kelime bugün tek; çoklu anahtar kelime istenirse `eventType` gibi tekrarlanan bir parametre olur ve uçtaki `EXISTS` filtresi (ADR-036) zaten buna hazır.

---

## ADR-038 — `SHARED` ve `UNKNOWN` Kapsamın Arayüzdeki Temsili: Aynı Tabloda, Kendi Satırında, Adıyla

**Karar.** Özet tablo, hiçbir ile atanamayan figürleri **il satırlarıyla aynı tabloda, kendi satırında ve kelimeyle etiketli** gösterir (`Ortak toplam`, `İl belirtilmemiş`). Bunun etrafındaki altı karar:

1. **Hiçbir toplam tarayıcıda hesaplanmaz.** Kova satırları, tip toplamı ve genel toplam üçü de sunucudan geldiği gibi basılır. Satırları toplayan bir arayüz, paylaşılan figür varken **farklı ve yanlış** bir sayı üretirdi — üstelik doğru görünen bir sayı.
2. **Fark, dipnotla açıklanır.** Paylaşılan ya da ilsiz bir satır varsa tablonun altına tek cümle düşer: bu satırlar hiçbir ile eklenmez, bu yüzden il satırlarının toplamı tip toplamına eşit değildir. Yalnızca böyle bir satır varken görünür.
3. **Olay tipi başına ayrı tablo.** Metrikler olay tipine ait; tek geniş tablo, katalogdaki her metrik için bir sütun taşır ve çoğu hücreyi boş bırakırdı — bu da "veri eksik" gibi okunur.
4. **Sütunlar katalog sırasında**, hiçbir satırın taşımadığı metrik sütun olmaz, ve bir kovanın figürü yoksa hücre **`—`**'dir, `0` değil. Sunucu anahtarı hiç göndermiyor; oraya sıfır yazmak metnin söylemediği bir şeyi söylemek olurdu.
5. **Genel toplam yalnızca birden fazla olay tipi varken.** Tek tiple genel toplam, tip toplamının rakamı rakamına tekrarı olurdu; iki kez basılan bir sayı okuyucuyu aralarındaki farkı aramaya davet eder.
6. **Gölgelendirme etiketin yanına eklenir, yerine geçmez** (NFR-16). Satırı ayıran şey `Ortak toplam` yazısı; arka plan rengi yalnızca ona eşlik ediyor.

**Bağlam.** TC-14, ADR-019'un veri modelindeki kuralının (bölüştürme, düşürme, çift sayma yok) arayüzdeki karşılığını soruyordu: tabloda ve grafikte nasıl gösterilecek, ve okuyucu il toplamlarıyla genel toplamı nasıl uzlaştıracak? ADR-036 uç tarafını çözmüştü: üç seviye tek sorgudan, `SHARED` tek kova, kapsam yalnızca il bir boyut olduğunda ayrı. Bu ADR onun ekrandaki hâli; grafik tarafı (T-28) aynı kuralı seri düzeyinde uygulayacak.

**Gerekçe.**
- **Uzlaştırma ancak her iki sayı da görünürken mümkün.** Örnek 3'te Bursa 8, Kocaeli 6 kaza; yaralı sütununda ikisi de boş, tip toplamı ise 10 yaralı diyor. Aradaki fark ortak satırın kendisi. Ortak satır gizlenseydi on yaralı hiçbir yerde görünmezdi; illere bölüştürülseydi metinde olmayan bir veri uydurulmuş olurdu; ayrı bir tabloya alınsaydı okuyucu farkı iki tablo arasında kendi kurmak zorunda kalırdı.
- **Toplamı istemcide hesaplamak, tam da bu ADR'nin göstermek istediği şeyi silerdi.** Satırlar toplandığında tip toplamı 4 yaralı yerine 0 çıkardı ve tablo kendi içinde "tutarlı" görünürdü. Sunucunun sayısını basmak bir tembellik değil; toplamın tanımının tek yerde durması (NFR-13) ve tutarsızlığın **görünür** kalması.
- **Bir test bunu bilerek tutarsız veriyle doğruluyor** — satırlarla toplamın kasten uyuşmadığı bir cevapta ekran sunucunun sayısını gösteriyor. Modül aritmetik yapsaydı "düzeltirdi". Backend tarafında `AnalyticsService` için yazılan testin birebir aynısı.
- **Dipnot olmadan fark, hata gibi okunur.** Toplamayı yapan bir okuyucu haklı olarak bir uyuşmazlık bulur; sistemin güvenilirliği hakkındaki yargısı o an oluşur. Cümlenin yalnızca ilgili tabloda çıkması, her tabloya konan bir uyarının görünmez hâle gelmesini önlüyor.
- **`—` ile `0` farkı veri dürüstlüğü.** "Bu kovada yaralı figürü çıkarılmadı" ile "yaralı sayısı sıfır" farklı iddialar; sunucu birincisini anahtarı hiç göndermeyerek söylüyor.
- **Bilinmeyen olay tipi ve metrik gizlenmiyor.** `OTHER` katalogda yok, kodun ürettiği bir değer (ADR-006); katalogda olmayan bir anahtarı atlamak, "her şeyi topluyorum" diyen bir tablodan kayıt düşürmek olurdu.

**Alternatifler.**
- *Paylaşılan figürü illere bölüştürmek:* Tablo sade görünürdü. ADR-019'un açıkça yasakladığı şey: metinde olmayan veriyi uydurmak.
- *Paylaşılan satırı gizleyip yalnızca toplamda tutmak:* İl satırları temiz olurdu. On yaralı hiçbir yerde görünmez, fark açıklanamaz hâle gelirdi.
- *Ayrı bir "ilsiz figürler" tablosu:* Kavramsal olarak düzenli. Uzlaştırmayı okuyucuya iki tablo arasında yaptırır; oysa mesele tam olarak bu iki sayının yan yana durması.
- *Satırları istemcide toplayıp tip toplamı üretmek:* Bir uç çağrısı azalırdı — ama sunucu zaten üç seviyeyi tek sorguda döndürüyor (ADR-036) ve hesaplanan toplam paylaşılan figürü kaybederdi.
- *Tek geniş tablo (tüm metrikler sütun):* Karşılaştırma kolaylaşırdı. Katalog büyüdükçe çoğu hücre boşalır ve boş hücre "eksik veri" gibi okunur.
- *Satırı yalnızca renkle ayırmak:* Görsel olarak yeterli görünür. Durumun yalnızca renkle taşınmaması isteri (NFR-16) bunu zaten dışlıyor.
- *Genel toplamı her zaman göstermek:* Tutarlı bir düzen. Tek olay tipinde aynı sayıyı iki kez basmak demek.

**Sonuçlar.**
- `analytics/summaryModel.ts` saf ve DOM'suz test edilebilir: yalnızca **düzen** kararı veriyor (blok, sütun, sıra), tek bir toplama işlemi içermiyor.
- Sunucudan gelen satır sırası korunuyor (iller ada göre, sonra paylaşılan, sonra ilsiz) — sıralamayı ekranda yeniden kurmak, uçtaki kararı ikinci kez vermek olurdu.
- **Tarayıcıda yakalanan hata:** genel toplam sütunları tekrarlanıyordu. Sıralama, her olay tipinin metriklerinin uç uca eklenmesinden geliyor ve `DEATH` ile `INJURED` birden fazla tipte tanımlı (PRD §7) — aynı toplam, onu tanımlayan her tip için bir kez basılıyordu. Testler bunu göremezdi çünkü test verisinde çakışan anahtar yoktu; tekilleştirme eklendi ve **çalışan sistemde görülen** hâliyle testle sabitlendi.
- Özet paneli filtreleri adres çubuğundan okuyor (ADR-037); liste ile arasında hiçbir bağlantı yok, dolayısıyla ikisinin farklı bir soruyu cevaplaması mümkün değil.
- Sorgu anahtarları `['analytics', …]` altında; `incidentDerivedKeys` artık kayıtlardan türeyen her şeyi tek yerde adlandırıyor, T-29 bunu geçersizleştirecek.

**İleride.** T-28'de grafik aynı soruyu seri düzeyinde soracak: `SHARED` ve `UNKNOWN` ayrı ve etiketli birer seri olacak, il serilerine eklenmeyecek — kural aynı, taşıyıcı farklı. `SHARED` kovasının kapsadığı il kombinasyonuna göre ayrıştırılması istenirse (ADR-036'nın "İleride"si) tablo yapısı değişmez, yalnızca satır sayısı artar. Metrik sayısı büyürse sütunlar yerine metrik başına satır düzenine geçmek gerekebilir; bugünkü katalogda en fazla dört metrik var ve sütun düzeni okunaklı.

---

## ADR-039 — Grafiğin İki Modu, Grafik Ayarlarının Adres Çubuğunda Yaşaması ve Kümülatifin Sunucudan İstenmesi

**Karar.** Zaman serisi grafiği tek seferde **tek bir olay tipi** çizer ve iki modu vardır:

1. **Kırılımsız** — bir çizgi = bir **metrik**. Okuyucu metrikleri zaman içinde karşılaştırır.
2. **İl kırılımlı** — bir çizgi = bir **yer** (il, `Ortak toplam`, `İl belirtilmemiş`) ve **tek bir metrik** çizilir. İller ancak aynı sayı üzerinden karşılaştırılabilir.

Bunun etrafındaki beş karar:

- **Grafik ayarları (`chart`, `metric`, `breakdown`, `cumulative`) adres çubuğunda durur** — filtrelerin yanında, ayrı anahtarlarda. Filtre değildirler: hangi kayıtların *sayıldığını* değil, hangi serilerin *çizildiğini* belirlerler; özet ve liste onlardan etkilenmez.
- **Her modül adres çubuğunda yalnızca kendi anahtarlarını yeniden yazar.** ADR-037'nin "sorgu dizisi filtre durumudur" ifadesi burada netleşiyor: sorgu dizisi **tüm görünümün** durumudur, filtreler onun bir parçası.
- **Grafik, filtrenin dışladığı bir olay tipini asla çizmez.** Adreste kalmış bir seçim geçerli değilse ilk izin verilen tipe düşülür.
- **Kümülatif sunucudan istenir** (`cumulative=true`), noktalar toplanarak üretilmez. Etiket de **cevaptan** okunur: istek uçarken anahtar ile cevap birbirini tutmaz ve kümülatif bir grafiği düz diye etiketlemek bambaşka bir olgu bildirmektir.
- **Seri gizle/göster bileşen durumudur, adres çubuğunda değil.** Ne istendiğini ne de sayılanı değiştirir; URL'de olsaydı her gösterge tıklaması tarayıcı geçmişine bir adım eklerdi.

**Bağlam.** FR-23 grafiği olay tipine bağlıyor ("seçilen tipin metrikleri ayrı seriler olarak çizilir"), FR-24 il kırılımını istiyor, FR-12 kümülatifi sunucudan istiyor. T-28 ayrıca "çok il seçildiğinde okunabilirlik" diyor. Uç tarafı ADR-036'da karara bağlanmıştı: cevap seri listesidir, `SHARED` tek kova, kapsam yalnızca il bir boyutken görünür.

**Gerekçe.**
- **Grafik yalnızca benzeri benzerle karşılaştırabilir.** Trafik kazası için üç metrik × üç kova dokuz çizgi eder ve "kaza sayısı" ile "can kaybı" aynı eksende anlamsızdır. İki mod bu sorunu bir ayarla değil, **soruyu netleştirerek** çözüyor: ya metrikleri karşılaştırırsın ya yerleri.
- **Metrik kısıtı hiçbir sayıyı değiştirmez.** Her seri kendi başına duruyor; birini çizmemek diğerini etkilemiyor ve kısıt asla bir serinin **içindeki** noktalara uygulanmıyor. Bu yüzden "istemcide filtreleme" değil, hangi çizginin çizileceği kararı.
- **Ayarlar URL'de, çünkü paylaşılan bir grafik gönderenin gördüğü grafik olmalı.** Aynı gerekçe ADR-037'nin filtreler için verdiği gerekçe; farklı olan tek şey, bu anahtarların sunucuya sorulan soruyu daraltmaması.
- **Ayrı anahtar kümeleri, ayrı yazarlar.** Filtre değişince grafiğin ayarlarının sıfırlanması (ya da tersi) tam olarak "iki kopya" hatasının URL'e taşınmış hâli olurdu. "Filtreleri temizle" de yalnızca filtreleri temizliyor.
- **Kümülatifi istemcide toplamak, "şu ana kadarki toplam"ın ikinci tanımını yazmak olurdu** (NFR-13) — üstelik seri sınırını da istemcinin bilmesi gerekirdi, ki o sınır paylaşılan figürü bir ilin çizgisine sokmamanın tek güvencesi.
- **Etiketi cevaptan okumak** ucuz bir dürüstlük: `keepPreviousData` ile eski veri ekranda kalırken anahtar yeni durumu gösterir; etiket anahtardan okunsaydı, gelmemiş bir cevabı anlatırdı.
- **Veri olmayan gün sıfır değil, boşluktur.** Sistem "hiçbir şey olmadı"yı bilmiyor, "hiçbir şey bildirilmedi"yi biliyor. Sıfır yazmak kümülatif modda toplamın düşüp tekrar toplandığı bir çizgi çizerdi. Çizgi boşluğun üzerinden birleştiriliyor (`connectNulls`) — nokta uydurmadan, seyrek seriyi tek çizgi olarak okunur kılmak için.

**Alternatifler.**
- *Filtredeki tüm olay tiplerini birden çizmek:* Grafik ile tablo birebir aynı kümeyi gösterirdi. Farklı birimlerdeki on beş çizgi okunamaz; FR-23 zaten "seçilen tipin metrikleri" diyor.
- *İl kırılımında tüm metrikleri çizmek:* Bir seçim kutusu eksilirdi. Dokuz çizgi ve karşılaştırılamaz eksen — task'ın "okunabilirlik" maddesi tam olarak bu.
- *Grafik ayarlarını bileşen durumunda tutmak:* Daha az URL gürültüsü. Paylaşılan bağlantı gönderenin grafiğini açmazdı ve ADR-037'nin "tek kopya" kuralı grafikte delinirdi.
- *Grafik ayarlarını `IncidentFilters`'ın içine koymak:* Tek bir çözümleme olurdu. Kümülatif anahtarına basmak listenin ve özetin sorgu anahtarını değiştirir, ikisini de gereksiz yere yeniden getirirdi.
- *Kümülatifi istemcide hesaplamak:* Sunucuya parametre eklemezdi (zaten var). Kuralın ikinci kopyası.
- *Veri olmayan günleri sıfırla doldurmak:* Çizgi kesintisiz olurdu. Veriye olmayan bir iddia eklemek.
- *Seri gizle/göster durumunu URL'e koymak:* Paylaşılan bağlantı gizlenenleri de taşırdı. Her tıklama bir geçmiş adımı; geri düğmesi "seriyi geri getir" hâline gelirdi.

**Sonuçlar.**
- `analytics/chartOptions.ts` (saf çözümleme + `resolve*` kuralları), `analytics/chartModel.ts` (seri → satır çevrimi; **tek bir toplama içermez**), `analytics/ChartPanel.tsx`. Recharts ilk kez kullanılıyor (ADR-026'nın öngördüğü gibi, ilk kullanıcısı bu task).
- Gösterge sıralaması kütüphanenin varsayılanına (alfabetik) bırakılmadı; çizgi sırası kataloğun sırası ve gösterge onu izliyor — özet tablo da aynı sırayı kullanıyor.
- **jsdom'un iki eksiği test kurulumunda kapatıldı:** `ResizeObserver` yok ve her öğe sıfır boyutlu. Bunlar olmadan grafik yalnızca çizilmemekle kalmıyor, **hata fırlatıyor** ve grafiği barındıran her ekran boş sayfa olarak render ediliyor. Konan şey tarayıcının yerleşimi; grafiğin kendisi gerçek SVG çiziyor — ADR-026'nın Recharts'ı seçme gerekçesi buydu.
- Testler göstergeyi ve çizgi sayısını okuyor: "seçilen tipin metrikleri ayrı seriler olarak çiziliyor" iddiası ekrandan doğrulanıyor, mock'tan değil.

**İleride.** X ekseni bugün kategorik: yalnızca veri olan günler eşit aralıklarla diziliyor, yani takvimdeki boşluklar orantılı görünmüyor. Gerçek zaman ekseni (`type="number"` + tarih ölçeği) veri sıklaştığında ilk adım. Gün yerine hafta/ay bazında gruplama ucun `date_trunc` ile genişlemesini gerektirir (ADR-036'nın "İleride"si) ve grafikte yalnızca bir seçim kutusu olur. Birden fazla olay tipini birlikte çizmek istenirse doğru yol tek eksende toplamak değil, tip başına küçük çoklu grafik (small multiples) olur; seri anahtarı zaten olay tipini taşıyor.

---

## ADR-040 — Canlı Tazeleme: Sinyal Geçersizleştirir, Delta Uygulamaz; Pencereli Birleştirme ve Kanıtlanmış İlgisizlikte Atlama

**Karar.** Akıştan gelen sinyal, ekrandaki hiçbir şeyi doğrudan değiştirmez; kayıtlardan türeyen sorguları **geçersizleştirir** ve sorgular kendilerini yeniden getirir. Bunun etrafındaki altı karar:

1. **Delta yok.** Sinyal veri taşımadığı için (ADR-021) satır eklenmez, sayaç artırılmaz, toplam güncellenmez. Tazeleme `['incidents']` ve `['analytics']` öneklerini geçersizleştirmekten ibaret; `incidentDerivedKeys` bu iki öneki tek yerde adlandırıyor.
2. **Görünüm boşaltılmaz.** `keepPreviousData` sayesinde eski veri yeni cevap gelene kadar ekranda kalır. Her sinyalde bir an boşalan tablo, kullanıcı gözünde sayfa yenilemesidir — canlılığın önlemek için var olduğu şey.
3. **Pencereli birleştirme (leading + trailing, 1 sn).** İlk sinyal **anında** tazeler; pencere içinde gelenler pencerenin sonundaki tek bir tazelemeye biner. On gönderim iki tazeleme eder, on değil.
4. **Atlama yalnızca kanıtlanmış ilgisizlikte.** Sinyal, taşıdığı alanlarla (olay tipi, tarih, il kodları) aktif filtreye uymadığını **kanıtlıyorsa** atlanır. Üç durumda her hâlükârda tazelenir: (a) ekranda o rapordan üretilmiş kayıtlar duruyorsa, (b) anahtar kelime filtresi aktifse, (c) sinyal okunamadıysa.
5. **Sayfa başına tek bağlantı.** Abonelik `AppShell`'de açılır; panel başına abonelik, tek gönderim için üç yeniden bağlanma ve üç tazeleme demekti.
6. **Yeniden bağlanma bize ait.** `EventSource` kendi yeniden denemesini yapar; **vazgeçtiğinde** (readyState `CLOSED`) biz 5 saniyede bir yeni bağlantı açarız. Bağlantı geri geldiğinde **tazelenir**, çünkü akış hiçbir şeyi tekrar oynatmaz (ADR-034).

**Bağlam.** TC-13 iki soruyu birlikte soruyordu: art arda gelen sinyaller nasıl birleştirilir, ve aktif filtreye uymayan olay ne yapar? Uç tarafı ADR-034'te sabitlenmişti: rapor başına bir mesaj, commit sonrası yayın, tekrar oynatma yok, istemci bazlı filtre yok. `CLAUDE.md`'nin 7. kuralı da sözleşmenin niyetini yazıyor: *olay ilgiyi belirlemeye yetecek kadar bilgi taşır, istemci yeniden sorgular*.

**Gerekçe.**

- **Delta, kuralın ikinci kopyasıdır.** Gelen sinyalle tabloya satır eklemek, "bu kayıt bu filtreye uyar mı", "hangi sayfaya girer", "hangi toplamı ne kadar artırır" sorularını istemcide cevaplamak demek — üçü de sunucunun sahip olduğu kurallar (NFR-13). Ayrıca paylaşılan figürün hangi toplamlara girip girmeyeceği (ADR-019) tam olarak istemcide tekrarlanmaması gereken aritmetik.
- **Birleştirme neden düz debounce değil?** Trailing debounce, sürekli akan bir sinyal dizisinde **hiç** tazelemez: her yeni sinyal zamanlayıcıyı ileri atar. Leading-only ise her sinyalde tazeler, yani birleştirmez. Leading + trailing ikisini de kapatıyor: tek gönderim anında görünür (beklemek arayüzü sebepsiz yavaş hissettirirdi), yoğun akışta ise pencere başına bir tazeleme tavanı var.
- **Atlamanın yönü tek taraflı seçildi.** Gereksiz bir tazeleme bir istektir ve ekranda hiçbir şeyi değiştirmez; atlanmış bir tazeleme ise değişmiş bir sayıyı **yerleşmiş gibi** gösterir ve akış aynı şeyi bir daha göndermediği için kimse düzeltmez. Bu yüzden atlama yalnızca sinyalin kendisi ilgisizliği kanıtladığında yapılıyor.
- **Ekrandaki rapor kimlikleri, tek gerçek boşluğu kapatıyor.** Reprocess önce siler sonra yazar (ADR-035); silinen kayıtlar tam olarak yeni sinyalin **bahsetmediği** kayıtlardır. Yalnızca içeriğe bakan bir ilgi testi bu durumu kaçırır ve ekranda artık var olmayan bir satır kalır. Görünen kayıtların rapor kimliğiyle karşılaştırma, zaten elimizdeki veriyle bu boşluğu kapatıyor.
- **Anahtar kelime hakkında sinyal hiçbir şey söylemiyor**, dolayısıyla o filtre aktifken ilgisizlik kanıtlanamaz. Tahmin etmek, veriye dayanmayan bir cevap uydurmak olurdu.
- **İl kodu listesi üç kapsamı da tek testle çözüyor.** Kod taşımayan bir kayıt (metinde il yok) il filtresine hiç uymaz — doğrusu da bu, çünkü il filtreli görünüm o kaydı zaten içermez; paylaşılan figür ise kapsadığı her ilin filtresine uyar (ADR-019).
- **Tarayıcının yeniden bağlanması yetmiyor.** Backend durduğunda bağlantı reddedilmiyor: nginx **502 döndürüyor** ve `EventSource` HTTP hata cevabını ölümcül sayıp `CLOSED`'a geçiyor — bir daha da denemiyor. Canlı kalması gereken bir sayfa için bu sessiz ölümdür; çalışan sistemde görüldü ve yeniden bağlanma bu yüzden bizim işimiz.
- **Geri gelince tazelemek zorunlu.** Akış durumsuz (ADR-034): bağlantı kopukken yayınlanan her şey kaçtı. Göstergeyi yeşile çevirip veriyi eski bırakmak, en kötü hâli — kullanıcıya "güncelsin" demek.

**Alternatifler.**
- *Sinyaldeki kayıtları doğrudan tabloya eklemek:* Tazeleme isteği hiç olmazdı. Filtre, sayfalama ve toplam kurallarının istemcide kopyası; ADR-021 zaten payload'ı bu yüzden dar tuttu.
- *Her sinyalde koşulsuz tazelemek:* En basiti ve her zaman doğru. Filtreye uymayan her gönderim üç isteğe mal olur ve ekranda hiçbir şey değişmez; sözleşme de ilgiyi belirlemek için alan taşıyor.
- *Yalnızca içeriğe bakıp atlamak (rapor kimliği kontrolü olmadan):* Daha az kod. Reprocess sonrası silinen kayıt ekranda kalırdı.
- *Düz debounce:* Tek satır. Sürekli akışta hiç tazelemez.
- *Throttle (yalnızca leading):* Basit tavan. Pencerenin sonundaki son sinyal kaybolur, yani veri değişikliği görünmeden kalır.
- *Panel başına abonelik:* Bileşenler bağımsız olurdu. Üç bağlantı, üç yeniden bağlanma, üç kat tazeleme.
- *Seri gizle/göster gibi tazelemeyi de kullanıcıya bırakmak ("yenile" düğmesi):* Öngörülebilir. FR-25 canlı tazeleme istiyor; düğme, akışın var olma sebebini ortadan kaldırır.

**Sonuçlar.**
- `realtime/incidentSignal.ts` (saf: sözleşme tipleri + `isRelevant`), `realtime/useIncidentStream.ts` (tek abonelik, birleştirme, yeniden bağlanma), `realtime/StreamStatus.tsx`.
- Gösterge dört durum taşıyor: `bağlanıyor` · `bağlı` · `yeniden bağlanıyor` · `kapalı`. Kapalıyken ayrıca **verinin kaybolmadığı** yazıyor — akış koptuğunda kaybedilen tek şey canlılık (ADR-021).
- **jsdom'da `EventSource` yok.** Kurulum dosyasına hiçbir şey yapmayan bir stub kondu (grafiğin `ResizeObserver`'ı gibi); akışın kendi testleri onu sürülebilir bir sahte ile değiştiriyor — açılma, kopma, vazgeçme ve kapanma yalnızca böyle deterministik test edilebiliyor (TC-16).
- Zaman bağımlı davranışlar sahte zamanlayıcıyla test edildi: on sinyal iki tazeleme, sürekli akışta pencere başına bir tazeleme, vazgeçilen bağlantıda 5 saniyede bir yeniden açma.
- **Doğrulama sırasında bir ölçüm hatası yakalandı ve tekrarlandı:** on gönderimin dokuzu aynı geçici dosyayı paylaştığı için mükerrer sayılmıştı (ADR-035 doğru davrandı); ölçüm ancak on **ayrı** metinle anlamlı oldu.

**İleride.** Pencere bugün sabit 1 saniye; gerçek kullanımda gürültülü bulunursa değeri ayarlanabilir ya da yük altında büyüyen uyarlamalı bir pencereye dönüşebilir — birleştirme noktası tek yerde. Yeniden bağlanma sabit 5 saniye; uzun kesintilerde artan bekleme (exponential backoff) aynı yere girer. `Last-Event-ID` ile tekrar oynatma sunucuya eklenirse (ADR-034'ün "İleride"si) bağlantı geri geldiğinde tazeleme yerine kaçan sinyaller işlenebilir; bugünkü tasarımda tazeleme zaten doğru cevap. Sekme arka plandayken tazelemeyi durdurup öne gelince bir kez tazelemek, çok sekmeli kullanımda istek sayısını düşürür — TanStack'in `focusManager`'ı zaten bu bilgiyi taşıyor.

---

## ADR-041 — İzlenebilirlik Ekranları: Sunucudan Gelen Offset'lerle Vurgulama, Metne Hiçbir Şey Eklememe ve Reprocess'in Yerinde Tazelenmesi

**Karar.** İki adreslenebilir ekran eklendi: `/incidents/:id` (olay kaydı) ve `/reports/:id` (ham bildirim). Vurgulama şu beş kararla çalışır:

1. **Konum sunucudan gelir, metinde arama yapılmaz.** Anahtar kelimeler `charStart`/`charEnd` ile işaretlenir. Offset'ler Java'da da JavaScript'te de **UTF-16 kod birimi** sayar; bu yüzden `ğ`, `ı`, `İ`, `ş` tek konum tutar ve vurgulama kaymaz. TC-18 böylece kapanır.
2. **Metne hiçbir şey eklenmez.** Vurgulanan parçanın içine ne etiket, ne işaret, ne de yalnızca ekran okuyucunun göreceği gizli bir açıklama konur. Bu ekranın varlık sebebi metnin **saklandığı gibi** görünmesi (FR-02); seçilip kopyalanan metin, gönderilen metin olmak zorunda. Rol bilgisi metnin **dışında** taşınır: üstte bir gösterge listesi, vurgunun `title`'ı ve rol başına farklı bir alt çizgi biçimi (düz/kesikli/noktalı/çift) — yani anlam yalnızca dört soluk tona bakarak ayırt edilmeye bırakılmaz (NFR-16).
3. **Çakışan aralıklar tek vurguya indirgenir.** Aralıklar gerçekten çakışıyor: bir metin birden fazla kayıt üretiyor ve her kayıt aynı metin üzerindeki kendi eşleşmelerini taşıyor; sınıflandırıcı hem `trafik kazası` hem `kazası` eşleştiriyor; aynı aralık iki rolle birden gelebiliyor. Her aralığı tek tek sarmak iç içe etiketler üretir, aynı kelimeyi iki kez boyar ve ikinci rolü kaybeder. Bunun yerine metin, **rol kümesinin değiştiği sınırlardan** bölünüyor; komşu parçalar aynı rol kümesine sahipse birleşiyor.
4. **Ham bildirim ekranı iki istek atar.** Ham metin ucundan metin, kayıt ucundan (`?rawReportId=`) türeyen kayıtlar ve analiz sonucu gelir — çünkü ikincisi `analysis`'in verisidir (ADR-021). Vurgulanan kelimeler **tüm** türeyen kayıtlardan toplanır.
5. **Reprocess yerinde tazeler, metni yeniden okumaz.** Makbuz dönen uç çağrıldıktan sonra kayıtlardan türeyen sorgular geçersizleştirilir; ham metin sorgusu `staleTime: Infinity` ile duruyor, çünkü ham kayıt write-once (ADR-005) — cevabı değişemeyecek bir soruyu tekrar sormanın anlamı yok.

**Bağlam.** FR-26 iki yönlü gezinmeyi ve "kelimelerin metin üzerinde vurgulanması"nı istiyor; kaynak dokümandaki "bold ile işaretlenmiş kelimeler" ipucunun görünür karşılığı bu. FR-17 offset'lerin saklanmasını ve dönülmesini T-14'te sözleşmeye eklemişti (C-3), gerekçesi de buradaki 1. madde: istemci kelimeyi yeniden ararsa Türkçe ek ve apostrof toleransı yüzünden yanlış yeri işaretler. TC-18 ise offset'lerin Unicode'da kaymaması sorusuydu.

**Gerekçe.**
- **Aramak neden yanlış:** il adları ekle geliyor (`Bursa'da`), aynı kelime metinde birden fazla kez geçebiliyor (üçüncü örnekte `Bursa'da` iki kez), ve normalize edilmiş eşleşme ham metindeki yazımla birebir aynı olmayabiliyor. Sunucunun verdiği konum bu üç sorunun da cevabı.
- **UTF-16 hizası bir tesadüf değil, iki platformun aynı sayması.** Java `String` ve JavaScript `string` aynı birimi sayar; dolayısıyla `text.slice(start, end)` tam olarak çıkarıcının eşleştirdiği karakterleri verir. Bir test bunu çalışan sistemden alınmış offset'lerle sabitliyor: saklanan konumdan dilimlenen metin, saklanan kelimenin kendisi.
- **Gizli açıklama kopyalamayı bozar.** Vurgunun içine konan "(il)" gibi bir metin ekran okuyucuda iyi okunur ama kopyalanan metne sızar — bu ekranda kabul edilemez. Rolü dışarıda taşımak hem metni temiz bırakıyor hem de bilgiyi görünür kılıyor.
- **Rol başına alt çizgi biçimi, renk körlüğüne karşı bedava sigorta.** Dört pastel ton birbirine yakın; biçim farkı ayrımı renge bağımlı olmaktan çıkarıyor.
- **Kayıt ekranı sonuçları değil kararları anlatıyor:** tarihin nereden geldiği, tipin tanınıp tanınmadığı, figürün illerle ilişkisi, hangi kelimenin hangi çıkarımı tetiklediği. İzlenebilirlik ekranının işi bu.
- **Adresteki kimlik kullanıcıdan gelir.** `/incidents/abc` için sunucuya `/incidents/NaN` sormak, yanlış bir adresi sunucu hatasına çevirirdi; sorgu böyle bir kimlikte hiç çalışmıyor. (Testle yakalandı — ilk sürüm isteği atıyordu.)

**Alternatifler.**
- *Kelimeyi metinde arayıp işaretlemek:* Offset'lere gerek kalmazdı. Ekli/apostroflu biçimlerde yanlış yeri işaretler, tekrar eden kelimelerde yalnızca ilkini bulur; C-3 tam olarak bunu önlemek için var.
- *Her anahtar kelimeyi kendi etiketiyle sarmak:* Basit döngü. Çakışan aralıklarda iç içe etiket ve çift boyama; ikinci rol kaybolur.
- *Vurgu içine ekran-okuyucu metni koymak:* Erişilebilirlik açısından en doğrudan yol. Kopyalanan metni bozar — bu ekranda metin ürünün kendisi.
- *`dangerouslySetInnerHTML` ile işaretlenmiş HTML üretmek:* Tek geçişte biterdi. Ham metni HTML'e gömmek, kullanıcı metnini işaretlemeye açar; React'in kaçışını bilerek devre dışı bırakmak için hiçbir sebep yok.
- *Ham bildirim ucunun türeyen kayıtları da döndürmesi:* Tek istek. `ingestion`'ın `analysis`'in verisini temsil etmesi demek (ADR-021).
- *Reprocess sonrası ham metni yeniden okumak:* "Her şeyi tazele" refleksi. Değişemeyecek bir cevabı yeniden istemek; ADR-005 write-once diyor.

**Sonuçlar.**
- `traceability/highlight.ts` saf ve DOM'suz test ediliyor; `HighlightedText`, `IncidentDetailPage`, `RawReportPage` görünüm katmanı.
- Kayıt listesine ve gönderim sonucuna "Aç" bağlantıları girdi; iki yön de artık arayüzden gezinilebiliyor (FR-08).
- **Çalışan sistemde doğrulandı:** ekrandaki metin, saklanan metinle **birebir aynı** (`identical: true`); `Bursa'da`, `Kocaeli'nde`, `kazası`, `hayatını kaybetti`, `yaralı` doğru yerlerde işaretli; `trafik kazası` tek vurgu ve `title` olarak "olay tipi, metrik" taşıyor; reprocess sonrası kayıtlar 26/27/28 → 49/50/51 oldu, **sayı üç kaldı**.
- `useIncident` yalnızca tam sayı kimlikte çalışıyor; `SubmissionResult` testleri artık router içinde render ediliyor, çünkü kart bir bağlantı taşıyor.

**İleride.** Vurgulama bugün tek bir `<p>` içinde; çok uzun metinlerde parça sayısı arttıkça sanallaştırma gerekebilir, ama bölme kuralı değişmez. Rol başına filtre ("yalnızca metrik tetikleyicilerini göster") aynı segment modelinin üzerine oturur. Ham bildirim listesi ucu (`GET /incident-reports`) bugün arayüzde kullanılmıyor; bir "son bildirimler" ekranı istenirse hazır. Reprocess bugün tek kayıt için; toplu reprocess ADR-035'in "İleride"sindeki analiz sürüm bilgisiyle birlikte anlamlı olur.
