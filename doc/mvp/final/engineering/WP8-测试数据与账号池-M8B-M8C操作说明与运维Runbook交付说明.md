# WP8 测试数据与账号池 - M8B/M8C 操作说明与运维 Runbook 交付说明

| 项 | 内容 |
|---|---|
| 交付阶段 | M8B 前端操作说明、M8C 运维 Runbook |
| 交付日期 | 2026-06-15 |
| 交付范围 | WP8 浏览器操作说明、WP8 运维 Runbook、README/研发拆解/前端设计/测试策略/技术设计/PRD/启动准备状态更新 |
| 非目标 | 修改前端运行时代码、修改服务端接口、修改数据库结构、实现真实文件下载、启用真实 cleanup worker、接入 WP7/WP9 真实执行器 |
| 涉及模块 | WP8 文档、README |
| 回滚方式 | 回退本次文档 commit；既有 `platform-api`、`portal-web`、WP8 quality gate、Playwright smoke 和 DB validation 不受影响 |

## 1. 目标与范围

M8B/M8C 目标是补齐 WP8-8.2 操作说明和 WP8-8.3 Runbook，把已经落地的 `#test-data` 工作台能力和运维排障路径整理为可执行文档。交付后，用户无需 curl 即可按文档完成：

1. 进入 `测试数据` 并理解权限边界。
2. 创建、更新、归档数据集并导入记录摘要。
3. 生成数据集脱敏导出摘要。
4. 创建、更新、禁用、归档账号池并维护账号摘要。
5. 申请、续租、释放账号租借并生成租借脱敏导出摘要。
6. 创建和重试清理任务控制面记录。
7. 按 Runbook 处理租借卡死、账号锁定、SecretRef 轮换、清理失败和脱敏导出异常。

本切片只补用户操作说明、运维 Runbook 与交付记录，不改变前端 UI、API contract、数据库或清理执行语义。

## 2. 主要变更

1. 新增 `WP8-测试数据与账号池-前端操作说明.md`，覆盖入口权限、页面结构、数据集、导出、账号池、账号摘要、租借、释放、清理任务、状态解释和常见排障。
2. 新增 `WP8-测试数据与账号池-运维Runbook.md`，覆盖开关、日常验证、发布准出、租借卡死、账号锁定、SecretRef 轮换、清理失败、脱敏导出异常和回滚。
3. 更新 `README.md`，新增 WP8 前端操作说明、运维 Runbook 和 M8B/M8C 交付说明索引。
4. 更新 WP8 PRD、任务拆解、前端设计、测试策略、技术设计和正式启动准备，标记 WP8-8.2/WP8-8.3 已完成并说明不改变运行时契约。

## 3. 验收入口

```bash
rg -n "WP8-8.2|WP8-8.3|M8B|M8C|前端操作说明|运维 Runbook|运维Runbook" README.md doc/mvp/final/engineering/WP8-*
git diff --check
```

建议回归：

```bash
cd portal-web && npm test -- api/testData.test.ts permissions.test.ts
cd portal-web && npm run build
```

## 4. 风险与后续

1. 文档说明基于当前 `TestDataWorkbench` 能力；如果后续新增筛选栏、分页、详情抽屉、真实文件下载或 cleanup worker，需要同步更新操作说明和 Playwright smoke。
2. 运维 Runbook 当前按 `cleanup-enabled=false` 默认安全边界编写；真实清理 adapter 启用前必须补 allowlist、dry-run、幂等、最大影响范围和回滚专项准出。
3. 本说明不能替代服务端权限校验；前端按钮显隐只做体验优化，最终准入仍由后端权限、项目 scope、对象状态和全局开关控制。

## 5. 五角色结论

| 角色 | 结论 | 说明 |
|---|---|---|
| 资深项目经理 | 有条件通过 | 范围限定为文档交付，回滚简单；条件是 README 和 WP8 主文档状态同步完成，当前已完成。 |
| 资深产品经理 | 有条件通过 | 用户操作路径和 Runbook 场景覆盖产品目标；条件是主文档与索引同步完成，当前已完成。 |
| 资深服务端架构师 | 通过 | 不改服务端接口、数据库或状态机；Runbook 坚持 RBAC、project scope、secretRef 不回显、active lease 约束和开关回滚边界。 |
| 资深前端工程师 | 通过 | 操作说明与当前 `TestDataWorkbench` 的 tab、按钮、导出面板和权限控制一致，未承诺未实现 UI。 |
| 资深质量工程师 | 通过 | 文档引用扫描、diff 检查、前端定向 Vitest 和 build 已通过；无需追加验证。 |

五角色均无阻断项。

## 6. 验证结果

已执行：

```bash
rg -n "WP8-8.2|WP8-8.3|M8B|M8C|前端操作说明|运维 Runbook|运维Runbook" README.md doc/mvp/final/engineering/WP8-*
git diff --check
cd portal-web && npm test -- api/testData.test.ts permissions.test.ts
cd portal-web && npm run build
```

结果：

| 命令 | 结果 | 说明 |
|---|---|---|
| `rg -n "WP8-8.2|WP8-8.3|M8B|M8C|前端操作说明|运维 Runbook|运维Runbook" README.md doc/mvp/final/engineering/WP8-*` | 通过 | README、WP8 主文档、新增操作说明、Runbook 和交付说明均可检索。 |
| `git diff --check` | 通过 | 未发现 whitespace error。 |
| `cd portal-web && npm test -- api/testData.test.ts permissions.test.ts` | 通过 | 2 个测试文件、25 个测试通过，确认 WP8 API helper 和权限映射未回归。 |
| `cd portal-web && npm run build` | 通过 | 构建通过；保留既有 `electron_mirror`、auth 动静态导入和 chunk size 警告，非本任务新增阻断。 |

未执行项：

1. 未运行 `mvn -B -pl platform-api test`、DB validation、Java 行数门禁和完整 WP8 quality gate：本切片只改文档和 README，不修改 Java、API、DB、脚本或前端运行时代码；前端定向测试与 build 已作为回归验证。
2. 未启用真实 cleanup worker、未实现导出文件下载、未接入 WP7/WP9 真实执行器：均为本切片明确非目标。

Java 准入说明：

本轮未修改 Java 生产或测试代码；《阿里巴巴 Java 开发手册》自查和核心 JavaDoc 补充不适用于本次文档-only 变更。
