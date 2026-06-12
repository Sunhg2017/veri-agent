#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FIXTURE_DIR="$ROOT_DIR/platform-api/src/test/resources/wp6-openapi-fixtures"

run_step() {
  local name="$1"
  shift
  echo "== $name =="
  "$@"
}

check_fixture_corpus() {
  local fixture
  for fixture in \
    openapi-minimal.json \
    openapi-path-query.yaml \
    openapi-secret-examples.json \
    openapi-invalid.json \
    openapi-large.json
  do
    if [[ ! -s "$FIXTURE_DIR/$fixture" ]]; then
      echo "Missing WP6 OpenAPI fixture: $FIXTURE_DIR/$fixture" >&2
      return 1
    fi
  done
}

main() {
  run_step "wp6 OpenAPI fixture corpus" check_fixture_corpus
  run_step "wp6 OpenAPI fixture parser smoke" \
    mvn -B -pl platform-api -Dtest=OpenApiFixtureSmokeTest test
  echo "WP6 OpenAPI fixture smoke passed."
}

main "$@"
