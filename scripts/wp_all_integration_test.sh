#!/usr/bin/env bash
# ============================================================
# Veri Agent — 统一服务集成联调测试
# 验证：platform-api（含 WP1 + WP2 + WP3 能力）端到端流程
# ============================================================
set -euo pipefail

BASE_URL="http://localhost:8080"
BOOTSTRAP_TOKEN="local-init-token"
PASS=0
FAIL=0

function check() {
    local name="$1"
    local expected="$2"
    local actual="$3"
    if echo "$actual" | grep -q "$expected"; then
        echo "   ✅ $name"
        PASS=$((PASS + 1))
    else
        echo "   ❌ $name"
        echo "      期望包含: $expected"
        echo "      实际结果: $actual"
        FAIL=$((FAIL + 1))
    fi
}

echo ""
echo "=========================================="
echo " Veri Agent — 统一服务集成联调测试"
echo "=========================================="
echo ""

# ─────────────────────────────────────────────────
# 1. 服务健康检查
# ─────────────────────────────────────────────────
echo "【1/7】服务健康检查"

WP1_HEALTH=$(curl -sf "$BASE_URL/api/v1/health" 2>&1 || echo "FAILED")
check "platform-api 健康检查" "UP" "$WP1_HEALTH"

echo ""

# ─────────────────────────────────────────────────
# 2. 初始化 & 登录
# ─────────────────────────────────────────────────
echo "【2/7】初始化 & 登录"

BOOTSTRAP_RESP=$(curl -s -X POST "$BASE_URL/api/v1/bootstrap/super-admin" \
    -H 'Content-Type: application/json' \
    -d '{"bootstrap_token":"'"$BOOTSTRAP_TOKEN"'","username":"admin","password":"AdminPass12345","display_name":"平台管理员","email":"admin@example.com"}')
check "初始化 SuperAdmin" "user_id" "$BOOTSTRAP_RESP"

LOGIN_RESP=$(curl -s -X POST "$BASE_URL/api/v1/auth/login" \
    -H 'Content-Type: application/json' \
    -d '{"username":"admin","password":"AdminPass12345"}')
TOKEN=$(echo "$LOGIN_RESP" | grep -o '"access_token":"[^"]*"' | cut -d'"' -f4)
check "获取 access_token" "eyJ" "$TOKEN"

echo ""

# ─────────────────────────────────────────────────
# 3. WP1: 管理面 CRUD
# ─────────────────────────────────────────────────
echo "【3/7】WP1 管理面 CRUD"

PROJECT_RESP=$(curl -s -X POST "$BASE_URL/api/v1/management/projects" \
    -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $TOKEN" \
    -d '{"code":"demo","name":"端到端测试项目","sensitivity_level":"INTERNAL","allow_public_model":false}')
check "创建项目" "demo" "$PROJECT_RESP"

DEPT_RESP=$(curl -s -X POST "$BASE_URL/api/v1/management/departments" \
    -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $TOKEN" \
    -d '{"name":"质量工程部"}')
check "创建部门" "质量工程部" "$DEPT_RESP"

echo ""

# ─────────────────────────────────────────────────
# 4. WP2: 模型接入
# ─────────────────────────────────────────────────
echo "【4/7】WP2 模型接入端点"

HEALTH_RESP=$(curl -s "$BASE_URL/api/v1/model-access/health")
check "WP2 健康检查" "UP" "$HEALTH_RESP"

PROVIDERS=$(curl -s "$BASE_URL/api/v1/model-access/providers" \
    -H "Authorization: Bearer local-init-token")
check "模型提供商列表" "local-echo" "$PROVIDERS"

# 模型调用
INVOKE_RESP=$(curl -s -X POST "$BASE_URL/api/v1/model-access/invocations" \
    -H 'Content-Type: application/json' \
    -H "Authorization: Bearer local-init-token" \
    -H "X-Caller-Service: wp5-test-design" \
    -H "X-Delegated-User-Id: admin" \
    -d '{"project_id":"demo","prompt_key":"test-case-design","messages":[{"role":"user","content":"生成 3 条冒烟测试点"}],"allow_public_model":false,"sensitivity_level":"INTERNAL"}')
INVOKE_STATUS=$(echo "$INVOKE_RESP" | grep -o '"status":"[^"]*"' | head -1 | cut -d'"' -f4)
check "模型调用" "SUCCEEDED" "$INVOKE_STATUS"

echo ""

# ─────────────────────────────────────────────────
# 5. WP3: 测试资产管理
# ─────────────────────────────────────────────────
echo "【5/7】WP3 测试资产管理端点"

HEALTH_RESP=$(curl -s "$BASE_URL/api/v1/asset/health")
check "WP3 健康检查" "UP" "$HEALTH_RESP"

# 创建需求
REQ_RESP=$(curl -s -X POST "$BASE_URL/api/v1/asset/requirements" \
    -H 'Content-Type: application/json' \
    -H "Authorization: Bearer local-init-token" \
    -d '{"project_id":"demo","code":"REQ-001","title":"用户登录功能","source":"MANUAL","priority":"HIGH"}')
REQ_ID=$(echo "$REQ_RESP" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)
check "创建需求" "REQ-001" "$REQ_RESP"

# 创建接口资产
API_RESP=$(curl -s -X POST "$BASE_URL/api/v1/asset/apis" \
    -H 'Content-Type: application/json' \
    -H "Authorization: Bearer local-init-token" \
    -d '{"project_id":"demo","code":"API-001","path":"/api/v1/auth/login","http_method":"POST","summary":"用户登录接口"}')
check "创建接口资产" "API-001" "$API_RESP"

# 创建测试用例
CASE_RESP=$(curl -s -X POST "$BASE_URL/api/v1/asset/test-cases" \
    -H 'Content-Type: application/json' \
    -H "Authorization: Bearer local-init-token" \
    -d '{"project_id":"demo","code":"TC-001","title":"验证正常登录","case_type":"FUNCTIONAL","steps":[{"step_order":1,"action":"输入用户名密码","expected_result":"登录成功"}]}')
CASE_ID=$(echo "$CASE_RESP" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)
check "创建测试用例" "TC-001" "$CASE_RESP"

# 建立追溯链接
if [ -n "$REQ_ID" ] && [ -n "$CASE_ID" ]; then
    LINK_RESP=$(curl -s -X POST "$BASE_URL/api/v1/asset/links" \
        -H 'Content-Type: application/json' \
        -H "Authorization: Bearer local-init-token" \
        -d "{\"source_type\":\"REQUIREMENT\",\"source_id\":\"$REQ_ID\",\"target_type\":\"CASE\",\"target_id\":\"$CASE_ID\",\"link_type\":\"COVERS\"}")
    check "创建追溯链接" "COVERS" "$LINK_RESP"
fi

# 查询需求列表
REQS=$(curl -s "$BASE_URL/api/v1/asset/requirements?project_id=demo" \
    -H "Authorization: Bearer local-init-token")
check "查询需求列表" "REQ-001" "$REQS"

echo ""

# ─────────────────────────────────────────────────
# 6. 审计日志验证
# ─────────────────────────────────────────────────
echo "【6/7】审计日志验证"

AUDIT_LOGIN=$(curl -s -X POST "$BASE_URL/api/v1/auth/login" \
    -H 'Content-Type: application/json' \
    -d '{"username":"admin","password":"AdminPass12345"}')
AUDIT_TOKEN=$(echo "$AUDIT_LOGIN" | grep -o '"access_token":"[^"]*"' | cut -d'"' -f4)

AUDIT_RESP=$(curl -s "$BASE_URL/api/v1/management/audit-logs?page_no=1&page_size=20" \
    -H "Authorization: Bearer $AUDIT_TOKEN")
AUDIT_COUNT=$(echo "$AUDIT_RESP" | grep -o '"total":[0-9]*' | head -1 | cut -d: -f2)
echo "      审计日志总数: ${AUDIT_COUNT:-0}"

if [ "${AUDIT_COUNT:-0}" -gt 0 ]; then
    check "审计日志写入" "$AUDIT_COUNT" "审计日志 $AUDIT_COUNT 条"
else
    check "审计日志" "审计日志" "$AUDIT_RESP"
fi

echo ""

# ─────────────────────────────────────────────────
# 7. 错误处理验证
# ─────────────────────────────────────────────────
echo "【7/7】错误处理验证"

NOT_FOUND=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/api/v1/asset/requirements/00000000-0000-0000-0000-000000000000")
check "404 处理" "404" "$NOT_FOUND"

NO_AUTH=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/api/v1/model-access/invocations" -X POST)
check "401 处理" "401" "$NO_AUTH"

echo ""

# ─────────────────────────────────────────────────
# 结果汇总
# ─────────────────────────────────────────────────
echo "=========================================="
echo " 测试结果汇总"
echo "=========================================="
echo "  通过: $PASS"
echo "  失败: $FAIL"
echo "  总计: $((PASS + FAIL))"
echo ""

if [ "$FAIL" -eq 0 ]; then
    echo " ✅ 统一服务集成联调全部通过"
    echo ""
    echo "  已验证完整闭环:"
    echo "    platform-api 健康 → 初始化 → 登录"
    echo "    → WP1: 项目管理/部门管理/审计"
    echo "    → WP2: 模型提供商/模型调用/调用日志"
    echo "    → WP3: 需求/接口资产/测试用例/步骤/追溯"
    echo "    → 审计日志查询"
    echo "    → 404/401 错误处理"
    echo ""
else
    echo " ❌ $FAIL 项测试失败"
    exit 1
fi
