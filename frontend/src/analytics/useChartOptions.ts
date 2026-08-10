import { useCallback, useMemo } from 'react';
import { useSearchParams } from 'react-router-dom';
import { applyChartOptions, parseChartOptions, type ChartOptions } from './chartOptions';

/**
 * The chart's settings, read from and written to the address bar - the same one
 * the filters live in (ADR-037), a different set of keys in it.
 */
export function useChartOptions() {
  const [params, setParams] = useSearchParams();

  const options = useMemo(() => parseChartOptions(params), [params]);

  const update = useCallback(
    (patch: Partial<ChartOptions>) => {
      setParams(applyChartOptions(params, { ...options, ...patch }));
    },
    [options, params, setParams],
  );

  return { options, update };
}
