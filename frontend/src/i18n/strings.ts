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
  catalog: {
    heading: 'Tanınan olay tipleri',
    note: 'Bu liste sunucudaki katalogdan gelir; arayüzde sabit yazılı değildir.',
    loading: 'Katalog yükleniyor…',
    provinceCount: (count: number) => `${count} il tanımlı`,
    metricCount: (count: number) => `${count} metrik`,
    empty: 'Katalogda tanımlı olay tipi yok.',
    retry: 'Tekrar dene',
  },
  placeholder: {
    heading: 'Arayüz kuruluyor',
    body: 'Bildirim girişi, kayıt listesi, özet tablo ve grafik sonraki adımlarda geliyor.',
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
