import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  getIncidentReport,
  getMetadata,
  listIncidentReports,
  listIncidents,
  listIncidentsByRawReport,
  submitIncidentReport,
} from './endpoints';

/**
 * The fixtures below were captured from the running system, not written from
 * the documentation - the same rule the Postman collection follows.
 */
const METADATA_FIXTURE = {
  eventTypes: [
    {
      key: 'EPIDEMIC',
      label: 'Salgın',
      metrics: [
        { key: 'NEW_CASE', label: 'Yeni vaka' },
        { key: 'DEATH', label: 'Can kaybı' },
      ],
    },
  ],
  provinces: [{ code: 1, name: 'Adana' }],
};

let fetchSpy: ReturnType<typeof vi.spyOn>;

function respondWith(body: unknown) {
  fetchSpy.mockResolvedValue({ ok: true, status: 200, json: async () => body } as Response);
}

function calledUrl(): string {
  return String(fetchSpy.mock.calls[0]?.[0]);
}

beforeEach(() => {
  fetchSpy = vi.spyOn(globalThis, 'fetch');
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe('getMetadata', () => {
  it('reads the catalog the interface is fed from', async () => {
    respondWith(METADATA_FIXTURE);

    const metadata = await getMetadata();

    expect(calledUrl()).toBe('/api/v1/metadata');
    expect(metadata.eventTypes[0]?.label).toBe('Salgın');
    expect(metadata.eventTypes[0]?.metrics).toHaveLength(2);
    expect(metadata.provinces[0]).toEqual({ code: 1, name: 'Adana' });
  });
});

describe('submitIncidentReport', () => {
  it('sends the text and receives a receipt, not a result', async () => {
    respondWith({ id: '6a78b031f6fe3fa987f9ffc9', submittedAt: '2026-08-09T16:52:01.177Z' });

    const receipt = await submitIncidentReport('Ankara’da 15 yeni vaka');

    const [url, init] = fetchSpy.mock.calls[0] ?? [];
    expect(url).toBe('/api/v1/incident-reports');
    expect((init as RequestInit).method).toBe('POST');
    expect((init as RequestInit).body).toBe(JSON.stringify({ text: 'Ankara’da 15 yeni vaka' }));

    // ADR-021: the response is the raw record's receipt. If a status or a
    // warnings field ever shows up here, the ownership rule has been broken.
    expect(Object.keys(receipt)).toEqual(['id', 'submittedAt']);
  });
});

describe('getIncidentReport', () => {
  it('escapes the id rather than pasting it into the path', async () => {
    respondWith({ id: 'a b/c', text: 'metin', submittedAt: '2026-08-09T16:52:01.177Z' });

    await getIncidentReport('a b/c');

    expect(calledUrl()).toBe('/api/v1/incident-reports/a%20b%2Fc');
  });
});

describe('listIncidentReports', () => {
  it('unwraps the pagination envelope, total count included', async () => {
    respondWith({
      content: [{ id: '1', text: 'metin', submittedAt: '2026-08-09T16:52:01.177Z' }],
      page: 0,
      size: 2,
      totalElements: 7,
      totalPages: 4,
    });

    const page = await listIncidentReports({ page: 0, size: 2 });

    expect(calledUrl()).toBe('/api/v1/incident-reports?page=0&size=2');
    // C-7: without a total, "no results at all" and "an empty page" look alike.
    expect(page.totalElements).toBe(7);
    expect(page.content).toHaveLength(1);
  });

  it('asks for no paging parameters when none were given', async () => {
    respondWith({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });

    await listIncidentReports();

    expect(calledUrl()).toBe('/api/v1/incident-reports');
  });
});

describe('listIncidents', () => {
  const EMPTY_PAGE = { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 };

  it('repeats the multi-valued filters, the way the endpoint reads them', async () => {
    respondWith(EMPTY_PAGE);

    await listIncidents({ eventTypes: ['EARTHQUAKE', 'EPIDEMIC'], provinces: [16, 41] });

    const url = new URL(calledUrl(), 'http://test');
    expect(url.searchParams.getAll('eventType')).toEqual(['EARTHQUAKE', 'EPIDEMIC']);
    // ADR-019: two provinces at once is the case a shared figure must come back
    // from exactly once, which is the server's job - and it cannot do it if the
    // second code never leaves here.
    expect(url.searchParams.getAll('province')).toEqual(['16', '41']);
  });

  it('carries filters, paging and sorting in one request', async () => {
    respondWith(EMPTY_PAGE);

    await listIncidents({
      from: '2020-05-01',
      to: '2020-05-31',
      keyword: 'deprem',
      page: 2,
      size: 20,
      sort: ['occurredOn,desc', 'id,desc'],
    });

    const url = new URL(calledUrl(), 'http://test');
    expect(url.pathname).toBe('/api/v1/incidents');
    expect(url.searchParams.get('from')).toBe('2020-05-01');
    expect(url.searchParams.get('to')).toBe('2020-05-31');
    expect(url.searchParams.get('keyword')).toBe('deprem');
    expect(url.searchParams.get('page')).toBe('2');
    expect(url.searchParams.getAll('sort')).toEqual(['occurredOn,desc', 'id,desc']);
  });

  it('leaves out what was not asked for', async () => {
    respondWith(EMPTY_PAGE);

    await listIncidents();

    // An empty `keyword=` is a filter for the empty string as far as the server
    // is concerned, not the absence of one.
    expect(calledUrl()).toBe('/api/v1/incidents');
  });

  it('asks about one report with the filter that answers it', async () => {
    respondWith({ ...EMPTY_PAGE, analysis: null });

    await listIncidentsByRawReport('6a78b031f6fe3fa987f9ffc9');

    // C-5: with submission answering only with an id (ADR-021), this filter is
    // the only way to find out what a report produced.
    expect(calledUrl()).toBe('/api/v1/incidents?rawReportId=6a78b031f6fe3fa987f9ffc9');
  });
});
