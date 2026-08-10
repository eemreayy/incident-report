import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { RawReportPage } from './RawReportPage';
import { IncidentDetailPage } from './IncidentDetailPage';
import { strings } from '../i18n/strings';
import type { Incident, IncidentPage } from '../api/types';

const REPORT_ID = '6a79ce8545b5cc8ef70f390a';

const CATALOG = {
  eventTypes: [
    {
      key: 'TRAFFIC_ACCIDENT',
      label: 'Trafik kazası',
      metrics: [
        { key: 'ACCIDENT_COUNT', label: 'Kaza sayısı' },
        { key: 'DEATH', label: 'Can kaybı' },
      ],
    },
  ],
  provinces: [{ code: 16, name: 'Bursa' }],
};

/** The third sample text and its stored keywords, captured from the system. */
const TEXT =
  "Son 24 saatte Bursa'da 8, Kocaeli'nde 6 trafik kazası meydana geldi. Bursa'da 1, " +
  "Kocaeli'nde ise 2 kişi kazalarda hayatını kaybetti. Her iki ilde toplam 10 kişi " +
  'yaralı olarak hastaneye kaldırıldı.';

const RAW_REPORT = { id: REPORT_ID, text: TEXT, submittedAt: '2026-08-10T13:12:00Z' };

function incident(id: number, patch: Partial<Incident> = {}): Incident {
  return {
    id,
    rawReportId: REPORT_ID,
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
    ...patch,
  };
}

function page(content: Incident[]): IncidentPage {
  return {
    content,
    page: 0,
    size: 20,
    totalElements: content.length,
    totalPages: 1,
    analysis: {
      status: 'ANALYZED',
      analyzedAt: '2026-08-10T13:12:01Z',
      incidentCount: content.length,
      warnings: [],
    },
  };
}

let fetchSpy: ReturnType<typeof vi.spyOn>;

function stubBackend(answer: (url: string, init?: RequestInit) => unknown) {
  fetchSpy.mockImplementation((input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input);
    const body = url.includes('/metadata') ? CATALOG : answer(url, init);
    return Promise.resolve({ ok: true, status: 200, json: async () => body } as Response);
  });
}

function renderPage() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[`/reports/${REPORT_ID}`]}>
        <Routes>
          <Route path="/reports/:id" element={<RawReportPage />} />
          <Route path="/incidents/:id" element={<IncidentDetailPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

function requests(): Array<{ url: string; method: string }> {
  return fetchSpy.mock.calls.map((call: unknown[]) => ({
    url: String(call[0]),
    method: (call[1] as RequestInit | undefined)?.method ?? 'GET',
  }));
}

beforeEach(() => {
  fetchSpy = vi.spyOn(globalThis, 'fetch');
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe('RawReportPage', () => {
  it('shows the stored text unchanged', async () => {
    // FR-02: what is on screen is what was submitted, character for character.
    stubBackend((url) => (url.includes('/incidents') ? page([incident(1)]) : RAW_REPORT));

    renderPage();

    await screen.findByText(strings.detail.derivedCount(1));
    // Exactly the stored text: no marker, no label, nothing injected between
    // the characters. Text selected from this screen is the text that was
    // submitted, highlights or not.
    expect(document.querySelector('.raw-text')?.textContent).toBe(TEXT);
  });

  it('marks the words that produced the records, and says what each triggered', async () => {
    // FR-26, and the reason the offsets exist: the highlight lands on what was
    // matched - suffix included - rather than on a word searched for again.
    stubBackend((url) => (url.includes('/incidents') ? page([incident(1)]) : RAW_REPORT));

    renderPage();

    const province = await screen.findByText("Bursa'da");
    expect(province.tagName).toBe('MARK');
    expect(province).toHaveAttribute('data-role', 'PROVINCE');
    expect(province).toHaveAttribute('title', strings.detail.keywordRole.PROVINCE);
  });

  it('highlights the keywords of every record derived from the text', async () => {
    // One text routinely produces several records and each carries its own
    // matches over the same text; a screen showing only the first record's
    // keywords would leave the rest of the sentence unmarked.
    stubBackend((url) =>
      url.includes('/incidents')
        ? page([
            incident(1, { keywords: [{ keyword: "Bursa'da", role: 'PROVINCE', charStart: 14, charEnd: 22 }] }),
            incident(2, {
              province: { code: 41, name: 'Kocaeli' },
              keywords: [
                { keyword: "Kocaeli'nde", role: 'PROVINCE', charStart: 26, charEnd: 37 },
              ],
            }),
          ])
        : RAW_REPORT,
    );

    renderPage();

    expect(await screen.findByText("Bursa'da")).toBeInTheDocument();
    expect(screen.getByText("Kocaeli'nde")).toBeInTheDocument();
  });

  it('reads the text and what came of it from two different endpoints', async () => {
    // FR-14's note: the raw report endpoint returns the text and nothing else,
    // because the outcome and the records belong to the analysis side.
    stubBackend((url) => (url.includes('/incidents') ? page([incident(1)]) : RAW_REPORT));

    renderPage();

    await screen.findByText(strings.detail.derivedCount(1));
    const urls = requests().map((request) => request.url);
    expect(urls).toContain(`/api/v1/incident-reports/${REPORT_ID}`);
    expect(urls).toContain(`/api/v1/incidents?rawReportId=${REPORT_ID}`);
  });

  it('leads to each record it produced', async () => {
    // FR-08, the direction this screen owns.
    stubBackend((url) =>
      url.includes('/incidents/7')
        ? incident(7)
        : url.includes('/incidents')
          ? page([incident(7)])
          : RAW_REPORT,
    );

    renderPage();

    await userEvent.click(await screen.findByRole('link', { name: strings.detail.openIncident }));

    expect(await screen.findByText(strings.detail.incidentHeading(7))).toBeInTheDocument();
  });

  it('says so when the text produced nothing', async () => {
    stubBackend((url) => (url.includes('/incidents') ? page([]) : RAW_REPORT));

    renderPage();

    expect(await screen.findByText(strings.detail.derivedEmpty)).toBeInTheDocument();
  });

  it('reprocesses on demand and shows the new result in place', async () => {
    // FR-15: the rules run again, the text is untouched, and the previous
    // records are replaced - so the screen must end up showing one record, not
    // the old one plus the new one.
    let reprocessed = false;
    stubBackend((url, init) => {
      if (init?.method === 'POST') {
        reprocessed = true;
        return { id: REPORT_ID, submittedAt: RAW_REPORT.submittedAt };
      }
      if (url.includes('/incidents')) {
        return page(reprocessed ? [incident(9)] : [incident(1)]);
      }
      return RAW_REPORT;
    });

    renderPage();
    await screen.findByText(strings.detail.derivedCount(1));

    await userEvent.click(screen.getByRole('button', { name: strings.detail.reprocess }));

    expect(await screen.findByText(strings.detail.reprocessDone)).toBeInTheDocument();
    const reprocessCall = requests().find((request) => request.method === 'POST');
    expect(reprocessCall?.url).toBe(`/api/v1/incident-reports/${REPORT_ID}/reprocess`);
    // One record, and it is the new one: nothing doubled.
    await screen.findByText(strings.detail.derivedCount(1));
    expect(await screen.findByRole('link', { name: strings.detail.openIncident })).toHaveAttribute(
      'href',
      '/incidents/9',
    );
  });

  it('does not ask for the text again after reprocessing', async () => {
    // ADR-005: the raw record is write-once, so re-reading it would be asking a
    // question whose answer cannot have changed.
    stubBackend((url, init) =>
      init?.method === 'POST'
        ? { id: REPORT_ID, submittedAt: RAW_REPORT.submittedAt }
        : url.includes('/incidents')
          ? page([incident(1)])
          : RAW_REPORT,
    );

    renderPage();
    await screen.findByText(strings.detail.derivedCount(1));

    await userEvent.click(screen.getByRole('button', { name: strings.detail.reprocess }));
    await screen.findByText(strings.detail.reprocessDone);

    const textReads = requests().filter(
      (request) => request.url === `/api/v1/incident-reports/${REPORT_ID}`,
    );
    expect(textReads).toHaveLength(1);
  });

  it('states a failed reprocess instead of looking like it worked', async () => {
    stubBackend((url, init) => {
      if (init?.method === 'POST') {
        throw new TypeError('Failed to fetch');
      }
      return url.includes('/incidents') ? page([incident(1)]) : RAW_REPORT;
    });

    renderPage();
    await screen.findByText(strings.detail.derivedCount(1));

    await userEvent.click(screen.getByRole('button', { name: strings.detail.reprocess }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      strings.errors.byCode['network.unreachable'] as string,
    );
    expect(screen.queryByText(strings.detail.reprocessDone)).not.toBeInTheDocument();
  });

  it('does not report a failed lookup as "this text produced nothing"', async () => {
    // FR-28: the difference between an answer and a question that was never
    // answered. One of them is fixed by trying again.
    stubBackend((url) => {
      if (url.includes('/incidents')) {
        throw new TypeError('Failed to fetch');
      }
      return RAW_REPORT;
    });

    renderPage();

    expect(await screen.findByRole('alert')).toHaveTextContent(
      strings.errors.byCode['network.unreachable'] as string,
    );
    expect(screen.queryByText(strings.detail.derivedEmpty)).not.toBeInTheDocument();
  });

  it('states a failure and offers a way forward', async () => {
    fetchSpy.mockImplementation((input: RequestInfo | URL) =>
      String(input).includes('/metadata')
        ? Promise.resolve({ ok: true, status: 200, json: async () => CATALOG } as Response)
        : Promise.reject(new TypeError('Failed to fetch')),
    );

    renderPage();

    expect(await screen.findByRole('alert')).toHaveTextContent(
      strings.errors.byCode['network.unreachable'] as string,
    );
    expect(screen.getByRole('button', { name: strings.detail.retry })).toBeInTheDocument();
  });
});
