import { keepPreviousData, useMutation, useQuery } from '@tanstack/react-query';
import {
  getMetadata,
  listIncidents,
  listIncidentsByRawReport,
  submitIncidentReport,
} from './endpoints';
import { probeBackendHealth } from './health';
import { toApiQuery, type IncidentFilters } from '../filters/incidentFilters';

/**
 * Query keys in one place. When the stream starts telling us that something
 * changed (T-29), invalidation will name these keys - so a key invented inline
 * at a call site would be a key nothing can invalidate.
 *
 * Everything read from /incidents sits under one prefix on purpose: a signal
 * says that records changed, not which view of them did, so invalidation has to
 * be able to name all of them at once.
 */
export const queryKeys = {
  metadata: ['metadata'] as const,
  backendHealth: ['backend-health'] as const,
  incidents: ['incidents'] as const,
  incidentList: (filters: IncidentFilters) => ['incidents', 'list', filters] as const,
  incidentsByRawReport: (rawReportId: string) =>
    ['incidents', 'by-raw-report', rawReportId] as const,
};

/**
 * The catalog changes only when the server restarts with a different YAML, so
 * it is worth holding on to: every dropdown in the application reads from this
 * one query rather than fetching its own copy.
 */
export function useMetadata() {
  return useQuery({
    queryKey: queryKeys.metadata,
    queryFn: ({ signal }) => getMetadata(signal),
    staleTime: Infinity,
  });
}

export function useBackendHealth() {
  return useQuery({
    queryKey: queryKeys.backendHealth,
    queryFn: probeBackendHealth,
  });
}

/**
 * What a submission produced. Enabled only once there is an id to ask about, so
 * the hook can sit in the tree before anything has been submitted.
 *
 * Analysis runs inside the submit request today (ADR-003), so by the time the
 * receipt arrives this query has something to find. If analysis ever moves off
 * the request, the same query answers "not analysed yet" and the stream fills it
 * in - the component above does not change (FR-19).
 */
export function useIncidentsByRawReport(rawReportId: string | null) {
  return useQuery({
    queryKey: queryKeys.incidentsByRawReport(rawReportId ?? ''),
    queryFn: ({ signal }) => listIncidentsByRawReport(rawReportId as string, signal),
    enabled: rawReportId !== null,
  });
}

/**
 * The filtered, sorted, paged record list (FR-21).
 *
 * The filters are the key, so every distinct view is cached separately and going
 * back to one is instant. `keepPreviousData` is what stops the table blanking
 * while the next page - or the next refresh, once the stream drives one (FR-25)
 * - is on its way: a view that empties on every change reads as a page reload.
 */
export function useIncidents(filters: IncidentFilters) {
  return useQuery({
    queryKey: queryKeys.incidentList(filters),
    queryFn: ({ signal }) => listIncidents(toApiQuery(filters), signal),
    placeholderData: keepPreviousData,
  });
}

export function useSubmitReport() {
  return useMutation({ mutationFn: (text: string) => submitIncidentReport(text) });
}
