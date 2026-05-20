#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${WP3_SMOKE_BASE_URL:-http://127.0.0.1:8080}"
API_BASE="${BASE_URL%/}/api/v1/asset"
SERVICE_TOKEN="${WP3_SERVICE_TOKEN:-local-asset-token}"
CALLER_SERVICE="${WP3_SMOKE_CALLER_SERVICE:-wp3-smoke}"
DELEGATED_USER_ID="${WP3_SMOKE_DELEGATED_USER_ID:-user-wp3-smoke}"
PROJECT_ID="${WP3_SMOKE_PROJECT_ID:-project-wp3-smoke-$(date +%s)-$RANDOM}"
PASS=0
FAIL=0

require_tool() {
  local tool="$1"
  if ! command -v "$tool" >/dev/null 2>&1; then
    echo "$tool is required for WP3 asset smoke test" >&2
    exit 127
  fi
}

urlencode() {
  jq -nr --arg value "$1" '$value | @uri'
}

headers=(
  -H "Authorization: Bearer $SERVICE_TOKEN"
  -H "X-Caller-Service: $CALLER_SERVICE"
  -H "X-Delegated-User-Id: $DELEGATED_USER_ID"
)

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
  curl -fsS -X POST "$API_BASE$path" "${headers[@]}" -H 'Content-Type: application/json' -d "$body"
}

put_json() {
  local path="$1"
  local body="$2"
  curl -fsS -X PUT "$API_BASE$path" "${headers[@]}" -H 'Content-Type: application/json' -d "$body"
}

get_json() {
  local path="$1"
  curl -fsS "$API_BASE$path" "${headers[@]}"
}

expect_http_error() {
  local name="$1"
  local expected_status="$2"
  local expected_code="$3"
  shift 3
  local body_file status body
  body_file="$(mktemp -t wp3-smoke-error.XXXXXX.json)"
  status="$(curl -sS -o "$body_file" -w '%{http_code}' "$@")"
  body="$(cat "$body_file")"
  rm -f "$body_file"
  if [[ "$status" == "$expected_status" ]] && printf '%s' "$body" | jq -e --arg code "$expected_code" '.code == $code' >/dev/null; then
    echo "   PASS $name"
    PASS=$((PASS + 1))
  else
    echo "   FAIL $name"
    echo "status=$status expected=$expected_status"
    echo "$body"
    FAIL=$((FAIL + 1))
  fi
}

main() {
  require_tool curl
  require_tool jq

  echo "== WP3 asset smoke =="
  echo "baseUrl=$BASE_URL project=$PROJECT_ID"

  local health requirement req_id list detail approved denied api api_id test_case case_id link links user_list
  health="$(curl -fsS "$API_BASE/health")"
  check "WP3 health" '.data.service == "asset-service" and .data.status == "UP"' "$health"

  requirement="$(post_json /requirements "$(jq -nc \
    --arg projectId "$PROJECT_ID" \
    '{projectId:$projectId,title:"WP3 smoke requirement",description:"Asset smoke",priority:"HIGH",tags:"smoke,wp3"}')")"
  check "Create requirement" '.data.title == "WP3 smoke requirement" and .data.status == "DRAFT" and (.data.code | startswith("REQ-"))' "$requirement"
  req_id="$(printf '%s' "$requirement" | jq -r '.data.id')"

  list="$(get_json "/requirements?projectId=$(urlencode "$PROJECT_ID")&status=DRAFT&keyword=$(urlencode smoke)&index=0&size=10")"
  check_arg "Requirement pagination and filters" id "$req_id" '.data.index == 0 and .data.size == 10 and .data.total == 1 and .data.items[0].id == $id' "$list"

  detail="$(get_json "/requirements/$req_id")"
  check_arg "Requirement detail" id "$req_id" '.data.id == $id and .data.priority == "HIGH"' "$detail"

  approved="$(put_json "/requirements/$req_id" "$(jq -nc '{title:"WP3 smoke requirement approved",description:"Approved",status:"APPROVED",priority:"HIGH",tags:"smoke,wp3"}')")"
  check "Requirement status transition" '.data.status == "APPROVED" and .data.title == "WP3 smoke requirement approved"' "$approved"

  denied="$(jq -nc '{title:"WP3 smoke requirement denied",description:"Denied",status:"REVIEWING",priority:"HIGH",tags:"smoke,wp3"}')"
  expect_http_error "Illegal status transition rejected" "409" "INVALID_STATE" \
    -X PUT "$API_BASE/requirements/$req_id" "${headers[@]}" -H 'Content-Type: application/json' -d "$denied"

  api="$(post_json /apis "$(jq -nc \
    --arg projectId "$PROJECT_ID" \
    '{projectId:$projectId,summary:"WP3 smoke API",httpMethod:"GET",path:"/wp3/smoke",status:"ACTIVE"}')")"
  check "Create API asset" '.data.summary == "WP3 smoke API" and .data.status == "ACTIVE" and (.data.code | startswith("API-"))' "$api"
  api_id="$(printf '%s' "$api" | jq -r '.data.id')"

  test_case="$(post_json /test-cases "$(jq -nc \
    --arg projectId "$PROJECT_ID" \
    --arg reqId "$req_id" \
    --arg apiId "$api_id" \
    '{projectId:$projectId,requirementId:$reqId,apiId:$apiId,title:"WP3 smoke case",priority:"MEDIUM",steps:[{action:"Call API",expectedResult:"200 OK"}]}')")"
  check_arg "Create test case" reqId "$req_id" '.data.title == "WP3 smoke case" and .data.requirementId == $reqId and (.data.code | startswith("TC-"))' "$test_case"
  case_id="$(printf '%s' "$test_case" | jq -r '.data.id')"

  link="$(post_json /links "$(jq -nc --arg reqId "$req_id" --arg apiId "$api_id" --arg caseId "$case_id" '{requirementId:$reqId,apiId:$apiId,caseId:$caseId}')")"
  check_arg "Create trace link" reqId "$req_id" '.data.requirementId == $reqId' "$link"

  links="$(get_json "/links?requirementId=$(urlencode "$req_id")")"
  check_arg "Trace link query" caseId "$case_id" '.data.total == 1 and .data.items[0].caseId == $caseId' "$links"

  if [[ -n "${WP3_USER_TOKEN:-}" ]]; then
    user_list="$(curl -fsS "$API_BASE/requirements?projectId=$(urlencode "$PROJECT_ID")" -H "Authorization: Bearer $WP3_USER_TOKEN")"
    check_arg "User bearer token asset read" id "$req_id" '.data.total >= 1 and (.data.items | any(.id == $id))' "$user_list"
  else
    echo "   SKIP user bearer token asset read; set WP3_USER_TOKEN to cover user RBAC in smoke"
  fi

  echo "== summary =="
  echo "pass=$PASS fail=$FAIL total=$((PASS + FAIL))"
  if [[ "$FAIL" -ne 0 ]]; then
    exit 1
  fi
  echo "WP3 asset smoke passed for project_id=$PROJECT_ID."
}

main "$@"
