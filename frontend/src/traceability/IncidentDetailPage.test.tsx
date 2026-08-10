import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { IncidentDetailPage } from './IncidentDetailPage';
import { strings } from '../i18n/strings';
import type { Incident } from '../api/types';

const CATALOG = {
  eventTypes: [
    {
      key: 'TRAFFIC_ACCIDENT',
      label: 'Trafik kazası',
      metrics: [
        { key: 'ACCIDENT_COUNT', label: 'Kaza sayısı' },
        { key: 'INJURED', label: 'Yaralı' },
      ],
    },
  ],
  provinces: [
    { code: 16, name: 'Bursa' },
    { code: 41, name: 'Kocaeli' },
  ],
};

const INCIDENT: Incident = {
  id: 26,
  rawReportId: '6a79ce8545b5cc8ef70f390a',
  occurredOn: '2026-08-10',
  dateSource: 'RELATIVE',
  eventType: 'TRAFFIC_ACCIDENT',
  classification: 'CLASSIFIED',
  provinceScope: 'SINGLE',
  province: { code: 16, name: 'Bursa' },
  sharedAcross: [],
  metrics: [{ metricType: 'ACCIDENT_COUNT', value: 8 }],
  keywords: [
    { keyword: 'Son 24 saatte', role: 'DATE', charStart: 0, charEnd: 13 },
    { keyword: "Bursa'da", role: 'PROVINCE', charStart: 14, charEnd: 22 },
    { keyword: 'trafik kazası', role: 'EVENT_TYPE', charStart: 40, charEnd: 53 },
  ],
};

let fetchSpy: ReturnType<typeof vi.spyOn>;

function stubBackend(answer: (url: string) => unknown) {
  fetchSpy.mockImplementation((input: RequestInfo | URL) => {
    const url = String(input);
    const body = url.includes('/metadata') ? CATALOG : answer(url);
    return Promise.resolve({ ok: true, status: 200, json: async () => body } as Response);
  });
}

function renderPage(id: string | number = INCIDENT.id) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[`/incidents/${id}`]}>
        <Routes>
          <Route path="/incidents/:id" element={<IncidentDetailPage />} />
          <Route path="/reports/:id" element={<p>ham metin ekranı</p>} />
          <Route path="/" element={<p>panel</p>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  fetchSpy = vi.spyOn(globalThis, 'fetch');
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe('IncidentDetailPage', () => {
  it('states the decisions the system made, not only the results', async () => {
    // FR-26: where the date came from, how the figures relate to provinces, and
    // which words triggered which extraction - the things a reader needs to
    // judge the record rather than take it on trust.
    stubBackend(() => INCIDENT);

    renderPage();

    expect(await screen.findByText(strings.detail.incidentHeading(26))).toBeInTheDocument();
    expect(screen.getByText('Trafik kazası')).toBeInTheDocument();
    expect(
      screen.getByText(new RegExp(strings.incident.dateSource.RELATIVE)),
    ).toBeInTheDocument();
    expect(screen.getByText('Bursa')).toBeInTheDocument();
    expect(screen.getByText(/Kaza sayısı/)).toBeInTheDocument();
  });

  it('says which word triggered which extraction', async () => {
    // FR-17: the words alone would not be traceability - what each one produced
    // is the point.
    stubBackend(() => INCIDENT);

    renderPage();

    const keyword = await screen.findByText("Bursa'da");
    expect(keyword).toHaveAttribute('data-role', 'PROVINCE');
    expect(screen.getByText('trafik kazası')).toHaveAttribute('data-role', 'EVENT_TYPE');
    expect(screen.getAllByText(strings.detail.keywordRole.PROVINCE)).not.toHaveLength(0);
  });

  it('leads to the text it came from', async () => {
    // FR-08, the direction this screen owns.
    stubBackend(() => INCIDENT);

    renderPage();

    await userEvent.click(
      await screen.findByRole('link', { name: strings.detail.openSourceReport }),
    );

    expect(await screen.findByText('ham metin ekranı')).toBeInTheDocument();
  });

  it('says a shared figure belongs to no single province', async () => {
    // ADR-019 again, on the one screen where a reader looks closely at a single
    // record - the wording must not let it be read as a province's own figure.
    stubBackend(() => ({
      ...INCIDENT,
      provinceScope: 'SHARED',
      province: undefined,
      sharedAcross: [
        { code: 16, name: 'Bursa' },
        { code: 41, name: 'Kocaeli' },
      ],
      metrics: [{ metricType: 'INJURED', value: 10 }],
    }));

    renderPage();

    expect(
      await screen.findByText(new RegExp(strings.incident.sharedProvinces)),
    ).toBeInTheDocument();
    expect(screen.getByText(/Bursa, Kocaeli/)).toBeInTheDocument();
  });

  it('marks a record the catalog did not recognise', async () => {
    stubBackend(() => ({ ...INCIDENT, eventType: 'OTHER', classification: 'UNCLASSIFIED' }));

    renderPage();

    expect(await screen.findByText(strings.incident.unclassified)).toBeInTheDocument();
    // ADR-006/ADR-007: OTHER is produced by code and never appears in the
    // catalog, so it is labelled from the typed contract rather than invented.
    expect(screen.getByText(strings.incident.otherEventType)).toBeInTheDocument();
  });

  it('says a record was not found instead of showing an empty screen', async () => {
    fetchSpy.mockImplementation((input: RequestInfo | URL) =>
      String(input).includes('/metadata')
        ? Promise.resolve({ ok: true, status: 200, json: async () => CATALOG } as Response)
        : Promise.resolve({
            ok: false,
            status: 404,
            headers: new Headers({ 'Content-Type': 'application/problem+json' }),
            json: async () => ({ status: 404, code: 'resource.not-found' }),
          } as Response),
    );

    renderPage(9999);

    expect(await screen.findByRole('alert')).toHaveTextContent(
      strings.errors.byCode['resource.not-found'] as string,
    );
  });

  it('does not ask the server about an address that cannot be a record', async () => {
    stubBackend(() => INCIDENT);

    renderPage('abc');

    expect(await screen.findByText(strings.detail.incidentMissing)).toBeInTheDocument();
    expect(
      fetchSpy.mock.calls.filter((call: unknown[]) => String(call[0]).includes('/incidents/')),
    ).toHaveLength(0);
  });
});
