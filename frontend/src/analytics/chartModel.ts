import { metricLabel } from '../i18n/catalogLabels';
import { strings } from '../i18n/strings';
import type { Metadata, TimeSeries, TimeSeriesSeries } from '../api/types';

/**
 * The server's series, arranged for a chart library. Nothing is added up here.
 *
 * A cumulative point is already cumulative when it arrives (FR-12), a province
 * line already excludes the figures that belong to no province (ADR-019), and
 * dates with no data are simply absent. All this module does is pivot points
 * into rows - a chart wants one object per date - and decide what each line is
 * called and coloured.
 */

/** Enough distinct colours for the series counts this chart allows; then it repeats. */
const PALETTE = [
  '#1f6feb',
  '#b3261e',
  '#1b7f4b',
  '#8a5a00',
  '#6f42c1',
  '#0b7285',
  '#c2255c',
  '#495057',
];

export interface ChartLine {
  /** Identifies the line in the pivoted rows, in the legend, and in what is hidden. */
  key: string;
  label: string;
  color: string;
}

export interface ChartData {
  /** One object per date, carrying a value per line - the shape a chart reads. */
  rows: Array<Record<string, string | number>>;
  lines: ChartLine[];
}

/**
 * Two modes, because a chart can only compare like with like.
 *
 * Without the breakdown a line is a metric, and the reader compares metrics over
 * time. With it a line is a place, and the reader compares places - which is
 * only meaningful for one metric at a time. Drawing every metric for every
 * province at once would put deaths and damaged buildings on one axis and answer
 * nothing; it is also where the readability problem in this task comes from.
 *
 * Restricting to one metric is a display choice and changes no number: each
 * series stands alone, so leaving one out never alters another. It is never
 * applied to the points inside a series.
 */
export function toChartData(
  timeSeries: TimeSeries,
  metadata: Metadata | undefined,
  metric: string | null,
): ChartData {
  const brokenDown = timeSeries.groupBy === 'PROVINCE';
  const drawn = brokenDown
    ? timeSeries.series.filter((series) => series.metric === metric)
    : timeSeries.series;

  const lines = drawn.map((series, index) => ({
    key: lineKey(series),
    label: lineLabel(series, metadata, brokenDown),
    color: PALETTE[index % PALETTE.length] as string,
  }));

  return { rows: pivot(drawn), lines };
}

/** Which metrics this event type could be broken down by, from the catalog (NFR-14). */
export function metricsOf(metadata: Metadata | undefined, eventType: string | null): string[] {
  return (
    metadata?.eventTypes
      .find((type) => type.key === eventType)
      ?.metrics.map((metric) => metric.key) ?? []
  );
}

/**
 * One row per date that any line has a point for.
 *
 * A date a line has no point for is left out of that line's key rather than
 * written as zero: the system does not know that nothing happened, only that
 * nothing was reported (see `TimeSeries`). A zero would be an assertion the data
 * does not make, and in cumulative mode it would draw a total that fell back to
 * nothing and then recovered.
 */
function pivot(series: TimeSeriesSeries[]): Array<Record<string, string | number>> {
  const byDate = new Map<string, Record<string, string | number>>();

  for (const line of series) {
    const key = lineKey(line);
    for (const point of line.points) {
      const row = byDate.get(point.date) ?? { date: point.date };
      row[key] = point.value;
      byDate.set(point.date, row);
    }
  }

  return [...byDate.values()].sort((a, b) => String(a.date).localeCompare(String(b.date)));
}

/** Stable and unique per line: the same series always lands in the same column. */
export function lineKey(series: TimeSeriesSeries): string {
  const bucket =
    series.provinceScope === 'SINGLE'
      ? `p${series.province?.code}`
      : (series.provinceScope ?? 'all');
  return `${series.eventType}|${series.metric}|${bucket}`;
}

/**
 * What the line is called: the metric when metrics are what differ, the place
 * when places are. A shared figure says so in words - "Ortak toplam" is not a
 * province name and cannot be mistaken for one (ADR-038).
 */
export function lineLabel(
  series: TimeSeriesSeries,
  metadata: Metadata | undefined,
  brokenDown: boolean,
): string {
  if (!brokenDown) {
    return metricLabel(metadata, series.eventType, series.metric);
  }
  switch (series.provinceScope) {
    case 'SINGLE':
      return series.province?.name ?? strings.incident.unknownProvince;
    case 'SHARED':
      return strings.incident.sharedProvinces;
    default:
      return strings.incident.unknownProvince;
  }
}
