import { describe, expect, it } from 'vitest';
import { eventTypeLabel, metricLabel } from './catalogLabels';
import { strings } from './strings';
import type { Metadata } from '../api/types';

const CATALOG: Metadata = {
  eventTypes: [
    {
      key: 'EPIDEMIC',
      label: 'Salgın',
      metrics: [
        { key: 'NEW_CASE', label: 'Yeni vaka' },
        { key: 'DEATH', label: 'Can kaybı' },
      ],
    },
    {
      key: 'EARTHQUAKE',
      label: 'Deprem',
      metrics: [
        { key: 'DEATH', label: 'Can kaybı' },
        { key: 'RESCUED', label: 'Kurtarılan' },
      ],
    },
  ],
  provinces: [],
};

describe('eventTypeLabel', () => {
  it('reads the label out of the catalog', () => {
    expect(eventTypeLabel(CATALOG, 'EARTHQUAKE')).toBe('Deprem');
  });

  it('labels OTHER, which the catalog never publishes', () => {
    // The unclassified fallback is produced by code, not by the YAML (ADR-006),
    // so it has no catalog entry and would otherwise reach a Turkish reader as
    // the bare word OTHER.
    expect(eventTypeLabel(CATALOG, 'OTHER')).toBe(strings.incident.otherEventType);
  });

  it('shows the key when the catalog does not know it', () => {
    // Deliberately not a Turkish word invented here: that would be the very
    // hardcoding NFR-14 forbids, and it would hide that the catalog is behind.
    expect(eventTypeLabel(CATALOG, 'AVALANCHE')).toBe('AVALANCHE');
  });

  it('shows the key while the catalog is still loading', () => {
    expect(eventTypeLabel(undefined, 'EPIDEMIC')).toBe('EPIDEMIC');
  });
});

describe('metricLabel', () => {
  it('prefers the metric declared by the record’s own event type', () => {
    expect(metricLabel(CATALOG, 'EPIDEMIC', 'NEW_CASE')).toBe('Yeni vaka');
  });

  it('falls back to another type that declares the same metric', () => {
    // DEATH is shared across event types on purpose (PRD 7). A record whose own
    // type does not list it must still get a word, not a bare key.
    expect(metricLabel(CATALOG, 'EPIDEMIC', 'RESCUED')).toBe('Kurtarılan');
  });

  it('shows the key when no event type declares the metric', () => {
    expect(metricLabel(CATALOG, 'EPIDEMIC', 'LIVESTOCK_LOST')).toBe('LIVESTOCK_LOST');
  });

  it('shows the key when the event type itself is unknown', () => {
    expect(metricLabel(CATALOG, 'AVALANCHE', 'DEATH')).toBe('Can kaybı');
    expect(metricLabel(CATALOG, 'AVALANCHE', 'BURIED')).toBe('BURIED');
  });

  it('shows the key while the catalog is still loading', () => {
    expect(metricLabel(undefined, 'EPIDEMIC', 'DEATH')).toBe('DEATH');
  });
});
