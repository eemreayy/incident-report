import { useState } from 'react';
import { useSubmitReport } from '../api/queries';
import { messageForError } from '../i18n/errorMessages';
import { strings } from '../i18n/strings';

/**
 * The single text area the whole system starts from (FR-18).
 *
 * Only the blank check is done here. The maximum length is a server setting and
 * is not published in /metadata, so guessing it in the source would be a number
 * that drifts the moment configuration changes; instead the server's own
 * `report.text.too-long` rejection is shown in Turkish. See C-9 in PRD 8.2.
 */
export function ReportForm({ onSubmitted }: { onSubmitted: (rawReportId: string) => void }) {
  const [text, setText] = useState('');
  const { mutate, isPending, isError, error, reset } = useSubmitReport();

  const isBlank = text.trim().length === 0;

  function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    if (isBlank || isPending) {
      return;
    }
    mutate(text, {
      onSuccess: (receipt) => {
        // Cleared only once the text is safely stored, so a failed submission
        // never costs the user what they typed.
        setText('');
        onSubmitted(receipt.id);
      },
    });
  }

  return (
    <section className="panel">
      <h2>{strings.form.heading}</h2>
      <form onSubmit={handleSubmit}>
        <label htmlFor="report-text">{strings.form.label}</label>
        <p className="muted">{strings.form.hint}</p>
        <textarea
          id="report-text"
          rows={6}
          value={text}
          placeholder={strings.form.placeholder}
          onChange={(event) => {
            setText(event.target.value);
            if (isError) {
              reset();
            }
          }}
        />
        <div className="form-actions">
          {/* Locked while in flight, so the same text cannot be sent twice. */}
          <button type="submit" disabled={isBlank || isPending}>
            {isPending ? strings.form.submitting : strings.form.submit}
          </button>
          <span className="muted">
            {strings.form.charCount(text.length)}
            {isBlank ? ` · ${strings.form.emptyHint}` : ''}
          </span>
        </div>
      </form>
      {isError && (
        <p className="error" role="alert">
          {messageForError(error)}
        </p>
      )}
    </section>
  );
}
