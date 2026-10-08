-- Firm customization, phase 1: brand colors (hex, nullable because existing firms keep
-- the firm-config fallback) + a per-firm logo-toggling flag with a sensible default.
--
-- Hibernate ddl-auto=update cannot add a boolean with a non-standard default to an existing
-- table reliably, so run this first on databases created before this migration.

ALTER TABLE firms
    ADD COLUMN IF NOT EXISTS logo_allowed boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS brand_primary_hex character varying(7),
    ADD COLUMN IF NOT EXISTS brand_secondary_hex character varying(7);
