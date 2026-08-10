import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterEach } from 'vitest';

/**
 * Gaps in jsdom, filled for every test file rather than per test.
 *
 * jsdom implements no layout, so it has no ResizeObserver and reports every
 * element as zero by zero. The chart measures its container before drawing, so
 * without these it does not merely draw nothing - it throws, and any screen
 * holding a chart renders as an empty page.
 *
 * This is a stand-in for the browser's layout, not for the chart: the chart
 * itself renders for real, which is why Recharts was chosen (ADR-026).
 */
globalThis.ResizeObserver = class {
  constructor(private readonly callback: ResizeObserverCallback) {}
  observe(target: Element) {
    this.callback(
      [{ target, contentRect: { width: 800, height: 320 } } as ResizeObserverEntry],
      this as unknown as ResizeObserver,
    );
  }
  unobserve() {}
  disconnect() {}
};

for (const [property, value] of [
  ['offsetWidth', 800],
  ['offsetHeight', 320],
  ['clientWidth', 800],
  ['clientHeight', 320],
] as const) {
  Object.defineProperty(HTMLElement.prototype, property, { configurable: true, value });
}

/**
 * jsdom has no EventSource either, and every screen subscribes to the stream.
 * This one does nothing: a connection that never opens and never delivers is
 * the right default for tests that are about something else. The tests that are
 * about the stream replace it with one they can drive.
 */
globalThis.EventSource = class {
  static readonly CONNECTING = 0;
  static readonly OPEN = 1;
  static readonly CLOSED = 2;
  readyState = 0;
  constructor(readonly url: string) {}
  addEventListener() {}
  removeEventListener() {}
  close() {}
} as unknown as typeof EventSource;

afterEach(() => {
  cleanup();
});
