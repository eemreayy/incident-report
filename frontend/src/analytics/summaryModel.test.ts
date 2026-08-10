import { describe, expect, it } from 'vitest';
import { toBlocks, totalMetricColumns, unattributedRows } from './summaryModel';
import type { Metadata, Summary, SummaryRow } from '../api/types';

const CATALOG: Metadata = {
  eventTypes: [
    {
      key: 'EPIDEMIC',
      label: 'Salgın',
      metrics: [
        { key: 'NEW_CASE', label: 'Yeni vaka' },
        { key: 'DEATH', label: 'Can kaybı' },
        { key: 'RECOVERED', label: 'Taburcu' },
      ],
    },
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

/** Example 3 from PRD §11, exactly as /analytics/summary answers it. */
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

function summary(patch: Partial<Summary>): Summary {
  return { rows: [], eventTypeTotals: [], total: { incidentCount: 0, metrics: {} }, ...patch };
}

function row(patch: Partial<SummaryRow>): SummaryRow {
  return { incidentCount: 1, metrics: {}, ...patch };
}

describe('toBlocks', () => {
  it('keeps the shared figure out of every province and in its own row', () => {
    // ADR-019, the whole reason this table is not a plain group-by: those ten
    // injured people belong to Bursa and Kocaeli together and to neither alone.
    const traffic = toBlocks(EXAMPLE_3, CATALOG)[0]!;

    const bursa = traffic.rows.find((r) => r.province?.name === 'Bursa');
    const kocaeli = traffic.rows.find((r) => r.province?.name === 'Kocaeli');
    const shared = traffic.rows.find((r) => r.provinceScope === 'SHARED');

    expect(bursa?.metrics).toEqual({ ACCIDENT_COUNT: 8, DEATH: 1 });
    expect(kocaeli?.metrics).toEqual({ ACCIDENT_COUNT: 6, DEATH: 2 });
    expect(shared?.metrics).toEqual({ INJURED: 10 });
    expect(bursa?.metrics.INJURED).toBeUndefined();
    expect(kocaeli?.metrics.INJURED).toBeUndefined();
  });

  it('takes the total from the server even when the rows do not add up to it', () => {
    // The decisive test. The rows here deliberately disagree with the total; if
    // this module did the arithmetic it would "correct" the number and, with a
    // shared figure present, the correction would be the wrong answer.
    const inconsistent = summary({
      rows: [row({ eventType: 'EPIDEMIC', provinceScope: 'SINGLE', metrics: { DEATH: 1 } })],
      eventTypeTotals: [row({ eventType: 'EPIDEMIC', incidentCount: 99, metrics: { DEATH: 77 } })],
    });

    const block = toBlocks(inconsistent, CATALOG)[0]!;

    expect(block.total?.metrics.DEATH).toBe(77);
    expect(block.total?.incidentCount).toBe(99);
  });

  it('gives each event type its own block, in catalog order', () => {
    // Metrics belong to event types: one wide table would carry a column for
    // every metric in the catalog and leave most cells empty on every row.
    const mixed = summary({
      rows: [
        row({ eventType: 'TRAFFIC_ACCIDENT', provinceScope: 'SINGLE', metrics: { INJURED: 3 } }),
        row({ eventType: 'EPIDEMIC', provinceScope: 'SINGLE', metrics: { NEW_CASE: 5 } }),
      ],
      eventTypeTotals: [
        row({ eventType: 'EPIDEMIC', metrics: { NEW_CASE: 5 } }),
        row({ eventType: 'TRAFFIC_ACCIDENT', metrics: { INJURED: 3 } }),
      ],
    });

    expect(toBlocks(mixed, CATALOG).map((block) => block.eventType)).toEqual([
      'EPIDEMIC',
      'TRAFFIC_ACCIDENT',
    ]);
  });

  it('columns follow the catalog, not the order the JSON happened to arrive in', () => {
    const traffic = toBlocks(EXAMPLE_3, CATALOG)[0]!;

    expect(traffic.metricKeys).toEqual(['ACCIDENT_COUNT', 'DEATH', 'INJURED']);
  });

  it('leaves out a metric no row carries', () => {
    // A column of nothing but dashes says less than not having the column.
    const onlyDeaths = summary({
      rows: [row({ eventType: 'EPIDEMIC', provinceScope: 'SINGLE', metrics: { DEATH: 2 } })],
      eventTypeTotals: [row({ eventType: 'EPIDEMIC', metrics: { DEATH: 2 } })],
    });

    expect(toBlocks(onlyDeaths, CATALOG)[0]?.metricKeys).toEqual(['DEATH']);
  });

  it('shows an event type and a metric the catalog does not know', () => {
    // OTHER is produced by code and never appears in the catalog (ADR-006), and
    // a server may run YAML this build has not seen. Hiding either would drop
    // records out of a table that claims to total everything.
    const unknown = summary({
      rows: [row({ eventType: 'OTHER', provinceScope: 'UNKNOWN', metrics: { MYSTERY: 1 } })],
      eventTypeTotals: [row({ eventType: 'OTHER', metrics: { MYSTERY: 1 } })],
    });

    const block = toBlocks(unknown, CATALOG)[0]!;

    expect(block.eventType).toBe('OTHER');
    expect(block.metricKeys).toEqual(['MYSTERY']);
  });

  it('survives having no catalog yet', () => {
    // The catalog is a second request; the summary can arrive first.
    expect(toBlocks(EXAMPLE_3, undefined)[0]?.rows).toHaveLength(3);
  });

  it('has nothing to show when nothing matched', () => {
    expect(toBlocks(summary({}), CATALOG)).toEqual([]);
  });
});

describe('totalMetricColumns', () => {
  it('spans event types, catalog order first', () => {
    const across = summary({
      total: { incidentCount: 4, metrics: { INJURED: 10, NEW_CASE: 15, MYSTERY: 1 } },
    });

    expect(totalMetricColumns(across, CATALOG)).toEqual(['NEW_CASE', 'INJURED', 'MYSTERY']);
  });

  it('gives a metric one column however many event types declare it', () => {
    // DEATH is shared across event types on purpose (PRD §7), so laying every
    // type's metrics end to end names it more than once. Seen in the browser
    // before it was seen here: the grand total printed "Can kaybı" three times,
    // with the same figure under each.
    const across = summary({
      total: { incidentCount: 4, metrics: { DEATH: 6, NEW_CASE: 15, INJURED: 10 } },
    });

    expect(totalMetricColumns(across, CATALOG)).toEqual(['NEW_CASE', 'DEATH', 'INJURED']);
  });
});

describe('unattributedRows', () => {
  it('finds the rows that owe the reader an explanation', () => {
    const rows = toBlocks(EXAMPLE_3, CATALOG)[0]?.rows ?? [];

    expect(unattributedRows(rows).map((r) => r.provinceScope)).toEqual(['SHARED']);
  });

  it('finds none when every figure belongs to one province', () => {
    expect(unattributedRows([row({ provinceScope: 'SINGLE' })])).toEqual([]);
  });
});
