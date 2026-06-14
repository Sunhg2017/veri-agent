#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODE="${WP9_WEBHOOK_SMOKE_MODE:-external}"
OUT_DIR="${WP9_WEBHOOK_SMOKE_OUT_DIR:-$ROOT_DIR/build/wp9-webhook-http-smoke}"
RUN_ID="$(date +%Y%m%d%H%M%S)-$$"

BASE_URL="${WP9_WEBHOOK_SMOKE_BASE_URL:-http://127.0.0.1:8080}"
API_BASE="${BASE_URL%/}/api/v1"
PROJECT_CODE="${WP9_WEBHOOK_SMOKE_PROJECT_CODE:-wp9wh$(date +%H%M%S)$RANDOM}"
PROJECT_ID="${WP9_WEBHOOK_SMOKE_PROJECT_ID:-}"
ADMIN_USERNAME="${WP9_WEBHOOK_SMOKE_ADMIN_USERNAME:-admin}"
ADMIN_PASSWORD="${WP9_WEBHOOK_SMOKE_ADMIN_PASSWORD:-AdminPass12345}"
ADMIN_CHANGED_PASSWORD="${WP9_WEBHOOK_SMOKE_ADMIN_CHANGED_PASSWORD:-AdminPass12345!Changed}"
WEBHOOK_SECRET="${WP9_WEBHOOK_SECRET:-wp9-webhook-http-smoke-secret}"
WEBHOOK_SECRET_REF="${WP9_WEBHOOK_SECRET_REF:-secret://wp9/webhook-smoke-$RUN_ID}"

IMAGE="${WP9_WEBHOOK_SMOKE_DB_IMAGE:-postgres:16-alpine}"
CONTAINER="${WP9_WEBHOOK_SMOKE_DB_CONTAINER:-veri-agent-wp9-webhook-smoke-pg-$$}"
DB_NAME="${WP9_WEBHOOK_SMOKE_DB_NAME:-veri_agent_wp9_webhook_smoke}"
DB_USER="${WP9_WEBHOOK_SMOKE_DB_USER:-wp9user}"
DB_PASSWORD="${WP9_WEBHOOK_SMOKE_DB_PASSWORD:-wp9pass}"
DB_HOST="${WP9_WEBHOOK_SMOKE_DB_HOST:-localhost}"
KEEP_RUNTIME="${WP9_KEEP_WEBHOOK_SMOKE_RUNTIME:-0}"

WP1_SERVICE_TOKEN_VALUE="${WP1_SERVICE_TOKEN:-local-platform-service-token}"
WP2_SERVICE_TOKEN_VALUE="${WP2_SERVICE_TOKEN:-local-model-access-token}"
WP3_SERVICE_TOKEN_VALUE="${WP3_SERVICE_TOKEN:-local-asset-token}"
WP4_SERVICE_TOKEN_VALUE="${WP4_SERVICE_TOKEN:-local-document-input-token}"
WP5_SERVICE_TOKEN_VALUE="${WP5_SERVICE_TOKEN:-local-test-design-token}"
AUTH_TOKEN_SECRET="${WP1_AUTH_TOKEN_SECRET:-local-auth-secret-32-byte-minimum!}"
LOCAL_SECRET_MASTER_KEY="${WP1_LOCAL_SECRET_MASTER_KEY:-0123456789abcdef0123456789abcdef}"

APP_PID=""
APP_LOG="$OUT_DIR/platform-api-$RUN_ID.log"
DB_LOG="$OUT_DIR/postgres-$RUN_ID.log"
SUPER_ADMIN_SEED_SQL="$ROOT_DIR/db/seed/wp1_super_admin.sql"
PASS=0
FAIL=0

require_tool() {
  local tool="$1"
  if ! command -v "$tool" >/dev/null 2>&1; then
    echo "$tool is required for WP9 webhook HTTP smoke" >&2
    exit 127
  fi
}

check() {
  local name="$1"
  local jq_expr="$2"
  local payload="$3"
  if printf '%s' "$payload" | jq -e "$jq_expr" >/dev/null; then
    echo "   PASS $name" >&2
    PASS=$((PASS + 1))
  else
    echo "   FAIL $name" >&2
    echo "$payload" >&2
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
    echo "   PASS $name" >&2
    PASS=$((PASS + 1))
  else
    echo "   FAIL $name" >&2
    echo "$payload" >&2
    FAIL=$((FAIL + 1))
  fi
}

check_absent() {
  local name="$1"
  local needle="$2"
  local payload="$3"
  if ! printf '%s' "$payload" | grep -F "$needle" >/dev/null; then
    echo "   PASS $name" >&2
    PASS=$((PASS + 1))
  else
    echo "   FAIL $name" >&2
    echo "$payload" >&2
    FAIL=$((FAIL + 1))
  fi
}

urlencode() {
  jq -nr --arg value "$1" '$value | @uri'
}

require_check() {
  local name="$1"
  local jq_expr="$2"
  local payload="$3"
  if printf '%s' "$payload" | jq -e "$jq_expr" >/dev/null; then
    echo "   PASS $name" >&2
  else
    echo "   FAIL $name" >&2
    echo "$payload" >&2
    exit 1
  fi
}

require_check_arg() {
  local name="$1"
  local arg_name="$2"
  local arg_value="$3"
  local jq_expr="$4"
  local payload="$5"
  if printf '%s' "$payload" | jq -e --arg "$arg_name" "$arg_value" "$jq_expr" >/dev/null; then
    echo "   PASS $name" >&2
  else
    echo "   FAIL $name" >&2
    echo "$payload" >&2
    exit 1
  fi
}

require_absent() {
  local name="$1"
  local needle="$2"
  local payload="$3"
  if ! printf '%s' "$payload" | grep -F "$needle" >/dev/null; then
    echo "   PASS $name" >&2
  else
    echo "   FAIL $name" >&2
    echo "$payload" >&2
    exit 1
  fi
}

post_json() {
  local path="$1"
  local body="$2"
  shift 2
  curl -fsS -X POST "$API_BASE$path" \
    -H 'Content-Type: application/json' \
    "$@" \
    -d "$body"
}

patch_json() {
  local path="$1"
  local body="$2"
  shift 2
  curl -fsS -X PATCH "$API_BASE$path" \
    -H 'Content-Type: application/json' \
    "$@" \
    -d "$body"
}

get_json() {
  local path="$1"
  shift
  curl -fsS "$API_BASE$path" "$@"
}

login_admin() {
  local password="$1"
  post_json /auth/login "$(jq -nc \
    --arg username "$ADMIN_USERNAME" \
    --arg password "$password" \
    '{username:$username,password:$password}')"
}

auth_token() {
  local login token password_change
  login="$(login_admin "$ADMIN_PASSWORD")"
  token="$(printf '%s' "$login" | jq -r '.data.accessToken // empty')"

  if [[ -z "$token" && "$ADMIN_CHANGED_PASSWORD" != "$ADMIN_PASSWORD" ]]; then
    login="$(login_admin "$ADMIN_CHANGED_PASSWORD")"
    token="$(printf '%s' "$login" | jq -r '.data.accessToken // empty')"
    if [[ -n "$token" ]]; then
      ADMIN_PASSWORD="$ADMIN_CHANGED_PASSWORD"
    fi
  fi

  check "Login SuperAdmin" '.data.accessToken | type == "string"' "$login"
  if [[ -z "$token" ]]; then
    echo "Unable to obtain SuperAdmin token. Seed SuperAdmin before running external WP9 webhook smoke." >&2
    exit 1
  fi

  if printf '%s' "$login" | jq -e '(.data.mustChangePassword == true) or (.data.must_change_password == true)' >/dev/null; then
    password_change="$(post_json /auth/change-password "$(jq -nc \
      --arg oldPassword "$ADMIN_PASSWORD" \
      --arg newPassword "$ADMIN_CHANGED_PASSWORD" \
      '{oldPassword:$oldPassword,newPassword:$newPassword}')" \
      -H "Authorization: Bearer $token")"
    check "Force initial password change" '.code == "OK" and .data.passwordChanged == true' "$password_change"

    ADMIN_PASSWORD="$ADMIN_CHANGED_PASSWORD"
    login="$(login_admin "$ADMIN_PASSWORD")"
    token="$(printf '%s' "$login" | jq -r '.data.accessToken // empty')"
    check "Login after password change" '(.data.accessToken | type == "string") and ((.data.mustChangePassword == false) or (.data.must_change_password == false))' "$login"
  fi

  printf '%s' "$token"
}

expect_http_error() {
  local name="$1"
  local expected_status="$2"
  local expected_message="$3"
  shift 3
  local body_file status body
  body_file="$(mktemp -t wp9-webhook-error.XXXXXX.json)"
  status="$(curl -sS -o "$body_file" -w '%{http_code}' "$@")"
  body="$(cat "$body_file")"
  rm -f "$body_file"
  if [[ "$status" == "$expected_status" ]] \
    && printf '%s' "$body" | jq -e --arg message "$expected_message" '.message == $message' >/dev/null; then
    echo "   PASS $name" >&2
    PASS=$((PASS + 1))
  else
    echo "   FAIL $name" >&2
    echo "status=$status expected=$expected_status" >&2
    echo "$body" >&2
    FAIL=$((FAIL + 1))
  fi
}

webhook_signature() {
  local timestamp="$1"
  local event_id="$2"
  local payload="$3"
  printf '%s' "$timestamp.$event_id.$payload" | openssl dgst -sha256 -hmac "$WEBHOOK_SECRET" -hex | awk '{print $NF}'
}

post_signed_webhook() {
  local trigger_id="$1"
  local payload="$2"
  local event_id="$3"
  local timestamp signature
  timestamp="$(date +%s)"
  signature="$(webhook_signature "$timestamp" "$event_id" "$payload")"
  curl -fsS -X POST "$API_BASE/execution/webhooks/$(urlencode "$trigger_id")" \
    -H 'Content-Type: application/json' \
    -H "X-VA-Timestamp: $timestamp" \
    -H "X-VA-Signature: $signature" \
    -H "X-VA-Event-Id: $event_id" \
    -d "$payload"
}

openapi_content() {
  jq -nc '{
    openapi: "3.0.3",
    info: {title: "WP9 webhook smoke API", version: "1.0.0"},
    paths: {
      "/v1/wp9-smoke": {
        get: {
          operationId: "getWp9Smoke",
          summary: "WP9 webhook smoke endpoint",
          responses: {"200": {description: "ok"}}
        }
      }
    }
  }'
}

create_secret() {
  local token="$1"
  post_json /management/secrets "$(jq -nc \
    --arg secretRef "$WEBHOOK_SECRET_REF" \
    --arg scopeId "$PROJECT_ID" \
    --arg value "$WEBHOOK_SECRET" \
    '{
      secretRef: $secretRef,
      providerCode: "local",
      purpose: "WEBHOOK_SIGNING",
      scopeType: "PROJECT",
      scopeId: $scopeId,
      secretValue: $value,
      secretVersion: "v1"
    }')" \
    -H "Authorization: Bearer $token"
}

create_project() {
  local token="$1"
  local created activated context
  if [[ -n "$PROJECT_ID" ]]; then
    printf '%s' "$PROJECT_ID"
    return
  fi
  created="$(post_json /management/projects "$(jq -nc \
    --arg code "$PROJECT_CODE" \
    '{
      code: $code,
      name: "WP9 webhook HTTP smoke",
      sensitivityLevel: "INTERNAL",
      allowPublicModel: false
    }')" \
    -H "Authorization: Bearer $token")"
  require_check "Create WP1 project context" '.data.name == "WP9 webhook HTTP smoke" and .data.status == "规划中"' "$created"

  activated="$(patch_json "/management/projects/$(urlencode "$PROJECT_CODE")/status" '{"status":"ACTIVE"}' \
    -H "Authorization: Bearer $token")"
  require_check "Activate WP1 project context" '.data.status == "进行中"' "$activated"

  context="$(get_json "/contexts/projects/$(urlencode "$PROJECT_CODE")" \
    -H "Authorization: Bearer $WP1_SERVICE_TOKEN_VALUE" \
    -H "X-Caller-Service: wp5-test-design" \
    -H "X-Delegated-User-Id: $ADMIN_USERNAME")"
  require_check "Resolve WP1 project context" '.data.resourceType == "PROJECT" and (.data.resourceId | type == "string") and .data.sensitivityLevel == "INTERNAL"' "$context"
  printf '%s' "$context" | jq -r '.data.resourceId'
}

create_approved_bundle() {
  local token="$1"
  local content spec created spec_id synced generation task_id bundle_id submitted approved
  content="$(openapi_content)"
  created="$(post_json /api-automation/specs "$(jq -nc \
    --arg projectId "$PROJECT_CODE" \
    --arg name "wp9-webhook-smoke-openapi" \
    --arg content "$content" \
    '{
      projectId: $projectId,
      sourceType: "TEXT",
      name: $name,
      versionLabel: "wp9-webhook-smoke",
      content: $content
    }')" \
    -H "Authorization: Bearer $token")"
  require_check "Create WP6 OpenAPI spec" '.data.spec.status == "PARSED" and .data.spec.endpointCount >= 1' "$created"
  spec_id="$(printf '%s' "$created" | jq -r '.data.spec.id')"

  synced="$(post_json "/api-automation/specs/$spec_id/sync" '{}' \
    -H "Authorization: Bearer $token")"
  require_check "Sync WP6 OpenAPI endpoints" '.data.counts.CREATED >= 1 or .data.counts.MATCHED >= 1' "$synced"

  generation="$(post_json /api-automation/generation-tasks "$(jq -nc \
    --arg projectId "$PROJECT_ID" \
    --arg specId "$spec_id" \
    --arg requestKey "wp9-webhook-smoke-$RUN_ID" \
    '{
      projectId: $projectId,
      specId: $specId,
      coverageTypes: ["SMOKE"],
      generationMode: "FALLBACK_ONLY",
      caseCountPerApi: 1,
      requestKey: $requestKey
    }')" \
    -H "Authorization: Bearer $token")"
  require_check "Generate WP6 script bundle" '.data.task.status == "COMPLETED" and .data.scriptBundles[0].staticCheckStatus == "PASSED"' "$generation"
  task_id="$(printf '%s' "$generation" | jq -r '.data.task.id')"
  bundle_id="$(printf '%s' "$generation" | jq -r '.data.scriptBundles[0].id')"

  submitted="$(post_json "/api-automation/script-bundles/$bundle_id/submit-review" '{"note":"wp9 webhook smoke ready"}' \
    -H "Authorization: Bearer $token")"
  require_check_arg "Submit WP6 bundle review" bundleId "$bundle_id" '.data.id == $bundleId and .data.status == "REVIEWING"' "$submitted"

  approved="$(post_json "/api-automation/script-bundles/$bundle_id/approve" '{"note":"wp9 webhook smoke approved"}' \
    -H "Authorization: Bearer $token")"
  require_check_arg "Approve WP6 script bundle" bundleId "$bundle_id" '.data.id == $bundleId and .data.status == "APPROVED"' "$approved"
  require_check_arg "WP6 bundle project scope" projectId "$PROJECT_ID" '.data.projectId == $projectId' "$approved"
  require_check_arg "WP6 generation task retained" taskId "$task_id" '.data.taskId == $taskId' "$approved"
  printf '%s' "$bundle_id"
}

create_execution_plan() {
  local token="$1"
  local bundle_id="$2"
  local created
  created="$(post_json /execution/plans "$(jq -nc \
    --arg projectId "$PROJECT_CODE" \
    --arg bundleId "$bundle_id" \
    '{
      projectId: $projectId,
      name: "WP9 webhook HTTP smoke",
      environmentKey: "staging",
      status: "READY",
      triggerPolicy: {manualEnabled: true, webhookEnabled: true, cronEnabled: false},
      dag: {
        nodes: [{
          key: "api-smoke",
          type: "API_TEST",
          dependencies: [],
          input: {apiAutomationBundleId: $bundleId},
          timeoutSeconds: 180,
          failurePolicy: "FAIL_FAST",
          retryPolicy: {maxAttempts: 1}
        }]
      }
    }')" \
    -H "Authorization: Bearer $token")"
  require_check "Create READY WP9 plan" '.data.status == "READY" and ((.data.nodeCount // (.data.nodes | length)) == 1)' "$created"
  printf '%s' "$created" | jq -r '.data.id'
}

create_execution_trigger() {
  local token="$1"
  local plan_id="$2"
  local created
  created="$(post_json "/execution/plans/$plan_id/triggers" "$(jq -nc \
    --arg secretRef "$WEBHOOK_SECRET_REF" \
    '{
      triggerType: "WEBHOOK",
      status: "ENABLED",
      secretRef: $secretRef,
      config: {
        source: "github-actions",
        eventType: "deployment",
        description: "WP9 external webhook HTTP smoke"
      }
    }')" \
    -H "Authorization: Bearer $token")"
  require_check "Create enabled WP9 webhook trigger" '.data.triggerType == "WEBHOOK" and .data.status == "ENABLED" and .data.secretRefConfigured == true' "$created"
  require_absent "Trigger response omits secretRef" "$WEBHOOK_SECRET_REF" "$created"
  printf '%s' "$created" | jq -r '.data.id'
}

run_external_smoke() {
  require_tool curl
  require_tool jq
  require_tool openssl
  require_tool awk

  API_BASE="${BASE_URL%/}/api/v1"
  echo "== WP9 webhook HTTP smoke =="
  echo "mode=external baseUrl=${BASE_URL%/} projectCode=$PROJECT_CODE"

  local health token secret bundle_id plan_id trigger_id invalid_payload invalid_event valid_payload valid_event accepted run_id duplicate events run export_json
  health="$(get_json /execution/health)"
  check "WP9 health reachable" '.data.service == "execution"' "$health"
  check "WP9 webhook globally enabled" '.data.webhookEnabled == true' "$health"

  token="$(auth_token)"
  PROJECT_ID="$(create_project "$token")"
  echo "projectId=$PROJECT_ID projectCode=$PROJECT_CODE" >&2
  secret="$(create_secret "$token")"
  check "Create webhook signing secret" '(.data.secretRef | type == "string") and .data.maskedValue == "********" and .data.status == "ACTIVE"' "$secret"
  check_absent "Secret response omits plaintext" "$WEBHOOK_SECRET" "$secret"

  bundle_id="$(create_approved_bundle "$token")"
  plan_id="$(create_execution_plan "$token" "$bundle_id")"
  trigger_id="$(create_execution_trigger "$token" "$plan_id")"

  invalid_payload="$(jq -nc --arg run "$RUN_ID" '{build:"bad-signature", run:$run, token:"wp9-payload-token"}')"
  invalid_event="wp9-webhook-invalid-$RUN_ID"
  expect_http_error "Reject invalid webhook signature" 403 "EXECUTION_TRIGGER_SIGNATURE_INVALID" \
    -X POST "$API_BASE/execution/webhooks/$(urlencode "$trigger_id")" \
    -H 'Content-Type: application/json' \
    -H "X-VA-Timestamp: $(date +%s)" \
    -H "X-VA-Event-Id: $invalid_event" \
    -H "X-VA-Signature: bad-signature" \
    -d "$invalid_payload"

  valid_payload="$(jq -nc --arg run "$RUN_ID" '{build:"2026.06.wp9", run:$run, token:"wp9-payload-token"}')"
  valid_event="wp9-webhook-valid-$RUN_ID"
  accepted="$(post_signed_webhook "$trigger_id" "$valid_payload" "$valid_event")"
  check_arg "Accept signed webhook" eventId "$valid_event" '.data.event.status == "ACCEPTED" and .data.event.sourceEventId == $eventId and .data.idempotentReplay == false and (.data.runId | type == "string")' "$accepted"
  check_absent "Accepted response omits payload token" "wp9-payload-token" "$accepted"
  run_id="$(printf '%s' "$accepted" | jq -r '.data.runId')"

  duplicate="$(post_signed_webhook "$trigger_id" "$valid_payload" "$valid_event")"
  check_arg "Webhook idempotent replay" runId "$run_id" '.data.runId == $runId and .data.idempotentReplay == true and .data.event.status == "DUPLICATE"' "$duplicate"

  events="$(get_json "/execution/triggers/$trigger_id/events" -H "Authorization: Bearer $token")"
  check_arg "Trigger events retain accepted event" eventId "$valid_event" '.data.items | any(.sourceEventId == $eventId and (.status == "ACCEPTED" or .status == "DUPLICATE") and (.requestDigest | type == "string"))' "$events"
  check_arg "Trigger events retain rejected signature" eventId "$invalid_event" '.data.items | any(.sourceEventId == $eventId and .status == "REJECTED" and .errorCode == "EXECUTION_TRIGGER_SIGNATURE_INVALID")' "$events"
  check_absent "Trigger events omit raw payload token" "wp9-payload-token" "$events"

  run="$(get_json "/execution/runs/$run_id" -H "Authorization: Bearer $token")"
  check_arg "Webhook run detail" eventId "$valid_event" '.data.triggerType == "WEBHOOK" and .data.sourceEventId == $eventId and .data.resultSummary.webhookPayloadStored == false and (.data.nodes | length) == 1' "$run"
  check_absent "Run detail omits raw payload token" "wp9-payload-token" "$run"
  check_absent "Run detail omits webhook secretRef" "$WEBHOOK_SECRET_REF" "$run"

  export_json="$(get_json "/execution/runs/$run_id/export" -H "Authorization: Bearer $token")"
  check "Webhook run export remains redacted" '.data.schemaVersion == "wp9-run-export-v1" and .data.redactionPolicy.triggerPayloadExported == false and .data.redactionPolicy.secretRefsExported == false' "$export_json"
  check_absent "Run export omits raw payload token" "wp9-payload-token" "$export_json"

  if [[ "$FAIL" -gt 0 ]]; then
    echo "WP9 webhook HTTP smoke failed: pass=$PASS fail=$FAIL" >&2
    exit 1
  fi
  echo "WP9 webhook HTTP smoke passed: pass=$PASS fail=$FAIL"
}

cleanup() {
  local status=$?
  if [[ -n "$APP_PID" ]] && kill -0 "$APP_PID" >/dev/null 2>&1; then
    kill "$APP_PID" >/dev/null 2>&1 || true
    wait "$APP_PID" >/dev/null 2>&1 || true
  fi
  if [[ "$KEEP_RUNTIME" != "1" ]]; then
    docker rm -f "$CONTAINER" >/dev/null 2>&1 || true
  else
    echo "WP9 webhook smoke runtime kept: container=$CONTAINER appPid=${APP_PID:-}"
  fi
  exit "$status"
}

choose_api_port() {
  if [[ -n "${WP9_WEBHOOK_SMOKE_API_PORT:-}" ]]; then
    echo "$WP9_WEBHOOK_SMOKE_API_PORT"
    return
  fi
  for _ in $(seq 1 20); do
    local candidate=$((19080 + RANDOM % 1000))
    if command -v lsof >/dev/null 2>&1; then
      if ! lsof -iTCP:"$candidate" -sTCP:LISTEN -Pn >/dev/null 2>&1; then
        echo "$candidate"
        return
      fi
    else
      echo "$candidate"
      return
    fi
  done
  echo 19085
}

start_postgres() {
  docker rm -f "$CONTAINER" >/dev/null 2>&1 || true
  docker run -d \
    --name "$CONTAINER" \
    -e "POSTGRES_DB=$DB_NAME" \
    -e "POSTGRES_USER=$DB_USER" \
    -e "POSTGRES_PASSWORD=$DB_PASSWORD" \
    -p "127.0.0.1::5432" \
    "$IMAGE" >/dev/null

  local timeout="${WP9_WEBHOOK_SMOKE_DB_READY_TIMEOUT_SECONDS:-60}"
  for _ in $(seq 1 "$timeout"); do
    if docker exec "$CONTAINER" pg_isready -U "$DB_USER" -d "$DB_NAME" >/dev/null 2>&1; then
      docker logs "$CONTAINER" > "$DB_LOG" 2>&1 || true
      return
    fi
    sleep 1
  done

  echo "WP9 webhook smoke PostgreSQL did not become ready in ${timeout}s" >&2
  docker logs "$CONTAINER" >&2 || true
  exit 1
}

host_postgres_port() {
  docker port "$CONTAINER" 5432/tcp | awk -F: 'NR == 1 {print $NF}'
}

seed_super_admin() {
  echo "== seeding WP1 SuperAdmin for WP9 webhook smoke ==" | tee -a "$DB_LOG"
  docker exec -i "$CONTAINER" psql -v ON_ERROR_STOP=1 -U "$DB_USER" -d "$DB_NAME" \
    -v WP1_SUPER_ADMIN_USERNAME="$ADMIN_USERNAME" \
    -v WP1_SUPER_ADMIN_PASSWORD="$ADMIN_PASSWORD" \
    -v 'WP1_SUPER_ADMIN_DISPLAY_NAME=WP9 Webhook Smoke Admin' \
    -v 'WP1_SUPER_ADMIN_EMAIL=wp9-webhook-smoke-admin@example.com' \
    < "$SUPER_ADMIN_SEED_SQL" >> "$DB_LOG" 2>&1
}

start_platform_api() {
  local api_port="$1"
  local db_port="$2"
  : > "$APP_LOG"
  (
    cd "$ROOT_DIR"
    WP1_AUTH_TOKEN_SECRET="$AUTH_TOKEN_SECRET" \
    WP1_SERVICE_TOKEN="$WP1_SERVICE_TOKEN_VALUE" \
    WP1_DATASOURCE_URL="jdbc:postgresql://$DB_HOST:$db_port/$DB_NAME" \
    WP1_DATASOURCE_USERNAME="$DB_USER" \
    WP1_DATASOURCE_PASSWORD="$DB_PASSWORD" \
    WP1_FLYWAY_LOCATIONS="filesystem:$ROOT_DIR/db/migration/wp1" \
    WP1_LOCAL_SECRET_MASTER_KEY="$LOCAL_SECRET_MASTER_KEY" \
    WP2_SERVICE_TOKEN="$WP2_SERVICE_TOKEN_VALUE" \
    WP3_SERVICE_TOKEN="$WP3_SERVICE_TOKEN_VALUE" \
    WP4_SERVICE_TOKEN="$WP4_SERVICE_TOKEN_VALUE" \
    WP5_SERVICE_TOKEN="$WP5_SERVICE_TOKEN_VALUE" \
    WP9_WEBHOOK_ENABLED=true \
    WP9_CRON_ENABLED=false \
      mvn -B -pl platform-api spring-boot:run \
        -Dspring-boot.run.profiles=db \
        -Dspring-boot.run.arguments="--server.port=$api_port"
  ) > "$APP_LOG" 2>&1 &
  APP_PID="$!"
}

wait_for_platform_api() {
  local base_url="$1"
  local timeout="${WP9_WEBHOOK_SMOKE_API_READY_TIMEOUT_SECONDS:-120}"
  for _ in $(seq 1 "$timeout"); do
    if curl -fsS "$base_url/api/v1/execution/health" >/dev/null 2>&1; then
      return
    fi
    if [[ -n "$APP_PID" ]] && ! kill -0 "$APP_PID" >/dev/null 2>&1; then
      echo "platform-api exited before WP9 webhook smoke could start. Log: $APP_LOG" >&2
      tail -n 160 "$APP_LOG" >&2 || true
      exit 1
    fi
    sleep 1
  done

  echo "platform-api did not become ready in ${timeout}s. Log: $APP_LOG" >&2
  tail -n 160 "$APP_LOG" >&2 || true
  exit 1
}

run_managed_smoke() {
  require_tool docker
  require_tool curl
  require_tool jq
  require_tool mvn
  require_tool openssl
  require_tool awk
  mkdir -p "$OUT_DIR"
  trap cleanup EXIT INT TERM

  local api_port db_port
  api_port="$(choose_api_port)"
  BASE_URL="http://127.0.0.1:$api_port"
  API_BASE="${BASE_URL%/}/api/v1"

  echo "== WP9 managed webhook HTTP smoke runtime =="
  echo "platformApi=$BASE_URL logs=$OUT_DIR project=$PROJECT_ID"
  start_postgres
  db_port="$(host_postgres_port)"
  start_platform_api "$api_port" "$db_port"
  wait_for_platform_api "$BASE_URL"
  seed_super_admin
  run_external_smoke
}

case "$MODE" in
  external)
    run_external_smoke
    ;;
  managed|auto)
    run_managed_smoke
    ;;
  *)
    echo "Unsupported WP9_WEBHOOK_SMOKE_MODE=$MODE; use external, managed, or auto." >&2
    exit 2
    ;;
esac
