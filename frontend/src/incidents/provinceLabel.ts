import { strings } from '../i18n/strings';
import type { Incident } from '../api/types';

/**
 * How a record relates to provinces, in words (ADR-019).
 *
 * Two views need this now - the submission result and the record table - and
 * they must not word it differently, because the wording is the whole point: a
 * shared figure belongs to none of its provinces alone, and a label that reads
 * like a province name would invite exactly the arithmetic the rule forbids.
 */
export function provinceLabel(incident: Incident): string {
  switch (incident.provinceScope) {
    case 'SINGLE':
      // The province key is absent rather than null for other scopes, so this
      // branch is the only one that may read it.
      return incident.province?.name ?? strings.incident.unknownProvince;
    case 'SHARED':
      return strings.incident.sharedProvinces;
    case 'UNKNOWN':
      return strings.incident.unknownProvince;
  }
}

/** The part that says who a shared figure covers, and that it was not split. */
export function provinceNote(incident: Incident): string | null {
  return incident.provinceScope === 'SHARED'
    ? strings.incident.sharedNote(incident.sharedAcross.map((province) => province.name).join(', '))
    : null;
}

export function provinceLine(incident: Incident): string {
  const note = provinceNote(incident);
  return note === null ? provinceLabel(incident) : `${provinceLabel(incident)} — ${note}`;
}
