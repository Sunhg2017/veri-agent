package com.songhg.veri.agent.document.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.document.config.DocumentInputProperties;
import com.songhg.veri.agent.document.domain.DocumentSourceConfig;
import com.songhg.veri.agent.document.domain.DocumentSourceStatus;
import com.songhg.veri.agent.document.domain.DocumentSourceType;
import com.songhg.veri.agent.document.domain.WebhookSignatureStatus;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Set;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
class DocumentWebhookSupport {

    private static final Set<DocumentSourceType> SUPPORTED_SOURCE_TYPES = Set.of(
            DocumentSourceType.TEXT,
            DocumentSourceType.MARKDOWN,
            DocumentSourceType.WORD,
            DocumentSourceType.PDF,
            DocumentSourceType.OCR,
            DocumentSourceType.CUSTOM_API
    );
    private static final Set<String> SUPPORTED_WEBHOOK_EVENT_VERSIONS = Set.of("1.0");
    private static final Set<String> SUPPORTED_WEBHOOK_EVENT_TYPES = Set.of(
            "requirement.created",
            "requirement.updated",
            "requirement.statusChanged",
            "requirement.archived"
    );

    private final ObjectMapper objectMapper;
    private final DocumentInputProperties properties;
    private final DocumentWebhookSecretResolver webhookSecretResolver;

    DocumentWebhookSupport(
            ObjectMapper objectMapper,
            DocumentInputProperties properties,
            DocumentWebhookSecretResolver webhookSecretResolver
    ) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.webhookSecretResolver = webhookSecretResolver;
    }

    int supportedSourceTypeCount() {
        return SUPPORTED_SOURCE_TYPES.size();
    }

    JsonNode parsePayloadOrNull(String rawPayload) {
        if (rawPayload == null) {
            return null;
        }
        try {
            return objectMapper.readTree(rawPayload);
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    JsonNode parsePayload(String rawPayload) {
        if (rawPayload == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, DocumentInputMessages.WEBHOOK_PAYLOAD_INVALID_JSON);
        }
        try {
            return objectMapper.readTree(rawPayload);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, DocumentInputMessages.WEBHOOK_PAYLOAD_INVALID_JSON);
        }
    }

    /**
     * Webhook ingress may accept syntactically invalid JSON only long enough to persist
     * the event envelope; asynchronous processing still rejects it via parsePayload.
     */
    String eventTypeOrDefault(String rawPayload) {
        JsonNode payload = parsePayloadOrNull(rawPayload);
        return firstText(textAt(payload, "eventType"), textAt(payload, "type"), "requirement.created");
    }

    WebhookSignatureStatus validateSignature(
            DocumentSourceConfig source,
            String rawPayload,
            String timestamp,
            String signature,
            String eventId,
            String idempotencyKey
    ) {
        if (!StringUtils.hasText(timestamp) || !StringUtils.hasText(signature)) {
            return WebhookSignatureStatus.MISSING;
        }
        long epochSeconds;
        try {
            epochSeconds = Long.parseLong(timestamp.trim());
        } catch (NumberFormatException exception) {
            return WebhookSignatureStatus.INVALID;
        }
        long skew = properties.webhookClockSkewSeconds() <= 0 ? 300 : properties.webhookClockSkewSeconds();
        if (Math.abs(Instant.now().getEpochSecond() - epochSeconds) > skew) {
            return WebhookSignatureStatus.EXPIRED;
        }
        String expected = hmacSha256(webhookSecretResolver.resolve(source), String.join(".",
                timestamp.trim(),
                eventId.trim(),
                idempotencyKey.trim(),
                rawPayload == null ? "" : rawPayload
        ));
        return constantTimeEquals(expected, signature.trim())
                ? WebhookSignatureStatus.VALID
                : WebhookSignatureStatus.INVALID;
    }

    void ensureExecutableSource(DocumentSourceType sourceType, DocumentSourceStatus status) {
        if (!SUPPORTED_SOURCE_TYPES.contains(sourceType)) {
            throw new BusinessException(ErrorCode.INVALID_STATE, DocumentInputMessages.SOURCE_TYPE_NOT_IMPLEMENTED.formatted(sourceType));
        }
        if (status != DocumentSourceStatus.ENABLED) {
            throw new BusinessException(ErrorCode.INVALID_STATE, DocumentInputMessages.SOURCE_NOT_ENABLED);
        }
    }

    void ensureSupportedWebhookEventType(String eventType) {
        if (!SUPPORTED_WEBHOOK_EVENT_TYPES.contains(eventType)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, DocumentInputMessages.UNSUPPORTED_EVENT_TYPE.formatted(eventType));
        }
    }

    void ensureSupportedWebhookEventVersion(String eventVersion) {
        if (!StringUtils.hasText(eventVersion)
                || !SUPPORTED_WEBHOOK_EVENT_VERSIONS.contains(eventVersion.trim())) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    DocumentInputMessages.UNSUPPORTED_EVENT_VERSION.formatted(eventVersion)
            );
        }
    }

    void ensureSourceWebhookEventVersion(DocumentSourceConfig source, String eventVersion) {
        if (source.sourceType() != DocumentSourceType.CUSTOM_API) {
            return;
        }
        String configured = normalizeEventVersion(source.eventVersion());
        if (!configured.equals(eventVersion.trim())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    DocumentInputMessages.WEBHOOK_EVENT_VERSION_MISMATCH.formatted(eventVersion));
        }
    }

    long maxWebhookPayloadBytes() {
        return properties.webhookMaxPayloadBytes() <= 0 ? 262144 : properties.webhookMaxPayloadBytes();
    }

    long maxImportContentBytes() {
        return properties.importMaxContentBytes() <= 0 ? 16777216 : properties.importMaxContentBytes();
    }

    String webhookPayloadLimitMessage() {
        return DocumentInputMessages.WEBHOOK_PAYLOAD_EXCEEDS_LIMIT
                + maxWebhookPayloadBytes()
                + " bytes。下一步：缩减单次事件 payload 或联系管理员调整 "
                + "WP4_WEBHOOK_MAX_PAYLOAD_BYTES。";
    }

    String webhookSignatureFailureMessage(WebhookSignatureStatus signatureStatus) {
        return switch (signatureStatus) {
            case MISSING -> DocumentInputMessages.WEBHOOK_SIGNATURE_MISSING_HINT
                    + "X-VA-Timestamp、X-VA-Signature、X-VA-Event-Id、X-VA-Idempotency-Key "
                    + "与 X-VA-Event-Version。";
            case EXPIRED -> DocumentInputMessages.WEBHOOK_SIGNATURE_EXPIRED_HINT
                    + "并确认请求在 WP4_WEBHOOK_CLOCK_SKEW_SECONDS 窗口内发送。";
            case INVALID -> DocumentInputMessages.WEBHOOK_SIGNATURE_INVALID_HINT
                    + "timestamp.eventId.idempotencyKey.rawBody 签名串和小写 hex 输出一致。";
            case VALID -> DocumentInputMessages.WEBHOOK_SIGNATURE_UNKNOWN_HINT
                    + "secretRef 配置。";
        };
    }

    int batchActionLimit() {
        return properties.batchActionLimit() <= 0 ? 100 : properties.batchActionLimit();
    }

    int maxReplayAttempts() {
        return properties.webhookMaxReplayAttempts() <= 0 ? 3 : properties.webhookMaxReplayAttempts();
    }

    long payloadSize(String rawPayload) {
        return (rawPayload == null ? "" : rawPayload).getBytes(StandardCharsets.UTF_8).length;
    }

    String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "导入内容摘要计算失败");
        }
    }

    String textAt(JsonNode node, String path) {
        if (node == null || !StringUtils.hasText(path)) {
            return null;
        }
        JsonNode current = node;
        for (String segment : path.split("\\.")) {
            if (!StringUtils.hasText(segment)) {
                continue;
            }
            current = current.path(segment.trim());
            if (current.isMissingNode() || current.isNull()) {
                return null;
            }
        }
        return current.isValueNode() ? current.asText() : current.toString();
    }

    String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    String normalizeSourceCode(String value) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "sourceCode 不能为空");
        }
        return value.trim();
    }

    String normalizeEventVersion(String value) {
        String normalized = StringUtils.hasText(value) ? value.trim() : "1.0";
        ensureSupportedWebhookEventVersion(normalized);
        return normalized;
    }

    private String hmacSha256(String secret, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, DocumentInputMessages.WEBHOOK_SIGNATURE_VERIFICATION_FAILED);
        }
    }

    private boolean constantTimeEquals(String expected, String actual) {
        if (!StringUtils.hasText(expected) || !StringUtils.hasText(actual)) {
            return false;
        }
        byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
        byte[] actualBytes = actual.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expectedBytes, actualBytes);
    }
}
