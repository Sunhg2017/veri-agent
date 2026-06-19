#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

echo "WP7 artifact redaction evaluation"
echo "corpus=wp7-artifact-redaction-v1"
echo "samples=secret_ref,authorization,cookie,lease_token,password,token,raw_dom,runner_stdout"
echo "backend_assertions=service_detail_export_samples,controller_export_secret_ref,health_policy"
mvn -B -pl platform-api \
  -Dtest=UiE2eRunServiceTest,UiE2eRunControllerTest,UiE2eHealthControllerTest \
  test
bash -lc "cd '$ROOT_DIR/portal-web' && npm run test -- src/api/uiE2e.test.ts src/uiE2eWorkbenchState.test.ts src/permissions.test.ts"
echo "WP7 artifact redaction evaluation passed."
