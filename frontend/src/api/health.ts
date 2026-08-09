/**
 * A single probe against the backend's health endpoint.
 *
 * It sits beside the API client but does not go through it: /actuator is not
 * under /api/v1 and does not answer with problem+json, so running it through a
 * helper built for that contract would only blur what each one guarantees.
 *
 * The URL is relative, like every other call - the browser reaches the backend on
 * its own origin (ADR-025). If the reverse proxy is wrong, this is what says so.
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
