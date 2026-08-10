import { useCallback, useMemo } from 'react';
import { useSearchParams } from 'react-router-dom';
import { parseFilters, toSearchParams, type IncidentFilters } from './incidentFilters';

/**
 * The one way to read and change the filters (TC-15).
 *
 * Every view that cares - the filter bar, the record list, and later the summary
 * and the chart - calls this hook instead of receiving props. They are therefore
 * not kept in step with each other; they are reading the same thing. Two views
 * showing different data would require two URLs, which cannot happen.
 *
 * Nothing here is stored: `useSearchParams` is the state, and the router is
 * already keeping history for it, so the back button undoes a filter change
 * exactly as a user expects.
 */
export function useIncidentFilters() {
  const [params, setParams] = useSearchParams();

  const filters = useMemo(() => parseFilters(params), [params]);

  /**
   * Changing what is being asked for always returns to the first page. Page 4 of
   * a narrower result is usually not a page at all, and an empty screen after
   * ticking a box reads as "no records" rather than "you are past the end".
   * Turning the page is the one change that is allowed to keep the page number.
   */
  const update = useCallback(
    (patch: Partial<IncidentFilters>) => {
      const changesFilter = Object.keys(patch).some((key) => key !== 'page');
      const next = { ...filters, ...patch };
      setParams(toSearchParams(changesFilter ? { ...next, page: 1 } : next));
    },
    [filters, setParams],
  );

  const clear = useCallback(() => setParams(new URLSearchParams()), [setParams]);

  return { filters, update, clear };
}
