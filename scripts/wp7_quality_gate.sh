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
  if is_truthy "${WP7_RELEASE_GATE:-0}"; then
    return 0
  fi
  case "${WP7_GATE_MODE:-development}" in
    release|RELEASE|preprod|PREPROD|prod|PROD|production|PRODUCTION)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

is_plan_only() {
  is_truthy "${WP7_QUALITY_GATE_PLAN_ONLY:-0}"
}

browser_smoke_requested() {
  case "${WP7_BROWSER_SMOKE:-0}" in
    1|true|TRUE|managed|auto)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

runner_smoke_requested() {
  case "${WP7_RUNNER_SMOKE:-0}" in
    1|true|TRUE|managed|auto)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

artifact_eval_requested() {
  case "${WP7_ARTIFACT_REDACTION_EVAL:-1}" in
    1|true|TRUE|managed|auto)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

validate_release_gate() {
  if ! is_release_gate; then
    return
  fi
  if ! browser_smoke_requested; then
    echo "WP7 release gate requires browser smoke. Set WP7_BROWSER_SMOKE=managed to run the local Playwright smoke." >&2
    exit 2
  fi
  if ! runner_smoke_requested; then
    echo "WP7 release gate requires runner smoke. Set WP7_RUNNER_SMOKE=managed to run the local runner smoke." >&2
    exit 2
  fi
  if ! artifact_eval_requested; then
    echo "WP7 release gate requires artifact redaction evaluation. Set WP7_ARTIFACT_REDACTION_EVAL=1." >&2
    exit 2
  fi
  echo "== wp7 release gate mode: browser smoke, runner smoke and artifact redaction eval explicitly required =="
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

run_browser_smoke() {
  case "${WP7_BROWSER_SMOKE:-1}" in
    1|true|TRUE|managed|auto)
      run_step "wp7 browser smoke" \
        bash "$ROOT_DIR/scripts/wp7_browser_smoke.sh"
      ;;
    0|false|FALSE|"")
      echo "== wp7 browser smoke skipped; set WP7_BROWSER_SMOKE=managed to run it =="
      ;;
    *)
      echo "Unsupported WP7_BROWSER_SMOKE=${WP7_BROWSER_SMOKE}; use managed, auto, 1, or 0." >&2
      exit 2
      ;;
  esac
}

run_runner_smoke() {
  case "${WP7_RUNNER_SMOKE:-1}" in
    1|true|TRUE|managed|auto)
      run_step "wp7 runner smoke" \
        bash "$ROOT_DIR/scripts/wp7_runner_smoke.sh"
      ;;
    0|false|FALSE|"")
      echo "== wp7 runner smoke skipped; set WP7_RUNNER_SMOKE=managed to run it =="
      ;;
    *)
      echo "Unsupported WP7_RUNNER_SMOKE=${WP7_RUNNER_SMOKE}; use managed, auto, 1, or 0." >&2
      exit 2
      ;;
  esac
}

run_artifact_eval() {
  case "${WP7_ARTIFACT_REDACTION_EVAL:-1}" in
    1|true|TRUE|managed|auto)
      run_step "wp7 artifact redaction evaluation" \
        bash "$ROOT_DIR/scripts/wp7_artifact_redaction_eval.sh"
      ;;
    0|false|FALSE|"")
      echo "== wp7 artifact redaction evaluation skipped by WP7_ARTIFACT_REDACTION_EVAL=0 =="
      ;;
    *)
      echo "Unsupported WP7_ARTIFACT_REDACTION_EVAL=${WP7_ARTIFACT_REDACTION_EVAL}; use 1 or 0." >&2
      exit 2
      ;;
  esac
}

main() {
  validate_release_gate

  run_step "wp7 script syntax" \
    check_script_syntax \
      "$ROOT_DIR/scripts/wp7_quality_gate.sh" \
      "$ROOT_DIR/scripts/wp7_frontend_e2e_smoke.sh" \
      "$ROOT_DIR/scripts/wp7_browser_smoke.sh" \
      "$ROOT_DIR/scripts/wp7_runner_smoke.sh" \
      "$ROOT_DIR/scripts/wp7_artifact_redaction_eval.sh" \
      "$ROOT_DIR/scripts/platform_api_java_line_guard.sh"

  run_step "platform-api Java line guard" \
    bash "$ROOT_DIR/scripts/platform_api_java_line_guard.sh"

  run_step "wp7 backend and OpenAPI tests" \
    mvn -B -pl platform-api \
      -Dtest=UiE2eSceneServiceTest,UiE2eBundleServiceTest,UiE2eRunServiceTest,UiE2eSceneControllerTest,UiE2eBundleControllerTest,UiE2eRunControllerTest,UiE2eFlakyMarkControllerTest,UiE2eHealthControllerTest,UiE2eOpenApiContractTest,OpenApiContractTest,PermissionCodeUsageTest \
      test

  run_step "portal-web WP7 tests" \
    bash -lc "cd '$ROOT_DIR/portal-web' && npm run test -- src/api/uiE2e.test.ts src/uiE2eWorkbenchState.test.ts src/permissions.test.ts"

  if [[ "${WP7_SKIP_FRONTEND_E2E:-0}" != "1" ]]; then
    run_browser_smoke
  else
    echo "== wp7 browser smoke skipped by WP7_SKIP_FRONTEND_E2E=1 =="
  fi

  run_step "portal-web build" \
    bash -lc "cd '$ROOT_DIR/portal-web' && npm run build"

  if [[ "${WP7_SKIP_DB_VALIDATION:-0}" != "1" ]]; then
    run_step "wp7 database validation via consolidated WP validation" \
      bash "$ROOT_DIR/db/validation/run_wp1_db_validation.sh"
  else
    echo "== wp7 database validation skipped by WP7_SKIP_DB_VALIDATION=1 =="
  fi

  run_runner_smoke
  run_artifact_eval

  if is_plan_only; then
    echo "WP7 quality gate plan completed; no validation commands executed."
  else
    echo "WP7 quality gate passed."
  fi
}

main "$@"
