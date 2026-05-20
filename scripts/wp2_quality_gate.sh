#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

run_step() {
  local name="$1"
  shift
  echo "== $name =="
  "$@"
}

run_step "wp2 script syntax" bash -n \
  "$ROOT_DIR/scripts/wp2_quality_gate.sh" \
  "$ROOT_DIR/scripts/wp2_model_access_smoke.sh" \
  "$ROOT_DIR/scripts/wp2_module_policy_smoke.sh"

run_step "platform-api tests" mvn -B -pl platform-api test

run_step "portal-web model access tests" bash -lc "cd '$ROOT_DIR/portal-web' && npm run test -- auth.test.ts bootstrap.test.ts modelAccess.test.ts permissions.test.ts"

run_step "portal-web build" bash -lc "cd '$ROOT_DIR/portal-web' && npm run build"

run_step "wp2 database validation" bash "$ROOT_DIR/db/validation/run_wp2_db_validation.sh"

if [[ "${WP2_RUN_HTTP_SMOKE:-0}" == "1" ]]; then
  run_step "unified platform-api http smoke" bash "$ROOT_DIR/scripts/wp_all_integration_test.sh"
else
  echo "== http smoke skipped; set WP2_RUN_HTTP_SMOKE=1 when platform-api is running =="
fi

if [[ "${WP2_RUN_POLICY_SMOKE:-0}" == "1" ]]; then
  run_step "wp2 module policy smoke" bash "$ROOT_DIR/scripts/wp2_module_policy_smoke.sh"
else
  echo "== policy smoke skipped; set WP2_RUN_POLICY_SMOKE=1 when platform-api is running =="
fi

echo "WP2 quality gate passed."
