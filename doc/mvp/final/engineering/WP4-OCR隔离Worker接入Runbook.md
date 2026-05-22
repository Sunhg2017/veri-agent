# WP4 OCR 隔离 Worker 接入 Runbook

| 项目 | 内容 |
|---|---|
| 覆盖范围 | WP4 Word/PDF/OCR 二进制解析中的 OCR worker 隔离 |
| 适用环境 | dev/test/preprod/prod |
| 当前实现 | `platform-api` 支持本地命令 OCR 与 HTTP 隔离 worker 两种模式 |
| 准出清单 | 参考 `doc/mvp/final/engineering/WP1-WP4-统一发布准出清单.md` 中 WP4 binary 和 WP4 smoke 入口 |

## 1. 目标

WP4-B1 的生产口径是：OCR 的高 CPU、超时、进程崩溃和依赖库风险不拖垮 `platform-api`。生产建议将 OCR 放到独立容器或 worker 服务中运行，`platform-api` 只通过受限 HTTP 契约提交二进制内容并等待文本结果。

本 runbook 不要求当前仓库内实现 worker 容器镜像；worker 可由运维、OCR 供应商代理或独立服务提供。

## 2. 推荐配置

### 2.1 生产隔离模式

```bash
WP4_OCR_WORKER_MODE=HTTP_WORKER
WP4_OCR_WORKER_URL=https://ocr-worker.example.internal/api/v1/ocr
WP4_OCR_WORKER_TOKEN=***
WP4_OCR_LOCAL_COMMAND_FALLBACK_ENABLED=false
WP4_OCR_TIMEOUT_SECONDS=30
WP4_OCR_MAX_OUTPUT_CHARS=20000
WP4_OCR_MAX_CONCURRENT_PROCESSES=2
```

生产环境建议关闭本地 fallback。此时如果 worker URL 未配置、worker 超时、worker 返回非 2xx 或返回空文本，WP4 会返回稳定错误，不会在 `platform-api` 进程内执行 `WP4_OCR_COMMAND`。

### 2.2 本地或联调 fallback 模式

```bash
WP4_OCR_WORKER_MODE=HTTP_WORKER
WP4_OCR_WORKER_URL=
WP4_OCR_LOCAL_COMMAND_FALLBACK_ENABLED=true
WP4_OCR_COMMAND='/usr/local/bin/ocr-adapter {input}'
```

该模式仅用于 dev/test 或 worker 尚未就绪的联调环境。启用 fallback 后，worker 未配置或调用失败会回落到本地命令 OCR。

### 2.3 旧本地命令模式

```bash
WP4_OCR_WORKER_MODE=LOCAL_COMMAND
WP4_OCR_COMMAND='/usr/local/bin/ocr-adapter {input}'
```

该模式保持向后兼容，但不建议用于生产高负载或高风险文件输入。

## 3. HTTP Worker 契约

`platform-api` 以 `POST` 调用 `WP4_OCR_WORKER_URL`，请求头：

| Header | 说明 |
|---|---|
| `Content-Type: application/json` | 固定 JSON 请求 |
| `Accept: application/json` | 固定 JSON 响应 |
| `Authorization: Bearer <WP4_OCR_WORKER_TOKEN>` | 配置 token 时发送；健康接口只暴露 token 是否配置，不暴露 token 明文 |

请求体：

```json
{
  "contentBase64": "<binary base64>",
  "maxOutputChars": 20000,
  "timeoutSeconds": 30
}
```

成功响应可使用任一字段承载文本：

```json
{
  "text": "识别出的需求文本"
}
```

```json
{
  "data": {
    "text": "识别出的需求文本"
  }
}
```

非 2xx 响应、空文本、调用超时、URL 配置非法都会被转换为 WP4 可读错误。错误响应不得包含原始文件路径、token、完整内部 endpoint 或 OCR provider 密钥。

## 4. 健康与观测

`GET /api/v1/document-input/health` 增加以下脱敏字段：

| 字段 | 含义 |
|---|---|
| `ocrWorkerMode` | `LOCAL_COMMAND`、`HTTP_WORKER` 或兼容别名 `EXTERNAL_WORKER` |
| `ocrRemoteWorkerConfigured` | 是否配置远端 worker URL |
| `ocrWorkerTokenConfigured` | 是否配置 worker token |
| `ocrLocalCommandFallbackEnabled` | 是否允许回落到本地命令 |
| `ocrLocalCommandExecutionAllowed` | 当前配置下 `platform-api` 是否可能执行本地 OCR 命令 |

portal-web 的 WP4 接口状态面板展示上述状态，但不展示 URL 或 token。

## 5. 验证

本地最小验证：

```bash
mvn -B -pl platform-api -Dtest=DocumentContentExtractorTest,DocumentInputControllerTest test
```

WP4 二进制链路：

```bash
bash scripts/wp4_binary_document_smoke.sh
```

基础准出仍按统一清单执行：

```bash
mvn -B -pl platform-api test
cd portal-web && npm test
cd portal-web && npm run build
```

涉及真实 worker 的预发发布，需要额外用真实 `WP4_OCR_WORKER_URL` 验证 OCR 图片或扫描 PDF，确认 `/health` 中：

- `ocrWorkerMode=HTTP_WORKER`
- `ocrRemoteWorkerConfigured=true`
- `ocrLocalCommandFallbackEnabled=false`
- `ocrLocalCommandExecutionAllowed=false`

## 6. 回滚

如 HTTP worker 故障且业务允许临时恢复本地 OCR，可回滚配置：

```bash
WP4_OCR_WORKER_MODE=LOCAL_COMMAND
WP4_OCR_COMMAND='/usr/local/bin/ocr-adapter {input}'
```

如生产环境不允许本地 OCR，则保持 `WP4_OCR_LOCAL_COMMAND_FALLBACK_ENABLED=false`，临时提示用户上传可复制文本的 PDF/Word/Markdown，或暂停 OCR 类输入源。
