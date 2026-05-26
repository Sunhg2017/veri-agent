#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

TARGETS=(
  "$ROOT_DIR/README.md"
  "$ROOT_DIR/platform-api"
  "$ROOT_DIR/portal-web/src"
  "$ROOT_DIR/db/migration/wp1"
  "$ROOT_DIR/doc/mvp/final/WP1-单平台P0交付版-PRD与架构补充.md"
  "$ROOT_DIR/doc/mvp/final/engineering/WP1-P0-API契约.md"
  "$ROOT_DIR/doc/mvp/final/engineering/WP1-当前可持续研发底座交付说明.md"
  "$ROOT_DIR/doc/mvp/final/engineering/WP1-单平台权限矩阵与菜单矩阵.md"
)

while IFS= read -r wp4_doc; do
  TARGETS+=("$wp4_doc")
done < <(find "$ROOT_DIR/doc/mvp/final/engineering" -maxdepth 1 -type f -name 'WP4-*.md' | sort)

ALLOWLIST=(
  # Negative assertions and cleanup migrations are allowed to mention retired tenant terms.
  "$ROOT_DIR/platform-api/src/test/java/com/songhg/veri/agent/common/openapi/OpenApiContractTest.java"
  "$ROOT_DIR/platform-api/src/test/java/com/songhg/veri/agent/modelaccess/openapi/ModelAccessOpenApiContractTest.java"
  "$ROOT_DIR/platform-api/src/test/java/com/songhg/veri/agent/asset/openapi/AssetOpenApiContractTest.java"
  "$ROOT_DIR/platform-api/src/test/java/com/songhg/veri/agent/testdesign/openapi/TestDesignOpenApiContractTest.java"
  "$ROOT_DIR/db/migration/wp1/V20260518_012__wp2_single_platform_scope.sql"
)

OBSOLETE_MIGRATIONS=(
  "$ROOT_DIR/db/migration/wp1/V20260517_006__wp1_super_admin_permission_closure.sql"
)

if ! command -v rg >/dev/null 2>&1; then
  echo "rg is required for WP1 single-platform guard" >&2
  exit 2
fi

pattern='(/tenants|TenantAdmin|tenant_id|tenantId|base_tenant|tenant:|tenant_code|租户管理员|租户切换|跨租户)'
matches="$(rg -n --no-heading "$pattern" "${TARGETS[@]}" || true)"

for allowed in "${ALLOWLIST[@]}"; do
  matches="$(printf '%s\n' "$matches" | grep -Fv "$allowed:" || true)"
done

if [[ -n "${matches//[[:space:]]/}" ]]; then
  echo "WP1 single-platform guard failed. Remove or explicitly allow these legacy multi-instance references:" >&2
  printf '%s\n' "$matches" >&2
  exit 1
fi

for obsolete in "${OBSOLETE_MIGRATIONS[@]}"; do
  if [[ -e "$obsolete" ]]; then
    echo "WP1 single-platform guard failed. Obsolete bootstrap-era migration must not be restored: ${obsolete#$ROOT_DIR/}" >&2
    exit 1
  fi
done

echo "WP1 single-platform guard passed."
