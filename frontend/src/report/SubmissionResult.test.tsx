import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { SubmissionResult } from './SubmissionResult';
import { strings } from '../i18n/strings';
import type { AnalysisOutcome, Incident } from '../api/types';

/** Captured from the running system, then trimmed to what the assertion needs. */
const CATALOG = {
  eventTypes: [
    {
      key: 'EPIDEMIC',
      label: 'Salgın',
      metrics: [
        { key: 'NEW_CASE', label: 'Yeni vaka' },
        { key: 'DEATH', label: 'Can kaybı' },
      ],
    },
    {
      key: 'TRAFFIC_ACCIDENT',
      label: 'Trafik kazası',
      metrics: [{ key: 'INJURED', label: 'Yaralı' }],
    },
  ],
  provinces: [{ code: 6, name: 'Ankara' }],
};

const ANALYZED: AnalysisOutcome = {
  status: 'ANALYZED',
  analyzedAt: '2026-08-10T06:56:28.958806Z',
  incidentCount: 1,
  warnings: [],
};

const ANKARA_EPIDEMIC: Incident = {
  id: 42,
  rawReportId: 'raw-1',
  occurredOn: '2020-04-20',
  dateSource: 'EXPLICIT',
  eventType: 'EPIDEMIC',
  classification: 'CLASSIFIED',
  provinceScope: 'SINGLE',
  province: { code: 6, name: 'Ankara' },
  sharedAcross: [],
  metrics: [
    { metricType: 'NEW_CASE', value: 15 },
    { metricType: 'DEATH', value: 1 },
  ],
  keywords: [],
};

let fetchSpy: ReturnType<typeof vi.spyOn>;

/** Answers by URL, so the catalog and the records cannot be confused. */
function stub(incidentPage: unknown) {
  fetchSpy.mockImplementation((input: RequestInfo | URL) => {
    const url = String(input);
    const body = url.includes('/metadata') ? CATALOG : incidentPage;
    return Promise.resolve({ ok: true, status: 200, json: async () => body } as Response);
  });
}

function renderResult() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={client}>
      <SubmissionResult rawReportId="raw-1" />
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  fetchSpy = vi.spyOn(globalThis, 'fetch');
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe('SubmissionResult', () => {
  it('asks for the records of this report only', async () => {
    stub({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0, analysis: ANALYZED });

    renderResult();

    await screen.findByText(strings.result.none);
    const urls = fetchSpy.mock.calls.map((call: unknown[]) => String(call[0]));
    expect(urls).toContain('/api/v1/incidents?rawReportId=raw-1');
  });

  it('shows what was extracted, labelled from the catalog', async () => {
    stub({
      content: [ANKARA_EPIDEMIC],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
      analysis: ANALYZED,
    });

    renderResult();

    // Labels come from /metadata (NFR-14) - the record itself carries only keys.
    expect(await screen.findByText('Salgın')).toBeInTheDocument();
    expect(screen.getByText(/Yeni vaka/)).toBeInTheDocument();
    expect(screen.getByText('15')).toBeInTheDocument();
    expect(screen.getByText('Ankara')).toBeInTheDocument();
    // FR-06: an explicit date is distinguishable from an assumed one.
    expect(screen.getByText(/2020-04-20/)).toHaveTextContent(strings.incident.dateSource.EXPLICIT);
  });

  it('says a text produced nothing rather than showing an empty box', async () => {
    stub({
      content: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
      analysis: { ...ANALYZED, incidentCount: 0 },
    });

    renderResult();

    expect(await screen.findByText(strings.result.none)).toBeInTheDocument();
  });

  it('labels an unrecognised event type without treating it as an error', async () => {
    stub({
      content: [
        {
          ...ANKARA_EPIDEMIC,
          eventType: 'OTHER',
          classification: 'UNCLASSIFIED',
          dateSource: 'DEFAULTED',
          provinceScope: 'UNKNOWN',
          province: undefined,
          metrics: [],
        },
      ],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
      analysis: ANALYZED,
    });

    renderResult();

    // FR-09: the report was not rejected. It is labelled, explained, and kept.
    expect(await screen.findByText(strings.incident.unclassified)).toBeInTheDocument();
    expect(screen.getByText(strings.incident.unclassifiedNote)).toBeInTheDocument();
    expect(screen.getByText(new RegExp(strings.incident.dateSource.DEFAULTED))).toBeInTheDocument();
    expect(screen.getByText(strings.incident.unknownProvince)).toBeInTheDocument();
    expect(screen.getByText(strings.incident.noMetrics)).toBeInTheDocument();
  });

  it('presents a shared total as belonging to no single province', async () => {
    stub({
      content: [
        {
          ...ANKARA_EPIDEMIC,
          id: 43,
          eventType: 'TRAFFIC_ACCIDENT',
          dateSource: 'RELATIVE',
          provinceScope: 'SHARED',
          province: undefined,
          sharedAcross: [
            { code: 16, name: 'Bursa' },
            { code: 41, name: 'Kocaeli' },
          ],
          metrics: [{ metricType: 'INJURED', value: 10 }],
        },
      ],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
      analysis: ANALYZED,
    });

    renderResult();

    // ADR-019: the wording must not let a reader attribute the 10 to either
    // province, and both must be named.
    const line = await screen.findByText(new RegExp(strings.incident.sharedProvinces));
    expect(line).toHaveTextContent('Bursa, Kocaeli');
    expect(screen.getByText(/Yaralı/)).toBeInTheDocument();
    expect(screen.getByText('10')).toBeInTheDocument();
  });

  it('reports a failed analysis instead of an empty result', async () => {
    stub({
      content: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
      analysis: {
        status: 'FAILED',
        analyzedAt: '2026-08-10T06:56:28Z',
        incidentCount: 0,
        warnings: [],
      },
    });

    renderResult();

    // Rule 4: the raw text survived, and the user is told so.
    expect(await screen.findByRole('alert')).toHaveTextContent(strings.result.failed);
  });

  it('never prints the server’s English warnings', async () => {
    const english =
      'No known event type matched this text. It was stored as OTHER and can be reprocessed once the catalog recognises it.';
    stub({
      content: [{ ...ANKARA_EPIDEMIC, classification: 'UNCLASSIFIED' }],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
      analysis: { ...ANALYZED, warnings: [english] },
    });

    renderResult();

    await screen.findByText(strings.incident.unclassified);
    expect(screen.queryByText(english)).not.toBeInTheDocument();
  });

  it('offers a way forward when the records cannot be fetched', async () => {
    fetchSpy.mockRejectedValue(new TypeError('Failed to fetch'));

    renderResult();

    expect(await screen.findByRole('alert')).toHaveTextContent(
      strings.errors.byCode['network.unreachable'] as string,
    );
    expect(screen.getByRole('button', { name: strings.result.retry })).toBeInTheDocument();
  });
});
