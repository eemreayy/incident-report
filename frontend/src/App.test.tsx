import { QueryClient } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { App } from './App';
import { strings } from './i18n/strings';

const CATALOG = {
  eventTypes: [{ key: 'EPIDEMIC', label: 'Salgın', metrics: [{ key: 'NEW_CASE', label: 'Vaka' }] }],
  provinces: [{ code: 6, name: 'Ankara' }],
};

/**
 * The shell makes two independent calls - the health probe and the catalog - so
 * the stub answers by URL. A single blanket response would hand the catalog a
 * health payload and pass for the wrong reason.
 */
function stubBackend({ healthy = true }: { healthy?: boolean } = {}) {
  vi.spyOn(globalThis, 'fetch').mockImplementation((input: RequestInfo | URL) => {
    const url = String(input);
    if (!healthy) {
      return Promise.reject(new TypeError('Failed to fetch'));
    }
    const body = url.includes('/actuator/health') ? { status: 'UP' } : CATALOG;
    return Promise.resolve({ ok: true, status: 200, json: async () => body } as Response);
  });
}

/** Retries off: a test should assert the failure path, not wait for it. */
function renderApp() {
  return render(
    <App queryClient={new QueryClient({ defaultOptions: { queries: { retry: false } } })} />,
  );
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('App', () => {
  it('renders the shell at the root route', async () => {
    stubBackend();

    renderApp();

    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent(strings.app.title);
    expect(await screen.findByText(new RegExp(strings.backendStatus.up))).toBeInTheDocument();
  });

  it('fills the interface from the catalog the server publishes', async () => {
    stubBackend();

    renderApp();

    // NFR-14 end to end: the label comes from the response, not from the source.
    expect(await screen.findByText('Salgın')).toBeInTheDocument();
  });

  it('says the backend is unreachable instead of failing to render', async () => {
    stubBackend({ healthy: false });

    renderApp();

    // FR-28: the interface does not go blank when the backend is gone. The
    // heading is still there next to the failure notice.
    expect(await screen.findByText(new RegExp(strings.backendStatus.down))).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 1 })).toBeInTheDocument();
    expect(await screen.findByRole('alert')).toBeInTheDocument();
  });

  it('carries the status in text, not only in colour', async () => {
    stubBackend({ healthy: false });

    renderApp();

    // NFR-16: a state a reader can only get from a coloured dot is a state some
    // readers cannot get at all. Waiting on the text rather than on the role,
    // because the element is already there while the probe is still pending.
    await screen.findByText(new RegExp(strings.backendStatus.down));

    const status = screen.getByRole('status');
    expect(status).toHaveTextContent(strings.backendStatus.down);
    expect(status).toHaveAttribute('data-state', 'down');
  });
});
