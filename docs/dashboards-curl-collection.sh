#!/usr/bin/env bash
# =============================================================================
# LAW ERP — Dashboards curl collection
# Case Dashboard + Case Assignments + Global Dashboard
#
# Usage:
#   Edit the CONFIG block below, then run:  bash docs/dashboards-curl-collection.sh
#   Or copy individual curl commands into your terminal.
#
# Server: http://localhost:6969   Base path: /api/v1
# Response envelope: { "success", "responseCode", "message", "data" }
#
# Roles:
#   Case Dashboard  -> FIRM_ADMIN (all matters) | ADVOCATE / PARALEGAL (assigned only)
#   Global Dashboard-> SUPER_ADMIN (all firms)  | FIRM_ADMIN (own firm only)
# =============================================================================

set -euo pipefail

# -----------------------------------------------------------------------------
# CONFIG — edit these
# -----------------------------------------------------------------------------
BASE="http://localhost:6969"
LAW_FIRM_CODE="APEX-LAW"
USERNAME="firmadmin"
PASSWORD="YourPassword123"
SUPER_USERNAME="superadmin"
SUPER_PASSWORD="YourSuperPassword123"
MATTER_NUMBER="APX-MAT-2026-00001"
USER_ID=""            # UUID of the employee to assign (from employee list)
NEW_PASSWORD="NewPass123"
TOTP_CODE="123456"    # 6-digit code from your authenticator app
# -----------------------------------------------------------------------------

echo "== 0. LOGIN (internal user) =="
LOGIN=$(curl -s -X POST "$BASE/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"data\":{\"lawFirmCode\":\"$LAW_FIRM_CODE\",\"username\":\"$USERNAME\",\"password\":\"$PASSWORD\"}}")
echo "$LOGIN" | head -c 400; echo

# Login response status drives the next step:
#   SUCCESS                  -> store accessToken
#   PASSWORD_CHANGE_REQUIRED -> POST /auth/change-password with passwordChangeToken
#   MFA_SETUP_REQUIRED       -> POST /auth/mfa/setup/confirm with mfaToken + TOTP
#   MFA_REQUIRED             -> POST /auth/mfa/validate with mfaToken + TOTP

echo "== 1. CHANGE PASSWORD (first login only) =="
PCT=$(echo "$LOGIN" | sed -n 's/.*"passwordChangeToken":"\([^"]*\)".*/\1/p')
if [ -n "$PCT" ]; then
  curl -s -X POST "$BASE/api/v1/auth/change-password" \
    -H "Content-Type: application/json" \
    -d "{\"data\":{\"passwordChangeToken\":\"$PCT\",\"newPassword\":\"$NEW_PASSWORD\",\"confirmPassword\":\"$NEW_PASSWORD\"}}" \
    | head -c 300; echo
fi

echo "== 2. VALIDATE MFA (TOTP) =="
MFA=$(echo "$LOGIN" | sed -n 's/.*"mfaToken":"\([^"]*\)".*/\1/p')
if [ -n "$MFA" ]; then
  LOGIN=$(curl -s -X POST "$BASE/api/v1/auth/mfa/validate" \
    -H "Content-Type: application/json" \
    -d "{\"data\":{\"mfaToken\":\"$MFA\",\"totpCode\":\"$TOTP_CODE\"}}")
  echo "$LOGIN" | head -c 300; echo
fi

TOKEN=$(echo "$LOGIN" | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')
echo "accessToken: ${TOKEN:0:24}..."

AUTH="Authorization: Bearer $TOKEN"

# -----------------------------------------------------------------------------
echo "== 3. CASE DASHBOARD (role-filtered) =="
curl -s "$BASE/api/v1/firm/dashboard" -H "$AUTH" | head -c 1200; echo

echo "== 4. CREATE MATTER (feeds dashboard) =="
curl -s -X POST "$BASE/api/v1/firm/matters" \
  -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"data":{"matterType":"LITIGATION","title":"Contract dispute - ACME vs Beta","originatingCourtLevel":"DISTRICT","courtName":"Kathmandu District Court","courtCaseNumber":"081-C1-7530","filingDate":"2083-04-29","description":"Breach of contract"}}' \
  | head -c 500; echo

echo "== 5. ASSIGN EMPLOYEE TO MATTER =="
curl -s -X POST "$BASE/api/v1/firm/matters/$MATTER_NUMBER/assignments" \
  -H "$AUTH" -H "Content-Type: application/json" \
  -d "{\"data\":{\"userId\":\"$USER_ID\",\"assignmentRole\":\"PRIMARY_ADVOCATE\"}}" \
  | head -c 500; echo

echo "== 6. LIST ASSIGNMENTS FOR MATTER =="
curl -s "$BASE/api/v1/firm/matters/$MATTER_NUMBER/assignments" -H "$AUTH" | head -c 600; echo

echo "== 7. REVOKE ASSIGNMENT =="
curl -s -X DELETE "$BASE/api/v1/firm/matters/$MATTER_NUMBER/assignments/$USER_ID" -H "$AUTH" | head -c 300; echo

# -----------------------------------------------------------------------------
echo "== 8. GLOBAL DASHBOARD (SUPER_ADMIN = all firms / FIRM_ADMIN = own firm) =="
curl -s "$BASE/api/v1/modules/dashboard" -H "$AUTH" | head -c 1200; echo

# -----------------------------------------------------------------------------
echo "== 9. SUPER ADMIN LOGIN + GLOBAL DASHBOARD =="
SA_LOGIN=$(curl -s -X POST "$BASE/api/v1/super-admin/login" \
  -H "Content-Type: application/json" \
  -d "{\"data\":{\"username\":\"$SUPER_USERNAME\",\"password\":\"$SUPER_PASSWORD\"}}")
SA_MFA=$(echo "$SA_LOGIN" | sed -n 's/.*"mfaToken":"\([^"]*\)".*/\1/p')
if [ -n "$SA_MFA" ]; then
  SA_LOGIN=$(curl -s -X POST "$BASE/api/v1/auth/mfa/validate" \
    -H "Content-Type: application/json" \
    -d "{\"data\":{\"mfaToken\":\"$SA_MFA\",\"totpCode\":\"$TOTP_CODE\"}}")
fi
SA_TOKEN=$(echo "$SA_LOGIN" | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')
curl -s "$BASE/api/v1/modules/dashboard" -H "Authorization: Bearer $SA_TOKEN" | head -c 1200; echo

echo "== done =="
