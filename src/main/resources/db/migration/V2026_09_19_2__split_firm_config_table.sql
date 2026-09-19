-- Migration: split per-firm settings out of system_config into firm_configs
--
-- Before: one table held both scopes, separated by a `scope` column with a nullable
-- `firm_id` (GLOBAL rows). After: `system_config` is platform settings only and
-- `firm_configs` holds one row per firm + key, with firm_id NOT NULL and a real FK.
--
-- Run the two column drops only after the copy below reports the same row count for
-- both tables.

-- 1. New table (mirrors the columns of system_config; no scope/firm_id ambiguity)
CREATE TABLE IF NOT EXISTS firm_configs (
    id             UUID         PRIMARY KEY,
    firm_id        UUID         NOT NULL,
    config_key     VARCHAR(50)  NOT NULL,
    config_value   TEXT,
    encrypted      BOOLEAN      NOT NULL DEFAULT FALSE,
    config_group   VARCHAR(50),
    input_type     VARCHAR(20)  NOT NULL DEFAULT 'TEXT',
    allowed_values TEXT,
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    allow_edit     BOOLEAN      NOT NULL DEFAULT TRUE,
    description    VARCHAR(200),
    created_at     TIMESTAMP,
    updated_at     TIMESTAMP,
    CONSTRAINT uq_firm_configs_firm_key UNIQUE (firm_id, config_key),
    CONSTRAINT fk_firm_configs_firm FOREIGN KEY (firm_id) REFERENCES firms(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_firm_configs_firm_id ON firm_configs (firm_id);

-- 2. Move existing per-firm rows across (idempotent: the unique constraint skips
--    anything already copied)
INSERT INTO firm_configs (id, firm_id, config_key, config_value, encrypted, config_group,
                          input_type, allowed_values, active, allow_edit, description,
                          created_at, updated_at)
SELECT id, firm_id, config_key, config_value, encrypted, config_group,
       COALESCE(input_type, 'TEXT'), allowed_values, COALESCE(active, TRUE),
       COALESCE(allow_edit, TRUE), description, created_at, updated_at
FROM system_config
WHERE scope = 'FIRM' AND firm_id IS NOT NULL
ON CONFLICT (firm_id, config_key) DO NOTHING;

-- 3. Verify before dropping (counts must match)
--   SELECT COUNT(*) FROM firm_configs;
--   SELECT COUNT(*) FROM system_config WHERE scope = 'FIRM';

-- 4. Clean system_config down to platform settings
DELETE FROM system_config WHERE scope = 'FIRM';

ALTER TABLE system_config DROP CONSTRAINT IF EXISTS uq_system_config_scope_firm_key;
DROP INDEX IF EXISTS uq_system_config_firm_key;
DROP INDEX IF EXISTS uq_system_config_global_key;

ALTER TABLE system_config DROP COLUMN IF EXISTS scope;
ALTER TABLE system_config DROP COLUMN IF EXISTS firm_id;

-- 5. GLOBAL keys are now unique on their own
CREATE UNIQUE INDEX IF NOT EXISTS uq_system_config_key ON system_config (config_key);
