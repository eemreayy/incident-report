import { QueryClient } from '@tanstack/react-query';

/**
 * Server-state defaults for the whole application (ADR-026).
 *
 * `placeholderData: keepPreviousData` is set per query rather than here, but the
 * principle it serves belongs at this level: a refresh must never blank the view
 * (FR-25). Data already on screen stays there until the new answer arrives -
 * otherwise every SSE signal would flash the table empty, which a user reads as
 * a page reload even though no navigation happened.
 */
export function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        // The stream tells us when something changed (ADR-021), so polling on a
        // timer would only duplicate that signal.
        refetchOnWindowFocus: false,
        staleTime: 30_000,
        retry: 1,
      },
    },
  });
}
