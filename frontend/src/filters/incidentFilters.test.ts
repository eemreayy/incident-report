import { describe, expect, it } from 'vitest';
import {
  DEFAULT_FILTERS,
  isFiltered,
  PAGE_SIZE,
  parseFilters,
  toApiQuery,
  toSearchParams,
  type IncidentFilters,
} from './incidentFilters';

function parse(query: string) {
  return parseFilters(new URLSearchParams(query));
}

function filters(patch: Partial<IncidentFilters> = {}): IncidentFilters {
  return { ...DEFAULT_FILTERS, ...patch };
}

describe('parseFilters', () => {
  it('reads a filtered view out of the address bar', () => {
    const parsed = parse(
      'eventType=EARTHQUAKE&eventType=EPIDEMIC&province=16&province=41' +
        '&from=2020-05-01&to=2020-05-31&keyword=deprem&sort=date-asc&page=3',
    );

    expect(parsed).toEqual({
      eventTypes: ['EARTHQUAKE', 'EPIDEMIC'],
      provinces: [16, 41],
      from: '2020-05-01',
      to: '2020-05-31',
      keyword: 'deprem',
      sort: 'date-asc',
      page: 3,
    });
  });

  it('reads an empty address bar as the unfiltered view', () => {
    expect(parse('')).toEqual(DEFAULT_FILTERS);
    expect(isFiltered(parse(''))).toBe(false);
  });

  it('keeps event types it has never heard of', () => {
    // NFR-14: the catalog lives on the server. Dropping an unknown key here
    // would mean a type added to the YAML cannot be linked to until the
    // frontend is rebuilt - the exact coupling the rule forbids.
    expect(parse('eventType=FLOOD').eventTypes).toEqual(['FLOOD']);
  });

  it('drops what it cannot read instead of failing', () => {
    // A URL is typed, pasted and truncated by hand; a stray character is not
    // worth an error screen.
    const parsed = parse('province=abc&province=0&from=dün&to=2020-13&sort=magnitude&page=0');

    expect(parsed).toEqual(DEFAULT_FILTERS);
  });

  it('puts multi-valued filters in one order, so one view has one address', () => {
    // The parsed object is also the query cache key: two spellings of the same
    // view must not become two cache entries, and two requests.
    expect(parse('province=41&province=16&province=16').provinces).toEqual([16, 41]);
    expect(parse('eventType=EPIDEMIC&eventType=EARTHQUAKE').eventTypes).toEqual([
      'EARTHQUAKE',
      'EPIDEMIC',
    ]);
  });

  it('treats a blank keyword as no keyword', () => {
    expect(parse('keyword=%20%20').keyword).toBeNull();
    expect(parse('keyword=%20deprem%20').keyword).toBe('deprem');
  });
});

describe('toSearchParams', () => {
  it('survives a round trip, which is what makes a shared link work', () => {
    const original = filters({
      eventTypes: ['EARTHQUAKE'],
      provinces: [16, 41],
      from: '2020-05-01',
      to: '2020-05-31',
      keyword: 'deprem',
      sort: 'date-asc',
      page: 2,
    });

    expect(parseFilters(toSearchParams(original))).toEqual(original);
  });

  it('leaves defaults out, so the unfiltered view has a clean address', () => {
    expect(toSearchParams(DEFAULT_FILTERS).toString()).toBe('');
    expect(toSearchParams(filters({ page: 1, sort: 'date-desc' })).toString()).toBe('');
  });

  it('repeats the multi-valued filters the way the endpoint takes them', () => {
    const params = toSearchParams(filters({ eventTypes: ['EPIDEMIC', 'EARTHQUAKE'] }));

    expect(params.getAll('eventType')).toEqual(['EARTHQUAKE', 'EPIDEMIC']);
  });
});

describe('toApiQuery', () => {
  it('translates the page number the reader sees into the one the server counts', () => {
    expect(toApiQuery(filters({ page: 1 })).page).toBe(0);
    expect(toApiQuery(filters({ page: 3 })).page).toBe(2);
    expect(toApiQuery(filters()).size).toBe(PAGE_SIZE);
  });

  it('asks the server to sort, and to break ties the same way every time', () => {
    // Without the second expression, two records from the same day could swap
    // between pages and one of them would be seen twice while the other is
    // never seen at all.
    expect(toApiQuery(filters({ sort: 'date-desc' })).sort).toEqual([
      'occurredOn,desc',
      'id,desc',
    ]);
    expect(toApiQuery(filters({ sort: 'date-asc' })).sort).toEqual(['occurredOn,asc', 'id,asc']);
  });

  it('passes every filter on rather than keeping any of them here', () => {
    const query = toApiQuery(
      filters({
        eventTypes: ['EPIDEMIC'],
        provinces: [6],
        from: '2020-04-01',
        to: '2020-04-30',
        keyword: 'vaka',
      }),
    );

    expect(query).toMatchObject({
      eventTypes: ['EPIDEMIC'],
      provinces: [6],
      from: '2020-04-01',
      to: '2020-04-30',
      keyword: 'vaka',
    });
  });
});

describe('isFiltered', () => {
  it('does not count paging or sorting as a filter', () => {
    // Both change the view; neither changes which records are in it, so neither
    // may turn "there is nothing yet" into "nothing matched your filters".
    expect(isFiltered(filters({ page: 4, sort: 'date-asc' }))).toBe(false);
  });

  it.each([
    ['event type', filters({ eventTypes: ['EPIDEMIC'] })],
    ['province', filters({ provinces: [6] })],
    ['start date', filters({ from: '2020-01-01' })],
    ['end date', filters({ to: '2020-01-01' })],
    ['keyword', filters({ keyword: 'vaka' })],
  ])('counts a %s filter', (_name, value) => {
    expect(isFiltered(value)).toBe(true);
  });
});
