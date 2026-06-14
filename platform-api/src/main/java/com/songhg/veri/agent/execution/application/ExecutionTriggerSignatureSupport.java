package com.songhg.veri.agent.execution.application;

import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.common.util.SensitiveTextSanitizer;
import com.songhg.veri.agent.execution.domain.ExecutionTrigger;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import java.util.function.Supplier;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.util.StringUtils;

/**
 * Centralizes WP9 trigger idempotency, digest, and webhook HMAC calculations.
 */
final class ExecutionTriggerSignatureSupport {

    String webhookRequestKey(UUID triggerId, String sourceEventId) {
        return "webhook:" + sha256(triggerId + ":" + sourceEventId).substring(0, 64);
    }

    String cronRequestKey(UUID triggerId, String sourceEventId) {
        return "cron:" + sha256(triggerId + ":" + sourceEventId).substring(0, 64);
    }

    String cronRequestDigest(ExecutionTrigger trigger, Instant fireAt) {
        return sha256(String.join(".",
                "cron",
                trigger.id().toString(),
                fireAt.toString(),
                trigger.configDigest()
        ));
    }

    String requestDigest(String timestamp, String sourceEventId, String rawPayload) {
        return sha256(String.join(".",
                timestamp == null ? "" : timestamp.trim(),
                sourceEventId == null ? "" : sourceEventId.trim(),
                rawPayload == null ? "" : rawPayload
        ));
    }

    boolean validWebhookSignature(
            Supplier<String> secretSupplier,
            String rawPayload,
            String timestamp,
            String signature,
            String sourceEventId,
            long allowedClockSkewSeconds
    ) {
        if (!StringUtils.hasText(timestamp) || !StringUtils.hasText(signature)) {
            return false;
        }
        long epochSeconds;
        try {
            epochSeconds = Long.parseLong(timestamp.trim());
        } catch (NumberFormatException exception) {
            return false;
        }
        if (Math.abs(Instant.now().getEpochSecond() - epochSeconds) > allowedClockSkewSeconds) {
            return false;
        }
        String secret = secretSupplier.get();
        String expected = hmacSha256(secret, String.join(".",
                timestamp.trim(),
                sourceEventId.trim(),
                rawPayload == null ? "" : rawPayload
        ));
        return constantTimeEquals(expected, signature.trim());
    }

    String sha256(String value) {
        return SensitiveTextSanitizer.sha256Hex(value);
    }

    String stringOrEmpty(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String hmacSha256(String secret, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "EXECUTION_TRIGGER_SIGNATURE_FAILED");
        }
    }

    private boolean constantTimeEquals(String expected, String actual) {
        if (!StringUtils.hasText(expected) || !StringUtils.hasText(actual)) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8)
        );
    }
}
