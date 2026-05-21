# WP3 审计事件字典

| action | resourceType | 触发场景 | result |
|---|---|---|---|
| `CREATE` | `REQUIREMENT` | 创建需求资产 | `SUCCEEDED` |
| `UPDATE` | `REQUIREMENT` | 编辑需求资产 | `SUCCEEDED` |
| `UPSERT` | `REQUIREMENT` | WP4 导入幂等更新 DRAFT 需求 | `SUCCEEDED` |
| `UPSERT_DENIED` | `REQUIREMENT` | WP4 导入试图更新非 DRAFT 且有差异的需求 | `DENIED` |
| `STATUS_CHANGE_DENIED` | `REQUIREMENT` | 非法需求状态转换 | `DENIED` |
| `ARCHIVE` | `REQUIREMENT` | 归档需求资产 | `SUCCEEDED` |
| `SOFT_DELETE` | `REQUIREMENT` | 软删除需求资产 | `SUCCEEDED` |
| `RESTORE` | `REQUIREMENT` | 恢复已归档或已软删除需求资产 | `SUCCEEDED` |
| `LIFECYCLE_CHANGE_DENIED` | `REQUIREMENT` | 非法需求生命周期转换 | `DENIED` |
| `CREATE` | `API` | 创建 API 资产 | `SUCCEEDED` |
| `UPDATE` | `API` | 编辑 API 资产 | `SUCCEEDED` |
| `STATUS_CHANGE_DENIED` | `API` | 非法 API 状态转换 | `DENIED` |
| `ARCHIVE` | `API` | 归档 API 资产 | `SUCCEEDED` |
| `SOFT_DELETE` | `API` | 软删除 API 资产 | `SUCCEEDED` |
| `RESTORE` | `API` | 恢复已归档或已软删除 API 资产 | `SUCCEEDED` |
| `LIFECYCLE_CHANGE_DENIED` | `API` | 非法 API 生命周期转换 | `DENIED` |
| `CREATE` | `PAGE` | 创建页面资产 | `SUCCEEDED` |
| `UPDATE` | `PAGE` | 编辑页面资产 | `SUCCEEDED` |
| `STATUS_CHANGE_DENIED` | `PAGE` | 非法页面状态转换 | `DENIED` |
| `ARCHIVE` | `PAGE` | 归档页面资产 | `SUCCEEDED` |
| `SOFT_DELETE` | `PAGE` | 软删除页面资产 | `SUCCEEDED` |
| `RESTORE` | `PAGE` | 恢复已归档或已软删除页面资产 | `SUCCEEDED` |
| `LIFECYCLE_CHANGE_DENIED` | `PAGE` | 非法页面生命周期转换 | `DENIED` |
| `CREATE` | `BUSINESS_FLOW` | 创建业务流资产 | `SUCCEEDED` |
| `UPDATE` | `BUSINESS_FLOW` | 编辑业务流资产 | `SUCCEEDED` |
| `STATUS_CHANGE_DENIED` | `BUSINESS_FLOW` | 非法业务流状态转换 | `DENIED` |
| `ARCHIVE` | `BUSINESS_FLOW` | 归档业务流资产 | `SUCCEEDED` |
| `SOFT_DELETE` | `BUSINESS_FLOW` | 软删除业务流资产 | `SUCCEEDED` |
| `RESTORE` | `BUSINESS_FLOW` | 恢复已归档或已软删除业务流资产 | `SUCCEEDED` |
| `LIFECYCLE_CHANGE_DENIED` | `BUSINESS_FLOW` | 非法业务流生命周期转换 | `DENIED` |
| `CREATE` | `TEST_CASE` | 创建测试用例 | `SUCCEEDED` |
| `UPDATE` | `TEST_CASE` | 编辑测试用例 | `SUCCEEDED` |
| `UPDATE` | `TEST_CASE_STEPS` | 替换测试步骤 | `SUCCEEDED` |
| `STATUS_CHANGE_DENIED` | `TEST_CASE` | 非法用例状态转换 | `DENIED` |
| `ARCHIVE` | `TEST_CASE` | 归档测试用例 | `SUCCEEDED` |
| `SOFT_DELETE` | `TEST_CASE` | 软删除测试用例 | `SUCCEEDED` |
| `RESTORE` | `TEST_CASE` | 恢复已归档或已软删除测试用例 | `SUCCEEDED` |
| `LIFECYCLE_CHANGE_DENIED` | `TEST_CASE` | 非法用例生命周期转换 | `DENIED` |
| `CREATE` | `TRACE_LINK` | 创建需求到 API/用例追踪关系 | `SUCCEEDED` |

## 审计要求

1. 写操作的 `scopeId` 使用项目 ID。
2. service token 调用通过 `X-Caller-Service` 和 `X-Delegated-User-Id` 保留服务与委托用户。
3. 用户态调用由 WP1 Bearer token 解析操作者。
4. 拒绝类事件不得产生脏资产。
5. 需求和测试用例的成功写操作同时生成 `asset_version_history` 版本记录；历史查询本轮不单独写读审计。
6. 生命周期类操作不得清理 trace link；需求和测试用例的归档、软删除和恢复同时生成 `asset_version_history`。
7. 后续导入和导出能力接入时必须沿用本字典扩展。
