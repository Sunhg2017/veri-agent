# Platform API 代码审查报告 · 第三轮 · 代码坏味道

> 审查日期: 2026-05-24
> 审查人员: Claude (Cowork mode)
> 审查范围: `platform-api` 模块全部 Java 源代码
> 聚焦: 代码坏味道（Code Smell），非功能性问题

---

## 说明

前两轮审查集中于**功能性问题**（安全、架构、性能、测试覆盖等），40+ 项中已完成 36 项。本轮从**代码洁癖**角度出发，关注代码结构、可读性、可维护性相关的坏味道——即使功能正确，长期也会侵蚀代码库健康度。

---

## 1. 🔴 God Object — 三个超过 700 行的类仍未拆分

A1 重构后整体类规模大幅下降，但以下三个类仍然是明显的"上帝对象"：

### 1.1 AssetImportExportService（987 行）

**文件**: `asset/application/AssetImportExportService.java`

**症状**:
- 混合了导入编排、CSV 编解码、JSON 编解码、OpenAPI 编解码、格式矩阵校验五种职责
- 内部定义了 `appendCsvValue()`、`appendCsvLine()`、`castMap()` 等工具方法
- 包含大量静态常量集合（`PRIORITIES`、`API_STATUSES`、`API_HTTP_METHODS` 等）

**建议**:
1. CSV 编解码 → 独立 `AssetCsvCodec` 组件，或直接用 Apache Commons CSV
2. OpenAPI 编解码 → 独立 `AssetOpenApiCodec` 组件
3. 工具方法（`castMap`、`appendCsvValue`）→ 公共 util 类

### 1.2 DocumentInputService（940 行）

**文件**: `documentinput/application/DocumentInputService.java`

**症状**:
- 虽然源管理、候选项、发布等已拆分出去，但核心的 webhook 接收、幂等校验、签名验证、重放控制、ingress guard 协调仍留在此类
- `health()` 方法（第 126 行）超过 50 行，从 `DocumentContentExtractor` 拉取 20+ 个配置值拼装响应

**建议**:
1. webhook 生命周期处理 → 独立 `DocumentWebhookProcessor` 或 `DocumentWebhookIngressService`
2. health 检查 -> 独立 `DocumentInputHealthAssembler`（仅做响应装配）

### 1.3 DocumentContentExtractor（790 行）

**文件**: `documentinput/application/DocumentContentExtractor.java`

**症状**:
- **功能侵占**：混合了 PDF 解析、Word 解析、OCR 本地命令调用、OCR 远端 HTTP 调用、恶意软件扫描、MIME 类型校验六种完全不同的技术职责
- 同时暴露 **30+ 个配置 getter**（`ocrTimeoutSeconds()`、`malwareScanEnabled()`、`pdfMaxPages()` 等），使其看起来更像一个配置中心而非内容提取器
- 配置 getter 占了类的一半行数，这些值其实可以直接从 `DocumentInputProperties` 读取，不需要封装一层

**建议**:
1. PDF 解析 → `PdfContentExtractor`
2. Word 解析 → `WordContentExtractor`
3. OCR 调用 → `OcrClient`（本地/远端两个实现通过策略模式切换）
4. 恶意软件扫描 → `MalwareScanner`
5. 配置 getter → 调用方直接读取 `DocumentInputProperties`，删除该层封装

---

## 2. 🔴 长参数列表 — saveRecord 的 19 个参数

**文件**: `modelaccess/application/ModelProviderInvocationService.java`（第 317-336 行）

```java
private InvocationRecord saveRecord(
    ModelInvocationCommand request,    // 1
    ServicePrincipal principal,        // 2
    PromptTemplate prompt,             // 3
    ModelProviderConfig provider,      // 4
    InvocationStatus status,           // 5
    boolean fallbackUsed,              // 6
    String promptDigest,               // 7
    String requestPreview,             // 8
    String responsePreview,            // 9
    int inputTokens,                   // 10
    int outputTokens,                  // 11
    BigDecimal totalCost,              // 12
    String errorCode,                  // 13
    String errorMessage,               // 14
    String routingRuleName,            // 15
    String modelCapability,            // 16
    String sensitivityLevel,           // 17
    Instant startedAt                  // 18, 19
)
```

**连带问题**: `recordBlocked()`、`handleBudgetViolation()`、`invokeProvider()` 都在反复传递同一组参数。`ModelInvocationExecutionPlan` 已承载执行上下文，但 `prompt`、`routingRuleName`、`modelCapability`、`sensitivityLevel`、`startedAt` 这些 plan 中已有的字段仍然逐个被提取为参数传递。

**建议**:
1. 将 plan 对象整体传入 `saveRecord()`，内部从中提取所需字段
2. 或定义一个 `InvocationRecordDraft` Builder 对象，避免长参数列表和重复构造

---

## 3. 🟠 重复代码 — CSV 工具方法两处独立存在

**文件对比**:
- `asset/application/AssetImportExportService.java`（第 839 行）: `appendCsvValue()`
- `modelaccess/application/ModelAccessService.java`（第 1194 行）: `appendCsvValue()`

**症状**: 两处实现了完全相同的 CSV 值转义逻辑——逗号转义、引号包裹、换行处理。属于 Copy-Paste 编程。

**建议**: 抽取到公共工具类（如 `common/util/CsvWriter`），或直接引入 `Apache Commons CSV` 依赖替代手写逻辑。

---

## 4. 🟠 Shotgun Surgery — ResponseEnvelopeAdvice 硬编码路径

**文件**: `common/web/ResponseEnvelopeAdvice.java`（第 36 行）

```java
if (body instanceof byte[] || request.getURI().getPath().startsWith("/v3/api-docs")) {
    return body;
}
```

**症状**: OpenAPI 文档路径 `/v3/api-docs` 硬编码在 ResponseBodyAdvice 中。若日后升级 SpringDoc 版本导致路径前缀变化，或自定义了文档路径，此处的逻辑必须同步修改。容易遗漏。

**建议**: 改为从配置读取，或通过 `@ConditionalOnExpression` / `@ConditionalOnProperty` 控制：

```java
private static final String API_DOCS_PATH = "/v3/api-docs";  // 或配置注入
```

---

## 5. 🟡 Primitive Obsession — 字符串类型标识符泛滥

**全项目范围**

**症状**: `String projectId`、`String applicationId`、`String environmentId`、`String scopeType`、`String status`、`String lifecycleStatus` 等字符串类型标识符遍布几乎所有 application service 的接口签名和领域对象字段。编译器无法帮助区分参数顺序错误。

**案例**:

```java
// AssetRequirementService.java — 多个 String 参数易混淆
public RequirementResponse updateRequirement(
    String projectId, String requirementId, String status, String priority, ...
)
```

**建议**: Q6 专项任务中已标记，优先为高频使用的标识符创建 Value Object：
- `ProjectId`、`ApplicationId`、`EnvironmentId`、`UserId`

---

## 6. 🟡 InMemory 服务线程安全策略粗糙

**文件**: `management/infrastructure/InMemoryManagementUserService.java`、`InMemoryManagementSecretReferenceService.java` 等

**症状**: InMemory 实现使用 `synchronized` 逐方法加锁，但内部数据结构是 `ArrayList`/`HashMap` 等非线程安全集合。虽然 local profile 下并发要求不高，但存在以下问题：
1. `synchronized` 锁在 `this` 实例上，粒度粗糙
2. 复合操作（如先查后改）需要外部同步
3. 与 Postgres 版的事务隔离行为不一致

**建议**: 将内部数据结构改为 `ConcurrentHashMap` + `CopyOnWriteArrayList`，或使用 `ReadWriteLock` 精细控制。对 InMemory 实现，容忍一定的数据不一致但避免死锁风险。

---

## 7. 🟡 手工校验代替 Jakarta Validation

**文件**: `asset/application/AssetImportExportService.java`

**症状**: 9 次调用自定义 `valueIn()` 方法校验字段合法性：

```java
valueIn(request.priority(), "MEDIUM", PRIORITIES, "priority")
valueIn(request.source(), "IMPORT", REQUIREMENT_SOURCES, "source")
```

**问题**: Jakarta Validation (`@NotBlank`、`@Pattern`、`@Size`) 已经在项目中使用，同一类中引入 `valueIn()` 增加了一种新的校验方式，造成不一致。

**建议**: 在 Request DTO 上使用 Jakarta Validation 注解约束替代手写校验，删除 `valueIn()` 方法。

---

## 8. 🟡 残余中文错误消息（20+ 处）

**症状**: Q2 专项虽未正式启动，但大部分中文消息已在迭代中被清理。剩余集中在：

| 文件 | 约计数 |
|------|--------|
| `DocumentInputService.java` | 15 处 |
| `DocumentModelRequirementParser.java` | 5 处 |

以 webhook 处理流程为主，如 `"webhook 事件不存在: "`、`"webhook 原始 payload 不可重放"`、`"模型响应不是有效 JSON"`。

**建议**: 作为 Q2 的起点，优先把这 20 处 webhook 相关消息抽取到 properties，覆盖范围小、改动风险低。

---

## 9. 🟡 ModelAccessService 中残留 appendCsvValue

**文件**: `modelaccess/application/ModelAccessService.java`

**症状**: 第 1194 行的 `appendCsvValue()` 是 CSV 流式导出改造（P4）后残留的代码。当前 CSV 导出已改为 `StreamingResponseBody` + `writeInvocationsCsv()` 分页写出，但 `appendCsvValue()` 方法未被删除。

**建议**: 确认无调用方后删除此残留方法。

---

## 10. ⚪ 值得注意但影响有限的观察

### 10.1 @RequirePermissions 容器注解未被使用
- `RequirePermissions.java` 已定义，但多权限场景直接用 `@Repeatable` 堆叠 `@RequirePermission` 也能工作
- 不一定是问题，但多了一种未被使用的类型会增加认知负担

### 10.2 测试覆盖率不均衡
- 518 个 main 文件，70 个 test 文件（约 7:1）
- 部分拆分服务的测试靠 Controller 集成测试间接覆盖，缺少 Service 层单元测试

---

## 汇总

| 级别 | 数量 | 编号 |
|------|------|------|
| 🔴 严重 | 3 | God Object × 3、长参数列表 |
| 🟠 中等 | 2 | 重复代码、Shotgun Surgery |
| 🟡 一般 | 5 | Primitive Obsession、线程安全、手工校验、中文消息、残留代码 |
| ⚪ 轻微 | 1 | 未使用的注解 |

**优先修复建议（按投入产出比）**：

1. **P0** — `ModelProviderInvocationService.saveRecord()` 收窄参数：用 `ModelInvocationExecutionPlan` 替代 6 个散参数，改动量小、提升方法签名可读性最明显
2. **P1** — `DocumentContentExtractor` 配置 getter 剥离：30 个配置查询方法抽走，剩下 400 行纯提取逻辑
3. **P2** — CSV 工具去重 + 残留代码删除：10 分钟改动量，消除两处重复
4. **P3** — `DocumentInputService` 继续拆分：webhook 处理流程独立，砍掉 300 行
