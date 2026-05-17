# model-access

`model-access` is the WP2 service for model provider configuration, prompt version management, guarded model invocation, fallback routing, usage cost calculation, and model invocation audit logs.

## Scope

- Model provider registry with create/update, enable/disable, priority, timeout, secret reference, and token cost fields.
- Provider readiness check endpoint for adapter/secret/base URL diagnostics without writing invocation logs.
- Prompt template versioning with one active version per `prompt_key`.
- Unified invocation API that renders active prompts, enforces `sensitivity_level` routing policy, blocks obvious secrets/PII, enforces optional daily budget limits, masks previews, records SHA-256 prompt digests, calculates cost, and falls back to the next enabled provider.
- WP1-compatible service-call shape: callers use `Authorization: Bearer <WP2_SERVICE_TOKEN>`, `X-Caller-Service`, `X-Delegated-User-Id`, and `X-Trace-Id`.
- PostgreSQL target schema is delivered in `db/migration/wp2/V20260517_001__wp2_model_access_schema.sql`.

## Run Locally

```bash
WP2_SERVICE_TOKEN=local-model-access-token \
mvn -pl model-access spring-boot:run
```

Health:

```bash
curl http://localhost:8081/api/v1/model-access/health
```

Invoke the local echo provider:

```bash
curl -X POST http://localhost:8081/api/v1/model-access/invocations \
  -H 'Authorization: Bearer local-model-access-token' \
  -H 'X-Caller-Service: wp5-test-design' \
  -H 'X-Delegated-User-Id: user-001' \
  -H 'Content-Type: application/json' \
  -d '{
    "project_id": "project-001",
    "prompt_key": "test-case-design",
    "prompt_variables": {"context": "登录流程"},
    "messages": [{"role": "user", "content": "生成冒烟测试点"}],
    "allow_public_model": false,
    "sensitivity_level": "INTERNAL"
  }'
```

Run the HTTP smoke against an already-started WP2 service:

```bash
WP2_SERVICE_TOKEN=local-model-access-token bash scripts/wp2_model_access_smoke.sh
```

Run the strict WP1/WP2 integration smoke after starting WP1 on `8080` and WP2 on `8081` with `WP2_PLATFORM_CONTEXT_VALIDATION=strict` and `WP2_PLATFORM_AUDIT_ENABLED=true`:

```bash
WP1_SERVICE_TOKEN=local-platform-service-token \
WP1_AUTH_TOKEN_SECRET=local-auth-secret \
WP2_SERVICE_TOKEN=local-model-access-token \
bash scripts/wp2_strict_integration_smoke.sh
```

## Environment

| Variable | Default | Description |
|---|---|---|
| `WP2_SERVICE_TOKEN` | `local-model-access-token` | Service token required by WP2 APIs. |
| `WP2_DEFAULT_MODEL` | `local-echo` | Default model name written to invocation logs. |
| `WP2_PLATFORM_API_BASE_URL` | `http://localhost:8080` | WP1 base URL for strict context validation. |
| `WP2_PLATFORM_API_SERVICE_TOKEN` | empty | Token used when WP2 calls WP1. Set it to WP1 `WP1_SERVICE_TOKEN` for strict integration. |
| `WP2_PLATFORM_CONTEXT_VALIDATION` | `mock` | Use `strict` to require caller/delegated user headers. |
| `WP2_PLATFORM_AUDIT_ENABLED` | `false` | Write sanitized invocation audit events to WP1 `/audit/events` when WP1 service token is configured. |
| `WP2_MAX_PROMPT_CHARS` | `12000` | Prompt size guardrail. |
| `WP2_DAILY_PLATFORM_COST_LIMIT` | `0` | Platform daily cost limit. `0` disables the guardrail. |
| `WP2_DAILY_PROJECT_COST_LIMIT` | `0` | Project daily cost limit. `0` disables the guardrail. |
| `WP2_BUDGET_ESTIMATED_OUTPUT_TOKENS` | `0` | Output token reserve used by pre-call budget projection. |
| `WP2_BUDGET_ZONE_ID` | `Asia/Shanghai` | Time zone used for daily budget windows. |
| `WP2_MAX_EXPORT_ROWS` | `10000` | Maximum invocation rows returned by the CSV export endpoint. |
| `WP2_PROVIDER_MAX_RETRIES` | `1` | Retries per provider before falling back to the next candidate. |
| `WP2_PROVIDER_CIRCUIT_FAILURE_THRESHOLD` | `3` | Consecutive provider failures before temporarily opening the circuit. |
| `WP2_PROVIDER_CIRCUIT_OPEN_MS` | `60000` | Circuit-open duration in milliseconds. |
| `WP2_PROVIDER_CHECK_CACHE_TTL_MS` | `30000` | Short readiness-check cache TTL in milliseconds. |
| `WP2_COST_ALERT_WARNING_RATIO` | `0.8` | Daily budget usage ratio that marks cost alerts as `WARNING`. |

## Delivery Notes

The service uses an in-memory repository in `local` profile and a PostgreSQL/JdbcTemplate repository in `db` profile. The provider adapter boundary includes an OpenAI-compatible HTTP client; the built-in `LOCAL_ECHO` and `MOCK_FAILURE` adapters make routing, fallback, cost, masking, and audit behavior testable without an external model.

The `db` profile seeds `local-echo-primary` and the `test-case-design` active prompt through `db/migration/wp2/V20260517_002__wp2_default_seed_data.sql`, so a fresh PostgreSQL database can run the same smoke invocation as local mode.

When `WP2_PLATFORM_CONTEXT_VALIDATION=strict`, WP2 calls WP1 `/api/v1/contexts/projects/{projectId}` and `/api/v1/contexts/applications/{applicationId}` with the configured service token. WP2 consumes the returned `allow_public_model` and `sensitivity_level`, then applies the stricter policy between the request and WP1 context before selecting a provider. When `WP2_PLATFORM_AUDIT_ENABLED=true`, WP2 posts sanitized audit summaries to WP1 `/api/v1/audit/events`.

WP1 audit write failures do not block the model invocation. WP2 logs a warning and increments `veri.agent.model_access.platform.audit.events{result="failed"}` so operations can alert on audit delivery issues.

For OpenAI-compatible providers, set `api_key_ref` to `env:YOUR_API_KEY_VARIABLE`. WP2 reads the variable at call time and never stores or logs the plaintext key.

`POST /api/v1/model-access/providers/{id}/check` performs a short provider readiness check and returns `UP` or `DOWN` with latency, sanitized error details, and a `cached` flag. It does not write an invocation audit row. For OpenAI-compatible providers, this endpoint may make a real model API call; repeat checks are cached for `WP2_PROVIDER_CHECK_CACHE_TTL_MS`.

`GET /api/v1/model-access/invocations` returns a paged response and accepts `project_id`, `application_id`, `sensitivity_level`, `status`, `provider_id`, `actor_service`, `start_time`, `end_time`, `page`, and `size`. `GET /api/v1/model-access/invocations/summary` returns count, status split, token totals, and total cost using the same filters. `GET /api/v1/model-access/invocations/export` returns a CSV file with the same filters and only sanitized audit fields.

`GET /api/v1/model-access/cost/alerts` returns platform/project daily budget alert states when cost limits are configured. `GET /api/v1/model-access/cost/report` returns daily project/application cost rows for up to 31 days.

Invocation `sensitivity_level` supports `PUBLIC`, `INTERNAL`, `CONFIDENTIAL`, and `RESTRICTED`; omitted values default to `INTERNAL`. `CONFIDENTIAL` and `RESTRICTED` requests cannot enable public-model routing and cannot explicitly select a non-local provider. Policy violations return `MODEL_POLICY_VIOLATION` and write a `BLOCKED` audit row with the normalized sensitivity level.

For compatibility with WP1 seed data and legacy documents, WP2 maps WP1 `STRICT` to `RESTRICTED` internally.

Budget guardrails are evaluated before a provider call. When the projected daily spend would exceed the configured platform or project limit, WP2 returns `BUDGET_EXCEEDED` and writes a `BLOCKED` invocation audit row with masked request preview and zero actual cost.

Provider calls retry each candidate according to `WP2_PROVIDER_MAX_RETRIES`. Repeated provider failures temporarily open an in-memory circuit breaker, so WP2 skips that provider and falls back to the next eligible provider until the circuit window expires.

## Quality Gate

```bash
bash scripts/wp2_quality_gate.sh
```

Set `WP2_RUN_HTTP_SMOKE=1` when a WP2 service is already running. Set `WP2_RUN_STRICT_SMOKE=1` when WP1 and WP2 are both running in strict integration mode.

## Operations Metrics

WP2 publishes Micrometer metrics through `/actuator/metrics`:

- `veri.agent.model_access.invocations`: invocation rows by `status`, `sensitivity_level`, `provider_type`, `fallback_used`, and `error_code`.
- `veri.agent.model_access.invocation.latency`: invocation latency by `status` and `provider_type`.
- `veri.agent.model_access.provider.checks`: provider readiness checks by `provider_type`, `status`, and `error_code`.
- `veri.agent.model_access.provider.check.latency`: provider readiness check latency.
- `veri.agent.model_access.platform.audit.events`: WP1 audit write attempts by `result`.
- `veri.agent.model_access.tokens` and `veri.agent.model_access.cost`: token and cost summaries without prompt or secret labels.
