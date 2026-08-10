import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { SummaryPanel } from './SummaryPanel';
import { strings } from '../i18n/strings';
import type { Summary } from '../api/types';

const CATALOG = {
  eventTypes: [
    {
      key: 'TRAFFIC_ACCIDENT',
      label: 'Trafik kazası',
      metrics: [
        { key: 'ACCIDENT_COUNT', label: 'Kaza sayısı' },
        { key: 'DEATH', label: 'Can kaybı' },
        { key: 'INJURED', label: 'Yaralı' },
      ],
    },
  ],
  provinces: [
    { code: 16, name: 'Bursa' },
    { code: 41, name: 'Kocaeli' },
  ],
};

/** Example 3 from PRD §11, captured from the running system. */
const EXAMPLE_3: Summary = {
  rows: [
    {
      eventType: 'TRAFFIC_ACCIDENT',
      provinceScope: 'SINGLE',
      province: { code: 16, name: 'Bursa' },
      incidentCount: 1,
      metrics: { ACCIDENT_COUNT: 8, DEATH: 1 },
    },
    {
      eventType: 'TRAFFIC_ACCIDENT',
      provinceScope: 'SINGLE',
      province: { code: 41, name: 'Kocaeli' },
      incidentCount: 1,
      metrics: { ACCIDENT_COUNT: 6, DEATH: 2 },
    },
    {
      eventType: 'TRAFFIC_ACCIDENT',
      provinceScope: 'SHARED',
      incidentCount: 1,
      metrics: { INJURED: 10 },
    },
  ],
  eventTypeTotals: [
    {
      eventType: 'TRAFFIC_ACCIDENT',
      incidentCount: 3,
      metrics: { ACCIDENT_COUNT: 14, DEATH: 3, INJURED: 10 },
    },
  ],
  total: { incidentCount: 3, metrics: { ACCIDENT_COUNT: 14, DEATH: 3, INJURED: 10 } },
};

const EMPTY: Summary = { rows: [], eventTypeTotals: [], total: { incidentCount: 0, metrics: {} } };

let fetchSpy: ReturnType<typeof vi.spyOn>;

function stubBackend(answer: (url: string) => unknown) {
  fetchSpy.mockImplementation((input: RequestInfo | URL) => {
    const url = String(input);
    const body = url.includes('/metadata') ? CATALOG : answer(url);
    return Promise.resolve({ ok: true, status: 200, json: async () => body } as Response);
  });
}

function renderPanel(initialUrl = '/') {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[initialUrl]}>
        <SummaryPanel />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

function rowNamed(name: string): HTMLElement {
  return screen.getByRole('rowheader', { name }).closest('tr') as HTMLElement;
}

function cells(row: HTMLElement): string[] {
  return [...row.querySelectorAll('td')].map((cell) => cell.textContent ?? '');
}

function summaryRequests(): string[] {
  return fetchSpy.mock.calls
    .map((call: unknown[]) => String(call[0]))
    .filter((url: string) => url.includes('/analytics/summary'));
}

beforeEach(() => {
  fetchSpy = vi.spyOn(globalThis, 'fetch');
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe('SummaryPanel', () => {
  it('gives each province its own row', async () => {
    // FR-24, and the first half of T-27's acceptance: example 3 must show Bursa
    // and Kocaeli separately rather than as one traffic-accident figure.
    stubBackend(() => EXAMPLE_3);

    renderPanel();

    expect(await screen.findByRole('rowheader', { name: 'Bursa' })).toBeInTheDocument();
    expect(cells(rowNamed('Bursa'))).toEqual(['1', '8', '1', '—']);
    expect(cells(rowNamed('Kocaeli'))).toEqual(['1', '6', '2', '—']);
  });

  it('adds the shared figure to neither province, and shows it as its own row', async () => {
    // ADR-019. The ten injured people are in the shared row and nowhere else -
    // the dash in each province's injured column is the assertion that matters.
    stubBackend(() => EXAMPLE_3);

    renderPanel();

    await screen.findByRole('rowheader', { name: strings.incident.sharedProvinces });
    const shared = rowNamed(strings.incident.sharedProvinces);
    expect(cells(shared)).toEqual(['1', '—', '—', '10']);
    expect(within(rowNamed('Bursa')).queryByText('10')).not.toBeInTheDocument();
    expect(within(rowNamed('Kocaeli')).queryByText('10')).not.toBeInTheDocument();
  });

  it('shows the total the server sent, not the sum of the rows on screen', async () => {
    // 8 + 6 = 14 accidents, but the injured column reads 10 while both province
    // rows say nothing: the difference is the shared row, and that is the data.
    stubBackend(() => EXAMPLE_3);

    renderPanel();

    await screen.findByRole('rowheader', { name: 'Bursa' });
    expect(cells(rowNamed(strings.summary.eventTypeTotal))).toEqual(['3', '14', '3', '10']);
  });

  it('says in words why the province rows do not add up to the total', async () => {
    // Without this line a reader who does the addition finds a discrepancy and
    // has every reason to read it as a bug.
    stubBackend(() => EXAMPLE_3);

    renderPanel();

    expect(
      await screen.findByText(strings.summary.reconcile(strings.incident.sharedProvinces)),
    ).toBeInTheDocument();
  });

  it('does not explain a difference that is not there', async () => {
    stubBackend(() => ({
      rows: [
        {
          eventType: 'TRAFFIC_ACCIDENT',
          provinceScope: 'SINGLE',
          province: { code: 16, name: 'Bursa' },
          incidentCount: 1,
          metrics: { ACCIDENT_COUNT: 8 },
        },
      ],
      eventTypeTotals: [
        { eventType: 'TRAFFIC_ACCIDENT', incidentCount: 1, metrics: { ACCIDENT_COUNT: 8 } },
      ],
      total: { incidentCount: 1, metrics: { ACCIDENT_COUNT: 8 } },
    }));

    renderPanel();

    await screen.findByRole('rowheader', { name: 'Bursa' });
    expect(screen.queryByText(/hiçbir ile eklenmez/)).not.toBeInTheDocument();
  });

  it('labels a figure whose text named no province', async () => {
    stubBackend(() => ({
      rows: [
        {
          eventType: 'TRAFFIC_ACCIDENT',
          provinceScope: 'UNKNOWN',
          incidentCount: 1,
          metrics: { INJURED: 7 },
        },
      ],
      eventTypeTotals: [
        { eventType: 'TRAFFIC_ACCIDENT', incidentCount: 1, metrics: { INJURED: 7 } },
      ],
      total: { incidentCount: 1, metrics: { INJURED: 7 } },
    }));

    renderPanel();

    const unknown = await screen.findByRole('rowheader', {
      name: strings.incident.unknownProvince,
    });
    expect(unknown).toBeInTheDocument();
    expect(screen.getByText(strings.summary.reconcile(strings.incident.unknownProvince))).toBeInTheDocument();
  });

  it('names the columns from the catalog', async () => {
    // NFR-14: metric labels come from /metadata, never from this source.
    stubBackend(() => EXAMPLE_3);

    renderPanel();

    const heading = await screen.findByRole('heading', { name: 'Trafik kazası' });
    const block = within(heading.parentElement as HTMLElement);
    expect(block.getByRole('columnheader', { name: 'Kaza sayısı' })).toBeInTheDocument();
    expect(block.getByRole('columnheader', { name: 'Yaralı' })).toBeInTheDocument();
  });

  it('asks the server for a filtered total rather than totalling a filtered view', async () => {
    // FR-22: the summary is an aggregate from the endpoint, consistent with the
    // active filters - and the filters come from the same address bar the record
    // list reads (ADR-037), so the two cannot answer different questions.
    stubBackend(() => EXAMPLE_3);

    renderPanel('/?eventType=TRAFFIC_ACCIDENT&province=16&province=41&from=2020-06-01&keyword=kaza');

    await screen.findByRole('rowheader', { name: 'Bursa' });
    const url = summaryRequests()[0] ?? '';
    expect(url).toContain('eventType=TRAFFIC_ACCIDENT');
    expect(url).toContain('province=16');
    expect(url).toContain('province=41');
    expect(url).toContain('from=2020-06-01');
    expect(url).toContain('keyword=kaza');
    // Paging and ordering are the record list's business; an aggregate has no
    // pages, and asking for one would total a narrower set than the table shows.
    expect(url).not.toContain('page=');
    expect(url).not.toContain('sort=');
  });

  it('refetches when the filters change, instead of re-totalling what it has', async () => {
    stubBackend(() => EXAMPLE_3);

    renderPanel();
    await screen.findByRole('rowheader', { name: 'Bursa' });

    // A second panel would be a second copy of the filter state; the address bar
    // is the only one, so this test drives it the way the filter bar does.
    window.history.pushState({}, '', '/?province=16');
    renderPanel('/?province=16');

    await vi.waitFor(() => expect(summaryRequests().length).toBeGreaterThan(1));
    expect(summaryRequests().some((url) => url.includes('province=16'))).toBe(true);
  });

  it('totals across event types, and again takes the number from the server', async () => {
    stubBackend(() => ({
      rows: [
        {
          eventType: 'TRAFFIC_ACCIDENT',
          provinceScope: 'SHARED',
          incidentCount: 1,
          metrics: { INJURED: 10 },
        },
        {
          eventType: 'FIRE',
          provinceScope: 'SINGLE',
          province: { code: 6, name: 'Ankara' },
          incidentCount: 1,
          metrics: { INJURED: 3 },
        },
      ],
      eventTypeTotals: [
        { eventType: 'TRAFFIC_ACCIDENT', incidentCount: 1, metrics: { INJURED: 10 } },
        { eventType: 'FIRE', incidentCount: 1, metrics: { INJURED: 3 } },
      ],
      total: { incidentCount: 2, metrics: { INJURED: 13 } },
    }));

    renderPanel();

    const grandTotal = await screen.findByRole('heading', { name: strings.summary.grandTotal });
    const table = grandTotal.parentElement?.querySelector('table') as HTMLElement;
    expect(cells(table.querySelector('tbody tr') as HTMLElement)).toEqual(['2', '13']);
  });

  it('does not print the same total twice when there is one event type', async () => {
    // With a single type the grand total repeats the type total digit for digit,
    // and a number printed twice invites a reader to look for a difference.
    stubBackend(() => EXAMPLE_3);

    renderPanel();

    await screen.findByRole('rowheader', { name: 'Bursa' });
    expect(
      screen.queryByRole('heading', { name: strings.summary.grandTotal }),
    ).not.toBeInTheDocument();
  });

  it('says nothing has been entered yet when nothing has', async () => {
    stubBackend(() => EMPTY);

    renderPanel();

    expect(await screen.findByText(strings.summary.empty)).toBeInTheDocument();
  });

  it('says the filters matched nothing when filters are what is on', async () => {
    stubBackend(() => EMPTY);

    renderPanel('/?eventType=FLOOD');

    expect(await screen.findByText(strings.summary.emptyFiltered)).toBeInTheDocument();
  });

  it('states a failure and offers a way forward', async () => {
    // FR-28, asserted by what the retry does rather than by its presence.
    fetchSpy.mockRejectedValueOnce(new TypeError('Failed to fetch'));
    fetchSpy.mockRejectedValueOnce(new TypeError('Failed to fetch'));
    stubBackend(() => EXAMPLE_3);

    renderPanel();

    expect(await screen.findByRole('alert')).toHaveTextContent(
      strings.errors.byCode['network.unreachable'] as string,
    );

    await userEvent.click(screen.getByRole('button', { name: strings.summary.retry }));

    expect(await screen.findByRole('rowheader', { name: 'Bursa' })).toBeInTheDocument();
  });
});
