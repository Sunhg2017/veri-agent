#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
COMPOSE_FILE="$PROJECT_DIR/infra/docker-compose.yml"

echo "== Veri Agent unified deployment =="

if ! command -v docker >/dev/null 2>&1; then
  echo "Docker is required." >&2
  exit 127
fi

if ! docker compose version >/dev/null 2>&1; then
  echo "Docker Compose is required." >&2
  exit 127
fi

cd "$PROJECT_DIR"
docker compose -f "$COMPOSE_FILE" build --parallel
docker compose -f "$COMPOSE_FILE" up -d

echo "waiting for PostgreSQL..."
until docker compose -f "$COMPOSE_FILE" exec -T postgres pg_isready -U veri_agent -d veri_agent >/dev/null 2>&1; do
  sleep 2
done

echo "waiting for platform-api..."
for _ in {1..40}; do
  if curl -fsS http://localhost:8080/api/v1/health >/dev/null 2>&1; then
    echo "platform-api is ready."
    echo "portal-web: http://localhost:5173"
    echo "platform-api health: http://localhost:8080/api/v1/health"
    echo "swagger: http://localhost:8080/swagger-ui.html"
    echo "next: bash scripts/wp_all_integration_test.sh"
    exit 0
  fi
  sleep 3
done

echo "platform-api did not become ready in time." >&2
docker compose -f "$COMPOSE_FILE" logs platform-api --tail 80
exit 1
