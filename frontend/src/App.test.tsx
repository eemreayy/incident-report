import { QueryClient } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { App } from './App';
import { strings } from './i18n/strings';

/** Retries off: a test should assert the failure path, not wait for it. */
function testQueryClient() {
  return new QueryClient({ defaultOptions: { queries: { retry: false } } });
}

function renderApp() {
  return render(<App queryClient={testQueryClient()} />);
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('App', () => {
  it('renders the shell at the root route', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: true,
      json: async () => ({ status: 'UP' }),
    } as Response);

    renderApp();

    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent(strings.app.title);
    expect(await screen.findByText(new RegExp(strings.backendStatus.up))).toBeInTheDocument();
  });

  it('says the backend is unreachable instead of failing to render', async () => {
    vi.spyOn(globalThis, 'fetch').mockRejectedValue(new Error('connection refused'));

    renderApp();

    // FR-28: the interface does not go blank when the backend is gone. The
    // heading is still there next to the failure notice.
    expect(await screen.findByText(new RegExp(strings.backendStatus.down))).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 1 })).toBeInTheDocument();
  });

  it('carries the status in text, not only in colour', async () => {
    vi.spyOn(globalThis, 'fetch').mockRejectedValue(new Error('connection refused'));

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
