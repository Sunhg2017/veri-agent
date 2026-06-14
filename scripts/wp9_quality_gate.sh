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
  if is_truthy "${WP9_RELEASE_GATE:-0}"; then
    return 0
  fi
  case "${WP9_GATE_MODE:-development}" in
    release|RELEASE|preprod|PREPROD|prod|PROD|production|PRODUCTION)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

is_plan_only() {
  is_truthy "${WP9_QUALITY_GATE_PLAN_ONLY:-0}"
}

scheduler_smoke_requested() {
  case "${WP9_SCHEDULER_SMOKE:-0}" in
    1|true|TRUE|managed|auto)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

webhook_http_smoke_requested() {
  case "${WP9_WEBHOOK_HTTP_SMOKE:-0}" in
    1|true|TRUE|managed|auto|external)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

run_worker_hosting_readiness() {
  local env_file
  for env_file in \
    "$ROOT_DIR/integrations/wp9-worker-hosting/web.env.example" \
    "$ROOT_DIR/integrations/wp9-worker-hosting/scheduler-active.env.example" \
    "$ROOT_DIR/integrations/wp9-worker-hosting/scheduler-standby.env.example"; do
    WP9_WORKER_HOSTING_ENV_FILE="$env_file" bash "$ROOT_DIR/scripts/wp9_worker_hosting_readiness.sh"
  done
}

validate_release_gate() {
  if ! is_release_gate; then
    return
  fi
  if ! scheduler_smoke_requested; then
    echo "WP9 release gate requires managed scheduler smoke. Set WP9_SCHEDULER_SMOKE=managed to run the local scheduler smoke." >&2
    exit 2
  fi
  if ! webhook_http_smoke_requested; then
    echo "WP9 release gate requires webhook HTTP smoke. Set WP9_WEBHOOK_HTTP_SMOKE=managed for local managed smoke or external for an already running service." >&2
    exit 2
  fi
  echo "== wp9 release gate mode: managed scheduler smoke and webhook HTTP smoke explicitly required =="
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

run_scheduler_smoke() {
  case "${WP9_SCHEDULER_SMOKE:-0}" in
    1|true|TRUE|managed|auto)
      run_step "wp9 managed scheduler smoke" \
        bash "$ROOT_DIR/scripts/wp9_scheduler_smoke.sh"
      ;;
    0|false|FALSE|"")
      echo "== wp9 managed scheduler smoke skipped; set WP9_SCHEDULER_SMOKE=managed to run it =="
      ;;
    *)
      echo "Unsupported WP9_SCHEDULER_SMOKE=${WP9_SCHEDULER_SMOKE}; use managed, auto, 1, or 0." >&2
      exit 2
      ;;
  esac
}

run_webhook_http_smoke() {
  case "${WP9_WEBHOOK_HTTP_SMOKE:-0}" in
    1|true|TRUE|managed|auto)
      run_step "wp9 managed webhook HTTP smoke" \
        env WP9_WEBHOOK_SMOKE_MODE=managed bash "$ROOT_DIR/scripts/wp9_webhook_http_smoke.sh"
      ;;
    external)
      run_step "wp9 external webhook HTTP smoke" \
        env WP9_WEBHOOK_SMOKE_MODE=external bash "$ROOT_DIR/scripts/wp9_webhook_http_smoke.sh"
      ;;
    0|false|FALSE|"")
      echo "== wp9 webhook HTTP smoke skipped; set WP9_WEBHOOK_HTTP_SMOKE=managed or external to run it =="
      ;;
    *)
      echo "Unsupported WP9_WEBHOOK_HTTP_SMOKE=${WP9_WEBHOOK_HTTP_SMOKE}; use managed, external, auto, 1, or 0." >&2
      exit 2
      ;;
  esac
}

main() {
  validate_release_gate

  run_step "wp9 script syntax" \
    check_script_syntax \
      "$ROOT_DIR/scripts/wp9_quality_gate.sh" \
      "$ROOT_DIR/scripts/wp9_frontend_e2e_smoke.sh" \
      "$ROOT_DIR/scripts/wp9_scheduler_smoke.sh" \
      "$ROOT_DIR/scripts/wp9_cron_capacity_smoke.sh" \
      "$ROOT_DIR/scripts/wp9_cron_backlog_smoke.sh" \
      "$ROOT_DIR/scripts/wp9_report_handoff_smoke.sh" \
      "$ROOT_DIR/scripts/wp9_webhook_http_smoke.sh" \
      "$ROOT_DIR/scripts/wp9_webhook_sign.sh" \
      "$ROOT_DIR/scripts/wp9_marketplace_package_smoke.sh" \
      "$ROOT_DIR/scripts/wp9_worker_hosting_readiness.sh" \
      "$ROOT_DIR/scripts/platform_api_java_line_guard.sh"

  run_step "platform-api Java line guard" \
    bash "$ROOT_DIR/scripts/platform_api_java_line_guard.sh"

  run_step "wp9 marketplace package smoke" \
    bash "$ROOT_DIR/scripts/wp9_marketplace_package_smoke.sh"

  run_step "wp9 worker hosting readiness" \
    run_worker_hosting_readiness

  run_step "wp9 cron capacity smoke" \
    bash "$ROOT_DIR/scripts/wp9_cron_capacity_smoke.sh"

  run_step "wp9 cron backlog smoke" \
    bash "$ROOT_DIR/scripts/wp9_cron_backlog_smoke.sh"

  run_step "wp9 report handoff smoke" \
    bash "$ROOT_DIR/scripts/wp9_report_handoff_smoke.sh"

  run_step "wp9 backend and OpenAPI tests" \
    mvn -B -pl platform-api -Dtest=ExecutionHealthControllerTest,ExecutionPlanControllerTest,ExecutionRunControllerTest,ExecutionRunDispatchControllerTest,ExecutionTriggerControllerTest,ExecutionDagValidatorTest,ExecutionSchedulerServiceTest,OpenApiContractTest,PermissionCodeUsageTest test

  run_step "portal-web WP9 tests" \
    bash -lc "cd '$ROOT_DIR/portal-web' && npm run test -- api/execution.test.ts executionDagEditor.test.ts permissions.test.ts"

  if [[ "${WP9_SKIP_FRONTEND_E2E:-0}" != "1" ]]; then
    run_step "portal-web WP9 Playwright smoke" \
      bash "$ROOT_DIR/scripts/wp9_frontend_e2e_smoke.sh"
  else
    echo "== portal-web WP9 Playwright smoke skipped by WP9_SKIP_FRONTEND_E2E=1 =="
  fi

  run_step "portal-web build" \
    bash -lc "cd '$ROOT_DIR/portal-web' && npm run build"

  if [[ "${WP9_SKIP_DB_VALIDATION:-0}" != "1" ]]; then
    run_step "wp9 database validation via consolidated WP validation" \
      bash "$ROOT_DIR/db/validation/run_wp1_db_validation.sh"
  else
    echo "== wp9 database validation skipped by WP9_SKIP_DB_VALIDATION=1 =="
  fi

  run_scheduler_smoke
  run_webhook_http_smoke

  if is_plan_only; then
    echo "WP9 quality gate plan completed; no validation commands executed."
  else
    echo "WP9 quality gate passed."
  fi
}

main "$@"
