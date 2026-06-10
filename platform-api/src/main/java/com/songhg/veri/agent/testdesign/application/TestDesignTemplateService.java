package com.songhg.veri.agent.testdesign.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.testdesign.application.command.CreateTestDesignTemplateCommand;
import com.songhg.veri.agent.testdesign.application.command.UpdateTestDesignTemplateCommand;
import com.songhg.veri.agent.testdesign.application.port.TestDesignRepository;
import com.songhg.veri.agent.testdesign.application.query.TestDesignTemplateQuery;
import com.songhg.veri.agent.testdesign.application.view.TestDesignTemplateResponse;
import com.songhg.veri.agent.testdesign.config.TestDesignProperties;
import com.songhg.veri.agent.testdesign.domain.CoverageType;
import com.songhg.veri.agent.testdesign.domain.TestDesignTemplate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Manages WP5 generation templates and resolves them into task creation defaults.
 *
 * <p>Templates are intentionally configuration-only. The service accepts Prompt identifiers, coverage types, bounded
 * case count and context asset identifiers, but rejects arbitrary context fields so template management cannot become
 * a side channel for storing raw Prompt text, document excerpts or model payloads.</p>
 */
@Service
public class TestDesignTemplateService {

    private static final List<String> DEFAULT_COVERAGE_TYPES = List.of("SMOKE", "FUNCTIONAL", "EXCEPTION");
    private static final String GENERATION_STRATEGY_BALANCED = "BALANCED";
    private static final String COVERAGE_STRATEGY_DEFAULT_ORDER = "DEFAULT_ORDER";
    private static final List<String> ALLOWED_GENERATION_STRATEGIES = List.of(
            GENERATION_STRATEGY_BALANCED,
            "RISK_FIRST",
            "COMPLIANCE",
            "EXPLORATORY"
    );
    private static final List<String> ALLOWED_COVERAGE_STRATEGIES = List.of(
            COVERAGE_STRATEGY_DEFAULT_ORDER,
            "SMOKE_FIRST",
            "RISK_FIRST",
            "REGRESSION_HEAVY",
            "SECURITY_PERMISSION"
    );
    private static final Pattern CODE_PATTERN = Pattern.compile("[A-Za-z0-9_.:-]+");
    private static final int MAX_NAME_LENGTH = 128;
    private static final int MAX_DESCRIPTION_LENGTH = 512;
    private static final int MAX_PROMPT_KEY_LENGTH = 128;
    private static final int MAX_PROMPT_VERSION_LENGTH = 64;
    private static final int MAX_CONTEXT_DEFAULT_IDS = 50;
    private static final List<String> ALLOWED_CONTEXT_DEFAULT_KEYS = List.of(
            "environmentKey",
            "contextApiIds",
            "contextPageIds",
            "contextFlowIds"
    );

    private final TestDesignRepository repository;
    private final TestDesignPlatformContextClient contextClient;
    private final TestDesignActorResolver actorResolver;
    private final TestDesignProperties properties;
    private final ObjectMapper objectMapper;

    public TestDesignTemplateService(
            TestDesignRepository repository,
            TestDesignPlatformContextClient contextClient,
            TestDesignActorResolver actorResolver,
            TestDesignProperties properties,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.contextClient = contextClient;
        this.actorResolver = actorResolver;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public PageResponse<TestDesignTemplateResponse> templates(TestDesignTemplateQuery query) {
        TestDesignTemplateQuery normalizedQuery = normalizedQuery(query);
        List<TestDesignTemplateResponse> items = repository.templates(normalizedQuery).stream()
                .map(this::toResponse)
                .toList();
        return PageResponse.of(items, normalizedQuery.index(), normalizedQuery.size(),
                repository.countTemplates(normalizedQuery));
    }

    @Transactional(readOnly = true)
    public TestDesignTemplateResponse template(UUID id) {
        return toResponse(templateOrThrow(id));
    }

    @Transactional
    public TestDesignTemplateResponse createTemplate(CreateTestDesignTemplateCommand command) {
        String projectId = scopedProjectId(command.projectId());
        TemplatePayload payload = templatePayload(command);
        ensureUniqueName(null, projectId, payload.name());
        Instant now = Instant.now();
        String actor = actorResolver.currentActor();
        TestDesignTemplate template = new TestDesignTemplate(
                UUID.randomUUID(),
                projectId,
                payload.name(),
                payload.description(),
                payload.promptKey(),
                payload.promptVersion(),
                String.join(",", payload.coverageTypes()),
                payload.generationStrategy(),
                payload.coverageStrategy(),
                payload.caseCountPerRequirement(),
                contextDefaultsJson(payload.contextDefaults()),
                payload.enabled(),
                actor,
                actor,
                now,
                now
        );
        TestDesignTemplate saved = repository.saveTemplate(template);
        writeAudit("TEMPLATE_CREATE", saved);
        return toResponse(saved);
    }

    @Transactional
    public TestDesignTemplateResponse updateTemplate(UUID id, UpdateTestDesignTemplateCommand command) {
        TestDesignTemplate existing = templateOrThrow(id);
        TemplatePayload payload = templatePayload(command);
        ensureUniqueName(existing.id(), existing.projectId(), payload.name());
        TestDesignTemplate updated = new TestDesignTemplate(
                existing.id(),
                existing.projectId(),
                payload.name(),
                payload.description(),
                payload.promptKey(),
                payload.promptVersion(),
                String.join(",", payload.coverageTypes()),
                payload.generationStrategy(),
                payload.coverageStrategy(),
                payload.caseCountPerRequirement(),
                contextDefaultsJson(payload.contextDefaults()),
                payload.enabled(),
                existing.createdBy(),
                actorResolver.currentActor(),
                existing.createdAt(),
                Instant.now()
        );
        TestDesignTemplate saved = repository.saveTemplate(updated);
        writeAudit("TEMPLATE_UPDATE", saved);
        return toResponse(saved);
    }

    @Transactional
    public TestDesignTemplateResponse disableTemplate(UUID id) {
        TestDesignTemplate existing = templateOrThrow(id);
        if (!existing.enabled()) {
            return toResponse(existing);
        }
        TestDesignTemplate disabled = new TestDesignTemplate(
                existing.id(),
                existing.projectId(),
                existing.name(),
                existing.description(),
                existing.promptKey(),
                existing.promptVersion(),
                existing.coverageTypes(),
                existing.generationStrategy(),
                existing.coverageStrategy(),
                existing.caseCountPerRequirement(),
                existing.contextDefaultsJson(),
                false,
                existing.createdBy(),
                actorResolver.currentActor(),
                existing.createdAt(),
                Instant.now()
        );
        TestDesignTemplate saved = repository.saveTemplate(disabled);
        writeAudit("TEMPLATE_DISABLE", saved);
        return toResponse(saved);
    }

    /**
     * Resolves an enabled template for task creation after enforcing project/global scope compatibility.
     */
    @Transactional(readOnly = true)
    public Optional<TaskTemplateDefaults> taskDefaults(UUID templateId, String projectId) {
        if (templateId == null) {
            return Optional.empty();
        }
        TestDesignTemplate template = templateOrThrow(templateId);
        if (!template.enabled()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "生成模板已禁用: " + template.name());
        }
        if (StringUtils.hasText(template.projectId()) && !Objects.equals(template.projectId(), projectId)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "生成模板不属于当前项目");
        }
        Map<String, Object> defaults = contextDefaults(template.contextDefaultsJson());
        return Optional.of(new TaskTemplateDefaults(
                template.id(),
                template.name(),
                template.promptKey(),
                template.promptVersion(),
                normalizedCoverageTypes(csvValues(template.coverageTypes()),
                        template.generationStrategy(), template.coverageStrategy()),
                template.generationStrategy(),
                template.coverageStrategy(),
                template.caseCountPerRequirement(),
                stringValue(defaults.get("environmentKey")),
                uuidList(defaults.get("contextApiIds"), "contextApiIds"),
                uuidList(defaults.get("contextPageIds"), "contextPageIds"),
                uuidList(defaults.get("contextFlowIds"), "contextFlowIds")
        ));
    }

    private TestDesignTemplateQuery normalizedQuery(TestDesignTemplateQuery query) {
        if (query == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "模板查询参数不能为空");
        }
        String projectId = trimToNull(query.projectId());
        if (projectId != null) {
            projectId = contextClient.projectContext(projectId).resourceId();
        }
        return new TestDesignTemplateQuery(
                projectId,
                query.enabled(),
                trimToNull(query.keyword()),
                query.includeGlobal(),
                query.page()
        );
    }

    private TemplatePayload templatePayload(CreateTestDesignTemplateCommand command) {
        String generationStrategy = normalizedGenerationStrategy(command.generationStrategy());
        String coverageStrategy = normalizedCoverageStrategy(command.coverageStrategy());
        return new TemplatePayload(
                normalizedName(command.name()),
                normalizedDescription(command.description()),
                normalizedCode(command.promptKey(), properties.promptKey(), "promptKey", MAX_PROMPT_KEY_LENGTH),
                normalizedCode(command.promptVersion(), properties.promptVersion(), "promptVersion",
                        MAX_PROMPT_VERSION_LENGTH),
                normalizedCoverageTypes(command.coverageTypes(), generationStrategy, coverageStrategy),
                generationStrategy,
                coverageStrategy,
                normalizedCaseCount(command.caseCountPerRequirement()),
                normalizedContextDefaults(command.contextDefaults()),
                command.enabled() == null || command.enabled()
        );
    }

    private TemplatePayload templatePayload(UpdateTestDesignTemplateCommand command) {
        String generationStrategy = normalizedGenerationStrategy(command.generationStrategy());
        String coverageStrategy = normalizedCoverageStrategy(command.coverageStrategy());
        return new TemplatePayload(
                normalizedName(command.name()),
                normalizedDescription(command.description()),
                normalizedCode(command.promptKey(), properties.promptKey(), "promptKey", MAX_PROMPT_KEY_LENGTH),
                normalizedCode(command.promptVersion(), properties.promptVersion(), "promptVersion",
                        MAX_PROMPT_VERSION_LENGTH),
                normalizedCoverageTypes(command.coverageTypes(), generationStrategy, coverageStrategy),
                generationStrategy,
                coverageStrategy,
                normalizedCaseCount(command.caseCountPerRequirement()),
                normalizedContextDefaults(command.contextDefaults()),
                command.enabled() == null || command.enabled()
        );
    }

    private void ensureUniqueName(UUID currentId, String projectId, String name) {
        Optional<TestDesignTemplate> existing = repository.templateByScopeAndName(projectId, name);
        if (existing.isPresent() && !Objects.equals(existing.get().id(), currentId)) {
            throw new BusinessException(ErrorCode.CONFLICT, "同一作用域下已存在同名生成模板");
        }
    }

    private String scopedProjectId(String projectId) {
        return StringUtils.hasText(projectId) ? contextClient.projectContext(projectId).resourceId() : null;
    }

    private String normalizedName(String value) {
        String normalized = requiredText(value, "name");
        validateLength(normalized, "name", MAX_NAME_LENGTH);
        rejectSensitive(normalized, "name");
        return normalized;
    }

    private String normalizedDescription(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        validateLength(normalized, "description", MAX_DESCRIPTION_LENGTH);
        rejectSensitive(normalized, "description");
        return normalized;
    }

    private String normalizedCode(String value, String fallback, String fieldName, int maxLength) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            normalized = requiredText(fallback, fieldName);
        }
        validateLength(normalized, fieldName, maxLength);
        rejectSensitive(normalized, fieldName);
        if (!CODE_PATTERN.matcher(normalized).matches()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, fieldName + " 仅支持字母、数字、点、冒号、下划线和连字符");
        }
        return normalized;
    }

    private int normalizedCaseCount(Integer value) {
        int max = Math.max(1, properties.maxCasesPerRequirement());
        if (value == null || value <= 0) {
            return max;
        }
        if (value > max) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "caseCountPerRequirement 不能大于 " + max);
        }
        return value;
    }

    private String normalizedGenerationStrategy(String value) {
        return normalizedStrategy(
                value,
                GENERATION_STRATEGY_BALANCED,
                "generationStrategy",
                ALLOWED_GENERATION_STRATEGIES
        );
    }

    private String normalizedCoverageStrategy(String value) {
        return normalizedStrategy(
                value,
                COVERAGE_STRATEGY_DEFAULT_ORDER,
                "coverageStrategy",
                ALLOWED_COVERAGE_STRATEGIES
        );
    }

    private String normalizedStrategy(String value, String fallback, String fieldName, List<String> allowedValues) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            normalized = fallback;
        }
        normalized = normalized.toUpperCase(Locale.ROOT);
        rejectSensitive(normalized, fieldName);
        if (!allowedValues.contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    fieldName + " 不支持: " + value + "，可选值: " + String.join(",", allowedValues));
        }
        return normalized;
    }

    private List<String> normalizedCoverageTypes(
            List<String> requestedTypes,
            String generationStrategy,
            String coverageStrategy
    ) {
        List<String> source = requestedTypes == null || requestedTypes.isEmpty()
                ? defaultCoverageTypes(generationStrategy)
                : requestedTypes;
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String requestedType : source) {
            String normalized = trimToNull(requestedType);
            if (normalized == null) {
                continue;
            }
            normalized = normalized.toUpperCase(Locale.ROOT);
            if (!CoverageType.codes().contains(normalized)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "不支持的覆盖类型: " + requestedType);
            }
            result.add(normalized);
        }
        if (result.isEmpty()) {
            return orderedCoverageTypes(defaultCoverageTypes(generationStrategy), coverageStrategy);
        }
        return orderedCoverageTypes(List.copyOf(result), coverageStrategy);
    }

    private List<String> defaultCoverageTypes(String generationStrategy) {
        return switch (generationStrategy) {
            case "RISK_FIRST" -> List.of("EXCEPTION", "BOUNDARY", "PERMISSION", "FUNCTIONAL");
            case "COMPLIANCE" -> List.of("PERMISSION", "EXCEPTION", "FUNCTIONAL", "REGRESSION");
            case "EXPLORATORY" -> List.of("BOUNDARY", "EXCEPTION", "FUNCTIONAL", "SMOKE");
            default -> DEFAULT_COVERAGE_TYPES;
        };
    }

    private List<String> orderedCoverageTypes(List<String> coverageTypes, String coverageStrategy) {
        if (COVERAGE_STRATEGY_DEFAULT_ORDER.equals(coverageStrategy)) {
            return coverageTypes;
        }
        List<String> preference = switch (coverageStrategy) {
            case "SMOKE_FIRST" -> List.of("SMOKE", "FUNCTIONAL", "EXCEPTION", "BOUNDARY", "PERMISSION", "REGRESSION");
            case "RISK_FIRST" -> List.of("EXCEPTION", "BOUNDARY", "PERMISSION", "FUNCTIONAL", "SMOKE", "REGRESSION");
            case "REGRESSION_HEAVY" -> List.of("REGRESSION", "FUNCTIONAL", "SMOKE", "EXCEPTION", "BOUNDARY", "PERMISSION");
            case "SECURITY_PERMISSION" -> List.of("PERMISSION", "EXCEPTION", "BOUNDARY", "FUNCTIONAL", "SMOKE", "REGRESSION");
            default -> List.of();
        };
        List<String> ordered = new ArrayList<>();
        for (String preferred : preference) {
            if (coverageTypes.contains(preferred)) {
                ordered.add(preferred);
            }
        }
        for (String coverageType : coverageTypes) {
            if (!ordered.contains(coverageType)) {
                ordered.add(coverageType);
            }
        }
        return List.copyOf(ordered);
    }

    private Map<String, Object> normalizedContextDefaults(Map<String, Object> rawDefaults) {
        if (rawDefaults == null || rawDefaults.isEmpty()) {
            return Map.of();
        }
        for (String key : rawDefaults.keySet()) {
            if (!ALLOWED_CONTEXT_DEFAULT_KEYS.contains(key)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "contextDefaults 不支持字段: " + key);
            }
        }
        Map<String, Object> defaults = new LinkedHashMap<>();
        String environmentKey = stringValue(rawDefaults.get("environmentKey"));
        if (environmentKey != null) {
            defaults.put("environmentKey", normalizedCode(environmentKey, null, "environmentKey", 64));
        }
        putUuidStrings(defaults, "contextApiIds", rawDefaults.get("contextApiIds"));
        putUuidStrings(defaults, "contextPageIds", rawDefaults.get("contextPageIds"));
        putUuidStrings(defaults, "contextFlowIds", rawDefaults.get("contextFlowIds"));
        return defaults;
    }

    private void putUuidStrings(Map<String, Object> defaults, String fieldName, Object rawValue) {
        List<UUID> ids = uuidList(rawValue, fieldName);
        if (!ids.isEmpty()) {
            defaults.put(fieldName, ids.stream().map(UUID::toString).toList());
        }
    }

    private List<UUID> uuidList(Object rawValue, String fieldName) {
        if (rawValue == null) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        if (rawValue instanceof List<?> list) {
            for (Object item : list) {
                if (item != null && StringUtils.hasText(String.valueOf(item))) {
                    values.add(String.valueOf(item));
                }
            }
        } else if (rawValue instanceof String text) {
            values.addAll(List.of(text.replace('，', ',').split("[,\\s]+")));
        } else {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, fieldName + " 必须是 UUID 字符串列表");
        }
        LinkedHashSet<UUID> ids = new LinkedHashSet<>();
        for (String value : values) {
            if (!StringUtils.hasText(value)) {
                continue;
            }
            try {
                ids.add(UUID.fromString(value.trim()));
            } catch (IllegalArgumentException exception) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, fieldName + " 包含非法 UUID: " + value.trim());
            }
        }
        if (ids.size() > MAX_CONTEXT_DEFAULT_IDS) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, fieldName + " 最多支持 " + MAX_CONTEXT_DEFAULT_IDS + " 个");
        }
        return List.copyOf(ids);
    }

    private TestDesignTemplateResponse toResponse(TestDesignTemplate template) {
        return new TestDesignTemplateResponse(
                template.id(),
                template.projectId(),
                template.name(),
                template.description(),
                template.promptKey(),
                template.promptVersion(),
                csvValues(template.coverageTypes()),
                template.generationStrategy(),
                template.coverageStrategy(),
                template.caseCountPerRequirement(),
                contextDefaults(template.contextDefaultsJson()),
                template.enabled(),
                template.createdBy(),
                template.updatedBy(),
                template.createdAt(),
                template.updatedAt()
        );
    }

    private Map<String, Object> contextDefaults(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return Map.of();
        }
        try {
            Map<String, Object> values = objectMapper.readValue(rawValue, new TypeReference<>() {
            });
            return normalizedContextDefaults(values);
        } catch (JsonProcessingException exception) {
            return Map.of();
        }
    }

    private String contextDefaultsJson(Map<String, Object> defaults) {
        try {
            return objectMapper.writeValueAsString(defaults == null ? Map.of() : defaults);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize test design template defaults", exception);
        }
    }

    private TestDesignTemplate templateOrThrow(UUID id) {
        return repository.template(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "生成模板不存在: " + id));
    }

    private static String requiredText(String value, String fieldName) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, fieldName + " 不能为空");
        }
        return normalized;
    }

    private static void validateLength(String value, String fieldName, int maxLength) {
        if (value.length() > maxLength) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, fieldName + " 长度不能超过 " + maxLength);
        }
    }

    private static void rejectSensitive(String value, String fieldName) {
        if (TestDesignSensitiveText.containsSensitiveText(value)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, fieldName + " 不能包含疑似敏感信息");
        }
    }

    private static List<String> csvValues(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return List.of();
        }
        return List.of(rawValue.replace('，', ',').split(",")).stream()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private static String stringValue(Object value) {
        if (value == null || !StringUtils.hasText(String.valueOf(value))) {
            return null;
        }
        return String.valueOf(value).trim();
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private void writeAudit(String action, TestDesignTemplate template) {
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("templateId", template.id());
        after.put("projectScoped", StringUtils.hasText(template.projectId()));
        after.put("projectId", template.projectId());
        after.put("name", template.name());
        after.put("promptKey", template.promptKey());
        after.put("promptVersion", template.promptVersion());
        after.put("coverageTypes", csvValues(template.coverageTypes()));
        after.put("generationStrategy", template.generationStrategy());
        after.put("coverageStrategy", template.coverageStrategy());
        after.put("caseCountPerRequirement", template.caseCountPerRequirement());
        after.put("enabled", template.enabled());
        if (StringUtils.hasText(template.projectId())) {
            contextClient.writeAuditEvent(action, "TEST_DESIGN_TEMPLATE", template.id().toString(),
                    template.projectId(), "SUCCEEDED", after);
            return;
        }
        contextClient.writePlatformAuditEvent(action, "TEST_DESIGN_TEMPLATE", template.id().toString(),
                "SUCCEEDED", after);
    }

    private record TemplatePayload(
            String name,
            String description,
            String promptKey,
            String promptVersion,
            List<String> coverageTypes,
            String generationStrategy,
            String coverageStrategy,
            int caseCountPerRequirement,
            Map<String, Object> contextDefaults,
            boolean enabled
    ) {
    }

    public record TaskTemplateDefaults(
            UUID templateId,
            String templateName,
            String promptKey,
            String promptVersion,
            List<String> coverageTypes,
            String generationStrategy,
            String coverageStrategy,
            int caseCountPerRequirement,
            String environmentKey,
            List<UUID> contextApiIds,
            List<UUID> contextPageIds,
            List<UUID> contextFlowIds
    ) {
    }
}
