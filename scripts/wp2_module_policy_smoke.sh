#!/usr/bin/env bash
set -euo pipefail

WP2_API_BASE="${WP2_MODULE_POLICY_SMOKE_WP2_API_BASE:-http://127.0.0.1:8080/api/v1/model-access}"
WP2_SERVICE_TOKEN="${WP2_SERVICE_TOKEN:-local-model-access-token}"
CALLER_SERVICE="${WP2_MODULE_POLICY_SMOKE_CALLER_SERVICE:-model-access}"
DELEGATED_USER_ID="${WP2_MODULE_POLICY_SMOKE_DELEGATED_USER_ID:-user-smoke}"
PROJECT_ID="${WP2_MODULE_POLICY_SMOKE_PROJECT_ID:-project-policy-smoke-$(date +%s)-$RANDOM}"

require_tool() {
  local tool="$1"
  if ! command -v "$tool" >/dev/null 2>&1; then
    echo "$tool is required for WP2 module policy smoke test" >&2
    exit 127
  fi
}

wp2_headers=(
  -H "Authorization: Bearer $WP2_SERVICE_TOKEN"
  -H "X-Caller-Service: $CALLER_SERVICE"
  -H "X-Delegated-User-Id: $DELEGATED_USER_ID"
)

post_wp2_capture() {
  local path="$1"
  local body="$2"
  local response_file="$3"
  curl -sS -o "$response_file" -w '%{http_code}' -X POST "$WP2_API_BASE$path" \
    "${wp2_headers[@]}" \
    -H 'Content-Type: application/json' \
    -d "$body"
}

main() {
  require_tool curl
  require_tool jq

  curl -fsS "$WP2_API_BASE/health" >/dev/null

  local blocked_file
  blocked_file="$(mktemp -t wp2-policy-blocked.XXXXXX.json)"
  local blocked_status
  blocked_status="$(post_wp2_capture /invocations "$(jq -nc \
    --arg projectId "$PROJECT_ID" \
    '{projectId:$projectId,messages:[{role:"user",content:"验证单体模块公开模型策略"}],allowPublicModel:true,sensitivityLevel:"PUBLIC"}')" "$blocked_file")"
  if [[ "$blocked_status" != "400" ]] || ! jq -e '.code == "MODEL_POLICY_VIOLATION"' "$blocked_file" >/dev/null; then
    echo "WP2 policy guard did not block public model routing from WP1 module policy" >&2
    cat "$blocked_file" >&2
    exit 1
  fi

  local success_file
  success_file="$(mktemp -t wp2-policy-success.XXXXXX.json)"
  local success_status
  success_status="$(post_wp2_capture /invocations "$(jq -nc \
    --arg projectId "$PROJECT_ID" \
    '{projectId:$projectId,promptKey:"test-case-design",promptVariables:{context:"WP2 policy smoke"},messages:[{role:"user",content:"生成 policy smoke 验证点"}],allowPublicModel:false,sensitivityLevel:"INTERNAL"}')" "$success_file")"
  if [[ "$success_status" != "200" ]] || ! jq -e '.data.providerName == "local-echo-primary" and (.data.content | startswith("local model response:"))' "$success_file" >/dev/null; then
    echo "WP2 policy smoke did not complete a local invocation" >&2
    cat "$success_file" >&2
    exit 1
  fi

  echo "WP2 module policy smoke passed for project_id=$PROJECT_ID."
}

main "$@"
