package com.songhg.veri.agent.testdesign.application;

import java.util.List;
import java.util.regex.Pattern;
import org.springframework.util.StringUtils;

final class TestDesignSensitiveText {

    static final String REDACTED_SECRET = "[REDACTED]";
    private static final List<Pattern> SENSITIVE_TEXT_PATTERNS = List.of(
            Pattern.compile("(?i)\\bbearer\\s+[a-z0-9._\\-]{8,}"),
            Pattern.compile("(?i)\\b(api[_-]?key|secret|token|password|passwd|authorization)\\s*[:=]\\s*[^\\s,;，；]+"),
            Pattern.compile("(?i)\\b(sk|pk|rk)_[a-z0-9]{8,}\\b")
    );

    private TestDesignSensitiveText() {
    }

    static String redact(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        String redacted = value;
        for (Pattern pattern : SENSITIVE_TEXT_PATTERNS) {
            redacted = pattern.matcher(redacted).replaceAll(REDACTED_SECRET);
        }
        return redacted;
    }

    static boolean containsSensitiveText(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        return SENSITIVE_TEXT_PATTERNS.stream()
                .anyMatch(pattern -> pattern.matcher(value).find());
    }
}
