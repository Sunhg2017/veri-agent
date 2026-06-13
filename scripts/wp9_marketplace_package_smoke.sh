#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PACKAGE_DIR="$ROOT_DIR/integrations/wp9-webhook-marketplace"

require_tool() {
  local tool="$1"
  if ! command -v "$tool" >/dev/null 2>&1; then
    echo "$tool is required for WP9 marketplace package smoke" >&2
    exit 127
  fi
}

assert_contains() {
  local name="$1"
  local haystack="$2"
  local needle="$3"
  if [[ "$haystack" != *"$needle"* ]]; then
    echo "FAIL $name: expected output to contain: $needle" >&2
    echo "$haystack" >&2
    exit 1
  fi
}

assert_not_contains() {
  local name="$1"
  local haystack="$2"
  local needle="$3"
  if [[ "$haystack" == *"$needle"* ]]; then
    echo "FAIL $name: output must not contain: $needle" >&2
    echo "$haystack" >&2
    exit 1
  fi
}

validate_package_files() {
  node - "$ROOT_DIR" <<'NODE'
const fs = require('fs');
const path = require('path');

const root = process.argv[2];
const packageDir = path.join(root, 'integrations/wp9-webhook-marketplace');

function fail(message) {
  console.error(message);
  process.exit(1);
}

function readRelative(relativePath) {
  const fullPath = path.join(packageDir, relativePath);
  if (!fs.existsSync(fullPath)) {
    fail(`Missing package file: ${relativePath}`);
  }
  return fs.readFileSync(fullPath, 'utf8');
}

const manifest = JSON.parse(readRelative('manifest.json'));
if (manifest.schemaVersion !== 'veri.wp9.webhook.marketplace.v1') {
  fail('Unexpected manifest schemaVersion');
}
if (manifest.packageId !== 'veri-agent.wp9.webhook.ci-generic') {
  fail('Unexpected manifest packageId');
}
for (const provider of ['github-actions', 'gitlab-ci', 'jenkins']) {
  if (!manifest.providers.includes(provider)) {
    fail(`Manifest missing provider: ${provider}`);
  }
}
if (manifest.runtimeContract.signature.canonicalString !== 'timestamp.eventId.rawBody') {
  fail('Manifest signature canonical string mismatch');
}
for (const header of ['X-VA-Timestamp', 'X-VA-Event-Id', 'X-VA-Signature']) {
  if (!manifest.runtimeContract.signature.headers.includes(header)) {
    fail(`Manifest missing signature header: ${header}`);
  }
}

const templates = manifest.templates || [];
if (templates.length !== 3) {
  fail('Expected exactly three provider templates');
}
for (const template of templates) {
  const content = readRelative(template.path);
  for (const required of ['X-VA-Timestamp', 'X-VA-Event-Id', 'X-VA-Signature', 'VERI_AGENT_WP9_WEBHOOK_URL', 'VERI_AGENT_WP9_WEBHOOK_SECRET']) {
    if (!content.includes(required)) {
      fail(`${template.path} missing ${required}`);
    }
  }
  if (!content.includes('timestamp.$') && !content.includes('$timestamp.')) {
    fail(`${template.path} does not build the WP9 canonical signature string`);
  }
  if (!content.includes('--data-binary')) {
    fail(`${template.path} must send the exact raw body with --data-binary`);
  }
  if (content.includes('secret://')) {
    fail(`${template.path} must not expose Veri Agent secretRef values to CI`);
  }
}

for (const payload of manifest.payloadExamples || []) {
  const raw = readRelative(payload.path);
  const parsed = JSON.parse(raw);
  if (parsed.provider === undefined || parsed.status === undefined) {
    fail(`${payload.path} must include provider and status`);
  }
}

const readme = readRelative('README.md');
for (const required of ['安装变量', '事件幂等策略', '上架检查', '真实 OAuth/App']) {
  if (!readme.includes(required)) {
    fail(`README missing section marker: ${required}`);
  }
}
console.log('WP9 marketplace package manifest and templates passed.');
NODE
}

validate_signature_helper() {
  local signature expected curl_output
  signature="$(
    WP9_WEBHOOK_OUTPUT=signature \
    WP9_WEBHOOK_SECRET='package-secret' \
    WP9_WEBHOOK_TRIGGER_ID='trigger-package' \
    WP9_WEBHOOK_TIMESTAMP='1760000000' \
    WP9_WEBHOOK_EVENT_ID='marketplace:offline:001' \
    WP9_WEBHOOK_PAYLOAD='{"provider":"marketplace","status":"success"}' \
    bash "$ROOT_DIR/scripts/wp9_webhook_sign.sh"
  )"
  expected="$(
    printf '%s' '1760000000.marketplace:offline:001.{"provider":"marketplace","status":"success"}' \
      | openssl dgst -sha256 -hmac 'package-secret' -hex \
      | awk '{print $NF}'
  )"
  if [[ "$signature" != "$expected" ]]; then
    echo "FAIL deterministic signature mismatch" >&2
    echo "expected=$expected actual=$signature" >&2
    exit 1
  fi

  curl_output="$(
    WP9_WEBHOOK_OUTPUT=curl \
    WP9_WEBHOOK_SECRET='package-secret' \
    WP9_WEBHOOK_TRIGGER_ID='trigger-package' \
    WP9_WEBHOOK_TIMESTAMP='1760000000' \
    WP9_WEBHOOK_EVENT_ID='marketplace:offline:001' \
    WP9_WEBHOOK_PAYLOAD='{"provider":"marketplace","status":"success"}' \
    bash "$ROOT_DIR/scripts/wp9_webhook_sign.sh"
  )"
  assert_contains "curl output includes signature header" "$curl_output" "X-VA-Signature"
  assert_contains "curl output includes raw body flag" "$curl_output" "--data-binary"
  assert_not_contains "curl output hides secret" "$curl_output" "package-secret"
}

main() {
  require_tool node
  require_tool openssl
  require_tool awk
  bash -n "$ROOT_DIR/scripts/wp9_webhook_sign.sh"
  bash -n "$ROOT_DIR/scripts/wp9_marketplace_package_smoke.sh"
  validate_package_files
  validate_signature_helper
  echo "WP9 marketplace package smoke passed."
}

main "$@"
