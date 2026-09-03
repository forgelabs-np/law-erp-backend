-- Migration: settings metadata for system_config (grouped, typed, DB-driven settings registry)
-- Adds: config_group, input_type, allowed_values, active, allow_edit

ALTER TABLE system_config ADD COLUMN config_group VARCHAR(50);
ALTER TABLE system_config ADD COLUMN input_type VARCHAR(20) NOT NULL DEFAULT 'TEXT';
ALTER TABLE system_config ADD COLUMN allowed_values TEXT;
ALTER TABLE system_config ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE system_config ADD COLUMN allow_edit BOOLEAN NOT NULL DEFAULT TRUE;

-- ── Backfill metadata for existing keys ──────────────────────────────────

-- EMAIL group (GLOBAL SMTP config)
UPDATE system_config
SET input_type = 'PASSWORD', config_group = 'EMAIL'
WHERE config_key = 'SMTP_PASSWORD';

UPDATE system_config
SET input_type = 'NUMBER', config_group = 'EMAIL'
WHERE config_key = 'SMTP_PORT';

UPDATE system_config
SET input_type = 'TEXT', config_group = 'EMAIL'
WHERE config_key IN ('SMTP_HOST', 'SMTP_USERNAME', 'SMTP_FROM_NAME', 'SMTP_FROM_ADDRESS');

-- APP group (GLOBAL)
UPDATE system_config
SET input_type = 'RADIO', allowed_values = 'Y,N', config_group = 'APP'
WHERE config_key = 'APP_PRODUCTION';

UPDATE system_config
SET input_type = 'TEXT', config_group = 'APP'
WHERE config_key = 'APP_NAME';

-- BRAND group (FIRM scope branding/email footer)
UPDATE system_config
SET input_type = 'TEXT', config_group = 'BRAND'
WHERE config_key IN ('BRAND_COLOR_PRIMARY', 'BRAND_COLOR_SECONDARY',
                     'EMAIL_FOOTER_TEXT', 'EMAIL_SIGNATURE', 'TIMEZONE');
