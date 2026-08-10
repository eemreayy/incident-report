package com.emreay.incidentreport.realtime.event;

import com.emreay.incidentreport.realtime.service.IncidentStream;
import com.emreay.incidentreport.realtime.web.IncidentSignalMessage;
import com.emreay.incidentreport.shared.event.IncidentRecordsProducedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Where a finished analysis becomes a message on the stream.
 *
 * <p>This module learns that records exist because an event said so. It never queries the module
 * that produced them, and it has nothing to say back — the edge is one-way, and the payload is a
 * plain record from {@code shared}, so this listener would work unchanged if the event ever arrived
 * from somewhere other than the same JVM.
 *
 * <p><strong>After the commit, not before.</strong> Analysis publishes from inside its transaction,
 * and telling clients at that moment would be telling them to look at rows PostgreSQL has not made
 * visible yet: the refetch would race the commit, return the previous state, and — because the
 * stream sends nothing twice — leave that client stale until the next unrelated submission. Waiting
 * for the commit also means a rolled-back analysis announces nothing, which is the honest outcome:
 * nothing changed.
 *
 * <p>Still synchronous and still on the submitting request's thread (ADR-003) — the phase moves
 * <em>when</em> this runs, not where.
 */
@Component
public class IncidentRecordsProducedEventListener {

    private static final Logger log = LoggerFactory.getLogger(IncidentRecordsProducedEventListener.class);

    private final IncidentStream stream;

    public IncidentRecordsProducedEventListener(IncidentStream stream) {
        this.stream = stream;
    }

    /**
     * A broadcast that fails must not fail the submission behind it. The text is stored, the records
     * are committed, and every client can still read both by querying; losing a refresh trigger is
     * the smallest possible consequence, and turning it into a 500 for the submitter would be the
     * largest.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIncidentRecordsProduced(IncidentRecordsProducedEvent event) {
        try {
            stream.broadcast(IncidentSignalMessage.from(event));
        } catch (RuntimeException failure) {
            log.error("could not signal raw report {} to connected clients; they will see the "
                    + "records on their next query", event.rawReportId(), failure);
        }
    }
}
