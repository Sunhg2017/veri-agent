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
  if is_truthy "${WP6_RELEASE_GATE:-0}"; then
    return 0
  fi
  case "${WP6_GATE_MODE:-development}" in
    release|RELEASE|preprod|PREPROD|prod|PROD|production|PRODUCTION)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

is_plan_only() {
  is_truthy "${WP6_QUALITY_GATE_PLAN_ONLY:-0}"
}

runner_smoke_requested() {
  case "${WP6_RUNNER_SMOKE:-0}" in
    1|true|TRUE|managed|pytest|auto|external)
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
  if ! runner_smoke_requested; then
    echo "WP6 release gate requires runner smoke. Set WP6_RUNNER_SMOKE=managed or pytest for local smoke, or WP6_RUNNER_SMOKE=external with a reviewed runner base URL." >&2
    exit 2
  fi
  echo "== wp6 release gate mode: runner smoke explicitly required =="
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

run_runner_smoke() {
  case "${WP6_RUNNER_SMOKE:-0}" in
    1|true|TRUE|managed|pytest|auto|external)
      run_step "wp6 runner smoke" \
        bash "$ROOT_DIR/scripts/wp6_runner_smoke.sh"
      ;;
    0|false|FALSE|"")
      echo "== wp6 runner smoke skipped; set WP6_RUNNER_SMOKE=managed, pytest or external for explicit runner smoke =="
      ;;
    *)
      echo "Unsupported WP6_RUNNER_SMOKE=${WP6_RUNNER_SMOKE}; use managed, pytest, auto, external, 1, or 0." >&2
      exit 2
      ;;
  esac
}

main() {
  validate_release_gate

  run_step "wp6 script syntax" \
    check_script_syntax \
      "$ROOT_DIR/scripts/wp6_quality_gate.sh" \
      "$ROOT_DIR/scripts/wp6_openapi_fixture_smoke.sh" \
      "$ROOT_DIR/scripts/wp6_runner_smoke.sh"

  run_step "wp6 OpenAPI fixture smoke" \
    bash "$ROOT_DIR/scripts/wp6_openapi_fixture_smoke.sh"

  run_step "wp6 backend and OpenAPI tests" \
    mvn -B -pl platform-api -Dtest=ApiAutomationServiceTest,ApiAutomationControllerTest,OpenApiSpecParserTest,OpenApiFixtureSmokeTest,OpenApiContractTest test

  run_step "portal-web WP6 tests" \
    bash -lc "cd '$ROOT_DIR/portal-web' && npm run test -- apiAutomation.test.ts permissions.test.ts"

  run_step "portal-web build" \
    bash -lc "cd '$ROOT_DIR/portal-web' && npm run build"

  if [[ "${WP6_SKIP_DB_VALIDATION:-0}" != "1" ]]; then
    run_step "wp6 database validation via consolidated WP validation" \
      bash "$ROOT_DIR/db/validation/run_wp1_db_validation.sh"
  else
    echo "== wp6 database validation skipped by WP6_SKIP_DB_VALIDATION=1 =="
  fi

  run_runner_smoke

  if is_plan_only; then
    echo "WP6 quality gate plan completed; no validation commands executed."
  else
    echo "WP6 quality gate passed."
  fi
}

main "$@"
