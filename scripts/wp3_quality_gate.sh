#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

run_step() {
  local name="$1"
  shift
  echo "== $name =="
  "$@"
}

main() {
  run_step "wp3 backend and OpenAPI tests" mvn -B -pl platform-api -Dtest=AssetControllerTest,AssetContextAuditContractTest,AssetOpenApiContractTest test

  run_step "portal-web asset tests" bash -lc "cd '$ROOT_DIR/portal-web' && npm run test -- assets.test.ts permissions.test.ts"

  if [[ "${WP3_SKIP_DB_VALIDATION:-0}" != "1" ]]; then
    run_step "wp3 database validation via consolidated WP validation" bash "$ROOT_DIR/db/validation/run_wp1_db_validation.sh"
  else
    echo "== wp3 database validation skipped by WP3_SKIP_DB_VALIDATION=1 =="
  fi

  if [[ "${WP3_RUN_HTTP_SMOKE:-0}" == "1" ]]; then
    run_step "wp3 http smoke" bash "$ROOT_DIR/scripts/wp3_asset_smoke.sh"
  else
    echo "== wp3 http smoke skipped; set WP3_RUN_HTTP_SMOKE=1 when platform-api is running =="
  fi

  echo "WP3 quality gate passed."
}

main "$@"
