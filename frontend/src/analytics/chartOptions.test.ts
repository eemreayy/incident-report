import { describe, expect, it } from 'vitest';
import {
  applyChartOptions,
  DEFAULT_CHART_OPTIONS,
  parseChartOptions,
  resolveEventType,
  resolveMetric,
} from './chartOptions';

function parse(query: string) {
  return parseChartOptions(new URLSearchParams(query));
}

describe('parseChartOptions', () => {
  it('reads the chart out of the address bar', () => {
    expect(parse('chart=EPIDEMIC&metric=DEATH&breakdown=province&cumulative=true')).toEqual({
      eventType: 'EPIDEMIC',
      metric: 'DEATH',
      breakdown: 'province',
      cumulative: true,
    });
  });

  it('reads an empty address bar as the plain chart', () => {
    expect(parse('')).toEqual(DEFAULT_CHART_OPTIONS);
  });

  it('treats anything it does not understand as the default', () => {
    // A URL is typed and truncated by hand; a stray value is not worth an error.
    expect(parse('breakdown=district&cumulative=evet')).toMatchObject({
      breakdown: 'none',
      cumulative: false,
    });
  });

  it('keeps an event type the catalog may not have', () => {
    // NFR-14 again: the catalog is the server's, and parsing must not depend on
    // it having loaded.
    expect(parse('chart=FLOOD').eventType).toBe('FLOOD');
  });
});

describe('applyChartOptions', () => {
  it('leaves the filters alone', () => {
    // The address bar carries the whole view; each module rewrites only its own
    // keys. Rebuilding it from scratch here would drop the filters every time
    // somebody ticked "cumulative".
    const params = new URLSearchParams('eventType=EPIDEMIC&province=6&page=3');

    const next = applyChartOptions(params, { ...DEFAULT_CHART_OPTIONS, cumulative: true });

    expect(next.getAll('eventType')).toEqual(['EPIDEMIC']);
    expect(next.get('province')).toBe('6');
    expect(next.get('page')).toBe('3');
    expect(next.get('cumulative')).toBe('true');
  });

  it('leaves defaults out of the address', () => {
    expect(applyChartOptions(new URLSearchParams(), DEFAULT_CHART_OPTIONS).toString()).toBe('');
  });

  it('survives a round trip', () => {
    const options = {
      eventType: 'EARTHQUAKE',
      metric: 'INJURED',
      breakdown: 'province' as const,
      cumulative: true,
    };

    expect(parseChartOptions(applyChartOptions(new URLSearchParams(), options))).toEqual(options);
  });

  it('replaces rather than repeats when a choice changes', () => {
    const first = applyChartOptions(new URLSearchParams(), {
      ...DEFAULT_CHART_OPTIONS,
      eventType: 'EPIDEMIC',
    });

    const second = applyChartOptions(first, { ...DEFAULT_CHART_OPTIONS, eventType: 'FIRE' });

    expect(second.getAll('chart')).toEqual(['FIRE']);
  });
});

describe('resolveEventType', () => {
  it('draws what was chosen', () => {
    expect(resolveEventType('FIRE', [], ['EPIDEMIC', 'FIRE'])).toBe('FIRE');
  });

  it('never draws a type the filters exclude', () => {
    // A chart showing records the table below does not is two answers to one
    // question, and nothing on screen would say which to believe.
    expect(resolveEventType('FIRE', ['EPIDEMIC'], ['EPIDEMIC', 'FIRE'])).toBe('EPIDEMIC');
  });

  it('picks the first thing it is allowed to draw when nothing is chosen', () => {
    expect(resolveEventType(null, [], ['EPIDEMIC', 'FIRE'])).toBe('EPIDEMIC');
    expect(resolveEventType(null, ['FIRE'], ['EPIDEMIC', 'FIRE'])).toBe('FIRE');
  });

  it('has nothing to draw when the catalog is empty or still on its way', () => {
    expect(resolveEventType(null, [], [])).toBeNull();
  });
});

describe('resolveMetric', () => {
  it('keeps the chosen metric, or falls back to the first the type has', () => {
    expect(resolveMetric('DEATH', ['ACCIDENT_COUNT', 'DEATH'])).toBe('DEATH');
    // Switching event type usually invalidates the metric: a traffic accident
    // has no "damaged building".
    expect(resolveMetric('DAMAGED_BUILDING', ['ACCIDENT_COUNT', 'DEATH'])).toBe('ACCIDENT_COUNT');
    expect(resolveMetric(null, [])).toBeNull();
  });
});
