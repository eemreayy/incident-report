import { onlineManager, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { createQueryClient } from './queryClient';
import { CatalogPanel } from '../shell/CatalogPanel';
import { strings } from '../i18n/strings';

afterEach(() => {
  onlineManager.setOnline(true);
  vi.restoreAllMocks();
});

describe('createQueryClient', () => {
  it('does not refetch on window focus, because the stream is the change signal', () => {
    const defaults = createQueryClient().getDefaultOptions().queries;

    expect(defaults?.refetchOnWindowFocus).toBe(false);
  });

  it('hands out a fresh client per call so tests cannot leak cache into each other', () => {
    expect(createQueryClient()).not.toBe(createQueryClient());
  });

  it('reports a failure even when the library believes the browser is offline', async () => {
    // The default networkMode pauses such a query: its status stays 'pending'
    // and the interface shows a spinner that never stops. This was observed
    // against a stopped backend, not imagined, and FR-28 rules it out - so the
    // assertion is on behaviour rather than on the setting that produces it.
    onlineManager.setOnline(false);
    vi.spyOn(globalThis, 'fetch').mockRejectedValue(new TypeError('Failed to fetch'));

    render(
      <QueryClientProvider client={createQueryClient()}>
        <CatalogPanel />
      </QueryClientProvider>,
    );

    const alert = await screen.findByRole('alert', {}, { timeout: 5000 });
    expect(alert).toHaveTextContent(strings.errors.byCode['network.unreachable'] as string);
    expect(screen.queryByText(strings.catalog.loading)).not.toBeInTheDocument();
  });
});
