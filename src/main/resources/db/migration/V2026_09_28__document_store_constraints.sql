-- Migration: document store constraints
--
-- Run this BEFORE starting the application on an environment that already has the documents
-- table (Hibernate ddl-auto=update creates the table but not these check constraints).
--
-- Two things:
--   1. A document belongs to exactly one owner: a case (matter_id) or a project (project_id).
--   2. matter_timeline.event_type gained DOCUMENT_UPLOADED. Hibernate generates a CHECK
--      constraint for enum columns, so an existing constraint would reject the new value —
--      the same trap that needed a migration when permissions.action gained new values.

-- 1. Exactly one owner per document.
--    (boolean <> boolean is XOR in PostgreSQL: true only when the two differ)
ALTER TABLE documents DROP CONSTRAINT IF EXISTS documents_owner_xor_check;
ALTER TABLE documents ADD CONSTRAINT documents_owner_xor_check
    CHECK ((matter_id IS NOT NULL) <> (project_id IS NOT NULL));

-- 2. Drop any existing CHECK on matter_timeline.event_type, whatever Hibernate named it,
--    then re-add the complete list including the new value.
DO $$
DECLARE
    existing_constraint RECORD;
BEGIN
    FOR existing_constraint IN
        SELECT conname
        FROM pg_constraint
        WHERE conrelid = 'matter_timeline'::regclass
          AND contype = 'c'
          AND pg_get_constraintdef(oid) LIKE '%event_type%'
    LOOP
        EXECUTE 'ALTER TABLE matter_timeline DROP CONSTRAINT ' || quote_ident(existing_constraint.conname);
    END LOOP;
END $$;

ALTER TABLE matter_timeline ADD CONSTRAINT matter_timeline_event_type_check CHECK (
    event_type IN (
        'MATTER_CREATED',
        'COURT_CASE_ADDED',
        'STAGE_CHANGE',
        'MEDIATION_FAILED',
        'MEDIATION_SUCCEEDED',
        'COURT_EVENT_SCHEDULED',
        'COURT_EVENT_HELD',
        'COURT_EVENT_ADJOURNED',
        'COURT_EVENT_CANCELLED',
        'PARTY_ADDED',
        'JUDGMENT_RECORDED',
        'APPEAL_FILED',
        'MATTER_NOTE_ADDED',
        'DOCUMENT_UPLOADED'
    )
);

-- Verify:
--   SELECT COUNT(*) FROM documents WHERE (matter_id IS NOT NULL) = (project_id IS NOT NULL);
--   -- must be 0: no document with both owners or neither
