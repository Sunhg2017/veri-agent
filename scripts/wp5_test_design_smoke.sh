#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${WP5_SMOKE_BASE_URL:-http://127.0.0.1:8080}"
ASSET_API_BASE="${BASE_URL%/}/api/v1/asset"
TEST_DESIGN_API_BASE="${BASE_URL%/}/api/v1/test-design"
ASSET_SERVICE_TOKEN="${WP3_SERVICE_TOKEN:-local-asset-token}"
TEST_DESIGN_SERVICE_TOKEN="${WP5_SERVICE_TOKEN:-local-test-design-token}"
CALLER_SERVICE="${WP5_SMOKE_CALLER_SERVICE:-wp5-test-design}"
DELEGATED_USER_ID="${WP5_SMOKE_DELEGATED_USER_ID:-user-wp5-smoke}"
PROJECT_ID="${WP5_SMOKE_PROJECT_ID:-project-wp5-smoke-$(date +%s)-$RANDOM}"
PASS=0
FAIL=0

require_tool() {
  local tool="$1"
  if ! command -v "$tool" >/dev/null 2>&1; then
    echo "$tool is required for WP5 smoke test" >&2
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

asset_headers=(
  -H "Authorization: Bearer $ASSET_SERVICE_TOKEN"
  -H "X-Caller-Service: $CALLER_SERVICE"
  -H "X-Delegated-User-Id: $DELEGATED_USER_ID"
)

test_design_headers=(
  -H "Authorization: Bearer $TEST_DESIGN_SERVICE_TOKEN"
  -H "X-Caller-Service: $CALLER_SERVICE"
  -H "X-Delegated-User-Id: $DELEGATED_USER_ID"
)

post_asset_json() {
  local path="$1"
  local body="$2"
  curl -fsS -X POST "$ASSET_API_BASE$path" \
    "${asset_headers[@]}" \
    -H 'Content-Type: application/json' \
    -d "$body"
}

get_asset_json() {
  local path="$1"
  curl -fsS "$ASSET_API_BASE$path" "${asset_headers[@]}"
}

post_test_design_json() {
  local path="$1"
  local body="$2"
  curl -fsS -X POST "$TEST_DESIGN_API_BASE$path" \
    "${test_design_headers[@]}" \
    -H 'Content-Type: application/json' \
    -d "$body"
}

main() {
  require_tool curl
  require_tool jq

  echo "== WP5 test-design smoke =="
  echo "baseUrl=$BASE_URL project=$PROJECT_ID"

  local health requirement requirement_id task task_id candidates candidate_targets confirm dry_run asset_before publish case_id case_asset links
  health="$(curl -fsS "$TEST_DESIGN_API_BASE/health")"
  check "WP5 health" '.data.service == "test-design" and .data.status == "UP" and .data.generationEnabled == true' "$health"

  requirement="$(post_asset_json /requirements "$(jq -nc \
    --arg projectId "$PROJECT_ID" \
    '{projectId:$projectId,title:"WP5 smoke 登录需求",description:"支持账号密码登录",priority:"HIGH",acceptanceCriteria:"登录成功后进入工作台",tags:"wp5,smoke"}')")"
  check "Create WP3 requirement" '.data.id != null and .data.projectId != null' "$requirement"
  requirement_id="$(printf '%s' "$requirement" | jq -r '.data.id')"

  task="$(post_test_design_json /tasks "$(jq -nc \
    --arg projectId "$PROJECT_ID" \
    --arg requirementId "$requirement_id" \
    '{projectId:$projectId,title:"WP5 smoke generation",requirementIds:[$requirementId],coverageTypes:["SMOKE","EXCEPTION"]}')")"
  check "Create WP5 task with candidates" '.data.task.status == "SUCCEEDED" and (.data.candidates | length) == 2 and (.data.candidates | all(.status == "GENERATED"))' "$task"
  task_id="$(printf '%s' "$task" | jq -r '.data.task.id')"

  candidates="$(curl -fsS "$TEST_DESIGN_API_BASE/tasks/$task_id/candidates" "${test_design_headers[@]}")"
  check "Candidate page" '.data.total == 2 and (.data.items | all(.steps | length >= 3))' "$candidates"
  candidate_targets="$(printf '%s' "$candidates" | jq -c '[.data.items[] | {id, version}]')"

  confirm="$(post_test_design_json /candidates/batch-action "$(jq -nc --argjson candidates "$candidate_targets" '{action:"CONFIRM",candidates:$candidates}')")"
  check "Batch confirm candidates" '.data.succeededCount == 2 and .data.failedCount == 0 and (.data.items | all(.candidate.status == "CONFIRMED"))' "$confirm"

  dry_run="$(post_test_design_json "/tasks/$task_id/publish-dry-run" '{}')"
  check "Publish dryRun does not create cases" '.data.dryRun == true and .data.created == 2 and (.data.createdCaseIds | length) == 0 and (.data.records | all(.result == "PLANNED"))' "$dry_run"
  asset_before="$(get_asset_json "/test-cases?projectId=$PROJECT_ID&source=AI_GENERATED")"
  check "No WP3 write after dryRun" '.data.total == 0' "$asset_before"

  publish="$(post_test_design_json "/tasks/$task_id/publish" '{}')"
  check "Publish creates WP3 cases" '.data.dryRun == false and .data.created == 2 and (.data.createdCaseIds | length) == 2 and (.data.records | all(.result == "SUCCEEDED"))' "$publish"
  case_id="$(printf '%s' "$publish" | jq -r '.data.createdCaseIds[0]')"

  case_asset="$(get_asset_json "/test-cases/$case_id")"
  check "WP3 test case is AI generated" '.data.source == "AI_GENERATED" and (.data.sourceRef | startswith("wp5:")) and (.data.steps | length) >= 3' "$case_asset"

  links="$(get_asset_json "/links?requirementId=$requirement_id&caseId=$case_id")"
  check "Trace link created" '.data.total >= 1 and .data.items[0].caseId != null' "$links"

  if [[ "$FAIL" -gt 0 ]]; then
    echo "WP5 smoke failed: pass=$PASS fail=$FAIL" >&2
    exit 2
  fi
  echo "WP5 smoke passed: pass=$PASS fail=$FAIL"
}

main "$@"
