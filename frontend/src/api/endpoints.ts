import { request } from './client';
import type { IncidentPage, Metadata, Page, RawReport, RawReportReceipt } from './types';

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
 * Everything `GET /incidents` accepts. All optional and combinable, exactly as
 * the endpoint has them (FR-10) - repeated `eventType` and `province` for the
 * multi-valued ones, and Spring's `page`/`size`/`sort` for the envelope.
 */
export interface IncidentListQuery {
  eventTypes?: string[];
  provinces?: number[];
  from?: string | null;
  to?: string | null;
  keyword?: string | null;
  rawReportId?: string | null;
  /** Zero-based, the way the server counts. */
  page?: number;
  size?: number;
  /** Spring sort expressions, e.g. `occurredOn,desc`. */
  sort?: string[];
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
