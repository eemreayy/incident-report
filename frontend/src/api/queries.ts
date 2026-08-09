import { useQuery } from '@tanstack/react-query';
import { getMetadata } from './endpoints';
import { probeBackendHealth } from './health';

/**
 * Query keys in one place. When the stream starts telling us that something
 * changed (T-29), invalidation will name these keys - so a key invented inline
 * at a call site would be a key nothing can invalidate.
 */
export const queryKeys = {
  metadata: ['metadata'] as const,
  backendHealth: ['backend-health'] as const,
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
