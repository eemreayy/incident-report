package com.emreay.incidentreport.analysis.catalog;

import java.util.List;

/**
 * Thrown when the catalog cannot be trusted, which stops the application from starting.
 *
 * <p>Starting anyway would be worse than not starting: the system would recognise less than it is
 * configured to, silently, and the reports it failed to classify would look like texts the catalog
 * genuinely does not cover.
 *
 * <p>Reports every problem it found rather than the first, so a broken catalog is fixed in one pass
 * instead of one restart per mistake.
 */
public class InvalidCatalogException extends RuntimeException {

    public InvalidCatalogException(String source, List<String> problems) {
        super("Event type catalog at " + source + " is not usable:\n  - "
                + String.join("\n  - ", problems));
    }

    public InvalidCatalogException(String source, Throwable cause) {
        super("Event type catalog at " + source + " could not be read: " + cause.getMessage(), cause);
    }
}
