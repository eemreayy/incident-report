-- Baseline schema for the analytical side (ADR-002: PostgreSQL holds structured data only).
--
-- Grain of `incident` is (raw report, date, province, event type) - ADR-019. The purpose of the
-- system is tracking incidents over time and by geographic region, so date and province are part
-- of the record's identity rather than attributes buried inside it.

-- ---------------------------------------------------------------------------
-- Reference data
-- ---------------------------------------------------------------------------

-- The 81 provinces of Turkey, keyed by their licence plate code. A lookup table rather than a
-- free-text column: it gives referential integrity, keeps the display name in one place, and
-- serves the metadata endpoint (FR-16).
create table province (
    code smallint     primary key,
    name varchar(32)  not null unique
);

-- ---------------------------------------------------------------------------
-- Incidents
-- ---------------------------------------------------------------------------

create table incident (
    id             bigint generated always as identity primary key,

    -- MongoDB ObjectId of the raw report this was derived from, as a 24-character hex string.
    -- One half of the two-way traceability required by FR-08; the other half is a query on this
    -- column. Deliberately not a foreign key - the two databases are separate stores (ADR-002).
    raw_report_id  varchar(24)  not null,

    occurred_on    date         not null,

    -- How the date was resolved (ADR-014). A relative phrase such as "son 24 saatte" is an
    -- extraction, not a fallback, and the difference must stay visible to the reader.
    date_source    varchar(16)  not null
        constraint incident_date_source_valid
        check (date_source in ('EXPLICIT', 'RELATIVE', 'DEFAULTED')),

    -- SINGLE  : the numbers belong to one named province
    -- SHARED  : the text gives a total across several provinces without splitting it
    --           ("her iki ilde toplam 10 kişi") - covered provinces are in incident_shared_province
    -- UNKNOWN : no province appears in the text at all
    province_scope varchar(16)  not null
        constraint incident_province_scope_valid
        check (province_scope in ('SINGLE', 'SHARED', 'UNKNOWN')),

    province_code  smallint     references province (code),

    -- Catalog keys, not database enums: the event type catalog is configuration and must grow
    -- without a migration (ADR-007).
    event_type     varchar(48)  not null,

    -- An unrecognised event type is stored rather than rejected (ADR-006), and must stay
    -- queryable so that gaps in the catalog are measurable.
    classification varchar(16)  not null
        constraint incident_classification_valid
        check (classification in ('CLASSIFIED', 'UNCLASSIFIED')),

    created_at     timestamptz  not null default now(),

    -- Makes the model self-enforcing: a province may only be attached when the numbers actually
    -- belong to it. Without this, a SHARED row could silently carry one province and be counted
    -- as that province's own figure.
    constraint incident_province_matches_scope check (
        (province_scope = 'SINGLE' and province_code is not null)
        or (province_scope in ('SHARED', 'UNKNOWN') and province_code is null)
    )
);

-- raw_report_id drives both traceability lookups and reprocessing, which deletes and rebuilds
-- every row derived from one report (FR-15).
create index incident_raw_report_id_idx on incident (raw_report_id);
create index incident_occurred_on_idx   on incident (occurred_on);
create index incident_event_type_idx    on incident (event_type);
create index incident_province_code_idx on incident (province_code);

-- ---------------------------------------------------------------------------
-- Metrics
-- ---------------------------------------------------------------------------

-- One row per extracted number (ADR-020). A column per metric would need a migration every time
-- the catalog grows, which contradicts ADR-007; this shape lets time series and cumulative
-- queries stay plain GROUP BY / SUM.
create table incident_metric (
    id          bigint generated always as identity primary key,
    incident_id bigint      not null references incident (id) on delete cascade,
    metric_type varchar(48) not null,
    metric_value integer    not null,

    -- A metric can only be extracted once per incident; a second value would mean the extraction
    -- produced two answers for the same question.
    constraint incident_metric_unique_per_incident unique (incident_id, metric_type)
);

create index incident_metric_type_idx on incident_metric (metric_type);

-- ---------------------------------------------------------------------------
-- Shared province coverage
-- ---------------------------------------------------------------------------

-- Which provinces a SHARED incident spans. This records coverage, never allocation: the number is
-- not split across these provinces and must never be added to any single province's total. It
-- exists so a province-filtered view can say "there is also a figure shared with Kocaeli" instead
-- of silently omitting it.
--
-- When several provinces are selected at once, a shared row must be counted once - hence queries
-- join through here with DISTINCT rather than summing per province.
create table incident_shared_province (
    incident_id   bigint   not null references incident (id) on delete cascade,
    province_code smallint not null references province (code),
    primary key (incident_id, province_code)
);

create index incident_shared_province_code_idx on incident_shared_province (province_code);

-- ---------------------------------------------------------------------------
-- Keywords
-- ---------------------------------------------------------------------------

-- The words that triggered each extraction, kept so the user can see why the system decided what
-- it decided and can filter on them (FR-17). Character offsets refer to the raw text in MongoDB.
create table incident_keyword (
    id          bigint generated always as identity primary key,
    incident_id bigint       not null references incident (id) on delete cascade,
    keyword     varchar(128) not null,
    keyword_role varchar(24) not null
        constraint incident_keyword_role_valid
        check (keyword_role in ('EVENT_TYPE', 'METRIC', 'PROVINCE', 'DATE')),
    char_start  integer,
    char_end    integer
);

create index incident_keyword_keyword_idx on incident_keyword (keyword);
