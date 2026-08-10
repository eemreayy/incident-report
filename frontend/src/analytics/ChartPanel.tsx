import { useState } from 'react';
import {
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { useMetadata, useTimeSeries } from '../api/queries';
import { isFiltered, toFilterQuery } from '../filters/incidentFilters';
import { useIncidentFilters } from '../filters/useIncidentFilters';
import { eventTypeLabel, metricLabel } from '../i18n/catalogLabels';
import { messageForError } from '../i18n/errorMessages';
import { strings } from '../i18n/strings';
import { metricsOf, toChartData } from './chartModel';
import { resolveEventType, resolveMetric } from './chartOptions';
import { useChartOptions } from './useChartOptions';

/**
 * Incidents over time (FR-23, FR-24, FR-12).
 *
 * Every number here was computed by the server: the running total, the province
 * split, the grouping into lines. What this component decides is what to ask for
 * and how to draw the answer.
 *
 * It reads the filters from the address bar exactly as the summary and the list
 * do (ADR-037), so it cannot be showing a different set of records than they
 * are - and the one narrowing it adds of its own, the event type it plots, is
 * always one the filters allow.
 */
export function ChartPanel() {
  const { filters } = useIncidentFilters();
  const { options, update } = useChartOptions();
  const { data: metadata } = useMetadata();

  // Which series are on screen is not filter state: it changes nothing about
  // what was asked or counted, and putting it in the address bar would make
  // every legend click a step in the browser's history.
  const [hidden, setHidden] = useState<string[]>([]);

  const catalogTypes = (metadata?.eventTypes ?? []).map((type) => type.key);
  const eventType = resolveEventType(options.eventType, filters.eventTypes, catalogTypes);
  const availableMetrics = metricsOf(metadata, eventType);
  const metric = resolveMetric(options.metric, availableMetrics);

  const query = {
    ...toFilterQuery(filters),
    eventTypes: eventType === null ? [] : [eventType],
    ...(options.breakdown === 'province' ? { groupBy: 'province' as const } : {}),
    ...(options.cumulative ? { cumulative: true } : {}),
  };
  const { data, isPending, isError, error, refetch, isFetching, isPlaceholderData } = useTimeSeries(
    query,
    eventType !== null,
  );

  if (eventType === null) {
    return (
      <section className="panel">
        <h2>{strings.chart.heading}</h2>
        <p className="muted">
          {metadata === undefined ? strings.chart.loading : strings.chart.noEventTypes}
        </p>
      </section>
    );
  }

  const chart = data === undefined ? null : toChartData(data, metadata, metric);

  return (
    <section className="panel" aria-busy={isPending}>
      <h2>{strings.chart.heading}</h2>

      <div className="chart-controls">
        <div className="filter-field">
          <label htmlFor="chart-event-type">{strings.chart.eventType}</label>
          <select
            id="chart-event-type"
            value={eventType}
            onChange={(event) => update({ eventType: event.target.value })}
          >
            {/* Only the types the filters allow: a chart of records the table
                below does not contain is two answers to one question. */}
            {(filters.eventTypes.length > 0 ? filters.eventTypes : catalogTypes).map((key) => (
              <option key={key} value={key}>
                {eventTypeLabel(metadata, key)}
              </option>
            ))}
          </select>
        </div>

        {options.breakdown === 'province' && metric !== null && (
          <div className="filter-field">
            <label htmlFor="chart-metric">{strings.chart.metric}</label>
            <select
              id="chart-metric"
              value={metric}
              onChange={(event) => update({ metric: event.target.value })}
            >
              {availableMetrics.map((key) => (
                <option key={key} value={key}>
                  {metricLabel(metadata, eventType, key)}
                </option>
              ))}
            </select>
          </div>
        )}

        <div className="chart-toggles">
          <label className="checkbox-option">
            <input
              type="checkbox"
              checked={options.breakdown === 'province'}
              onChange={(event) =>
                update({ breakdown: event.target.checked ? 'province' : 'none' })
              }
            />
            {strings.chart.breakdown}
          </label>
          <label className="checkbox-option">
            <input
              type="checkbox"
              checked={options.cumulative}
              // FR-12: asked of the server. Adding the points up here would be a
              // second definition of "the total so far", in a second language.
              onChange={(event) => update({ cumulative: event.target.checked })}
            />
            {strings.chart.cumulative}
          </label>
        </div>
      </div>

      {options.breakdown === 'province' && <p className="muted">{strings.chart.breakdownHint}</p>}

      {isError ? (
        <>
          <p className="error" role="alert">
            {messageForError(error)}
          </p>
          <button type="button" onClick={() => void refetch()}>
            {strings.chart.retry}
          </button>
        </>
      ) : isPending || chart === null ? (
        <p className="muted">{strings.chart.loading}</p>
      ) : chart.lines.length === 0 ? (
        <p>{isFiltered(filters) ? strings.chart.emptyFiltered : strings.chart.empty}</p>
      ) : (
        <>
          <p className="muted" aria-live="polite">
            {/* Read from the answer rather than from the switch: while a request
                is in flight the two disagree, and a cumulative chart labelled as
                a plain one is read as a different fact entirely. */}
            {data.cumulative ? strings.chart.cumulativeOn : strings.chart.plain}
            {isFetching && isPlaceholderData ? ` · ${strings.chart.refreshing}` : ''}
          </p>
          <div className="chart-area">
            <ResponsiveContainer width="100%" height={320}>
              <LineChart data={chart.rows} margin={{ top: 8, right: 16, bottom: 8, left: 0 }}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="date" tick={{ fontSize: 12 }} />
                <YAxis tick={{ fontSize: 12 }} allowDecimals={false} />
                <Tooltip />
                <Legend
                  onClick={(entry) => toggle(String(entry.dataKey), setHidden)}
                  // The library sorts the legend alphabetically by default. The
                  // order the lines are in is the catalog's, which is the order
                  // the summary table uses too - the legend follows it rather
                  // than inventing a third one.
                  itemSorter={(item) =>
                    chart.lines.findIndex((line) => line.key === String(item.dataKey))
                  }
                />
                {chart.lines.map((line) => (
                  <Line
                    key={line.key}
                    type="monotone"
                    dataKey={line.key}
                    name={line.label}
                    stroke={line.color}
                    strokeWidth={2}
                    dot
                    // A date with no report is a gap, not a zero; joining across
                    // it keeps a sparse series visible as one line rather than
                    // as a scattering of unconnected dots.
                    connectNulls
                    hide={hidden.includes(line.key)}
                  />
                ))}
              </LineChart>
            </ResponsiveContainer>
          </div>
          <p className="muted">{strings.chart.legendHint}</p>
          <p className="muted">{strings.chart.scopeNote}</p>
        </>
      )}
    </section>
  );
}

function toggle(key: string, setHidden: React.Dispatch<React.SetStateAction<string[]>>) {
  setHidden((current) =>
    current.includes(key) ? current.filter((existing) => existing !== key) : [...current, key],
  );
}
