import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, useLocation } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ChartPanel } from './ChartPanel';
import { strings } from '../i18n/strings';
import type { TimeSeries } from '../api/types';

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
    {
      key: 'FIRE',
      label: 'Yangın',
      metrics: [{ key: 'INJURED', label: 'Yaralı' }],
    },
  ],
  provinces: [
    { code: 16, name: 'Bursa' },
    { code: 41, name: 'Kocaeli' },
  ],
};

const BY_METRIC: TimeSeries = {
  cumulative: false,
  groupBy: 'NONE',
  series: [
    {
      eventType: 'TRAFFIC_ACCIDENT',
      metric: 'ACCIDENT_COUNT',
      points: [
        { date: '2020-06-01', value: 8 },
        { date: '2020-06-02', value: 6 },
      ],
    },
    {
      eventType: 'TRAFFIC_ACCIDENT',
      metric: 'DEATH',
      points: [{ date: '2020-06-01', value: 1 }],
    },
  ],
};

const BY_PROVINCE: TimeSeries = {
  cumulative: false,
  groupBy: 'PROVINCE',
  series: [
    {
      eventType: 'TRAFFIC_ACCIDENT',
      metric: 'ACCIDENT_COUNT',
      provinceScope: 'SINGLE',
      province: { code: 16, name: 'Bursa' },
      points: [{ date: '2020-06-01', value: 8 }],
    },
    {
      eventType: 'TRAFFIC_ACCIDENT',
      metric: 'INJURED',
      provinceScope: 'SHARED',
      points: [{ date: '2020-06-01', value: 10 }],
    },
  ],
};

const CUMULATIVE: TimeSeries = {
  cumulative: true,
  groupBy: 'NONE',
  series: [
    {
      eventType: 'TRAFFIC_ACCIDENT',
      metric: 'ACCIDENT_COUNT',
      points: [
        { date: '2020-06-01', value: 8 },
        { date: '2020-06-02', value: 14 },
      ],
    },
  ],
};

const EMPTY: TimeSeries = { cumulative: false, groupBy: 'NONE', series: [] };

let fetchSpy: ReturnType<typeof vi.spyOn>;

function CurrentSearch() {
  return <p data-testid="search">{useLocation().search}</p>;
}

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
        <ChartPanel />
        <CurrentSearch />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

function search() {
  return screen.getByTestId('search').textContent ?? '';
}

function seriesRequests(): string[] {
  return fetchSpy.mock.calls
    .map((call: unknown[]) => String(call[0]))
    .filter((url: string) => url.includes('/time-series'));
}

/** The legend is what names the lines on screen, so it is what the tests read. */
function legend(): string[] {
  return [...document.querySelectorAll('.recharts-legend-item-text')].map(
    (item) => item.textContent ?? '',
  );
}

beforeEach(() => {
  fetchSpy = vi.spyOn(globalThis, 'fetch');
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe('ChartPanel', () => {
  it('draws the selected type’s metrics as separate lines', async () => {
    // FR-23, and T-28's first acceptance criterion.
    stubBackend(() => BY_METRIC);

    renderPanel();

    await vi.waitFor(() => expect(legend()).toEqual(['Kaza sayısı', 'Can kaybı']));
    expect(document.querySelectorAll('.recharts-line').length).toBe(2);
  });

  it('offers the event types the catalog publishes', async () => {
    // NFR-14: no chart in this application knows an event type by heart.
    stubBackend(() => BY_METRIC);

    renderPanel();

    const selector = await screen.findByLabelText(strings.chart.eventType);
    expect([...selector.querySelectorAll('option')].map((option) => option.textContent)).toEqual([
      'Trafik kazası',
      'Yangın',
    ]);
  });

  it('asks the server for the running total instead of adding the points up', async () => {
    // FR-12. The values below are the server's cumulative ones; a chart that
    // computed them would be a second definition of "the total so far".
    stubBackend(() => CUMULATIVE);

    renderPanel();

    await screen.findByLabelText(strings.chart.eventType);
    await userEvent.click(screen.getByRole('checkbox', { name: strings.chart.cumulative }));

    await vi.waitFor(() =>
      expect(seriesRequests().some((url) => url.includes('cumulative=true'))).toBe(true),
    );
    expect(search()).toContain('cumulative=true');
  });

  it('says which chart this is, from the answer rather than from the switch', async () => {
    stubBackend(() => CUMULATIVE);

    renderPanel('/?cumulative=true');

    expect(await screen.findByText(strings.chart.cumulativeOn)).toBeInTheDocument();
    expect(screen.queryByText(strings.chart.plain)).not.toBeInTheDocument();
  });

  it('breaks the lines down by province, and keeps the shared figure its own', async () => {
    // FR-24 and ADR-019 at series level: the shared figure is neither folded
    // into a province's line nor dropped.
    stubBackend(() => BY_PROVINCE);

    renderPanel('/?breakdown=province&metric=INJURED');

    await vi.waitFor(() =>
      expect(seriesRequests().some((url) => url.includes('groupBy=province'))).toBe(true),
    );
    await vi.waitFor(() => expect(legend()).toEqual([strings.incident.sharedProvinces]));
  });

  it('compares provinces on one metric at a time', async () => {
    // Every metric for every province at once puts deaths and accident counts on
    // one axis; the breakdown therefore draws one metric, chosen from the
    // catalog, and says so.
    stubBackend(() => BY_PROVINCE);

    renderPanel('/?breakdown=province');

    const metricSelector = await screen.findByLabelText(strings.chart.metric);
    expect([...metricSelector.querySelectorAll('option')].map((o) => o.textContent)).toEqual([
      'Kaza sayısı',
      'Can kaybı',
      'Yaralı',
    ]);
    expect(screen.getByText(strings.chart.breakdownHint)).toBeInTheDocument();
  });

  it('has no metric selector when metrics are what it is drawing', async () => {
    stubBackend(() => BY_METRIC);

    renderPanel();

    await screen.findByLabelText(strings.chart.eventType);
    expect(screen.queryByLabelText(strings.chart.metric)).not.toBeInTheDocument();
  });

  it('keeps its settings in the address bar, so a chart can be sent to somebody', async () => {
    stubBackend(() => BY_PROVINCE);

    renderPanel();

    await screen.findByLabelText(strings.chart.eventType);
    await userEvent.click(screen.getByRole('checkbox', { name: strings.chart.breakdown }));

    expect(search()).toContain('breakdown=province');
  });

  it('never plots a type the filters exclude', async () => {
    // The chart and the table cannot answer different questions - so a chart
    // setting left over from a wider filter is not honoured.
    stubBackend(() => BY_METRIC);

    renderPanel('/?eventType=FIRE&chart=TRAFFIC_ACCIDENT');

    const selector = await screen.findByLabelText(strings.chart.eventType);
    expect(selector).toHaveValue('FIRE');
    expect([...selector.querySelectorAll('option')]).toHaveLength(1);
  });

  it('carries the filters into its own request', async () => {
    stubBackend(() => BY_METRIC);

    renderPanel('/?province=16&from=2020-06-01&to=2020-06-30&keyword=kaza');

    await screen.findByLabelText(strings.chart.eventType);
    const url = seriesRequests()[0] ?? '';
    expect(url).toContain('province=16');
    expect(url).toContain('from=2020-06-01');
    expect(url).toContain('to=2020-06-30');
    expect(url).toContain('keyword=kaza');
    // One type at a time, and always one the filters allow.
    expect(url).toContain('eventType=TRAFFIC_ACCIDENT');
  });

  it('hides a line when its name in the legend is clicked, and brings it back', async () => {
    stubBackend(() => BY_METRIC);

    renderPanel();

    await vi.waitFor(() => expect(document.querySelectorAll('.recharts-line').length).toBe(2));
    await userEvent.click(screen.getByText('Can kaybı'));

    await vi.waitFor(() => expect(document.querySelectorAll('.recharts-line').length).toBe(1));
    // Hidden, not filtered out: the name stays in the legend to be clicked again.
    expect(legend()).toEqual(['Kaza sayısı', 'Can kaybı']);

    await userEvent.click(screen.getByText('Can kaybı'));
    await vi.waitFor(() => expect(document.querySelectorAll('.recharts-line').length).toBe(2));
  });

  it('says there is nothing to draw rather than drawing an empty box', async () => {
    stubBackend(() => EMPTY);

    renderPanel();

    expect(await screen.findByText(strings.chart.empty)).toBeInTheDocument();
  });

  it('says so differently when filters are what left it empty', async () => {
    stubBackend(() => EMPTY);

    renderPanel('/?province=16');

    expect(await screen.findByText(strings.chart.emptyFiltered)).toBeInTheDocument();
  });

  it('states a failure and offers a way forward', async () => {
    // Only the series call fails: with the catalog gone there would be no event
    // type to plot and the panel would be answering a different question.
    let firstAttempt = true;
    stubBackend(() => {
      if (firstAttempt) {
        firstAttempt = false;
        throw new TypeError('Failed to fetch');
      }
      return BY_METRIC;
    });

    renderPanel();

    expect(await screen.findByRole('alert')).toHaveTextContent(
      strings.errors.byCode['network.unreachable'] as string,
    );

    await userEvent.click(screen.getByRole('button', { name: strings.chart.retry }));

    await vi.waitFor(() => expect(legend()).toEqual(['Kaza sayısı', 'Can kaybı']));
  });
});
