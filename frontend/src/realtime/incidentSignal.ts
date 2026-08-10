import type { IncidentFilters } from '../filters/incidentFilters';

/**
 * What the stream says, and how the client decides whether to care (FR-25).
 *
 * The signal carries no figures: enough to judge relevance, never enough to draw
 * a row (ADR-021, ADR-034). So the only two things done with it are the two
 * below - decide whether this view could have changed, and if so, refetch.
 */

export interface IncidentSignalRecord {
  incidentId: number;
  occurredOn: string;
  eventType: string;
  /**
   * Which provinces this record answers for. One code for a record about a
   * single province, several for a figure shared between them, none at all when
   * the text named none - so one intersection test covers all three scopes
   * (ADR-034).
   */
  provinceCodes: number[];
}

export interface IncidentSignal {
  rawReportId: string;
  analyzedAt: string;
  incidents: IncidentSignalRecord[];
}

/** Anything unreadable is treated as "something happened" - see `isRelevant`. */
export function parseSignal(data: string): IncidentSignal | null {
  try {
    const parsed = JSON.parse(data) as IncidentSignal;
    return Array.isArray(parsed?.incidents) ? parsed : null;
  } catch {
    return null;
  }
}

/**
 * Whether this signal could have changed what is on screen.
 *
 * The bias is deliberate and one-way: a signal is skipped only when it *proves*
 * it cannot matter here. Everything else refreshes. A needless refetch costs one
 * request and changes nothing on screen; a skipped one leaves a figure that has
 * moved looking settled, and nothing later corrects it - the stream never sends
 * anything twice.
 *
 * Three things make a signal relevant regardless of its contents:
 *
 * - the view is already showing records from this report, because reprocess
 *   deletes and rewrites (ADR-035) and the records that vanished are exactly the
 *   ones this signal no longer mentions;
 * - a keyword filter is active, which the signal cannot speak to at all - it
 *   carries no keywords, and guessing would be inventing an answer;
 * - nothing is filtered, so everything matters.
 *
 * Otherwise the signal is relevant when any of its records passes the filters
 * that *can* be judged from it. Note what is not attempted: no record is drawn
 * from this, no count is adjusted, nothing is added to a table. Only "is it
 * worth asking the server again".
 */
export function isRelevant(
  signal: IncidentSignal | null,
  filters: IncidentFilters,
  shownRawReportIds: readonly string[],
): boolean {
  if (signal === null) {
    return true;
  }
  if (shownRawReportIds.includes(signal.rawReportId)) {
    return true;
  }
  if (filters.keyword !== null) {
    return true;
  }
  if (
    filters.eventTypes.length === 0 &&
    filters.provinces.length === 0 &&
    filters.from === null &&
    filters.to === null
  ) {
    return true;
  }
  return signal.incidents.some((record) => matches(record, filters));
}

function matches(record: IncidentSignalRecord, filters: IncidentFilters): boolean {
  if (filters.eventTypes.length > 0 && !filters.eventTypes.includes(record.eventType)) {
    return false;
  }
  if (
    filters.provinces.length > 0 &&
    // No codes means the text named no province, and a province-filtered view
    // does not contain such a record - so this correctly fails to match.
    !record.provinceCodes.some((code) => filters.provinces.includes(code))
  ) {
    return false;
  }
  // ISO dates compare correctly as strings, which is why the API uses them.
  if (filters.from !== null && record.occurredOn < filters.from) {
    return false;
  }
  if (filters.to !== null && record.occurredOn > filters.to) {
    return false;
  }
  return true;
}
