-- A firm's roles are created empty and the Super Admin grants permissions afterwards: that is the
-- documented, tested rule ("a freshly created firm's FIRM_ADMIN starts with 0 permissions"), and
-- FirmServiceImpl/FirmRoleServiceImpl enforce it. The consequence is that FIRM_CONFIG:* — added to
-- the platform later than these firms were created — was on the global template role only, so a
-- firm's own FIRM_ADMIN never received it.
--
-- The endpoint requires both the role and the permission:
--   @PreAuthorize("hasRole('FIRM_ADMIN')") + permissionEvaluator.require("FIRM_CONFIG:VIEW"/"EDIT")
-- so those firms got 403 on GET/PUT /api/v1/firm/config and could not white-label themselves.
--
-- Grant the FIRM_CONFIG permissions to every firm-scoped FIRM_ADMIN role that is missing them.
-- Idempotent: safe to re-run, and a no-op for firms whose admin already has them.

INSERT INTO role_permissions (id, uuid, role_id, permission_id, is_active, created_at, updated_at)
SELECT gen_random_uuid(), gen_random_uuid(), r.id, p.id, true, now(), now()
FROM roles r
CROSS JOIN permissions p
WHERE r.firm_id IS NOT NULL
  AND r.role_code = 'FIRM_ADMIN'
  AND p.code IN ('FIRM_CONFIG:ACCESS', 'FIRM_CONFIG:VIEW', 'FIRM_CONFIG:EDIT', 'FIRM_CONFIG:DELETE')
  AND NOT EXISTS (
      SELECT 1
      FROM role_permissions rp
      WHERE rp.role_id = r.id
        AND rp.permission_id = p.id
  );
