import type { IncidentListQuery } from '../api/endpoints';

/**
 * The filter state, and the address bar that holds it (FR-21, TC-15).
 *
 * There is no second copy of this state anywhere: no store, no context, no
 * component that remembers what is selected. The query string *is* the state, so
 * a shared link, a reload and the back button all land on the same view for
 * free, and the record list, the summary and the chart cannot drift apart -
 * they read the same URL rather than being handed the same props.
 *
 * Everything here is a pure function over `URLSearchParams`, which is why the
 * rules below are tested without a DOM.
 */

export const SORT_OPTIONS = ['date-desc', 'date-asc'] as const;
export type IncidentSort = (typeof SORT_OPTIONS)[number];

/** Server-side paging (FR-21): a page is fetched, never a table sliced in the browser. */
export const PAGE_SIZE = 20;

export interface IncidentFilters {
  /** Catalog keys. Not validated against the catalog - see `parseFilters`. */
  eventTypes: string[];
  /** Licence-plate codes. */
  provinces: number[];
  /** Inclusive bounds, ISO `YYYY-MM-DD`, as the API takes them. */
  from: string | null;
  to: string | null;
  keyword: string | null;
  sort: IncidentSort;
  /**
   * One-based, because this number is read by a human in the address bar. The
   * API counts from zero and `toApiQuery` is the single place that converts.
   */
  page: number;
}

export const DEFAULT_FILTERS: IncidentFilters = {
  eventTypes: [],
  provinces: [],
  from: null,
  to: null,
  keyword: null,
  sort: 'date-desc',
  page: 1,
};

const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;

/**
 * Reads the address bar.
 *
 * Anything unreadable is dropped rather than rejected: a URL is typed, edited
 * and truncated by hand, and a hand-edited character is not worth an error
 * screen. What is *not* dropped is an unknown event type or province code -
 * validating those here would mean the catalog has to have loaded before the
 * URL can be understood, and would silently discard a type this build has not
 * heard of yet. The server is the one that knows the catalog (NFR-14).
 */
export function parseFilters(params: URLSearchParams): IncidentFilters {
  const sort = params.get('sort');
  return {
    eventTypes: unique(params.getAll('eventType').filter((key) => key.length > 0)).sort(),
    provinces: unique(params.getAll('province').map(toProvinceCode).filter(isNumber)).sort(
      (a, b) => a - b,
    ),
    from: toIsoDate(params.get('from')),
    to: toIsoDate(params.get('to')),
    keyword: toKeyword(params.get('keyword')),
    sort: isSort(sort) ? sort : DEFAULT_FILTERS.sort,
    page: toPage(params.get('page')),
  };
}

/**
 * Writes the address bar, leaving out everything that is at its default. The
 * unfiltered view therefore has a clean URL, and two ways of arriving at the
 * same view produce the same string - which matters because this string is also
 * what the query cache is keyed by.
 */
export function toSearchParams(filters: IncidentFilters): URLSearchParams {
  const params = new URLSearchParams();
  for (const key of [...filters.eventTypes].sort()) {
    params.append('eventType', key);
  }
  for (const code of [...filters.provinces].sort((a, b) => a - b)) {
    params.append('province', String(code));
  }
  if (filters.from) {
    params.set('from', filters.from);
  }
  if (filters.to) {
    params.set('to', filters.to);
  }
  if (filters.keyword) {
    params.set('keyword', filters.keyword);
  }
  if (filters.sort !== DEFAULT_FILTERS.sort) {
    params.set('sort', filters.sort);
  }
  if (filters.page > 1) {
    params.set('page', String(filters.page));
  }
  return params;
}

/**
 * Turns the view's filters into the request the server answers.
 *
 * Both translations that exist live here and nowhere else: the page number
 * (human, one-based) and the sort order (a name on screen, a pair of Spring
 * sort expressions on the wire - `id` after `occurredOn` so that two records
 * from the same day keep their order between pages).
 */
export function toApiQuery(filters: IncidentFilters): IncidentListQuery {
  const direction = filters.sort === 'date-asc' ? 'asc' : 'desc';
  return {
    eventTypes: filters.eventTypes,
    provinces: filters.provinces,
    from: filters.from,
    to: filters.to,
    keyword: filters.keyword,
    page: filters.page - 1,
    size: PAGE_SIZE,
    sort: [`occurredOn,${direction}`, `id,${direction}`],
  };
}

/** Whether anything is narrowing the result, which is what an empty result has to explain. */
export function isFiltered(filters: IncidentFilters): boolean {
  return (
    filters.eventTypes.length > 0 ||
    filters.provinces.length > 0 ||
    filters.from !== null ||
    filters.to !== null ||
    filters.keyword !== null
  );
}

function unique<T>(values: T[]): T[] {
  return [...new Set(values)];
}

function toProvinceCode(raw: string): number | null {
  const code = Number(raw);
  return Number.isInteger(code) && code > 0 ? code : null;
}

function isNumber(value: number | null): value is number {
  return value !== null;
}

function toIsoDate(raw: string | null): string | null {
  return raw !== null && ISO_DATE.test(raw) ? raw : null;
}

function toKeyword(raw: string | null): string | null {
  const trimmed = raw?.trim() ?? '';
  return trimmed.length > 0 ? trimmed : null;
}

function toPage(raw: string | null): number {
  const page = Number(raw);
  return Number.isInteger(page) && page > 1 ? page : DEFAULT_FILTERS.page;
}

function isSort(value: string | null): value is IncidentSort {
  return SORT_OPTIONS.includes(value as IncidentSort);
}
