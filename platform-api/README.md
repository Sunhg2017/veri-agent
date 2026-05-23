# platform-api

`platform-api` is the single consolidated Java service for WP1 control-plane, WP2 model access, and WP3 test assets. WP1/WP2/WP3 are domain/task splits inside this service, not separately deployed services.

## Current Skeleton

Implemented in this baseline:

- Spring Boot 3.5.x + Java 21 + Maven module.
- Unified API response shape: `code`, `message`, `traceId`, `data`.
- `X-Trace-Id` request/response propagation and MDC logging field.
- Global exception handling for validation, business, authentication, authorization, and unexpected errors.
- Stateless Spring Security baseline with public health and example endpoints.
- SuperAdmin is initialized by the DB seed script and must change password after first login.
- Login, forced password change, refresh-token rotation, logout/session revoke, and current-user APIs with a lightweight Bearer token baseline.
- WP1 management APIs for departments, users, user account lifecycle, projects, applications, environments, integrations, audit logs, and settings CRUD.
- Formal project/application/environment request models in the `db` profile, covering create, detail, update, status transitions, project members, application owners, environment authorized users, resource-scoped role binding, codes, project/application ownership, sensitivity level, public-model policy, default URLs, environment type, and project/application environment scope.
- RBAC permission checks on management APIs. The `local` profile resolves built-in role permissions in memory; the `db` profile resolves permissions from `rbac_role_permission` and applies resource-scope filtering to project, application, environment, audit, and settings views.
- Local profile keeps in-memory sample data; `db` profile persists departments, users, sessions, projects, applications, environments, settings, and audit logs in PostgreSQL, including audit before/after/diff fields.
- OpenAPI metadata and Bearer security scheme are configured, and contract tests protect WP1/WP2/WP3 key paths.
- WP2 model access APIs are available under `/api/v1/model-access`.
- WP3 asset APIs are available under `/api/v1/asset`, covering requirements, APIs, pages, business flows, test cases, steps, and trace links.
- WP2/WP3 reuse WP1 context validation and audit writing through same-process Spring services, not HTTP callbacks to this service.
- Example paged endpoint for API contract and test scaffolding.
- Actuator health/info/metrics exposure.

## Run

```bash
mvn -pl platform-api spring-boot:run
```

The default profile is `local`, so the service can start before a database is configured.

To run local in-memory mode:

`WP1_AUTH_TOKEN_SECRET` must be at least 32 bytes. The value below is a local-only example.

```bash
WP1_AUTH_TOKEN_SECRET=local-auth-secret-32-byte-minimum! \
WP4_WEBHOOK_SECRET=local-document-input-webhook-secret \
mvn -pl platform-api spring-boot:run
```

To run with PostgreSQL and Flyway:

```bash
docker compose -f infra/docker-compose.yml up -d postgres
```

```bash
WP1_AUTH_TOKEN_SECRET=local-auth-secret-32-byte-minimum! \
WP1_DATASOURCE_URL=jdbc:postgresql://localhost:5432/veri_agent \
WP1_DATASOURCE_USERNAME=veri_agent \
WP1_DATASOURCE_PASSWORD=veri_agent_dev \
WP2_SERVICE_TOKEN=local-model-access-token \
WP3_SERVICE_TOKEN=local-asset-token \
WP4_WEBHOOK_SECRET=local-document-input-webhook-secret \
mvn -pl platform-api spring-boot:run -Dspring-boot.run.profiles=db
```

`WP4_WEBHOOK_SECRET` is only a local example for webhook smoke tests. Production should resolve WP4 webhook signing secrets through SecretProvider and disable the local fallback with `WP4_LOCAL_WEBHOOK_SECRET_FALLBACK_ENABLED=false`.

Health endpoint:

```bash
curl http://localhost:8080/api/v1/health
```

Seed the first SuperAdmin after Flyway migrations have created the WP1 tables and roles:

```bash
WP1_DATASOURCE_URL=jdbc:postgresql://localhost:5432/veri_agent \
WP1_DATASOURCE_USERNAME=veri_agent \
WP1_DATASOURCE_PASSWORD=veri_agent_dev \
WP1_SUPER_ADMIN_USERNAME=admin_user \
WP1_SUPER_ADMIN_PASSWORD=PlainPassword123 \
WP1_SUPER_ADMIN_DISPLAY_NAME=SuperAdmin \
WP1_SUPER_ADMIN_EMAIL=admin@example.com \
bash scripts/wp1_seed_super_admin.sh
```

Login and call a management API:

```bash
TOKEN="$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin_user","password":"PlainPassword123"}' \
  | jq -r '.data.accessToken')"

curl http://localhost:8080/api/v1/management/departments \
  -H "Authorization: Bearer $TOKEN"
```

Create a project, application, and application-scoped environment:

```bash
curl -X POST http://localhost:8080/api/v1/management/projects \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"code":"wp1-demo","name":"WP1 Demo","sensitivityLevel":"CONFIDENTIAL","allowPublicModel":false}'

curl -X POST http://localhost:8080/api/v1/management/applications \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"code":"wp1-demo-web","name":"WP1 Demo Web","project":"wp1-demo","appType":"Web","defaultApiBaseUrl":"https://api.demo.local","sensitivityLevel":"STRICT","allowPublicModel":false}'

curl -X POST http://localhost:8080/api/v1/management/environments \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"code":"wp1-demo-stg","name":"WP1 Demo Staging","project":"wp1-demo","application":"wp1-demo-web","scopeType":"APPLICATION","envType":"STAGING","webUrl":"https://demo.local","apiBaseUrl":"https://api.demo.local"}'
```

Rotate a refresh token and revoke the current session:

```bash
LOGIN="$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin_user","password":"PlainPassword123"}')"

REFRESH_TOKEN="$(echo "$LOGIN" | jq -r '.data.refreshToken')"

curl -X POST http://localhost:8080/api/v1/auth/refresh \
  -H 'Content-Type: application/json' \
  -d "{\"refreshToken\":\"$REFRESH_TOKEN\"}"

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
  -d '{"newPassword":"NewPassword123"}'
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
bash db/validation/run_wp2_db_validation.sh
```

When the `db` profile service is already running, execute the HTTP smoke test. It covers SuperAdmin login and first-login password change, token rotation, formal project/application/environment create/detail/update/status DTOs, project member add/list/remove with project-scoped role binding, application owner add/list/remove with application-scoped role binding, environment user add/list/remove with environment-scoped role binding, settings CRUD/status, sensitive setting rejection, core management object create/list paths, resource-scope list filtering, structured audit filters, failed-login audit, RBAC denial, account lock/unlock, account lifecycle, password change, and logout:

```bash
bash scripts/wp_all_integration_test.sh
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
