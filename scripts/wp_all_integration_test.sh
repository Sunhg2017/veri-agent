#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${WP_ALL_BASE_URL:-http://localhost:8080}"
BOOTSTRAP_TOKEN="${WP1_BOOTSTRAP_TOKEN:-local-init-token}"
WP2_SERVICE_TOKEN="${WP2_SERVICE_TOKEN:-local-model-access-token}"
WP3_SERVICE_TOKEN="${WP3_SERVICE_TOKEN:-local-asset-token}"
WP4_SERVICE_TOKEN="${WP4_SERVICE_TOKEN:-local-document-input-token}"
WP4_WEBHOOK_SECRET="${WP4_WEBHOOK_SECRET:-local-document-input-webhook-secret}"
ADMIN_USERNAME="${WP_ALL_ADMIN_USERNAME:-admin}"
ADMIN_PASSWORD="${WP_ALL_ADMIN_PASSWORD:-AdminPass12345}"
PROJECT_CODE="${WP_ALL_PROJECT_CODE:-demo-$(date +%s)-$RANDOM}"
WP4_SOURCE_CODE="${WP_ALL_WP4_SOURCE_CODE:-wp-all-$(date +%s)-$RANDOM}"
PASS=0
FAIL=0

require_tool() {
  local tool="$1"
  if ! command -v "$tool" >/dev/null 2>&1; then
    echo "$tool is required for integration test" >&2
    exit 127
  fi
}

check() {
  local name="$1"
  local jq_expr="$2"
  local payload="$3"
  if printf '%s' "$payload" | jq -e "$jq_expr" >/dev/null; then
    echo "   PASS $name"
    PASS=$((PASS + 1))
  else
    echo "   FAIL $name"
    echo "$payload"
    FAIL=$((FAIL + 1))
  fi
}

check_arg() {
  local name="$1"
  local arg_name="$2"
  local arg_value="$3"
  local jq_expr="$4"
  local payload="$5"
  if printf '%s' "$payload" | jq -e --arg "$arg_name" "$arg_value" "$jq_expr" >/dev/null; then
    echo "   PASS $name"
    PASS=$((PASS + 1))
  else
    echo "   FAIL $name"
    echo "$payload"
    FAIL=$((FAIL + 1))
  fi
}

post_json() {
  local path="$1"
  local body="$2"
  shift 2
  curl -sS -X POST "$BASE_URL$path" "$@" -H 'Content-Type: application/json' -d "$body"
}

get_json() {
  local path="$1"
  shift
  curl -sS "$BASE_URL$path" "$@"
}

urlencode() {
  jq -nr --arg value "$1" '$value | @uri'
}

webhook_signature() {
  local timestamp="$1"
  local event_id="$2"
  local idempotency_key="$3"
  local payload="$4"
  printf '%s' "$timestamp.$event_id.$idempotency_key.$payload" | openssl dgst -sha256 -hmac "$WP4_WEBHOOK_SECRET" -hex | awk '{print $NF}'
}

post_wp4_webhook() {
  local source_code="$1"
  local payload="$2"
  local event_id="$3"
  local idempotency_key="$4"
  local timestamp signature
  timestamp="$(date +%s)"
  signature="$(webhook_signature "$timestamp" "$event_id" "$idempotency_key" "$payload")"
  post_json "/api/v1/document-input/webhooks/$(urlencode "$source_code")" "$payload" \
    -H "X-VA-Timestamp: $timestamp" \
    -H "X-VA-Signature: $signature" \
    -H "X-VA-Event-Id: $event_id" \
    -H "X-VA-Idempotency-Key: $idempotency_key" \
    -H "X-VA-Event-Version: 1.0"
}

main() {
  require_tool curl
  require_tool jq
  require_tool openssl
  require_tool awk

  echo "== WP1-WP4 unified integration test =="
  echo "baseUrl=$BASE_URL project=$PROJECT_CODE wp4Source=$WP4_SOURCE_CODE"

  local health
  health="$(get_json /api/v1/health)"
  check "platform health" '.data.status == "UP"' "$health"

  local bootstrap
  bootstrap="$(post_json /api/v1/bootstrap/super-admin "$(jq -nc \
    --arg token "$BOOTSTRAP_TOKEN" \
    --arg username "$ADMIN_USERNAME" \
    --arg password "$ADMIN_PASSWORD" \
    '{bootstrapToken:$token,username:$username,password:$password,displayName:"平台管理员",email:"admin@example.com"}')")"
  check "bootstrap super admin" '.code == "OK" or .code == "CONFLICT"' "$bootstrap"

  local login token
  login="$(post_json /api/v1/auth/login "$(jq -nc \
    --arg username "$ADMIN_USERNAME" \
    --arg password "$ADMIN_PASSWORD" \
    '{username:$username,password:$password}')")"
  token="$(printf '%s' "$login" | jq -r '.data.accessToken // empty')"
  check "login" '.data.accessToken | type == "string"' "$login"

  local auth_headers=(-H "Authorization: Bearer $token")
  local wp2_headers=(
    -H "Authorization: Bearer $WP2_SERVICE_TOKEN"
    -H "X-Caller-Service: wp-all-integration"
    -H "X-Delegated-User-Id: $ADMIN_USERNAME"
  )
  local wp3_headers=(
    -H "Authorization: Bearer $WP3_SERVICE_TOKEN"
    -H "X-Caller-Service: wp-all-integration"
    -H "X-Delegated-User-Id: $ADMIN_USERNAME"
  )
  local wp4_headers=(
    -H "Authorization: Bearer $WP4_SERVICE_TOKEN"
    -H "X-Caller-Service: wp-all-integration"
    -H "X-Delegated-User-Id: $ADMIN_USERNAME"
  )

  local project
  project="$(post_json /api/v1/management/projects "$(jq -nc \
    --arg code "$PROJECT_CODE" \
    '{code:$code,name:"端到端测试项目",sensitivityLevel:"INTERNAL",allowPublicModel:false}')" \
    "${auth_headers[@]}")"
  check "WP1 create project" '(.code == "OK" and .data.name == "端到端测试项目" and .data.status == "规划中") or .code == "CONFLICT"' "$project"

  local context
  context="$(get_json "/api/v1/contexts/projects/$PROJECT_CODE?include=configs" \
    -H "Authorization: Bearer ${WP1_SERVICE_TOKEN:-local-platform-service-token}" \
    -H "X-Caller-Service: wp-all-integration" \
    -H "X-Delegated-User-Id: $ADMIN_USERNAME")"
  check "WP1 service context" '.data.resourceType == "PROJECT" and .data.sensitivityLevel == "INTERNAL"' "$context"

  local wp2_health
  wp2_health="$(get_json /api/v1/model-access/health)"
  check "WP2 health" '.data.service == "model-access" and .data.status == "UP"' "$wp2_health"

  local providers provider_id
  providers="$(get_json /api/v1/model-access/providers "${wp2_headers[@]}")"
  provider_id="$(printf '%s' "$providers" | jq -r '.data[] | select(.name == "local-echo-primary") | .id' | head -n 1)"
  check "WP2 provider seed" '.data | any(.name == "local-echo-primary")' "$providers"

  if [[ -n "$provider_id" && "$provider_id" != "null" ]]; then
    local provider_check
    provider_check="$(post_json "/api/v1/model-access/providers/$provider_id/check" '{}' "${wp2_headers[@]}")"
    check "WP2 provider check" '.data.status == "UP"' "$provider_check"
  fi

  local invocation
  invocation="$(post_json /api/v1/model-access/invocations "$(jq -nc \
    --arg projectId "$PROJECT_CODE" \
    '{projectId:$projectId,promptKey:"test-case-design",promptVariables:{context:"WP all smoke"},messages:[{role:"user",content:"生成 3 条冒烟测试点"}],allowPublicModel:false,sensitivityLevel:"INTERNAL"}')" \
    "${wp2_headers[@]}")"
  check "WP2 invocation" '.data.providerName == "local-echo-primary" and (.data.content | startswith("local model response:"))' "$invocation"

  local wp3_health
  wp3_health="$(get_json /api/v1/asset/health)"
  check "WP3 health" '.data.service == "asset-service" and .data.status == "UP"' "$wp3_health"

  local req req_id api api_id page flow testcase case_id link
  req="$(post_json /api/v1/asset/requirements "$(jq -nc \
    --arg projectId "$PROJECT_CODE" \
    '{projectId:$projectId,title:"用户登录功能",description:"登录能力需求",priority:"HIGH"}')" \
    "${wp3_headers[@]}")"
  req_id="$(printf '%s' "$req" | jq -r '.data.id // empty')"
  check "WP3 create requirement" '.data.title == "用户登录功能" and .data.status == "DRAFT"' "$req"

  api="$(post_json /api/v1/asset/apis "$(jq -nc \
    --arg projectId "$PROJECT_CODE" \
    '{projectId:$projectId,path:"/api/v1/auth/login",httpMethod:"POST",summary:"用户登录接口"}')" \
    "${wp3_headers[@]}")"
  api_id="$(printf '%s' "$api" | jq -r '.data.id // empty')"
  check "WP3 create API asset" '.data.httpMethod == "POST" and .data.status == "ACTIVE"' "$api"

  page="$(post_json /api/v1/asset/pages "$(jq -nc \
    --arg projectId "$PROJECT_CODE" \
    '{projectId:$projectId,name:"登录页",urlPattern:"/login",componentTree:{form:"login"}}')" \
    "${wp3_headers[@]}")"
  check "WP3 create page asset" '.data.name == "登录页" and .data.status == "ACTIVE"' "$page"

  flow="$(post_json /api/v1/asset/business-flows "$(jq -nc \
    --arg projectId "$PROJECT_CODE" \
    '{projectId:$projectId,name:"登录主流程",priority:"HIGH",flowJson:{nodes:["open","submit"]}}')" \
    "${wp3_headers[@]}")"
  check "WP3 create business flow" '.data.name == "登录主流程" and .data.status == "DRAFT"' "$flow"

  testcase="$(post_json /api/v1/asset/test-cases "$(jq -nc \
    --arg projectId "$PROJECT_CODE" \
    --arg requirementId "$req_id" \
    --arg apiId "$api_id" \
    '{projectId:$projectId,title:"验证正常登录",requirementId:$requirementId,apiId:$apiId,steps:[{action:"输入用户名密码",expectedResult:"登录成功"}]}')" \
    "${wp3_headers[@]}")"
  case_id="$(printf '%s' "$testcase" | jq -r '.data.id // empty')"
  check "WP3 create test case" '.data.title == "验证正常登录" and (.data.steps | length) == 1' "$testcase"

  if [[ -n "$req_id" && -n "$case_id" ]]; then
    link="$(post_json /api/v1/asset/links "$(jq -nc \
      --arg requirementId "$req_id" \
      --arg apiId "$api_id" \
      --arg caseId "$case_id" \
      '{requirementId:$requirementId,apiId:$apiId,caseId:$caseId}')" \
      "${wp3_headers[@]}")"
    check "WP3 create trace link" '.data.requirementId == "'"$req_id"'" and .data.caseId == "'"$case_id"'"' "$link"
  fi

  local audit
  audit="$(get_json "/api/v1/management/audit-logs?index=0&size=20" "${auth_headers[@]}")"
  check "WP1 audit page" '.data.items | type == "array"' "$audit"

  local wp4_health
  wp4_health="$(get_json /api/v1/document-input/health)"
  check "WP4 health" '.data.service == "document-input" and .data.status == "UP"' "$wp4_health"

  local wp4_import wp4_import_id wp4_candidates wp4_candidate_ids wp4_batch wp4_dry_run wp4_publish wp4_requirement_id wp4_asset
  wp4_import="$(post_json /api/v1/document-input/imports "$(jq -nc \
    --arg projectId "$PROJECT_CODE" \
    '{projectId:$projectId,sourceType:"MARKDOWN",sourceRef:"wp-all-md",content:"## WP4 联动需求\nPriority: HIGH\nTags: wp4, integration"}')" \
    "${wp4_headers[@]}")"
  check "WP4 Markdown import" '.data.status == "SUCCEEDED" and .data.pendingCount == 1' "$wp4_import"
  wp4_import_id="$(printf '%s' "$wp4_import" | jq -r '.data.id // empty')"

  wp4_candidates="$(get_json "/api/v1/document-input/imports/$wp4_import_id/candidates" "${wp4_headers[@]}")"
  wp4_candidate_ids="$(printf '%s' "$wp4_candidates" | jq -c '[.data.items[].id]')"
  check "WP4 candidate list" '.data.total == 1 and .data.items[0].status == "PENDING"' "$wp4_candidates"

  wp4_batch="$(post_json /api/v1/document-input/candidates/batch-action "$(jq -nc --argjson ids "$wp4_candidate_ids" '{action:"CONFIRM",candidateIds:$ids}')" \
    "${wp4_headers[@]}")"
  check "WP4 batch confirm" '.data.succeededCount == 1 and .data.items[0].candidate.status == "CONFIRMED"' "$wp4_batch"

  wp4_dry_run="$(post_json "/api/v1/document-input/imports/$wp4_import_id/publish" "$(jq -nc --argjson ids "$wp4_candidate_ids" '{dryRun:true,candidateIds:$ids}')" \
    "${wp4_headers[@]}")"
  check "WP4 publish dryRun" '.data.dryRun == true and .data.plannedCreateCount == 1 and .data.totalCreated == 0' "$wp4_dry_run"

  wp4_publish="$(post_json "/api/v1/document-input/imports/$wp4_import_id/publish" '{}' "${wp4_headers[@]}")"
  wp4_requirement_id="$(printf '%s' "$wp4_publish" | jq -r '.data.createdRequirementIds[0] // empty')"
  check "WP4 publish to WP3" '.data.dryRun == false and .data.totalCreated == 1 and .data.publishedCount == 1' "$wp4_publish"

  wp4_asset="$(get_json "/api/v1/asset/requirements/$wp4_requirement_id" "${wp3_headers[@]}")"
  check_arg "WP4-created WP3 requirement" id "$wp4_requirement_id" '.data.id == $id and (.data.tags | contains("document-input"))' "$wp4_asset"

  local wp4_source wp4_webhook_payload wp4_event_id wp4_idem wp4_webhook wp4_events
  wp4_source="$(post_json /api/v1/document-input/sources "$(jq -nc \
    --arg sourceCode "$WP4_SOURCE_CODE" \
    --arg projectId "$PROJECT_CODE" \
    '{sourceCode:$sourceCode,name:"WP all webhook source",sourceType:"CUSTOM_API",defaultProjectId:$projectId,endpointUrl:"https://example.test/wp-all"}')" \
    "${wp4_headers[@]}")"
  check_arg "WP4 CUSTOM_API source" sourceCode "$WP4_SOURCE_CODE" '.data.sourceCode == $sourceCode and .data.sourceType == "CUSTOM_API"' "$wp4_source"

  wp4_event_id="evt-wp-all-$RANDOM"
  wp4_idem="idem-wp-all-$RANDOM"
  wp4_webhook_payload="$(jq -nc \
    --arg projectId "$PROJECT_CODE" \
    '{projectId:$projectId,eventType:"requirement.created",id:"REQ-WP-ALL",requirements:[{title:"WP4 webhook 联动需求",priority:"LOW",tags:["wp4","webhook"]}]}')"
  wp4_webhook="$(post_wp4_webhook "$WP4_SOURCE_CODE" "$wp4_webhook_payload" "$wp4_event_id" "$wp4_idem")"
  check_arg "WP4 signed webhook" sourceCode "$WP4_SOURCE_CODE" '.data.sourceCode == $sourceCode and .data.pendingCount == 1' "$wp4_webhook"

  wp4_events="$(get_json "/api/v1/document-input/webhook-events?sourceCode=$(urlencode "$WP4_SOURCE_CODE")" "${wp4_headers[@]}")"
  check_arg "WP4 webhook event log" eventId "$wp4_event_id" '.data.items | any(.eventId == $eventId and .signatureStatus == "VALID")' "$wp4_events"

  local forbidden
  forbidden="$(curl -sS -o /tmp/wp-all-forbidden.json -w '%{http_code}' "$BASE_URL/api/v1/model-access/providers")"
  if [[ "$forbidden" == "403" ]]; then
    echo "   PASS service token rejection"
    PASS=$((PASS + 1))
  else
    echo "   FAIL service token rejection"
    cat /tmp/wp-all-forbidden.json
    FAIL=$((FAIL + 1))
  fi

  echo "== summary =="
  echo "pass=$PASS fail=$FAIL total=$((PASS + FAIL))"
  if [[ "$FAIL" -ne 0 ]]; then
    exit 1
  fi
  echo "WP1-WP4 unified integration test passed."
}

main "$@"
