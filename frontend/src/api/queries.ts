import {
  keepPreviousData,
  useMutation,
  useQuery,
  useQueryClient,
} from '@tanstack/react-query';
import {
  getIncident,
  getIncidentReport,
  getMetadata,
  getSummary,
  getTimeSeries,
  listIncidents,
  listIncidentsByRawReport,
  reprocessIncidentReport,
  submitIncidentReport,
  type TimeSeriesQuery,
} from './endpoints';
import { probeBackendHealth } from './health';
import { toApiQuery, toFilterQuery, type IncidentFilters } from '../filters/incidentFilters';

/**
 * Query keys in one place. When the stream starts telling us that something
 * changed (T-29), invalidation will name these keys - so a key invented inline
 * at a call site would be a key nothing can invalidate.
 *
 * Each family sits under one prefix on purpose: a signal says that records
 * changed, not which view of them did, so invalidation has to be able to name
 * all of them at once.
 */
export const queryKeys = {
  metadata: ['metadata'] as const,
  backendHealth: ['backend-health'] as const,
  incidents: ['incidents'] as const,
  incidentList: (filters: IncidentFilters) => ['incidents', 'list', filters] as const,
  incidentsByRawReport: (rawReportId: string) =>
    ['incidents', 'by-raw-report', rawReportId] as const,
  incident: (id: number) => ['incidents', 'one', id] as const,
  rawReport: (id: string) => ['raw-reports', id] as const,
  analytics: ['analytics'] as const,
  summary: (filters: IncidentFilters) => ['analytics', 'summary', filters] as const,
  timeSeries: (query: TimeSeriesQuery) => ['analytics', 'time-series', query] as const,
};

/**
 * Everything whose answer changes when a record is stored. The list, what a
 * submission produced, and every aggregate over them - a new record moves all of
 * them, so the stream (T-29) refreshes this list rather than naming views one by
 * one and forgetting the one added last.
 */
export const incidentDerivedKeys = [queryKeys.incidents, queryKeys.analytics] as const;

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

/**
 * The same view, totalled (FR-22). Keyed by the same filters as the list, so the
 * two cannot be showing different questions, and holding the previous answer for
 * the same reason the list does: a table that empties on every refresh reads as
 * a page reload.
 */
export function useSummary(filters: IncidentFilters) {
  return useQuery({
    queryKey: queryKeys.summary(filters),
    queryFn: ({ signal }) => getSummary(toFilterQuery(filters), signal),
    placeholderData: keepPreviousData,
  });
}

/**
 * The chart's series (FR-23). The whole query is the key - the filters and what
 * the chart asked of them - so switching back to a view already seen is instant
 * and switching the cumulative toggle is a different question, not a mutation of
 * this one's answer.
 *
 * `enabled` because there is nothing to plot until an event type is settled on:
 * a request with no type would draw every metric of every type on one axis.
 */
export function useTimeSeries(query: TimeSeriesQuery, enabled = true) {
  return useQuery({
    queryKey: queryKeys.timeSeries(query),
    queryFn: ({ signal }) => getTimeSeries(query, signal),
    placeholderData: keepPreviousData,
    enabled,
  });
}

export function useSubmitReport() {
  return useMutation({ mutationFn: (text: string) => submitIncidentReport(text) });
}

/**
 * One record and the words behind it (FR-17, FR-26).
 *
 * The id comes out of the address bar, so it can be anything a person typed.
 * Asking the server about `/incidents/NaN` would turn a wrong address into a
 * server error; the query simply does not run for one that cannot be an id.
 */
export function useIncident(id: number) {
  return useQuery({
    queryKey: queryKeys.incident(id),
    queryFn: ({ signal }) => getIncident(id, signal),
    enabled: Number.isInteger(id),
  });
}

/**
 * The stored text, exactly as submitted. It is written once and never touched
 * again (ADR-005), so this is the one query in the application whose answer
 * cannot change - reprocess included.
 */
export function useRawReport(id: string) {
  return useQuery({
    queryKey: queryKeys.rawReport(id),
    queryFn: ({ signal }) => getIncidentReport(id, signal),
    staleTime: Infinity,
  });
}

/**
 * Runs the rules over a stored text again (FR-15).
 *
 * The receipt says nothing about what came out, so everything derived from
 * records is marked stale and the screen reads the new answer through the same
 * queries it was already using. The connected clients hear about it on the
 * stream; this is the same refresh, arriving by the shorter route.
 */
export function useReprocess() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (rawReportId: string) => reprocessIncidentReport(rawReportId),
    onSuccess: () => {
      for (const queryKey of incidentDerivedKeys) {
        void queryClient.invalidateQueries({ queryKey });
      }
    },
  });
}
