#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${SAFEOPS_BASE_URL:-http://127.0.0.1:8088}"
ACTOR_ID="${SAFEOPS_ACTOR_ID:-linux-smoke-approver}"
ACTOR_ROLES="${SAFEOPS_ACTOR_ROLES:-APPROVER,EXECUTOR}"

echo "[smoke] checking actuator health at ${BASE_URL}"
curl --fail --silent "${BASE_URL}/actuator/health" | grep -q '"status":"UP"'

echo "[smoke] checking audit trace list"
curl --fail --silent "${BASE_URL}/api/audit/traces" > /tmp/safeops-audit-traces.json

echo "[smoke] checking missing trace integrity endpoint"
curl --fail --silent "${BASE_URL}/api/audit/traces/linux-smoke-missing/integrity" | grep -q '"found":false'

echo "[smoke] checking approvals API rejects missing approval with structured error"
curl --silent --output /tmp/safeops-approval-error.json --write-out "%{http_code}" \
  --header "Content-Type: application/json" \
  --header "X-Actor-Id: ${ACTOR_ID}" \
  --header "X-Actor-Roles: ${ACTOR_ROLES}" \
  --data '{"approvalId":"missing-approval"}' \
  "${BASE_URL}/api/approvals/approve" | grep -q '404'
grep -q '"code":"RESOURCE_NOT_FOUND"' /tmp/safeops-approval-error.json

echo "[smoke] ok"
