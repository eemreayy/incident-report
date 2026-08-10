import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { IncidentListPanel } from './IncidentListPanel';
import { strings } from '../i18n/strings';
import type { Incident, IncidentPage } from '../api/types';

const CATALOG = {
  eventTypes: [
    {
      key: 'EARTHQUAKE',
      label: 'Deprem',
      metrics: [
        { key: 'INJURED', label: 'Yaralı' },
        { key: 'DEATH', label: 'Can kaybı' },
      ],
    },
  ],
  provinces: [
    { code: 16, name: 'Bursa' },
    { code: 41, name: 'Kocaeli' },
  ],
};

/** Example 3 from PRD §11, as the endpoint answers it. */
const BURSA: Incident = {
  id: 1,
  rawReportId: 'r1',
  occurredOn: '2020-06-01',
  dateSource: 'RELATIVE',
  eventType: 'EARTHQUAKE',
  classification: 'CLASSIFIED',
  provinceScope: 'SINGLE',
  province: { code: 16, name: 'Bursa' },
  sharedAcross: [],
  metrics: [
    { metricType: 'DAMAGED_BUILDING', value: 8 },
    { metricType: 'DEATH', value: 1 },
  ],
  keywords: [],
};

const SHARED: Incident = {
  id: 3,
  rawReportId: 'r1',
  occurredOn: '2020-06-01',
  dateSource: 'RELATIVE',
  eventType: 'EARTHQUAKE',
  classification: 'CLASSIFIED',
  provinceScope: 'SHARED',
  sharedAcross: [
    { code: 16, name: 'Bursa' },
    { code: 41, name: 'Kocaeli' },
  ],
  metrics: [{ metricType: 'INJURED', value: 10 }],
  keywords: [],
};

function page(content: Incident[], patch: Partial<IncidentPage> = {}): IncidentPage {
  return {
    content,
    page: 0,
    size: 20,
    totalElements: content.length,
    totalPages: content.length === 0 ? 0 : 1,
    ...patch,
  };
}

let fetchSpy: ReturnType<typeof vi.spyOn>;

/** Answers by URL: the panel reads the catalog as well as the records. */
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
        <IncidentListPanel />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

function incidentRequests(): string[] {
  return fetchSpy.mock.calls
    .map((call: unknown[]) => String(call[0]))
    .filter((url: string) => url.includes('/incidents'));
}

beforeEach(() => {
  fetchSpy = vi.spyOn(globalThis, 'fetch');
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe('IncidentListPanel', () => {
  it('shows the records the server sent, with their labels from the catalog', async () => {
    stubBackend(() => page([BURSA]));

    renderPanel();

    expect(await screen.findByText('Bursa')).toBeInTheDocument();
    const row = screen.getByText('Bursa').closest('tr') as HTMLElement;
    expect(within(row).getByText('Deprem')).toBeInTheDocument();
    expect(within(row).getByText('Can kaybı:')).toBeInTheDocument();
    expect(within(row).getByText('2020-06-01')).toBeInTheDocument();
    expect(within(row).getByText(strings.incident.dateSourceShort.RELATIVE)).toBeInTheDocument();
  });

  it('shows a shared figure as its own labelled row, attributed to no province', async () => {
    // ADR-019: never split across the provinces, never dropped. A reader has to
    // be able to reconcile the per-province figures with the grand total, and
    // this row is the only way that is possible.
    stubBackend(() => page([BURSA, SHARED]));

    renderPanel();

    const shared = (await screen.findByText(strings.incident.sharedProvinces)).closest(
      'tr',
    ) as HTMLElement;
    expect(within(shared).getByText(/Bursa, Kocaeli/)).toHaveTextContent(
      strings.incident.sharedNote('Bursa, Kocaeli'),
    );
    expect(within(shared).getByText('Yaralı:')).toBeInTheDocument();
  });

  it('sends the filters to the server instead of narrowing rows here', async () => {
    // FR-21, and the reason it is asserted on the request rather than on the
    // screen: a table filtered in the browser looks identical until the day the
    // result no longer fits on one page.
    stubBackend(() => page([BURSA]));

    renderPanel('/?eventType=EARTHQUAKE&province=16&from=2020-06-01&to=2020-06-30&keyword=deprem');

    await screen.findByText('Bursa');
    const url = incidentRequests()[0] ?? '';
    expect(url).toContain('eventType=EARTHQUAKE');
    expect(url).toContain('province=16');
    expect(url).toContain('from=2020-06-01');
    expect(url).toContain('to=2020-06-30');
    expect(url).toContain('keyword=deprem');
  });

  it('draws exactly what came back, even when it does not match the filter', async () => {
    // The strongest available proof that no filtering happens here: the server
    // is made to answer with a record the filter excludes, and it is drawn.
    // A client-side filter would swallow it.
    stubBackend(() => page([BURSA]));

    renderPanel('/?province=41');

    expect(await screen.findByText('Bursa')).toBeInTheDocument();
  });

  it('asks the server for the next page rather than paging in memory', async () => {
    stubBackend((url) =>
      url.includes('page=1')
        ? page([SHARED], { page: 1, totalElements: 2, totalPages: 2 })
        : page([BURSA], { totalElements: 2, totalPages: 2 }),
    );

    renderPanel();

    await screen.findByText('Bursa');
    await userEvent.click(screen.getByRole('button', { name: strings.list.next }));

    expect(await screen.findByText(strings.incident.sharedProvinces)).toBeInTheDocument();
    expect(incidentRequests().some((url) => url.includes('page=1'))).toBe(true);
    expect(screen.getByText(strings.list.pageStatus(2, 2))).toBeInTheDocument();
  });

  it('stops at both ends of the result', async () => {
    stubBackend(() => page([BURSA], { totalElements: 2, totalPages: 2 }));

    renderPanel();

    await screen.findByText('Bursa');
    expect(screen.getByRole('button', { name: strings.list.previous })).toBeDisabled();
    expect(screen.getByRole('button', { name: strings.list.next })).toBeEnabled();
  });

  it('says nothing has been entered yet when nothing has', async () => {
    stubBackend(() => page([]));

    renderPanel();

    expect(await screen.findByText(strings.list.empty)).toBeInTheDocument();
  });

  it('says the filters matched nothing when filters are what is on', async () => {
    // FR-21: an empty table with no explanation leaves the analyst wondering
    // whether the system is broken. Only one of the two empties is fixed by
    // changing the filters, so they must not read the same.
    stubBackend(() => page([]));

    renderPanel('/?eventType=EARTHQUAKE');

    expect(await screen.findByText(strings.list.emptyFiltered)).toBeInTheDocument();
  });

  it('does not call paging a filter', async () => {
    stubBackend(() => page([], { page: 3 }));

    renderPanel('/?page=4');

    expect(await screen.findByText(strings.list.empty)).toBeInTheDocument();
  });

  it('states a failure and offers a way forward', async () => {
    // FR-28. The retry is asserted by what it does, not by its presence.
    fetchSpy.mockRejectedValueOnce(new TypeError('Failed to fetch'));
    fetchSpy.mockRejectedValueOnce(new TypeError('Failed to fetch'));
    stubBackend(() => page([BURSA]));

    renderPanel();

    expect(await screen.findByRole('alert')).toHaveTextContent(
      strings.errors.byCode['network.unreachable'] as string,
    );

    await userEvent.click(screen.getByRole('button', { name: strings.list.retry }));

    expect(await screen.findByText('Bursa')).toBeInTheDocument();
  });

  it('keeps the previous page on screen while the next one is fetched', async () => {
    // FR-25 in advance: a view that blanks on every refresh reads as a page
    // reload, which is what the stream must never look like.
    let release: (() => void) | undefined;
    const secondPage = new Promise<IncidentPage>((resolve) => {
      release = () => resolve(page([SHARED], { page: 1, totalElements: 2, totalPages: 2 }));
    });
    stubBackend((url) =>
      url.includes('page=1') ? secondPage : page([BURSA], { totalElements: 2, totalPages: 2 }),
    );

    renderPanel();

    await screen.findByText('Bursa');
    await userEvent.click(screen.getByRole('button', { name: strings.list.next }));

    await screen.findByText(new RegExp(strings.list.refreshing));
    expect(screen.getByText('Bursa')).toBeInTheDocument();

    release?.();
    expect(await screen.findByText(strings.incident.sharedProvinces)).toBeInTheDocument();
  });
});
