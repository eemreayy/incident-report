import { describe, expect, it } from 'vitest';
import { lineKey, lineLabel, metricsOf, toChartData } from './chartModel';
import { strings } from '../i18n/strings';
import type { Metadata, TimeSeries, TimeSeriesSeries } from '../api/types';

const CATALOG: Metadata = {
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

function series(patch: Partial<TimeSeriesSeries> = {}): TimeSeriesSeries {
  return {
    eventType: 'TRAFFIC_ACCIDENT',
    metric: 'ACCIDENT_COUNT',
    points: [{ date: '2020-06-01', value: 8 }],
    ...patch,
  };
}

function response(patch: Partial<TimeSeries> = {}): TimeSeries {
  return { cumulative: false, groupBy: 'NONE', series: [], ...patch };
}

describe('toChartData', () => {
  it('draws one line per metric when province is not a dimension', () => {
    const data = toChartData(
      response({
        series: [
          series({ metric: 'ACCIDENT_COUNT' }),
          series({ metric: 'DEATH', points: [{ date: '2020-06-01', value: 3 }] }),
        ],
      }),
      CATALOG,
      null,
    );

    expect(data.lines.map((line) => line.label)).toEqual(['Kaza sayısı', 'Can kaybı']);
  });

  it('draws one line per place, for one metric, when it is', () => {
    // The readability rule: comparing provinces means comparing the same number
    // across them. Every metric for every province on one axis would put deaths
    // and accident counts on the same scale and answer nothing.
    const data = toChartData(
      response({
        groupBy: 'PROVINCE',
        series: [
          series({ metric: 'ACCIDENT_COUNT', provinceScope: 'SINGLE', province: { code: 16, name: 'Bursa' } }),
          series({ metric: 'ACCIDENT_COUNT', provinceScope: 'SINGLE', province: { code: 41, name: 'Kocaeli' } }),
          series({ metric: 'DEATH', provinceScope: 'SINGLE', province: { code: 16, name: 'Bursa' } }),
        ],
      }),
      CATALOG,
      'ACCIDENT_COUNT',
    );

    expect(data.lines.map((line) => line.label)).toEqual(['Bursa', 'Kocaeli']);
  });

  it('keeps the shared figure as its own line, named as what it is', () => {
    // ADR-019 at series level: not folded into a province's line, not dropped.
    const data = toChartData(
      response({
        groupBy: 'PROVINCE',
        series: [
          series({ metric: 'INJURED', provinceScope: 'SINGLE', province: { code: 16, name: 'Bursa' } }),
          series({ metric: 'INJURED', provinceScope: 'SHARED', points: [{ date: '2020-06-01', value: 10 }] }),
          series({ metric: 'INJURED', provinceScope: 'UNKNOWN', points: [{ date: '2020-06-01', value: 4 }] }),
        ],
      }),
      CATALOG,
      'INJURED',
    );

    expect(data.lines.map((line) => line.label)).toEqual([
      'Bursa',
      strings.incident.sharedProvinces,
      strings.incident.unknownProvince,
    ]);
  });

  it('puts every line of a date on one row, in date order', () => {
    const data = toChartData(
      response({
        series: [
          series({
            metric: 'ACCIDENT_COUNT',
            points: [
              { date: '2020-06-02', value: 5 },
              { date: '2020-06-01', value: 8 },
            ],
          }),
          series({ metric: 'DEATH', points: [{ date: '2020-06-01', value: 1 }] }),
        ],
      }),
      CATALOG,
      null,
    );

    expect(data.rows.map((row) => row.date)).toEqual(['2020-06-01', '2020-06-02']);
    expect(data.rows[0]).toEqual({
      date: '2020-06-01',
      'TRAFFIC_ACCIDENT|ACCIDENT_COUNT|all': 8,
      'TRAFFIC_ACCIDENT|DEATH|all': 1,
    });
  });

  it('leaves a day with no report empty rather than writing a zero', () => {
    // The system does not know that nothing happened, only that nothing was
    // reported - and in cumulative mode a zero would draw a running total that
    // dropped to nothing and then recovered.
    const data = toChartData(
      response({
        series: [
          series({ metric: 'ACCIDENT_COUNT', points: [{ date: '2020-06-01', value: 8 }] }),
          series({ metric: 'DEATH', points: [{ date: '2020-06-02', value: 1 }] }),
        ],
      }),
      CATALOG,
      null,
    );

    expect(data.rows[0]).not.toHaveProperty('TRAFFIC_ACCIDENT|DEATH|all');
    expect(data.rows[1]).not.toHaveProperty('TRAFFIC_ACCIDENT|ACCIDENT_COUNT|all');
  });

  it('passes the values through untouched, cumulative included', () => {
    // FR-12: the running total is the server's, and this module must not be
    // able to produce one. The values below are already cumulative.
    const data = toChartData(
      response({
        cumulative: true,
        series: [
          series({
            metric: 'INJURED',
            points: [
              { date: '2019-01-01', value: 1 },
              { date: '2019-01-02', value: 3 },
              { date: '2019-01-03', value: 6 },
            ],
          }),
        ],
      }),
      CATALOG,
      null,
    );

    expect(data.rows.map((row) => row['TRAFFIC_ACCIDENT|INJURED|all'])).toEqual([1, 3, 6]);
  });

  it('gives every line its own colour, and no line none', () => {
    const data = toChartData(
      response({
        series: [
          series({ metric: 'ACCIDENT_COUNT' }),
          series({ metric: 'DEATH' }),
          series({ metric: 'INJURED' }),
        ],
      }),
      CATALOG,
      null,
    );

    expect(new Set(data.lines.map((line) => line.color)).size).toBe(3);
  });

  it('has nothing to draw when nothing matched', () => {
    expect(toChartData(response(), CATALOG, null)).toEqual({ rows: [], lines: [] });
  });
});

describe('lineKey', () => {
  it('tells two provinces of one metric apart', () => {
    const bursa = lineKey(
      series({ provinceScope: 'SINGLE', province: { code: 16, name: 'Bursa' } }),
    );
    const kocaeli = lineKey(
      series({ provinceScope: 'SINGLE', province: { code: 41, name: 'Kocaeli' } }),
    );

    expect(bursa).not.toBe(kocaeli);
  });

  it('tells the shared and the province-less buckets apart', () => {
    expect(lineKey(series({ provinceScope: 'SHARED' }))).not.toBe(
      lineKey(series({ provinceScope: 'UNKNOWN' })),
    );
  });
});

describe('lineLabel', () => {
  it('falls back to the key when the catalog does not know the metric', () => {
    // NFR-14: inventing a Turkish word here is the hardcoding the rule forbids.
    expect(lineLabel(series({ metric: 'MYSTERY' }), CATALOG, false)).toBe('MYSTERY');
  });
});

describe('metricsOf', () => {
  it('offers what the catalog declares for the type, in its order', () => {
    expect(metricsOf(CATALOG, 'TRAFFIC_ACCIDENT')).toEqual([
      'ACCIDENT_COUNT',
      'DEATH',
      'INJURED',
    ]);
  });

  it('offers nothing for a type the catalog does not know, or none at all', () => {
    expect(metricsOf(CATALOG, 'OTHER')).toEqual([]);
    expect(metricsOf(undefined, 'TRAFFIC_ACCIDENT')).toEqual([]);
  });
});
