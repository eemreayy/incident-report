import { useIncidentsByRawReport, useMetadata } from '../api/queries';
import { messageForError } from '../i18n/errorMessages';
import { strings } from '../i18n/strings';
import { IncidentCard } from './IncidentCard';

/**
 * What the submitted text turned into (FR-19).
 *
 * Read with the receipt's id, not waited for on the stream: the submitter sees
 * their own result whether or not the live connection is up, and the stream is
 * left to do what it is for - telling *other* clients (ADR-021).
 *
 * The server's `warnings` are deliberately not printed. They are English prose
 * with no code to translate, so what a reader sees here is derived from the
 * machine-readable fields instead - status, classification, date source (C-9).
 */
export function SubmissionResult({ rawReportId }: { rawReportId: string }) {
  const { data, isPending, isError, error, refetch } = useIncidentsByRawReport(rawReportId);
  const { data: metadata } = useMetadata();

  if (isPending) {
    return (
      <section className="panel" aria-busy="true">
        <h2>{strings.result.heading}</h2>
        <p className="muted">{strings.result.loading}</p>
      </section>
    );
  }

  if (isError) {
    return (
      <section className="panel">
        <h2>{strings.result.heading}</h2>
        <p className="error" role="alert">
          {messageForError(error)}
        </p>
        <button type="button" onClick={() => void refetch()}>
          {strings.result.retry}
        </button>
      </section>
    );
  }

  const analysis = data.analysis;

  return (
    <section className="panel">
      <h2>{strings.result.heading}</h2>

      {analysis == null ? (
        <p className="muted">{strings.result.missing}</p>
      ) : analysis.status === 'FAILED' ? (
        // Rule 4: the text survives an analysis failure, and the user is told
        // rather than left with a silently empty result.
        <p role="alert">{strings.result.failed}</p>
      ) : data.content.length === 0 ? (
        <p>{strings.result.none}</p>
      ) : (
        <>
          <p className="muted">{strings.result.recordCount(data.totalElements)}</p>
          <ul className="incident-list">
            {data.content.map((incident) => (
              <IncidentCard key={incident.id} incident={incident} metadata={metadata} />
            ))}
          </ul>
        </>
      )}
    </section>
  );
}
