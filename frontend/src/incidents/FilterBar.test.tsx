import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, useLocation } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { FilterBar } from './FilterBar';
import { strings } from '../i18n/strings';

/** Captured from the running system, like every fixture in this codebase. */
const CATALOG = {
  eventTypes: [
    { key: 'EPIDEMIC', label: 'Salgın', metrics: [{ key: 'NEW_CASE', label: 'Yeni vaka' }] },
    { key: 'EARTHQUAKE', label: 'Deprem', metrics: [{ key: 'INJURED', label: 'Yaralı' }] },
  ],
  provinces: [
    { code: 6, name: 'Ankara' },
    { code: 16, name: 'Bursa' },
    { code: 41, name: 'Kocaeli' },
  ],
};

function CurrentSearch() {
  return <p data-testid="search">{useLocation().search}</p>;
}

function renderBar(initialUrl = '/') {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[initialUrl]}>
        <FilterBar />
        <CurrentSearch />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

function search() {
  return screen.getByTestId('search').textContent ?? '';
}

beforeEach(() => {
  vi.spyOn(globalThis, 'fetch').mockResolvedValue({
    ok: true,
    status: 200,
    json: async () => CATALOG,
  } as Response);
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe('FilterBar', () => {
  it('offers what the server publishes, and nothing written here', async () => {
    // FR-27 / NFR-14: adding FLOOD to the YAML has to reach this bar with no
    // frontend release, which is only true while no list lives in the source.
    renderBar();

    expect(await screen.findByRole('checkbox', { name: 'Salgın' })).toBeInTheDocument();
    expect(screen.getByRole('checkbox', { name: 'Deprem' })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: 'Kocaeli' })).toBeInTheDocument();
  });

  it('puts a chosen event type in the address bar', async () => {
    renderBar();

    await userEvent.click(await screen.findByRole('checkbox', { name: 'Deprem' }));

    expect(search()).toContain('eventType=EARTHQUAKE');
  });

  it('shows what the address bar already says, so a shared link opens ticked', async () => {
    renderBar('/?eventType=EPIDEMIC&province=16&from=2020-05-01&keyword=deprem');

    expect(await screen.findByRole('checkbox', { name: 'Salgın' })).toBeChecked();
    expect(screen.getByRole('checkbox', { name: 'Deprem' })).not.toBeChecked();
    expect(screen.getByRole('option', { name: 'Bursa' })).toBeInTheDocument();
    expect(screen.getByLabelText(strings.filters.from)).toHaveValue('2020-05-01');
    expect(screen.getByLabelText(strings.filters.keyword)).toHaveValue('deprem');
  });

  it('unticks a type by removing it from the address, not by hiding rows', async () => {
    renderBar('/?eventType=EPIDEMIC&eventType=EARTHQUAKE');

    await userEvent.click(await screen.findByRole('checkbox', { name: 'Salgın' }));

    expect(search()).not.toContain('EPIDEMIC');
    expect(search()).toContain('eventType=EARTHQUAKE');
  });

  it('puts every chosen province in the address bar', async () => {
    // Two at once is the case that matters: a figure shared between them has to
    // come back once, and the server can only do that if it is told about both
    // (ADR-019).
    renderBar();
    await screen.findByRole('option', { name: 'Bursa' });

    await userEvent.selectOptions(screen.getByLabelText(strings.filters.province), ['16', '41']);

    expect(search()).toContain('province=16');
    expect(search()).toContain('province=41');
  });

  it('applies the keyword when the search is submitted, not on every keystroke', async () => {
    renderBar();
    const field = screen.getByLabelText(strings.filters.keyword);

    await userEvent.type(field, 'deprem');
    expect(search()).not.toContain('keyword');

    await userEvent.click(screen.getByRole('button', { name: strings.filters.apply }));
    expect(search()).toContain('keyword=deprem');
  });

  it('treats a blank search box as no keyword filter', async () => {
    renderBar('/?keyword=deprem');

    await userEvent.clear(screen.getByLabelText(strings.filters.keyword));
    await userEvent.click(screen.getByRole('button', { name: strings.filters.apply }));

    expect(search()).not.toContain('keyword');
  });

  it('puts a date range in the address bar', async () => {
    renderBar();

    await userEvent.type(screen.getByLabelText(strings.filters.from), '2020-05-01');

    expect(search()).toContain('from=2020-05-01');
  });

  it('asks the server for a different order rather than reordering rows', async () => {
    renderBar();

    await userEvent.selectOptions(
      screen.getByLabelText(strings.filters.sort),
      strings.filters.sortOption['date-asc'],
    );

    expect(search()).toContain('sort=date-asc');
  });

  it('clears every filter at once', async () => {
    renderBar('/?eventType=EPIDEMIC&province=6&keyword=vaka&page=3');

    await userEvent.click(screen.getByRole('button', { name: strings.filters.clear }));

    expect(search()).toBe('');
    expect(await screen.findByRole('checkbox', { name: 'Salgın' })).not.toBeChecked();
  });

  it('says the catalog failed rather than loading forever', async () => {
    // FR-28. A pending message left on screen after the request failed is the
    // interface telling the user to keep waiting for something that is not
    // coming; the filters that do not need the catalog keep working.
    vi.spyOn(globalThis, 'fetch').mockRejectedValue(new TypeError('Failed to fetch'));

    renderBar();

    expect(await screen.findByRole('alert')).toHaveTextContent(
      strings.errors.byCode['network.unreachable'] as string,
    );
    expect(screen.queryByText(strings.filters.loading)).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: strings.filters.retry })).toBeInTheDocument();
    expect(screen.getByLabelText(strings.filters.keyword)).toBeEnabled();
  });

  it('still lets the analyst filter while the catalog is on its way', async () => {
    // FR-28: a pending query says so instead of leaving an unexplained gap, and
    // the controls that do not depend on it keep working.
    renderBar();

    expect(screen.getByText(strings.filters.loading)).toBeInTheDocument();
    expect(screen.getByLabelText(strings.filters.keyword)).toBeEnabled();
    await screen.findByRole('checkbox', { name: 'Salgın' });
  });
});
