import { eventTypeLabel, metricLabel } from '../i18n/catalogLabels';
import { strings } from '../i18n/strings';
import { unattributedRows, type SummaryBlock } from './summaryModel';
import type { Metadata, SummaryRow } from '../api/types';

/**
 * One event type's totals: a row per province bucket, then the type's own total
 * (FR-22, FR-24).
 *
 * The shared and province-less rows are the reason this table is shaped the way
 * it is. They are neither split across provinces nor left out (ADR-019), so they
 * sit in the same table as the provinces, labelled as what they are - and the
 * total below them comes from the server, which is why it is visibly larger than
 * the province rows add up to. The note under the table says so in words, since
 * a difference nobody explains is indistinguishable from a mistake.
 */
export function SummaryTable({
  block,
  metadata,
}: {
  block: SummaryBlock;
  metadata: Metadata | undefined;
}) {
  const unattributed = unattributedRows(block.rows);

  return (
    <section className="summary-block">
      <h3>{eventTypeLabel(metadata, block.eventType)}</h3>
      <div className="table-scroll">
        <table className="summary-table">
          <thead>
            <tr>
              <th scope="col">{strings.summary.column.breakdown}</th>
              <th scope="col">{strings.summary.column.incidentCount}</th>
              {block.metricKeys.map((metric) => (
                <th key={metric} scope="col">
                  {metricLabel(metadata, block.eventType, metric)}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {block.rows.map((row) => (
              <tr key={rowKey(row)} data-scope={row.provinceScope}>
                {/* The label is what marks these rows out, not the shading that
                    goes with it: a row a reader can only tell apart by its
                    background is a row some readers cannot tell apart at all
                    (NFR-16). "Ortak toplam" reads as a province name to nobody. */}
                <th scope="row">{breakdownLabel(row)}</th>
                <td>{row.incidentCount}</td>
                {block.metricKeys.map((metric) => (
                  <td key={metric}>{metricCell(row, metric)}</td>
                ))}
              </tr>
            ))}
          </tbody>
          {block.total !== undefined && (
            <tfoot>
              <tr>
                <th scope="row">{strings.summary.eventTypeTotal}</th>
                <td>
                  <strong>{block.total.incidentCount}</strong>
                </td>
                {block.metricKeys.map((metric) => (
                  <td key={metric}>
                    <strong>{metricCell(block.total as SummaryRow, metric)}</strong>
                  </td>
                ))}
              </tr>
            </tfoot>
          )}
        </table>
      </div>
      {unattributed.length > 0 && (
        <p className="muted">
          {strings.summary.reconcile(unattributed.map(breakdownLabel).join(', '))}
        </p>
      )}
    </section>
  );
}

function rowKey(row: SummaryRow): string {
  return row.provinceScope === 'SINGLE' ? `p${row.province?.code}` : String(row.provinceScope);
}

function breakdownLabel(row: SummaryRow): string {
  switch (row.provinceScope) {
    case 'SINGLE':
      return row.province?.name ?? strings.incident.unknownProvince;
    case 'SHARED':
      // The summary buckets every shared figure together (ADR-036), so unlike a
      // record this row cannot say which provinces it covers - the wording must
      // not imply otherwise.
      return strings.incident.sharedProvinces;
    default:
      return strings.incident.unknownProvince;
  }
}

/**
 * A metric the bucket has no figure for is a dash, not a zero. The server omits
 * the key when nothing was extracted, and writing 0 there would state something
 * the text never said.
 */
function metricCell(row: SummaryRow, metric: string): string | number {
  return row.metrics[metric] ?? strings.summary.noValue;
}
