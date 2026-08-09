import { BackendStatus } from './BackendStatus';
import { strings } from '../i18n/strings';

/**
 * The frame every screen sits in. The screens themselves (S-1..S-3 in PRD 5.4)
 * arrive with T-25 onwards; what is here is the shell and nothing more.
 */
export function AppShell() {
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
        <section className="placeholder">
          <h2>{strings.placeholder.heading}</h2>
          <p>{strings.placeholder.body}</p>
        </section>
      </main>
    </div>
  );
}
