#!/usr/bin/env bash
# ============================================================
# Veri Agent — 一键部署脚本（Postgres + WP1 + WP2 + 前端）
# ============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
COMPOSE_FILE="$PROJECT_DIR/infra/docker-compose.yml"

echo "=========================================="
echo " Veri Agent — 完整集成部署"
echo "=========================================="

# 1. 检查 Docker
if ! command -v docker &>/dev/null; then
    echo "❌ Docker 未安装。请先安装 Docker Desktop 或 Docker Engine。"
    exit 1
fi

# 2. 检查 Docker Compose
if ! docker compose version &>/dev/null; then
    echo "❌ Docker Compose 不可用。"
    exit 1
fi

# 3. 构建并启动所有服务
echo ""
echo "📦 构建并启动所有服务..."
echo "   第一次构建需要下载 Maven 依赖，可能需要 2-5 分钟。"
echo ""
cd "$PROJECT_DIR"

docker compose -f "$COMPOSE_FILE" build --parallel
echo ""
echo "🚀 启动服务..."
docker compose -f "$COMPOSE_FILE" up -d

# 4. 等待服务就绪
echo ""
echo "⏳ 等待服务就绪..."

# 等待 postgres
echo "   · PostgreSQL ..."
until docker compose -f "$COMPOSE_FILE" exec -T postgres pg_isready -U veri_agent -d veri_agent &>/dev/null; do
    sleep 2
done
echo "     ✅ PostgreSQL 就绪"

# 等待 platform-api
echo "   · WP1 Platform API (端口 8080) ..."
for i in {1..30}; do
    if curl -sf http://localhost:8080/api/v1/health >/dev/null 2>&1; then
        echo "     ✅ WP1 就绪"
        break
    fi
    if [ "$i" -eq 30 ]; then
        echo "     ❌ WP1 启动超时"
        docker compose -f "$COMPOSE_FILE" logs platform-api --tail 30
        exit 1
    fi
    sleep 3
done

# 等待 model-access
echo "   · WP2 Model Access (端口 8081) ..."
for i in {1..30}; do
    if curl -sf http://localhost:8081/api/v1/model-access/health >/dev/null 2>&1; then
        echo "     ✅ WP2 就绪"
        break
    fi
    if [ "$i" -eq 30 ]; then
        echo "     ❌ WP2 启动超时"
        docker compose -f "$COMPOSE_FILE" logs model-access --tail 30
        exit 1
    fi
    sleep 3
done

# 5. 验收摘要
echo ""
echo "=========================================="
echo " ✅ 部署完成 — 访问地址"
echo "=========================================="
echo ""
echo "  前端控制台   → http://localhost:5173"
echo "  WP1 健康     → http://localhost:8080/api/v1/health"
echo "  WP1 Swagger  → http://localhost:8080/swagger-ui.html"
echo "  WP2 健康     → http://localhost:8081/api/v1/model-access/health"
echo "  WP2 Swagger  → http://localhost:8081/swagger-ui.html"
echo ""
echo "  初始化 SuperAdmin:"
echo "    curl -X POST http://localhost:8080/api/v1/bootstrap/super-admin \\"
echo "      -H 'Content-Type: application/json' \\"
echo "      -d '{\"bootstrap_token\":\"local-init-token\",\"username\":\"admin\",\"password\":\"AdminPass12345\",\"display_name\":\"平台管理员\",\"email\":\"admin@example.com\"}'"
echo ""
echo "  后续步骤: bash scripts/wp1_wp2_integration_test.sh"
echo "=========================================="
