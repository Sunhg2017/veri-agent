#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

main() {
  echo "WP10 diagnosis context redaction evaluation"
  echo "corpus=wp10-diagnosis-context-redaction-v1"
  echo "samples=raw_prompt,raw_response,runner_stdout,runner_stderr,request_body,response_body,webhook_payload,secret_ref,authorization,lease_token"
  mvn -B -pl platform-api \
    -Dtest=ReportDiagnosisContextRedactionEvaluationTest \
    test
}

main "$@"
