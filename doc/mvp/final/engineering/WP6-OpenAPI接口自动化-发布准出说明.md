# WP6 OpenAPI 接口自动化 - 发布准出说明

| 项目 | 内容 |
|---|---|
| 工作包 | WP6 OpenAPI 接口自动化 |
| 文档性质 | 发布准出、验证记录、风险和回滚说明 |
| 当前分支 | `codex/wp6-runner-smoke` |
| 远端 | `origin/codex/wp6-runner-smoke` |
| 日期 | 2026-06-13 |

## 1. 准出结论

WP6 P0/P1 控制面已经形成可验收闭环：OpenAPI 规格可导入、解析、脱敏、归档、diff、sync-preview、sync，可基于 WP3 API 和 WP5/WP3 测试用例摘要生成接口自动化用例草稿，可生成 Pytest/httpx 脚本包摘要并完成静态校验、提交评审、审批、驳回，可创建受控运行任务、取消可取消状态任务、采集并导出脱敏运行结果。

当前准出口径不包含 WP9 调度、真实后台 worker 池、进程级强杀、分布式任务回收、WP10 Allure 风格完整报告和 AI 失败诊断。

## 2. 范围和非目标

本次准出范围：

1. WP6 后端控制面、DB schema、权限、审计、OpenAPI parser、diff/sync、生成、脚本包评审、runner port、Managed HTTP adapter、Pytest subprocess adapter、run/cancel/export。
2. WP6 前端工作台、权限入口、导入、diff 筛选、同步、生成范围选择、生成历史详情、脚本包评审、运行、取消和脱敏导出。
3. WP6 专项脚本、fixture smoke、runner smoke、DB validation 和 release gate。
4. WP6 技术设计、测试策略、Runner Runbook、前端操作说明和研发拆解状态台账。

非目标：

1. 不建设定时计划、DAG、CI webhook、worker 池和分布式调度；后续由 WP9 承接。
2. 不提供完整报告门户、Allure 聚合和 AI 失败诊断；后续由 WP10 承接。
3. 不持久化生成源码、stdout/stderr 原文、完整请求/响应正文、baseUrl 明文、secretRef 明文或 secret 值。

## 3. 验证记录

最近一次完整准出验证已在 `codex/wp6-runner-smoke` 执行通过：

```bash
mvn -B -pl platform-api -Dtest=ApiAutomationServiceTest,ApiAutomationControllerTest test
cd portal-web && npm test -- apiAutomation.test.ts
cd portal-web && npm test
cd portal-web && npm run build
mvn -B -pl platform-api test
WP6_GATE_MODE=release WP6_RUNNER_SMOKE=managed bash scripts/wp6_quality_gate.sh
git diff --check
```

结果：

1. 后端专项测试通过，覆盖 WP6 service/controller 状态机、归档、diff/sync、生成、脚本包、runner 和权限路径。
2. 前端专项测试和全量 Vitest 通过。
3. 前端构建通过；仅保留既有 Vite dynamic import/chunk size 警告。
4. `platform-api` 全量 Maven 测试通过。
5. WP6 release gate 通过，包含 OpenAPI fixture smoke、后端/OpenAPI 契约测试、前端 WP6 测试、Playwright desktop/mobile smoke、前端 build、DB validation 和 managed runner smoke。
6. `git diff --check` 通过。

## 4. 跳过项和原因

未执行真实外部业务目标 runner smoke。原因是 WP6 P0/P1 准出使用 managed loopback 和受控 adapter smoke 验证 runner 契约、安全边界、allowlist、timeout、secretRef digest、取消控制面和脱敏导出；外部目标必须先经过 baseUrl、allowlist、测试数据和破坏性写入风险评审，再使用 `WP6_RUNNER_SMOKE=external` 执行。

未执行 WP9/WP10 验证。原因是调度编排和完整报告诊断不属于 WP6 当前范围。

## 5. 发布风险

| 风险 | 准出控制 | 处置 |
|---|---|---|
| runner 误访问未授权地址 | baseUrl 标准化、私网/metadata/.local 阻断、allowlist 必须命中 | 关闭 `runner-enabled`，清空 allowlist，重跑 runner smoke |
| 运行结果或导出泄露敏感信息 | 只落 host/digest、错误码、断言摘要；禁止请求/响应正文、stdout/stderr 和 secret 明文 | 阻断发布，修复脱敏后重跑 release gate |
| 模型输出不稳定 | WP2 调用受策略控制，输出 schema 校验失败时按配置 fallback | 关闭模型生成或切回 `FALLBACK_ONLY` |
| OpenAPI 样本过大或不规范 | spec 大小、endpoint 数量、OpenAPI 3.x 和 parser fixture 门禁 | 拒绝导入或归档问题 spec，不影响已同步 WP3 资产 |
| 真实 Pytest 环境缺依赖 | Pytest subprocess 必须显式启用，环境需预装 Python、pytest、httpx | 保持 Managed HTTP 或 Disabled adapter，待环境补齐后重跑 smoke |

## 6. 回滚方式

1. 功能回滚优先回滚到上一已验证提交；当前分支远端为 `origin/codex/wp6-runner-smoke`。
2. runner 风险优先通过配置回滚：设置 `veri-agent.api-automation.runner-enabled=false`，清空或收紧 `runner-allowed-base-url-patterns`。
3. 数据库结构已经纳入统一迁移和 validation；生产环境不建议破坏性回滚，按前滚修复补充新迁移。
4. 已产生的 spec、case、bundle、run 和 result 记录保留审计摘要；如外部产物发现敏感内容，按安全流程删除外部产物并保留 digest 证据。
5. release gate 回退到 `WP6_RUNNER_SMOKE=managed`，真实目标修复和重新评审后再恢复 external smoke。

## 7. 五角色准出结论

| 角色 | 结论 | 说明 |
|---|---|---|
| 资深项目经理 | 通过 | WP6 P0/P1 范围、里程碑、风险、回滚和后续 WP9/WP10 边界清晰。 |
| 资深产品经理 | 通过 | 浏览器主链路已覆盖导入、diff、sync、生成、评审、运行、取消和导出。 |
| 资深服务端架构师 | 通过 | 服务端接口、状态机、权限、审计、runner port、adapter 和安全边界已纳入测试。 |
| 资深前端工程师 | 通过 | 前端工作台、权限按钮、响应式和 Playwright smoke 已覆盖。 |
| 资深质量工程师 | 通过 | release gate、DB validation、fixture smoke、runner smoke 和构建测试均有准出入口。 |

