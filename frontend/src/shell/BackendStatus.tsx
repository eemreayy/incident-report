import { useBackendHealth } from '../api/queries';
import { strings } from '../i18n/strings';

/**
 * Shows whether the backend is reachable.
 *
 * The state is carried by text, not only by colour (NFR-16) - a status a reader
 * can only get from a green dot is a status some readers cannot get at all.
 */
export function BackendStatus() {
  const { data, isPending, isError } = useBackendHealth();

  const state = isPending ? 'checking' : isError || data === 'DOWN' ? 'down' : 'up';

  return (
    <p className="backend-status" data-state={state} role="status">
      <span aria-hidden="true">●</span> {strings.backendStatus.label}:{' '}
      {strings.backendStatus[state === 'checking' ? 'checking' : state]}
    </p>
  );
}
