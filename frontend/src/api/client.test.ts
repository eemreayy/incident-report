import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { request } from './client';
import { ApiError } from './problem';

function jsonResponse(body: unknown, init: { status?: number; ok?: boolean } = {}) {
  const status = init.status ?? 200;
  return {
    ok: init.ok ?? status < 400,
    status,
    json: async () => body,
  } as Response;
}

let fetchSpy: ReturnType<typeof vi.spyOn>;

beforeEach(() => {
  fetchSpy = vi.spyOn(globalThis, 'fetch');
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe('request', () => {
  it('calls a relative URL under /api/v1', async () => {
    fetchSpy.mockResolvedValue(jsonResponse({ ok: true }));

    await request('/metadata');

    const [url] = fetchSpy.mock.calls[0] ?? [];
    expect(url).toBe('/api/v1/metadata');
    // ADR-025: same origin. An absolute URL here would mean the reverse-proxy
    // decision had been undone without anyone noticing.
    expect(String(url)).not.toMatch(/^https?:\/\//);
  });

  it('sends a JSON body with a content type, and omits both when there is none', async () => {
    fetchSpy.mockResolvedValue(jsonResponse({}));

    await request('/incident-reports', { method: 'POST', body: { text: 'metin' } });
    const [, postInit] = fetchSpy.mock.calls[0] ?? [];
    expect(postInit).toMatchObject({ method: 'POST', body: JSON.stringify({ text: 'metin' }) });
    expect((postInit as RequestInit).headers).toMatchObject({
      'Content-Type': 'application/json',
    });

    fetchSpy.mockClear();
    await request('/metadata');
    const [, getInit] = fetchSpy.mock.calls[0] ?? [];
    expect((getInit as RequestInit).body).toBeUndefined();
    expect((getInit as RequestInit).headers).not.toHaveProperty('Content-Type');
  });

  it('turns a problem+json failure into an ApiError carrying its code', async () => {
    fetchSpy.mockResolvedValue(
      jsonResponse(
        {
          type: 'https://incident-report/problems/report.text.blank',
          title: 'Invalid request',
          status: 400,
          detail: 'Incident report text must not be empty.',
          code: 'report.text.blank',
        },
        { status: 400 },
      ),
    );

    const error = await request('/incident-reports', { method: 'POST', body: {} }).catch(
      (e: unknown) => e,
    );

    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({
      code: 'report.text.blank',
      status: 400,
      detail: 'Incident report text must not be empty.',
    });
  });

  it('still produces an error when the failure body is not problem+json', async () => {
    // Some middlebox answering 400 with HTML must not become a resolved
    // promise. 502 is deliberately not used here - it has its own meaning now,
    // asserted in the next test.
    fetchSpy.mockResolvedValue({
      ok: false,
      status: 400,
      json: async () => {
        throw new SyntaxError('Unexpected token <');
      },
    } as unknown as Response);

    const error = (await request('/metadata').catch((e: unknown) => e)) as ApiError;

    expect(error).toBeInstanceOf(ApiError);
    expect(error.code).toBe('response.unreadable');
    expect(error.status).toBe(400);
  });

  it('calls a stopped backend behind the proxy unavailable, not unreadable', async () => {
    // What a real outage looks like: nginx answers 502 with an HTML page. To a
    // reader that means "the server is down", not "the server said something
    // odd" - and the two deserve different sentences.
    fetchSpy.mockResolvedValue({
      ok: false,
      status: 502,
      json: async () => {
        throw new SyntaxError('Unexpected token <');
      },
    } as unknown as Response);

    const error = (await request('/metadata').catch((e: unknown) => e)) as ApiError;

    expect(error.code).toBe('gateway.unavailable');
    expect(error.status).toBe(502);
  });

  it('reports an unreachable server distinctly from a server that answered badly', async () => {
    fetchSpy.mockRejectedValue(new TypeError('Failed to fetch'));

    const error = (await request('/metadata').catch((e: unknown) => e)) as ApiError;

    expect(error.code).toBe('network.unreachable');
    expect(error.status).toBe(0);
  });

  it('lets an abort through unchanged instead of reporting it as a network failure', async () => {
    fetchSpy.mockRejectedValue(new DOMException('The operation was aborted.', 'AbortError'));

    const error = await request('/metadata').catch((e: unknown) => e);

    expect(error).toBeInstanceOf(DOMException);
    expect(error).not.toBeInstanceOf(ApiError);
  });

  it('does not try to parse a body out of 204', async () => {
    const json = vi.fn();
    fetchSpy.mockResolvedValue({ ok: true, status: 204, json } as unknown as Response);

    await expect(request('/whatever')).resolves.toBeUndefined();
    expect(json).not.toHaveBeenCalled();
  });

  it('fails when a successful response carries an unreadable body', async () => {
    fetchSpy.mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => {
        throw new SyntaxError('bozuk');
      },
    } as unknown as Response);

    const error = (await request('/metadata').catch((e: unknown) => e)) as ApiError;

    expect(error.code).toBe('response.unreadable');
  });
});
