package com.songhg.veri.agent.testdesign.application;

import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.testdesign.application.view.TestDesignStepResponse;
import com.songhg.veri.agent.testdesign.domain.CoverageType;
import com.songhg.veri.agent.testdesign.domain.TestDesignCandidate;
import com.songhg.veri.agent.testdesign.domain.TestDesignCandidateStatus;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class TestDesignCandidateQualityGate {

    private static final int MIN_STEP_COUNT = 2;
    private static final int MAX_STEP_COUNT = 12;
    private static final int MAX_TITLE_LENGTH = 160;
    private static final int MAX_TEXT_LENGTH = 2_000;
    private static final Set<String> PRIORITIES = Set.of("CRITICAL", "HIGH", "MEDIUM", "LOW");

    private final TestDesignResponseMapper responseMapper;

    public TestDesignCandidateQualityGate(TestDesignResponseMapper responseMapper) {
        this.responseMapper = responseMapper;
    }

    /**
     * Blocks invalid generated output before any candidate row is persisted.
     */
    public void validateGeneratedBatch(List<TestDesignCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            throw qualityException("生成输出为空");
        }
        validateCandidates(candidates);
        validateUniqueCandidates(candidates);
    }

    /**
     * Applies the same schema and duplicate checks to human-edited candidates before review can continue.
     */
    public void validateReviewCandidate(TestDesignCandidate candidate, List<TestDesignCandidate> taskCandidates) {
        validateCandidate(candidate);
        List<TestDesignCandidate> candidates = new ArrayList<>();
        if (taskCandidates != null) {
            taskCandidates.stream()
                    .filter(Objects::nonNull)
                    .filter(item -> !candidate.id().equals(item.id()))
                    .forEach(candidates::add);
        }
        candidates.add(candidate);
        validateUniqueCandidates(candidates);
    }

    private void validateCandidates(List<TestDesignCandidate> candidates) {
        candidates.forEach(this::validateCandidate);
    }

    private void validateCandidate(TestDesignCandidate candidate) {
        List<String> violations = new ArrayList<>();
        require(candidate.id() != null, "id 不能为空", violations);
        require(candidate.taskId() != null, "taskId 不能为空", violations);
        require(StringUtils.hasText(candidate.projectId()), "projectId 不能为空", violations);
        require(candidate.requirementId() != null, "requirementId 不能为空", violations);
        requireText(candidate.title(), "title", MAX_TITLE_LENGTH, violations);
        requireCoverageType(candidate.coverageType(), violations);
        requirePriority(candidate.priority(), violations);
        requireStatus(candidate.status(), violations);
        requireText(candidate.expectedResult(), "expectedResult", MAX_TEXT_LENGTH, violations);
        require(StringUtils.hasText(candidate.duplicateKey()), "duplicateKey 不能为空", violations);
        require(StringUtils.hasText(candidate.promptKey()), "promptKey 不能为空", violations);
        require(StringUtils.hasText(candidate.promptVersion()), "promptVersion 不能为空", violations);
        require(candidate.confidence() >= 0D && candidate.confidence() <= 1D, "confidence 必须在 0 到 1 之间", violations);
        validateOptionalText(candidate.description(), "description", violations);
        validateOptionalText(candidate.preconditions(), "preconditions", violations);
        validateOptionalText(candidate.tags(), "tags", violations);
        validateSteps(candidate, violations);
        if (!violations.isEmpty()) {
            throw qualityException(String.join("; ", violations));
        }
    }

    private void validateSteps(TestDesignCandidate candidate, List<String> violations) {
        List<TestDesignStepResponse> steps = responseMapper.steps(candidate.stepsJson());
        require(steps.size() >= MIN_STEP_COUNT, "steps 至少需要 " + MIN_STEP_COUNT + " 步", violations);
        require(steps.size() <= MAX_STEP_COUNT, "steps 最多支持 " + MAX_STEP_COUNT + " 步", violations);
        for (int index = 0; index < steps.size(); index++) {
            TestDesignStepResponse step = steps.get(index);
            require(step.stepOrder() == index, "steps[" + index + "].stepOrder 必须连续", violations);
            requireText(step.action(), "steps[" + index + "].action", MAX_TEXT_LENGTH, violations);
            requireText(step.expectedResult(), "steps[" + index + "].expectedResult", MAX_TEXT_LENGTH, violations);
        }
    }

    private void validateUniqueCandidates(List<TestDesignCandidate> candidates) {
        Set<String> duplicateKeys = new HashSet<>();
        Set<String> titleKeys = new HashSet<>();
        for (TestDesignCandidate candidate : candidates) {
            String duplicateKey = normalizeIdentity(candidate.duplicateKey());
            if (StringUtils.hasText(duplicateKey) && !duplicateKeys.add(duplicateKey)) {
                throw qualityException("duplicateKey 重复: " + candidate.duplicateKey());
            }
            String titleKey = normalizeIdentity(candidate.requirementId() + ":" + candidate.coverageType() + ":" + candidate.title());
            if (StringUtils.hasText(titleKey) && !titleKeys.add(titleKey)) {
                throw qualityException("同需求同覆盖类型候选标题重复");
            }
        }
    }

    private void requireCoverageType(String value, List<String> violations) {
        require(StringUtils.hasText(value), "coverageType 不能为空", violations);
        if (StringUtils.hasText(value) && !CoverageType.codes().contains(value)) {
            violations.add("coverageType 不支持: " + value);
        }
    }

    private void requirePriority(String value, List<String> violations) {
        require(StringUtils.hasText(value), "priority 不能为空", violations);
        if (StringUtils.hasText(value) && !PRIORITIES.contains(value)) {
            violations.add("priority 不支持: " + value);
        }
    }

    private void requireStatus(String value, List<String> violations) {
        require(StringUtils.hasText(value), "status 不能为空", violations);
        if (StringUtils.hasText(value) && !TestDesignCandidateStatus.codes().contains(value)) {
            violations.add("status 不支持: " + value);
        }
    }

    private void requireText(String value, String fieldName, int maxLength, List<String> violations) {
        require(StringUtils.hasText(value), fieldName + " 不能为空", violations);
        validateOptionalText(value, fieldName, maxLength, violations);
    }

    private void validateOptionalText(String value, String fieldName, List<String> violations) {
        validateOptionalText(value, fieldName, MAX_TEXT_LENGTH, violations);
    }

    private void validateOptionalText(String value, String fieldName, int maxLength, List<String> violations) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        if (value.length() > maxLength) {
            violations.add(fieldName + " 长度不能超过 " + maxLength);
        }
        if (TestDesignSensitiveText.containsSensitiveText(value)) {
            violations.add(fieldName + " 包含疑似敏感信息");
        }
    }

    private static void require(boolean condition, String message, List<String> violations) {
        if (!condition) {
            violations.add(message);
        }
    }

    private static BusinessException qualityException(String message) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, "WP5 候选用例质量门禁不通过: " + message);
    }

    private static String normalizeIdentity(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        value.trim().toLowerCase(Locale.ROOT).codePoints()
                .filter(Character::isLetterOrDigit)
                .forEach(builder::appendCodePoint);
        return builder.toString();
    }
}
