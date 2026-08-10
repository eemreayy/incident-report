import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { REFRESH_WINDOW_MS, RETRY_DELAY_MS, useIncidentStream } from './useIncidentStream';
import { queryKeys } from '../api/queries';
import type { IncidentPage } from '../api/types';

/**
 * jsdom has no EventSource, so the transport is stood in for - and standing it
 * in is also what makes the connection's own behaviour testable: opening,
 * breaking, giving up and being closed are events the real thing only produces
 * against a real server.
 */
class FakeEventSource {
  static readonly CONNECTING = 0;
  static readonly OPEN = 1;
  static readonly CLOSED = 2;
  static instances: FakeEventSource[] = [];

  readyState = FakeEventSource.CONNECTING;
  closed = false;
  onopen: (() => void) | null = null;
  onerror: (() => void) | null = null;
  private readonly listeners = new Map<string, Array<(event: MessageEvent<string>) => void>>();

  constructor(readonly url: string) {
    FakeEventSource.instances.push(this);
  }

  addEventListener(type: string, listener: (event: MessageEvent<string>) => void) {
    this.listeners.set(type, [...(this.listeners.get(type) ?? []), listener]);
  }

  close() {
    this.closed = true;
    this.readyState = FakeEventSource.CLOSED;
  }

  /* Driving the connection from a test. */

  open() {
    this.readyState = FakeEventSource.OPEN;
    this.onopen?.();
  }

  emit(data: unknown) {
    for (const listener of this.listeners.get('incidents') ?? []) {
      listener({ data: JSON.stringify(data) } as MessageEvent<string>);
    }
  }

  drop({ givenUp = false } = {}) {
    this.readyState = givenUp ? FakeEventSource.CLOSED : FakeEventSource.CONNECTING;
    this.onerror?.();
  }
}

/**
 * The connection is driven inside act(): each of these ends in a React state
 * update, and outside act() the assertion would run against the render before it.
 */
function open(stream: FakeEventSource) {
  act(() => stream.open());
}

function drop(stream: FakeEventSource, options: { givenUp?: boolean } = {}) {
  act(() => stream.drop(options));
}

function emit(stream: FakeEventSource, signal: unknown) {
  act(() => stream.emit(signal));
}

function signalFor(rawReportId: string, eventType = 'EARTHQUAKE', provinceCodes = [16]) {
  return {
    rawReportId,
    analyzedAt: '2026-08-10T14:30:59Z',
    incidents: [{ incidentId: 1, occurredOn: '2020-06-01', eventType, provinceCodes }],
  };
}

function Subscriber() {
  const { status } = useIncidentStream();
  return <p data-testid="status">{status}</p>;
}

let queryClient: QueryClient;
let invalidate: ReturnType<typeof vi.spyOn>;
let unmountSubscriber: () => void;

function renderSubscriber(initialUrl = '/') {
  queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  invalidate = vi.spyOn(queryClient, 'invalidateQueries');
  const { unmount } = render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[initialUrl]}>
        <Subscriber />
      </MemoryRouter>
    </QueryClientProvider>,
  );
  unmountSubscriber = unmount;
  return FakeEventSource.instances.at(-1) as FakeEventSource;
}

function invalidatedKeys(): string[] {
  return (invalidate.mock.calls as Array<[{ queryKey: unknown }]>).map((call) =>
    JSON.stringify(call[0].queryKey),
  );
}

/** One refresh marks several query families stale; this counts refreshes, not calls. */
function refreshes(): number {
  return invalidatedKeys().filter((key) => key === JSON.stringify(queryKeys.incidents)).length;
}

beforeEach(() => {
  vi.useFakeTimers();
  FakeEventSource.instances = [];
  vi.stubGlobal('EventSource', FakeEventSource);
});

afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe('useIncidentStream', () => {
  it('subscribes once, to an address on this page’s own origin', () => {
    renderSubscriber();

    expect(FakeEventSource.instances).toHaveLength(1);
    // ADR-025: relative, like every other call. An absolute address here would
    // be the one that got away.
    expect(FakeEventSource.instances[0]?.url).toBe('/api/v1/stream/incidents');
  });

  it('refreshes every view derived from records when a signal arrives', () => {
    const stream = renderSubscriber();

    emit(stream, signalFor('report-1'));

    const invalidated = invalidatedKeys();
    expect(invalidated).toContain(JSON.stringify(queryKeys.incidents));
    expect(invalidated).toContain(JSON.stringify(queryKeys.analytics));
  });

  it('does not turn ten submissions into ten refreshes', () => {
    // The task's own acceptance criterion. The first signal refreshes at once,
    // because one submission is the usual case and waiting would feel slow;
    // the rest ride on a single refresh at the end of the window.
    const stream = renderSubscriber();

    for (let i = 0; i < 10; i += 1) {
      emit(stream, signalFor(`report-${i}`));
      vi.advanceTimersByTime(20);
    }

    expect(refreshes()).toBe(1);
    vi.advanceTimersByTime(REFRESH_WINDOW_MS);
    expect(refreshes()).toBe(2);
  });

  it('keeps refreshing under a steady trickle rather than waiting for silence', () => {
    // What a plain trailing debounce would get wrong: a signal every so often,
    // forever, and the view never refreshes at all.
    const stream = renderSubscriber();

    for (let i = 0; i < 4; i += 1) {
      emit(stream, signalFor(`report-${i}`));
      vi.advanceTimersByTime(REFRESH_WINDOW_MS);
    }

    expect(refreshes()).toBeGreaterThanOrEqual(3);
  });

  it('ignores a signal that cannot affect the view being shown', () => {
    const stream = renderSubscriber('/?eventType=EPIDEMIC');

    emit(stream, signalFor('report-1', 'EARTHQUAKE'));
    vi.advanceTimersByTime(REFRESH_WINDOW_MS * 2);

    expect(refreshes()).toBe(0);
  });

  it('refreshes for a report whose records are on screen, whatever the filters', () => {
    // Reprocess: the records that disappeared are the ones the signal no longer
    // mentions, so relevance cannot be judged from its contents alone.
    const stream = renderSubscriber('/?eventType=EPIDEMIC');
    const page: IncidentPage = {
      content: [
        {
          id: 1,
          rawReportId: 'report-1',
          occurredOn: '2020-06-01',
          dateSource: 'EXPLICIT',
          eventType: 'EPIDEMIC',
          classification: 'CLASSIFIED',
          provinceScope: 'UNKNOWN',
          sharedAcross: [],
          metrics: [],
          keywords: [],
        },
      ],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    };
    queryClient.setQueryData(['incidents', 'list', { anything: true }], page);

    emit(stream, signalFor('report-1', 'EARTHQUAKE'));

    expect(refreshes()).toBe(1);
  });

  it('follows the filters as they change, without reopening the connection', () => {
    const stream = renderSubscriber('/?eventType=EPIDEMIC');

    emit(stream, signalFor('report-1', 'EARTHQUAKE'));
    expect(refreshes()).toBe(0);
    expect(FakeEventSource.instances).toHaveLength(1);
  });

  it('reports the connection in words a reader can act on', () => {
    const stream = renderSubscriber();

    expect(screen.getByTestId('status')).toHaveTextContent('connecting');

    open(stream);
    expect(screen.getByTestId('status')).toHaveTextContent('open');

    drop(stream);
    expect(screen.getByTestId('status')).toHaveTextContent('reconnecting');

    drop(stream, { givenUp: true });
    expect(screen.getByTestId('status')).toHaveTextContent('closed');
  });

  it('opens a new stream when the browser has given up on the old one', () => {
    // Seen against the real system: a stopped backend answers through nginx
    // with a 502 rather than refusing the connection, and EventSource treats
    // that as fatal - it reaches CLOSED and never tries again. Left to the
    // browser, a page meant to stay live would sit there silently dead.
    const stream = renderSubscriber();
    open(stream);

    drop(stream, { givenUp: true });
    expect(screen.getByTestId('status')).toHaveTextContent('closed');
    expect(FakeEventSource.instances).toHaveLength(1);

    act(() => vi.advanceTimersByTime(RETRY_DELAY_MS));

    expect(FakeEventSource.instances).toHaveLength(2);
    open(FakeEventSource.instances[1] as FakeEventSource);
    expect(screen.getByTestId('status')).toHaveTextContent('open');
    // Nothing was replayed while it was down, so coming back means refetching.
    expect(refreshes()).toBe(1);
  });

  it('does not open a second stream while the browser is still trying', () => {
    const stream = renderSubscriber();
    open(stream);

    drop(stream);
    act(() => vi.advanceTimersByTime(RETRY_DELAY_MS * 2));

    expect(screen.getByTestId('status')).toHaveTextContent('reconnecting');
    expect(FakeEventSource.instances).toHaveLength(1);
  });

  it('refreshes when it comes back, because nothing is replayed', () => {
    // ADR-034: the stream sends nothing twice. Everything that happened while
    // the connection was down was missed outright, so reconnecting is a reason
    // to refetch and not merely to change an indicator.
    const stream = renderSubscriber();
    open(stream);
    expect(refreshes()).toBe(0);

    drop(stream);
    open(stream);

    expect(refreshes()).toBe(1);
  });

  it('closes the connection when the page goes', () => {
    // The socket must not outlive the page: a server that keeps a subscriber
    // nobody will ever read from finds out only when it next tries to write.
    const stream = renderSubscriber();

    unmountSubscriber();

    expect(stream.closed).toBe(true);
  });

  it('does not refresh after the page has gone', () => {
    // A refresh scheduled inside the window would otherwise fire into a tree
    // that is no longer there.
    const stream = renderSubscriber();
    emit(stream, signalFor('report-1'));
    emit(stream, signalFor('report-2'));
    expect(refreshes()).toBe(1);

    unmountSubscriber();
    vi.advanceTimersByTime(REFRESH_WINDOW_MS * 2);

    expect(refreshes()).toBe(1);
  });
});
