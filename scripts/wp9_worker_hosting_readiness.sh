#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${WP9_WORKER_HOSTING_ENV_FILE:-}"

load_env_file() {
  if [[ -z "$ENV_FILE" ]]; then
    return
  fi
  if [[ ! -f "$ENV_FILE" ]]; then
    echo "WP9_WORKER_HOSTING_ENV_FILE does not exist: $ENV_FILE" >&2
    exit 2
  fi
  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a
}

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

is_falsey() {
  case "${1:-}" in
    ""|0|false|FALSE|no|NO|off|OFF)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

is_positive_int() {
  [[ "${1:-}" =~ ^[1-9][0-9]*$ ]]
}

fail() {
  echo "FAIL $1" >&2
  exit 1
}

warn() {
  echo "WARN $1" >&2
}

assert_positive_range() {
  local name="$1"
  local value="$2"
  local min="$3"
  local max="$4"
  if ! is_positive_int "$value"; then
    fail "$name must be a positive integer, got '${value:-unset}'"
  fi
  if (( value < min || value > max )); then
    fail "$name must be in [$min,$max], got $value"
  fi
}

assert_worker_id() {
  local value="$1"
  if [[ -z "$value" ]]; then
    fail "WP9_SCHEDULER_WORKER_ID is required for role $ROLE"
  fi
  if (( ${#value} > 128 )); then
    fail "WP9_SCHEDULER_WORKER_ID must be <=128 chars"
  fi
  if [[ ! "$value" =~ ^[A-Za-z0-9_.:-]+$ ]]; then
    fail "WP9_SCHEDULER_WORKER_ID contains unsupported characters"
  fi
}

is_release_gate() {
  if is_truthy "${WP9_RELEASE_GATE:-0}"; then
    return 0
  fi
  case "${WP9_GATE_MODE:-development}" in
    release|RELEASE|preprod|PREPROD|prod|PROD|production|PRODUCTION)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

validate_release_evidence() {
  if ! is_release_gate; then
    return
  fi
  case "${WP9_SCHEDULER_SMOKE:-0}" in
    1|true|TRUE|managed|auto)
      ;;
    *)
      fail "release worker readiness requires WP9_SCHEDULER_SMOKE=managed"
      ;;
  esac
  case "${WP9_WEBHOOK_HTTP_SMOKE:-0}" in
    1|true|TRUE|managed|external|auto)
      ;;
    *)
      fail "release worker readiness requires WP9_WEBHOOK_HTTP_SMOKE=managed or external"
      ;;
  esac
}

validate_role_switches() {
  case "$ROLE" in
    web)
      if ! is_truthy "$XXL_JOB_ENABLED"; then
        fail "web role must set PLATFORM_XXL_JOB_ENABLED=true so shared scheduled handlers stay registered"
      fi
      if is_truthy "$SCHEDULER_ENABLED"; then
        fail "web role must keep WP9_SCHEDULER_ENABLED=false"
      fi
      if is_truthy "$CRON_ENABLED"; then
        fail "web role must keep WP9_CRON_ENABLED=false"
      fi
      ;;
    scheduler-active)
      if ! is_truthy "$XXL_JOB_ENABLED"; then
        fail "scheduler-active role requires PLATFORM_XXL_JOB_ENABLED=true"
      fi
      if ! is_truthy "$SCHEDULER_ENABLED"; then
        fail "scheduler-active role requires WP9_SCHEDULER_ENABLED=true"
      fi
      if is_truthy "$WEBHOOK_ENABLED"; then
        fail "scheduler-active role must keep WP9_WEBHOOK_ENABLED=false; use web instances for ingress"
      fi
      ;;
    scheduler-standby)
      if ! is_truthy "$XXL_JOB_ENABLED"; then
        fail "scheduler-standby role must keep PLATFORM_XXL_JOB_ENABLED=true so failover only flips WP9 role switches"
      fi
      if ! is_falsey "$SCHEDULER_ENABLED"; then
        fail "scheduler-standby role must keep WP9_SCHEDULER_ENABLED=false until failover"
      fi
      if ! is_falsey "$CRON_ENABLED"; then
        fail "scheduler-standby role must keep WP9_CRON_ENABLED=false until failover"
      fi
      if ! is_falsey "$WEBHOOK_ENABLED"; then
        fail "scheduler-standby role must keep WP9_WEBHOOK_ENABLED=false"
      fi
      ;;
    *)
      fail "Unsupported WP9_WORKER_HOSTING_ROLE=$ROLE; use web, scheduler-active, or scheduler-standby"
      ;;
  esac
}

validate_timing() {
  assert_positive_range "WP9_SCHEDULER_INTERVAL_MS" "$SCHEDULER_INTERVAL_MS" 1 600000
  assert_positive_range "WP9_SCHEDULER_INITIAL_DELAY_MS" "$SCHEDULER_INITIAL_DELAY_MS" 1 3600000
  assert_positive_range "WP9_SCHEDULER_TICK_BATCH_SIZE" "$SCHEDULER_TICK_BATCH_SIZE" 1 100
  assert_positive_range "WP9_MAX_CONCURRENT_NODES_PER_RUN" "$MAX_CONCURRENT_NODES_PER_RUN" 1 100
  assert_positive_range "WP9_NODE_HEARTBEAT_TIMEOUT_SECONDS" "$NODE_HEARTBEAT_TIMEOUT_SECONDS" 1 86400
  assert_positive_range "WP9_RECOVERY_BATCH_SIZE" "$RECOVERY_BATCH_SIZE" 1 1000

  if (( SCHEDULER_TICK_BATCH_SIZE > MAX_CONCURRENT_NODES_PER_RUN )); then
    fail "WP9_SCHEDULER_TICK_BATCH_SIZE must be <= WP9_MAX_CONCURRENT_NODES_PER_RUN"
  fi
  if (( NODE_HEARTBEAT_TIMEOUT_SECONDS * 1000 <= SCHEDULER_INTERVAL_MS * 2 )); then
    warn "node heartbeat timeout is close to scheduler interval; recovery may be aggressive"
  fi
}

print_summary() {
  cat <<EOF
WP9 worker hosting readiness passed.
role=$ROLE
xxlJobEnabled=$XXL_JOB_ENABLED
schedulerEnabled=$SCHEDULER_ENABLED
cronEnabled=$CRON_ENABLED
webhookEnabled=$WEBHOOK_ENABLED
workerId=$SCHEDULER_WORKER_ID
schedulerIntervalMs=$SCHEDULER_INTERVAL_MS
schedulerTickBatchSize=$SCHEDULER_TICK_BATCH_SIZE
nodeHeartbeatTimeoutSeconds=$NODE_HEARTBEAT_TIMEOUT_SECONDS
envFile=${ENV_FILE:-<current-shell>}
EOF
}

main() {
  load_env_file
  ROLE="${WP9_WORKER_HOSTING_ROLE:-web}"
  XXL_JOB_ENABLED="${PLATFORM_XXL_JOB_ENABLED:-false}"
  SCHEDULER_ENABLED="${WP9_SCHEDULER_ENABLED:-false}"
  CRON_ENABLED="${WP9_CRON_ENABLED:-false}"
  WEBHOOK_ENABLED="${WP9_WEBHOOK_ENABLED:-false}"
  SCHEDULER_WORKER_ID="${WP9_SCHEDULER_WORKER_ID:-wp9-managed-worker}"
  SCHEDULER_INTERVAL_MS="${WP9_SCHEDULER_INTERVAL_MS:-5000}"
  SCHEDULER_INITIAL_DELAY_MS="${WP9_SCHEDULER_INITIAL_DELAY_MS:-30000}"
  SCHEDULER_TICK_BATCH_SIZE="${WP9_SCHEDULER_TICK_BATCH_SIZE:-4}"
  MAX_CONCURRENT_NODES_PER_RUN="${WP9_MAX_CONCURRENT_NODES_PER_RUN:-4}"
  NODE_HEARTBEAT_TIMEOUT_SECONDS="${WP9_NODE_HEARTBEAT_TIMEOUT_SECONDS:-180}"
  RECOVERY_BATCH_SIZE="${WP9_RECOVERY_BATCH_SIZE:-50}"

  assert_worker_id "$SCHEDULER_WORKER_ID"
  validate_role_switches
  validate_timing
  validate_release_evidence
  print_summary
}

main "$@"
