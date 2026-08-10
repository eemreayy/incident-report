import { Link, useParams } from 'react-router-dom';
import {
  useIncidentsByRawReport,
  useMetadata,
  useRawReport,
  useReprocess,
} from '../api/queries';
import { eventTypeLabel } from '../i18n/catalogLabels';
import { messageForError } from '../i18n/errorMessages';
import { provinceLine } from '../incidents/provinceLabel';
import { strings } from '../i18n/strings';
import { HighlightedText } from './HighlightedText';
import type { Incident } from '../api/types';

/**
 * S-3 in PRD 5.4: the stored text, what was made of it, and the way to make it
 * again (FR-26, FR-14, FR-15).
 *
 * Two requests, deliberately. The raw report endpoint returns the text and
 * nothing else, because the analysis outcome and the derived records are the
 * analysis side's data (ADR-021) - so they are read from the records endpoint
 * with this report's id. That split is what keeps either module from
 * representing the other's data.
 *
 * The keywords highlighted on the text are collected from every record derived
 * from it: one text routinely produces several records, and each carries its own
 * matches over the same text.
 */
export function RawReportPage() {
  const { id = '' } = useParams();
  const report = useRawReport(id);
  const derived = useIncidentsByRawReport(id === '' ? null : id);
  const { data: metadata } = useMetadata();
  const reprocess = useReprocess();

  if (report.isPending) {
    return (
      <ReportShell busy>
        <p className="muted">{strings.detail.loading}</p>
      </ReportShell>
    );
  }

  if (report.isError) {
    return (
      <ReportShell>
        <p className="error" role="alert">
          {messageForError(report.error)}
        </p>
        <button type="button" onClick={() => void report.refetch()}>
          {strings.detail.retry}
        </button>
      </ReportShell>
    );
  }

  const records = derived.data?.content ?? [];
  const analysis = derived.data?.analysis;
  const keywords = records.flatMap((incident) => incident.keywords);

  return (
    <ReportShell>
      <h1>{strings.detail.reportHeading}</h1>
      <p className="muted">
        {strings.detail.submittedAt}: {report.data.submittedAt}
        {analysis != null && (
          <>
            {' · '}
            {strings.detail.analysisStatus}:{' '}
            {analysis.status === 'ANALYZED'
              ? strings.detail.analysisAnalyzed
              : strings.detail.analysisFailed}
            {' · '}
            {strings.detail.analyzedAt}: {analysis.analyzedAt}
          </>
        )}
      </p>

      <h2>{strings.detail.rawText}</h2>
      <HighlightedText text={report.data.text} keywords={keywords} />
      <p className="muted">{strings.detail.highlightNote}</p>

      <div className="form-actions">
        <button
          type="button"
          disabled={reprocess.isPending}
          onClick={() => reprocess.mutate(id)}
        >
          {reprocess.isPending ? strings.detail.reprocessing : strings.detail.reprocess}
        </button>
        <span className="muted">{strings.detail.reprocessNote}</span>
      </div>
      {reprocess.isError && (
        <p className="error" role="alert">
          {messageForError(reprocess.error)}
        </p>
      )}
      {reprocess.isSuccess && !reprocess.isPending && (
        <p role="status">{strings.detail.reprocessDone}</p>
      )}

      <h2>{strings.detail.derived}</h2>
      {derived.isPending ? (
        <p className="muted">{strings.detail.loading}</p>
      ) : derived.isError ? (
        // Saying "this text produced nothing" when the question was never
        // answered would be reporting a failure as a fact (FR-28).
        <>
          <p className="error" role="alert">
            {messageForError(derived.error)}
          </p>
          <button type="button" onClick={() => void derived.refetch()}>
            {strings.detail.retry}
          </button>
        </>
      ) : records.length === 0 ? (
        <p>{strings.detail.derivedEmpty}</p>
      ) : (
        <>
          <p className="muted">{strings.detail.derivedCount(records.length)}</p>
          <ul className="incident-list">
            {records.map((incident) => (
              <li key={incident.id} className="incident">
                <DerivedRecord incident={incident} label={eventTypeLabel(metadata, incident.eventType)} />
              </li>
            ))}
          </ul>
        </>
      )}
    </ReportShell>
  );
}

function DerivedRecord({ incident, label }: { incident: Incident; label: string }) {
  return (
    <>
      <div className="incident-head">
        <strong>{label}</strong>
        {incident.classification === 'UNCLASSIFIED' && (
          <span className="badge badge-warn">{strings.incident.unclassified}</span>
        )}
      </div>
      <p className="muted">
        {incident.occurredOn} · {provinceLine(incident)}
      </p>
      {/* FR-08's other direction: from the text back to each record it produced. */}
      <Link to={`/incidents/${incident.id}`}>{strings.detail.openIncident}</Link>
    </>
  );
}

function ReportShell({ children, busy = false }: { children: React.ReactNode; busy?: boolean }) {
  return (
    <div className="app-shell">
      {/* The same landmarks as the panel screen: a reader jumping by landmark
          should not find one page shaped differently from the others. */}
      <nav>
        <Link to="/">{strings.detail.backToPanel}</Link>
      </nav>
      <main>
        <section className="panel" aria-busy={busy}>
          {children}
        </section>
      </main>
    </div>
  );
}
