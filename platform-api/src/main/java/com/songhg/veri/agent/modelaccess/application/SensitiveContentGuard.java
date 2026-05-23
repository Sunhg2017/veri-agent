package com.songhg.veri.agent.modelaccess.application;

import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class SensitiveContentGuard {

    private static final List<SensitiveRule> RULES = List.of(
            new SensitiveRule(
                    Pattern.compile("(?i)(api[_-]?key|secret|password|passwd|token)(\\s*[:=]\\s*)([^\\s,;}]+)"),
                    matcher -> matcher.group(1) + matcher.group(2) + "***"
            ),
            new SensitiveRule(
                    Pattern.compile("(?i)(internal[_-]?token|corp[_-]?secret|private[_-]?key)(\\s*[:=]\\s*)([^\\s,;}]+)"),
                    matcher -> matcher.group(1) + matcher.group(2) + "***"
            ),
            new SensitiveRule(
                    Pattern.compile("(?i)(bearer\\s+)[a-z0-9._~+/=-]{12,}"),
                    matcher -> matcher.group(1) + "***"
            ),
            new SensitiveRule(Pattern.compile("\\b\\d{15}(\\d{2}[0-9xX])?\\b"), matcher -> "***ID_CARD***"),
            new SensitiveRule(Pattern.compile("(?<!\\d)(?:\\+?86[-\\s]?)?1[3-9]\\d{9}(?!\\d)"), matcher -> "***PHONE***"),
            new SensitiveRule(Pattern.compile("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b"), matcher -> "***EMAIL***"),
            new SensitiveRule(Pattern.compile("\\b(?:\\d[ -]?){13,19}\\b"), matcher -> "***BANK_CARD***")
    );

    public void assertSafe(String content) {
        if (content == null) {
            return;
        }
        boolean blocked = RULES.stream().anyMatch(rule -> rule.pattern().matcher(content).find());
        if (blocked) {
            throw new BusinessException(ErrorCode.SENSITIVE_CONTENT_BLOCKED, "请求内容疑似包含密钥、令牌、身份证号、手机号、邮箱或银行卡号，已阻断模型调用");
        }
    }

    public String mask(String content) {
        if (content == null) {
            return null;
        }
        String masked = applyMaskRules(content);
        if (masked.length() > 600) {
            return masked.substring(0, 600) + "...";
        }
        return masked;
    }

    private String applyMaskRules(String content) {
        List<MaskReplacement> replacements = new ArrayList<>();
        for (SensitiveRule rule : RULES) {
            Matcher matcher = rule.pattern().matcher(content);
            while (matcher.find()) {
                replacements.add(new MaskReplacement(
                        matcher.start(),
                        matcher.end(),
                        rule.replacement().replace(matcher)
                ));
            }
        }
        replacements.sort(Comparator.comparingInt(MaskReplacement::start));
        StringBuilder masked = new StringBuilder(content.length());
        int cursor = 0;
        for (MaskReplacement replacement : replacements) {
            if (replacement.start() < cursor) {
                continue;
            }
            masked.append(content, cursor, replacement.start());
            masked.append(replacement.value());
            cursor = replacement.end();
        }
        masked.append(content, cursor, content.length());
        return masked.toString();
    }

    public String digest(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }

    private record SensitiveRule(Pattern pattern, Replacement replacement) {
    }

    private record MaskReplacement(int start, int end, String value) {
    }

    @FunctionalInterface
    private interface Replacement {
        String replace(Matcher matcher);
    }
}
