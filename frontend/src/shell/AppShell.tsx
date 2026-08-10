import { useState } from 'react';
import { BackendStatus } from './BackendStatus';
import { CatalogPanel } from './CatalogPanel';
import { ReportForm } from '../report/ReportForm';
import { SubmissionResult } from '../report/SubmissionResult';
import { strings } from '../i18n/strings';

/**
 * The panel screen, S-1 in PRD 5.4, as far as it goes today: enter a report and
 * see what came out of it. The record list, the summary and the chart join it in
 * the tasks that follow.
 *
 * The last submission's id is held here rather than inside the form, because the
 * result outlives the form's own state - the text area is cleared on success and
 * the result must stay on screen.
 */
export function AppShell() {
  const [lastRawReportId, setLastRawReportId] = useState<string | null>(null);

  return (
    <div className="app-shell">
      <header className="app-header">
        <div>
          <h1>{strings.app.title}</h1>
          <p className="app-subtitle">{strings.app.subtitle}</p>
        </div>
        <BackendStatus />
      </header>
      <main>
        <ReportForm onSubmitted={setLastRawReportId} />
        {lastRawReportId !== null && <SubmissionResult rawReportId={lastRawReportId} />}
        <CatalogPanel />
      </main>
    </div>
  );
}
