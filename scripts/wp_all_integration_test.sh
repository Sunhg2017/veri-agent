#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${WP_ALL_BASE_URL:-http://localhost:8080}"
BOOTSTRAP_TOKEN="${WP1_BOOTSTRAP_TOKEN:-local-init-token}"
WP2_SERVICE_TOKEN="${WP2_SERVICE_TOKEN:-local-model-access-token}"
WP3_SERVICE_TOKEN="${WP3_SERVICE_TOKEN:-local-asset-token}"
ADMIN_USERNAME="${WP_ALL_ADMIN_USERNAME:-admin}"
ADMIN_PASSWORD="${WP_ALL_ADMIN_PASSWORD:-AdminPass12345}"
PROJECT_CODE="${WP_ALL_PROJECT_CODE:-demo-$(date +%s)-$RANDOM}"
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

main() {
  require_tool curl
  require_tool jq

  echo "== WP1-WP3 unified integration test =="
  echo "baseUrl=$BASE_URL project=$PROJECT_CODE"

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

  local project
  project="$(post_json /api/v1/management/projects "$(jq -nc \
    --arg code "$PROJECT_CODE" \
    '{code:$code,name:"端到端测试项目",sensitivityLevel:"INTERNAL",allowPublicModel:false}')" \
    "${auth_headers[@]}")"
  check "WP1 create project" '.data.code == "'"$PROJECT_CODE"'" or .code == "CONFLICT"' "$project"

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
  echo "WP1-WP3 unified integration test passed."
}

main "$@"
