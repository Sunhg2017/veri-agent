# WP4 Webhook 签名样例与联调说明

| 项目 | 内容 |
|---|---|
| 覆盖任务 | WP4-A4 Webhook 签名样例和联调包 |
| 当前路径 | `POST /api/v1/document-input/webhooks/{sourceCode}` |
| 签名算法 | HMAC-SHA256，hex 小写 |
| 日期 | 2026-05-20 |

## 1. 必填 Header

| Header | 示例 | 说明 |
|---|---|---|
| `X-VA-Timestamp` | `1760000000` | Unix epoch seconds；默认允许偏移由 `WP4_WEBHOOK_CLOCK_SKEW_SECONDS` 控制，当前默认 300 秒 |
| `X-VA-Event-Id` | `evt-20260520-0001` | 外部事件唯一 ID |
| `X-VA-Idempotency-Key` | `req-1001-v3` | 业务幂等键；同 key 不得对应不同 payload |
| `X-VA-Event-Version` | `1.0` | 必须与 source 配置的 `eventVersion` 匹配 |
| `X-VA-Signature` | `d4c3...` | HMAC-SHA256 hex 小写 |

签名串固定为：

```text
timestamp.eventId.idempotencyKey.rawBody
```

其中 `rawBody` 必须是 HTTP 请求实际发送的原始 JSON 字符串；签名前后不能重新格式化、排序字段、追加换行或改变空格。

## 2. cURL + openssl 样例

```bash
BASE_URL='http://127.0.0.1:8080'
SOURCE_CODE='wp4-smoke'
SECRET='local-document-input-webhook-secret'
TIMESTAMP="$(date +%s)"
EVENT_ID="evt-$(date +%s)"
IDEMPOTENCY_KEY="req-1001-v1"
PAYLOAD='{"projectId":"project-001","eventType":"requirement.created","eventVersion":"1.0","id":"REQ-1001","requirements":[{"title":"Webhook signed requirement","priority":"HIGH"}]}'
SIGNATURE="$(printf '%s' "$TIMESTAMP.$EVENT_ID.$IDEMPOTENCY_KEY.$PAYLOAD" | openssl dgst -sha256 -hmac "$SECRET" -hex | awk '{print $NF}')"

curl -X POST "$BASE_URL/api/v1/document-input/webhooks/$SOURCE_CODE" \
  -H 'Content-Type: application/json' \
  -H "X-VA-Timestamp: $TIMESTAMP" \
  -H "X-VA-Event-Id: $EVENT_ID" \
  -H "X-VA-Idempotency-Key: $IDEMPOTENCY_KEY" \
  -H 'X-VA-Event-Version: 1.0' \
  -H "X-VA-Signature: $SIGNATURE" \
  --data-binary "$PAYLOAD"
```

`--data-binary` 可以减少客户端改写 body 的概率；如果改用 `-d @file.json`，签名也必须用同一个文件的原始内容。

## 3. Node.js 样例

```javascript
import crypto from "node:crypto";

const secret = process.env.WP4_WEBHOOK_SECRET;
const timestamp = Math.floor(Date.now() / 1000).toString();
const eventId = `evt-${Date.now()}`;
const idempotencyKey = "req-1001-v1";
const rawBody = JSON.stringify({
  projectId: "project-001",
  eventType: "requirement.created",
  eventVersion: "1.0",
  id: "REQ-1001",
  requirements: [
    { title: "Webhook signed requirement", priority: "HIGH" }
  ]
});

const canonical = `${timestamp}.${eventId}.${idempotencyKey}.${rawBody}`;
const signature = crypto
  .createHmac("sha256", secret)
  .update(canonical, "utf8")
  .digest("hex");

await fetch("http://127.0.0.1:8080/api/v1/document-input/webhooks/wp4-smoke", {
  method: "POST",
  headers: {
    "Content-Type": "application/json",
    "X-VA-Timestamp": timestamp,
    "X-VA-Event-Id": eventId,
    "X-VA-Idempotency-Key": idempotencyKey,
    "X-VA-Event-Version": "1.0",
    "X-VA-Signature": signature
  },
  body: rawBody
});
```

## 4. Java 样例

```java
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

class VeriAgentWebhookSample {
    static String hmacSha256Hex(String secret, String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }

    public static void main(String[] args) throws Exception {
        String secret = System.getenv("WP4_WEBHOOK_SECRET");
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String eventId = "evt-" + timestamp;
        String idempotencyKey = "req-1001-v1";
        String rawBody = "{\"projectId\":\"project-001\",\"eventType\":\"requirement.created\",\"eventVersion\":\"1.0\",\"id\":\"REQ-1001\",\"requirements\":[{\"title\":\"Webhook signed requirement\",\"priority\":\"HIGH\"}]}";
        String signature = hmacSha256Hex(secret, timestamp + "." + eventId + "." + idempotencyKey + "." + rawBody);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:8080/api/v1/document-input/webhooks/wp4-smoke"))
                .header("Content-Type", "application/json")
                .header("X-VA-Timestamp", timestamp)
                .header("X-VA-Event-Id", eventId)
                .header("X-VA-Idempotency-Key", idempotencyKey)
                .header("X-VA-Event-Version", "1.0")
                .header("X-VA-Signature", signature)
                .POST(HttpRequest.BodyPublishers.ofString(rawBody, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());
        System.out.println(response.statusCode());
        System.out.println(response.body());
    }
}
```

## 5. 联调前置条件

1. 已创建 `CUSTOM_API` source，`sourceCode` 与请求路径一致。
2. source 状态为 ENABLED，`eventVersion` 与 `X-VA-Event-Version` 一致。
3. source 配置了 `secretRef`，且 WP1 SecretProvider 能解析 ACTIVE、未过期、用途为 `WEBHOOK_SIGNING`、作用域为 `CONFIG + document_input_source.id` 的密钥。
4. dev/test 可使用显式 `WP4_WEBHOOK_SECRET`、`wp4-webhook-default` 或自定义 `veri-agent.document-input.webhook-secrets` fallback；生产建议设置 `WP4_LOCAL_WEBHOOK_SECRET_FALLBACK_ENABLED=false`。
5. SecretProvider 成功解析结果会按 `WP4_WEBHOOK_SECRET_CACHE_TTL_SECONDS` 短 TTL 缓存，配置/default fallback 不缓存；轮换时旧密钥至少保留 `max(WP4_WEBHOOK_SECRET_CACHE_TTL_SECONDS, WP4_WEBHOOK_SECRET_ROTATION_OVERLAP_SECONDS)`。
6. payload 大小不超过 `WP4_WEBHOOK_MAX_PAYLOAD_BYTES`。

## 6. 排错表

| 现象 | 常见原因 | 处理 |
|---|---|---|
| `webhook 缺少 X-VA-*` | Header 未传或代理丢失 | 确认外部系统和网关转发所有 `X-VA-*` Header |
| 签名 `INVALID` | raw body 不一致、secret 错误、hex 大小写或 canonical string 拼错 | 固定 rawBody 字节；使用 `timestamp.eventId.idempotencyKey.rawBody`；签名输出小写 hex |
| 签名 `EXPIRED` | timestamp 超过时间窗口或机器时钟漂移 | 校准 NTP；确认 `WP4_WEBHOOK_CLOCK_SKEW_SECONDS` |
| `webhook payload 超过上限` | 单次事件体超过 `WP4_WEBHOOK_MAX_PAYLOAD_BYTES` | 拆分事件、缩减字段或联系管理员调整上限 |
| `eventVersion` 不匹配 | Header 和 source 配置不一致 | 升级 source `eventVersion` 或外部系统回退版本 |
| `CONFLICT` 幂等冲突 | 同一 idempotencyKey 对应不同 payload | 外部系统生成稳定业务幂等键；变更 payload 时换新 key |
| source 不存在或停用 | 路径 sourceCode 错误，或 source 被禁用 | 查询 `/api/v1/document-input/sources` 和 source health |
| 密钥引用未解析 | SecretProvider 未配置、用途/作用域/过期不匹配、fallback 关闭 | 检查 `secret_reference`、provider 状态和 `WP4_LOCAL_WEBHOOK_SECRET_FALLBACK_ENABLED` |
| 轮换后仍按旧密钥验签 | SecretProvider 解析结果仍在短 TTL 缓存内，或外部系统尚未切到新 secretRef | 等待 `max(WP4_WEBHOOK_SECRET_CACHE_TTL_SECONDS, WP4_WEBHOOK_SECRET_ROTATION_OVERLAP_SECONDS)`；确认 source 已更新并查看 `/api/v1/document-input/health` 的 `webhookSecretCache*` 字段 |

## 7. 验收

外部系统联调完成的准出证据：

1. 成功请求响应包含 `traceId`，`data.status=ACCEPTED` 且返回 webhook 事件 ID；后台处理后可通过 `/api/v1/document-input/webhook-events` 查询 `importId`、`PROCESSED/FAILED` 状态和候选或导入记录。
2. 重复投递同一 `eventId` + `idempotencyKey` 不重复生成候选。
3. 故意改错签名会被拒绝，且不进入业务解析。
4. `/api/v1/document-input/webhook-events` 能按 `sourceCode` 查到事件、签名状态和处理状态。
5. `veri.agent.document_input.webhooks` 指标可查询。
