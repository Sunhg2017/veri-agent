#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

echo "== model-access tests =="
mvn -B -pl model-access test

echo "== wp2 database validation =="
bash "$ROOT_DIR/db/validation/run_wp2_db_validation.sh"

if [[ "${WP2_RUN_HTTP_SMOKE:-0}" == "1" ]]; then
  echo "== wp2 model-access http smoke =="
  bash "$ROOT_DIR/scripts/wp2_model_access_smoke.sh"
else
  echo "== wp2 model-access http smoke skipped; set WP2_RUN_HTTP_SMOKE=1 when model-access is running =="
fi

if [[ "${WP2_RUN_STRICT_SMOKE:-0}" == "1" ]]; then
  echo "== wp2 strict wp1 integration smoke =="
  bash "$ROOT_DIR/scripts/wp2_strict_integration_smoke.sh"
else
  echo "== wp2 strict integration smoke skipped; set WP2_RUN_STRICT_SMOKE=1 when WP1/WP2 strict services are running =="
fi

echo "WP2 quality gate passed."
