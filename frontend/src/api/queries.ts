import { useMutation, useQuery } from '@tanstack/react-query';
import { getMetadata, listIncidentsByRawReport, submitIncidentReport } from './endpoints';
import { probeBackendHealth } from './health';

/**
 * Query keys in one place. When the stream starts telling us that something
 * changed (T-29), invalidation will name these keys - so a key invented inline
 * at a call site would be a key nothing can invalidate.
 */
export const queryKeys = {
  metadata: ['metadata'] as const,
  backendHealth: ['backend-health'] as const,
  incidentsByRawReport: (rawReportId: string) => ['incidents', { rawReportId }] as const,
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

export function useSubmitReport() {
  return useMutation({ mutationFn: (text: string) => submitIncidentReport(text) });
}
