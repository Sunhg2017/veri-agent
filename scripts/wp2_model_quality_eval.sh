#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TASK_TYPE="${WP2_MODEL_EVAL_TASK:-ALL}"

cd "$ROOT_DIR"
echo "== WP2 model quality eval =="
echo "taskType=$TASK_TYPE"
mvn -B -pl platform-api \
  -Dtest=ModelAccessQualityEvaluationTest \
  -Dwp2.model.eval.taskType="$TASK_TYPE" \
  test
