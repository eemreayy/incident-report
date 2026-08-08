/**
 * Analysis module — extraction, classification and the analytical read side.
 *
 * <p>Responsibilities: listen for stored raw reports, extract date, province, event type, numeric
 * metrics and keywords from the text, persist the structured result, and serve filtered listings
 * and aggregations.
 *
 * <p>Constraints (see {@code CLAUDE.md}):
 * <ul>
 *   <li>Owns PostgreSQL. Must never touch MongoDB.</li>
 *   <li>Reacts to ingestion events synchronously; does not depend on the {@code ingestion}
 *       module — enforced by this module's pom.</li>
 *   <li>The event type catalog is configuration, never hardcoded.</li>
 * </ul>
 */
package com.emreay.incidentreport.analysis;
