-- Migration: Add CREDENTIAL_VIEW and CREDENTIAL_REVEAL to permissions.action CHECK constraint
-- Run this BEFORE starting the application after adding the new PermissionAction enum values.

-- For PostgreSQL: drop the old CHECK constraint and add a new one with the extra values
ALTER TABLE permissions DROP CONSTRAINT IF EXISTS permissions_action_check;

ALTER TABLE permissions ADD CONSTRAINT permissions_action_check CHECK (
    action IN (
        'VIEW', 'CREATE', 'EDIT', 'DELETE', 'ACCESS',
        'UPLOAD', 'DOWNLOAD', 'SHARE', 'EXPORT',
        'SCHEDULE', 'UPDATE_STATUS', 'ASSIGN',
        'APPROVE', 'REJECT', 'REVIEW',
        'ARCHIVE', 'RESTORE', 'PRINT', 'FORWARD',
        'CREDENTIAL_VIEW', 'CREDENTIAL_REVEAL'
    )
);

-- Also add PROJECT, CREDENTIAL, RENEWAL to audit_entity CHECK if it exists
ALTER TABLE audit_logs DROP CONSTRAINT IF EXISTS audit_logs_entity_type_check;
