-- Migration: Add FIRM to users.user_type CHECK constraint
-- FIRM = firm owner/admin, distinct from FIRM_USER = employees

-- 1. Drop old constraint and add new one with FIRM included
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_user_type_check;

ALTER TABLE users ADD CONSTRAINT users_user_type_check CHECK (
    user_type IN ('SUPER_ADMIN', 'FIRM', 'FIRM_USER', 'CLIENT')
);

-- 2. Migrate existing firm admin users from FIRM_USER -> FIRM
--    Firm admins are identified by having the FIRM_ADMIN role on a firm-scoped role
UPDATE users u
SET user_type = 'FIRM'
WHERE u.user_type = 'FIRM_USER'
  AND u.role_id IN (
      SELECT r.id FROM roles r
      WHERE r.role_code = 'FIRM_ADMIN'
        AND r.firm_id IS NOT NULL
  );

-- 3. Update the system FIRM_ADMIN role's applicable_to to FIRM
UPDATE roles
SET applicable_to = 'FIRM'
WHERE role_code = 'FIRM_ADMIN'
  AND firm_id IS NULL
  AND is_system = true;
