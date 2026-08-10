package com.emreay.incidentreport.ingestion.repository;

import com.emreay.incidentreport.ingestion.domain.RawIncidentReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

/**
 * Creates the indexes this collection needs, at startup.
 *
 * <p>Explicit rather than Spring Data's {@code auto-index-creation}, for the same reason PostgreSQL
 * has migrations instead of {@code ddl-auto: update}: the index this module depends on is a
 * correctness guarantee, not a convenience, and it should be created by something you can read and
 * name rather than as a side effect of a global switch in another module's configuration.
 *
 * <p>The unique index on the text digest is what makes repeated submissions safe under
 * concurrency (ADR-035). Looking the text up before inserting handles the ordinary case — a user
 * pressing submit twice — but two identical requests arriving together would both find nothing and
 * both insert. The index turns that race into a duplicate-key error the service can answer, instead
 * of a second report and a double-counted casualty figure.
 *
 * <p>It is <strong>sparse</strong>: reports written before the digest existed have no such field,
 * and MongoDB would otherwise read them all as sharing the value {@code null} and refuse to build
 * the index at all. Every report written from now on has one, so the exemption applies to history
 * only — those older texts simply do not participate in duplicate detection.
 */
@Component
class RawIncidentReportIndexes {

    private static final Logger log = LoggerFactory.getLogger(RawIncidentReportIndexes.class);

    RawIncidentReportIndexes(MongoOperations mongo) {
        mongo.indexOps(RawIncidentReport.class)
                .ensureIndex(new Index().on("textHash", Sort.Direction.ASC).unique().sparse());

        log.debug("ensured the unique index on raw_incident_reports.textHash");
    }
}
