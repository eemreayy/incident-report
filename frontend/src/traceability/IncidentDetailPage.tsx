import { Link, useParams } from 'react-router-dom';
import { useIncident, useMetadata } from '../api/queries';
import { eventTypeLabel, metricLabel } from '../i18n/catalogLabels';
import { messageForError } from '../i18n/errorMessages';
import { provinceLine } from '../incidents/provinceLabel';
import { strings } from '../i18n/strings';

/**
 * S-2 in PRD 5.4: one record, and everything the system decided about it
 * (FR-26, FR-17, FR-06).
 *
 * The point of the screen is traceability, so it states the decisions rather
 * than only the results: where the date came from, whether the type was
 * recognised, how the figures relate to provinces, which words triggered which
 * extraction - and a link to the text all of it came from.
 */
export function IncidentDetailPage() {
  const { id } = useParams();
  const incidentId = Number(id);
  const { data, isPending, isError, error, refetch } = useIncident(incidentId);
  const { data: metadata } = useMetadata();

  if (!Number.isInteger(incidentId)) {
    return (
      <DetailShell>
        <p>{strings.detail.incidentMissing}</p>
      </DetailShell>
    );
  }

  if (isPending) {
    return (
      <DetailShell busy>
        <p className="muted">{strings.detail.loading}</p>
      </DetailShell>
    );
  }

  if (isError) {
    return (
      <DetailShell>
        <p className="error" role="alert">
          {messageForError(error)}
        </p>
        <button type="button" onClick={() => void refetch()}>
          {strings.detail.retry}
        </button>
      </DetailShell>
    );
  }

  return (
    <DetailShell>
      <h1>{strings.detail.incidentHeading(data.id)}</h1>

      <div className="incident-head">
        <strong>{eventTypeLabel(metadata, data.eventType)}</strong>
        {data.classification === 'UNCLASSIFIED' && (
          <span className="badge badge-warn">{strings.incident.unclassified}</span>
        )}
      </div>

      <dl className="detail-list">
        <dt>{strings.detail.occurredOn}</dt>
        <dd>
          {data.occurredOn}{' '}
          <span className="muted">· {strings.incident.dateSource[data.dateSource]}</span>
        </dd>

        <dt>{strings.detail.scope}</dt>
        <dd>{provinceLine(data)}</dd>

        <dt>{strings.detail.metrics}</dt>
        <dd>
          {data.metrics.length === 0 ? (
            <span className="muted">{strings.incident.noMetrics}</span>
          ) : (
            <ul className="metric-list">
              {data.metrics.map((metric) => (
                <li key={metric.metricType}>
                  {metricLabel(metadata, data.eventType, metric.metricType)}:{' '}
                  <strong>{metric.value}</strong>
                </li>
              ))}
            </ul>
          )}
        </dd>

        <dt>{strings.detail.keywords}</dt>
        <dd>
          {data.keywords.length === 0 ? (
            <span className="muted">{strings.detail.keywordsEmpty}</span>
          ) : (
            <ul className="keyword-list">
              {/* FR-17: which word triggered which extraction, not just which
                  words were found. */}
              {data.keywords.map((keyword) => (
                <li key={`${keyword.role}-${keyword.charStart}-${keyword.charEnd}`}>
                  <mark className="keyword" data-role={keyword.role}>
                    {keyword.keyword}
                  </mark>{' '}
                  <span className="muted">{strings.detail.keywordRole[keyword.role]}</span>
                </li>
              ))}
            </ul>
          )}
        </dd>

        <dt>{strings.detail.sourceReport}</dt>
        <dd>
          {/* FR-08, one of its two directions: from a record to the text it was
              derived from. The other direction lives on that screen. */}
          <Link to={`/reports/${data.rawReportId}`}>{strings.detail.openSourceReport}</Link>
        </dd>
      </dl>
    </DetailShell>
  );
}

function DetailShell({ children, busy = false }: { children: React.ReactNode; busy?: boolean }) {
  return (
    <div className="app-shell">
      <p>
        <Link to="/">{strings.detail.backToPanel}</Link>
      </p>
      <section className="panel" aria-busy={busy}>
        {children}
      </section>
    </div>
  );
}
