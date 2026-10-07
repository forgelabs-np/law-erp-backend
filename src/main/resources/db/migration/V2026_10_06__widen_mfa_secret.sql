-- Migration: widen users.mfa_secret for at-rest encryption
--
-- users.mfa_secret is now written through EncryptedStringConverter (AES-256-GCM, Base64).
-- A 32-char Base32 secret becomes ~80 Base64 characters, which does not fit the old varchar(64).
-- Hibernate ddl-auto=update may or may not widen the column on a database that already has the
-- table, so run this first — same convention as the other files in this directory.

ALTER TABLE users ALTER COLUMN mfa_secret TYPE varchar(255);
