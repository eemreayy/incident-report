import { describe, expect, it } from 'vitest';
import { isRelevant, parseSignal, type IncidentSignal } from './incidentSignal';
import { DEFAULT_FILTERS, type IncidentFilters } from '../filters/incidentFilters';

/** Captured from the running stream, not written from the documentation. */
const RAW =
  '{"rawReportId":"6a79e0a3d79a62c3ff0b8e0c","analyzedAt":"2026-08-10T14:30:59.864904080Z",' +
  '"incidents":[{"incidentId":30,"occurredOn":"2021-03-12","eventType":"FLOOD","provinceCodes":[6]}]}';

function filters(patch: Partial<IncidentFilters> = {}): IncidentFilters {
  return { ...DEFAULT_FILTERS, ...patch };
}

function signal(patch: Partial<IncidentSignal> = {}): IncidentSignal {
  return {
    rawReportId: 'report-1',
    analyzedAt: '2026-08-10T14:30:59Z',
    incidents: [
      { incidentId: 1, occurredOn: '2020-06-01', eventType: 'EARTHQUAKE', provinceCodes: [16] },
    ],
    ...patch,
  };
}

describe('parseSignal', () => {
  it('reads what the stream actually sends', () => {
    const parsed = parseSignal(RAW);

    expect(parsed?.rawReportId).toBe('6a79e0a3d79a62c3ff0b8e0c');
    expect(parsed?.incidents[0]).toEqual({
      incidentId: 30,
      occurredOn: '2021-03-12',
      eventType: 'FLOOD',
      provinceCodes: [6],
    });
  });

  it('returns nothing it cannot read, rather than a half-built signal', () => {
    expect(parseSignal('not json')).toBeNull();
    expect(parseSignal('{"rawReportId":"x"}')).toBeNull();
  });
});

describe('isRelevant', () => {
  it('refreshes an unfiltered view for anything', () => {
    expect(isRelevant(signal(), filters(), [])).toBe(true);
  });

  it('refreshes when a record of the signal passes the filters', () => {
    expect(isRelevant(signal(), filters({ eventTypes: ['EARTHQUAKE'] }), [])).toBe(true);
    expect(isRelevant(signal(), filters({ provinces: [16] }), [])).toBe(true);
    expect(isRelevant(signal(), filters({ from: '2020-01-01', to: '2020-12-31' }), [])).toBe(true);
  });

  it('skips a signal that proves it cannot matter here', () => {
    // The one thing skipping is for: an earthquake in Bursa changes nothing on a
    // screen showing epidemics in Ankara, and refetching would produce an
    // identical answer.
    expect(isRelevant(signal(), filters({ eventTypes: ['EPIDEMIC'] }), [])).toBe(false);
    expect(isRelevant(signal(), filters({ provinces: [6] }), [])).toBe(false);
    expect(isRelevant(signal(), filters({ from: '2021-01-01' }), [])).toBe(false);
    expect(isRelevant(signal(), filters({ to: '2019-12-31' }), [])).toBe(false);
  });

  it('needs only one record of a signal to match', () => {
    // One submission routinely produces several records (the third sample text
    // produces three), and one of them being relevant makes the whole report so.
    const mixed = signal({
      incidents: [
        { incidentId: 1, occurredOn: '2020-06-01', eventType: 'FIRE', provinceCodes: [6] },
        { incidentId: 2, occurredOn: '2020-06-01', eventType: 'EARTHQUAKE', provinceCodes: [16] },
      ],
    });

    expect(isRelevant(mixed, filters({ provinces: [16] }), [])).toBe(true);
  });

  it('does not match a province filter with a record that named no province', () => {
    // No codes means the text named none, and a province-filtered view does not
    // contain such a record - so there is nothing for it to change.
    const noProvince = signal({
      incidents: [
        { incidentId: 1, occurredOn: '2020-06-01', eventType: 'EARTHQUAKE', provinceCodes: [] },
      ],
    });

    expect(isRelevant(noProvince, filters({ provinces: [16] }), [])).toBe(false);
    expect(isRelevant(noProvince, filters(), [])).toBe(true);
  });

  it('matches a province filter through a shared figure', () => {
    // A figure given across Bursa and Kocaeli answers to both filters (ADR-019),
    // which is why the signal carries a list of codes rather than a scope name.
    const shared = signal({
      incidents: [
        { incidentId: 1, occurredOn: '2020-06-01', eventType: 'EARTHQUAKE', provinceCodes: [16, 41] },
      ],
    });

    expect(isRelevant(shared, filters({ provinces: [41] }), [])).toBe(true);
  });

  it('refreshes when the view already shows records from this report', () => {
    // The hole that judging by contents alone would leave: reprocess deletes a
    // report's records and writes new ones, so the records that disappeared are
    // exactly the ones the new signal no longer mentions. Without this, a row
    // that no longer exists would stay on screen with nothing to correct it.
    const elsewhere = signal({ rawReportId: 'report-9' });

    expect(isRelevant(elsewhere, filters({ eventTypes: ['EPIDEMIC'] }), ['report-9'])).toBe(true);
  });

  it('refreshes whenever a keyword filter is on', () => {
    // The signal carries no keywords, so it cannot prove irrelevance. Guessing
    // would be answering a question the stream was never given the data for.
    expect(isRelevant(signal(), filters({ keyword: 'deprem', eventTypes: ['EPIDEMIC'] }), [])).toBe(
      true,
    );
  });

  it('refreshes on a signal it could not read', () => {
    // Something happened; what it was is unknown. The safe direction is one
    // needless request, not a view that has silently stopped being true.
    expect(isRelevant(null, filters({ eventTypes: ['EPIDEMIC'] }), [])).toBe(true);
  });
});
