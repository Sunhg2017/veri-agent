#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
QUALITY_GATE="$ROOT_DIR/scripts/wp5_quality_gate.sh"

run_gate() {
  WP5_QUALITY_GATE_PLAN_ONLY=1 "$@"
}

assert_contains() {
  local name="$1"
  local haystack="$2"
  local needle="$3"
  if [[ "$haystack" != *"$needle"* ]]; then
    echo "FAIL $name: expected output to contain: $needle" >&2
    echo "$haystack" >&2
    exit 1
  fi
  echo "PASS $name"
}

assert_fails_with() {
  local name="$1"
  local needle="$2"
  shift 2
  local output status
  set +e
  output="$(run_gate "$@" 2>&1)"
  status=$?
  set -e
  if [[ "$status" -eq 0 ]]; then
    echo "FAIL $name: command unexpectedly succeeded" >&2
    echo "$output" >&2
    exit 1
  fi
  assert_contains "$name" "$output" "$needle"
}

assert_succeeds_with() {
  local name="$1"
  local needle="$2"
  shift 2
  local output
  output="$(run_gate "$@" 2>&1)"
  assert_contains "$name" "$output" "$needle"
}

assert_fails_with \
  "release gate requires HTTP smoke" \
  "WP5 release gate requires HTTP smoke" \
  env -u WP5_RELEASE_GATE -u WP5_SMOKE_BASE_URL WP5_GATE_MODE=release WP5_RUN_HTTP_SMOKE=0 bash "$QUALITY_GATE"

assert_fails_with \
  "release external smoke requires base url" \
  "WP5 release gate external HTTP smoke requires WP5_SMOKE_BASE_URL" \
  env -u WP5_RELEASE_GATE -u WP5_SMOKE_BASE_URL WP5_GATE_MODE=release WP5_RUN_HTTP_SMOKE=external WP5_RUN_AI_EVAL=1 bash "$QUALITY_GATE"

assert_fails_with \
  "release managed smoke requires AI eval" \
  "WP5 release gate requires AI quality evaluation" \
  env -u WP5_RELEASE_GATE -u WP5_SMOKE_BASE_URL WP5_GATE_MODE=release WP5_RUN_HTTP_SMOKE=1 WP5_RUN_AI_EVAL=0 bash "$QUALITY_GATE"

assert_succeeds_with \
  "release managed smoke and AI eval accepted" \
  "wp5 release gate mode: HTTP smoke (1) and AI quality evaluation required" \
  env -u WP5_RELEASE_GATE -u WP5_SMOKE_BASE_URL WP5_GATE_MODE=release WP5_RUN_HTTP_SMOKE=1 WP5_RUN_AI_EVAL=1 bash "$QUALITY_GATE"

assert_succeeds_with \
  "release external smoke and AI eval accepted" \
  "wp5 release gate mode: HTTP smoke (external) and AI quality evaluation required" \
  env -u WP5_GATE_MODE WP5_RELEASE_GATE=1 WP5_RUN_HTTP_SMOKE=external WP5_RUN_AI_EVAL=1 WP5_SMOKE_BASE_URL=http://127.0.0.1:8080 bash "$QUALITY_GATE"

assert_succeeds_with \
  "development gate can skip HTTP smoke" \
  "wp5 http smoke skipped" \
  env -u WP5_GATE_MODE -u WP5_RELEASE_GATE -u WP5_SMOKE_BASE_URL WP5_RUN_HTTP_SMOKE=0 bash "$QUALITY_GATE"

echo "WP5 quality gate mode contract passed."
