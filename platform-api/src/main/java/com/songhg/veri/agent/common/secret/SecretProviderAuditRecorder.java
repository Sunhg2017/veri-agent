package com.songhg.veri.agent.common.secret;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.common.audit.AuditLogWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class SecretProviderAuditRecorder {

    private static final Logger log = LoggerFactory.getLogger(SecretProviderAuditRecorder.class);
    static final String ACTION = "SECRET_RESOLVE";
    static final String RESOURCE_TYPE = "secret_reference";

    private final AuditLogWriter auditLogWriter;
    private final ObjectMapper objectMapper;

    public SecretProviderAuditRecorder(AuditLogWriter auditLogWriter, ObjectMapper objectMapper) {
        this.auditLogWriter = auditLogWriter;
        this.objectMapper = objectMapper;
    }

    static SecretProviderAuditRecorder noop() {
        return new SecretProviderAuditRecorder(null, new ObjectMapper());
    }

    void recordSuccess(Target target, SecretResolveContext context) {
        record(target, context, "SUCCESS", null);
    }

    void recordFailure(Target target, SecretResolveContext context, String reason) {
        record(target, context, "FAILED", sanitize(reason, target == null ? null : target.secretRef()));
    }

    private void record(Target target, SecretResolveContext context, String result, String reason) {
        if (auditLogWriter == null || target == null || !StringUtils.hasText(target.secretRef())) {
            return;
        }
        String digest = digest(target.secretRef());
        String resourceId = "sha256:" + digest;
        String targetName = target.providerCode() + ":" + digest.substring(0, 16);
        try {
            auditLogWriter.record(new AuditLogWriter.AuditRecord(
                    null,
                    ACTION,
                    RESOURCE_TYPE,
                    resourceId,
                    result,
                    reason,
                    targetName,
                    null,
                    detailsJson(target, context, result, reason, digest, targetName),
                    null
            ));
        } catch (RuntimeException exception) {
            log.warn("SecretProvider resolve audit write failed for providerType={}, result={}",
                    target.providerType(), result, exception);
        }
    }

    private String detailsJson(
            Target target,
            SecretResolveContext context,
            String result,
            String reason,
            String digest,
            String targetName
    ) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("name", targetName);
        details.put("secretRefDigest", digest);
        details.put("providerCode", target.providerCode());
        details.put("providerType", target.providerType());
        details.put("secretVersion", target.secretVersion());
        details.put("purpose", firstText(context == null ? null : context.purpose(), target.purpose()));
        details.put("callerService", context == null ? null : context.callerService());
        details.put("scopeType", firstText(context == null ? null : context.scopeType(), target.scopeType()));
        details.put("scopeId", firstText(context == null ? null : context.scopeId(), target.scopeId()));
        details.put("result", result);
        if (StringUtils.hasText(reason)) {
            details.put("failureReason", reason);
        }
        try {
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("SecretProvider audit details serialization failed", exception);
        }
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private static String sanitize(String reason, String secretRef) {
        if (!StringUtils.hasText(reason)) {
            return "SECRET_RESOLVE_FAILED";
        }
        String sanitized = reason.trim();
        if (StringUtils.hasText(secretRef)) {
            sanitized = sanitized.replace(secretRef.trim(), "<secret-ref>");
        }
        return sanitized.length() > 240 ? sanitized.substring(0, 240) : sanitized;
    }

    private static String digest(String secretRef) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(messageDigest.digest(secretRef.trim().getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SecretProvider audit digest failed", exception);
        }
    }

    record Target(
            String secretRef,
            String providerCode,
            String providerType,
            String secretVersion,
            String purpose,
            String scopeType,
            String scopeId
    ) {
    }
}
