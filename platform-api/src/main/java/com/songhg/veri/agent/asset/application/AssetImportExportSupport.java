package com.songhg.veri.agent.asset.application;

import static com.songhg.veri.agent.asset.application.AssetFormatValidator.API_HTTP_METHODS;
import static com.songhg.veri.agent.asset.application.AssetFormatValidator.FORMAT_CSV;
import static com.songhg.veri.agent.asset.application.AssetFormatValidator.FORMAT_OPENAPI;
import static com.songhg.veri.agent.asset.application.AssetFormatValidator.STATUS_ACTIVE;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.asset.application.command.CreateTestCaseRequest;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.common.util.CsvEncoder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.util.StringUtils;

/**
 * 导入导出公共格式支持
 *
 * <p>CSV/JSON/OpenAPI 解析、JSON 标准化、枚举/UUID 校验等跨资产类型逻辑集中在这里；
 * handler 不再复制格式细节，只保留资产本身的业务规则
 */
final class AssetImportExportSupport {

    private final AssetJsonCodec assetJsonCodec;
    private final ObjectMapper objectMapper;

    AssetImportExportSupport(ObjectMapper objectMapper) {
        this.assetJsonCodec = new AssetJsonCodec(objectMapper);
        this.objectMapper = objectMapper;
    }

    List<Map<String, String>> parseImportRows(String format, String content) {
        if (FORMAT_CSV.equals(format)) {
            return parseCsv(content);
        }
        if (FORMAT_OPENAPI.equals(format)) {
            return parseOpenApi(content);
        }
        try {
            JsonNode root = objectMapper.readTree(content);
            JsonNode rows = root.isArray() ? root : root.path("items");
            if (!rows.isArray()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "JSON 导入内容必须是数组或包含 items 数组");
            }
            List<Map<String, String>> result = new ArrayList<>();
            for (JsonNode item : rows) {
                result.add(flattenJsonObject(item));
            }
            return result;
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "JSON 导入内容格式不合法");
        }
    }

    Object jsonCommandValue(String rawValue) {
        try {
            return objectMapper.readTree(defaultJson(rawValue));
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "JSON 字段格式不合法");
        }
    }

    String jsonCommandString(String rawValue) {
        return jsonString(jsonCommandValue(rawValue));
    }

    List<CreateTestCaseRequest.StepDto> parseImportSteps(String rawSteps) {
        if (!StringUtils.hasText(rawSteps)) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(rawSteps);
            if (!root.isArray()) {
                return List.of(new CreateTestCaseRequest.StepDto(rawSteps, "待补充"));
            }
            List<CreateTestCaseRequest.StepDto> steps = new ArrayList<>();
            for (JsonNode item : root) {
                steps.add(new CreateTestCaseRequest.StepDto(
                        textOrDefault(item.path("action"), "待补充操作"),
                        textOrDefault(item.path("expectedResult"), "待补充预期")
                ));
            }
            return steps;
        } catch (JsonProcessingException exception) {
            return List.of(new CreateTestCaseRequest.StepDto(rawSteps, "待补充"));
        }
    }

    void validateJsonField(Map<String, String> row, String field, List<String> errors) {
        String value = trimToNull(rowValue(row, field));
        if (value == null) {
            return;
        }
        try {
            objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            errors.add(field + " 不是合法 JSON");
        }
    }

    JsonNode jsonNode(String json) {
        return assetJsonCodec.parseJson(json);
    }

    String jsonString(Object value) {
        return assetJsonCodec.toJsonString(value);
    }

    private List<Map<String, String>> parseOpenApi(String content) {
        try {
            JsonNode root = objectMapper.readTree(content);
            String apiVersion = trimToNull(textOrNull(root.path("info").path("version")));
            JsonNode paths = root.path("paths");
            if (!paths.isObject()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "OpenAPI 内容缺少 paths");
            }
            List<Map<String, String>> rows = new ArrayList<>();
            paths.fields().forEachRemaining(pathEntry -> pathEntry.getValue().fields().forEachRemaining(methodEntry -> {
                String method = methodEntry.getKey().toUpperCase(Locale.ROOT);
                if (!API_HTTP_METHODS.contains(method)) {
                    return;
                }
                JsonNode operation = methodEntry.getValue();
                Map<String, String> row = new LinkedHashMap<>();
                row.put("path", pathEntry.getKey());
                row.put("httpMethod", method);
                row.put("summary", textOrDefault(operation.path("summary"), method + " " + pathEntry.getKey()));
                row.put("description", textOrNull(operation.path("description")));
                row.put("status", STATUS_ACTIVE);
                row.put("source", FORMAT_OPENAPI);
                row.put("sourceRef", openApiSourceRef(pathEntry.getKey(), method));
                row.put("version", apiVersion);
                row.put("requestSchema", openApiRequestSchema(operation));
                row.put("responseSchema", openApiResponseSchema(operation));
                rows.add(row);
            }));
            return rows;
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "OpenAPI 导入内容格式不合法");
        }
    }

    private Map<String, String> flattenJsonObject(JsonNode item) {
        if (!item.isObject()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "JSON 导入数组元素必须是对象");
        }
        Map<String, String> row = new LinkedHashMap<>();
        item.fields().forEachRemaining(entry -> row.put(
                entry.getKey(),
                entry.getValue().isContainerNode() ? jsonString(entry.getValue()) : textOrNull(entry.getValue())
        ));
        return row;
    }

    private String openApiRequestSchema(JsonNode operation) {
        JsonNode schema = operation.path("requestBody").path("content").path("application/json").path("schema");
        return schema.isMissingNode() ? "{}" : jsonString(schema);
    }

    private String openApiResponseSchema(JsonNode operation) {
        JsonNode responses = operation.path("responses");
        if (!responses.isObject()) {
            return "{}";
        }
        for (String code : List.of("200", "201", "202", "default")) {
            String schema = openApiResponseSchemaByCode(responses.path(code));
            if (schema != null) {
                return schema;
            }
        }
        var fields = responses.fields();
        while (fields.hasNext()) {
            String schema = openApiResponseSchemaByCode(fields.next().getValue());
            if (schema != null) {
                return schema;
            }
        }
        return "{}";
    }

    private String openApiResponseSchemaByCode(JsonNode response) {
        JsonNode schema = response.path("content").path("application/json").path("schema");
        return schema.isMissingNode() ? null : jsonString(schema);
    }

    static List<Map<String, String>> parseCsv(String content) {
        List<String> lines = content.lines()
                .filter(StringUtils::hasText)
                .toList();
        if (lines.isEmpty()) {
            return List.of();
        }
        List<String> headers = splitCsvLine(lines.getFirst()).stream()
                .map(String::trim)
                .toList();
        List<Map<String, String>> rows = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            List<String> values = splitCsvLine(lines.get(i));
            Map<String, String> row = new LinkedHashMap<>();
            for (int column = 0; column < headers.size(); column++) {
                row.put(headers.get(column), column < values.size() ? trimToNull(values.get(column)) : null);
            }
            rows.add(row);
        }
        return rows;
    }

    static List<String> splitCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (c == ',' && !quoted) {
                values.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        values.add(current.toString());
        return values;
    }

    static void requireImportField(Map<String, String> row, String field, List<String> errors) {
        if (!StringUtils.hasText(rowValue(row, field))) {
            errors.add(field + " 不能为空");
        }
    }

    static void validateImportEnum(
            Map<String, String> row,
            String field,
            Set<String> allowedValues,
            List<String> errors
    ) {
        String value = trimToNull(rowValue(row, field));
        if (value != null && !allowedValues.contains(value.toUpperCase(Locale.ROOT))) {
            errors.add(field + " 不合法: " + value);
        }
    }

    static void validateUuidField(Map<String, String> row, String field, List<String> errors) {
        String value = trimToNull(rowValue(row, field));
        if (value == null) {
            return;
        }
        try {
            UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            errors.add(field + " 不是合法 UUID");
        }
    }

    static String rowValue(Map<String, String> row, String field) {
        if (row.containsKey(field)) {
            return row.get(field);
        }
        for (Map.Entry<String, String> entry : row.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(field)) {
                return entry.getValue();
            }
        }
        return null;
    }

    static UUID uuidOrNull(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : UUID.fromString(trimmed);
    }

    static String defaultJson(String value) {
        return StringUtils.hasText(value) ? value : "{}";
    }

    static String valueIn(String rawValue, String defaultValue, Set<String> allowedValues, String fieldName) {
        String value = StringUtils.hasText(rawValue) ? rawValue.trim().toUpperCase(Locale.ROOT) : defaultValue;
        if (!allowedValues.contains(value)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, fieldName + " 不合法: " + rawValue);
        }
        return value;
    }

    static String textOrNull(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? null : node.asText();
    }

    static String textOrDefault(JsonNode node, String defaultValue) {
        String value = textOrNull(node);
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    static String assetCode(String prefix, UUID id) {
        return prefix + "-" + id.toString().replace("-", "").substring(0, 12);
    }

    static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    static String mergeTags(String existing, String incoming) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        addTags(tags, existing);
        addTags(tags, incoming);
        return tags.isEmpty() ? null : String.join(",", tags);
    }

    static String normalizedTags(String rawTags) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        addTags(tags, rawTags);
        return String.join(",", tags);
    }

    static void appendCsvLine(StringBuilder csv, Object... values) {
        CsvEncoder.appendLine(csv, values);
    }

    static boolean hasUsefulSchema(String schema) {
        return StringUtils.hasText(schema) && !"{}".equals(schema.trim());
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }

    private static String openApiSourceRef(String path, String method) {
        return "#/paths/" + openApiPointerSegment(path) + "/" + method.toLowerCase(Locale.ROOT);
    }

    private static String openApiPointerSegment(String value) {
        return value.replace("~", "~0").replace("/", "~1");
    }

    private static void addTags(LinkedHashSet<String> tags, String rawTags) {
        if (!StringUtils.hasText(rawTags)) {
            return;
        }
        for (String tag : rawTags.replace("，", ",").split(",")) {
            String trimmed = tag.trim();
            if (StringUtils.hasText(trimmed)) {
                tags.add(trimmed);
            }
        }
    }
}
