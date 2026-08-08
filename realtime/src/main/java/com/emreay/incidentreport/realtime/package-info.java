/**
 * Realtime module — one-way push of newly produced structured records.
 *
 * <p>Responsibilities: hold Server-Sent Events subscriptions and broadcast an event whenever the
 * analysis module produces new structured records, so clients can refresh tables and charts
 * without reloading the page.
 *
 * <p>Constraints (see {@code CLAUDE.md}):
 * <ul>
 *   <li>Owns no database. It is a transport layer, not a source of truth.</li>
 *   <li>The stream is server-to-client only; it accepts no inbound messages.</li>
 * </ul>
 */
package com.emreay.incidentreport.realtime;
