import { useState } from 'react';
import { BackendStatus } from './BackendStatus';
import { FilterBar } from '../incidents/FilterBar';
import { IncidentListPanel } from '../incidents/IncidentListPanel';
import { ReportForm } from '../report/ReportForm';
import { SubmissionResult } from '../report/SubmissionResult';
import { strings } from '../i18n/strings';

/**
 * The panel screen, S-1 in PRD 5.4, as far as it goes today: enter a report, see
 * what came out of it, and read the records themselves. The summary and the
 * chart join the filter bar in the tasks that follow.
 *
 * Note what is *not* passed between the filter bar and the list: nothing. They
 * both read the address bar (TC-15), which is what will let the summary and the
 * chart show the same view without a fourth copy of the filter state.
 *
 * The last submission's id is held here rather than inside the form, because the
 * result outlives the form's own state - the text area is cleared on success and
 * the result must stay on screen. That id is deliberately not a filter: it is
 * about one submission, not about what the analyst is looking at.
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
        <FilterBar />
        <IncidentListPanel />
      </main>
    </div>
  );
}
