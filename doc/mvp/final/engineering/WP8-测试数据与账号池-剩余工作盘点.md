# WP8 测试数据与账号池 - 剩余工作盘点

| 项目 | 内容 |
|---|---|
| 工作包 | WP8 测试数据与账号池 |
| 文档性质 | 当前范围剩余项审计、后续专项边界 |
| 日期 | 2026-06-24 |

## 1. 结论

截至 WP8 文件下载、cleanup adapter 和账号自动开通切片收口，WP8 当前承诺范围内没有剩余 P0 功能开发项。剩余工作只剩发布前按目标环境执行 release gate、填写发布记录，以及后续增强另行立项。

`WP8-8.2 操作说明` 已由 `WP8-测试数据与账号池-前端操作说明.md` 补齐；`WP8-8.3 Runbook` 已由 `WP8-测试数据与账号池-运维Runbook.md` 补齐；`WP8-8.4 发布准出说明` 已由 `WP8-测试数据与账号池-发布准出说明.md` 补齐。

## 2. 当前范围已完成项

| 领域 | 完成证据 |
|---|---|
| 数据集 | 数据集 CRUD、记录摘要导入、schema/cleanup policy 摘要、数据集脱敏导出摘要、文件下载和 OpenAPI contract。 |
| 账号池 | 账号池 CRUD、禁用/归档、账号摘要新增/更新、`secretRefDigest`、健康摘要、自动开通 adapter 和不回显 secretRef。 |
| 租借 | 申请、续租、释放、requestKey 幂等、TTL 限制、active lease 并发唯一、过期回收服务方法和租借导出摘要。 |
| 清理任务 | 准备/刷新/清理/回滚任务记录、查询、重试、失败摘要、`cleanup-enabled=false` 安全边界和 HTTP cleanup adapter。 |
| 跨 WP 引用 | WP9 lease adapter、WP7 runner contract、WP10 report evidence 的应用层脱敏契约。 |
| Frontend | `#test-data` 工作台覆盖策略、数据集、账号池、租借、清理任务、数据集导出、租借导出、导出文件下载和 390px smoke。 |
| Quality gate | WP8 quality gate、front-end Playwright smoke、managed lease concurrency smoke、DB validation 和 Java 行数门禁入口。 |
| 文档交付 | PRD、技术设计、前端设计、测试策略、研发拆解、前端操作说明、运维 Runbook、发布准出说明、剩余工作盘点和 M8I 发布准出收口交付说明。 |

## 3. 后续专项

| 后续项 | 当前判断 | 不阻断原因 |
|---|---|---|
| 真实文件下载 | 已完成 | WP8 数据集/租借均提供 `/export/download` JSON attachment，沿用脱敏摘要 schema 和 `testData:export/export-enabled`。 |
| 真实 cleanup worker / 破坏性 adapter | 已完成受控实现 | 当前默认 `cleanup-enabled=false`；显式开启且 HTTP adapter ready 后才执行真实清理，失败和摘要均可审计。 |
| WP7/WP9/WP10 对 WP8 契约的真实消费 | 已由对应 WP 接入 | WP8 继续拥有账号池、租借状态机和脱敏契约；WP7 凭据注入、WP9 自动申请/释放和 WP10 报告证据消费均已通过应用层契约接入，不再构成 WP8 剩余项。 |
| 真实业务账号自动开通 | 已完成配置化实现 | 当前默认 `account-provisioning-enabled=false`；启用后按 `leasePolicy.provisioning`、adapter ready、`minAvailable/maxAccounts` 和 batch size 补齐账号。 |
| 更细粒度筛选、分页和详情抽屉 | 后续前端体验增强 | 当前主链路和 smoke 已覆盖 P0 操作。 |
| 外部 HTTP 并发压测、真实业务系统演练和容量指标 | 后续运维容量专项 | 当前 release gate 使用 managed 并发 smoke 和 adapter 契约测试证明一致性边界。 |
| 多实例 worker 运维演练 | 后续运维专项 | 当前 worker 通过方法同步与 repository 条件更新避免重复处理；多实例故障切换和锁指标需目标环境演练。 |

## 4. 发布前必做

发布目标环境前建议执行：

```bash
bash scripts/wp8_quality_gate.sh
WP8_GATE_MODE=release WP8_LEASE_CONCURRENCY_SMOKE=managed bash scripts/wp8_quality_gate.sh
git diff --check
```

如果发布只包含文档变更，可按变更影响面执行：

```bash
rg -n "WP8-8.4|M8I|发布准出说明|剩余工作盘点|当前 WP8 范围无剩余 P0 功能开发项" README.md doc/mvp/final/engineering/WP8-* doc/mvp/final/engineering/当前实现基线.md
git diff --check
```

涉及 Java、API、DB、权限、导出、安全或前端运行时变更时，不得只按文档门禁准出，必须追加对应的 Maven、Vitest、build、DB validation、Playwright smoke 和 WP8 quality gate。
