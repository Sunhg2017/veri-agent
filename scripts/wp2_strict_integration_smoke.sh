#!/usr/bin/env bash
set -euo pipefail

WP1_API_BASE="${WP2_STRICT_SMOKE_WP1_API_BASE:-http://127.0.0.1:8080/api/v1}"
WP2_API_BASE="${WP2_STRICT_SMOKE_WP2_API_BASE:-http://127.0.0.1:8081/api/v1/model-access}"
WP2_ACTUATOR_BASE="${WP2_STRICT_SMOKE_WP2_ACTUATOR_BASE:-http://127.0.0.1:8081/actuator}"
WP1_SERVICE_TOKEN="${WP1_SERVICE_TOKEN:-local-platform-service-token}"
WP2_SERVICE_TOKEN="${WP2_SERVICE_TOKEN:-local-model-access-token}"
CALLER_SERVICE="${WP2_STRICT_SMOKE_CALLER_SERVICE:-wp2-strict-smoke}"
DELEGATED_USER_ID="${WP2_STRICT_SMOKE_DELEGATED_USER_ID:-user-smoke}"
PROJECT_ID="${WP2_STRICT_SMOKE_PROJECT_ID:-project-strict-smoke-$(date +%s)-$RANDOM}"

require_tool() {
  local tool="$1"
  if ! command -v "$tool" >/dev/null 2>&1; then
    echo "$tool is required for WP2 strict integration smoke test" >&2
    exit 127
  fi
}

wp1_headers=(
  -H "Authorization: Bearer $WP1_SERVICE_TOKEN"
  -H "X-Caller-Service: $CALLER_SERVICE"
  -H "X-Delegated-User-Id: $DELEGATED_USER_ID"
)

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

  curl -fsS "$WP1_API_BASE/health" >/dev/null
  curl -fsS "$WP2_API_BASE/health" >/dev/null

  local context
  context="$(curl -fsS "$WP1_API_BASE/contexts/projects/$PROJECT_ID?include=apps,environments,configs" "${wp1_headers[@]}")"
  if ! printf '%s' "$context" | jq -e '.data.resource_type == "PROJECT" and .data.allow_public_model == false and .data.sensitivity_level == "INTERNAL"' >/dev/null; then
    echo "WP1 context endpoint did not return the strict routing policy expected by WP2" >&2
    echo "$context" >&2
    exit 1
  fi

  local blocked_file
  blocked_file="$(mktemp -t wp2-strict-blocked.XXXXXX.json)"
  local blocked_status
  blocked_status="$(post_wp2_capture /invocations "{
    \"project_id\":\"$PROJECT_ID\",
    \"messages\":[{\"role\":\"user\",\"content\":\"验证 strict 模式公开模型策略\"}],
    \"allow_public_model\":true,
    \"sensitivity_level\":\"PUBLIC\"
  }" "$blocked_file")"
  if [[ "$blocked_status" != "400" ]] || ! jq -e '.code == "MODEL_POLICY_VIOLATION"' "$blocked_file" >/dev/null; then
    echo "WP2 strict mode did not block public model routing from WP1 policy" >&2
    cat "$blocked_file" >&2
    exit 1
  fi

  local success_file
  success_file="$(mktemp -t wp2-strict-success.XXXXXX.json)"
  local success_status
  success_status="$(post_wp2_capture /invocations "{
    \"project_id\":\"$PROJECT_ID\",
    \"prompt_key\":\"test-case-design\",
    \"prompt_variables\":{\"context\":\"WP2 strict smoke\"},
    \"messages\":[{\"role\":\"user\",\"content\":\"生成 strict smoke 验证点\"}],
    \"allow_public_model\":false,
    \"sensitivity_level\":\"INTERNAL\"
  }" "$success_file")"
  if [[ "$success_status" != "200" ]] || ! jq -e '.data.provider_name == "local-echo-primary" and (.data.content | startswith("local model response:"))' "$success_file" >/dev/null; then
    echo "WP2 strict mode did not complete a local invocation" >&2
    cat "$success_file" >&2
    exit 1
  fi

  local audit_metric
  audit_metric="$(curl -fsS "$WP2_ACTUATOR_BASE/metrics/veri.agent.model_access.platform.audit.events" "${wp2_headers[@]}" || true)"
  if [[ -n "$audit_metric" ]] && ! printf '%s' "$audit_metric" | jq -e '.name == "veri.agent.model_access.platform.audit.events"' >/dev/null; then
    echo "WP2 audit metric endpoint returned an unexpected payload" >&2
    echo "$audit_metric" >&2
    exit 1
  fi

  echo "WP2 strict integration smoke passed for project_id=$PROJECT_ID."
}

main "$@"
