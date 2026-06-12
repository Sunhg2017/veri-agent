#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

mode="${WP6_RUNNER_SMOKE:-managed}"
case "$mode" in
  1|true|TRUE|auto)
    mode="managed"
    ;;
  managed|external|pytest)
    ;;
  *)
    echo "Unsupported WP6_RUNNER_SMOKE=${WP6_RUNNER_SMOKE:-}; use managed, pytest, auto, external, 1, or true." >&2
    exit 2
    ;;
esac

derive_host() {
  local url="$1"
  python3 - "$url" <<'PY'
import sys
from urllib.parse import urlparse

parsed = urlparse(sys.argv[1])
if parsed.scheme not in {"http", "https"} or not parsed.hostname:
    raise SystemExit(2)
print(parsed.hostname.lower())
PY
}

base_url="${WP6_RUNNER_BASE_URL:-https://api.wp6-smoke.example.test/service}"
if [[ "$mode" == "external" && -z "${WP6_RUNNER_BASE_URL:-}" ]]; then
  echo "WP6_RUNNER_SMOKE=external requires WP6_RUNNER_BASE_URL to be explicitly reviewed and configured." >&2
  exit 2
fi

allowed_host="${WP6_RUNNER_ALLOWED_HOST:-$(derive_host "$base_url")}"
if [[ -z "$allowed_host" ]]; then
  echo "WP6 runner smoke could not derive an allowed host from WP6_RUNNER_BASE_URL." >&2
  exit 2
fi

echo "== wp6 runner ${mode} smoke =="
if [[ "$mode" == "pytest" ]]; then
  mvn -B -pl platform-api \
    -Dtest=PytestSubprocessApiAutomationRunnerAdapterTest,ApiAutomationRunnerConfigurationTest \
    test
else
  mvn -B -pl platform-api \
    -Dtest=ApiAutomationRunnerSmokeTest,ManagedHttpApiAutomationRunnerAdapterTest,PytestSubprocessApiAutomationRunnerAdapterTest,ApiAutomationRunnerConfigurationTest \
    -Dwp6.runner.smoke.baseUrl="$base_url" \
    -Dwp6.runner.smoke.allowedHost="$allowed_host" \
    test
fi

echo "WP6 runner ${mode} smoke passed."
