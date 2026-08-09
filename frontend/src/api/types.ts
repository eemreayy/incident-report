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
