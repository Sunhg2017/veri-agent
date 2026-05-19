package com.songhg.veri.agent.documentinput.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.documentinput.domain.DocumentFieldMapping;
import com.songhg.veri.agent.documentinput.domain.DocumentSourceType;
import com.songhg.veri.agent.documentinput.domain.ParsedRequirementDraft;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class DocumentRequirementParser {

    private static final Set<String> PRIORITIES = Set.of("CRITICAL", "HIGH", "MEDIUM", "LOW");
    private static final Pattern MARKDOWN_HEADING = Pattern.compile("^#{1,6}\\s+(.+)$");
    private static final Pattern PRIORITY_LINE = Pattern.compile("(?i)^(priority|优先级)\\s*[:：]\\s*(.+)$");
    private static final Pattern TAGS_LINE = Pattern.compile("(?i)^(tags|标签)\\s*[:：]\\s*(.+)$");
    private static final Pattern ACCEPTANCE_START = Pattern.compile("(?i)^(acceptance\\s*criteria|验收标准)\\s*[:：]?\\s*(.*)$");

    private final ObjectMapper objectMapper;

    public DocumentRequirementParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<ParsedRequirementDraft> parse(
            DocumentSourceType sourceType,
            String fallbackTitle,
            String content,
            DocumentFieldMapping mapping
    ) {
        if (!StringUtils.hasText(content)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "content 不能为空");
        }
        String trimmed = content.trim();
        if (looksLikeJson(trimmed)) {
            List<ParsedRequirementDraft> parsed = parseJson(trimmed, fallbackTitle, mapping);
            if (!parsed.isEmpty()) {
                return parsed;
            }
        }
        String textualContent = normalizeEscapedLineBreaks(trimmed);
        if (sourceType == DocumentSourceType.MARKDOWN || textualContent.contains("\n#")) {
            return parseMarkdown(fallbackTitle, textualContent);
        }
        return parsePlainText(fallbackTitle, textualContent);
    }

    private List<ParsedRequirementDraft> parseJson(
            String content,
            String fallbackTitle,
            DocumentFieldMapping mapping
    ) {
        try {
            JsonNode root = objectMapper.readTree(content);
            List<JsonNode> items = itemNodes(root, textOr(mapping.itemPath(), "requirements"));
            List<ParsedRequirementDraft> requirements = new ArrayList<>();
            for (JsonNode item : items) {
                String title = firstText(
                        textAt(item, mapping.titlePath()),
                        textAt(item, "title"),
                        textAt(item, "name"),
                        fallbackTitle
                );
                if (!StringUtils.hasText(title)) {
                    continue;
                }
                String acceptanceCriteria = firstText(
                        textAt(item, mapping.acceptanceCriteriaPath()),
                        textAt(item, "acceptanceCriteria"),
                        textAt(item, "acceptance_criteria")
                );
                requirements.add(new ParsedRequirementDraft(
                        title.trim(),
                        firstText(textAt(item, mapping.descriptionPath()), textAt(item, "description"), textAt(item, "content")),
                        normalizePriority(firstText(textAt(item, mapping.priorityPath()), textAt(item, "priority"))),
                        acceptanceCriteria,
                        normalizeTags(firstText(textAt(item, mapping.tagsPath()), textAt(item, "tags"))),
                        null
                ));
            }
            return requirements;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "JSON 文档内容无法解析");
        }
    }

    private List<ParsedRequirementDraft> parseMarkdown(String fallbackTitle, String content) {
        List<Section> sections = new ArrayList<>();
        Section current = null;
        for (String line : content.split("\\R")) {
            Matcher matcher = MARKDOWN_HEADING.matcher(line.trim());
            if (matcher.matches()) {
                if (current != null) {
                    sections.add(current);
                }
                current = new Section(matcher.group(1).trim(), new StringBuilder());
            } else if (current != null) {
                current.body().append(line).append('\n');
            }
        }
        if (current != null) {
            sections.add(current);
        }
        if (sections.isEmpty()) {
            return parsePlainText(fallbackTitle, content);
        }
        return sections.stream()
                .filter(section -> StringUtils.hasText(section.title()))
                .map(section -> draftFromText(section.title(), section.body().toString()))
                .toList();
    }

    private List<ParsedRequirementDraft> parsePlainText(String fallbackTitle, String content) {
        String[] parts = content.split("\\R", 2);
        String title = StringUtils.hasText(fallbackTitle) ? fallbackTitle.trim() : parts[0].trim();
        String body = parts.length > 1 ? parts[1].trim() : content;
        return List.of(draftFromText(title, body));
    }

    private ParsedRequirementDraft draftFromText(String title, String body) {
        return new ParsedRequirementDraft(
                title,
                stripMetadataLines(body),
                priorityFromLines(body),
                acceptanceCriteriaFromLines(body),
                tagsFromLines(body),
                null
        );
    }

    private List<JsonNode> itemNodes(JsonNode root, String itemPath) {
        List<JsonNode> configured = nodesAt(root, itemPath);
        if (!configured.isEmpty()) {
            return configured;
        }
        if (root.isArray()) {
            return elements(root);
        }
        for (String candidate : List.of("requirements", "items", "documents", "data")) {
            List<JsonNode> nodes = nodesAt(root, candidate);
            if (!nodes.isEmpty()) {
                return nodes;
            }
        }
        return List.of(root);
    }

    private List<JsonNode> nodesAt(JsonNode root, String path) {
        JsonNode node = nodeAt(root, path == null ? "" : path.replace("[]", ""));
        if (node == null || node.isMissingNode() || node.isNull()) {
            return List.of();
        }
        if (node.isArray()) {
            return elements(node);
        }
        return List.of(node);
    }

    private JsonNode nodeAt(JsonNode root, String path) {
        if (!StringUtils.hasText(path) || "$".equals(path.trim())) {
            return root;
        }
        String normalized = normalizePath(path);
        JsonNode current = root;
        for (String segment : normalized.split("\\.")) {
            if (!StringUtils.hasText(segment)) {
                continue;
            }
            current = current.path(segment.trim());
            if (current.isMissingNode() || current.isNull()) {
                return null;
            }
        }
        return current;
    }

    private String textAt(JsonNode node, String path) {
        JsonNode value = nodeAt(node, path);
        if (value == null || value.isMissingNode() || value.isNull()) {
            return null;
        }
        if (value.isArray()) {
            List<String> values = new ArrayList<>();
            Iterator<JsonNode> iterator = value.elements();
            while (iterator.hasNext()) {
                JsonNode item = iterator.next();
                if (!item.isNull()) {
                    values.add(item.asText());
                }
            }
            return String.join(",", values);
        }
        if (value.isObject()) {
            return value.toString();
        }
        return value.asText();
    }

    private List<JsonNode> elements(JsonNode node) {
        List<JsonNode> values = new ArrayList<>();
        node.elements().forEachRemaining(values::add);
        return values;
    }

    private String normalizePath(String path) {
        String value = path.trim();
        if (value.startsWith("$.")) {
            return value.substring(2);
        }
        if (value.startsWith("/")) {
            return value.substring(1).replace("/", ".");
        }
        return value;
    }

    private String priorityFromLines(String body) {
        for (String line : body.split("\\R")) {
            Matcher matcher = PRIORITY_LINE.matcher(cleanListPrefix(line));
            if (matcher.matches()) {
                return normalizePriority(matcher.group(2));
            }
        }
        return "MEDIUM";
    }

    private String acceptanceCriteriaFromLines(String body) {
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
        for (String line : body.split("\\R")) {
            Matcher matcher = TAGS_LINE.matcher(cleanListPrefix(line));
            if (matcher.matches()) {
                return normalizeTags(matcher.group(2));
            }
        }
        return null;
    }

    private String stripMetadataLines(String body) {
        List<String> lines = new ArrayList<>();
        boolean skippingAcceptance = false;
        for (String line : body.split("\\R")) {
            String cleaned = cleanListPrefix(line);
            if (PRIORITY_LINE.matcher(cleaned).matches() || TAGS_LINE.matcher(cleaned).matches()) {
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

    private String cleanListPrefix(String line) {
        return line.trim().replaceFirst("^[-*]\\s+", "");
    }

    private String normalizePriority(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return "MEDIUM";
        }
        String normalized = rawValue.trim().toUpperCase(Locale.ROOT);
        normalized = switch (normalized) {
            case "P0", "BLOCKER" -> "CRITICAL";
            case "P1", "IMPORTANT" -> "HIGH";
            case "P2", "NORMAL" -> "MEDIUM";
            case "P3", "MINOR" -> "LOW";
            default -> normalized;
        };
        return PRIORITIES.contains(normalized) ? normalized : "MEDIUM";
    }

    private String normalizeTags(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return null;
        }
        return rawValue.trim()
                .replace("，", ",")
                .replaceAll("\\s*,\\s*", ",");
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String textOr(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    private boolean looksLikeJson(String content) {
        return content.startsWith("{") || content.startsWith("[");
    }

    private String normalizeEscapedLineBreaks(String content) {
        return content
                .replace("\\r\\n", "\n")
                .replace("\\n", "\n")
                .replace("\\r", "\n");
    }

    private record Section(String title, StringBuilder body) {
    }
}
