#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

is_truthy() {
  case "${1:-}" in
    1|true|TRUE|yes|YES|on|ON)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

is_release_gate() {
  if is_truthy "${WP5_RELEASE_GATE:-0}"; then
    return 0
  fi
  case "${WP5_GATE_MODE:-development}" in
    release|RELEASE|preprod|PREPROD|prod|PROD|production|PRODUCTION)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

is_plan_only() {
  is_truthy "${WP5_QUALITY_GATE_PLAN_ONLY:-0}"
}

http_smoke_requested() {
  case "${WP5_RUN_HTTP_SMOKE:-0}" in
    1|true|TRUE|managed|auto|external)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

ai_eval_requested() {
  is_truthy "${WP5_RUN_AI_EVAL:-0}"
}

validate_release_gate() {
  if ! is_release_gate; then
    return
  fi
  if ! http_smoke_requested; then
    echo "WP5 release gate requires HTTP smoke. Set WP5_RUN_HTTP_SMOKE=1 for managed smoke, or WP5_RUN_HTTP_SMOKE=external with WP5_SMOKE_BASE_URL for an existing platform-api." >&2
    exit 2
  fi
  if [[ "${WP5_RUN_HTTP_SMOKE:-0}" == "external" && -z "${WP5_SMOKE_BASE_URL:-}" ]]; then
    echo "WP5 release gate external HTTP smoke requires WP5_SMOKE_BASE_URL." >&2
    exit 2
  fi
  if ! ai_eval_requested; then
    echo "WP5 release gate requires AI quality evaluation. Set WP5_RUN_AI_EVAL=1 to run the WP5 golden set baseline." >&2
    exit 2
  fi
  echo "== wp5 release gate mode: HTTP smoke (${WP5_RUN_HTTP_SMOKE}) and AI quality evaluation required =="
}

run_step() {
  local name="$1"
  shift
  echo "== $name =="
  if is_plan_only; then
    printf 'PLAN'
    printf ' %q' "$@"
    printf '\n'
    return
  fi
  "$@"
}

check_script_syntax() {
  local script
  for script in "$@"; do
    bash -n "$script"
  done
}

main() {
  validate_release_gate

  run_step "wp5 script syntax" \
    check_script_syntax \
      "$ROOT_DIR/scripts/wp5_quality_gate.sh" \
      "$ROOT_DIR/scripts/wp5_test_design_smoke.sh" \
      "$ROOT_DIR/scripts/wp5_managed_http_smoke.sh" \
      "$ROOT_DIR/scripts/wp5_quality_gate_mode_test.sh"

  run_step "wp5 quality gate mode contract" \
    bash "$ROOT_DIR/scripts/wp5_quality_gate_mode_test.sh"

  run_step "wp5 backend and OpenAPI tests" \
    mvn -B -pl platform-api -Dtest=TestDesignControllerTest,TestDesignTaskReportServiceTest,TestDesignGenerationServiceTest,TestDesignAsyncGenerationControllerTest,TestDesignModelGenerationControllerTest,TestDesignModelFallbackControllerTest,TestDesignEventRecoveryServiceTest,TestDesignPublishCompensationServiceTest,TestDesignOpenApiContractTest,PermissionCodeUsageTest,ServiceTokenAuthenticationFilterTest test

  run_step "portal-web WP5 tests" \
    bash -lc "cd '$ROOT_DIR/portal-web' && npm run test -- api/testDesign.test.ts testDesignQuality.test.ts testDesignQualitySummary.test.ts testDesignPromptTrend.test.ts testDesignAuditSummary.test.ts testDesignReviewSummary.test.ts testDesignPagination.test.ts testDesignSelection.test.ts testDesignBatchEdit.test.ts testDesignConfirmation.test.ts testDesignExport.test.ts testDesignIdempotency.test.ts testDesignTaskDiagnostics.test.ts testDesignGenerationSource.test.ts testDesignContextPolicy.test.ts permissions.test.ts"

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

  if is_plan_only; then
    echo "WP5 quality gate plan completed; no validation commands executed."
  else
    echo "WP5 quality gate passed."
  fi
}

main "$@"
