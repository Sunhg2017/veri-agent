#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

echo "== platform-api tests =="
mvn -B -pl platform-api test

echo "== wp2 database validation =="
bash "$ROOT_DIR/db/validation/run_wp2_db_validation.sh"

if [[ "${WP2_RUN_HTTP_SMOKE:-0}" == "1" ]]; then
  echo "== unified platform-api http smoke =="
  bash "$ROOT_DIR/scripts/wp_all_integration_test.sh"
else
  echo "== http smoke skipped; set WP2_RUN_HTTP_SMOKE=1 when platform-api is running =="
fi

echo "WP2 quality gate passed."
