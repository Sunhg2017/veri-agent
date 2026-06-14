#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MAX_LINES="${PLATFORM_API_JAVA_MAX_LINES:-1200}"
SOURCE_DIR="$ROOT_DIR/platform-api/src/main/java"

if ! [[ "$MAX_LINES" =~ ^[1-9][0-9]*$ ]]; then
  echo "PLATFORM_API_JAVA_MAX_LINES must be a positive integer, got: $MAX_LINES" >&2
  exit 2
fi

violations=()
while IFS= read -r -d '' file; do
  lines="$(wc -l < "$file" | tr -d '[:space:]')"
  if (( lines > MAX_LINES )); then
    relative="${file#$ROOT_DIR/}"
    violations+=("$lines $relative")
  fi
done < <(find "$SOURCE_DIR" -name '*.java' -type f -print0)

if (( ${#violations[@]} > 0 )); then
  {
    echo "Platform API Java line guard failed: production Java files must be <= ${MAX_LINES} lines."
    printf '%s\n' "${violations[@]}" | sort -nr
  } >&2
  exit 1
fi

echo "Platform API Java line guard passed: all production Java files are <= ${MAX_LINES} lines."
