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

is_plan_only() {
  is_truthy "${WP10_QUALITY_GATE_PLAN_ONLY:-0}"
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
  run_step "wp10 script syntax" \
    check_script_syntax \
      "$ROOT_DIR/scripts/wp10_quality_gate.sh" \
      "$ROOT_DIR/scripts/wp10_report_smoke.sh" \
      "$ROOT_DIR/scripts/wp10_defect_draft_smoke.sh" \
      "$ROOT_DIR/scripts/wp10_export_redaction_smoke.sh" \
      "$ROOT_DIR/scripts/platform_api_java_line_guard.sh"

  run_step "platform-api Java line guard" \
    bash "$ROOT_DIR/scripts/platform_api_java_line_guard.sh"

  run_step "wp10 report smoke" \
    bash "$ROOT_DIR/scripts/wp10_report_smoke.sh"

  run_step "wp10 defect draft smoke" \
    bash "$ROOT_DIR/scripts/wp10_defect_draft_smoke.sh"

  run_step "wp10 export redaction smoke" \
    bash "$ROOT_DIR/scripts/wp10_export_redaction_smoke.sh"

  run_step "wp10 backend and OpenAPI tests" \
    mvn -B -pl platform-api \
      -Dtest=ReportControllerTest,ReportingHealthControllerTest,ReportingOpenApiContractTest,OpenApiContractTest,PermissionCodeUsageTest \
      test

  run_step "wp10 frontend Vitest" \
    bash -c "cd '$ROOT_DIR/portal-web' && npm test -- --run src/api/reports.test.ts src/permissions.test.ts"

  run_step "wp10 frontend build" \
    bash -c "cd '$ROOT_DIR/portal-web' && npm run build"

  if [[ "${WP10_SKIP_DB_VALIDATION:-0}" != "1" ]]; then
    run_step "wp10 database validation via consolidated WP validation" \
      bash "$ROOT_DIR/db/validation/run_wp1_db_validation.sh"
  else
    echo "== wp10 database validation skipped by WP10_SKIP_DB_VALIDATION=1 =="
  fi

  if is_plan_only; then
    echo "WP10 quality gate plan completed; no validation commands executed."
  else
    echo "WP10 quality gate passed."
  fi
}

main "$@"
