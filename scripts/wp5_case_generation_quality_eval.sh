#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

echo "== wp5 case generation quality eval =="
echo "corpus=wp5-case-generation-baseline-v1"

cd "$ROOT_DIR"
mvn -B -pl platform-api -Dtest=TestDesignQualityEvaluationTest test

echo "WP5 case generation quality eval passed."
