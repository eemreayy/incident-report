import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { CatalogPanel } from './CatalogPanel';
import { strings } from '../i18n/strings';

const CATALOG = {
  eventTypes: [
    { key: 'EPIDEMIC', label: 'Salgın', metrics: [{ key: 'NEW_CASE', label: 'Yeni vaka' }] },
    { key: 'FLOOD', label: 'Sel', metrics: [] },
  ],
  provinces: [
    { code: 1, name: 'Adana' },
    { code: 6, name: 'Ankara' },
  ],
};

let fetchSpy: ReturnType<typeof vi.spyOn>;

function renderPanel() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <CatalogPanel />
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  fetchSpy = vi.spyOn(globalThis, 'fetch');
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe('CatalogPanel', () => {
  it('lists what the server publishes, with nothing hardcoded', async () => {
    fetchSpy.mockResolvedValue({ ok: true, status: 200, json: async () => CATALOG } as Response);

    renderPanel();

    // NFR-14: these labels exist in the response, not in the source. FLOOD is
    // the one the PRD added to show the catalog growing without a code change.
    expect(await screen.findByText('Salgın')).toBeInTheDocument();
    expect(screen.getByText('Sel')).toBeInTheDocument();
    expect(screen.getByText(strings.catalog.provinceCount(2))).toBeInTheDocument();
  });

  it('shows an event type the source has never heard of', async () => {
    fetchSpy.mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({
        eventTypes: [{ key: 'AVALANCHE', label: 'Çığ', metrics: [] }],
        provinces: [],
      }),
    } as Response);

    renderPanel();

    // The real test of NFR-14: adding a type to the YAML must reach the screen
    // without a frontend release.
    expect(await screen.findByText('Çığ')).toBeInTheDocument();
  });

  it('explains a failure in Turkish and offers a way forward', async () => {
    fetchSpy.mockRejectedValue(new TypeError('Failed to fetch'));

    renderPanel();

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent(strings.errors.byCode['network.unreachable'] as string);
    expect(screen.getByRole('button', { name: strings.catalog.retry })).toBeInTheDocument();
  });

  it('retries when asked, and shows the catalog once the server comes back', async () => {
    fetchSpy.mockRejectedValueOnce(new TypeError('Failed to fetch'));
    renderPanel();
    await screen.findByRole('alert');

    fetchSpy.mockResolvedValue({ ok: true, status: 200, json: async () => CATALOG } as Response);
    await userEvent.click(screen.getByRole('button', { name: strings.catalog.retry }));

    expect(await screen.findByText('Salgın')).toBeInTheDocument();
  });

  it('says so when the catalog is empty rather than rendering nothing', async () => {
    fetchSpy.mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({ eventTypes: [], provinces: [] }),
    } as Response);

    renderPanel();

    expect(await screen.findByText(strings.catalog.empty)).toBeInTheDocument();
  });
});
