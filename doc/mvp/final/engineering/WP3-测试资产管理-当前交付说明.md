# WP3 测试资产管理 - 当前交付说明

| 项目 | 内容 |
|---|---|
| 工作包 | WP3 测试资产管理 |
| 当前口径 | 单个 `platform-api` Java 服务内的 asset 领域模块 |
| 依赖 | WP1 项目上下文、RBAC、审计；WP4 发布链路 |
| 日期 | 2026-05-20 |

## 1. 当前范围

WP3 当前提供测试资产的最小闭环：需求、API、页面、业务流、测试用例、步骤和追踪关系。API 统一位于 `/api/v1/asset`，响应通过平台 envelope 返回，列表响应使用 `items/index/size/total`。

当前实现支持：

1. 需求资产：创建、详情、编辑、列表分页与 `projectId/status/source/keyword` 筛选。
2. API 资产：创建、详情、编辑、列表分页与基础筛选。
3. 页面资产：创建、详情、编辑、列表分页，保留 `sourceRef/sourceVersion/componentTree/screenshotUrl` 作为原型输入预留。
4. 业务流资产：创建、详情、编辑、列表分页。
5. 测试用例：创建、详情、编辑、步骤替换、列表分页。
6. 追踪链接：需求到 API/用例的链接创建与查询。
7. 版本历史：需求和测试用例返回 `version`，写入、编辑、WP4 幂等更新和用例步骤替换会保存版本快照、字段 diff、操作者和 traceId；portal-web 需求/测试用例详情可查看历史版本、变更字段、diff、snapshot 和链路 traceId。
8. 资产生命周期：需求、API、页面、业务流和测试用例支持独立 `lifecycleStatus=ACTIVE/ARCHIVED/DELETED`，列表默认仅返回 ACTIVE，可按生命周期筛选；归档保留唯一性，软删除释放现有唯一性约束，恢复前校验冲突。
9. 追踪矩阵：前端基于需求、API、测试用例和追踪链接做只读聚合，展示覆盖状态、缺口和一跳影响范围。
10. WP4 发布：`IMPORT + sourceRef` 需求幂等写入，重复导入在 DRAFT 状态下更新，非 DRAFT 差异阻断。
11. 导入/导出：需求、API、测试用例支持同步 CSV/JSON 导入导出；API 资产支持 OpenAPI 导入导出，OpenAPI 导入解析 path、method、summary、description、request/response schema 和 `info.version`，重复导入按 `projectId + path + httpMethod` 幂等复用或更新。导入提供 `dryRun`、逐行 action/status/errors，导出文件只包含业务字段，不包含 traceId、snapshot 或历史记录。

## 2. 非范围

本轮不实现历史版本回滚、API/Page/BusinessFlow 的完整版本历史、正式后端聚合影响分析服务、企业原型连接器真实拉取、可视化业务流画布和测试执行结果闭环。这些能力保留在后续 P1/P2 任务中。

## 3. 权限

资产 API 同时支持内部 service token 和登录用户 Bearer token：

| 权限 | 用途 |
|---|---|
| `asset:read` | 读取资产列表、详情、步骤和追踪链接 |
| `asset:manage` | 创建、编辑资产，维护步骤、追踪链接和导入资产 |
| `asset:review` | 前端状态流操作入口 |
| `asset:export` | 导出资产 |

内部服务调用使用 `WP3_SERVICE_TOKEN`，并通过 `X-Caller-Service`、`X-Delegated-User-Id` 保留调用来源。登录用户调用由 WP1 RBAC 决定是否可访问；项目上下文停用、未授权或审计写失败时，资产服务会拒绝操作并由契约测试固定“不产生脏资产”的行为。

## 4. API 入口

| 能力 | 路径 |
|---|---|
| 健康检查 | `GET /api/v1/asset/health` |
| 需求 | `GET/POST /api/v1/asset/requirements`，`GET/PUT /api/v1/asset/requirements/{id}`，`GET/PATCH /api/v1/asset/requirements/{id}/lifecycle`，`GET /api/v1/asset/requirements/{id}/versions` |
| 导入/导出 | `POST /api/v1/asset/imports`，`GET /api/v1/asset/exports` |
| API 资产 | `GET/POST /api/v1/asset/apis`，`GET/PUT /api/v1/asset/apis/{id}`，`GET/PATCH /api/v1/asset/apis/{id}/lifecycle` |
| 页面 | `GET/POST /api/v1/asset/pages`，`GET/PUT /api/v1/asset/pages/{id}`，`GET/PATCH /api/v1/asset/pages/{id}/lifecycle` |
| 业务流 | `GET/POST /api/v1/asset/business-flows`，`GET/PUT /api/v1/asset/business-flows/{id}`，`GET/PATCH /api/v1/asset/business-flows/{id}/lifecycle` |
| 测试用例 | `GET/POST /api/v1/asset/test-cases`，`GET/PUT /api/v1/asset/test-cases/{id}`，`GET/PATCH /api/v1/asset/test-cases/{id}/lifecycle`，`GET /api/v1/asset/test-cases/{id}/versions` |
| 用例步骤 | `GET/PUT /api/v1/asset/test-cases/{id}/steps` |
| 追踪链接 | `GET/POST /api/v1/asset/links` |

## 5. 数据模型

PostgreSQL 表位于 `db/migration/wp1/V20260518_014__wp3_asset_base_schema.sql`，生命周期扩展位于 `db/migration/wp1/V20260521_022__wp3_asset_lifecycle.sql`，页面原型来源版本预留位于 `db/migration/wp1/V20260521_023__wp3_page_prototype_source_version.sql`：

- `asset_requirement`
- `asset_api`
- `asset_page`
- `asset_business_flow`
- `asset_test_case`
- `asset_test_step`
- `asset_link`
- `asset_version_history`，由 `V20260520_017__wp3_asset_version_history.sql` 新增，记录需求/测试用例的 append-only 版本历史、字段 diff、快照、操作者和 traceId。

核心唯一性：

- `asset_requirement(project_id, code)`
- `asset_requirement(project_id, source, source_ref)`，仅 `IMPORT` 且 `source_ref` 非空
- `asset_api(project_id, service_name, path, http_method)`
- `asset_page(project_id, code)`
- `asset_business_flow(project_id, code)`
- `asset_test_case(project_id, code)`
- `asset_link(source_type, source_id, target_type, target_id, link_type)`
- `asset_version_history(asset_type, asset_id, version)`

版本口径：

- `asset_requirement.version` 和 `asset_test_case.version` 从 `1` 开始，服务端在写操作时递增。
- 需求创建、人工编辑、WP4 导入幂等更新会保存历史；无差异导入不生成新版本。
- 测试用例创建、用例编辑、步骤替换会保存历史；测试用例历史快照包含 steps。
- 历史表由 DB trigger 阻止 `UPDATE/DELETE`，运行时应用角色只应具备 `SELECT/INSERT`。

生命周期口径：

- 五类资产均新增 `lifecycle_status`、`archived_at`，并复用既有 `deleted_at` 代表软删除。
- `ACTIVE -> ARCHIVED/DELETED`、`ARCHIVED -> ACTIVE/DELETED`、`DELETED -> ACTIVE` 为允许转换，其他转换返回稳定错误并写拒绝审计。
- 默认列表过滤 `ACTIVE`；调用方可使用 `lifecycleStatus` 查询 `ARCHIVED` 或 `DELETED`。
- 普通详情接口不返回 `DELETED` 资产；生命周期详情接口可回看已归档/已删除资产。
- `DELETED` 释放现有 partial unique index；恢复前校验项目内 code/sourceRef/path 等唯一性冲突，冲突返回 `CONFLICT`。
- 需求和测试用例的归档、软删除、恢复会写入 `asset_version_history`，trace link 不随资产软删除被清理。

页面原型输入口径：

- 页面资产 `source` 支持 `MANUAL/FIGMA/LANHU/AXURE`，`sourceRef` 保存外部页面、节点或原型标识。
- `sourceVersion` 保存外部原型版本、节点版本或导入批次版本，避免与页面资产自身历史版本混用。
- `componentTree` 保存标准化组件树 JSON，`screenshotUrl` 保存截图或预览图地址；当前不实现外部连接器拉取和自动同步。

导入/导出口径：

- `POST /api/v1/asset/imports` 请求字段为 `assetType`、`format`、`projectId`、`dryRun`、`content`；`assetType` 支持 `REQUIREMENT/API/TEST_CASE`，`format` 支持 `CSV/JSON/OPENAPI`，其中 `OPENAPI` 仅支持 API 资产。
- CSV 第一行为字段名；JSON 支持数组或 `{ "items": [...] }`；OpenAPI 解析 `paths` 下的 HTTP method、path、summary、description、request/response schema，并将 `info.version` 映射到 API `version` 字段。
- dryRun 不写资产，只返回逐行 `PLANNED/FAILED`、`CREATE/UPDATE/LINK_EXISTING/CONFLICT_REVIEW_REQUIRED` 和错误明细。
- 需求导入使用 `source=IMPORT + sourceRef` 做幂等：无差异复用，DRAFT 有差异更新并生成历史，非 DRAFT 有差异返回冲突。
- API OpenAPI 导入使用 `projectId + path + httpMethod` 做幂等：无差异返回 `LINK_EXISTING`，有差异更新既有 API 的 summary、description、schema、version、source/sourceRef 和 status，不重复创建同一接口。
- 导出按列表筛选口径输出当前页上限 100 条；CSV/JSON/OpenAPI 导出不包含 traceId、snapshot、历史记录和审计上下文。

## 6. 状态流

需求和测试用例使用 `DRAFT/REVIEWING/APPROVED/DEPRECATED`。`APPROVED` 只能保持或进入 `DEPRECATED`，`DEPRECATED` 不允许恢复。API、页面和业务流使用各自状态集合，非法转换返回稳定错误并写拒绝审计。

## 7. 前端入口

`portal-web` 已增加资产库入口、需求资产工作台、API 资产工作台、页面资产工作台、业务流资产工作台和测试用例工作台：

- 按 `asset:read` 展示资产库导航。
- 支持需求列表、本地筛选、详情、创建、编辑和状态流入口。
- 展示 WP4 发布的 `source/sourceRef/sourceUrl/acceptanceCriteria`。
- 支持 API 列表、详情、创建、编辑、`method/path/status/source/keyword` 筛选、`version` 展示/编辑和 request/response schema 展示。
- API 页保留 OpenAPI 导入入口；后端已有 OpenAPI schema/version/idempotent 导入导出接口，前端导入工作流和 schema diff 仍归后续前端增强。
- 支持页面资产列表、详情、创建、编辑、`projectId/status/source/keyword` 筛选、`sourceRef/sourceVersion/screenshotUrl` 展示和 `componentTree` JSON 预览/编辑校验。
- 支持业务流资产列表、详情、创建、编辑、`projectId/status/keyword` 筛选、状态流入口和 `flowJson` JSON 预览/编辑校验。
- 支持测试用例列表、详情、创建、编辑、`projectId/status/source/keyword` 筛选、关联需求/API 展示与跳转、步骤新增/删除/上移/下移和整体保存。
- 支持追踪矩阵只读页面：按 `projectId`、需求/API/用例状态、覆盖状态和关键词筛选，展示需求维度覆盖矩阵、缺 API/用例缺口、孤立 API/用例和需求/API/用例的一跳影响范围。
- 追踪矩阵当前复用现有列表与 `/api/v1/asset/links` 读契约；多跳影响、页面/业务流纳入矩阵、补链编辑和后端聚合接口仍归后续增强。

## 8. 验证

本地准出：

```bash
WP3_SKIP_DB_VALIDATION=1 bash scripts/wp3_quality_gate.sh
```

包含数据库校验：

```bash
bash scripts/wp3_quality_gate.sh
```

针对已启动服务的 HTTP smoke：

```bash
WP3_SMOKE_BASE_URL=http://127.0.0.1:8080 \
WP3_SERVICE_TOKEN=local-asset-token \
bash scripts/wp3_asset_smoke.sh
```

全量后端与前端验证：

```bash
mvn -pl platform-api test
cd portal-web && npm run test && npm run build
```

PR/主干 CI 可通过 `.github/workflows/wp3-asset-management.yml` 复用同一 WP3 quality gate。

## 9. 后续入口

后续优先补齐历史版本回滚、前端导入导出工作流、后端聚合影响分析服务、页面/业务流追踪关系和真实原型连接器同步。
