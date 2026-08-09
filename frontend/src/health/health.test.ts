import { afterEach, describe, expect, it, vi } from 'vitest';
import { probeBackendHealth } from './health';

function mockFetch(response: Partial<Response> & { json?: () => Promise<unknown> }) {
  return vi.spyOn(globalThis, 'fetch').mockResolvedValue(response as Response);
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('probeBackendHealth', () => {
  it('asks the backend on its own origin, with no absolute address', async () => {
    const fetchSpy = mockFetch({ ok: true, json: async () => ({ status: 'UP' }) });

    await probeBackendHealth();

    // The whole point of ADR-025: a relative path. An absolute one would mean
    // the same-origin decision had quietly been abandoned.
    expect(fetchSpy).toHaveBeenCalledWith('/actuator/health');
  });

  it('reports UP when the backend says so', async () => {
    mockFetch({ ok: true, json: async () => ({ status: 'UP' }) });

    await expect(probeBackendHealth()).resolves.toBe('UP');
  });

  it('reports DOWN when the backend answers with a failure status', async () => {
    mockFetch({ ok: false });

    await expect(probeBackendHealth()).resolves.toBe('DOWN');
  });

  it('reports DOWN when the body says anything other than UP', async () => {
    mockFetch({ ok: true, json: async () => ({ status: 'OUT_OF_SERVICE' }) });

    await expect(probeBackendHealth()).resolves.toBe('DOWN');
  });

  it('reports DOWN when the body carries no status at all', async () => {
    mockFetch({ ok: true, json: async () => ({}) });

    await expect(probeBackendHealth()).resolves.toBe('DOWN');
  });
});
