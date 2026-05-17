# platform-api

`platform-api` is the WP1 control-plane service for users, RBAC, departments, projects, applications, environments, configuration, secrets, and audit.

## Current Skeleton

Implemented in this baseline:

- Spring Boot 3.5.x + Java 21 + Maven module.
- Unified API response shape: `code`, `message`, `trace_id`, `data`.
- `X-Trace-Id` request/response propagation and MDC logging field.
- Global exception handling for validation, business, authentication, authorization, and unexpected errors.
- Stateless Spring Security baseline with public health and example endpoints.
- SuperAdmin bootstrap API in both `local` and `db` profiles with token validation and BCrypt password hashing.
- Login, refresh-token rotation, logout/session revoke, and current-user APIs with a lightweight Bearer token baseline.
- WP1 management APIs for departments, users, user account lifecycle, projects, applications, environments, integrations, audit logs, and settings CRUD.
- Formal project/application/environment request models in the `db` profile, covering create, detail, update, status transitions, project members, application owners, environment authorized users, resource-scoped role binding, codes, project/application ownership, sensitivity level, public-model policy, default URLs, environment type, and project/application environment scope.
- RBAC permission checks on management APIs. The `local` profile resolves built-in role permissions in memory; the `db` profile resolves permissions from `rbac_role_permission` and applies resource-scope filtering to project, application, environment, audit, and settings views.
- Local profile keeps in-memory sample data; `db` profile persists departments, users, sessions, projects, applications, environments, settings, and audit logs in PostgreSQL, including audit before/after/diff fields.
- OpenAPI metadata and Bearer security scheme are configured, and contract tests protect WP1 key paths, including settings CRUD.
- Example paged endpoint for API contract and test scaffolding.
- Actuator health/info/metrics exposure.

## Run

```bash
mvn -pl platform-api spring-boot:run
```

The default profile is `local`, so the service can start before a database is configured.

To run local in-memory mode:

```bash
WP1_BOOTSTRAP_TOKEN=local-init-token \
WP1_AUTH_TOKEN_SECRET=local-auth-secret \
mvn -pl platform-api spring-boot:run
```

To run with PostgreSQL and Flyway:

```bash
docker compose -f infra/docker-compose.wp1.yml up -d postgres
```

```bash
WP1_BOOTSTRAP_TOKEN=local-init-token \
WP1_AUTH_TOKEN_SECRET=local-auth-secret \
WP1_DATASOURCE_URL=jdbc:postgresql://localhost:5432/veri_agent \
WP1_DATASOURCE_USERNAME=veri_agent \
WP1_DATASOURCE_PASSWORD=veri_agent_dev \
mvn -pl platform-api spring-boot:run -Dspring-boot.run.profiles=db
```

Health endpoint:

```bash
curl http://localhost:8080/api/v1/health
```

Bootstrap the first local SuperAdmin:

```bash
WP1_BOOTSTRAP_TOKEN=local-init-token mvn -pl platform-api spring-boot:run
curl -X POST http://localhost:8080/api/v1/bootstrap/super-admin \
  -H 'Content-Type: application/json' \
  -d '{
    "bootstrap_token": "local-init-token",
    "username": "admin_user",
    "password": "PlainPassword123",
    "display_name": "平台管理员",
    "email": "admin@example.com"
  }'
```

Login and call a management API:

```bash
TOKEN="$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin_user","password":"PlainPassword123"}' \
  | jq -r '.data.access_token')"

curl http://localhost:8080/api/v1/management/departments \
  -H "Authorization: Bearer $TOKEN"
```

Create a project, application, and application-scoped environment:

```bash
curl -X POST http://localhost:8080/api/v1/management/projects \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"code":"wp1-demo","name":"WP1 Demo","sensitivity_level":"CONFIDENTIAL","allow_public_model":false}'

curl -X POST http://localhost:8080/api/v1/management/applications \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"code":"wp1-demo-web","name":"WP1 Demo Web","project":"wp1-demo","app_type":"Web","default_api_base_url":"https://api.demo.local","sensitivity_level":"STRICT","allow_public_model":false}'

curl -X POST http://localhost:8080/api/v1/management/environments \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"code":"wp1-demo-stg","name":"WP1 Demo Staging","project":"wp1-demo","application":"wp1-demo-web","scope_type":"APPLICATION","env_type":"STAGING","web_url":"https://demo.local","api_base_url":"https://api.demo.local"}'
```

Rotate a refresh token and revoke the current session:

```bash
LOGIN="$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin_user","password":"PlainPassword123"}')"

REFRESH_TOKEN="$(echo "$LOGIN" | jq -r '.data.refresh_token')"

curl -X POST http://localhost:8080/api/v1/auth/refresh \
  -H 'Content-Type: application/json' \
  -d "{\"refresh_token\":\"$REFRESH_TOKEN\"}"

curl -X POST http://localhost:8080/api/v1/auth/logout \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"reason":"user logout"}'
```

Manage a user account:

```bash
curl http://localhost:8080/api/v1/management/users/tester_user \
  -H "Authorization: Bearer $TOKEN"

curl -X POST http://localhost:8080/api/v1/management/users/tester_user/lock \
  -H "Authorization: Bearer $TOKEN"

curl -X POST http://localhost:8080/api/v1/management/users/tester_user/unlock \
  -H "Authorization: Bearer $TOKEN"

curl -X POST http://localhost:8080/api/v1/management/users/tester_user/disable \
  -H "Authorization: Bearer $TOKEN"

curl -X POST http://localhost:8080/api/v1/management/users/tester_user/reset-password \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"new_password":"NewPassword123"}'
```

Swagger UI:

```text
http://localhost:8080/swagger-ui.html
```

## Test

```bash
mvn -pl platform-api test
```

Database migration validation is maintained at the repository root:

```bash
bash db/validation/run_wp1_db_validation.sh
```

When the `db` profile service is already running, execute the HTTP smoke test. It covers SuperAdmin bootstrap/login, token rotation, formal project/application/environment create/detail/update/status DTOs, project member add/list/remove with project-scoped role binding, application owner add/list/remove with application-scoped role binding, environment user add/list/remove with environment-scoped role binding, settings CRUD/status, sensitive setting rejection, core management object create/list paths, resource-scope list filtering, structured audit filters, failed-login audit, RBAC denial, account lock/unlock, account lifecycle, password change, and logout:

```bash
WP1_BOOTSTRAP_TOKEN=local-init-token bash scripts/wp1_db_profile_smoke.sh
```

The GitHub Actions workflow also runs a db-profile smoke job against PostgreSQL.

## WP1 1-8 Completion Notes

1. Settings now have paged list, create, detail, update, and status APIs.
2. RBAC now includes persisted resource-scope filtering plus frontend menu and button authorization rules.
3. Account and authentication audit covers successful login, failed login, account lifecycle, and password flows.
4. Audit persistence covers success, failure, denial, change results, and before/after/diff payloads.
5. Sensitive settings are rejected when submitted as plaintext and must use masked or secret-reference values.
6. OpenAPI contract tests cover settings CRUD alongside authentication, management, and account lifecycle paths.
7. The db-profile smoke test covers settings CRUD, sensitive setting rejection, failed-login audit, resource-scope filtering, and RBAC denial.
8. Release database-role validation is available via `scripts/wp1_release_role_validation.sh`.
