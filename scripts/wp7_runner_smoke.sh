#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

echo "== wp7 runner smoke =="
mvn -B -pl platform-api \
  -Dtest=UiE2eRunServiceTest,ManagedPreviewUiE2eRunnerAdapterTest,UiE2eRunnerConfigurationTest,ExecutionRunDispatchSupportTest,ExecutionRunServiceTest \
  test
echo "WP7 runner smoke passed."
