# Delegation-Chain E2E Verification Log
Date: 2026-09-08 | Branch: devG | Suite: DelegationChainE2ETest (MockMvc + H2, real async taskExecutor)
Result: 6/6 steps PASSED — full backend suite 266/266 green.

Each numbered step below maps 1:1 to the operator guide (docs/super-admin-firm-admin-guide.md)
and was verified through real HTTP calls against the running application context.

STEP 0  SA registers, onboards firm CHAIN1 via POST /super-admin/firms;
        firm admin + paralegal authenticate. Verified: both role clones start with 0 permissions.
STEP 1  SA override PUT /super-admin/firms/{firmId}/roles/{roleId}/permissions
        grants the FIRM_ADMIN clone 10 permissions. Verified: 10 rows present.
STEP 2  SA read GET /super-admin/firms/{firmId}/roles returns the firm's roles with
        permissions + holder counts. Verified: >=2 roles visible to SA.
STEP 3  Firm Admin PUT /firm/roles/{roleId}/permissions gives PARALEGAL a 4-permission
        subset within their ceiling. Verified: 4 rows; ceiling payload drives checkbox UI.
STEP 4  John/Ron case: POST /firm/roles creates custom role SENIOR_PARALEGAL
        anchored on the PARALEGAL system template. Verified: nonexistent anchor -> 404.
STEP 5  SA narrows FIRM_ADMIN template (removes USER_MANAGEMENT:VIEW):
        GET .../preview shows delta first; PUT returns syncJobId; job polled to COMPLETED
        (real async run on taskExecutor). Verified cascade: firm admin clone narrowed,
        employee clone holds nothing the admin lost, template itself stripped.
STEP 6  Chain validation (both directions verified):
        - PARALEGAL template add within FIRM_ADMIN template -> accepted (200)
        - FIRM_ADMIN template narrowed below PARALEGAL template -> REJECTED (400),
          error names the employee template and offending codes.

Notes captured while testing (frontend-relevant):
- First attempt to narrow with BILLING:VIEW was correctly REJECTED with 400: the ADVOCATE
  template holds BILLING:VIEW (READ_ONLY) so removing it from FIRM_ADMIN violates the chain.
  This is the invariant working, not a bug. Use USER_MANAGEMENT:VIEW for legal narrowings.
- permissionVersion bumps force re-login of affected users (HTTP 401 on next call).
  Frontend must re-authenticate after role/permission changes — including the firm admin
  token after an SA override targeting the admin's own role.
- Firm-role endpoints (existing contract) require "roleId" inside the JSON body even though
  the path variable wins; template endpoints take only { "permissionIds": [...] }.
