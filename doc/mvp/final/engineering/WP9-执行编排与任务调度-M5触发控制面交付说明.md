# WP9 执行编排与任务调度 - M5 触发控制面交付说明

## 范围

- 新增 webhook/cron trigger 管理 API、safe config summary、secretRef 引用和 digest。
- 新增 webhook 外部入口，默认全局关闭，启用后校验 HMAC-SHA256 签名、时间窗和 `sourceEventId` 幂等。
- 新增 trigger event 持久化，记录 requestDigest、状态、runId、错误码、traceId，不保存 payload、签名值或 secret 明文。
- 新增 cron 元数据保存能力，包括 `cron/timezone/nextFireAt` 摘要；生产 cron scanner 和错过补偿不在本切片。

## 接口

- `POST /api/v1/execution/plans/{id}/triggers`
- `GET /api/v1/execution/plans/{id}/triggers`
- `GET /api/v1/execution/triggers/{id}`
- `PATCH /api/v1/execution/triggers/{id}`
- `POST /api/v1/execution/triggers/{id}/dry-run`
- `GET /api/v1/execution/triggers/{id}/events`
- `POST /api/v1/execution/webhooks/{id}`

## 验收要点

- Webhook/cron 全局开关默认关闭，启用 trigger 需要对应全局开关打开。
- Webhook trigger 启用必须配置 `secretRef`，签名串为 `timestamp.eventId.rawBody`。
- 同一 `triggerId + sourceEventId` 重复 webhook 不重复创建 run。
- 响应、event、run summary 不包含 secretRef 明文、secret 值、签名值或 webhook payload 原文。
