# WP1-WP4 API 版本化策略

| 项目 | 内容 |
|---|---|
| 覆盖范围 | `platform-api` 中 `/api/v1/**` HTTP API，包括 WP1 平台控制面、WP2 模型接入、WP3 资产管理和 WP4 文档输入 |
| 当前版本 | `v1` |
| 版本载体 | URL path prefix：`/api/v1` |
| 适用阶段 | 本地开发、CI、预发和生产发布 |

## 1. 版本原则

1. 当前所有公开和内部 HTTP API 统一使用 `/api/v1` 前缀，不在同一接口族中混用 query/header 版本。
2. `v1` 内只允许兼容性变更；破坏性变更必须新建 `/api/v2` 或更高版本路径，并保留并行迁移窗口。
3. OpenAPI 契约是版本冻结依据，`/v3/api-docs` 中必须包含全局 `x-api-version-policy` 和每个 `/api/v1` operation 的版本生命周期扩展字段。
4. Controller 层必须标注 `@ApiVersion`，默认生命周期为 `STABLE`；仅供服务间调用、测试样例或平台内部治理的接口标注为 `INTERNAL`。
5. 已废弃接口使用 `DEPRECATED` 生命周期，必须同时给出 sunset 时间和 replacement 路径；未完成迁移前不得删除旧路径。

## 2. 兼容性规则

兼容性变更可以继续留在 `v1`：

- 新增可选请求字段，默认行为与旧请求一致。
- 新增响应字段，旧字段语义保持不变。
- 新增接口路径或新增非必填查询条件。
- 新增枚举值，但调用方有默认 fallback，且 OpenAPI 契约测试覆盖。
- 错误响应继续使用统一 envelope，新增错误码不改变既有错误码语义。

破坏性变更必须进入新版本：

- 删除或重命名路径、字段、枚举值、权限点或错误码。
- 改变字段类型、必填性、默认分页语义或排序语义。
- 改变鉴权方式、资源作用域边界或服务 token 调用契约。
- 改变同步/异步语义、幂等键规则或导入导出文件格式。
- 改变 webhook 签名、时间戳窗口、事件版本或重放语义。

## 3. 生命周期字段

`@ApiVersion` 会写入 OpenAPI operation 扩展字段：

| 字段 | 含义 |
|---|---|
| `x-api-version` | 当前 API path 版本，例如 `v1` |
| `x-api-lifecycle` | `STABLE`、`INTERNAL` 或 `DEPRECATED` |
| `x-api-version-since` | 当前接口进入该版本的起始年月 |
| `x-api-sunset` | 废弃接口下线时间，仅 `DEPRECATED` 必填 |
| `x-api-replacement` | 废弃接口替代路径，仅 `DEPRECATED` 必填 |

全局 `info.x-api-version-policy` 固定当前版本、path prefix、兼容变更口径和生命周期枚举，供前端、测试和外部集成生成工具读取。

## 4. 变更流程

1. 需求评审时确认是否兼容 `v1`，无法兼容时创建新版本路径。
2. Controller 或 operation 标注 `@ApiVersion`，内部接口显式标注 `INTERNAL`。
3. 更新 OpenAPI 契约测试，覆盖新增路径、字段、权限点、错误码和版本扩展字段。
4. 影响 WP1-WP4 共享 envelope、分页、权限、审计、DB 或前端工作流时，按 `WP1-WP4-统一发布准出清单.md` 扩大验证范围。
5. 发布说明必须记录是否兼容、是否新增版本、是否存在 deprecated/sunset 接口和回滚方式。

## 5. 验收入口

```bash
mvn -B -pl platform-api -Dtest=OpenApiContractTest,ModelAccessOpenApiContractTest,AssetOpenApiContractTest,DocumentInputOpenApiContractTest test
```

涉及跨 WP 行为或全局配置时继续执行：

```bash
bash scripts/wp1_quality_gate.sh
```
