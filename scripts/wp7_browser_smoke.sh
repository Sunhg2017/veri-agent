#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

echo "== wp7 browser smoke =="
bash "$ROOT_DIR/scripts/wp7_frontend_e2e_smoke.sh"
echo "WP7 browser smoke passed."
