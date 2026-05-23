#!/usr/bin/env bash
set -euo pipefail

API_BASE="${WP2_SMOKE_API_BASE:-http://127.0.0.1:8080/api/v1/model-access}"
SERVICE_TOKEN="${WP2_SERVICE_TOKEN:-local-model-access-token}"
CALLER_SERVICE="${WP2_SMOKE_CALLER_SERVICE:-model-access}"
DELEGATED_USER_ID="${WP2_SMOKE_DELEGATED_USER_ID:-user-smoke}"
PROJECT_ID="${WP2_SMOKE_PROJECT_ID:-project-smoke-$(date +%s)-$RANDOM}"

require_tool() {
  local tool="$1"
  if ! command -v "$tool" >/dev/null 2>&1; then
    echo "$tool is required for WP2 model-access smoke test" >&2
    exit 127
  fi
}

auth_headers=(
  -H "Authorization: Bearer $SERVICE_TOKEN"
  -H "X-Caller-Service: $CALLER_SERVICE"
  -H "X-Delegated-User-Id: $DELEGATED_USER_ID"
)

post_json() {
  local path="$1"
  local body="$2"
  curl -fsS -X POST "$API_BASE$path" \
    "${auth_headers[@]}" \
    -H 'Content-Type: application/json' \
    -d "$body"
}

get_json() {
  local path="$1"
  curl -fsS "$API_BASE$path" "${auth_headers[@]}"
}

main() {
  require_tool curl
  require_tool jq

  local health
  health="$(curl -fsS "$API_BASE/health")"
  if ! printf '%s' "$health" | jq -e '.data.service == "model-access" and .data.enabledProviders >= 1 and .data.activePrompts >= 1' >/dev/null; then
    echo "WP2 health did not report seeded provider and prompt" >&2
    echo "$health" >&2
    exit 1
  fi

  local providers
  providers="$(get_json /providers)"
  local provider_id
  provider_id="$(printf '%s' "$providers" | jq -r '.data[] | select(.name == "local-echo-primary") | .id' | head -n 1)"
  if [[ -z "$provider_id" || "$provider_id" == "null" ]]; then
    echo "WP2 providers did not include local-echo-primary" >&2
    echo "$providers" >&2
    exit 1
  fi

  local provider_check
  provider_check="$(post_json "/providers/$provider_id/check" '{}')"
  if ! printf '%s' "$provider_check" | jq -e '.data.providerName == "local-echo-primary" and .data.status == "UP" and .data.cached == false and .data.errorCode == null' >/dev/null; then
    echo "WP2 provider check did not report local provider UP" >&2
    echo "$provider_check" >&2
    exit 1
  fi

  local cached_provider_check
  cached_provider_check="$(post_json "/providers/$provider_id/check" '{}')"
  if ! printf '%s' "$cached_provider_check" | jq -e '.data.providerName == "local-echo-primary" and .data.status == "UP" and .data.cached == true' >/dev/null; then
    echo "WP2 provider check did not reuse cached readiness result" >&2
    echo "$cached_provider_check" >&2
    exit 1
  fi

  local invocation
  invocation="$(post_json /invocations "$(jq -nc \
    --arg projectId "$PROJECT_ID" \
    '{projectId:$projectId,promptKey:"test-case-design",promptVariables:{context:"WP2 smoke"},messages:[{role:"user",content:"生成 smoke 验证点"}],allowPublicModel:false,sensitivityLevel:"INTERNAL"}')")"
  if ! printf '%s' "$invocation" | jq -e '.data.providerName == "local-echo-primary" and (.data.content | startswith("local model response:")) and .data.totalCost != null' >/dev/null; then
    echo "WP2 invocation did not return expected local echo response" >&2
    echo "$invocation" >&2
    exit 1
  fi

  local summary
  summary="$(get_json "/invocations/summary?projectId=$PROJECT_ID&sensitivityLevel=INTERNAL")"
  if ! printf '%s' "$summary" | jq -e '.data.total >= 1 and .data.succeeded >= 1 and .data.totalCost != null' >/dev/null; then
    echo "WP2 invocation summary did not include smoke invocation" >&2
    echo "$summary" >&2
    exit 1
  fi

  local cost_report
  cost_report="$(get_json "/cost/report?projectId=$PROJECT_ID")"
  if ! printf '%s' "$cost_report" | jq -e --arg project_id "$PROJECT_ID" '.data.rows | any(.projectId == $project_id and .succeeded >= 1 and .totalCost != null)' >/dev/null; then
    echo "WP2 cost report did not include smoke project" >&2
    echo "$cost_report" >&2
    exit 1
  fi

  local page
  page="$(get_json "/invocations?projectId=$PROJECT_ID&sensitivityLevel=INTERNAL&index=0&size=5")"
  if ! printf '%s' "$page" | jq -e '.data.items[0].promptDigest != null and .data.items[0].requestPreview != null and .data.items[0].sensitivityLevel == "INTERNAL"' >/dev/null; then
    echo "WP2 invocation page did not include sanitized audit fields" >&2
    echo "$page" >&2
    exit 1
  fi

  local export_file
  export_file="$(mktemp -t wp2-invocations.XXXXXX.csv)"
  curl -fsS "$API_BASE/invocations/export?projectId=$PROJECT_ID&sensitivityLevel=INTERNAL" \
    "${auth_headers[@]}" \
    -o "$export_file"
  if ! head -n 1 "$export_file" | grep -q '^invocationId,createdAt,projectId'; then
    echo "WP2 invocation export did not return CSV header" >&2
    cat "$export_file" >&2
    exit 1
  fi
  if ! grep -q "$PROJECT_ID" "$export_file"; then
    echo "WP2 invocation export did not include smoke project" >&2
    cat "$export_file" >&2
    exit 1
  fi
  if grep -q 'promptPlaintext\|apiKeyValue\|secretValue' "$export_file"; then
    echo "WP2 invocation export exposed forbidden plaintext fields" >&2
    cat "$export_file" >&2
    exit 1
  fi

  echo "WP2 model-access smoke passed for project_id=$PROJECT_ID."
}

main "$@"
