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
  run_step "wp5 script syntax" \
    bash -n \
      "$ROOT_DIR/scripts/wp5_quality_gate.sh" \
      "$ROOT_DIR/scripts/wp5_test_design_smoke.sh" \
      "$ROOT_DIR/scripts/wp5_managed_http_smoke.sh"

  run_step "wp5 backend and OpenAPI tests" \
    mvn -B -pl platform-api -Dtest=TestDesignControllerTest,TestDesignAsyncGenerationControllerTest,TestDesignEventRecoveryServiceTest,TestDesignOpenApiContractTest,PermissionCodeUsageTest,ServiceTokenAuthenticationFilterTest test

  run_step "portal-web WP5 tests" \
    bash -lc "cd '$ROOT_DIR/portal-web' && npm run test -- api/testDesign.test.ts testDesign.test.ts testDesignQuality.test.ts testDesignPagination.test.ts testDesignSelection.test.ts testDesignBatchEdit.test.ts testDesignConfirmation.test.ts testDesignExport.test.ts testDesignIdempotency.test.ts permissions.test.ts"

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

  case "${WP5_RUN_HTTP_SMOKE:-0}" in
    1|true|TRUE)
      if [[ -n "${WP5_SMOKE_BASE_URL:-}" ]]; then
        run_step "wp5 http smoke" bash "$ROOT_DIR/scripts/wp5_test_design_smoke.sh"
      else
        run_step "wp5 managed http smoke" bash "$ROOT_DIR/scripts/wp5_managed_http_smoke.sh"
      fi
      ;;
    external)
      run_step "wp5 http smoke" bash "$ROOT_DIR/scripts/wp5_test_design_smoke.sh"
      ;;
    managed|auto)
      run_step "wp5 managed http smoke" bash "$ROOT_DIR/scripts/wp5_managed_http_smoke.sh"
      ;;
    0|false|FALSE|"")
      echo "== wp5 http smoke skipped; set WP5_RUN_HTTP_SMOKE=1 to run managed smoke, or WP5_RUN_HTTP_SMOKE=external with WP5_SMOKE_BASE_URL for an existing platform-api =="
      ;;
    *)
      echo "Unsupported WP5_RUN_HTTP_SMOKE=${WP5_RUN_HTTP_SMOKE}; use 1, managed, auto, external, or 0." >&2
      exit 2
      ;;
  esac

  echo "WP5 quality gate passed."
}

main "$@"
