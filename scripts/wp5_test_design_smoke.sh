#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${WP5_SMOKE_BASE_URL:-http://127.0.0.1:8080}"
ASSET_API_BASE="${BASE_URL%/}/api/v1/asset"
TEST_DESIGN_API_BASE="${BASE_URL%/}/api/v1/test-design"
MANAGEMENT_API_BASE="${BASE_URL%/}/api/v1/management"
AUTH_API_BASE="${BASE_URL%/}/api/v1/auth"
PLATFORM_API_BASE="${BASE_URL%/}/api/v1"
ASSET_SERVICE_TOKEN="${WP3_SERVICE_TOKEN:-local-asset-token}"
TEST_DESIGN_SERVICE_TOKEN="${WP5_SERVICE_TOKEN:-local-test-design-token}"
PLATFORM_SERVICE_TOKEN="${WP1_SERVICE_TOKEN:-local-platform-service-token}"
CALLER_SERVICE="${WP5_SMOKE_CALLER_SERVICE:-wp5-test-design}"
DELEGATED_USER_ID="${WP5_SMOKE_DELEGATED_USER_ID:-user-wp5-smoke}"
PROJECT_ID="${WP5_SMOKE_PROJECT_ID:-wp5-smoke-$(date +%s)-$((RANDOM % 1000))}"
ADMIN_USERNAME="${WP5_SMOKE_ADMIN_USERNAME:-admin}"
ADMIN_PASSWORD="${WP5_SMOKE_ADMIN_PASSWORD:-AdminPass12345}"
ADMIN_CHANGED_PASSWORD="${WP5_SMOKE_ADMIN_NEW_PASSWORD:-AdminPass12345Changed!}"
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

platform_headers=(
  -H "Authorization: Bearer $PLATFORM_SERVICE_TOKEN"
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

patch_asset_json() {
  local path="$1"
  local body="$2"
  curl -fsS -X PATCH "$ASSET_API_BASE$path" \
    "${asset_headers[@]}" \
    -H 'Content-Type: application/json' \
    -d "$body"
}

get_asset_json() {
  local path="$1"
  curl -fsS "$ASSET_API_BASE$path" "${asset_headers[@]}"
}

post_json() {
  local url="$1"
  local body="$2"
  shift 2
  curl -sS -X POST "$url" "$@" -H 'Content-Type: application/json' -d "$body"
}

patch_json() {
  local url="$1"
  local body="$2"
  shift 2
  curl -sS -X PATCH "$url" "$@" -H 'Content-Type: application/json' -d "$body"
}

urlencode() {
  jq -nr --arg value "$1" '$value | @uri'
}

post_test_design_json() {
  local path="$1"
  local body="$2"
  curl -fsS -X POST "$TEST_DESIGN_API_BASE$path" \
    "${test_design_headers[@]}" \
    -H 'Content-Type: application/json' \
    -d "$body"
}

put_test_design_json() {
  local path="$1"
  local body="$2"
  curl -fsS -X PUT "$TEST_DESIGN_API_BASE$path" \
    "${test_design_headers[@]}" \
    -H 'Content-Type: application/json' \
    -d "$body"
}

get_test_design_json() {
  local path="$1"
  curl -fsS "$TEST_DESIGN_API_BASE$path" "${test_design_headers[@]}"
}

expect_test_design_http_error() {
  local path="$1"
  local body="$2"
  local expected_status="$3"
  local expected_code="$4"
  local body_file status payload
  body_file="$(mktemp)"
  status="$(curl -sS -o "$body_file" -w '%{http_code}' -X POST "$TEST_DESIGN_API_BASE$path" \
    "${test_design_headers[@]}" \
    -H 'Content-Type: application/json' \
    -d "$body")"
  payload="$(cat "$body_file")"
  rm -f "$body_file"
  jq -nc \
    --argjson httpStatus "$status" \
    --arg expectedStatus "$expected_status" \
    --arg expectedCode "$expected_code" \
    --argjson body "$payload" \
    '{
      httpStatus: $httpStatus,
      expectedStatus: ($expectedStatus | tonumber),
      expectedCode: $expectedCode,
      body: $body
    }'
}

wait_for_task_candidates() {
  local task_id="$1"
  local expected_count="$2"
  local attempts="${WP5_SMOKE_TASK_WAIT_ATTEMPTS:-60}"
  local delay="${WP5_SMOKE_TASK_WAIT_DELAY_SECONDS:-1}"
  local detail=""
  for ((attempt = 1; attempt <= attempts; attempt++)); do
    detail="$(curl -fsS "$TEST_DESIGN_API_BASE/tasks/$task_id" "${test_design_headers[@]}")"
    if printf '%s' "$detail" | jq -e \
      --argjson expected "$expected_count" \
      '.data.task.status == "SUCCEEDED" and (.data.candidates | length) == $expected and (.data.candidates | all(.status == "GENERATED"))' >/dev/null; then
      printf '%s' "$detail"
      return 0
    fi
    sleep "$delay"
  done
  printf '%s' "$detail"
  return 1
}

wait_for_task_publish_records() {
  local task_id="$1"
  local expected_total="$2"
  local expected_result="$3"
  local attempts="${WP5_SMOKE_PUBLISH_WAIT_ATTEMPTS:-60}"
  local delay="${WP5_SMOKE_PUBLISH_WAIT_DELAY_SECONDS:-1}"
  local records=""
  for ((attempt = 1; attempt <= attempts; attempt++)); do
    records="$(curl -fsS "$TEST_DESIGN_API_BASE/tasks/$task_id/publish-records" "${test_design_headers[@]}")"
    if printf '%s' "$records" | jq -e \
      --argjson expected "$expected_total" \
      --arg result "$expected_result" \
      '.data.total >= $expected and (.data.items | length) >= $expected and ([.data.items[] | select(.result == $result)] | length) >= $expected' >/dev/null; then
      printf '%s' "$records"
      return 0
    fi
    sleep "$delay"
  done
  printf '%s' "$records"
  return 1
}

wait_for_task_published() {
  local task_id="$1"
  local expected_published="$2"
  local attempts="${WP5_SMOKE_PUBLISH_WAIT_ATTEMPTS:-60}"
  local delay="${WP5_SMOKE_PUBLISH_WAIT_DELAY_SECONDS:-1}"
  local detail=""
  for ((attempt = 1; attempt <= attempts; attempt++)); do
    detail="$(curl -fsS "$TEST_DESIGN_API_BASE/tasks/$task_id" "${test_design_headers[@]}")"
    if printf '%s' "$detail" | jq -e \
      --argjson expected "$expected_published" \
      '.data.task.status == "PUBLISHED" and .data.task.publishedCount >= $expected and (.data.candidates | map(select(.status == "PUBLISHED")) | length) >= $expected and (.data.publishRecords | length) >= $expected' >/dev/null; then
      printf '%s' "$detail"
      return 0
    fi
    sleep "$delay"
  done
  printf '%s' "$detail"
  return 1
}

login_admin() {
  local password="$1"
  post_json "$AUTH_API_BASE/login" "$(jq -nc \
    --arg username "$ADMIN_USERNAME" \
    --arg password "$password" \
    '{username:$username,password:$password}')"
}

get_platform_context() {
  local project_key="$1"
  curl -sS "$PLATFORM_API_BASE/contexts/projects/$(urlencode "$project_key")" "${platform_headers[@]}"
}

prepare_project() {
  if [[ "${WP5_SMOKE_PREPARE_PROJECT:-1}" != "1" ]]; then
    return
  fi

  local login token project activated project_context project_resource_id
  login="$(login_admin "$ADMIN_PASSWORD")"
  token="$(printf '%s' "$login" | jq -r '.data.accessToken // empty')"
  if [[ -z "$token" && "$ADMIN_CHANGED_PASSWORD" != "$ADMIN_PASSWORD" ]]; then
    login="$(login_admin "$ADMIN_CHANGED_PASSWORD")"
    token="$(printf '%s' "$login" | jq -r '.data.accessToken // empty')"
    if [[ -n "$token" ]]; then
      ADMIN_PASSWORD="$ADMIN_CHANGED_PASSWORD"
    fi
  fi
  check "Login smoke admin" '.data.accessToken | type == "string"' "$login"

  if printf '%s' "$login" | jq -e '(.data.mustChangePassword == true) or (.data.must_change_password == true)' >/dev/null; then
    local password_change
    password_change="$(post_json "$AUTH_API_BASE/change-password" "$(jq -nc \
      --arg oldPassword "$ADMIN_PASSWORD" \
      --arg newPassword "$ADMIN_CHANGED_PASSWORD" \
      '{oldPassword:$oldPassword,newPassword:$newPassword}')" \
      -H "Authorization: Bearer $token")"
    check "Change initial admin password" '.code == "OK" and .data.passwordChanged == true' "$password_change"
    ADMIN_PASSWORD="$ADMIN_CHANGED_PASSWORD"
    login="$(login_admin "$ADMIN_PASSWORD")"
    token="$(printf '%s' "$login" | jq -r '.data.accessToken // empty')"
    check "Login smoke admin after password change" '.data.accessToken | type == "string"' "$login"
  fi

  project="$(post_json "$MANAGEMENT_API_BASE/projects" "$(jq -nc \
    --arg code "$PROJECT_ID" \
    '{code:$code,name:"WP5 smoke project",sensitivityLevel:"INTERNAL",allowPublicModel:false}')" \
    -H "Authorization: Bearer $token")"
  check "Prepare WP5 smoke project" '(.code == "OK" and .data.name == "WP5 smoke project") or .code == "CONFLICT"' "$project"
  activated="$(patch_json "$MANAGEMENT_API_BASE/projects/$(urlencode "$PROJECT_ID")/status" '{"status":"ACTIVE"}' \
    -H "Authorization: Bearer $token")"
  check "Activate WP5 smoke project" '.code == "OK"' "$activated"
  project_context="$(get_platform_context "$PROJECT_ID")"
  check "Resolve WP5 smoke project context" '.code == "OK" and .data.status == "ACTIVE" and (.data.resourceId | type == "string")' "$project_context"
  project_resource_id="$(printf '%s' "$project_context" | jq -r '.data.resourceId // empty')"
  if [[ "$FAIL" -gt 0 ]]; then
    echo "WP5 smoke setup failed: pass=$PASS fail=$FAIL" >&2
    exit 2
  fi
  PROJECT_ID="$project_resource_id"
}

validate_release_readiness_policy() {
  local health_payload="$1"
  if printf '%s' "$health_payload" | jq -e '.data.releaseReadinessPolicy.publishBlockingEnabled == true' >/dev/null; then
    check "Release readiness policy is blocking" \
      '.data.releaseReadinessPolicy.decisionMode == "BLOCKING_QUALITY_GATE" and .data.releaseReadinessPolicy.advisoryOnly == false and .data.releaseReadinessPolicy.publishBlockingEnabled == true and .data.releaseReadinessPolicy.aggregateOnly == true' \
      "$health_payload"
  else
    check "Release readiness policy is advisory by default" \
      '.data.releaseReadinessPolicy.decisionMode == "ADVISORY_QUALITY_GATE" and .data.releaseReadinessPolicy.advisoryOnly == true and .data.releaseReadinessPolicy.publishBlockingEnabled == false and .data.releaseReadinessPolicy.aggregateOnly == true' \
      "$health_payload"
  fi
}

release_readiness_publish_blocking_enabled() {
  local health_payload="$1"
  printf '%s' "$health_payload" | jq -e '.data.releaseReadinessPolicy.publishBlockingEnabled == true' >/dev/null
}

validate_release_readiness_blocking() {
  local requirement api task candidates candidate_id candidate update_body updated confirm deleted first_publish restored quality dry_run records_before blocked_retry records_after case_lookup
  local requirement_id api_id task_id source_ref
  requirement="$(post_asset_json /requirements "$(jq -nc \
    --arg projectId "$PROJECT_ID" \
    '{projectId:$projectId,title:"WP5 smoke 发布准出阻断需求",description:"正式发布前必须通过聚合质量准出",priority:"HIGH",acceptanceCriteria:"质量阻断时不得写入 WP3 用例",tags:"wp5,release-gate"}')")"
  check "Create release-readiness seed requirement" '.data.id != null and .data.projectId != null' "$requirement"
  requirement_id="$(printf '%s' "$requirement" | jq -r '.data.id')"

  api="$(post_asset_json /apis "$(jq -nc \
    --arg projectId "$PROJECT_ID" \
    --arg path "/wp5-smoke/release-gate/$RANDOM" \
    '{projectId:$projectId,summary:"WP5 release gate smoke API",description:"publish failure seed",httpMethod:"POST",path:$path,version:"1.0.0",requestSchema:"{}",responseSchema:"{}",status:"ACTIVE"}')")"
  check "Create release-readiness seed API" '.data.id != null and .data.lifecycleStatus == "ACTIVE"' "$api"
  api_id="$(printf '%s' "$api" | jq -r '.data.id')"

  task="$(post_test_design_json /tasks "$(jq -nc \
    --arg projectId "$PROJECT_ID" \
    --arg requirementId "$requirement_id" \
    '{projectId:$projectId,environmentKey:"qa",title:"WP5 release gate smoke generation",requirementIds:[$requirementId],coverageTypes:["SMOKE"]}')")"
  check "Queue release-readiness seed task" '.data.task.id != null and (.data.task.status == "QUEUED" or .data.task.status == "RUNNING" or .data.task.status == "SUCCEEDED")' "$task"
  task_id="$(printf '%s' "$task" | jq -r '.data.task.id')"
  if ! task="$(wait_for_task_candidates "$task_id" 1)"; then
    check "Create release-readiness candidate" '.data.task.status == "SUCCEEDED" and (.data.candidates | length) == 1 and .data.candidates[0].status == "GENERATED"' "$task"
  else
    check "Create release-readiness candidate" '.data.task.status == "SUCCEEDED" and (.data.candidates | length) == 1 and .data.candidates[0].status == "GENERATED"' "$task"
  fi

  candidates="$(get_test_design_json "/tasks/$task_id/candidates")"
  check "Release-readiness candidate page" '.data.total == 1 and (.data.items[0].steps | length) >= 2' "$candidates"
  candidate="$(printf '%s' "$candidates" | jq -c '.data.items[0]')"
  candidate_id="$(printf '%s' "$candidate" | jq -r '.id')"
  source_ref="wp5:$candidate_id"

  update_body="$(jq -nc \
    --argjson candidate "$candidate" \
    --arg apiId "$api_id" \
    '{
      title: $candidate.title,
      description: $candidate.description,
      apiId: $apiId,
      coverageType: $candidate.coverageType,
      priority: $candidate.priority,
      preconditions: $candidate.preconditions,
      steps: ($candidate.steps | map({action, expectedResult})),
      expectedResult: $candidate.expectedResult,
      tags: $candidate.tags,
      version: $candidate.version
    }')"
  updated="$(put_test_design_json "/candidates/$candidate_id" "$update_body")"
  check "Attach API to release-readiness candidate" '.data.apiId != null and .data.status == "EDITED"' "$updated"

  confirm="$(post_test_design_json "/candidates/$candidate_id/confirm" "$(jq -nc \
    --argjson version "$(printf '%s' "$updated" | jq '.data.version')" \
    '{version:$version,comment:"WP5 release gate smoke confirm"}')")"
  check "Confirm release-readiness candidate" '.data.status == "CONFIRMED" and .data.apiId != null' "$confirm"

  deleted="$(patch_asset_json "/apis/$api_id/lifecycle" '{"lifecycleStatus":"DELETED","reason":"WP5 release gate smoke failure seed"}')"
  check "Delete release-readiness seed API" '.data.lifecycleStatus == "DELETED" and .data.deletedAt != null' "$deleted"

  first_publish="$(post_test_design_json "/tasks/$task_id/publish" '{}')"
  check "Seed publish accepted for async API failure" \
    '.data.dryRun == false and .data.total == 1 and .data.created == 0 and (.data.createdCaseIds | length) == 0 and .data.records[0].result == "QUEUED" and .data.records[0].candidateStatus == "PUBLISH_QUEUED"' \
    "$first_publish"
  first_publish="$(wait_for_task_publish_records "$task_id" 1 "FAILED")"
  check "Seed publish records API failure" \
    '.data.total >= 1 and (.data.items | any(.result == "FAILED" and .action == "CREATE" and (.errorMessage | contains("API不存在"))))' \
    "$first_publish"

  restored="$(patch_asset_json "/apis/$api_id/lifecycle" '{"lifecycleStatus":"ACTIVE","reason":"WP5 release gate smoke restore before retry"}')"
  check "Restore release-readiness seed API" '.data.lifecycleStatus == "ACTIVE" and .data.deletedAt == null' "$restored"

  quality="$(get_test_design_json "/tasks/$task_id/quality/summary")"
  check "Release-readiness quality is blocked after failed publish" \
    '.data.readiness.status == "BLOCKED" and .data.readiness.blockingCount >= 1 and (.data.readiness.checks | any(.code == "errorPresent" and .status == "FAILED" and .severity == "BLOCKING"))' \
    "$quality"

  dry_run="$(post_test_design_json "/tasks/$task_id/publish-dry-run" '{}')"
  check "Blocked task dryRun remains diagnostic" \
    '.data.dryRun == true and .data.total == 1 and (.data.records | length) == 1 and (.data.records[0].result == "PLANNED" or .data.records[0].result == "CONFLICT")' \
    "$dry_run"

  records_before="$(get_test_design_json "/tasks/$task_id/publish-records")"
  check "Publish records before blocked retry" '.data.total == 1 and .data.items[0].result == "FAILED"' "$records_before"

  blocked_retry="$(expect_test_design_http_error "/tasks/$task_id/publish" '{}' 409 INVALID_STATE)"
  check "Blocked formal publish returns INVALID_STATE" \
    '.httpStatus == .expectedStatus and .body.code == .expectedCode and (.body.message | contains("WP5 发布准出质量门禁不通过")) and (.body.message | contains("readiness=BLOCKED"))' \
    "$blocked_retry"

  records_after="$(get_test_design_json "/tasks/$task_id/publish-records")"
  check "Blocked retry does not append publish record" '.data.total == 1 and .data.items[0].result == "FAILED"' "$records_after"

  case_lookup="$(get_asset_json "/test-cases?projectId=$PROJECT_ID&source=AI_GENERATED&keyword=$(urlencode "$source_ref")")"
  check "Blocked retry does not create WP3 case" '.data.total == 0' "$case_lookup"
}

main() {
  require_tool curl
  require_tool jq

  echo "== WP5 test-design smoke =="
  echo "baseUrl=$BASE_URL project=$PROJECT_ID"

  local health project_policy env_policy pending_effective approved_project approved_env effective_policy overrides requirement requirement_id task task_id candidates candidate_targets confirm calibration_task calibration_task_id calibration_candidates calibration_targets calibration_reject calibration_ignore corpus_summary dry_run asset_before publish scope_summary case_id case_asset links
  health="$(curl -fsS "$TEST_DESIGN_API_BASE/health")"
  check "WP5 health" '.data.service == "test-design" and .data.status == "UP" and .data.generationEnabled == true' "$health"
  validate_release_readiness_policy "$health"
  prepare_project
  if release_readiness_publish_blocking_enabled "$health"; then
    validate_release_readiness_blocking
  fi

  project_policy="$(post_test_design_json "/context-policies/projects/$(urlencode "$PROJECT_ID")/overrides" "$(jq -nc \
    '{contextExistingCasesPerRequirement:3,contextRequirementDescriptionChars:321,changeReasonCode:"SMOKE_VALIDATION"}')")"
  check "Request project context policy override" \
    '.data.status == "PENDING" and .data.overrideLimits.existingCasesPerRequirement == 3 and .data.overrideLimits.requirementDescriptionChars == 321 and .data.changeReasonCodeCaptured == true and (.data.changeReasonCode | not)' \
    "$project_policy"

  pending_effective="$(get_test_design_json "/context-policies/projects/$(urlencode "$PROJECT_ID")/effective?environmentKey=qa")"
  check "Pending context policy override is not effective" \
    '(.data.appliedOverrideScopes | index("PROJECT") | not) and (.data.appliedOverrideScopes | index("ENVIRONMENT") | not) and .data.overrideStatusCounts.PENDING == 1 and .data.contextPolicyGovernance.policySource == "PLATFORM_DEFAULT" and .data.contextPolicyGovernance.governanceStatus == "OVERRIDE_STORE_READY" and .data.contextPolicyOperations.projectOverrideStoreReady == true and .data.aggregateOnly == true and .data.policyBodyExported == false and .data.policyDiffPreviewExported == false and .data.approvalNotesExported == false and .data.ticketUrlExported == false' \
    "$pending_effective"

  approved_project="$(post_test_design_json "/context-policies/overrides/$(printf '%s' "$project_policy" | jq -r '.data.id')/approve" "$(jq -nc \
    '{approvalReasonCode:"QUALITY_BASELINE"}')")"
  check "Approve project context policy override" \
    '.data.status == "APPROVED" and .data.approvalReasonCodeCaptured == true and (.data.approvalReasonCode | not)' \
    "$approved_project"

  env_policy="$(post_test_design_json "/context-policies/projects/$(urlencode "$PROJECT_ID")/environments/qa/overrides" "$(jq -nc \
    '{contextExistingCasesPerRequirement:1,contextAssetSchemaChars:111,changeReasonCode:"SMOKE_VALIDATION"}')")"
  approved_env="$(post_test_design_json "/context-policies/overrides/$(printf '%s' "$env_policy" | jq -r '.data.id')/approve" "$(jq -nc \
    '{approvalReasonCode:"PROJECT_COMPLEXITY"}')")"
  check "Approve environment context policy override" \
    '.data.status == "APPROVED" and .data.overrideLimits.existingCasesPerRequirement == 1 and .data.overrideLimits.linkedAssetSchemaChars == 111' \
    "$approved_env"

  effective_policy="$(get_test_design_json "/context-policies/projects/$(urlencode "$PROJECT_ID")/effective?environmentKey=qa")"
  check "Effective context policy applies project then environment" \
    '.data.contextLimits.existingCasesPerRequirement == 1 and .data.contextLimits.requirementDescriptionChars == 321 and .data.contextLimits.linkedAssetSchemaChars == 111 and .data.appliedOverrideScopes == ["PLATFORM_DEFAULT","PROJECT","ENVIRONMENT"] and .data.contextPolicyGovernance.policySource == "PROJECT_ENVIRONMENT_OVERRIDE" and .data.contextPolicyGovernance.governanceStatus == "OVERRIDE_APPROVED" and .data.contextPolicyOperations.operationMode == "PROJECT_ENVIRONMENT_OVERRIDE" and .data.contextPolicyOperations.policyResolutionOrder == "PLATFORM_DEFAULT_PROJECT_ENVIRONMENT" and .data.contextPolicyOperations.projectOverrideStoreReady == true and .data.contextPolicyOperations.environmentOverrideStoreReady == true and .data.policyBodyExported == false and .data.policyDiffPreviewExported == false' \
    "$effective_policy"

  overrides="$(get_test_design_json "/context-policies/projects/$(urlencode "$PROJECT_ID")/overrides?environmentKey=qa")"
  check "Context policy overrides are sanitized" \
    '.data | length == 2 and all(.[]; (.status == "APPROVED") and (.changeReasonCodeCaptured == true) and (.approvalReasonCodeCaptured == true) and (.changeReasonCode | not) and (.approvalReasonCode | not) and (.policyBody | not) and (.policyDiff | not) and (.approvalNotes | not) and (.ticketUrl | not))' \
    "$overrides"

  requirement="$(post_asset_json /requirements "$(jq -nc \
    --arg projectId "$PROJECT_ID" \
    '{projectId:$projectId,title:"WP5 smoke 登录需求",description:"支持账号密码登录",priority:"HIGH",acceptanceCriteria:"登录成功后进入工作台",tags:"wp5,smoke"}')")"
  check "Create WP3 requirement" '.data.id != null and .data.projectId != null' "$requirement"
  requirement_id="$(printf '%s' "$requirement" | jq -r '.data.id')"

  task="$(post_test_design_json /tasks "$(jq -nc \
    --arg projectId "$PROJECT_ID" \
    --arg requirementId "$requirement_id" \
    '{projectId:$projectId,environmentKey:"qa",title:"WP5 smoke generation",requirementIds:[$requirementId],coverageTypes:["SMOKE","EXCEPTION"]}')")"
  check "Queue WP5 task" '(.data.task.status == "QUEUED" or .data.task.status == "RUNNING" or .data.task.status == "SUCCEEDED") and (.data.task.generatedCount | type == "number") and .data.task.contextSummary.limits.existingCasesPerRequirement == 1 and .data.task.contextSummary.limits.requirementDescriptionChars == 321 and .data.task.contextSummary.limits.linkedAssetSchemaChars == 111 and .data.task.contextSummary.policyOperations.approvedOverrideApplied == true' "$task"
  task_id="$(printf '%s' "$task" | jq -r '.data.task.id')"
  if ! task="$(wait_for_task_candidates "$task_id" 2)"; then
    check "Create WP5 task with candidates" '.data.task.status == "SUCCEEDED" and (.data.candidates | length) == 2 and (.data.candidates | all(.status == "GENERATED"))' "$task"
  else
    check "Create WP5 task with candidates" '.data.task.status == "SUCCEEDED" and (.data.candidates | length) == 2 and (.data.candidates | all(.status == "GENERATED"))' "$task"
  fi

  candidates="$(curl -fsS "$TEST_DESIGN_API_BASE/tasks/$task_id/candidates" "${test_design_headers[@]}")"
  check "Candidate page" '.data.total == 2 and (.data.items | all(.steps | length >= 3))' "$candidates"
  candidate_targets="$(printf '%s' "$candidates" | jq -c '[.data.items[] | {id, version}]')"

  confirm="$(post_test_design_json /candidates/batch-action "$(jq -nc --argjson candidates "$candidate_targets" '{action:"CONFIRM",candidates:$candidates}')")"
  check "Batch confirm candidates" '.data.succeededCount == 2 and .data.failedCount == 0 and (.data.items | all(.candidate.status == "CONFIRMED"))' "$confirm"

  calibration_task="$(post_test_design_json /tasks "$(jq -nc \
    --arg projectId "$PROJECT_ID" \
    --arg requirementId "$requirement_id" \
    '{projectId:$projectId,environmentKey:"qa",title:"WP5 evaluation corpus calibration",requirementIds:[$requirementId],coverageTypes:["BOUNDARY","EXCEPTION"]}')")"
  check "Queue evaluation corpus calibration task" '(.data.task.status == "QUEUED" or .data.task.status == "RUNNING" or .data.task.status == "SUCCEEDED")' "$calibration_task"
  calibration_task_id="$(printf '%s' "$calibration_task" | jq -r '.data.task.id')"
  if ! calibration_task="$(wait_for_task_candidates "$calibration_task_id" 2)"; then
    check "Create evaluation corpus calibration candidates" '.data.task.status == "SUCCEEDED" and (.data.candidates | length) == 2 and (.data.candidates | all(.status == "GENERATED"))' "$calibration_task"
  else
    check "Create evaluation corpus calibration candidates" '.data.task.status == "SUCCEEDED" and (.data.candidates | length) == 2 and (.data.candidates | all(.status == "GENERATED"))' "$calibration_task"
  fi
  calibration_candidates="$(get_test_design_json "/tasks/$calibration_task_id/candidates")"
  calibration_targets="$(printf '%s' "$calibration_candidates" | jq -c '[.data.items[] | {id, version}]')"
  calibration_reject="$(post_test_design_json "/candidates/$(printf '%s' "$calibration_targets" | jq -r '.[0].id')/reject" "$(jq -nc \
    --argjson version "$(printf '%s' "$calibration_targets" | jq '.[0].version')" \
    '{version:$version,reason:"WP5_EVALUATION_CORPUS_REJECT",comment:"WP5 evaluation corpus reject explanation"}')")"
  check "Reject evaluation corpus calibration candidate" '.data.status == "REJECTED"' "$calibration_reject"
  calibration_ignore="$(post_test_design_json "/candidates/$(printf '%s' "$calibration_targets" | jq -r '.[1].id')/ignore" "$(jq -nc \
    --argjson version "$(printf '%s' "$calibration_targets" | jq '.[1].version')" \
    '{version:$version,reason:"WP5_EVALUATION_CORPUS_IGNORE",comment:"WP5 evaluation corpus ignore explanation"}')")"
  check "Ignore evaluation corpus calibration candidate" '.data.status == "IGNORED"' "$calibration_ignore"

  corpus_summary="$(get_test_design_json "/quality/evaluation-corpus-summary?projectId=$(urlencode "$PROJECT_ID")&promptKey=wp5-test-design-v1")"
  if printf '%s' "$corpus_summary" | jq -e --arg projectId "$PROJECT_ID" \
    '.data.projectId == $projectId and .data.promptKey == "wp5-test-design-v1" and .data.policy.policyVersion == "wp5-evaluation-corpus-policy-v1" and .data.policy.corpusMode == "GOLDEN_SET_BASELINE" and .data.policy.qualityGateMode == "MANUAL_OPT_IN_AI_EVAL" and .data.taskCount >= 1 and .data.candidateCount >= 2 and .data.promptVersionCount >= 1 and (.data.readinessDistribution | length) >= 1 and .data.feedbackSignalCount >= 2 and .data.sampleCandidateCount >= 2 and .data.sampleExplanationCount >= 2 and .data.aggregateOnly == true and .data.corpusRowExported == false and .data.candidateBodyExported == false and .data.reviewCommentExported == false and .data.promptBodyExported == false' >/dev/null; then
    echo "   PASS Evaluation corpus operations summary is aggregate-only"
    PASS=$((PASS + 1))
  else
    echo "   FAIL Evaluation corpus operations summary is aggregate-only"
    echo "$corpus_summary"
    FAIL=$((FAIL + 1))
  fi

  dry_run="$(post_test_design_json "/tasks/$task_id/publish-dry-run" '{}')"
  check "Publish dryRun does not create cases" '.data.dryRun == true and .data.created == 2 and (.data.createdCaseIds | length) == 0 and (.data.records | all(.result == "PLANNED"))' "$dry_run"
  asset_before="$(get_asset_json "/test-cases?projectId=$PROJECT_ID&source=AI_GENERATED")"
  check "No WP3 write after dryRun" '.data.total == 0' "$asset_before"

  publish="$(post_test_design_json "/tasks/$task_id/publish" '{}')"
  check "Publish accepted for async WP3 write" '.data.dryRun == false and .data.created == 0 and (.data.createdCaseIds | length) == 0 and (.data.records | length) == 2 and (.data.records | all(.result == "QUEUED" and .candidateStatus == "PUBLISH_QUEUED"))' "$publish"
  publish="$(wait_for_task_published "$task_id" 2)"
  check "Publish creates WP3 cases" '.data.task.status == "PUBLISHED" and .data.task.publishedCount == 2 and ([.data.publishRecords[] | select(.result == "SUCCEEDED")] | length) >= 2' "$publish"

  scope_summary="$(get_test_design_json "/quality/scope-summary?projectId=$(urlencode "$PROJECT_ID")&promptKey=wp5-test-design-v1")"
  if printf '%s' "$scope_summary" | jq -e --arg projectId "$PROJECT_ID" \
    '.data.projectId == $projectId and .data.promptKey == "wp5-test-design-v1" and .data.policy.policyVersion == "wp5-scope-policy-v1" and .data.policy.scopeModel == "PROJECT_RESOURCE_SCOPE" and .data.taskCount >= 1 and .data.candidateCount >= 2 and .data.publishRecordCount >= 2 and .data.projectBucketCount >= 1 and .data.candidateScopeMismatchCount == 0 and .data.publishScopeMismatchCount == 0 and .data.candidateScopeCoveragePercent == 100 and .data.publishScopeCoveragePercent == 100 and (.data.metrics | any(.code == "scopeMismatches" and .count == 0)) and (.data.readiness | any(.code == "detailIdentifiersRedacted" and .ready == true)) and .data.aggregateOnly == true and .data.candidateIdentifierListExported == false and .data.roleRuleDetailExported == false and .data.serviceTokenValueExported == false' >/dev/null; then
    echo "   PASS Scope operations summary is aggregate-only"
    PASS=$((PASS + 1))
  else
    echo "   FAIL Scope operations summary is aggregate-only"
    echo "$scope_summary"
    FAIL=$((FAIL + 1))
  fi

  case_id="$(printf '%s' "$publish" | jq -r '.data.publishRecords[] | select(.result == "SUCCEEDED" and .assetCaseId != null) | .assetCaseId' | head -n 1)"

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
