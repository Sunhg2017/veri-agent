# WP9 执行编排与任务调度 - 发布准出说明

| 项目 | 内容 |
|---|---|
| 工作包 | WP9 执行编排与任务调度 |
| 文档性质 | 发布准出、验证记录、风险、回滚和剩余边界说明 |
| 当前分支 | `codex/wp9-planning` |
| 远端 | `origin/codex/wp9-planning` |
| 日期 | 2026-06-15 |

## 1. 准出结论

WP9 当前承诺范围已经形成可验收闭环。`platform-api` 已提供执行计划、DAG dryRun、运行状态机、内部队列认领、heartbeat/recovery、WP6 API_TEST dispatch、WP7 UI_TEST dispatch、WP8 账号租借自动申请/释放、REPORT_HANDOFF、webhook/cron 触发控制面、生产 CRON scanner、run export、scheduler loop 和 release gate；`portal-web` 已提供 `#execution` 工作台，覆盖计划、DAG、运行、取消、重试、导出和触发配置主链路。

当前准出口径不包含 WP8 账号池自身能力扩展、真实供应商 OAuth/App 上架、独立外部 worker 二进制或 CRON 生产压测容量承诺。这些均已作为后续专项记录，不构成本轮 WP9 发布阻断。

## 2. 范围和非目标

本次准出范围：

1. WP9 后端控制面：health、plan CRUD、DAG validator、dryRun、manual run、run detail/list、cancel、retry、run export、trigger 管理、webhook 签名、CRON scanner、scheduler loop、queue claim、heartbeat/recovery、WP6 dispatch 和 REPORT_HANDOFF。
2. WP9 前端工作台：入口权限、调度策略、计划列表、DAG 编辑、DAG dryRun、手动运行、运行详情、取消、重试、导出摘要、触发配置、trigger dryRun、启停和事件查看。
3. WP9 准出脚本：quality gate、scheduler smoke、webhook HTTP smoke、frontend Playwright smoke、marketplace package smoke、worker hosting readiness、report handoff smoke、CRON capacity smoke 和 CRON backlog smoke。
4. WP9 交付文档：PRD、技术设计、前端设计、前端操作说明、测试策略、研发拆解、Scheduler/Trigger Runbook、供应商 webhook 样例、marketplace 包、worker 托管策略、WP10 handoff、CRON capacity/backlog 和本发布准出说明。

非目标：

1. 不在 WP9 内自建浏览器执行器；`UI_TEST` 通过 WP7 应用服务 dispatch，浏览器执行能力本身仍由 WP7 独立演进和准出。
2. 不实现真实 cleanup worker、破坏性清理 adapter 或账号池自动开通；WP8 账号租借应用层契约已由 WP9 调度接入。
3. 不生成 WP10 侧报告正文、诊断页面或报告归档；WP9 只提供 handoff 摘要和脱敏 run export，完整报告消费与展示由 WP10 控制面承接。
4. 不申请真实供应商 OAuth/App 上架，不实现安装授权和卸载回收。
5. 不承诺生产吞吐数值，不做 CRON 历史 backfill；已通过 capacity/backlog smoke 冻结当前限批和不补偿语义。
6. 不新增独立 worker 进程或分布式锁；当前生产可用同一 `platform-api` 镜像按 web/scheduler-active/scheduler-standby env 角色部署。

## 3. 验证记录

M8I 收口后已执行并通过的验证组合：

```bash
rg -n "M8I|发布准出说明|剩余工作盘点|当前 WP9 范围无剩余功能开发项" README.md doc/mvp/final/engineering/WP9-* doc/mvp/final/engineering/当前实现基线.md
WP9_QUALITY_GATE_PLAN_ONLY=1 bash scripts/wp9_quality_gate.sh
git diff --check
cd portal-web && npm test
cd portal-web && npm run build
mvn -B -pl platform-api test
```

结果：

1. 文档引用检查通过，M8I、发布准出说明、剩余工作盘点和“当前 WP9 范围无剩余功能开发项”已能被 README、研发拆解、技术设计、测试策略和实现基线索引。
2. `WP9_QUALITY_GATE_PLAN_ONLY=1 bash scripts/wp9_quality_gate.sh` 通过，已列出 WP9 quality gate 的脚本语法、marketplace、worker hosting、CRON、report handoff、后端、前端、Playwright、build 和 DB validation 计划。
3. `git diff --check` 通过。
4. `portal-web` Vitest 通过，24 个测试文件、186 个测试。
5. `portal-web` build 通过；仅保留既有 Vite dynamic import/chunk size warning。
6. `platform-api` 全量 Maven 测试通过，541 个测试，无失败、无错误。

本轮收口文档补齐后，准出前建议执行：

```bash
bash scripts/wp9_quality_gate.sh
WP9_GATE_MODE=release WP9_SCHEDULER_SMOKE=managed WP9_WEBHOOK_HTTP_SMOKE=managed bash scripts/wp9_quality_gate.sh
git diff --check
```

release/preprod/prod 模式必须显式启用 managed scheduler smoke 和 webhook HTTP smoke；如果使用 `WP9_WEBHOOK_HTTP_SMOKE=external`，需要先评审目标服务、base URL、密钥、测试数据和破坏性写入风险。

## 4. 跳过项和原因

未执行真实供应商 OAuth/App 安装。原因是当前 WP9 只交付 signed webhook 模板包和 CI 签名样例，真实上架、授权、卸载和审核材料属于后续供应商平台专项。

未执行真实 CRON 生产压测。原因是当前 WP9 准出只冻结 missed-fire 不补偿、单次 materialize、tick batch 限批和后续 tick 接续处理的控制面语义；吞吐指标、容量看板和 backfill 限额需要独立压测环境。

未执行跨 WP 联合发布级端到端回归。原因是 WP9 已提供 `REPORT_HANDOFF` 完成摘要和脱敏 run export，WP10 对完整报告的生成、诊断和归档由其自身工作包 release gate 覆盖。

未执行独立 worker 进程、多活 leader election 或分布式锁验证。原因是当前托管策略通过同一 `platform-api` 镜像和 env 角色区分 web、scheduler-active、scheduler-standby；多活调度和锁租约是后续平台化增强。

## 5. 发布风险

| 风险 | 准出控制 | 处置 |
|---|---|---|
| scheduler 误在 web 实例启用 | `wp9_worker_hosting_readiness.sh` 校验 web/scheduler-active/scheduler-standby env | 关闭误启实例的 `WP9_SCHEDULER_ENABLED` 和 `WP9_CRON_ENABLED`，保留 queue claim 证据 |
| webhook 误触发或重复触发 | HMAC、timestamp、sourceEventId 幂等、trigger event 证据和 webhook HTTP smoke | 禁用 trigger，撤销或轮换 secret，复用 eventId 重放核验证据 |
| CRON 积压造成突发 run | `schedulerTickBatchSize` 限批、capacity/backlog smoke、Runbook 容量保护 | 下调 trigger 频率、扩容 scheduler worker 或暂停高风险 trigger |
| run export 泄露敏感内容 | run export 只复用脱敏 detail，quality gate 覆盖 export 证据 | 阻断发布，修复 redaction 后重跑 WP9 quality gate |
| WP6 安全边界被绕过 | API_TEST 只通过 WP6 应用服务，不直连 runner adapter | 关闭 scheduler/webhook/cron，回退到 WP6 手动 run |
| WP10 误认为报告已生成 | 文档和 handoff 摘要明确 `rawReportStored=false`，不生成报告正文 | WP10 端补完整消费契约后再做端到端准出 |

## 6. 回滚方式

1. 功能开关回滚优先：关闭 `WP9_SCHEDULER_ENABLED`、`WP9_WEBHOOK_ENABLED`、`WP9_CRON_ENABLED`，或将对应 trigger 置为 `DISABLED/PAUSED`。
2. 调度托管回滚：将 scheduler-active 实例切回 standby 或停止，web 实例保持 scheduler/cron 关闭。
3. webhook 风险回滚：撤销 WP1 SecretProvider 中的 webhook signing secret，更新 CI secret store，保留 trigger event 和审计证据。
4. CRON 风险回滚：暂停高风险 trigger，不直接删除 run、trigger event 或 queue claim；如需历史修复，单独提交 backfill 方案。
5. 数据库迁移遵循前滚修复优先；生产环境不做破坏性 drop。
6. 代码回滚优先回滚到上一已验证提交；当前远端分支为 `origin/codex/wp9-planning`。

## 7. 五角色准出结论

| 角色 | 结论 | 说明 |
|---|---|---|
| 资深项目经理 | 通过 | 当前 WP9 范围、非目标、风险、验证和回滚已收口；后续专项不阻断本轮准出。 |
| 资深产品经理 | 通过 | 浏览器主链路和外部触发说明已覆盖，WP10 完整报告、供应商 OAuth/App 和生产容量承诺边界清晰。 |
| 资深服务端架构师 | 通过 | 服务端控制面、调度、触发、WP6 dispatch、report handoff、脱敏导出和安全边界已由测试和脚本覆盖。 |
| 资深前端工程师 | 通过 | `#execution` 工作台、权限、响应式、导出和触发配置主链路已有 Vitest/Playwright 证据和操作说明。 |
| 资深质量工程师 | 通过 | WP9 quality gate、release gate 要求、DB validation、smoke 脚本和最近全量测试记录已具备准出证据。 |
