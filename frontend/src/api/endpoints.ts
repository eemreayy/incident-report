import { request } from './client';
import type { Metadata, Page, RawReport, RawReportReceipt } from './types';

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
