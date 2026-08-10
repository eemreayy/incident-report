import type { ExtractedKeyword, KeywordRole } from '../api/types';

/**
 * Cutting the raw text into the pieces a highlighter draws (FR-26, TC-18).
 *
 * Two things make this less trivial than wrapping each keyword in a tag.
 *
 * The text is shown exactly as it was submitted (FR-02), so the positions have
 * to come from the server rather than from searching for the word again: a
 * province arrives suffixed (`Ankara'da`), a keyword can appear twice, and
 * searching would mark the wrong one. The offsets are UTF-16 code units, which
 * is what `String` counts in Java and what `string` counts here - so
 * `text.slice(start, end)` lands on exactly the characters the extractor
 * matched, Turkish letters included.
 *
 * And the ranges genuinely overlap. One text produces several records, each
 * carrying its own keywords, so the same date is reported once per record; the
 * classifier matches both `trafik kazası` and `kazası`; and one span can carry
 * two roles at once. Wrapping each range on its own would nest tags, draw the
 * same word twice and lose whichever role came second.
 */

export interface HighlightSegment {
  text: string;
  /** Empty for the stretches between keywords. Several when ranges overlap. */
  roles: KeywordRole[];
}

export function toSegments(text: string, keywords: ExtractedKeyword[]): HighlightSegment[] {
  const ranges = keywords.filter(
    (keyword) =>
      keyword.charStart >= 0 && keyword.charEnd <= text.length && keyword.charStart < keyword.charEnd,
  );

  if (ranges.length === 0) {
    return text.length === 0 ? [] : [{ text, roles: [] }];
  }

  // Every start and end is a point where the set of roles can change; between
  // two consecutive boundaries it cannot, so those stretches are the smallest
  // pieces worth drawing.
  const boundaries = [
    ...new Set([0, text.length, ...ranges.flatMap((r) => [r.charStart, r.charEnd])]),
  ].sort((a, b) => a - b);

  const segments: HighlightSegment[] = [];
  for (let i = 0; i < boundaries.length - 1; i += 1) {
    const start = boundaries[i] as number;
    const end = boundaries[i + 1] as number;
    const roles = rolesCovering(ranges, start, end);
    const previous = segments[segments.length - 1];

    // Neighbouring pieces that ended up with the same roles are one piece: the
    // reader should see one highlight over `trafik kazası`, not two touching
    // ones because a shorter keyword matched inside it.
    if (previous !== undefined && sameRoles(previous.roles, roles)) {
      previous.text += text.slice(start, end);
    } else {
      segments.push({ text: text.slice(start, end), roles });
    }
  }
  return segments;
}

function rolesCovering(ranges: ExtractedKeyword[], start: number, end: number): KeywordRole[] {
  const roles = new Set<KeywordRole>();
  for (const range of ranges) {
    if (range.charStart <= start && range.charEnd >= end) {
      roles.add(range.role);
    }
  }
  return [...roles].sort();
}

function sameRoles(left: KeywordRole[], right: KeywordRole[]): boolean {
  return left.length === right.length && left.every((role, index) => role === right[index]);
}
