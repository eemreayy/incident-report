/**
 * Every user-visible string in the application, in one place.
 *
 * The rest of the codebase is English - identifiers, comments, file names. What
 * the analyst reads is Turkish, because the analyst is (PRD 2.2). Keeping the
 * two apart means a Turkish word never appears inside a component, and adding a
 * second language later would touch this file and nothing else.
 */
export const strings = {
  app: {
    title: 'Olay Bildirim Sistemi',
    subtitle: 'Açık kaynak metinlerinden yapılandırılmış olay verisi',
  },
  backendStatus: {
    label: 'Sunucu',
    checking: 'kontrol ediliyor',
    up: 'bağlı',
    down: 'ulaşılamıyor',
  },
  form: {
    heading: 'Yeni olay bildirimi',
    label: 'Açık kaynaktan aldığınız metni buraya girin',
    hint: 'Tarih, il, olay tipi ve sayılar metinden otomatik çıkarılır. Cümlelerin sırası önemli değildir.',
    placeholder:
      'Örnek: 20.04.2020 tarihinde Ankara’da salgın kapsamında yapılan testlerde 15 yeni vaka tespit edildi.',
    submit: 'Gönder',
    submitting: 'Gönderiliyor…',
    charCount: (count: number) => `${count} karakter`,
    emptyHint: 'Göndermek için metin girin.',
  },
  result: {
    heading: 'Bu bildirimden çıkarılanlar',
    loading: 'Sonuç getiriliyor…',
    retry: 'Tekrar dene',
    recordCount: (count: number) => `${count} kayıt üretildi`,
    // FR-19: zero records is a legitimate answer, so the screen says so rather
    // than showing an empty box.
    none: 'Bu metinden yapılandırılmış kayıt üretilemedi. Ham metin saklandı ve kurallar geliştikçe yeniden işlenebilir.',
    failed: 'Analiz başarısız oldu. Ham metin saklandı; sorun giderildiğinde yeniden işlenebilir.',
    missing: 'Bu bildirime ait bir analiz kaydı bulunamadı.',
  },
  filters: {
    heading: 'Filtreler',
    eventType: 'Olay tipi',
    province: 'İl',
    provinceHint: 'Birden fazla il seçmek için Ctrl (Mac’te Cmd) ile tıklayın.',
    from: 'Başlangıç tarihi',
    to: 'Bitiş tarihi',
    keyword: 'Anahtar kelime',
    keywordHint: 'Metinden çıkarılan anahtar kelimelerde aranır.',
    apply: 'Ara',
    clear: 'Filtreleri temizle',
    sort: 'Sıralama',
    sortOption: {
      'date-desc': 'Tarih: yeniden eskiye',
      'date-asc': 'Tarih: eskiden yeniye',
    },
    loading: 'Filtre seçenekleri yükleniyor…',
    note: 'Seçtiğiniz filtreler adres çubuğuna yansır; bağlantıyı paylaştığınızda aynı görünüm açılır.',
  },
  chart: {
    heading: 'Zaman serisi',
    loading: 'Grafik getiriliyor…',
    refreshing: 'Güncelleniyor…',
    retry: 'Tekrar dene',
    eventType: 'Grafikteki olay tipi',
    metric: 'Karşılaştırılan metrik',
    breakdown: 'İl kırılımı',
    cumulative: 'Kümülatif',
    // FR-12: the two look alike and read completely differently, so the chart
    // says which one it is - and says it from the answer, not from the switch.
    cumulativeOn: 'Her nokta kendisi ve kendinden öncekilerin toplamıdır.',
    plain: 'Her nokta o güne ait değerdir.',
    breakdownHint: 'İl kırılımında yalnızca tek bir metrik çizilir; iller ancak aynı sayı üzerinden karşılaştırılabilir.',
    legendHint: 'Bir seriyi gizlemek veya göstermek için göstergedeki adına tıklayın.',
    empty: 'Grafiğe çizilecek veri yok. Yukarıdaki formdan bir bildirim girebilirsiniz.',
    emptyFiltered: 'Seçtiğiniz filtrelere ve olay tipine uyan veri yok.',
    noEventTypes: 'Katalogda çizilebilecek bir olay tipi yok.',
    // The chart's own settings never change which records are counted; saying so
    // keeps a reader from reading the chart as a narrower table.
    scopeNote:
      'Grafik, aşağıdaki tablolarla aynı filtreleri kullanır; olay tipi ve metrik seçimi yalnızca hangi serilerin çizildiğini belirler.',
    dateAxis: 'Tarih',
  },
  summary: {
    heading: 'Özet',
    loading: 'Özet getiriliyor…',
    refreshing: 'Güncelleniyor…',
    retry: 'Tekrar dene',
    empty: 'Özetlenecek kayıt yok. Yukarıdaki formdan bir bildirim girebilirsiniz.',
    emptyFiltered: 'Seçtiğiniz filtrelere uyan kayıt yok.',
    note: 'Bütün toplamlar sunucudan gelir; bu tabloda hiçbir sayı tarayıcıda hesaplanmaz.',
    column: {
      breakdown: 'İl / kapsam',
      incidentCount: 'Kayıt',
    },
    eventTypeTotal: 'Tip toplamı',
    grandTotal: 'Genel toplam',
    noValue: '—',
    /**
     * ADR-019 in one sentence, shown only where it applies. A reader who adds
     * the province rows and compares them with the total below is *right* to
     * find a difference — and without this line would reasonably read it as a
     * bug rather than as the one thing the text did not say.
     */
    reconcile: (labels: string) =>
      `${labels} satırları hiçbir ile eklenmez; bu yüzden il satırlarının toplamı tip toplamına eşit değildir. Tip toplamı = il satırları + bu satırlar.`,
  },
  list: {
    heading: 'Kayıtlar',
    loading: 'Kayıtlar getiriliyor…',
    refreshing: 'Güncelleniyor…',
    retry: 'Tekrar dene',
    total: (count: number) => `${count} kayıt bulundu`,
    // FR-21: an empty result says which of the two empties it is, because
    // "there is nothing" and "nothing matches what you asked for" are different
    // answers and only one of them is fixed by changing the filters.
    empty: 'Henüz kayıt yok. Yukarıdaki formdan bir bildirim girerek başlayabilirsiniz.',
    emptyFiltered: 'Seçtiğiniz filtrelere uyan kayıt yok. Filtreleri genişletmeyi deneyin.',
    column: {
      date: 'Tarih',
      eventType: 'Olay tipi',
      province: 'İl',
      metrics: 'Metrikler',
    },
    noMetrics: 'Metrik yok',
    previous: 'Önceki',
    next: 'Sonraki',
    pageStatus: (page: number, totalPages: number) => `Sayfa ${page} / ${totalPages}`,
  },
  incident: {
    unknownProvince: 'İl belirtilmemiş',
    sharedProvinces: 'Ortak toplam',
    // ADR-019: the figure belongs to none of them alone, so the wording must not
    // suggest it can be attributed to one.
    sharedNote: (names: string) => `${names} illerine ait, ayrıştırılamayan toplam`,
    metricsHeading: 'Metrikler',
    noMetrics: 'Sayısal metrik çıkarılamadı',
    unclassified: 'Olay tipi tanınamadı',
    /**
     * `OTHER` is not a catalog entry - it is what the code produces when nothing
     * matched (ADR-006), so it never appears in /metadata. ADR-007's addendum
     * draws exactly this line: data that grows on its own comes from the
     * catalog, structural values that change only with code belong to the typed
     * client contract. This is one of those, so labelling it here is not the
     * hardcoding NFR-14 forbids.
     */
    otherEventType: 'Diğer / Belirsiz',
    dateSource: {
      EXPLICIT: 'tarih metinde açıkça yazıyor',
      RELATIVE: 'tarih göreli bir ifadeden çözüldü',
      DEFAULTED: 'metinde tarih yok, gönderim tarihi kullanıldı',
    },
    /** The same three facts, in a table cell's worth of room (FR-06). */
    dateSourceShort: {
      EXPLICIT: 'metinden',
      RELATIVE: 'göreli ifadeden',
      DEFAULTED: 'gönderim tarihi',
    },
    unclassifiedNote:
      'Katalogda eşleşen bir olay tipi yok. Kayıt reddedilmedi; çıkarılabilen bilgiler saklandı.',
  },
  /**
   * Keyed by the error contract's `code`, which is the half of an RFC 7807
   * response meant to be read by a machine. The server's own `detail` is
   * English and is deliberately never shown.
   */
  errors: {
    byCode: {
      'report.text.blank': 'Bildirim metni boş olamaz.',
      'report.text.too-long': 'Bildirim metni izin verilen uzunluğu aşıyor.',
      'resource.not-found': 'Aradığınız kayıt bulunamadı.',
      'query.date-range.invalid': 'Başlangıç tarihi bitiş tarihinden sonra olamaz.',
      'request.bad-request': 'İstek anlaşılamadı.',
      'request.method-not-allowed': 'Bu işlem bu adres için geçerli değil.',
      'request.unsupported-media-type': 'İstek biçimi desteklenmiyor.',
      internal: 'Sunucuda beklenmeyen bir hata oluştu.',
      'network.unreachable': 'Sunucuya ulaşılamıyor. Bağlantınızı kontrol edin.',
      'response.unreadable': 'Sunucudan beklenmeyen bir cevap geldi.',
      'gateway.unavailable': 'Sunucuya şu anda ulaşılamıyor. Kısa süre sonra tekrar deneyin.',
    } as Record<string, string | undefined>,
    unknown: 'Beklenmeyen bir hata oluştu.',
  },
} as const;
