/**
 * RFC 7807 problem responses, parsed in exactly one place.
 *
 * Note what this file does *not* contain: any Turkish. The server's `detail` is
 * English ("Incident report text must not be empty."), so showing it raw would
 * put English in front of a Turkish user. `code` is the machine-readable half of
 * the contract and is what the interface translates - see i18n/errorMessages.
 */
export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  code?: string;
  timestamp?: string;
}

/** Codes the server never sends; produced here so every failure has one. */
export const CLIENT_ERROR_CODES = {
  /** The request never reached a server - offline, DNS, connection refused. */
  unreachable: 'network.unreachable',
  /** A response arrived but could not be read as the contract describes. */
  unreadable: 'response.unreadable',
  /**
   * The proxy answered but the backend behind it did not. Separated from
   * `unreadable` because the two mean different things to a reader: one is
   * "the server said something odd", the other is "the server is down". A
   * stopped backend produces exactly this - nginx returns 502 with an HTML
   * page, which is neither problem+json nor the application's fault.
   */
  gatewayUnavailable: 'gateway.unavailable',
} as const;

const GATEWAY_STATUSES = new Set([502, 503, 504]);

export class ApiError extends Error {
  readonly code: string;
  readonly status: number;
  /** The server's own wording, kept for logs and for codes we do not know. */
  readonly detail: string | null;

  constructor(code: string, status: number, detail: string | null) {
    super(`${code} (${status})${detail ? `: ${detail}` : ''}`);
    this.name = 'ApiError';
    this.code = code;
    this.status = status;
    this.detail = detail;
  }

  static unreachable(): ApiError {
    return new ApiError(CLIENT_ERROR_CODES.unreachable, 0, null);
  }

  /**
   * A failed response becomes an ApiError. A body that is not problem+json - a
   * proxy's HTML error page, say - still yields an error carrying the status,
   * because the caller must not have to tell those two cases apart.
   */
  static async fromResponse(response: Response): Promise<ApiError> {
    let problem: ProblemDetail | null;
    try {
      problem = (await response.json()) as ProblemDetail;
    } catch {
      problem = null;
    }
    const code =
      problem?.code ??
      (GATEWAY_STATUSES.has(response.status)
        ? CLIENT_ERROR_CODES.gatewayUnavailable
        : CLIENT_ERROR_CODES.unreadable);
    return new ApiError(code, response.status, problem?.detail ?? null);
  }
}
