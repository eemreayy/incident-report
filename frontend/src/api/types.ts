/**
 * The API contract, as the running system actually answers it.
 *
 * These types were written against captured responses rather than from the
 * documentation, the same rule the Postman collection follows. Anything the
 * backend does not serve yet - /incidents, /analytics, reprocess - is absent on
 * purpose: a type for an endpoint that returns 404 is a guess, and a guess here
 * would compile happily and mislead the tasks that follow.
 */

/** A catalog entry: the machine key plus the label a user reads (FR-16). */
export interface MetricDefinition {
  key: string;
  label: string;
}

export interface EventTypeDefinition {
  key: string;
  label: string;
  metrics: MetricDefinition[];
}

export interface Province {
  code: number;
  name: string;
}

/**
 * Everything the interface is allowed to offer as a choice. Nothing in the UI
 * may hardcode an event type, a metric or a province (NFR-14): adding FLOOD to
 * the YAML must show up here and therefore on screen, with no frontend release.
 */
export interface Metadata {
  eventTypes: EventTypeDefinition[];
  provinces: Province[];
}

/**
 * What submission answers with: a receipt, not a result (ADR-021). What was
 * extracted is read separately, once /incidents exists (T-16).
 */
export interface RawReportReceipt {
  id: string;
  submittedAt: string;
}

/** A stored raw report. Carries no analysis outcome - that is analysis's data. */
export interface RawReport {
  id: string;
  text: string;
  submittedAt: string;
}

/** The pagination envelope. `totalElements` is what separates "no results" from "empty page". */
export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/** How the record's date was arrived at. A relative phrase is an extraction, not a fallback (ADR-014). */
export type DateSource = 'EXPLICIT' | 'RELATIVE' | 'DEFAULTED';

/** Whether the catalog recognised the event type at all (ADR-006). */
export type Classification = 'CLASSIFIED' | 'UNCLASSIFIED';

/** How the record relates to a province (ADR-019). */
export type ProvinceScope = 'SINGLE' | 'SHARED' | 'UNKNOWN';

export type KeywordRole = 'DATE' | 'PROVINCE' | 'EVENT_TYPE' | 'METRIC';

export interface IncidentMetric {
  metricType: string;
  value: number;
}

/** Where in the raw text the word sat, so it can be highlighted without searching (C-3). */
export interface ExtractedKeyword {
  keyword: string;
  role: KeywordRole;
  charStart: number;
  charEnd: number;
}

export interface Incident {
  id: number;
  rawReportId: string;
  occurredOn: string;
  dateSource: DateSource;
  /** A catalog key. Its label comes from /metadata, never from here (NFR-14). */
  eventType: string;
  classification: Classification;
  provinceScope: ProvinceScope;
  /**
   * Absent - not null - unless the scope is SINGLE. Captured from the running
   * system: the key is simply not serialised for SHARED and UNKNOWN records.
   */
  province?: Province;
  /** The provinces a SHARED figure covers. Never split across them (ADR-019). */
  sharedAcross: Province[];
  metrics: IncidentMetric[];
  keywords: ExtractedKeyword[];
}

export type AnalysisStatus = 'ANALYZED' | 'FAILED';

/**
 * The analysis side's own account of a report (ADR-021). It rides on the
 * envelope only when the query is filtered by rawReportId, and is null when no
 * such report exists.
 */
export interface AnalysisOutcome {
  status: AnalysisStatus;
  analyzedAt: string;
  incidentCount: number;
  /** Free text, in English, with no code to translate. See C-9 in PRD 8.2. */
  warnings: string[];
}

export interface IncidentPage extends Page<Incident> {
  analysis?: AnalysisOutcome | null;
}

/**
 * One cell of the summary table, at whichever level it belongs to (FR-22).
 *
 * The same shape serves all three levels, and what a row is about is said by
 * which keys are present: a bucket row carries an event type and a scope, an
 * event type total carries only the type, the grand total carries neither. The
 * server omits them rather than sending nulls - captured from the response, not
 * assumed.
 *
 * `province` appears only for `SINGLE`. A `SHARED` row names no provinces at
 * all: it is one bucket per event type (ADR-036), and which provinces a figure
 * covers is read from the record itself.
 */
export interface SummaryRow {
  eventType?: string;
  provinceScope?: ProvinceScope;
  province?: Province;
  incidentCount: number;
  /** Keyed by catalog metric name. Empty when records carry no figures at all. */
  metrics: Record<string, number>;
}

/**
 * Three levels, from one query over one filtered set.
 *
 * `eventTypeTotals` and `total` are not conveniences to be recomputed here: with
 * a shared figure in play the bucket rows genuinely do not add up to the total
 * above them (ADR-019), and adding them up in the browser would produce a
 * different, wrong number - and hide exactly the thing this table exists to show.
 */
export interface Summary {
  rows: SummaryRow[];
  eventTypeTotals: SummaryRow[];
  total: SummaryRow;
}
