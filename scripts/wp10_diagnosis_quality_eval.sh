#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

echo "== wp10 diagnosis quality eval =="
echo "corpus=wp10-diagnosis-quality-baseline-v1"
echo "scenarios=timeout,dependency_blocked,runner_disabled,account_locked,webhook_idempotency_conflict"

cd "$ROOT_DIR"
mvn -B -pl platform-api -Dtest=RuleFailureClassifierQualityEvaluationTest test

echo "WP10 diagnosis quality eval passed."
