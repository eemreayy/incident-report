import { QueryClientProvider, type QueryClient } from '@tanstack/react-query';
import { BrowserRouter, Route, Routes } from 'react-router-dom';
import { AppShell } from './shell/AppShell';

/**
 * The query client is injected rather than created here, so a test can hand in
 * one with retries disabled instead of waiting out the real retry policy.
 *
 * One route today. Filter state belongs in the URL (FR-21) and the two detail
 * screens are addressable (PRD 5.4), which is why the router is wired now
 * rather than retrofitted once those tasks land.
 */
export function App({ queryClient }: { queryClient: QueryClient }) {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <Routes>
          <Route path="/" element={<AppShell />} />
        </Routes>
      </BrowserRouter>
    </QueryClientProvider>
  );
}
