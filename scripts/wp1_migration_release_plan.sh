#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MIGRATION_DIR="${WP1_MIGRATION_DIR:-$ROOT_DIR/db/migration/wp1}"
OUT_DIR="${WP1_MIGRATION_PLAN_OUT_DIR:-$ROOT_DIR/build/wp1-migration-release-plan}"
CURRENT_VERSION="${WP1_MIGRATION_CURRENT_VERSION:-0}"
TARGET_VERSION="${WP1_MIGRATION_TARGET_VERSION:-latest}"
RELEASE_NAME="${WP1_RELEASE_NAME:-local-$(date -u +%Y%m%dT%H%M%SZ)}"

fail() {
  echo "ERROR: $*" >&2
  exit 1
}

normalize_version() {
  local value="$1"
  value="${value#V}"
  value="${value//./_}"
  if [[ -z "$value" ]]; then
    echo "0"
  else
    echo "$value"
  fi
}

hash_file() {
  local file="$1"
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$file" | awk '{print $1}'
  elif command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "$file" | awk '{print $1}'
  else
    fail "sha256sum or shasum is required"
  fi
}

has_version() {
  local candidate="$1"
  local seen
  if [[ "${#versions[@]}" -eq 0 ]]; then
    return 1
  fi
  for seen in "${versions[@]}"; do
    if [[ "$seen" == "$candidate" ]]; then
      return 0
    fi
  done
  return 1
}

[[ -d "$MIGRATION_DIR" ]] || fail "migration directory not found: $MIGRATION_DIR"

mkdir -p "$OUT_DIR"

CURRENT_VERSION="$(normalize_version "$CURRENT_VERSION")"
if [[ "$TARGET_VERSION" != "latest" ]]; then
  TARGET_VERSION="$(normalize_version "$TARGET_VERSION")"
fi

versions=()
files=()
while IFS= read -r file; do
  base="$(basename "$file")"
  if [[ ! "$base" =~ ^V[0-9]{8}_[0-9]{3}__[A-Za-z0-9_]+\.sql$ ]]; then
    fail "invalid Flyway migration filename: $base"
  fi
  version="${base%%__*}"
  version="${version#V}"
  if has_version "$version"; then
    fail "duplicate Flyway migration version: $version"
  fi
  versions+=("$version")
  files+=("$file")
done < <(find "$MIGRATION_DIR" -maxdepth 1 -type f -name 'V*.sql' | sort)

if [[ "${#files[@]}" -eq 0 ]]; then
  fail "no Flyway migrations found in $MIGRATION_DIR"
fi

latest_version="${versions[$((${#versions[@]} - 1))]}"
if [[ "$TARGET_VERSION" == "latest" ]]; then
  TARGET_VERSION="$latest_version"
fi

if [[ "$CURRENT_VERSION" != "0" ]] && ! has_version "$CURRENT_VERSION"; then
  fail "current migration version not found: $CURRENT_VERSION"
fi
has_version "$TARGET_VERSION" || fail "target migration version not found: $TARGET_VERSION"
if [[ "$CURRENT_VERSION" > "$TARGET_VERSION" ]]; then
  fail "current migration version is newer than target: $CURRENT_VERSION > $TARGET_VERSION"
fi

manifest="$OUT_DIR/manifest.tsv"
plan="$OUT_DIR/release-plan.md"

{
  printf "order\tversion\tscope\trollback_strategy\tsha256\tfile\n"
  pending_count=0
  for i in "${!files[@]}"; do
    version="${versions[$i]}"
    file="${files[$i]}"
    scope="baseline"
    if [[ "$version" > "$CURRENT_VERSION" ]]; then
      if [[ "$version" < "$TARGET_VERSION" || "$version" == "$TARGET_VERSION" ]]; then
        scope="pending"
        pending_count=$((pending_count + 1))
      fi
    fi
    if [[ "$version" > "$TARGET_VERSION" ]]; then
      scope="future"
    fi
    printf "%s\t%s\t%s\t%s\t%s\t%s\n" \
      "$((i + 1))" \
      "$version" \
      "$scope" \
      "forward-fix-or-restore-backup" \
      "$(hash_file "$file")" \
      "${file#$ROOT_DIR/}"
  done
} > "$manifest"

{
  echo "# WP1-WP4 Migration Release Plan"
  echo
  echo "| Item | Value |"
  echo "|---|---|"
  echo "| Release name | $RELEASE_NAME |"
  echo "| Migration directory | ${MIGRATION_DIR#$ROOT_DIR/} |"
  echo "| Current version | $CURRENT_VERSION |"
  echo "| Target version | $TARGET_VERSION |"
  echo "| Latest repository version | $latest_version |"
  echo "| Pending migrations | $pending_count |"
  echo "| Manifest | $manifest |"
  echo
  echo "## Rollback Posture"
  echo
  echo "This project uses forward-only Flyway V migrations. Flyway Undo migrations are not used."
  echo "If a migration has not been applied, stop the release, fix the migration, rerun validation, and redeploy."
  echo "If a migration was applied and the application has not cut over, prefer restoring the pre-migration database backup."
  echo "If traffic has already cut over, prefer a forward-fix migration or a compensating application release, then run smoke and release role validation again."
  echo
  echo "## Required Release Evidence"
  echo
  echo "1. Pre-migration backup artifact or DBA backup ticket."
  echo "2. This release plan and manifest."
  echo "3. Output from db/validation/run_wp1_db_validation.sh or CI equivalent."
  echo "4. Output from scripts/wp1_release_role_validation.sh for the target environment."
  echo "5. Post-migration smoke result and rollback/forward-fix decision record."
  echo
  echo "## Pending Migrations"
  echo
  echo "| Order | Version | File | SHA-256 |"
  echo "|---|---|---|---|"
  while IFS=$'\t' read -r order version scope rollback hash file; do
    if [[ "$order" == "order" || "$scope" != "pending" ]]; then
      continue
    fi
    echo "| $order | $version | $file | $hash |"
  done < "$manifest"
  if [[ "$pending_count" -eq 0 ]]; then
    echo "| - | - | No pending migration for the selected current/target version. | - |"
  fi
} > "$plan"

echo "WP1 migration release plan generated."
echo "Plan: $plan"
echo "Manifest: $manifest"
echo "Current version: $CURRENT_VERSION"
echo "Target version: $TARGET_VERSION"
echo "Pending migrations: $pending_count"
