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
- **Tekrar yok.** Compose'un `include:` özelliği backend'in kendi compose dosyasını olduğu gibi alıyor. `app`, `postgres`, `mongodb` tanımları **tek yerde**, onları sahiplenen repo'da duruyor; devops repo'su yalnızca frontend'i ve servisler arası bağlantıyı ekliyor. Duplikasyon gerekseydi bu ayrım maliyetli olurdu.
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

**Karar.** Tarayıcı API'ye **kendi kökeni üzerinden** ulaşır. Frontend container'ındaki nginx statik dosyaları sunar ve `/api/*` ile `/actuator/health` isteklerini compose ağı üzerinden `app:8080`'e proxy'ler. Backend'de **CORS yapılandırması yoktur**; frontend kaynak kodunda **mutlak API adresi yoktur**. Geliştirmede aynı davranışı Vite'ın dev proxy'si verir.

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

**Sonuçlar.** Beş üretim bağımlılığı: React, React DOM, React Router, TanStack Query, Recharts. Recharts iskelette **kullanılmıyor** — ilk kullanıcısı T-28. Şimdi kurulmasının sebebi kararın kaydedilmesi ve React 19 ile birlikte çözülüp derlendiğinin doğrulanması; bu tür bir uyumsuzluğu T-28'de değil bugün öğrenmek gerekiyordu (doğrulandı: `npm run build` temiz geçiyor, üretim bundle'ı 264 kB / gzip 84 kB). TanStack Query'nin önbellek anahtarları filtre durumuyla birebir eşleşmek zorunda; bu, T-26'da filtre durumu tasarlanırken dikkat edilecek nokta.

**İleride.** Grafik ihtiyacı Recharts'ın sınırını zorlarsa (çok büyük veri, özel etkileşim) geçiş yalnızca grafik bileşenlerini etkiler — veri sunucudan hazır geldiği için (NFR-13) dönüştürme mantığı taşınmaz. TanStack Query'nin `invalidateQueries` yüzeyi, SSE sinyalinin bağlanacağı tek nokta olduğu için ileride taşımaya (WebSocket, polling) geçilse bile değişen yer tek kalır.
