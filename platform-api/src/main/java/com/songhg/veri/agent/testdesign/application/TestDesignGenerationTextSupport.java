package com.songhg.veri.agent.testdesign.application;

import com.songhg.veri.agent.asset.application.view.RequirementResponse;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.testdesign.domain.CoverageType;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.util.StringUtils;

final class TestDesignGenerationTextSupport {

    private static final Set<String> CANDIDATE_PRIORITIES = Set.of("CRITICAL", "HIGH", "MEDIUM", "LOW");

    private TestDesignGenerationTextSupport() {
    }

    static String normalizeCoverageType(String rawValue, String fallback) {
        if (!StringUtils.hasText(rawValue)) {
            return fallback;
        }
        String normalized = rawValue.trim().toUpperCase(Locale.ROOT);
        if (!CoverageType.codes().contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "不支持的覆盖类型: " + rawValue);
        }
        return normalized;
    }

    static String normalizePriority(String rawValue, String fallback) {
        if (!StringUtils.hasText(rawValue)) {
            return StringUtils.hasText(fallback) ? fallback : "MEDIUM";
        }
        String normalized = rawValue.trim().toUpperCase(Locale.ROOT);
        if (!CANDIDATE_PRIORITIES.contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "不支持的优先级: " + rawValue);
        }
        return normalized;
    }

    static String priorityFor(String requirementPriority, String coverageType) {
        if ("EXCEPTION".equals(coverageType) || "PERMISSION".equals(coverageType)) {
            return "HIGH";
        }
        return normalizePriority(requirementPriority, "MEDIUM");
    }

    static double confidenceFor(String coverageType) {
        return switch (coverageType) {
            case "SMOKE", "FUNCTIONAL" -> 0.86D;
            case "EXCEPTION" -> 0.82D;
            default -> 0.78D;
        };
    }

    static String preconditions(RequirementResponse requirement) {
        if (StringUtils.hasText(requirement.acceptanceCriteria())) {
            return "需求验收标准已明确，测试前需准备满足业务上下文的数据";
        }
        return "需求描述已确认，测试数据和账号权限已准备";
    }

    static String modelCaseDescription(TestDesignModelOutputParser.ModelGeneratedCase generatedCase) {
        List<String> parts = new ArrayList<>();
        addDescriptionPart(parts, generatedCase.description());
        addDescriptionPart(parts, generatedCase.rationale() == null ? null : "依据: " + generatedCase.rationale());
        addDescriptionPart(parts, generatedCase.riskNotes() == null ? null : "风险: " + generatedCase.riskNotes());
        String description = String.join("\n", parts);
        if (!StringUtils.hasText(description)) {
            return null;
        }
        String redacted = redactSensitiveText(description);
        return redacted.length() <= 2000 ? redacted : redacted.substring(0, 1997) + "...";
    }

    static List<String> modelCaseTags(TestDesignModelOutputParser.ModelGeneratedCase generatedCase) {
        List<String> tags = new ArrayList<>();
        if (generatedCase.tags() != null) {
            tags.addAll(generatedCase.tags());
        }
        tags.add("wp5");
        tags.add("ai-generated");
        tags.add("model");
        tags.add(generatedCase.coverageType().toLowerCase(Locale.ROOT));
        return tags;
    }

    static String safeErrorMessage(RuntimeException exception) {
        String message = exception.getMessage();
        if (!StringUtils.hasText(message)) {
            return exception.getClass().getSimpleName();
        }
        String redacted = redactSensitiveText(message).replaceAll("\\s+", " ").trim();
        return redacted.length() <= 500 ? redacted : redacted.substring(0, 497) + "...";
    }

    static String redactSensitiveText(String value) {
        // WP5 must not echo obvious secrets from WP3/WP4 source text while the full WP2 context packer is still pending.
        return TestDesignSensitiveText.redact(value);
    }

    static String redactedPreview(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = redactSensitiveText(value).replaceAll("\\s+", " ").trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    static String candidateExportPreview(String value, int maxLength) {
        String preview = redactedPreview(value, maxLength);
        if (!StringUtils.hasText(preview)) {
            return preview;
        }
        return preview
                .replaceAll("(?i)raw\\s*prompt|rawPrompt", "[REDACTED]")
                .replaceAll("(?i)prompt\\s*plaintext|promptPlaintext", "[REDACTED]")
                .replaceAll("(?i)model\\s*input|modelInput", "[REDACTED]");
    }

    static List<String> summaryTags(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        return List.of(value.replace('，', ',').split(",")).stream()
                .map(tag -> redactedPreview(tag, 64))
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    static String duplicateKey(UUID requirementId, String coverageType, String title) {
        return requirementId + ":" + coverageType + ":" + (title == null ? "" : title.trim().toLowerCase(Locale.ROOT));
    }

    static String tagsText(List<String> tags) {
        if (tags == null) {
            return null;
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String tag : tags) {
            if (StringUtils.hasText(tag)) {
                result.add(tag.trim());
            }
        }
        return result.isEmpty() ? null : String.join(",", result);
    }

    private static void addDescriptionPart(List<String> parts, String value) {
        if (StringUtils.hasText(value)) {
            parts.add(value.trim());
        }
    }
}
