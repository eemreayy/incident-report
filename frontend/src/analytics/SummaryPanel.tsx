import { useMetadata, useSummary } from '../api/queries';
import { isFiltered } from '../filters/incidentFilters';
import { useIncidentFilters } from '../filters/useIncidentFilters';
import { messageForError } from '../i18n/errorMessages';
import { metricLabel } from '../i18n/catalogLabels';
import { strings } from '../i18n/strings';
import { SummaryTable } from './SummaryTable';
import { toBlocks, totalMetricColumns } from './summaryModel';

/**
 * The same records as the table below, totalled (FR-22).
 *
 * It reads the filters from the address bar rather than from a parent, exactly
 * as the list does (ADR-037) - so the summary and the list cannot be answering
 * two different questions, and no wiring between them is needed to keep it that
 * way.
 */
export function SummaryPanel() {
  const { filters } = useIncidentFilters();
  const { data, isPending, isError, error, refetch, isFetching, isPlaceholderData } =
    useSummary(filters);
  const { data: metadata } = useMetadata();

  if (isPending) {
    return (
      <section className="panel" aria-busy="true">
        <h2>{strings.summary.heading}</h2>
        <p className="muted">{strings.summary.loading}</p>
      </section>
    );
  }

  if (isError) {
    return (
      <section className="panel">
        <h2>{strings.summary.heading}</h2>
        <p className="error" role="alert">
          {messageForError(error)}
        </p>
        <button type="button" onClick={() => void refetch()}>
          {strings.summary.retry}
        </button>
      </section>
    );
  }

  const blocks = toBlocks(data, metadata);

  if (blocks.length === 0) {
    return (
      <section className="panel">
        <h2>{strings.summary.heading}</h2>
        <p>{isFiltered(filters) ? strings.summary.emptyFiltered : strings.summary.empty}</p>
      </section>
    );
  }

  const totalMetrics = totalMetricColumns(data, metadata);

  return (
    <section className="panel">
      <h2>{strings.summary.heading}</h2>
      <p className="muted" aria-live="polite">
        {strings.summary.note}
        {isFetching && isPlaceholderData ? ` · ${strings.summary.refreshing}` : ''}
      </p>

      {blocks.map((block) => (
        <SummaryTable key={block.eventType} block={block} metadata={metadata} />
      ))}

      {/* Across every event type, and again the server's own number - shown only
          when there is more than one type to add across. With a single type it
          would be a second table repeating the first digit for digit, and a
          number printed twice invites the reader to look for a difference
          between them. */}
      {blocks.length > 1 && (
        <section className="summary-block">
          <h3>{strings.summary.grandTotal}</h3>
          <div className="table-scroll">
            <table className="summary-table">
              <thead>
                <tr>
                  <th scope="col">{strings.summary.column.incidentCount}</th>
                  {totalMetrics.map((metric) => (
                    <th key={metric} scope="col">
                      {/* No event type here, so the label falls back to any type
                        that declares the key - shared metrics carry the same
                        label in each (PRD 7). */}
                      {metricLabel(metadata, '', metric)}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                <tr>
                  <td>
                    <strong>{data.total.incidentCount}</strong>
                  </td>
                  {totalMetrics.map((metric) => (
                    <td key={metric}>
                      <strong>{data.total.metrics[metric] ?? strings.summary.noValue}</strong>
                    </td>
                  ))}
                </tr>
              </tbody>
            </table>
          </div>
        </section>
      )}
    </section>
  );
}
