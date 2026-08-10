import { useMetadata } from '../api/queries';
import { SORT_OPTIONS, type IncidentSort } from '../filters/incidentFilters';
import { useIncidentFilters } from '../filters/useIncidentFilters';
import { strings } from '../i18n/strings';

/**
 * The filter bar (FR-21).
 *
 * It owns no state. Every control shows what the address bar says and writes
 * back to it, which is why this component and the record list need no
 * connection: they are looking at the same URL (TC-15).
 *
 * Nothing offered here is written in this file. Event types and provinces come
 * from /metadata, so a type added to the server's YAML appears in these controls
 * with no frontend release (NFR-14, FR-27).
 */
export function FilterBar() {
  const { filters, update, clear } = useIncidentFilters();
  const { data: metadata } = useMetadata();

  /**
   * A selection is a decision and applies at once; a half-typed word is not, so
   * the keyword applies when the form is submitted. The alternative - a timer -
   * would send a request per pause in typing and make every test of this screen
   * depend on the clock (TC-16).
   */
  function applyKeyword(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const typed = new FormData(event.currentTarget).get('keyword');
    const keyword = typeof typed === 'string' ? typed.trim() : '';
    update({ keyword: keyword.length > 0 ? keyword : null });
  }

  function toggleEventType(key: string, selected: boolean) {
    update({
      eventTypes: selected
        ? [...filters.eventTypes, key]
        : filters.eventTypes.filter((existing) => existing !== key),
    });
  }

  function selectProvinces(options: HTMLCollectionOf<HTMLOptionElement>) {
    update({ provinces: Array.from(options, (option) => Number(option.value)) });
  }

  return (
    <section className="panel filter-bar">
      <h2>{strings.filters.heading}</h2>
      <form onSubmit={applyKeyword}>
        <fieldset className="filter-group">
          <legend>{strings.filters.eventType}</legend>
          {metadata === undefined ? (
            <p className="muted">{strings.filters.loading}</p>
          ) : (
            <div className="checkbox-row">
              {metadata.eventTypes.map((eventType) => (
                <label key={eventType.key} className="checkbox-option">
                  <input
                    type="checkbox"
                    checked={filters.eventTypes.includes(eventType.key)}
                    onChange={(event) => toggleEventType(eventType.key, event.target.checked)}
                  />
                  {eventType.label}
                </label>
              ))}
            </div>
          )}
        </fieldset>

        <div className="filter-row">
          <div className="filter-field">
            <label htmlFor="filter-province">{strings.filters.province}</label>
            <select
              id="filter-province"
              multiple
              size={4}
              value={filters.provinces.map(String)}
              onChange={(event) => selectProvinces(event.target.selectedOptions)}
            >
              {(metadata?.provinces ?? []).map((province) => (
                <option key={province.code} value={province.code}>
                  {province.name}
                </option>
              ))}
            </select>
            <p className="muted">{strings.filters.provinceHint}</p>
          </div>

          <div className="filter-field">
            <label htmlFor="filter-from">{strings.filters.from}</label>
            <input
              id="filter-from"
              type="date"
              value={filters.from ?? ''}
              max={filters.to ?? undefined}
              onChange={(event) => update({ from: event.target.value || null })}
            />

            <label htmlFor="filter-to">{strings.filters.to}</label>
            <input
              id="filter-to"
              type="date"
              value={filters.to ?? ''}
              min={filters.from ?? undefined}
              onChange={(event) => update({ to: event.target.value || null })}
            />
          </div>

          <div className="filter-field">
            <label htmlFor="filter-keyword">{strings.filters.keyword}</label>
            {/* Keyed by what is applied: when the URL changes underneath - the
                back button, a shared link - the box is remounted showing it,
                rather than holding on to a word nothing is filtered by. */}
            <input
              key={filters.keyword ?? ''}
              id="filter-keyword"
              name="keyword"
              type="search"
              defaultValue={filters.keyword ?? ''}
            />
            <p className="muted">{strings.filters.keywordHint}</p>

            <label htmlFor="filter-sort">{strings.filters.sort}</label>
            <select
              id="filter-sort"
              value={filters.sort}
              onChange={(event) => update({ sort: event.target.value as IncidentSort })}
            >
              {SORT_OPTIONS.map((option) => (
                <option key={option} value={option}>
                  {strings.filters.sortOption[option]}
                </option>
              ))}
            </select>
          </div>
        </div>

        <div className="form-actions">
          <button type="submit">{strings.filters.apply}</button>
          <button type="button" className="secondary" onClick={clear}>
            {strings.filters.clear}
          </button>
          <span className="muted">{strings.filters.note}</span>
        </div>
      </form>
    </section>
  );
}
