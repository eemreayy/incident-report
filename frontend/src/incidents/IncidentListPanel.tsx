import { useIncidents, useMetadata } from '../api/queries';
import { isFiltered } from '../filters/incidentFilters';
import { useIncidentFilters } from '../filters/useIncidentFilters';
import { messageForError } from '../i18n/errorMessages';
import { strings } from '../i18n/strings';
import { IncidentTable } from './IncidentTable';

/**
 * The record list with its three states (FR-21, FR-28).
 *
 * It reads the filters from the address bar rather than from a parent, so the
 * filter bar above it is not wired to it at all - and the summary and the chart
 * will join on the same terms (TC-15).
 *
 * Paging is asked of the server, not applied to a full table held in memory: the
 * buttons below move a page number in the URL, and everything else follows from
 * the request that makes.
 */
export function IncidentListPanel() {
  const { filters, update } = useIncidentFilters();
  const { data, isPending, isError, error, refetch, isFetching, isPlaceholderData } =
    useIncidents(filters);
  const { data: metadata } = useMetadata();

  if (isPending) {
    return (
      <section className="panel" aria-busy="true">
        <h2>{strings.list.heading}</h2>
        <p className="muted">{strings.list.loading}</p>
      </section>
    );
  }

  if (isError) {
    return (
      <section className="panel">
        <h2>{strings.list.heading}</h2>
        <p className="error" role="alert">
          {messageForError(error)}
        </p>
        <button type="button" onClick={() => void refetch()}>
          {strings.list.retry}
        </button>
      </section>
    );
  }

  // The server counts pages from zero and reports none at all for an empty
  // result; the reader is looking at "page 1 of 1" either way.
  const totalPages = Math.max(data.totalPages, 1);
  const page = data.page + 1;

  return (
    <section className="panel">
      <h2>{strings.list.heading}</h2>

      {data.totalElements === 0 ? (
        <p>{isFiltered(filters) ? strings.list.emptyFiltered : strings.list.empty}</p>
      ) : (
        <>
          <p className="muted" aria-live="polite">
            {strings.list.total(data.totalElements)}
            {/* Shown while the previous page is still on screen: the table is
                deliberately not blanked during a refetch (FR-25). */}
            {isFetching && isPlaceholderData ? ` · ${strings.list.refreshing}` : ''}
          </p>
          <IncidentTable incidents={data.content} metadata={metadata} />
          <div className="pagination">
            <button
              type="button"
              className="secondary"
              disabled={page <= 1}
              onClick={() => update({ page: page - 1 })}
            >
              {strings.list.previous}
            </button>
            <span className="muted">{strings.list.pageStatus(page, totalPages)}</span>
            <button
              type="button"
              className="secondary"
              disabled={page >= totalPages}
              onClick={() => update({ page: page + 1 })}
            >
              {strings.list.next}
            </button>
          </div>
        </>
      )}
    </section>
  );
}
