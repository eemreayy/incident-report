import { ApiError, CLIENT_ERROR_CODES } from './problem';

/**
 * The single door to the backend (NFR-13).
 *
 * Every path here is relative. The API is on this page's own origin - nginx in
 * production, the Vite proxy in development (ADR-025) - so there is no base URL
 * to configure and none to get wrong. Introducing one would quietly undo that
 * decision, which is why a test asserts the shape of the URL this builds.
 */
const API_ROOT = '/api/v1';

/**
 * For the one endpoint that is not fetched: the stream is opened by
 * `EventSource`, which builds its own request. It still has to come through this
 * file, or the rule above would hold for every address but that one.
 */
export function apiUrl(path: string): string {
  return `${API_ROOT}${path}`;
}

interface RequestOptions {
  method?: string;
  body?: unknown;
  signal?: AbortSignal;
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body, signal } = options;

  const init: RequestInit = { method, headers: buildHeaders(body !== undefined) };
  if (body !== undefined) {
    init.body = JSON.stringify(body);
  }
  if (signal) {
    init.signal = signal;
  }

  let response: Response;
  try {
    response = await fetch(`${API_ROOT}${path}`, init);
  } catch (cause) {
    // fetch only rejects when the request never completed. A 500 is a resolved
    // promise, and is handled below - the two are different failures.
    if (cause instanceof DOMException && cause.name === 'AbortError') {
      throw cause;
    }
    throw ApiError.unreachable();
  }

  if (!response.ok) {
    throw await ApiError.fromResponse(response);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  try {
    return (await response.json()) as T;
  } catch {
    throw new ApiError(CLIENT_ERROR_CODES.unreadable, response.status, null);
  }
}

function buildHeaders(hasBody: boolean): HeadersInit {
  return hasBody
    ? { Accept: 'application/json', 'Content-Type': 'application/json' }
    : { Accept: 'application/json' };
}
