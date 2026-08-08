/**
 * Shared kernel — types that genuinely belong to more than one module.
 *
 * <p>Cross-module domain events and the common error contract live here. A type used by a single
 * module does not. Keep this package small: anything here is, by definition, something every
 * module may couple to, which makes it expensive to change.
 *
 * <p>Constraints (see {@code CLAUDE.md}):
 * <ul>
 *   <li>Depends on no other module — enforced by this module's pom.</li>
 *   <li>No persistence, no web layer, no framework-specific entities.</li>
 * </ul>
 */
package com.emreay.incidentreport.shared;
