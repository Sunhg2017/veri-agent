package com.songhg.veri.agent.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.util.StringUtils;

/**
 * Shared bounded text, sensitive text redaction and digest helpers for evidence and audit payloads.
 *
 * <p>Callers must still decide which fields are safe to persist. This utility only centralizes the mechanical
 * redaction/digest behavior so WP6/WP9 services do not drift into incompatible null handling or regex rules.</p>
 */
public final class SensitiveTextSanitizer {

    private static final List<Pattern> SENSITIVE_TEXT_PATTERNS = List.of(
            Pattern.compile("(?i)\\bbearer\\s+[a-z0-9._\\-]{8,}"),
            Pattern.compile("(?i)\\b(api[_-]?key|secret|token|password|passwd|authorization|cookie)\\s*[:=]\\s*[^\\s,;，；]+"),
            Pattern.compile("(?i)\\blease\\s+token(?:\\s*[:=]\\s*[^\\s,;，；]+)?"),
            Pattern.compile("(?i)\\b(sk|pk|rk)_[a-z0-9_-]{8,}\\b")
    );
    private static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s,;，；]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern SECRET_REF_PATTERN = Pattern.compile("secret://[^\\s,;，；]+", Pattern.CASE_INSENSITIVE);

    private SensitiveTextSanitizer() {
    }

    public static String boundedNullableText(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return boundedText(value, maxLength);
    }

    public static String boundedText(String value, int maxLength) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    public static String boundedWithEllipsis(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    public static String redactSensitiveText(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        String redacted = value;
        for (Pattern pattern : SENSITIVE_TEXT_PATTERNS) {
            redacted = pattern.matcher(redacted).replaceAll("[REDACTED]");
        }
        return redacted;
    }

    public static boolean containsSensitiveText(String value) {
        return StringUtils.hasText(value)
                && SENSITIVE_TEXT_PATTERNS.stream().anyMatch(pattern -> pattern.matcher(value).find());
    }

    public static String sanitizedErrorSummary(String value, String fallback, int maxLength) {
        String summary = StringUtils.hasText(value) ? value.trim() : fallback;
        summary = URL_PATTERN.matcher(summary).replaceAll("[REDACTED_URL]");
        summary = SECRET_REF_PATTERN.matcher(summary).replaceAll("[REDACTED_SECRET_REF]");
        summary = redactSensitiveText(summary);
        return boundedWithEllipsis(summary, maxLength);
    }

    /**
     * Sanitizes persisted evidence fields where plain text may contain runtime URLs or secret references.
     */
    public static String sanitizedEvidenceText(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        String sanitized = URL_PATTERN.matcher(value.trim()).replaceAll("[REDACTED_URL]");
        sanitized = SECRET_REF_PATTERN.matcher(sanitized).replaceAll("[REDACTED_SECRET_REF]");
        sanitized = redactSensitiveText(sanitized);
        return boundedWithEllipsis(sanitized, maxLength);
    }

    public static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public static String sha256Hex(Object value) {
        return sha256Hex(value == null ? "" : String.valueOf(value));
    }

    public static Map<String, Object> unreadableMap() {
        return Map.of("unreadable", true);
    }
}
