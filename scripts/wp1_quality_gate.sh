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
  run_step "wp1 single-platform guard" bash "$ROOT_DIR/scripts/wp1_single_platform_guard.sh"

  run_step "platform-api tests" mvn -B -pl platform-api test

  run_step "portal-web tests" bash -lc "cd '$ROOT_DIR/portal-web' && npm run test"
  run_step "portal-web build" bash -lc "cd '$ROOT_DIR/portal-web' && npm run build"

  if [[ "${WP1_SKIP_DB_VALIDATION:-0}" != "1" ]]; then
    run_step "wp1 database validation" bash "$ROOT_DIR/db/validation/run_wp1_db_validation.sh"
  else
    echo "== wp1 database validation skipped by WP1_SKIP_DB_VALIDATION=1 =="
  fi

  if [[ "${WP1_RUN_DB_SMOKE:-0}" == "1" ]]; then
    run_step "wp1 db profile smoke" bash "$ROOT_DIR/scripts/wp1_db_profile_smoke.sh"
  else
    echo "== wp1 db profile smoke skipped; set WP1_RUN_DB_SMOKE=1 when platform-api db profile is running =="
  fi

  echo "WP1 quality gate passed."
}

main "$@"
