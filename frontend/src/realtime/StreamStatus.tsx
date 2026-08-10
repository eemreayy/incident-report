import { strings } from '../i18n/strings';
import type { StreamStatus as Status } from './useIncidentStream';

/**
 * Whether the page is being told about new records.
 *
 * Carried in text, like the backend indicator beside it (NFR-16), and it says
 * what still works when it is not: nothing becomes unreachable without the
 * stream, only automatic (ADR-021).
 */
export function StreamStatus({ status }: { status: Status }) {
  return (
    <p className="stream-status" data-state={status} role="status">
      <span aria-hidden="true">●</span> {strings.stream.label}: {strings.stream[status]}
      {status !== 'open' && <span className="muted"> · {strings.stream.note}</span>}
    </p>
  );
}
