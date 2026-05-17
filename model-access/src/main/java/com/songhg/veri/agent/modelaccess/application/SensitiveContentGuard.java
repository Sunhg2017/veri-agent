package com.songhg.veri.agent.modelaccess.application;

import com.songhg.veri.agent.modelaccess.common.BusinessException;
import com.songhg.veri.agent.modelaccess.common.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class SensitiveContentGuard {

    private static final List<Pattern> BLOCK_PATTERNS = List.of(
            Pattern.compile("(?i)(api[_-]?key|secret|password|passwd|token)\\s*[:=]\\s*[^\\s,;}]+"),
            Pattern.compile("(?i)bearer\\s+[a-z0-9._~+/=-]{12,}"),
            Pattern.compile("\\b\\d{15}(\\d{2}[0-9xX])?\\b")
    );

    private static final List<Pattern> MASK_PATTERNS = List.of(
            Pattern.compile("(?i)(api[_-]?key|secret|password|passwd|token)(\\s*[:=]\\s*)([^\\s,;}]+)"),
            Pattern.compile("(?i)(bearer\\s+)[a-z0-9._~+/=-]{12,}"),
            Pattern.compile("\\b\\d{15}(\\d{2}[0-9xX])?\\b")
    );

    public void assertSafe(String content) {
        if (content == null) {
            return;
        }
        boolean blocked = BLOCK_PATTERNS.stream().anyMatch(pattern -> pattern.matcher(content).find());
        if (blocked) {
            throw new BusinessException(ErrorCode.SENSITIVE_CONTENT_BLOCKED, "请求内容疑似包含密钥、令牌或身份证号，已阻断模型调用");
        }
    }

    public String mask(String content) {
        if (content == null) {
            return null;
        }
        String masked = content;
        masked = MASK_PATTERNS.get(0).matcher(masked).replaceAll("$1$2***");
        masked = MASK_PATTERNS.get(1).matcher(masked).replaceAll("$1***");
        masked = MASK_PATTERNS.get(2).matcher(masked).replaceAll("***ID_CARD***");
        if (masked.length() > 600) {
            return masked.substring(0, 600) + "...";
        }
        return masked;
    }

    public String digest(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }
}
