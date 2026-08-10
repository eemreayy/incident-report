import { strings } from './strings';
import type { Metadata } from '../api/types';

/**
 * Values the catalog never publishes because the code, not the YAML, produces
 * them. Looked up after the catalog and before falling back to the raw key.
 */
const STRUCTURAL_LABELS: Record<string, string | undefined> = {
  OTHER: strings.incident.otherEventType,
};

/**
 * Turns catalog keys into the words a user reads.
 *
 * The labels live in the server's YAML and arrive through /metadata (NFR-14), so
 * a new event type reaches the screen with no frontend release. When a key is
 * missing from the catalog the key itself is shown: wrong-looking on purpose,
 * because inventing a Turkish label here would be the very hardcoding the rule
 * forbids, and silently blanking it would hide real data.
 */
export function eventTypeLabel(metadata: Metadata | undefined, key: string): string {
  const fromCatalog = metadata?.eventTypes.find((type) => type.key === key)?.label;
  return fromCatalog ?? STRUCTURAL_LABELS[key] ?? key;
}

/**
 * Metric labels are declared per event type, so the lookup starts there. It
 * falls back to any type that declares the same key - DEATH is deliberately
 * shared across types (PRD 7) and carries the same label in each.
 */
export function metricLabel(
  metadata: Metadata | undefined,
  eventTypeKey: string,
  metricKey: string,
): string {
  const own = metadata?.eventTypes
    .find((type) => type.key === eventTypeKey)
    ?.metrics.find((metric) => metric.key === metricKey);
  if (own) {
    return own.label;
  }
  for (const type of metadata?.eventTypes ?? []) {
    const found = type.metrics.find((metric) => metric.key === metricKey);
    if (found) {
      return found.label;
    }
  }
  return metricKey;
}
