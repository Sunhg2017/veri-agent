#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT_DIR="${WP5_SMOKE_OUT_DIR:-$ROOT_DIR/build/wp5-http-smoke}"
RUN_ID="$(date +%Y%m%d%H%M%S)-$$"

IMAGE="${WP5_SMOKE_DB_IMAGE:-postgres:16-alpine}"
CONTAINER="${WP5_SMOKE_DB_CONTAINER:-veri-agent-wp5-smoke-pg-$$}"
DB_NAME="${WP5_SMOKE_DB_NAME:-veri_agent_wp5_smoke}"
DB_USER="${WP5_SMOKE_DB_USER:-wp5user}"
DB_PASSWORD="${WP5_SMOKE_DB_PASSWORD:-wp5pass}"
DB_HOST="${WP5_SMOKE_DB_HOST:-localhost}"
KEEP_RUNTIME="${WP5_KEEP_SMOKE_RUNTIME:-0}"

WP1_SERVICE_TOKEN_VALUE="${WP1_SERVICE_TOKEN:-local-platform-service-token}"
WP2_SERVICE_TOKEN_VALUE="${WP2_SERVICE_TOKEN:-local-model-access-token}"
WP3_SERVICE_TOKEN_VALUE="${WP3_SERVICE_TOKEN:-local-asset-token}"
WP4_SERVICE_TOKEN_VALUE="${WP4_SERVICE_TOKEN:-local-document-input-token}"
WP5_SERVICE_TOKEN_VALUE="${WP5_SERVICE_TOKEN:-local-test-design-token}"
CALLER_SERVICE="${WP5_SMOKE_CALLER_SERVICE:-wp5-test-design}"
AUTH_TOKEN_SECRET="${WP1_AUTH_TOKEN_SECRET:-local-auth-secret-32-byte-minimum!}"
LOCAL_SECRET_MASTER_KEY="${WP1_LOCAL_SECRET_MASTER_KEY:-0123456789abcdef0123456789abcdef}"
TEST_DESIGN_TRUSTED_SERVICES="${WP5_TRUSTED_CALLER_SERVICES:-test-design,$CALLER_SERVICE}"
ASSET_TRUSTED_SERVICES="${WP3_TRUSTED_CALLER_SERVICES:-asset-service,$CALLER_SERVICE,wp4-document-input}"
ADMIN_USERNAME="${WP5_SMOKE_ADMIN_USERNAME:-admin}"
ADMIN_PASSWORD="${WP5_SMOKE_ADMIN_PASSWORD:-AdminPass12345}"

APP_PID=""
APP_LOG="$OUT_DIR/platform-api-$RUN_ID.log"
DB_LOG="$OUT_DIR/postgres-$RUN_ID.log"
SUPER_ADMIN_SEED_SQL="$ROOT_DIR/db/seed/wp1_super_admin.sql"

is_truthy() {
  case "${1:-}" in
    1|true|TRUE|yes|YES|on|ON)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

is_release_gate() {
  if is_truthy "${WP5_RELEASE_GATE:-0}"; then
    return 0
  fi
  case "${WP5_GATE_MODE:-development}" in
    release|RELEASE|preprod|PREPROD|prod|PROD|production|PRODUCTION)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

release_readiness_publish_blocking_enabled() {
  if [[ -n "${WP5_RELEASE_READINESS_PUBLISH_BLOCKING_ENABLED:-}" ]]; then
    if is_truthy "$WP5_RELEASE_READINESS_PUBLISH_BLOCKING_ENABLED"; then
      echo true
    else
      echo false
    fi
    return
  fi
  if is_release_gate; then
    echo true
  else
    echo false
  fi
}

require_tool() {
  local tool="$1"
  if ! command -v "$tool" >/dev/null 2>&1; then
    echo "$tool is required for WP5 managed HTTP smoke" >&2
    exit 127
  fi
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
    echo "WP5 managed smoke runtime kept: container=$CONTAINER appPid=${APP_PID:-}"
  fi
  exit "$status"
}

choose_api_port() {
  if [[ -n "${WP5_SMOKE_API_PORT:-}" ]]; then
    echo "$WP5_SMOKE_API_PORT"
    return
  fi
  for _ in $(seq 1 20); do
    local candidate=$((18080 + RANDOM % 1000))
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
  echo 18085
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

  local timeout="${WP5_SMOKE_DB_READY_TIMEOUT_SECONDS:-60}"
  for _ in $(seq 1 "$timeout"); do
    if docker exec "$CONTAINER" pg_isready -U "$DB_USER" -d "$DB_NAME" >/dev/null 2>&1; then
      docker logs "$CONTAINER" > "$DB_LOG" 2>&1 || true
      return
    fi
    sleep 1
  done

  echo "WP5 smoke PostgreSQL did not become ready in ${timeout}s" >&2
  docker logs "$CONTAINER" >&2 || true
  exit 1
}

host_postgres_port() {
  docker port "$CONTAINER" 5432/tcp | awk -F: 'NR == 1 {print $NF}'
}

seed_super_admin() {
  echo "== seeding WP1 SuperAdmin for WP5 managed smoke ==" | tee -a "$DB_LOG"
  docker exec -i "$CONTAINER" psql -v ON_ERROR_STOP=1 -U "$DB_USER" -d "$DB_NAME" \
    -v WP1_SUPER_ADMIN_USERNAME="$ADMIN_USERNAME" \
    -v WP1_SUPER_ADMIN_PASSWORD="$ADMIN_PASSWORD" \
    -v 'WP1_SUPER_ADMIN_DISPLAY_NAME=WP5 Smoke Admin' \
    -v 'WP1_SUPER_ADMIN_EMAIL=wp5-smoke-admin@example.com' \
    < "$SUPER_ADMIN_SEED_SQL" >> "$DB_LOG" 2>&1
}

start_platform_api() {
  local api_port="$1"
  local db_port="$2"
  local release_blocking_enabled="$3"
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
    WP5_RELEASE_READINESS_PUBLISH_BLOCKING_ENABLED="$release_blocking_enabled" \
    WP3_TRUSTED_CALLER_SERVICES="$ASSET_TRUSTED_SERVICES" \
    WP5_TRUSTED_CALLER_SERVICES="$TEST_DESIGN_TRUSTED_SERVICES" \
      mvn -B -pl platform-api spring-boot:run \
        -Dspring-boot.run.profiles=db \
        -Dspring-boot.run.arguments="--server.port=$api_port"
  ) > "$APP_LOG" 2>&1 &
  APP_PID="$!"
}

wait_for_platform_api() {
  local base_url="$1"
  local timeout="${WP5_SMOKE_API_READY_TIMEOUT_SECONDS:-120}"
  for _ in $(seq 1 "$timeout"); do
    if curl -fsS "$base_url/api/v1/test-design/health" >/dev/null 2>&1; then
      return
    fi
    if [[ -n "$APP_PID" ]] && ! kill -0 "$APP_PID" >/dev/null 2>&1; then
      echo "platform-api exited before WP5 smoke could start. Log: $APP_LOG" >&2
      tail -n 160 "$APP_LOG" >&2 || true
      exit 1
    fi
    sleep 1
  done

  echo "platform-api did not become ready in ${timeout}s. Log: $APP_LOG" >&2
  tail -n 160 "$APP_LOG" >&2 || true
  exit 1
}

main() {
  require_tool docker
  require_tool curl
  require_tool jq
  require_tool mvn
  mkdir -p "$OUT_DIR"
  trap cleanup EXIT INT TERM

  local api_port base_url db_port release_blocking_enabled
  api_port="$(choose_api_port)"
  base_url="http://127.0.0.1:$api_port"
  release_blocking_enabled="$(release_readiness_publish_blocking_enabled)"

  echo "== WP5 managed HTTP smoke runtime =="
  echo "platformApi=$base_url logs=$OUT_DIR"
  echo "releaseReadinessPublishBlockingEnabled=$release_blocking_enabled"
  start_postgres
  db_port="$(host_postgres_port)"
  start_platform_api "$api_port" "$db_port" "$release_blocking_enabled"
  wait_for_platform_api "$base_url"
  seed_super_admin

  WP1_SERVICE_TOKEN="$WP1_SERVICE_TOKEN_VALUE" \
  WP3_SERVICE_TOKEN="$WP3_SERVICE_TOKEN_VALUE" \
  WP5_SERVICE_TOKEN="$WP5_SERVICE_TOKEN_VALUE" \
  WP5_SMOKE_BASE_URL="$base_url" \
  WP5_SMOKE_CALLER_SERVICE="$CALLER_SERVICE" \
    bash "$ROOT_DIR/scripts/wp5_test_design_smoke.sh"
}

main "$@"
