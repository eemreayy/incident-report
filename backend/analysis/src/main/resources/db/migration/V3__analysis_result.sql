-- How each raw report was read (ADR-021).
--
-- This used to live on the MongoDB document, which meant the ingestion module storing and
-- publishing data produced by the analysis module, and a raw record that kept being written to
-- after it was inserted. Both are now gone: the raw document is write-once, and this table is the
-- one place that answers "how did reading that text go".

create table analysis_result (
    id             bigint generated always as identity primary key,

    -- MongoDB ObjectId of the report this describes. Unique rather than merely indexed:
    -- reprocessing overwrites this row instead of adding a second one, because "what does the
    -- system currently know about this text" has exactly one current answer (FR-15).
    raw_report_id  varchar(24)  not null unique,

    status         varchar(16)  not null
        constraint analysis_result_status_valid
        check (status in ('ANALYZED', 'FAILED')),

    analyzed_at    timestamptz  not null,

    -- Structured records produced. Zero is a legitimate answer, not an error.
    incident_count integer      not null default 0
        constraint analysis_result_incident_count_valid check (incident_count >= 0),

    -- Exception type and message, for whoever operates the system. Never mapped into a response:
    -- the same rule that keeps stack traces out of the error contract.
    failure_reason varchar(1024),

    -- A failure explains itself; a success has nothing to explain.
    constraint analysis_result_failure_has_reason check (
        (status = 'FAILED' and failure_reason is not null)
        or (status = 'ANALYZED' and failure_reason is null)
    )
);

create index analysis_result_status_idx on analysis_result (status);

-- What the reader could not do, in the words shown to the user (FR-09). A child table rather than
-- an array column, for the same reason metrics are rows: it stays queryable without casting.
create table analysis_warning (
    analysis_result_id bigint       not null references analysis_result (id) on delete cascade,
    warning            varchar(512) not null
);

create index analysis_warning_result_idx on analysis_warning (analysis_result_id);
