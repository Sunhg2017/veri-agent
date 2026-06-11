package com.songhg.veri.agent.apiautomation.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ApiAutomationModelOutputParser {

    static final String SCHEMA_VERSION = "wp6-api-automation-v1";

    private static final int MIN_CASE_COUNT = 1;
    private static final int MAX_CASE_COUNT = 100;
    private static final int MAX_TITLE_LENGTH = 256;
    private static final int MAX_PATH_LENGTH = 512;
    private static final int MAX_TEXT_LENGTH = 512;
    private static final int MAX_ASSERTION_LENGTH = 64;
    private static final int MAX_ASSERTION_COUNT = 20;
    private static final Set<String> HTTP_METHODS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS");
    private static final Set<String> COVERAGE_TYPES = Set.of("SMOKE", "FUNCTIONAL", "EXCEPTION");
    private static final Set<String> ROOT_FIELDS = Set.of("schemaVersion", "cases");
    private static final Set<String> CASE_FIELDS = Set.of(
            "assetApiId",
            "title",
            "method",
            "path",
            "coverageType",
            "expectedStatus",
            "assertions",
            "requestTemplate",
            "rationale"
    );
    private static final Set<String> REQUEST_TEMPLATE_FIELDS = Set.of(
            "aggregateOnly",
            "parameterCount",
            "pathParameterCount",
            "queryParameterCount",
            "headerCount",
            "requestBodyPresent",
            "bodyTemplateStored",
            "secretValuesStored",
            "endpointSnapshotId",
            "dataProfile",
            "negativeInputCategory",
            "notes"
    );
    private static final Set<String> FORBIDDEN_TEMPLATE_FIELD_NAMES = Set.of(
            "body",
            "rawbody",
            "payload",
            "example",
            "headers",
            "cookies",
            "authorization",
            "token",
            "secret",
            "password",
            "apikey"
    );
    private static final List<Pattern> SENSITIVE_TEXT_PATTERNS = List.of(
            Pattern.compile("(?i)\\bbearer\\s+[a-z0-9._\\-]{8,}"),
            Pattern.compile("(?i)\\b(api[_-]?key|secret|token|password|passwd|authorization)\\s*[:=]\\s*[^\\s,;，；]+"),
            Pattern.compile("(?i)\\b(sk|pk|rk)_[a-z0-9_-]{8,}\\b")
    );
    private static final Pattern ASSERTION_PATTERN = Pattern.compile("[A-Z0-9_.:-]{1,64}");
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    public ApiAutomationModelOutputParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Parses untrusted WP2 model text and returns only bounded, aggregate-safe case drafts for WP6 persistence.
     */
    public List<ModelGeneratedApiCase> parse(String rawOutput) {
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
        String schemaVersion = requiredText(root.path("schemaVersion"), "$.schemaVersion", MAX_TEXT_LENGTH, violations);
        if (StringUtils.hasText(schemaVersion) && !SCHEMA_VERSION.equals(schemaVersion)) {
            violations.add("$.schemaVersion 必须是 " + SCHEMA_VERSION);
        }
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
        List<ModelGeneratedApiCase> cases = new ArrayList<>();
        for (int index = 0; index < casesNode.size(); index++) {
            cases.add(parseCase(casesNode.get(index), index));
        }
        return Collections.unmodifiableList(cases);
    }

    private ModelGeneratedApiCase parseCase(JsonNode node, int index) {
        List<String> violations = new ArrayList<>();
        String path = "$.cases[" + index + "]";
        if (!node.isObject()) {
            throw schemaException(path + " 必须是对象");
        }
        requireAllowedFields(node, CASE_FIELDS, path, violations);
        UUID assetApiId = optionalUuid(node.path("assetApiId"), path + ".assetApiId", violations);
        String title = requiredText(node.path("title"), path + ".title", MAX_TITLE_LENGTH, violations);
        String method = normalizeEnum(requiredText(node.path("method"), path + ".method", MAX_TEXT_LENGTH, violations));
        if (StringUtils.hasText(method) && !HTTP_METHODS.contains(method)) {
            violations.add(path + ".method 不支持: " + method);
        }
        String apiPath = requiredText(node.path("path"), path + ".path", MAX_PATH_LENGTH, violations);
        if (StringUtils.hasText(apiPath) && (!apiPath.startsWith("/") || apiPath.contains("?"))) {
            violations.add(path + ".path 必须是 OpenAPI path，不允许查询串");
        }
        String coverageType = normalizeEnum(requiredText(node.path("coverageType"), path + ".coverageType",
                MAX_TEXT_LENGTH, violations));
        if (StringUtils.hasText(coverageType) && !COVERAGE_TYPES.contains(coverageType)) {
            violations.add(path + ".coverageType 不支持: " + coverageType);
        }
        int expectedStatus = expectedStatus(node.path("expectedStatus"), path + ".expectedStatus", violations);
        List<String> assertions = assertions(node.path("assertions"), path + ".assertions", violations);
        Map<String, Object> requestTemplate = requestTemplate(node.path("requestTemplate"), path + ".requestTemplate",
                violations);
        String rationale = optionalText(node.path("rationale"), path + ".rationale", MAX_TEXT_LENGTH, violations);
        if (!violations.isEmpty()) {
            throw schemaException(String.join("; ", violations));
        }
        return new ModelGeneratedApiCase(
                assetApiId,
                title,
                method,
                apiPath,
                coverageType,
                expectedStatus,
                assertions,
                requestTemplate,
                rationale
        );
    }

    private UUID optionalUuid(JsonNode node, String path, List<String> violations) {
        String value = optionalText(node, path, MAX_TEXT_LENGTH, violations);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            violations.add(path + " 必须是 UUID");
            return null;
        }
    }

    private int expectedStatus(JsonNode node, String path, List<String> violations) {
        if (!node.isInt()) {
            violations.add(path + " 必须是整数");
            return 0;
        }
        int value = node.asInt();
        if (value < 100 || value > 599) {
            violations.add(path + " 必须在 100-599 之间");
        }
        return value;
    }

    private List<String> assertions(JsonNode node, String path, List<String> violations) {
        if (!node.isArray()) {
            violations.add(path + " 必须是数组");
            return List.of();
        }
        require(node.size() >= 1, path + " 至少需要 1 条", violations);
        require(node.size() <= MAX_ASSERTION_COUNT, path + " 最多支持 " + MAX_ASSERTION_COUNT + " 条", violations);
        List<String> values = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int index = 0; index < node.size(); index++) {
            String itemPath = path + "[" + index + "]";
            String value = normalizeEnum(requiredText(node.get(index), itemPath, MAX_ASSERTION_LENGTH, violations));
            if (StringUtils.hasText(value) && !ASSERTION_PATTERN.matcher(value).matches()) {
                violations.add(itemPath + " 仅支持大写字母、数字、点、下划线、冒号和短横线");
            }
            if (StringUtils.hasText(value) && !seen.add(value)) {
                violations.add(itemPath + " 重复: " + value);
            }
            if (StringUtils.hasText(value)) {
                values.add(value);
            }
        }
        return Collections.unmodifiableList(values);
    }

    private Map<String, Object> requestTemplate(JsonNode node, String path, List<String> violations) {
        if (!node.isObject()) {
            violations.add(path + " 必须是对象");
            return Map.of();
        }
        requireAllowedFields(node, REQUEST_TEMPLATE_FIELDS, path, violations);
        Iterator<String> fieldNames = node.fieldNames();
        while (fieldNames.hasNext()) {
            String fieldName = fieldNames.next();
            String normalized = fieldName.toLowerCase(Locale.ROOT);
            if (FORBIDDEN_TEMPLATE_FIELD_NAMES.contains(normalized)) {
                violations.add(path + " 不允许包含原始请求字段: " + fieldName);
            }
        }
        if (!node.path("aggregateOnly").asBoolean(false)) {
            violations.add(path + ".aggregateOnly 必须为 true");
        }
        if (node.path("bodyTemplateStored").asBoolean(false)) {
            violations.add(path + ".bodyTemplateStored 必须为 false");
        }
        if (node.path("secretValuesStored").asBoolean(false)) {
            violations.add(path + ".secretValuesStored 必须为 false");
        }
        Map<String, Object> sanitized = new LinkedHashMap<>();
        try {
            Map<String, Object> raw = objectMapper.convertValue(node, MAP_TYPE);
            raw.forEach((key, value) -> {
                if (value instanceof String text) {
                    if (text.length() > MAX_TEXT_LENGTH || containsSensitiveText(text)) {
                        violations.add(path + "." + key + " 包含超长或疑似敏感文本");
                    } else {
                        sanitized.put(key, text);
                    }
                } else if (value instanceof Boolean || value instanceof Number) {
                    sanitized.put(key, value);
                } else if (value != null) {
                    violations.add(path + "." + key + " 仅支持字符串、数字或布尔值");
                }
            });
        } catch (IllegalArgumentException exception) {
            violations.add(path + " 无法转换为聚合模板");
        }
        sanitized.put("aggregateOnly", true);
        sanitized.put("bodyTemplateStored", false);
        sanitized.put("secretValuesStored", false);
        return Collections.unmodifiableMap(sanitized);
    }

    private String requiredText(JsonNode node, String path, int maxLength, List<String> violations) {
        String value = optionalText(node, path, maxLength, violations);
        require(StringUtils.hasText(value), path + " 不能为空", violations);
        return value;
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
        if (StringUtils.hasText(value) && containsSensitiveText(value)) {
            violations.add(path + " 包含疑似敏感信息");
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

    private static boolean containsSensitiveText(String value) {
        return SENSITIVE_TEXT_PATTERNS.stream().anyMatch(pattern -> pattern.matcher(value).find());
    }

    private static void require(boolean condition, String message, List<String> violations) {
        if (!condition) {
            violations.add(message);
        }
    }

    private static BusinessException schemaException(String message) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, "WP6 模型输出结构校验不通过: " + message);
    }

    public record ModelGeneratedApiCase(
            UUID assetApiId,
            String title,
            String method,
            String path,
            String coverageType,
            int expectedStatus,
            List<String> assertions,
            Map<String, Object> requestTemplate,
            String rationale
    ) {
    }
}
