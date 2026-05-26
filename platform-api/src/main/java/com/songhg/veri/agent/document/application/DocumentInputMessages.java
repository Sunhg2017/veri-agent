package com.songhg.veri.agent.document.application;

/**
 * Centralizes Chinese business messages for the document-input module.
 *
 * <p>These constants replace inline string literals so that messages are
 * reviewable, traceable, and can be externalised to a resource bundle later.
 */
final class DocumentInputMessages {

    private DocumentInputMessages() {
    }

    // Webhook event lifecycle
    static final String WEBHOOK_EVENT_NOT_FOUND = "webhook 事件不存在: %s";
    static final String WEBHOOK_PAYLOAD_NOT_REPLAYABLE = "webhook 原始 payload 不可重放";
    static final String WEBHOOK_SIGNATURE_FAILED = "签名未通过的 webhook 事件不可重放";
    static final String WEBHOOK_ONLY_FAILED_OR_DEAD = "仅失败或死信 webhook 事件允许重放";
    static final String WEBHOOK_IDEMPOTENCY_KEY_CONFLICT = "webhook 幂等键已使用但 payload 不一致";
    static final String WEBHOOK_EVENT_PENDING = "webhook 事件已接收但未成功处理";

    // Source & webhook status
    static final String SOURCE_NOT_FOUND = "文档源不存在: %s";
    static final String SOURCE_NOT_ENABLED = "文档源未启用";
    static final String WEBHOOK_IP_NOT_ALLOWED = "webhook 来源 IP 不在白名单: %s";
    static final String WEBHOOK_RATE_LIMITED = "webhook 请求过于频繁: dimension=%s, limit=%d, windowSeconds=%d, remoteIp=%s";
    static final String WEBHOOK_PAYLOAD_MISSING_PROJECT_ID = "webhook payload 缺少 projectId";
    static final String WEBHOOK_PAYLOAD_INVALID_JSON = "webhook payload 不是合法 JSON";
    static final String WEBHOOK_MISSING_TIMESTAMP = "webhook 缺少 X-VA-Timestamp";
    static final String WEBHOOK_MISSING_SIGNATURE = "webhook 缺少 X-VA-Signature";
    static final String WEBHOOK_MISSING_EVENT_ID = "webhook 缺少 X-VA-Event-Id";
    static final String WEBHOOK_MISSING_IDEMPOTENCY_KEY = "webhook 缺少 X-VA-Idempotency-Key";
    static final String WEBHOOK_MISSING_EVENT_VERSION = "webhook 缺少 X-VA-Event-Version";
    static final String WEBHOOK_SIGNATURE_VERIFICATION_FAILED = "webhook 签名校验失败";
    static final String WEBHOOK_EVENT_VERSION_MISMATCH = "webhook eventVersion 与文档源配置不一致: %s";
    static final String WEBHOOK_PAYLOAD_EXCEEDS_LIMIT = "webhook payload 超过上限: ";
    static final String WEBHOOK_SIGNATURE_MISSING_HINT = "webhook 签名缺失。下一步：确认外部系统和网关转发 ";
    static final String WEBHOOK_SIGNATURE_EXPIRED_HINT = "webhook 签名已过期。下一步：校准外部系统和平台服务器时间，";
    static final String WEBHOOK_SIGNATURE_INVALID_HINT = "webhook 签名无效。下一步：确认 secretRef/WP4_WEBHOOK_SECRET、raw body、";
    static final String WEBHOOK_SIGNATURE_UNKNOWN_HINT = "webhook 签名无效或已过期。下一步：检查签名串、时间窗口和 ";

    // Source status & feature flags
    static final String SOURCE_TYPE_NOT_IMPLEMENTED = "%s 数据流尚未实现";
    static final String INPUT_DISABLED = "WP4 文档输入已关闭";
    static final String WEBHOOK_INPUT_DISABLED = "WP4 webhook 输入已关闭";
    static final String UNSUPPORTED_EVENT_TYPE = "不支持的 webhook eventType: %s";
    static final String UNSUPPORTED_EVENT_VERSION = "不支持的 webhook eventVersion: %s";

    // DocumentModelRequirementParser messages
    static final String MODEL_NO_VALID_REQUIREMENTS = "模型未返回有效候选需求";
    static final String MODEL_PARSE_FAILED = "模型解析失败";
    static final String MODEL_RESPONSE_NOT_JSON = "模型响应不是有效 JSON";
    static final String MODEL_REQUEST_SERIALIZE_FAILED = "模型解析请求无法序列化";
}
