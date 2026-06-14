package com.songhg.veri.agent.testdesign.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.testdesign.domain.CoverageType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class TestDesignModelOutputParser {

    private static final int MIN_CASE_COUNT = 1;
    private static final int MAX_CASE_COUNT = 50;
    private static final int MIN_STEP_COUNT = 2;
    private static final int MAX_STEP_COUNT = 12;
    private static final int MAX_TITLE_LENGTH = 160;
    private static final int MAX_TEXT_LENGTH = 2_000;
    private static final int MAX_SCHEMA_VERSION_LENGTH = 32;
    private static final int MAX_REF_LENGTH = 128;
    private static final int MAX_TAG_LENGTH = 64;
    private static final int MAX_LIST_SIZE = 20;
    private static final double DEFAULT_CONFIDENCE = 0.5D;
    private static final Set<String> PRIORITIES = Set.of("CRITICAL", "HIGH", "MEDIUM", "LOW");
    private static final Set<String> ROOT_FIELDS = Set.of("schemaVersion", "cases");
    private static final Set<String> CASE_FIELDS = Set.of(
            "title",
            "description",
            "coverageType",
            "priority",
            "preconditions",
            "steps",
            "expectedResult",
            "requirementRef",
            "apiRefs",
            "pageRefs",
            "flowRefs",
            "tags",
            "rationale",
            "riskNotes",
            "confidence"
    );
    private static final Set<String> STEP_FIELDS = Set.of("action", "expectedResult");

    private final ObjectMapper objectMapper;

    public TestDesignModelOutputParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Parses raw WP2 model text at the WP5 boundary and rejects malformed or sensitive content before persistence.
     * The raw response itself is intentionally discarded so later task records keep only structured candidates and
     * model invocation metadata.
     */
    public List<ModelGeneratedCase> parse(String rawOutput) {
        String json = extractJson(rawOutput);
        if (!StringUtils.hasText(json)) {
            throw schemaException("模型输出不是 JSON 对象");
        }
        JsonNode root = readRoot(json);
        List<String> violations = new ArrayList<>();
        if (!root.isObject()) {
            throw schemaException("根节点必须是对象");
        }
        requireAllowedFields(root, ROOT_FIELDS, "$", violations);
        validateOptionalText(root.path("schemaVersion"), "$.schemaVersion", MAX_SCHEMA_VERSION_LENGTH, violations);
        JsonNode casesNode = root.path("cases");
        if (!casesNode.isArray()) {
            violations.add("$.cases 必须是数组");
        } else {
            require(casesNode.size() >= MIN_CASE_COUNT, "$.cases 至少需要 " + MIN_CASE_COUNT + " 条", violations);
            require(casesNode.size() <= MAX_CASE_COUNT, "$.cases 最多支持 " + MAX_CASE_COUNT + " 条", violations);
        }
        if (!violations.isEmpty()) {
            throw schemaException(String.join("; ", violations));
        }
        List<ModelGeneratedCase> cases = new ArrayList<>();
        for (int index = 0; index < casesNode.size(); index++) {
            cases.add(parseCase(casesNode.get(index), index));
        }
        return Collections.unmodifiableList(cases);
    }

    private ModelGeneratedCase parseCase(JsonNode node, int index) {
        List<String> violations = new ArrayList<>();
        String path = "$.cases[" + index + "]";
        if (!node.isObject()) {
            throw schemaException(path + " 必须是对象");
        }
        requireAllowedFields(node, CASE_FIELDS, path, violations);
        String title = requiredText(node.path("title"), path + ".title", MAX_TITLE_LENGTH, violations);
        String coverageType = normalizeEnum(requiredText(node.path("coverageType"), path + ".coverageType", MAX_REF_LENGTH, violations));
        if (StringUtils.hasText(coverageType) && !CoverageType.codes().contains(coverageType)) {
            violations.add(path + ".coverageType 不支持: " + coverageType);
        }
        String priority = normalizeEnum(requiredText(node.path("priority"), path + ".priority", MAX_REF_LENGTH, violations));
        if (StringUtils.hasText(priority) && !PRIORITIES.contains(priority)) {
            violations.add(path + ".priority 不支持: " + priority);
        }
        String description = optionalText(node.path("description"), path + ".description", MAX_TEXT_LENGTH, violations);
        String preconditions = optionalText(node.path("preconditions"), path + ".preconditions", MAX_TEXT_LENGTH, violations);
        String expectedResult = requiredText(node.path("expectedResult"), path + ".expectedResult", MAX_TEXT_LENGTH, violations);
        String requirementRef = optionalText(node.path("requirementRef"), path + ".requirementRef", MAX_REF_LENGTH, violations);
        List<String> apiRefs = optionalStringList(node.path("apiRefs"), path + ".apiRefs", MAX_REF_LENGTH, violations);
        List<String> pageRefs = optionalStringList(node.path("pageRefs"), path + ".pageRefs", MAX_REF_LENGTH, violations);
        List<String> flowRefs = optionalStringList(node.path("flowRefs"), path + ".flowRefs", MAX_REF_LENGTH, violations);
        List<String> tags = optionalStringList(node.path("tags"), path + ".tags", MAX_TAG_LENGTH, violations);
        String rationale = optionalText(node.path("rationale"), path + ".rationale", MAX_TEXT_LENGTH, violations);
        String riskNotes = optionalText(node.path("riskNotes"), path + ".riskNotes", MAX_TEXT_LENGTH, violations);
        double confidence = confidence(node.path("confidence"), path + ".confidence", violations);
        List<ModelGeneratedStep> steps = steps(node.path("steps"), path + ".steps", violations);
        if (!violations.isEmpty()) {
            throw schemaException(String.join("; ", violations));
        }
        return new ModelGeneratedCase(
                title,
                description,
                coverageType,
                priority,
                preconditions,
                steps,
                expectedResult,
                requirementRef,
                apiRefs,
                pageRefs,
                flowRefs,
                tags,
                rationale,
                riskNotes,
                confidence
        );
    }

    private List<ModelGeneratedStep> steps(JsonNode node, String path, List<String> violations) {
        if (!node.isArray()) {
            violations.add(path + " 必须是数组");
            return List.of();
        }
        require(node.size() >= MIN_STEP_COUNT, path + " 至少需要 " + MIN_STEP_COUNT + " 步", violations);
        require(node.size() <= MAX_STEP_COUNT, path + " 最多支持 " + MAX_STEP_COUNT + " 步", violations);
        List<ModelGeneratedStep> steps = new ArrayList<>();
        for (int index = 0; index < node.size(); index++) {
            String stepPath = path + "[" + index + "]";
            JsonNode stepNode = node.get(index);
            if (!stepNode.isObject()) {
                violations.add(stepPath + " 必须是对象");
                continue;
            }
            requireAllowedFields(stepNode, STEP_FIELDS, stepPath, violations);
            String action = requiredText(stepNode.path("action"), stepPath + ".action", MAX_TEXT_LENGTH, violations);
            String expectedResult = requiredText(stepNode.path("expectedResult"), stepPath + ".expectedResult",
                    MAX_TEXT_LENGTH, violations);
            steps.add(new ModelGeneratedStep(index, action, expectedResult));
        }
        return Collections.unmodifiableList(steps);
    }

    private String requiredText(JsonNode node, String path, int maxLength, List<String> violations) {
        String value = optionalText(node, path, maxLength, violations);
        require(StringUtils.hasText(value), path + " 不能为空", violations);
        return value;
    }

    private void validateOptionalText(JsonNode node, String path, int maxLength, List<String> violations) {
        optionalText(node, path, maxLength, violations);
    }

    private String optionalText(JsonNode node, String path, int maxLength, List<String> violations) {
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (!node.isTextual()) {
            violations.add(path + " 必须是字符串");
            return null;
        }
        String value = node.asText().trim();
        if (value.length() > maxLength) {
            violations.add(path + " 长度不能超过 " + maxLength);
        }
        if (StringUtils.hasText(value) && TestDesignSensitiveText.containsSensitiveText(value)) {
            violations.add(path + " 包含疑似敏感信息");
        }
        return value;
    }

    private List<String> optionalStringList(JsonNode node, String path, int maxLength, List<String> violations) {
        if (node.isMissingNode() || node.isNull()) {
            return List.of();
        }
        if (!node.isArray()) {
            violations.add(path + " 必须是数组");
            return List.of();
        }
        require(node.size() <= MAX_LIST_SIZE, path + " 最多支持 " + MAX_LIST_SIZE + " 条", violations);
        List<String> values = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int index = 0; index < node.size(); index++) {
            String itemPath = path + "[" + index + "]";
            String value = requiredText(node.get(index), itemPath, maxLength, violations);
            if (StringUtils.hasText(value) && !seen.add(value.toLowerCase(Locale.ROOT))) {
                violations.add(itemPath + " 重复: " + value);
            }
            if (StringUtils.hasText(value)) {
                values.add(value);
            }
        }
        return Collections.unmodifiableList(values);
    }

    private double confidence(JsonNode node, String path, List<String> violations) {
        if (node.isMissingNode() || node.isNull()) {
            return DEFAULT_CONFIDENCE;
        }
        if (!node.isNumber()) {
            violations.add(path + " 必须是数字");
            return DEFAULT_CONFIDENCE;
        }
        double value = node.asDouble();
        if (value < 0D || value > 1D) {
            violations.add(path + " 必须在 0 到 1 之间");
        }
        return value;
    }

    private void requireAllowedFields(JsonNode node, Set<String> allowedFields, String path, List<String> violations) {
        Iterator<String> fieldNames = node.fieldNames();
        while (fieldNames.hasNext()) {
            String fieldName = fieldNames.next();
            if (!allowedFields.contains(fieldName)) {
                violations.add(path + " 包含未知字段: " + fieldName);
            }
        }
    }

    private JsonNode readRoot(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException exception) {
            throw schemaException("模型输出 JSON 无法解析");
        }
    }

    private String extractJson(String content) {
        if (!StringUtils.hasText(content)) {
            return null;
        }
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```[a-zA-Z]*\\s*", "").replaceFirst("\\s*```$", "").trim();
        }
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        return start >= 0 && end > start ? trimmed.substring(start, end + 1) : null;
    }

    private static String normalizeEnum(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : value;
    }

    private static void require(boolean condition, String message, List<String> violations) {
        if (!condition) {
            violations.add(message);
        }
    }

    private static BusinessException schemaException(String message) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, "WP5 模型输出结构校验不通过: " + message);
    }

    public record ModelGeneratedCase(
            /** 模型生成的用例标题 */
            String title,
            /** 模型生成的用例说明 */
            String description,
            /** 覆盖类型编码 */
            String coverageType,
            /** 优先级编码 */
            String priority,
            /** 执行前置条件 */
            String preconditions,
            /** 用例步骤列表 */
            List<ModelGeneratedStep> steps,
            /** 用例整体预期结果 */
            String expectedResult,
            /** 需求引用编码或 ID */
            String requirementRef,
            /** 关联 API 引用列表 */
            List<String> apiRefs,
            /** 关联页面引用列表 */
            List<String> pageRefs,
            /** 关联业务流程引用列表 */
            List<String> flowRefs,
            /** 模型生成的标签列表 */
            List<String> tags,
            /** 生成理由或覆盖依据 */
            String rationale,
            /** 风险提示 */
            String riskNotes,
            /** 模型自评置信度，范围 0 到 1 */
            double confidence
    ) {
    }

    public record ModelGeneratedStep(
            /** 步骤序号，从 0 开始按模型输出顺序归一化 */
            int stepOrder,
            /** 步骤操作 */
            String action,
            /** 步骤预期结果 */
            String expectedResult
    ) {
    }
}
