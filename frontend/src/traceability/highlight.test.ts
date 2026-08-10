import { describe, expect, it } from 'vitest';
import { toSegments } from './highlight';
import type { ExtractedKeyword } from '../api/types';

function keyword(
  charStart: number,
  charEnd: number,
  role: ExtractedKeyword['role'] = 'METRIC',
  text = '',
): ExtractedKeyword {
  return { keyword: text, role, charStart, charEnd };
}

/**
 * The third sample text with the keywords the running system stored for it -
 * captured, not written. Three records came out of this one text, so the date
 * is reported three times, `trafik kazası` and `kazası` overlap, and the same
 * span arrives with two different roles.
 */
const SAMPLE_3 =
  "Son 24 saatte Bursa'da 8, Kocaeli'nde 6 trafik kazası meydana geldi. Bursa'da 1, " +
  "Kocaeli'nde ise 2 kişi kazalarda hayatını kaybetti. Her iki ilde toplam 10 kişi " +
  'yaralı olarak hastaneye kaldırıldı.';

const SAMPLE_3_KEYWORDS: ExtractedKeyword[] = [
  { keyword: 'Son 24 saatte', role: 'DATE', charStart: 0, charEnd: 13 },
  { keyword: 'Son 24 saatte', role: 'DATE', charStart: 0, charEnd: 13 },
  { keyword: "Bursa'da", role: 'PROVINCE', charStart: 14, charEnd: 22 },
  { keyword: "Kocaeli'nde", role: 'PROVINCE', charStart: 26, charEnd: 37 },
  { keyword: 'trafik kazası', role: 'EVENT_TYPE', charStart: 40, charEnd: 53 },
  { keyword: 'trafik kazası', role: 'METRIC', charStart: 40, charEnd: 53 },
  { keyword: 'kazası', role: 'EVENT_TYPE', charStart: 47, charEnd: 53 },
];

describe('toSegments', () => {
  it('marks the words the extractor matched and leaves the rest alone', () => {
    const segments = toSegments('Ankara’da 15 yeni vaka', [keyword(0, 9, 'PROVINCE')]);

    expect(segments).toEqual([
      { text: 'Ankara’da', roles: ['PROVINCE'] },
      { text: ' 15 yeni vaka', roles: [] },
    ]);
  });

  it('lands on the right characters in Turkish text', () => {
    // TC-18. The offsets are UTF-16 code units, which is what Java's String
    // counts and what a JS string counts - so ğ, ı, İ and ş occupy exactly one
    // position in both and the highlight cannot drift. The suffix is part of
    // the match, which is also why the client must not search for the word
    // again: "İzmir" would be found, "İzmir'de" is what was matched.
    const text = "İzmir'de sağlık ekipleri çalıştı";
    const segments = toSegments(text, [keyword(0, 8, 'PROVINCE')]);

    expect(segments[0]).toEqual({ text: "İzmir'de", roles: ['PROVINCE'] });
    expect(text.slice(0, 8)).toBe("İzmir'de");
  });

  it('reproduces every keyword the running system recorded', () => {
    // The strongest form of the offset claim: slicing the text at the stored
    // positions gives back the stored words, for a real report.
    for (const stored of SAMPLE_3_KEYWORDS) {
      expect(SAMPLE_3.slice(stored.charStart, stored.charEnd)).toBe(stored.keyword);
    }
  });

  it('draws one highlight where ranges overlap, carrying both roles', () => {
    // `trafik kazası` and `kazası` overlap, and the longer span is also a metric
    // trigger. Wrapping each range on its own would nest tags and mark the same
    // word twice.
    const segments = toSegments(SAMPLE_3, SAMPLE_3_KEYWORDS);
    const marked = segments.filter((segment) => segment.roles.length > 0);

    expect(marked.map((segment) => segment.text)).toEqual([
      'Son 24 saatte',
      "Bursa'da",
      "Kocaeli'nde",
      // One highlight over the whole phrase: the shorter keyword sits inside
      // the longer one and both carry the same roles, so splitting it would
      // draw a seam the reader has no way to interpret.
      'trafik kazası',
    ]);
    expect(marked[3]?.roles).toEqual(['EVENT_TYPE', 'METRIC']);
  });

  it('puts the text back together exactly as it came', () => {
    // Nothing is dropped, duplicated or reordered: the raw text is shown
    // unchanged (FR-02), highlighting or not.
    const segments = toSegments(SAMPLE_3, SAMPLE_3_KEYWORDS);

    expect(segments.map((segment) => segment.text).join('')).toBe(SAMPLE_3);
  });

  it('reports a repeated keyword once per place it appears', () => {
    // "Bursa'da" is in this text twice and each is its own highlight; a client
    // searching for the word would have marked only the first.
    const segments = toSegments(SAMPLE_3, [
      ...SAMPLE_3_KEYWORDS,
      { keyword: "Bursa'da", role: 'PROVINCE', charStart: 69, charEnd: 77 },
    ]);

    expect(segments.filter((segment) => segment.text === "Bursa'da")).toHaveLength(2);
  });

  it('merges two ranges that describe the same thing', () => {
    const segments = toSegments('ab', [keyword(0, 2, 'DATE'), keyword(0, 2, 'DATE')]);

    expect(segments).toEqual([{ text: 'ab', roles: ['DATE'] }]);
  });

  it('joins neighbouring pieces that ended up with the same roles', () => {
    // Two touching ranges of one role read as one highlight, not two boxes.
    const segments = toSegments('abcd', [keyword(0, 2, 'DATE'), keyword(2, 4, 'DATE')]);

    expect(segments).toEqual([{ text: 'abcd', roles: ['DATE'] }]);
  });

  it('ignores a range that does not fit the text', () => {
    // Defensive rather than expected: an offset past the end would otherwise
    // slice silently and shift everything after it.
    const segments = toSegments('kısa', [keyword(0, 99), keyword(-1, 2), keyword(3, 3)]);

    expect(segments).toEqual([{ text: 'kısa', roles: [] }]);
  });

  it('has nothing to draw for an empty text', () => {
    expect(toSegments('', [])).toEqual([]);
  });

  it('returns the whole text when nothing was matched', () => {
    expect(toSegments('metin', [])).toEqual([{ text: 'metin', roles: [] }]);
  });
});
