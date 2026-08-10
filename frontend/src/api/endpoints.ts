import { request } from './client';
import type {
  Incident,
  IncidentPage,
  Metadata,
  Page,
  RawReport,
  RawReportReceipt,
  Summary,
  TimeSeries,
} from './types';

/**
 * The endpoints the backend serves today. Each one is a thin named call - the
 * knowledge of *how* to talk HTTP lives in client.ts, and the knowledge of what
 * the answers look like lives in types.ts.
 */

/** Feeds every choice the interface offers (FR-16, NFR-14). */
export function getMetadata(signal?: AbortSignal): Promise<Metadata> {
  return request<Metadata>('/metadata', signal ? { signal } : {});
}

/**
 * Submitting answers with a receipt: the raw report's id and when it arrived.
 * Not what was extracted - that is read back separately (ADR-021, FR-19).
 */
export function submitIncidentReport(text: string): Promise<RawReportReceipt> {
  return request<RawReportReceipt>('/incident-reports', { method: 'POST', body: { text } });
}

/**
 * One record, with the words that produced it and where they sat in the text
 * (FR-17). The offsets are what the raw report screen highlights with - without
 * them the client would have to search the text again and would mark the wrong
 * occurrence (C-3).
 */
export function getIncident(id: number, signal?: AbortSignal): Promise<Incident> {
  return request<Incident>(`/incidents/${id}`, signal ? { signal } : {});
}

/**
 * Runs the current rules over a stored text again (FR-15).
 *
 * Answers with a receipt, exactly as submission does (ADR-021): what the rules
 * made of it this time is read back through the records, not returned here.
 * The previous records are replaced rather than added to, so nothing doubles.
 */
export function reprocessIncidentReport(id: string): Promise<RawReportReceipt> {
  return request<RawReportReceipt>(`/incident-reports/${encodeURIComponent(id)}/reprocess`, {
    method: 'POST',
  });
}

export function getIncidentReport(id: string, signal?: AbortSignal): Promise<RawReport> {
  return request<RawReport>(
    `/incident-reports/${encodeURIComponent(id)}`,
    signal ? { signal } : {},
  );
}

export function listIncidentReports(
  params: { page?: number; size?: number } = {},
  signal?: AbortSignal,
): Promise<Page<RawReport>> {
  const query = new URLSearchParams();
  if (params.page !== undefined) query.set('page', String(params.page));
  if (params.size !== undefined) query.set('size', String(params.size));
  const suffix = query.size > 0 ? `?${query.toString()}` : '';
  return request<Page<RawReport>>(`/incident-reports${suffix}`, signal ? { signal } : {});
}

/**
 * The filters every reading endpoint takes, in the same spelling (FR-10). They
 * are shared rather than repeated because the record list, the summary and the
 * chart are three views of one dataset: a filter that reached one of them and
 * not another would put two contradictory answers on the same screen (FR-23).
 */
export interface IncidentFilterQuery {
  eventTypes?: string[];
  provinces?: number[];
  from?: string | null;
  to?: string | null;
  keyword?: string | null;
}

/** Adds what only the paged record listing takes: one report, a page, an order. */
export interface IncidentListQuery extends IncidentFilterQuery {
  rawReportId?: string | null;
  /** Zero-based, the way the server counts. */
  page?: number;
  size?: number;
  /** Spring sort expressions, e.g. `occurredOn,desc`. */
  sort?: string[];
}

function filterParams(query: IncidentFilterQuery): URLSearchParams {
  const params = new URLSearchParams();
  for (const eventType of query.eventTypes ?? []) {
    params.append('eventType', eventType);
  }
  for (const province of query.provinces ?? []) {
    params.append('province', String(province));
  }
  if (query.from) params.set('from', query.from);
  if (query.to) params.set('to', query.to);
  if (query.keyword) params.set('keyword', query.keyword);
  return params;
}

/**
 * The record list (FR-21).
 *
 * Filtering, sorting and paging are all in this query string: the browser never
 * receives rows it then hides. Which is also why every filter has to survive the
 * trip - a parameter dropped here would look exactly like a filter that did not
 * match anything.
 */
export function listIncidents(
  query: IncidentListQuery = {},
  signal?: AbortSignal,
): Promise<IncidentPage> {
  const params = filterParams(query);
  if (query.rawReportId) params.set('rawReportId', query.rawReportId);
  if (query.page !== undefined) params.set('page', String(query.page));
  if (query.size !== undefined) params.set('size', String(query.size));
  for (const sort of query.sort ?? []) {
    params.append('sort', sort);
  }

  const suffix = params.size > 0 ? `?${params.toString()}` : '';
  return request<IncidentPage>(`/incidents${suffix}`, signal ? { signal } : {});
}

/**
 * What a submission produced. The only way to answer "what was extracted",
 * because the receipt does not say (ADR-021, FR-19); the envelope also carries
 * the analysis outcome for this report.
 */
export function listIncidentsByRawReport(
  rawReportId: string,
  signal?: AbortSignal,
): Promise<IncidentPage> {
  return listIncidents({ rawReportId }, signal);
}

/** What the chart adds to the filters: a dimension, and a running total (FR-12, FR-24). */
export interface TimeSeriesQuery extends IncidentFilterQuery {
  groupBy?: 'province';
  cumulative?: boolean;
}

/**
 * The chart's series (FR-23).
 *
 * `cumulative` is a request, not a transformation to apply on arrival: what "the
 * total so far" means is a rule, and a second copy of it here would drift from
 * the server's (NFR-13). The same goes for the province dimension - the server
 * decides which points belong to which line.
 */
export function getTimeSeries(
  query: TimeSeriesQuery = {},
  signal?: AbortSignal,
): Promise<TimeSeries> {
  const params = filterParams(query);
  if (query.groupBy) params.set('groupBy', query.groupBy);
  if (query.cumulative) params.set('cumulative', 'true');

  const suffix = params.size > 0 ? `?${params.toString()}` : '';
  return request<TimeSeries>(`/analytics/time-series${suffix}`, signal ? { signal } : {});
}

/**
 * The summary table's numbers (FR-22, FR-24).
 *
 * Takes the filters and nothing else: there is no paging here because the answer
 * is already an aggregate, and no ordering because the server decides it - single
 * provinces by name, then the shared figures, then the records that named none.
 */
export function getSummary(
  query: IncidentFilterQuery = {},
  signal?: AbortSignal,
): Promise<Summary> {
  const params = filterParams(query);
  const suffix = params.size > 0 ? `?${params.toString()}` : '';
  return request<Summary>(`/analytics/summary${suffix}`, signal ? { signal } : {});
}
