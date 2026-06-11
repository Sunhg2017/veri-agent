# WP6 OpenAPI 接口自动化 - 需求文档与 PRD

| 项目 | 内容 |
|---|---|
| 工作包 | WP6 OpenAPI 接口自动化 |
| 角色产出 | 资深产品经理 |
| 文档性质 | 需求文档、产品边界和验收标准 |
| 当前口径 | 面向接口自动化资产生产和受控试运行；不建设 UI/E2E、执行调度和完整诊断报告 |
| 版本 | v0.1 |
| 日期 | 2026-06-11 |

## 1. 背景

WP1-WP5 已完成平台底座、模型接入、测试资产、需求输入和 AI 用例生成评审。WP6 承接“已确认测试用例和 API 资产”到“可执行接口自动化脚本”的生产链路，重点解决接口自动化规模化起步时的规格导入、用例生成、断言生成、脚本审查、单次试运行和结果回写。

## 2. 用户价值

| 用户 | 价值 |
|---|---|
| 测试工程师 | 从 OpenAPI 和已确认用例快速生成接口自动化草稿，减少手写 Pytest 脚本。 |
| 项目负责人 | 看到接口自动化覆盖范围、生成状态、运行摘要和失败风险。 |
| 开发工程师 | 通过 API diff 和试运行结果定位接口契约变更影响。 |
| 审计/质量负责人 | 追踪谁导入规格、谁生成脚本、谁触发运行、结果是否泄露敏感信息。 |

## 3. 产品目标

1. 支持导入 OpenAPI 3.x 规格并形成可审查的接口清单。
2. 将 OpenAPI endpoint 与 WP3 API 资产对齐，支持新增、更新和跳过 preview。
3. 基于 WP5 已确认用例、API schema 和策略生成接口自动化用例草稿。
4. 产出 Pytest 脚本包和静态校验摘要，允许人工审查后进入试运行。
5. 受控触发单次接口自动化运行，采集结果摘要和脱敏错误。
6. 全链路保留项目 scope、traceId、审计事件和回滚路径。

## 4. 范围

| 功能 | P0 口径 | 验收关注点 |
|---|---|---|
| OpenAPI 源管理 | 上传文件、URL 拉取、文本粘贴三种来源；保存源类型、digest、版本和解析状态 | 不保存密钥；重复 digest 可复用 |
| 规格解析 | 解析 service、path、method、operationId、tags、参数、请求体、响应码和 schema 摘要 | 非 OpenAPI 3.x 或超大文件阻断 |
| API diff | 展示 OpenAPI endpoint 与 WP3 `asset_api` 的新增、变更、匹配和跳过 | 不直接静默写 WP3 |
| API sync | 用户确认后通过 WP3 应用服务创建/更新 API 资产 | 写审计，失败可定位 |
| 用例生成 | 按 API、标签、已确认 WP5 用例和覆盖策略生成接口自动化用例草稿 | 生成来源、输入 digest、Prompt 版本可追踪 |
| 脚本包 | 生成 Pytest 文件树、依赖清单、环境变量引用和静态校验摘要 | 不暴露 secretRef 明文 |
| 人工审查 | 脚本包支持 `DRAFT/REVIEWING/APPROVED/REJECTED/ARCHIVED` 状态 | 未审批不允许进入 release gate |
| 试运行 | 支持手动触发一次运行，选择环境、baseUrl、secretRef 和用例范围 | 超时、并发、目标地址受控 |
| 结果采集 | 聚合 pass/fail/skip/error、耗时、断言摘要、脱敏错误和产物引用 | 不保存完整请求响应正文 |

## 5. 非目标

| 非目标 | 说明 |
|---|---|
| UI 自动化 | 不生成 Playwright 脚本，不采集截图/视频。 |
| 定时调度 | 不做计划、DAG、cron、CI webhook 和 worker 池。 |
| 完整报告 | 不做 Allure 风格多维报告、AI 失败诊断和缺陷草稿。 |
| 账号池和数据工厂 | 不创建测试账号、测试数据和清理任务。 |
| 破坏性接口探索 | 不自动调用未明确选择的写接口，不做 fuzzing、压测或越权攻击。 |

## 6. 核心流程

```mermaid
flowchart LR
    A["导入 OpenAPI"] --> B["解析与安全校验"]
    B --> C["API diff 预览"]
    C --> D["同步 WP3 API 资产"]
    D --> E["生成接口自动化用例"]
    E --> F["生成 Pytest 脚本包"]
    F --> G["人工审查"]
    G --> H["受控试运行"]
    H --> I["结果摘要与审计"]
```

## 7. 权限点

| 权限点 | 用途 | 默认角色建议 |
|---|---|---|
| `apiAutomation:read` | 查看 OpenAPI 源、生成任务、脚本包和运行结果 | SuperAdmin、PlatformAdmin、ProjectOwner、AppOwner、Tester、Developer、Auditor |
| `apiAutomation:import` | 导入规格、执行 API diff 和 sync | SuperAdmin、PlatformAdmin、ProjectOwner、AppOwner、Tester |
| `apiAutomation:generate` | 生成接口用例和脚本包 | SuperAdmin、PlatformAdmin、ProjectOwner、AppOwner、Tester |
| `apiAutomation:review` | 审查、驳回、归档脚本包 | SuperAdmin、PlatformAdmin、ProjectOwner、AppOwner、Tester |
| `apiAutomation:execute` | 触发受控试运行 | SuperAdmin、PlatformAdmin、ProjectOwner、AppOwner、Tester |
| `apiAutomation:export` | 导出脚本包摘要、运行摘要和审计聚合 | SuperAdmin、PlatformAdmin、ProjectOwner、Auditor |

## 8. 状态机

| 对象 | 状态 |
|---|---|
| OpenAPI 源 | `UPLOADED`、`PARSING`、`PARSED`、`PARSE_FAILED`、`ARCHIVED` |
| API sync 任务 | `DRAFT`、`APPLYING`、`APPLIED`、`FAILED` |
| 生成任务 | `QUEUED`、`RUNNING`、`SUCCEEDED`、`FAILED`、`CANCELLED` |
| 自动化用例 | `DRAFT`、`READY`、`DISABLED`、`ARCHIVED` |
| 脚本包 | `DRAFT`、`REVIEWING`、`APPROVED`、`REJECTED`、`ARCHIVED` |
| 运行任务 | `QUEUED`、`RUNNING`、`SUCCEEDED`、`FAILED`、`TIMEOUT`、`CANCELLED` |

## 9. 页面入口

WP6 在 portal-web 新增“接口自动化”一级入口或测试资产下的二级入口。没有 `apiAutomation:read` 权限时不显示入口；直接访问仍由后端鉴权。

## 10. 验收标准

1. 用户可以完成 OpenAPI 导入、解析、diff、确认同步 WP3 API 资产。
2. 用户可以选择 API 范围和 WP5 已确认用例，生成接口自动化用例和脚本包。
3. 试运行结果能区分通过、断言失败、执行错误、超时和跳过。
4. 全链路响应包含 traceId，失败有稳定错误码和脱敏提示。
5. 导出和页面不包含 secretRef 明文、环境变量值、完整请求响应正文或 provider 原始错误。
6. 权限、项目 scope、审计、DB validation、前端测试、构建和 smoke 纳入准出。
