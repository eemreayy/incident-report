/**
 * Ingestion module — the write side for raw incident reports.
 *
 * <p>Responsibilities: accept free-form text, validate it, persist it <em>verbatim</em>, read it
 * back, and trigger reprocessing. Publishes a domain event once a report has been stored.
 *
 * <p>Constraints (see {@code CLAUDE.md}):
 * <ul>
 *   <li>Owns MongoDB. Must never touch PostgreSQL.</li>
 *   <li>Raw text is immutable — no update, no delete.</li>
 *   <li>Does not depend on the {@code analysis} module — enforced by this module's pom.</li>
 * </ul>
 */
package com.emreay.incidentreport.ingestion;
