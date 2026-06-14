#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${WP9_WEBHOOK_BASE_URL:-${WP9_WEBHOOK_SMOKE_BASE_URL:-http://127.0.0.1:8080}}"
WEBHOOK_URL="${WP9_WEBHOOK_URL:-}"
TRIGGER_ID="${WP9_WEBHOOK_TRIGGER_ID:-}"
SECRET="${WP9_WEBHOOK_SECRET:-}"
SECRET_FILE="${WP9_WEBHOOK_SECRET_FILE:-}"
PAYLOAD="${WP9_WEBHOOK_PAYLOAD:-}"
PAYLOAD_FILE="${WP9_WEBHOOK_PAYLOAD_FILE:-}"
EVENT_ID="${WP9_WEBHOOK_EVENT_ID:-ci-$(date +%s)-$RANDOM}"
TIMESTAMP="${WP9_WEBHOOK_TIMESTAMP:-$(date +%s)}"
OUTPUT="${WP9_WEBHOOK_OUTPUT:-curl}"
SEND="${WP9_WEBHOOK_SEND:-0}"

require_tool() {
  local tool="$1"
  if ! command -v "$tool" >/dev/null 2>&1; then
    echo "$tool is required for WP9 webhook signing" >&2
    exit 127
  fi
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

shell_quote() {
  printf '%q' "$1"
}

load_secret() {
  if [[ -n "$SECRET_FILE" ]]; then
    if [[ ! -f "$SECRET_FILE" ]]; then
      echo "WP9_WEBHOOK_SECRET_FILE does not exist: $SECRET_FILE" >&2
      exit 2
    fi
    SECRET="$(<"$SECRET_FILE")"
    SECRET="${SECRET%$'\n'}"
  fi
  if [[ -z "$SECRET" ]]; then
    echo "WP9_WEBHOOK_SECRET or WP9_WEBHOOK_SECRET_FILE is required" >&2
    exit 2
  fi
}

resolve_webhook_url() {
  if [[ -n "$WEBHOOK_URL" ]]; then
    return
  fi
  if [[ -z "$TRIGGER_ID" ]]; then
    echo "Set WP9_WEBHOOK_URL or WP9_WEBHOOK_TRIGGER_ID" >&2
    exit 2
  fi
  WEBHOOK_URL="${BASE_URL%/}/api/v1/execution/webhooks/$TRIGGER_ID"
}

resolve_payload() {
  if [[ -n "$PAYLOAD_FILE" && -n "$PAYLOAD" ]]; then
    echo "Set only one of WP9_WEBHOOK_PAYLOAD_FILE or WP9_WEBHOOK_PAYLOAD" >&2
    exit 2
  fi
  if [[ -n "$PAYLOAD_FILE" ]]; then
    if [[ ! -f "$PAYLOAD_FILE" ]]; then
      echo "WP9_WEBHOOK_PAYLOAD_FILE does not exist: $PAYLOAD_FILE" >&2
      exit 2
    fi
    return
  fi
  if [[ -z "$PAYLOAD" ]]; then
    PAYLOAD='{"source":"ci","provider":"generic","status":"success"}'
  fi
}

webhook_signature() {
  if [[ -n "$PAYLOAD_FILE" ]]; then
    { printf '%s' "$TIMESTAMP.$EVENT_ID."; cat "$PAYLOAD_FILE"; } \
      | openssl dgst -sha256 -hmac "$SECRET" -hex \
      | awk '{print $NF}'
  else
    printf '%s' "$TIMESTAMP.$EVENT_ID.$PAYLOAD" \
      | openssl dgst -sha256 -hmac "$SECRET" -hex \
      | awk '{print $NF}'
  fi
}

print_headers() {
  local signature="$1"
  printf 'X-VA-Timestamp: %s\n' "$TIMESTAMP"
  printf 'X-VA-Event-Id: %s\n' "$EVENT_ID"
  printf 'X-VA-Signature: %s\n' "$signature"
}

print_curl() {
  local signature="$1"
  printf 'curl -fsS -X POST %s \\\n' "$(shell_quote "$WEBHOOK_URL")"
  printf '  -H %s \\\n' "$(shell_quote 'Content-Type: application/json')"
  printf '  -H %s \\\n' "$(shell_quote "X-VA-Timestamp: $TIMESTAMP")"
  printf '  -H %s \\\n' "$(shell_quote "X-VA-Event-Id: $EVENT_ID")"
  printf '  -H %s \\\n' "$(shell_quote "X-VA-Signature: $signature")"
  if [[ -n "$PAYLOAD_FILE" ]]; then
    printf '  --data-binary %s\n' "$(shell_quote "@$PAYLOAD_FILE")"
  else
    printf '  --data-binary %s\n' "$(shell_quote "$PAYLOAD")"
  fi
}

send_webhook() {
  local signature="$1"
  require_tool curl
  if [[ -n "$PAYLOAD_FILE" ]]; then
    curl -fsS -X POST "$WEBHOOK_URL" \
      -H 'Content-Type: application/json' \
      -H "X-VA-Timestamp: $TIMESTAMP" \
      -H "X-VA-Event-Id: $EVENT_ID" \
      -H "X-VA-Signature: $signature" \
      --data-binary "@$PAYLOAD_FILE"
  else
    curl -fsS -X POST "$WEBHOOK_URL" \
      -H 'Content-Type: application/json' \
      -H "X-VA-Timestamp: $TIMESTAMP" \
      -H "X-VA-Event-Id: $EVENT_ID" \
      -H "X-VA-Signature: $signature" \
      --data-binary "$PAYLOAD"
  fi
}

main() {
  require_tool openssl
  require_tool awk
  load_secret
  resolve_webhook_url
  resolve_payload

  local signature
  signature="$(webhook_signature)"

  if is_truthy "$SEND"; then
    send_webhook "$signature"
    return
  fi

  case "$OUTPUT" in
    curl)
      print_curl "$signature"
      ;;
    headers)
      print_headers "$signature"
      ;;
    signature)
      printf '%s\n' "$signature"
      ;;
    *)
      echo "Unsupported WP9_WEBHOOK_OUTPUT=$OUTPUT; use curl, headers, or signature." >&2
      exit 2
      ;;
  esac
}

main "$@"
