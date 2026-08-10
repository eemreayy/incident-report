import { Link } from 'react-router-dom';
import { eventTypeLabel, metricLabel } from '../i18n/catalogLabels';
import { strings } from '../i18n/strings';
import { provinceLabel, provinceNote } from './provinceLabel';
import type { Incident, Metadata } from '../api/types';

/**
 * The records, as a table (FR-21).
 *
 * Exactly the rows the server sent, in the order it sent them: no sorting, no
 * filtering, no totals. Everything the reader sees was decided by the query, so
 * what is on screen and what the URL says can never disagree.
 */
export function IncidentTable({
  incidents,
  metadata,
}: {
  incidents: Incident[];
  metadata: Metadata | undefined;
}) {
  return (
    <div className="table-scroll">
      <table className="incident-table">
        <thead>
          <tr>
            <th scope="col">{strings.list.column.date}</th>
            <th scope="col">{strings.list.column.eventType}</th>
            <th scope="col">{strings.list.column.province}</th>
            <th scope="col">{strings.list.column.metrics}</th>
            <th scope="col">{strings.list.column.detail}</th>
          </tr>
        </thead>
        <tbody>
          {incidents.map((incident) => (
            <tr key={incident.id}>
              <td>
                {incident.occurredOn}
                <br />
                <span className="muted">
                  {strings.incident.dateSourceShort[incident.dateSource]}
                </span>
              </td>
              <td>
                {eventTypeLabel(metadata, incident.eventType)}
                {incident.classification === 'UNCLASSIFIED' && (
                  <>
                    {' '}
                    <span className="badge badge-warn">{strings.incident.unclassified}</span>
                  </>
                )}
              </td>
              <td>
                {provinceLabel(incident)}
                {/* ADR-019: a shared figure is neither split across its provinces
                    nor dropped from a province-filtered view. It appears here as
                    its own row, labelled, so the per-province figures and the
                    grand total can be reconciled by the reader. */}
                {provinceNote(incident) !== null && (
                  <>
                    <br />
                    <span className="muted">{provinceNote(incident)}</span>
                  </>
                )}
              </td>
              <td>
                {incident.metrics.length === 0 ? (
                  <span className="muted">{strings.list.noMetrics}</span>
                ) : (
                  <ul className="metric-list">
                    {incident.metrics.map((metric) => (
                      <li key={metric.metricType}>
                        {metricLabel(metadata, incident.eventType, metric.metricType)}:{' '}
                        <strong>{metric.value}</strong>
                      </li>
                    ))}
                  </ul>
                )}
              </td>
              <td>
                {/* FR-08/FR-26: every row is a way into the record and, from
                    there, into the text it came from. */}
                <Link to={`/incidents/${incident.id}`}>{strings.list.detail}</Link>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
