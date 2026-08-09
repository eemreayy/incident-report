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
  placeholder: {
    heading: 'Arayüz kuruluyor',
    body: 'Bildirim girişi, kayıt listesi, özet tablo ve grafik sonraki adımlarda geliyor.',
  },
} as const;
