import { QueryClientProvider, type QueryClient } from '@tanstack/react-query';
import { BrowserRouter, Route, Routes } from 'react-router-dom';
import { AppShell } from './shell/AppShell';
import { IncidentDetailPage } from './traceability/IncidentDetailPage';
import { RawReportPage } from './traceability/RawReportPage';

/**
 * The query client is injected rather than created here, so a test can hand in
 * one with retries disabled instead of waiting out the real retry policy.
 *
 * Three screens, all addressable (PRD 5.4): the panel, whose filters live in its
 * query string (FR-21), and the two detail screens, whose addresses carry the id
 * so a link to one record can be shared.
 */
export function App({ queryClient }: { queryClient: QueryClient }) {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <Routes>
          <Route path="/" element={<AppShell />} />
          <Route path="/incidents/:id" element={<IncidentDetailPage />} />
          <Route path="/reports/:id" element={<RawReportPage />} />
        </Routes>
      </BrowserRouter>
    </QueryClientProvider>
  );
}
