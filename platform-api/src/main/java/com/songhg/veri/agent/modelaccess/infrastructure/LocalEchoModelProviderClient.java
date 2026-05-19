package com.songhg.veri.agent.modelaccess.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.modelaccess.application.ModelProviderClient;
import com.songhg.veri.agent.modelaccess.application.ProviderCallRequest;
import com.songhg.veri.agent.modelaccess.application.ProviderCallResult;
import com.songhg.veri.agent.modelaccess.domain.ModelProviderConfig;
import com.songhg.veri.agent.modelaccess.domain.ProviderType;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class LocalEchoModelProviderClient implements ModelProviderClient {

    private static final String WP4_PARSE_MARKER = "WP4_REQUIREMENT_EXTRACTION_V1";
    private static final Pattern MARKDOWN_HEADING = Pattern.compile("^#{1,6}\\s+(.+)$");
    private static final Pattern PRIORITY_LINE = Pattern.compile("(?i)^(priority|优先级)\\s*[:：]\\s*(.+)$");
    private static final Pattern ACCEPTANCE_START = Pattern.compile("(?i)^(acceptance\\s*criteria|验收标准)\\s*[:：]?\\s*(.*)$");

    private final ObjectMapper objectMapper;

    public LocalEchoModelProviderClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(ModelProviderConfig provider) {
        return provider.providerType() == ProviderType.LOCAL_ECHO;
    }

    @Override
    public ProviderCallResult call(ModelProviderConfig provider, ProviderCallRequest request) {
        if (containsWp4ParseMarker(request)) {
            String content = wp4RequirementParseResponse(request.messageText());
            int inputTokens = estimateTokens(request.prompt()) + estimateTokens(request.messageText());
            int outputTokens = estimateTokens(content);
            return new ProviderCallResult(content, inputTokens, outputTokens);
        }
        String content = "local model response: " + firstNonBlank(request.messageText(), request.prompt());
        int inputTokens = estimateTokens(request.prompt()) + estimateTokens(request.messageText());
        int outputTokens = estimateTokens(content);
        return new ProviderCallResult(content, inputTokens, outputTokens);
    }

    private boolean containsWp4ParseMarker(ProviderCallRequest request) {
        return contains(request.prompt(), WP4_PARSE_MARKER) || contains(request.messageText(), WP4_PARSE_MARKER);
    }

    private String wp4RequirementParseResponse(String messageText) {
        try {
            JsonNode payload = objectMapper.readTree(stripRolePrefix(messageText));
            String content = text(payload, "content");
            String fallbackTitle = text(payload, "title");
            List<Map<String, Object>> requirements = parseContent(content, fallbackTitle);
            return objectMapper.writeValueAsString(Map.of("requirements", requirements));
        } catch (Exception exception) {
            try {
                return objectMapper.writeValueAsString(Map.of("requirements", List.of()));
            } catch (Exception ignored) {
                return "{\"requirements\":[]}";
            }
        }
    }

    private String stripRolePrefix(String messageText) {
        if (messageText == null) {
            return "";
        }
        String trimmed = messageText.trim();
        return trimmed.startsWith("user: ") ? trimmed.substring("user: ".length()).trim() : trimmed;
    }

    private List<Map<String, Object>> parseContent(String content, String fallbackTitle) {
        if (!StringUtils.hasText(content)) {
            return List.of();
        }
        String trimmed = normalizeEscapedLineBreaks(content.trim());
        List<Map<String, Object>> jsonRequirements = parseJsonRequirements(trimmed);
        if (!jsonRequirements.isEmpty()) {
            return jsonRequirements;
        }
        List<Map<String, Object>> markdownRequirements = parseMarkdownRequirements(trimmed);
        if (!markdownRequirements.isEmpty()) {
            return markdownRequirements;
        }
        String[] parts = trimmed.split("\\R", 2);
        String title = StringUtils.hasText(fallbackTitle) ? fallbackTitle.trim() : parts[0].trim();
        String body = parts.length > 1 ? parts[1].trim() : trimmed;
        return List.of(requirement(title, body));
    }

    private List<Map<String, Object>> parseJsonRequirements(String content) {
        if (!content.startsWith("{") && !content.startsWith("[")) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(content);
            JsonNode items = root.isArray() ? root : root.path("requirements");
            if (!items.isArray()) {
                items = root.path("items");
            }
            if (!items.isArray()) {
                return root.path("title").isMissingNode() ? List.of() : List.of(requirementFromJson(root));
            }
            List<Map<String, Object>> requirements = new ArrayList<>();
            items.forEach(item -> {
                if (StringUtils.hasText(text(item, "title")) || StringUtils.hasText(text(item, "name"))) {
                    requirements.add(requirementFromJson(item));
                }
            });
            return requirements;
        } catch (Exception exception) {
            return List.of();
        }
    }

    private Map<String, Object> requirementFromJson(JsonNode item) {
        String title = firstText(text(item, "title"), text(item, "name"));
        String description = firstText(text(item, "description"), text(item, "detail"), text(item, "content"));
        String priority = firstText(text(item, "priority"), text(item, "level"));
        String acceptanceCriteria = firstText(text(item, "acceptanceCriteria"), text(item, "acceptance_criteria"), text(item, "checks"));
        return requirement(title, description, priority, acceptanceCriteria, text(item, "tags"), text(item, "labels"));
    }

    private List<Map<String, Object>> parseMarkdownRequirements(String content) {
        List<Map<String, Object>> requirements = new ArrayList<>();
        String currentTitle = null;
        StringBuilder body = new StringBuilder();
        for (String line : content.split("\\R")) {
            Matcher matcher = MARKDOWN_HEADING.matcher(line.trim());
            if (matcher.matches()) {
                if (StringUtils.hasText(currentTitle)) {
                    requirements.add(requirement(currentTitle, body.toString()));
                }
                currentTitle = matcher.group(1).trim();
                body = new StringBuilder();
            } else if (currentTitle != null) {
                body.append(line).append('\n');
            }
        }
        if (StringUtils.hasText(currentTitle)) {
            requirements.add(requirement(currentTitle, body.toString()));
        }
        return requirements;
    }

    private Map<String, Object> requirement(String title, String body) {
        return requirement(title, stripMetadataLines(body), priorityFromLines(body), acceptanceCriteriaFromLines(body), tagsFromLines(body), null);
    }

    private Map<String, Object> requirement(
            String title,
            String description,
            String priority,
            String acceptanceCriteria,
            String rawTags,
            String fallbackTags
    ) {
        return Map.of(
                "title", StringUtils.hasText(title) ? title.trim() : "未命名需求",
                "description", safe(description),
                "priority", normalizePriority(priority),
                "acceptanceCriteria", safe(acceptanceCriteria),
                "tags", tags(rawTags, fallbackTags),
                "confidence", 0.86
        );
    }

    private String priorityFromLines(String body) {
        if (body == null) {
            return "MEDIUM";
        }
        for (String line : body.split("\\R")) {
            Matcher matcher = PRIORITY_LINE.matcher(cleanListPrefix(line));
            if (matcher.matches()) {
                return matcher.group(2);
            }
        }
        return "MEDIUM";
    }

    private String acceptanceCriteriaFromLines(String body) {
        if (body == null) {
            return null;
        }
        StringBuilder criteria = new StringBuilder();
        boolean collecting = false;
        for (String line : body.split("\\R")) {
            String cleaned = cleanListPrefix(line);
            Matcher matcher = ACCEPTANCE_START.matcher(cleaned);
            if (matcher.matches()) {
                collecting = true;
                if (StringUtils.hasText(matcher.group(2))) {
                    criteria.append(matcher.group(2).trim()).append('\n');
                }
                continue;
            }
            if (collecting && StringUtils.hasText(cleaned)) {
                criteria.append(cleaned).append('\n');
            } else if (collecting) {
                break;
            }
        }
        return StringUtils.hasText(criteria.toString()) ? criteria.toString().trim() : null;
    }

    private String tagsFromLines(String body) {
        if (body == null) {
            return null;
        }
        for (String line : body.split("\\R")) {
            String cleaned = cleanListPrefix(line);
            if (cleaned.toLowerCase(Locale.ROOT).startsWith("tags:") || cleaned.startsWith("标签:") || cleaned.startsWith("标签：")) {
                return cleaned.replaceFirst("(?i)^tags\\s*[:：]\\s*", "").replaceFirst("^标签\\s*[:：]\\s*", "");
            }
        }
        return null;
    }

    private String stripMetadataLines(String body) {
        if (body == null) {
            return "";
        }
        List<String> lines = new ArrayList<>();
        boolean skippingAcceptance = false;
        for (String line : body.split("\\R")) {
            String cleaned = cleanListPrefix(line);
            if (PRIORITY_LINE.matcher(cleaned).matches()
                    || cleaned.toLowerCase(Locale.ROOT).startsWith("tags:")
                    || cleaned.startsWith("标签:") || cleaned.startsWith("标签：")) {
                continue;
            }
            if (ACCEPTANCE_START.matcher(cleaned).matches()) {
                skippingAcceptance = true;
                continue;
            }
            if (skippingAcceptance && StringUtils.hasText(cleaned)) {
                continue;
            }
            skippingAcceptance = false;
            if (StringUtils.hasText(line)) {
                lines.add(line.trim());
            }
        }
        return String.join("\n", lines);
    }

    private List<String> tags(String rawTags, String fallbackTags) {
        List<String> values = new ArrayList<>();
        addTags(values, rawTags);
        addTags(values, fallbackTags);
        addTags(values, "ai-assisted");
        return values;
    }

    private void addTags(List<String> values, String rawTags) {
        if (!StringUtils.hasText(rawTags)) {
            return;
        }
        for (String tag : rawTags.replace("，", ",").split(",")) {
            String trimmed = tag.trim();
            if (StringUtils.hasText(trimmed) && !values.contains(trimmed)) {
                values.add(trimmed);
            }
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        if (value.isArray()) {
            List<String> values = new ArrayList<>();
            value.forEach(item -> values.add(item.asText()));
            return String.join(",", values);
        }
        return value.asText();
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String normalizePriority(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return "MEDIUM";
        }
        String normalized = rawValue.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "P0", "BLOCKER", "CRITICAL" -> "CRITICAL";
            case "P1", "IMPORTANT", "HIGH" -> "HIGH";
            case "P3", "MINOR", "LOW" -> "LOW";
            default -> "MEDIUM";
        };
    }

    private String cleanListPrefix(String line) {
        return line.trim().replaceFirst("^[-*]\\s+", "");
    }

    private String normalizeEscapedLineBreaks(String content) {
        return content
                .replace("\\r\\n", "\n")
                .replace("\\n", "\n")
                .replace("\\r", "\n");
    }

    private boolean contains(String value, String expected) {
        return value != null && value.contains(expected);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.length() > 500 ? first.substring(0, 500) : first;
        }
        if (second == null) {
            return "";
        }
        return second.length() > 500 ? second.substring(0, 500) : second;
    }

    private int estimateTokens(String content) {
        if (content == null || content.isBlank()) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(content.length() / 4.0));
    }
}
