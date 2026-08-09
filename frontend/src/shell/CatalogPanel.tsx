import { useMetadata } from '../api/queries';
import { messageForError } from '../i18n/errorMessages';
import { strings } from '../i18n/strings';

/**
 * Shows the catalog the server publishes.
 *
 * This is not a screen from the PRD - the real ones start at T-25. It exists so
 * the rule this task is built around is visible and testable rather than merely
 * asserted: what the interface offers comes from /metadata, so adding an event
 * type to the YAML changes this list with no frontend release (NFR-14, FR-27).
 */
export function CatalogPanel() {
  const { data, isPending, isError, error, refetch } = useMetadata();

  if (isPending) {
    return (
      <section className="panel" aria-busy="true">
        <h2>{strings.catalog.heading}</h2>
        <p className="muted">{strings.catalog.loading}</p>
      </section>
    );
  }

  if (isError) {
    // FR-28: a failure states what happened and offers a way forward, rather
    // than leaving an empty box behind.
    return (
      <section className="panel">
        <h2>{strings.catalog.heading}</h2>
        <p className="error" role="alert">
          {messageForError(error)}
        </p>
        <button type="button" onClick={() => void refetch()}>
          {strings.catalog.retry}
        </button>
      </section>
    );
  }

  return (
    <section className="panel">
      <h2>{strings.catalog.heading}</h2>
      {data.eventTypes.length === 0 ? (
        <p className="muted">{strings.catalog.empty}</p>
      ) : (
        <ul className="catalog-list">
          {data.eventTypes.map((eventType) => (
            <li key={eventType.key}>
              <strong>{eventType.label}</strong>{' '}
              <span className="muted">{strings.catalog.metricCount(eventType.metrics.length)}</span>
            </li>
          ))}
        </ul>
      )}
      <p className="muted">{strings.catalog.provinceCount(data.provinces.length)}</p>
      <p className="muted">{strings.catalog.note}</p>
    </section>
  );
}
