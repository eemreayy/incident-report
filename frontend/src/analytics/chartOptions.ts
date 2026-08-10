/**
 * What the chart is showing, kept in the address bar next to the filters.
 *
 * These are not filters. They change which of the selected records are *drawn*
 * and how, never which records are counted - the summary and the list are
 * untouched by them, and a chart setting never narrows a total. They live in the
 * URL for the same reason the filters do (ADR-037): a chart somebody sends you
 * should open showing what they were looking at.
 *
 * Each module owns its own query-string keys and rewrites only those, so
 * changing a filter leaves the chart alone and the other way round.
 */

export type Breakdown = 'none' | 'province';

export interface ChartOptions {
  /**
   * Which event type is drawn. Null means "not chosen yet" and is resolved
   * against the catalog where it is rendered - parsing cannot depend on the
   * catalog having loaded, and must not drop a type this build has not heard of.
   */
  eventType: string | null;
  /** Which metric is compared across provinces. Only used when broken down. */
  metric: string | null;
  breakdown: Breakdown;
  cumulative: boolean;
}

export const DEFAULT_CHART_OPTIONS: ChartOptions = {
  eventType: null,
  metric: null,
  breakdown: 'none',
  cumulative: false,
};

const CHART_PARAMS = ['chart', 'metric', 'breakdown', 'cumulative'] as const;

export function parseChartOptions(params: URLSearchParams): ChartOptions {
  return {
    eventType: blankToNull(params.get('chart')),
    metric: blankToNull(params.get('metric')),
    breakdown: params.get('breakdown') === 'province' ? 'province' : 'none',
    cumulative: params.get('cumulative') === 'true',
  };
}

/** Rewrites the chart's own keys in a copy of the address bar, leaving the rest. */
export function applyChartOptions(params: URLSearchParams, options: ChartOptions): URLSearchParams {
  const next = new URLSearchParams(params);
  for (const key of CHART_PARAMS) {
    next.delete(key);
  }
  if (options.eventType) next.set('chart', options.eventType);
  if (options.metric) next.set('metric', options.metric);
  if (options.breakdown !== 'none') next.set('breakdown', options.breakdown);
  if (options.cumulative) next.set('cumulative', 'true');
  return next;
}

/**
 * Which event type the chart draws, given what is chosen, what the filters allow
 * and what the catalog offers.
 *
 * The chart draws one type at a time because its series are that type's metrics
 * (FR-23). What it draws can never be something the filters exclude: a chart
 * showing records the table below it does not is two answers to one question.
 * So a choice outside the filter is not honoured - the first allowed type is.
 */
export function resolveEventType(
  chosen: string | null,
  filtered: string[],
  catalog: string[],
): string | null {
  const allowed = filtered.length > 0 ? filtered : catalog;
  if (chosen !== null && allowed.includes(chosen)) {
    return chosen;
  }
  return allowed[0] ?? null;
}

/** The same, for the metric compared across provinces: the chosen one if the type has it. */
export function resolveMetric(chosen: string | null, available: string[]): string | null {
  if (chosen !== null && available.includes(chosen)) {
    return chosen;
  }
  return available[0] ?? null;
}

function blankToNull(value: string | null): string | null {
  const trimmed = value?.trim() ?? '';
  return trimmed.length > 0 ? trimmed : null;
}
