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
  if is_truthy "${WP8_RELEASE_GATE:-0}"; then
    return 0
  fi
  case "${WP8_GATE_MODE:-development}" in
    release|RELEASE|preprod|PREPROD|prod|PROD|production|PRODUCTION)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

is_plan_only() {
  is_truthy "${WP8_QUALITY_GATE_PLAN_ONLY:-0}"
}

lease_concurrency_smoke_requested() {
  case "${WP8_LEASE_CONCURRENCY_SMOKE:-0}" in
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
  if ! lease_concurrency_smoke_requested; then
    echo "WP8 release gate requires lease concurrency smoke. Set WP8_LEASE_CONCURRENCY_SMOKE=managed to run the local managed smoke." >&2
    exit 2
  fi
  echo "== wp8 release gate mode: lease concurrency smoke explicitly required =="
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

run_lease_concurrency_smoke() {
  case "${WP8_LEASE_CONCURRENCY_SMOKE:-0}" in
    1|true|TRUE|managed|auto)
      run_step "wp8 account lease concurrency smoke" \
        bash "$ROOT_DIR/scripts/wp8_account_lease_concurrency_smoke.sh"
      ;;
    0|false|FALSE|"")
      echo "== wp8 account lease concurrency smoke skipped; set WP8_LEASE_CONCURRENCY_SMOKE=managed to run it =="
      ;;
    *)
      echo "Unsupported WP8_LEASE_CONCURRENCY_SMOKE=${WP8_LEASE_CONCURRENCY_SMOKE}; use managed, auto, 1, or 0." >&2
      exit 2
      ;;
  esac
}

main() {
  validate_release_gate

  run_step "wp8 script syntax" \
    check_script_syntax \
      "$ROOT_DIR/scripts/wp8_quality_gate.sh" \
      "$ROOT_DIR/scripts/wp8_frontend_e2e_smoke.sh" \
      "$ROOT_DIR/scripts/wp8_account_lease_concurrency_smoke.sh" \
      "$ROOT_DIR/scripts/platform_api_java_line_guard.sh"

  run_step "platform-api Java line guard" \
    bash "$ROOT_DIR/scripts/platform_api_java_line_guard.sh"

  run_step "wp8 backend tests" \
    mvn -B -pl platform-api -Dtest=TestDataHealthControllerTest,TestDataHealthServiceTest,TestDataSetControllerTest,TestDataSetServiceTest,TestAccountPoolControllerTest,TestAccountPoolServiceTest,TestAccountLeaseControllerTest,TestAccountLeaseServiceTest,TestDataTaskControllerTest,TestDataTaskServiceTest,TestDataWorkerServiceTest,TestDataCrossWpReferenceServiceTest,ConfiguredTestDataAdaptersTest,WorkerJobHandlerTest,TestDataOpenApiContractTest,OpenApiContractTest,PermissionCodeUsageTest test

  run_step "wp8 database repository contract" \
    mvn -B -pl platform-api -Dtest=DbProfileRepositoryContractTest test

  run_step "portal-web WP8 tests" \
    bash -lc "cd '$ROOT_DIR/portal-web' && npm run test -- api/testData.test.ts permissions.test.ts"

  if [[ "${WP8_SKIP_FRONTEND_E2E:-0}" != "1" ]]; then
    run_step "portal-web WP8 Playwright smoke" \
      bash "$ROOT_DIR/scripts/wp8_frontend_e2e_smoke.sh"
  else
    echo "== portal-web WP8 Playwright smoke skipped by WP8_SKIP_FRONTEND_E2E=1 =="
  fi

  run_step "portal-web build" \
    bash -lc "cd '$ROOT_DIR/portal-web' && npm run build"

  if [[ "${WP8_SKIP_DB_VALIDATION:-0}" != "1" ]]; then
    run_step "wp8 database validation via consolidated WP validation" \
      bash "$ROOT_DIR/db/validation/run_wp1_db_validation.sh"
  else
    echo "== wp8 database validation skipped by WP8_SKIP_DB_VALIDATION=1 =="
  fi

  run_lease_concurrency_smoke

  if is_plan_only; then
    echo "WP8 quality gate plan completed; no validation commands executed."
  else
    echo "WP8 quality gate passed."
  fi
}

main "$@"
