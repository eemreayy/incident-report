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

        // Under the default 'online' mode, a query whose retries fail while the
        // library believes the browser is offline is *paused*: its status stays
        // 'pending' and never becomes 'error', which on screen is a spinner that
        // never stops - what FR-28 forbids. 'always' suits this application
        // because the API is on the page's own origin (ADR-025): if the page
        // loaded, there is no separate offline state worth modelling, and a
        // stated failure with a retry button beats an endless spinner.
        //
        // Worth knowing, because it looks like the same bug: retries are also
        // paused while the document is hidden - `canContinue()` in the retryer
        // ANDs focusManager.isFocused(), whatever the networkMode. A background
        // tab therefore holds its spinner until it is looked at again. That is
        // the library working as designed, not this setting failing.
        networkMode: 'always',
      },
    },
  });
}
