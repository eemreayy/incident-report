import type { Metadata, Summary, SummaryRow } from '../api/types';

/**
 * The summary, arranged for reading (FR-22, FR-24). No arithmetic happens here.
 *
 * Every number on screen comes from the server exactly as it arrived, including
 * both totals. That is the point rather than an economy: when a figure is shared
 * between provinces, the bucket rows genuinely do not add up to the event type
 * total above them (ADR-019). Summing the rows here would produce a different
 * number, and it would look right - it would simply have lost the ten injured
 * people the text refused to attribute to either province.
 *
 * What this module does decide is arrangement: which columns a block has and in
 * what order.
 */

export interface SummaryBlock {
  /** Catalog key. The label is resolved where it is rendered (NFR-14). */
  eventType: string;
  /** Bucket rows, in the order the server sent: provinces, then shared, then none. */
  rows: SummaryRow[];
  /** The server's own total for this event type - never the sum of `rows`. */
  total: SummaryRow | undefined;
  metricKeys: string[];
}

/**
 * One block per event type, because metrics belong to event types: a single wide
 * table would carry a column for every metric in the catalog and leave most of
 * them empty on every row, which reads as missing data rather than as a metric
 * that does not apply.
 */
export function toBlocks(summary: Summary, metadata: Metadata | undefined): SummaryBlock[] {
  const order = eventTypeOrder(summary, metadata);

  return order.map((eventType) => {
    const rows = summary.rows.filter((row) => row.eventType === eventType);
    const total = summary.eventTypeTotals.find((row) => row.eventType === eventType);
    return {
      eventType,
      rows,
      total,
      metricKeys: metricColumns(eventType, [...rows, ...(total ? [total] : [])], metadata),
    };
  });
}

/**
 * The columns of the grand total, which spans event types and therefore has no
 * catalog entry of its own: the catalog's own order first, so a metric keeps the
 * position a reader has already seen it in, then anything left over.
 */
export function totalMetricColumns(summary: Summary, metadata: Metadata | undefined): string[] {
  const catalogOrder = (metadata?.eventTypes ?? []).flatMap((type) =>
    type.metrics.map((metric) => metric.key),
  );
  return orderKeys(Object.keys(summary.total.metrics), catalogOrder);
}

/** Whether this block has a figure that belongs to no single province. */
export function unattributedRows(rows: SummaryRow[]): SummaryRow[] {
  return rows.filter((row) => row.provinceScope !== 'SINGLE');
}

/**
 * Event types in catalog order, then any the catalog does not know.
 *
 * An unknown key is kept rather than hidden: `OTHER` is produced by code and
 * never appears in the catalog (ADR-006), and a type could equally arrive from a
 * server whose YAML is ahead of this build. Dropping either would quietly remove
 * records from a total that claims to be complete.
 */
function eventTypeOrder(summary: Summary, metadata: Metadata | undefined): string[] {
  const present = new Set<string>();
  for (const row of [...summary.rows, ...summary.eventTypeTotals]) {
    if (row.eventType !== undefined) {
      present.add(row.eventType);
    }
  }
  const catalogOrder = (metadata?.eventTypes ?? []).map((type) => type.key);
  return orderKeys([...present], catalogOrder);
}

/**
 * A block's columns: the metrics this event type declares, in the order the
 * catalog declares them, minus the ones no row carries - a column of nothing but
 * dashes says less than not having it. Metrics the catalog does not declare are
 * appended rather than dropped, for the same reason as unknown event types.
 */
function metricColumns(
  eventType: string,
  rows: SummaryRow[],
  metadata: Metadata | undefined,
): string[] {
  const present = new Set(rows.flatMap((row) => Object.keys(row.metrics)));
  const catalogOrder =
    metadata?.eventTypes
      .find((type) => type.key === eventType)
      ?.metrics.map((metric) => metric.key) ?? [];
  return orderKeys([...present], catalogOrder);
}

/**
 * Known keys in the given order, then the rest alphabetically so it is stable.
 *
 * The order may name a key more than once and must still yield one column: the
 * grand total's ordering is every event type's metrics laid end to end, and
 * DEATH is deliberately declared by several types (PRD 7). Without the
 * de-duplication the same total is printed once per type that declares it.
 */
function orderKeys(keys: string[], order: string[]): string[] {
  const known = [...new Set(order)].filter((key) => keys.includes(key));
  const rest = keys.filter((key) => !order.includes(key)).sort();
  return [...known, ...rest];
}
