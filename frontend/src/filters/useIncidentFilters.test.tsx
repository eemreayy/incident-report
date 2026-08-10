import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, useLocation, useNavigate } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import { useIncidentFilters } from './useIncidentFilters';

/**
 * A stand-in for the real screens: it does nothing but read the filters and
 * change them, which is exactly what the filter bar, the list and - later - the
 * chart do. Two of them are rendered side by side in one test, because "one
 * source of filter state" is a claim about more than one component.
 */
function FilterProbe({ label = 'probe' }: { label?: string } = {}) {
  const { filters, update, clear } = useIncidentFilters();
  const location = useLocation();
  const navigate = useNavigate();

  return (
    <div>
      <p data-testid={`${label}-filters`}>{JSON.stringify(filters)}</p>
      <p data-testid={`${label}-search`}>{location.search}</p>
      <button type="button" onClick={() => update({ eventTypes: ['EPIDEMIC'] })}>
        {label}: tip
      </button>
      <button type="button" onClick={() => update({ page: 4 })}>
        {label}: sayfa
      </button>
      <button type="button" onClick={clear}>
        {label}: temizle
      </button>
      <button type="button" onClick={() => navigate(-1)}>
        {label}: geri
      </button>
    </div>
  );
}

function renderProbe(initialUrl = '/', children = <FilterProbe />) {
  return render(<MemoryRouter initialEntries={[initialUrl]}>{children}</MemoryRouter>);
}

function filtersOf(label = 'probe') {
  return JSON.parse(screen.getByTestId(`${label}-filters`).textContent ?? '{}');
}

describe('useIncidentFilters', () => {
  it('reads the view out of the address it was opened at', () => {
    // FR-21: the copied address opens the same view. Nothing else in the
    // application has to have happened first.
    renderProbe('/?eventType=EPIDEMIC&province=6&page=2');

    expect(filtersOf()).toMatchObject({ eventTypes: ['EPIDEMIC'], provinces: [6], page: 2 });
  });

  it('writes a filter change into the address bar', async () => {
    renderProbe();

    await userEvent.click(screen.getByRole('button', { name: 'probe: tip' }));

    expect(screen.getByTestId('probe-search')).toHaveTextContent('eventType=EPIDEMIC');
  });

  it('returns to the first page when the filter changes', async () => {
    // Page 4 of a narrower result is usually not a page at all, and the empty
    // screen that follows reads as "no records" rather than "past the end".
    renderProbe('/?page=4');

    await userEvent.click(screen.getByRole('button', { name: 'probe: tip' }));

    expect(filtersOf().page).toBe(1);
  });

  it('keeps the page number when the page itself is what changed', async () => {
    renderProbe('/?eventType=EPIDEMIC');

    await userEvent.click(screen.getByRole('button', { name: 'probe: sayfa' }));

    expect(filtersOf()).toMatchObject({ page: 4, eventTypes: ['EPIDEMIC'] });
  });

  it('clears back to a clean address', async () => {
    renderProbe('/?eventType=EPIDEMIC&province=6&keyword=vaka&page=3');

    await userEvent.click(screen.getByRole('button', { name: 'probe: temizle' }));

    expect(screen.getByTestId('probe-search')).toHaveTextContent('');
    expect(filtersOf()).toMatchObject({ eventTypes: [], provinces: [], keyword: null, page: 1 });
  });

  it('shows every reader the same filters, without them being wired together', async () => {
    // TC-15: the filter bar and the list are not connected to each other. What
    // keeps them showing the same view is that there is only one place the
    // filters live - so a change made by one is already the other's state.
    renderProbe(
      '/',
      <>
        <FilterProbe label="bar" />
        <FilterProbe label="list" />
      </>,
    );

    await userEvent.click(screen.getByRole('button', { name: 'bar: tip' }));

    expect(filtersOf('list')).toMatchObject({ eventTypes: ['EPIDEMIC'] });
  });

  it('leaves the chart’s own settings alone when a filter changes', async () => {
    // The address bar carries the whole view, of which the filters are one part.
    // Rebuilding the query string from the filters alone would reset the chart
    // every time somebody ticked a box.
    renderProbe('/?chart=EPIDEMIC&breakdown=province&cumulative=true');

    await userEvent.click(screen.getByRole('button', { name: 'probe: tip' }));

    expect(screen.getByTestId('probe-search')).toHaveTextContent('chart=EPIDEMIC');
    expect(screen.getByTestId('probe-search')).toHaveTextContent('breakdown=province');
    expect(screen.getByTestId('probe-search')).toHaveTextContent('cumulative=true');
  });

  it('clears the filters and only the filters', async () => {
    renderProbe('/?eventType=EPIDEMIC&province=6&chart=FIRE&cumulative=true');

    await userEvent.click(screen.getByRole('button', { name: 'probe: temizle' }));

    const search = screen.getByTestId('probe-search');
    expect(search).not.toHaveTextContent('eventType');
    expect(search).not.toHaveTextContent('province');
    expect(search).toHaveTextContent('chart=FIRE');
    expect(search).toHaveTextContent('cumulative=true');
  });

  it('lets the back button undo a filter change', async () => {
    // The router is already keeping this history, which is a large part of why
    // the URL was chosen over a store.
    renderProbe('/?province=6');

    await userEvent.click(screen.getByRole('button', { name: 'probe: tip' }));
    expect(filtersOf().eventTypes).toEqual(['EPIDEMIC']);

    await userEvent.click(screen.getByRole('button', { name: 'probe: geri' }));

    await vi.waitFor(() => expect(filtersOf().eventTypes).toEqual([]));
    expect(filtersOf().provinces).toEqual([6]);
  });
});
