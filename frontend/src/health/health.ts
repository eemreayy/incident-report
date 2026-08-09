/**
 * A single probe against the backend's health endpoint.
 *
 * This is not the API client - that arrives with T-24, together with the typed
 * contract and RFC 7807 parsing. What this exists for is to prove the decision
 * this task made: the browser reaches the API on its own origin (ADR-025), so
 * the URL below is relative and there is no configurable base address anywhere.
 * If the reverse proxy is wrong, this probe is what says so.
 */
export type BackendHealth = 'UP' | 'DOWN';

interface ActuatorHealthResponse {
  status?: string;
}

export async function probeBackendHealth(): Promise<BackendHealth> {
  const response = await fetch('/actuator/health');
  if (!response.ok) {
    return 'DOWN';
  }
  const body = (await response.json()) as ActuatorHealthResponse;
  return body.status === 'UP' ? 'UP' : 'DOWN';
}
