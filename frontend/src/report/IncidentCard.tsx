import { eventTypeLabel, metricLabel } from '../i18n/catalogLabels';
import { provinceLine } from '../incidents/provinceLabel';
import { strings } from '../i18n/strings';
import type { Incident, Metadata } from '../api/types';

/**
 * One extracted record, as a reader needs to judge it.
 *
 * Three things are stated rather than implied, because each is a decision the
 * system made that the user is entitled to see: where the date came from
 * (FR-06), whether the event type was recognised at all (FR-09), and how the
 * figures relate to provinces (ADR-019).
 */
export function IncidentCard({
  incident,
  metadata,
}: {
  incident: Incident;
  metadata: Metadata | undefined;
}) {
  return (
    <li className="incident">
      <div className="incident-head">
        <strong>{eventTypeLabel(metadata, incident.eventType)}</strong>
        {incident.classification === 'UNCLASSIFIED' && (
          <span className="badge badge-warn">{strings.incident.unclassified}</span>
        )}
      </div>

      <p className="muted">
        {incident.occurredOn} · {strings.incident.dateSource[incident.dateSource]}
      </p>

      <p>{provinceLine(incident)}</p>

      {incident.metrics.length === 0 ? (
        <p className="muted">{strings.incident.noMetrics}</p>
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

      {incident.classification === 'UNCLASSIFIED' && (
        <p className="muted">{strings.incident.unclassifiedNote}</p>
      )}
    </li>
  );
}
