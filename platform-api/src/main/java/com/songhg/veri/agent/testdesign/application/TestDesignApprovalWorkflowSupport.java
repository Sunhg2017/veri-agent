package com.songhg.veri.agent.testdesign.application;

import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.util.StringUtils;

/**
 * Shared validation and normalization rules for WP5 approval work orders.
 *
 * <p>Context policy overrides, release-readiness exceptions and future WP5 approval flows use the same state machine,
 * work-order metadata and note hygiene rules. Keeping these rules together prevents one flow from accepting sensitive
 * text, invalid status transitions or malformed work-order identifiers that another flow rejects.</p>
 */
public final class TestDesignApprovalWorkflowSupport {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String NOTE_TYPE_REQUEST = "REQUEST";
    public static final String NOTE_TYPE_REVIEW = "REVIEW";
    public static final String NOTE_TYPE_COMMENT = "COMMENT";
    public static final String NOTE_TYPE_WORK_ORDER = "WORK_ORDER";

    public static final int MAX_NOTE_CHARS = 1000;
    public static final int MAX_WORK_ORDER_KEY_CHARS = 128;
    public static final int MAX_WORK_ORDER_TITLE_CHARS = 256;
    public static final int MAX_WORK_ORDER_URL_CHARS = 512;

    private static final List<String> ALLOWED_WORK_ORDER_STATUSES = List.of(
            "OPEN",
            "IN_REVIEW",
            STATUS_APPROVED,
            STATUS_REJECTED,
            "CANCELLED"
    );
    private static final List<String> ALLOWED_OPERATOR_NOTE_TYPES = List.of(
            NOTE_TYPE_COMMENT,
            NOTE_TYPE_WORK_ORDER
    );

    private TestDesignApprovalWorkflowSupport() {
    }

    public static int bounded(String fieldName, int value, int maxValue) {
        if (value <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, fieldName + " 必须大于 0");
        }
        if (value > maxValue) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, fieldName + " 不能大于 " + maxValue);
        }
        return value;
    }

    public static String reasonCode(String value, String fieldName, List<String> allowedReasonCodes) {
        String normalized = requiredCode(value, fieldName).toUpperCase(Locale.ROOT);
        if (allowedReasonCodes == null || !allowedReasonCodes.contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, fieldName + " 不在允许范围内");
        }
        return normalized;
    }

    public static String noteType(String value) {
        String normalized = requiredCode(value, "noteType").toUpperCase(Locale.ROOT);
        if (!ALLOWED_OPERATOR_NOTE_TYPES.contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "noteType 不在允许范围内");
        }
        return normalized;
    }

    public static String workOrderStatus(String value, String nextApprovalStatus) {
        if (!StringUtils.hasText(value)) {
            return STATUS_APPROVED.equals(nextApprovalStatus) ? STATUS_APPROVED : STATUS_REJECTED;
        }
        String normalized = requiredCode(value, "workOrderStatus").toUpperCase(Locale.ROOT);
        if (!ALLOWED_WORK_ORDER_STATUSES.contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "workOrderStatus 不在允许范围内");
        }
        return normalized;
    }

    public static String workOrderKey(String value, UUID approvalId, String prefix) {
        String safePrefix = StringUtils.hasText(prefix) ? prefix.trim() : "WP5-APPROVAL";
        String normalized = StringUtils.hasText(value)
                ? value.trim()
                : safePrefix + "-" + approvalId.toString().substring(0, 8);
        if (normalized.length() > MAX_WORK_ORDER_KEY_CHARS) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "workOrderKey 不能大于 " + MAX_WORK_ORDER_KEY_CHARS
            );
        }
        if (!normalized.matches("[A-Za-z0-9_.:-]{1,128}")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "workOrderKey 只能包含字母、数字、点、冒号、下划线或短横线");
        }
        return normalized;
    }

    public static String workOrderUrl(String value) {
        String normalized = boundedSafeText(value, "workOrderUrl", MAX_WORK_ORDER_URL_CHARS, false, false);
        if (normalized == null) {
            return null;
        }
        if (!normalized.matches("https?://[^\\s]+")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "workOrderUrl 仅支持 http 或 https URL");
        }
        return normalized;
    }

    public static String replacementText(
            String nextValue,
            String currentValue,
            String fieldName,
            int maxLength,
            boolean allowNewline
    ) {
        return StringUtils.hasText(nextValue)
                ? boundedSafeText(nextValue, fieldName, maxLength, allowNewline, false)
                : currentValue;
    }

    public static String boundedSafeText(
            String value,
            String fieldName,
            int maxLength,
            boolean allowNewline,
            boolean required
    ) {
        if (!StringUtils.hasText(value)) {
            if (required) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, fieldName + " 不能为空");
            }
            return null;
        }
        String normalized = value.trim();
        if (!allowNewline && (normalized.contains("\n") || normalized.contains("\r"))) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, fieldName + " 不能包含换行");
        }
        if (normalized.length() > maxLength) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, fieldName + " 不能大于 " + maxLength);
        }
        if (TestDesignSensitiveText.containsSensitiveText(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, fieldName + " 不能包含密钥、token 或授权信息");
        }
        return normalized;
    }

    public static String requiredCode(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, fieldName + " 不能为空");
        }
        String normalized = value.trim();
        if (!normalized.matches("[A-Za-z0-9_.:-]{1,64}")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, fieldName + " 只能包含字母、数字、点、冒号、下划线或短横线");
        }
        return normalized;
    }

    public static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    public static String sha256OrNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }
}
