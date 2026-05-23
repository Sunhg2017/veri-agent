#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${WP4_SMOKE_BASE_URL:-http://127.0.0.1:8080}"
API_BASE="${BASE_URL%/}/api/v1/document-input"
ASSET_API_BASE="${BASE_URL%/}/api/v1/asset"
SERVICE_TOKEN="${WP4_SERVICE_TOKEN:-local-document-input-token}"
ASSET_SERVICE_TOKEN="${WP3_SERVICE_TOKEN:-local-asset-token}"
CALLER_SERVICE="${WP4_SMOKE_CALLER_SERVICE:-wp4-document-input}"
DELEGATED_USER_ID="${WP4_SMOKE_DELEGATED_USER_ID:-user-wp4-smoke}"
PROJECT_ID="${WP4_SMOKE_PROJECT_ID:-project-wp4-smoke-$(date +%s)-$RANDOM}"
WEBHOOK_SECRET="${WP4_WEBHOOK_SECRET:-local-document-input-webhook-secret}"
SOURCE_CODE="${WP4_SMOKE_SOURCE_CODE:-wp4-smoke-$(date +%s)-$RANDOM}"
PASS=0
FAIL=0

require_tool() {
  local tool="$1"
  if ! command -v "$tool" >/dev/null 2>&1; then
    echo "$tool is required for WP4 document-input smoke test" >&2
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

urlencode() {
  jq -nr --arg value "$1" '$value | @uri'
}

document_headers=(
  -H "Authorization: Bearer $SERVICE_TOKEN"
  -H "X-Caller-Service: $CALLER_SERVICE"
  -H "X-Delegated-User-Id: $DELEGATED_USER_ID"
)

asset_headers=(
  -H "Authorization: Bearer $ASSET_SERVICE_TOKEN"
  -H "X-Caller-Service: $CALLER_SERVICE"
  -H "X-Delegated-User-Id: $DELEGATED_USER_ID"
)

post_document_json() {
  local path="$1"
  local body="$2"
  curl -fsS -X POST "$API_BASE$path" \
    "${document_headers[@]}" \
    -H 'Content-Type: application/json' \
    -d "$body"
}

get_document_json() {
  local path="$1"
  curl -fsS "$API_BASE$path" "${document_headers[@]}"
}

get_asset_json() {
  local path="$1"
  curl -fsS "$ASSET_API_BASE$path" "${asset_headers[@]}"
}

webhook_signature() {
  local timestamp="$1"
  local event_id="$2"
  local idempotency_key="$3"
  local payload="$4"
  printf '%s' "$timestamp.$event_id.$idempotency_key.$payload" | openssl dgst -sha256 -hmac "$WEBHOOK_SECRET" -hex | awk '{print $NF}'
}

post_signed_webhook() {
  local source_code="$1"
  local payload="$2"
  local event_id="$3"
  local idempotency_key="$4"
  local timestamp signature
  timestamp="$(date +%s)"
  signature="$(webhook_signature "$timestamp" "$event_id" "$idempotency_key" "$payload")"
  curl -fsS -X POST "$API_BASE/webhooks/$(urlencode "$source_code")" \
    -H 'Content-Type: application/json' \
    -H "X-VA-Timestamp: $timestamp" \
    -H "X-VA-Signature: $signature" \
    -H "X-VA-Event-Id: $event_id" \
    -H "X-VA-Idempotency-Key: $idempotency_key" \
    -H "X-VA-Event-Version: 1.0" \
    -d "$payload"
}

expect_http_error() {
  local name="$1"
  local expected_status="$2"
  local expected_code="$3"
  shift 3
  local body_file status body
  body_file="$(mktemp -t wp4-smoke-error.XXXXXX.json)"
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
  require_tool openssl
  require_tool awk

  echo "== WP4 document-input smoke =="
  echo "baseUrl=$BASE_URL project=$PROJECT_ID sourceCode=$SOURCE_CODE"

  local health
  health="$(curl -fsS "$API_BASE/health")"
  check "WP4 health" '.data.service == "document-input" and .data.status == "UP" and .data.inputEnabled == true' "$health"
  local model_parse_enabled
  model_parse_enabled="$(printf '%s' "$health" | jq -r '.data.modelParseEnabled // false')"

  local markdown_import import_id candidates filtered_candidates candidate_ids candidate_targets batch dry_run publish requirement_id records asset
  markdown_import="$(post_document_json /imports "$(jq -nc \
    --arg projectId "$PROJECT_ID" \
    '{projectId:$projectId,sourceType:"MARKDOWN",sourceRef:"wp4-smoke-md",title:"WP4 smoke Markdown import",content:"## 登录需求\nPriority: HIGH\nTags: auth, smoke\nAcceptance Criteria:\n- 登录成功\n\n## 退出需求\nPriority: LOW\nTags: auth, smoke"}')")"
  check "Markdown import creates candidates" '.data.status == "SUCCEEDED" and .data.totalParsed == 2 and .data.pendingCount == 2' "$markdown_import"
  if [[ "$model_parse_enabled" == "true" ]]; then
    check "AI model parse requirements" '.data.requirements | all(.parseSource == "MODEL" and (.modelInvocationId | type == "string") and .modelProviderName == "local-echo-primary")' "$markdown_import"
  fi
  import_id="$(printf '%s' "$markdown_import" | jq -r '.data.id')"

  candidates="$(get_document_json "/imports/$import_id/candidates")"
  check "Candidate page" '.data.total == 2 and (.data.items | all(.status == "PENDING"))' "$candidates"
  if [[ "$model_parse_enabled" == "true" ]]; then
    check "AI model parse candidates" '.data.items | all(.parseSource == "MODEL" and (.modelInvocationId | type == "string") and .modelProviderName == "local-echo-primary")' "$candidates"
  fi
  filtered_candidates="$(get_document_json "/imports/$import_id/candidates?status=PENDING&sourceRef=wp4-smoke-md&keyword=$(urlencode "登录")")"
  check "Candidate filters" '.data.total == 1 and .data.items[0].title == "登录需求"' "$filtered_candidates"
  candidate_ids="$(printf '%s' "$candidates" | jq -c '[.data.items[].id]')"
  candidate_targets="$(printf '%s' "$candidates" | jq -c '[.data.items[] | {id, version}]')"

  batch="$(post_document_json /candidates/batch-action "$(jq -nc --argjson candidates "$candidate_targets" '{action:"CONFIRM",candidates:$candidates}')")"
  check "Versioned batch confirm" '.data.action == "CONFIRM" and .data.total == 2 and .data.succeededCount == 2 and .data.failedCount == 0 and (.data.items | all(.candidate.version == 1))' "$batch"

  dry_run="$(post_document_json "/imports/$import_id/publish" "$(jq -nc --argjson ids "$candidate_ids" '{dryRun:true,candidateIds:$ids}')")"
  check "Publish dryRun has no WP3 write" '.data.dryRun == true and .data.totalCreated == 0 and .data.plannedCreateCount == 2 and (.data.records | length) == 2 and (.data.records | all(.result == "PLANNED"))' "$dry_run"

  publish="$(post_document_json "/imports/$import_id/publish" '{}')"
  check "Publish confirmed candidates" '.data.dryRun == false and .data.totalCreated == 2 and .data.publishedCount == 2 and (.data.createdRequirementIds | length) == 2 and (.data.records | all(.candidateStatus == "PUBLISHED"))' "$publish"
  requirement_id="$(printf '%s' "$publish" | jq -r '.data.records[] | select(.externalRequirementId == "wp4-smoke-md#1") | .assetRequirementId' | head -n 1)"

  records="$(get_document_json "/imports/$import_id/publish-records")"
  check "Publish records" '.data.total == 2 and (.data.items | all(.candidateStatus == "PUBLISHED"))' "$records"

  asset="$(get_asset_json "/requirements/$requirement_id")"
  check_arg "WP3 requirement created from WP4" id "$requirement_id" '.data.id == $id and .data.status == "DRAFT" and .data.source == "IMPORT" and .data.sourceRef == "wp4-smoke-md#1" and (.data.acceptanceCriteria | contains("登录成功")) and (.data.tags | contains("document-input"))' "$asset"

  local update_import update_import_id update_candidates update_candidate_id update_candidate_version update_dry_run update_publish updated_asset
  update_import="$(post_document_json /imports "$(jq -nc \
    --arg projectId "$PROJECT_ID" \
    '{projectId:$projectId,sourceType:"MARKDOWN",sourceRef:"wp4-smoke-md",sourceUrl:"https://example.test/wp4-smoke-md-v2",title:"WP4 smoke Markdown update",content:"## 登录需求\n登录流程增加二次确认\nPriority: CRITICAL\nTags: auth, smoke, update\nAcceptance Criteria:\n- 登录成功\n- 二次确认成功"}')")"
  check "Repeated import creates update candidate" '.data.status == "SUCCEEDED" and .data.totalParsed >= 1 and .data.pendingCount >= 1' "$update_import"
  update_import_id="$(printf '%s' "$update_import" | jq -r '.data.id')"
  update_candidates="$(get_document_json "/imports/$update_import_id/candidates?keyword=$(urlencode "登录")")"
  update_candidate_id="$(printf '%s' "$update_candidates" | jq -r '.data.items[0].id')"
  update_candidate_version="$(printf '%s' "$update_candidates" | jq -r '.data.items[0].version')"
  post_document_json "/candidates/$update_candidate_id/confirm" "$(jq -nc --argjson version "$update_candidate_version" '{version:$version}')" >/dev/null
  update_dry_run="$(post_document_json "/imports/$update_import_id/publish" "$(jq -nc --arg id "$update_candidate_id" '{dryRun:true,candidateIds:[$id]}')")"
  check_arg "Publish dryRun detects WP3 update" id "$requirement_id" '.data.dryRun == true and .data.plannedCreateCount == 0 and .data.plannedUpdateCount == 1 and .data.records[0].action == "UPDATE" and .data.records[0].assetRequirementId == $id and (.data.records[0].diffSummary | contains("acceptanceCriteria"))' "$update_dry_run"
  update_publish="$(post_document_json "/imports/$update_import_id/publish" '{}')"
  check_arg "Publish updates existing WP3 requirement" id "$requirement_id" '.data.dryRun == false and .data.totalCreated == 1 and .data.createdRequirementIds[0] == $id and .data.publishedCount == 1' "$update_publish"
  updated_asset="$(get_asset_json "/requirements/$requirement_id")"
  check "WP3 requirement keeps sourceRef and receives update" '.data.sourceRef == "wp4-smoke-md#1" and .data.priority == "CRITICAL" and (.data.acceptanceCriteria | contains("二次确认成功")) and (.data.tags | contains("update"))' "$updated_asset"

  local source source_id source_health
  source="$(post_document_json /sources "$(jq -nc \
    --arg sourceCode "$SOURCE_CODE" \
    --arg projectId "$PROJECT_ID" \
    '{sourceCode:$sourceCode,name:"WP4 smoke webhook source",sourceType:"CUSTOM_API",defaultProjectId:$projectId,endpointUrl:"https://example.test/wp4-smoke",secretRef:"secret://wp4/smoke",eventVersion:"1.0",mappingVersion:"default"}')")"
  check_arg "Create CUSTOM_API source" sourceCode "$SOURCE_CODE" '.data.sourceCode == $sourceCode and .data.sourceType == "CUSTOM_API" and .data.secretRef == "secret://wp4/smoke" and .data.eventVersion == "1.0" and .data.mappingVersion == "default"' "$source"
  source_id="$(printf '%s' "$source" | jq -r '.data.id')"

  source_health="$(get_document_json "/sources/$source_id/health")"
  check_arg "Source health" sourceCode "$SOURCE_CODE" '.data.sourceCode == $sourceCode and .data.ready == true and .data.signatureAlgorithm != null and .data.secretRefConfigured == true and .data.eventVersion == "1.0"' "$source_health"

  local webhook_payload event_id idem webhook duplicate events invalid_payload
  event_id="evt-wp4-smoke-$RANDOM"
  idem="idem-wp4-smoke-$RANDOM"
  webhook_payload="$(jq -nc \
    --arg projectId "$PROJECT_ID" \
    '{projectId:$projectId,eventType:"requirement.created",eventVersion:"1.0",id:"REQ-WP4-SMOKE",title:"WP4 smoke webhook import",requirements:[{title:"Webhook 需求",description:"来自自研需求平台",priority:"LOW",tags:["webhook","smoke"]}]}')"
  webhook="$(post_signed_webhook "$SOURCE_CODE" "$webhook_payload" "$event_id" "$idem")"
  check_arg "Signed webhook creates import" sourceCode "$SOURCE_CODE" '.data.sourceCode == $sourceCode and .data.totalParsed == 1 and .data.pendingCount == 1' "$webhook"
  duplicate="$(post_signed_webhook "$SOURCE_CODE" "$webhook_payload" "$event_id" "$idem")"
  check_arg "Webhook idempotent replay" importId "$(printf '%s' "$webhook" | jq -r '.data.id')" '.data.id == $importId' "$duplicate"

  events="$(get_document_json "/webhook-events?sourceCode=$(urlencode "$SOURCE_CODE")&status=PROCESSED")"
  check_arg "Webhook event log" eventId "$event_id" '.data.total >= 1 and (.data.items | any(.eventId == $eventId and .signatureStatus == "VALID"))' "$events"

  invalid_payload="$(jq -nc --arg projectId "$PROJECT_ID" '{projectId:$projectId,eventType:"requirement.created",id:"REQ-WP4-BAD",requirements:[{title:"Bad signature"}]}')"
  expect_http_error "Webhook invalid signature rejected" "403" "FORBIDDEN" \
    -X POST "$API_BASE/webhooks/$(urlencode "$SOURCE_CODE")" \
    -H 'Content-Type: application/json' \
    -H "X-VA-Timestamp: $(date +%s)" \
    -H "X-VA-Signature: bad-signature" \
    -H "X-VA-Event-Id: evt-wp4-bad-$RANDOM" \
    -H "X-VA-Idempotency-Key: idem-wp4-bad-$RANDOM" \
    -H "X-VA-Event-Version: 1.0" \
    -d "$invalid_payload"

  local metrics
  metrics="$(curl -fsS "${BASE_URL%/}/actuator/metrics/veri.agent.document_input.imports")"
  check "WP4 import metric exists" '.name == "veri.agent.document_input.imports" and (.measurements | length) >= 1' "$metrics"
  if [[ "$model_parse_enabled" == "true" ]]; then
    metrics="$(curl -fsS "${BASE_URL%/}/actuator/metrics/veri.agent.document_input.model_parse")"
    check "WP4 model parse metric exists" '.name == "veri.agent.document_input.model_parse" and (.measurements | length) >= 1' "$metrics"
  fi

  echo "== summary =="
  echo "pass=$PASS fail=$FAIL total=$((PASS + FAIL))"
  if [[ "$FAIL" -ne 0 ]]; then
    exit 1
  fi
  echo "WP4 document-input smoke passed for project_id=$PROJECT_ID."
}

main "$@"
