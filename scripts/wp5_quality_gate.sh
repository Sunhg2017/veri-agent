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
  run_step "wp5 backend and OpenAPI tests" \
    mvn -B -pl platform-api -Dtest=TestDesignControllerTest,TestDesignOpenApiContractTest,PermissionCodeUsageTest,ServiceTokenAuthenticationFilterTest test

  run_step "portal-web WP5 tests" \
    bash -lc "cd '$ROOT_DIR/portal-web' && npm run test -- testDesign.test.ts permissions.test.ts"

  run_step "portal-web build" \
    bash -lc "cd '$ROOT_DIR/portal-web' && npm run build"

  if [[ "${WP5_SKIP_DB_VALIDATION:-0}" != "1" ]]; then
    run_step "wp5 database validation via consolidated WP validation" \
      bash "$ROOT_DIR/db/validation/run_wp1_db_validation.sh"
  else
    echo "== wp5 database validation skipped by WP5_SKIP_DB_VALIDATION=1 =="
  fi

  if [[ "${WP5_RUN_AI_EVAL:-0}" == "1" ]]; then
    run_step "wp5 case generation quality eval" bash "$ROOT_DIR/scripts/wp5_case_generation_quality_eval.sh"
  else
    echo "== wp5 case generation quality eval skipped; set WP5_RUN_AI_EVAL=1 to run baseline corpus =="
  fi

  if [[ "${WP5_RUN_HTTP_SMOKE:-0}" == "1" ]]; then
    run_step "wp5 http smoke" bash "$ROOT_DIR/scripts/wp5_test_design_smoke.sh"
  else
    echo "== wp5 http smoke skipped; set WP5_RUN_HTTP_SMOKE=1 when platform-api is running =="
  fi

  echo "WP5 quality gate passed."
}

main "$@"
