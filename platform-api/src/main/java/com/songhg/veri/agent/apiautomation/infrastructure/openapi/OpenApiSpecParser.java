package com.songhg.veri.agent.apiautomation.infrastructure.openapi;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.songhg.veri.agent.apiautomation.application.parser.OpenApiParseResult;
import com.songhg.veri.agent.apiautomation.application.parser.ParsedOpenApiEndpoint;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Parses OpenAPI 3.x JSON/YAML into sanitized control-plane snapshots.
 */
@Component
public class OpenApiSpecParser {

    public static final String PARSER_VERSION = "wp6-openapi-parser-v1";
    private static final String MASKED = "***MASKED***";
    private static final int MAX_TEXT_CHARS = 4_096;
    private static final int MAX_TAGS = 8;
    private static final Set<String> HTTP_METHODS = Set.of(
            "get",
            "post",
            "put",
            "patch",
            "delete",
            "head",
            "options"
    );
    private static final List<String> SENSITIVE_NAME_FRAGMENTS = List.of(
            "authorization",
            "api_key",
            "apikey",
            "token",
            "cookie",
            "password",
            "secret",
            "credential"
    );

    private final ObjectMapper objectMapper;

    public OpenApiSpecParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public OpenApiParseResult parse(String content, int endpointMaxCount) {
        if (!StringUtils.hasText(content)) {
            throw parseFailure("OpenAPI 内容不能为空");
        }
        JsonNode root = parseTree(content);
        if (!root.isObject()) {
            throw parseFailure("OpenAPI 顶层必须是对象");
        }
        String openApiVersion = text(root.path("openapi"));
        if (!openApiVersion.startsWith("3.")) {
            throw parseFailure("仅支持 OpenAPI 3.x");
        }
        JsonNode paths = root.path("paths");
        if (!paths.isObject()) {
            throw parseFailure("OpenAPI paths 必须是对象");
        }

        JsonNode sanitized = sanitize(root, "");
        List<ParsedOpenApiEndpoint> endpoints = parseEndpoints(sanitized, endpointMaxCount);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("parserVersion", PARSER_VERSION);
        summary.put("openApiVersion", openApiVersion);
        summary.put("title", truncate(text(sanitized.path("info").path("title")), 128));
        summary.put("endpointCount", endpoints.size());
        summary.put("requestBodyStored", false);
        summary.put("responseBodyStored", false);
        summary.put("sensitiveExampleMasked", true);
        summary.put("aggregateOnly", true);
        return new OpenApiParseResult(writeJson(sanitized), summary, endpoints);
    }

    private List<ParsedOpenApiEndpoint> parseEndpoints(JsonNode root, int endpointMaxCount) {
        List<ParsedOpenApiEndpoint> endpoints = new ArrayList<>();
        String serviceName = serviceName(root);
        JsonNode paths = root.path("paths");
        Iterator<Map.Entry<String, JsonNode>> pathEntries = paths.fields();
        while (pathEntries.hasNext()) {
            Map.Entry<String, JsonNode> pathEntry = pathEntries.next();
            JsonNode pathItem = pathEntry.getValue();
            if (!pathItem.isObject()) {
                continue;
            }
            int pathParameterCount = countArray(pathItem.path("parameters"));
            Iterator<Map.Entry<String, JsonNode>> operationEntries = pathItem.fields();
            while (operationEntries.hasNext()) {
                Map.Entry<String, JsonNode> operationEntry = operationEntries.next();
                String method = operationEntry.getKey().toLowerCase(Locale.ROOT);
                if (!HTTP_METHODS.contains(method) || !operationEntry.getValue().isObject()) {
                    continue;
                }
                if (endpoints.size() >= endpointMaxCount) {
                    throw tooLarge("endpoint 数量超过上限: " + endpointMaxCount);
                }
                endpoints.add(endpoint(pathEntry.getKey(), pathParameterCount, method, operationEntry.getValue(), serviceName));
            }
        }
        if (endpoints.isEmpty()) {
            throw parseFailure("OpenAPI paths 中未发现可解析的 HTTP operation");
        }
        return List.copyOf(endpoints);
    }

    private ParsedOpenApiEndpoint endpoint(
            String path,
            int pathParameterCount,
            String method,
            JsonNode operation,
            String serviceName
    ) {
        List<String> tags = tags(operation.path("tags"));
        List<String> statuses = responseStatuses(operation.path("responses"));
        int parameterCount = pathParameterCount + countArray(operation.path("parameters"));
        Map<String, Object> digestSource = new LinkedHashMap<>();
        digestSource.put("method", method.toUpperCase(Locale.ROOT));
        digestSource.put("path", path);
        digestSource.put("operationId", text(operation.path("operationId")));
        digestSource.put("tags", tags);
        digestSource.put("parameters", operation.path("parameters"));
        digestSource.put("requestBody", operation.path("requestBody"));
        digestSource.put("responses", operation.path("responses"));
        return new ParsedOpenApiEndpoint(
                serviceName,
                truncate(text(operation.path("operationId")), 256),
                method.toUpperCase(Locale.ROOT),
                truncate(path, 512),
                truncate(text(operation.path("summary")), 512),
                tags,
                parameterCount,
                operation.has("requestBody"),
                statuses,
                sha256(writeJson(digestSource))
        );
    }

    private JsonNode parseTree(String content) {
        try {
            return objectMapper.readTree(content);
        } catch (JsonProcessingException jsonException) {
            try {
                LoaderOptions options = new LoaderOptions();
                options.setCodePointLimit(Math.max(content.length() * 2, 3_000_000));
                Object yaml = new Yaml(new SafeConstructor(options)).load(content);
                return objectMapper.valueToTree(yaml);
            } catch (RuntimeException yamlException) {
                throw parseFailure("OpenAPI JSON/YAML 解析失败");
            }
        }
    }

    private JsonNode sanitize(JsonNode node, String fieldName) {
        if (node == null || node.isNull()) {
            return JsonNodeFactory.instance.nullNode();
        }
        if (isSensitiveName(fieldName)) {
            return JsonNodeFactory.instance.textNode(MASKED);
        }
        if (node.isObject()) {
            ObjectNode objectNode = JsonNodeFactory.instance.objectNode();
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                objectNode.set(field.getKey(), sanitize(field.getValue(), field.getKey()));
            }
            return objectNode;
        }
        if (node.isArray()) {
            ArrayNode arrayNode = JsonNodeFactory.instance.arrayNode();
            node.forEach(item -> arrayNode.add(sanitize(item, fieldName)));
            return arrayNode;
        }
        if (node.isTextual()) {
            String value = node.asText();
            return JsonNodeFactory.instance.textNode(isSensitiveValue(value) ? MASKED : truncate(value, MAX_TEXT_CHARS));
        }
        return node.deepCopy();
    }

    private boolean isSensitiveName(String fieldName) {
        if (!StringUtils.hasText(fieldName)) {
            return false;
        }
        // OpenAPI path-item keys are route templates, not data field names. A path such as
        // /v1/credentials must remain an object so endpoint extraction can still run.
        if (fieldName.startsWith("/")) {
            return false;
        }
        String normalized = fieldName.replace("-", "_").toLowerCase(Locale.ROOT);
        return SENSITIVE_NAME_FRAGMENTS.stream().anyMatch(normalized::contains);
    }

    private boolean isSensitiveValue(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String trimmed = value.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.startsWith("bearer ") || lower.startsWith("basic ")) {
            return true;
        }
        if (lower.startsWith("token ") || lower.startsWith("apikey ")) {
            return true;
        }
        return (trimmed.startsWith("sk-") || trimmed.startsWith("pk-")) && trimmed.length() > 24;
    }

    private List<String> tags(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        List<String> tags = new ArrayList<>();
        node.forEach(item -> {
            String value = truncate(text(item), 64);
            if (StringUtils.hasText(value) && tags.size() < MAX_TAGS) {
                tags.add(value);
            }
        });
        return List.copyOf(tags);
    }

    private List<String> responseStatuses(JsonNode responses) {
        if (!responses.isObject()) {
            return List.of();
        }
        List<String> statuses = new ArrayList<>();
        responses.fieldNames().forEachRemaining(status -> statuses.add(truncate(status, 16)));
        return List.copyOf(statuses);
    }

    private String serviceName(JsonNode root) {
        String title = text(root.path("info").path("title"));
        return StringUtils.hasText(title) ? truncate(title, 128) : "openapi-service";
    }

    private int countArray(JsonNode node) {
        return node.isArray() ? node.size() : 0;
    }

    private String text(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        return node.isTextual() ? node.asText() : node.asText("");
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() <= maxLength) {
            return trimmed;
        }
        return trimmed.substring(0, maxLength);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "OpenAPI 摘要序列化失败");
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "SHA-256 不可用");
        }
    }

    private BusinessException parseFailure(String message) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, "OPENAPI_PARSE_FAILED: " + message);
    }

    private BusinessException tooLarge(String message) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, "OPENAPI_TOO_LARGE: " + message);
    }
}
