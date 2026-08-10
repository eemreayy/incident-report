import { useState } from 'react';
import { BackendStatus } from './BackendStatus';
import { SummaryPanel } from '../analytics/SummaryPanel';
import { FilterBar } from '../incidents/FilterBar';
import { IncidentListPanel } from '../incidents/IncidentListPanel';
import { ReportForm } from '../report/ReportForm';
import { SubmissionResult } from '../report/SubmissionResult';
import { strings } from '../i18n/strings';

/**
 * The panel screen, S-1 in PRD 5.4, as far as it goes today: enter a report, see
 * what came out of it, read the totals and then the records themselves. The
 * chart joins them in T-28.
 *
 * Note what is *not* passed between the filter bar, the summary and the list:
 * nothing. All three read the address bar (ADR-037), which is what keeps them
 * showing one view without a copy of the filter state per panel.
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
        <SummaryPanel />
        <IncidentListPanel />
      </main>
    </div>
  );
}
