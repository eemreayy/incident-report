import { useEffect, useRef, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { apiUrl } from '../api/client';
import { incidentDerivedKeys, queryKeys } from '../api/queries';
import { useIncidentFilters } from '../filters/useIncidentFilters';
import { isRelevant, parseSignal } from './incidentSignal';
import type { IncidentPage } from '../api/types';

/**
 * The live connection (FR-25, ADR-004).
 *
 * One subscription for the page, opened once and closed when the page goes. Not
 * one per panel: three connections would mean three of everything - three
 * reconnects, three refreshes per submission - for one question, "has anything
 * changed".
 *
 * What arrives is a trigger, never data. Nothing on screen is built from a
 * signal; the queries that already own each view are marked stale and refetch
 * themselves, which is also why a dropped connection loses nothing but
 * liveness (ADR-021).
 */

/** How close together signals have to be to share one refresh. */
export const REFRESH_WINDOW_MS = 1000;

/** How long to wait before opening a stream the browser has given up on. */
export const RETRY_DELAY_MS = 5000;

export type StreamStatus = 'connecting' | 'open' | 'reconnecting' | 'closed';

export function useIncidentStream() {
  const queryClient = useQueryClient();
  const { filters } = useIncidentFilters();
  const [status, setStatus] = useState<StreamStatus>('connecting');

  // The stream is opened once; these keep the callbacks working with the
  // current filters without tearing the connection down and rebuilding it every
  // time somebody ticks a box.
  const filtersRef = useRef(filters);
  useEffect(() => {
    filtersRef.current = filters;
  }, [filters]);

  const lastRefreshRef = useRef(0);
  const pendingRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const wasBrokenRef = useRef(false);

  useEffect(() => {
    /**
     * Marks every view derived from records as stale. TanStack refetches the
     * ones on screen and leaves the data in place until the answer arrives, so
     * a refresh never blanks the table - a view that empties on every signal is
     * read as a page reload, which is exactly what live updating is meant to
     * avoid.
     */
    function refresh() {
      lastRefreshRef.current = Date.now();
      for (const queryKey of incidentDerivedKeys) {
        void queryClient.invalidateQueries({ queryKey });
      }
    }

    /**
     * Ten submissions in a row must not mean ten refreshes. The first signal
     * refreshes at once - the usual case is a single submission, and waiting
     * would make the interface feel slow for no reason - and anything arriving
     * within the window rides on one refresh scheduled at its end. So a burst
     * costs two requests rather than ten, and a steady trickle still refreshes
     * once per window rather than never, which is what a plain trailing
     * debounce would do.
     */
    function scheduleRefresh() {
      const waited = Date.now() - lastRefreshRef.current;
      if (waited >= REFRESH_WINDOW_MS) {
        refresh();
        return;
      }
      if (pendingRef.current === null) {
        pendingRef.current = setTimeout(() => {
          pendingRef.current = null;
          refresh();
        }, REFRESH_WINDOW_MS - waited);
      }
    }

    let source: EventSource | null = null;
    let retry: ReturnType<typeof setTimeout> | null = null;

    function connect() {
      const stream = new EventSource(apiUrl('/stream/incidents'));
      source = stream;

      stream.onopen = () => {
        setStatus('open');
        // The stream replays nothing (ADR-034), so anything that happened while
        // the connection was down was missed outright. Coming back is therefore
        // a reason to refetch, not just to change an indicator.
        if (wasBrokenRef.current) {
          wasBrokenRef.current = false;
          scheduleRefresh();
        }
      };

      stream.onerror = () => {
        wasBrokenRef.current = true;
        if (stream.readyState !== EventSource.CLOSED) {
          // Still retrying on its own; nothing to do but say so.
          setStatus('reconnecting');
          return;
        }
        // CLOSED means EventSource has given up, and it never tries again -
        // which for a page meant to stay live is silent death. Reconnecting is
        // therefore ours to do: a stopped backend answers through nginx with a
        // 502 rather than refusing the connection, and that is the case the
        // browser treats as fatal.
        setStatus('closed');
        stream.close();
        if (retry === null) {
          retry = setTimeout(() => {
            retry = null;
            setStatus('connecting');
            connect();
          }, RETRY_DELAY_MS);
        }
      };

      stream.addEventListener('incidents', (event) => {
        const signal = parseSignal((event as MessageEvent<string>).data);
        if (isRelevant(signal, filtersRef.current, shownRawReportIds(queryClient))) {
          scheduleRefresh();
        }
      });
    }

    connect();

    return () => {
      if (pendingRef.current !== null) {
        clearTimeout(pendingRef.current);
        pendingRef.current = null;
      }
      if (retry !== null) {
        clearTimeout(retry);
      }
      source?.close();
    };
  }, [queryClient]);

  return { status };
}

/**
 * Which reports the records currently on screen came from.
 *
 * This is what closes the one hole in judging relevance from the signal alone:
 * reprocess deletes a report's records and writes new ones (ADR-035), and the
 * ones that disappeared are precisely the ones the new signal does not mention.
 * A view showing them would keep showing them.
 */
function shownRawReportIds(queryClient: ReturnType<typeof useQueryClient>): string[] {
  const pages = queryClient.getQueriesData<IncidentPage>({ queryKey: queryKeys.incidents });
  return pages.flatMap(([, page]) => (page?.content ?? []).map((incident) => incident.rawReportId));
}
