# WP3 测试资产管理 - PRD 与架构补充

## 1. 产品目标

WP3 负责承接 WP4 输入、WP5 用例生成、后续执行与报告链路所需的测试资产底座。MVP 目标不是一次性做完整 ALM，而是稳定形成“资产入库、状态治理、追踪关系、权限审计、前端可见”的最小闭环。

## 2. 资产类型

| 资产 | 说明 | 当前状态 |
|---|---|---|
| Requirement | 需求资产，支持手工创建和 WP4 导入 | P0 可用 |
| API | 接口资产，保存 path/method/schema | P0 可用，OpenAPI 导入为 P1 |
| Page | 页面资产，保留原型来源、原型版本、组件树和截图地址 | P0 可用，真实原型连接器为 P2 |
| BusinessFlow | 业务流程资产，保存结构化 flowJson | P0 可用 |
| TestCase | 测试用例，关联需求/API，维护步骤 | P0 可用 |
| TraceLink | 需求、API、用例之间的追踪关系 | P0 可用 |

## 3. 状态与评审

需求和用例状态：

```text
DRAFT -> REVIEWING -> APPROVED -> DEPRECATED
DRAFT -> APPROVED
REVIEWING -> DRAFT
REVIEWING -> DEPRECATED
```

`APPROVED` 不允许回到 `REVIEWING/DRAFT`，`DEPRECATED` 不允许恢复。非法状态流写 `STATUS_CHANGE_DENIED` 审计。

## 4. 编码与唯一性

当前由服务端按 UUID 生成短编码：

- Requirement: `REQ-xxxxxxxxxxxx`
- API: `API-xxxxxxxxxxxx`
- Page: `PAGE-xxxxxxxxxxxx`
- BusinessFlow: `FLOW-xxxxxxxxxxxx`
- TestCase: `TC-xxxxxxxxxxxx`

数据库通过 `project_id + code` 保证唯一。导入需求额外通过 `project_id + source + source_ref` 幂等。

## 5. 权限模型

| 权限 | 角色建议 |
|---|---|
| `asset:read` | SuperAdmin、PlatformAdmin、ProjectOwner、AppOwner、Tester、Developer、Auditor |
| `asset:manage` | SuperAdmin、PlatformAdmin、ProjectOwner、AppOwner、Tester |
| `asset:review` | SuperAdmin、PlatformAdmin、ProjectOwner、AppOwner、Tester |
| `asset:export` | SuperAdmin、PlatformAdmin、ProjectOwner、Auditor |

用户态 API 通过 WP1 Bearer token 进入，内部服务通过 `WP3_SERVICE_TOKEN` 进入。后续资源作用域过滤继续对齐 WP1 项目成员与角色绑定。

## 6. 审计模型

写操作、拒绝操作和 WP4 导入更新都必须写审计。审计 action/resourceType 以 `WP3-审计事件字典.md` 为准。

## 7. 版本与历史

需求和测试用例维护服务端版本号，创建版本为 `1`，每次人工编辑、WP4 DRAFT 需求幂等更新、测试用例步骤替换时递增。版本历史保存到 `asset_version_history`：

- `assetType` 仅覆盖 `REQUIREMENT` 和 `TEST_CASE`。
- `changeType` 覆盖 `CREATE/UPDATE/UPSERT/STEPS_UPDATE`。
- `changedFields` 和 `diff` 使用 API 字段名，便于前端直接展示。
- `snapshot` 保存白名单字段；测试用例快照必须包含 steps。
- `actor` 来自 service token 的 `callerService:delegatedUserId` 或登录用户。
- `traceId` 用于关联平台审计与请求链路。

历史表是 append-only 账本，不支持修改、删除或回滚；版本回滚和 diff 可视化归后续增强。

## 8. WP4 集成

WP4 发布候选到 WP3 时：

1. `dryRun=true` 不写资产，只返回 `CREATE/UPDATE/CONFLICT_REVIEW_REQUIRED` 计划。
2. 正式发布保留 `source=IMPORT`、`sourceRef`、`sourceUrl`、`acceptanceCriteria`。
3. 重复导入同一 `externalRequirementId` 更新既有 DRAFT 需求。
4. 既有需求非 DRAFT 且存在差异时阻断，等待人工评审。

## 9. 页面原型预留

页面资产当前只做连接器前置预留，不拉取外部原型系统：

- `source` 标识来源：`MANUAL/FIGMA/LANHU/AXURE`。
- `sourceRef` 保存外部页面、节点或原型标识。
- `sourceVersion` 保存外部原型版本、节点版本或导入批次版本。
- `componentTree` 保存标准化组件树 JSON，`screenshotUrl` 保存截图或预览图地址。

真实连接器后续必须写入同一组字段，并继续走 WP1 RBAC、项目上下文和审计链路。

## 10. 后续设计约束

- API 不回到 snake_case。
- 不引入独立 `asset-service`。
- 不直接读取 WP1 表绕过应用服务上下文。
- 不引入租户字段。
- 导出、删除、恢复、版本历史必须保留审计和追踪关系。
