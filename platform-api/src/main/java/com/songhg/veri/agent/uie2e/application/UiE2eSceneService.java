package com.songhg.veri.agent.uie2e.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.integration.application.view.PlatformContext;
import com.songhg.veri.agent.uie2e.application.command.CreateUiE2eSceneCommand;
import com.songhg.veri.agent.uie2e.application.command.UpdateUiE2eSceneCommand;
import com.songhg.veri.agent.uie2e.application.port.UiE2eRepository;
import com.songhg.veri.agent.uie2e.application.query.UiE2eScenePageRequest;
import com.songhg.veri.agent.uie2e.application.query.UiE2eSceneQuery;
import com.songhg.veri.agent.uie2e.application.view.UiE2eSceneDetailResponse;
import com.songhg.veri.agent.uie2e.application.view.UiE2eSceneStepResponse;
import com.songhg.veri.agent.uie2e.application.view.UiE2eSceneSummaryResponse;
import com.songhg.veri.agent.uie2e.config.UiE2eProperties;
import com.songhg.veri.agent.uie2e.domain.UiE2eScene;
import com.songhg.veri.agent.uie2e.domain.UiE2eSceneStep;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class UiE2eSceneService {

    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{1,128}$");
    private static final Set<String> SCENE_STATUSES = Set.of("DRAFT", "REVIEWING", "APPROVED", "DISABLED", "ARCHIVED");
    private static final Set<String> WRITABLE_STATUSES = Set.of("DRAFT", "REVIEWING", "APPROVED", "DISABLED");
    private static final Set<String> RISK_LEVELS = Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");
    private static final Set<String> STEP_TYPES = Set.of(
            "LOGIN",
            "NAVIGATE",
            "QUERY",
            "FORM_FILL",
            "CLICK",
            "ASSERT",
            "WAIT",
            "APPROVAL",
            "EXPORT",
            "CUSTOM"
    );
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };

    private final UiE2eRepository repository;
    private final UiE2ePlatformContextClient contextClient;
    private final UiE2eActorResolver actorResolver;
    private final UiE2eCrossWpReferenceService crossWpReferenceService;
    private final UiE2eProperties properties;
    private final ObjectMapper objectMapper;

    public UiE2eSceneService(
            UiE2eRepository repository,
            UiE2ePlatformContextClient contextClient,
            UiE2eActorResolver actorResolver,
            UiE2eCrossWpReferenceService crossWpReferenceService,
            UiE2eProperties properties,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.contextClient = contextClient;
        this.actorResolver = actorResolver;
        this.crossWpReferenceService = crossWpReferenceService;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * Creates a scene inside the resolved project scope and rejects any attempt to enter archived state directly.
     * Cross-WP references are validated through aggregate evidence adapters so WP7 stays decoupled from WP3/WP5 tables.
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public UiE2eSceneDetailResponse createScene(CreateUiE2eSceneCommand command) {
        assertEnabled();
        PlatformContext context = contextClient.projectContext(command.projectId());
        String projectId = context.resourceId();
        String code = boundedCode(command.code());
        repository.sceneByProjectAndCode(projectId, code).ifPresent(existing -> {
            throw new BusinessException(ErrorCode.CONFLICT, "UI/E2E scene code 已存在");
        });
        String status = normalizeWritableStatus(command.status(), "DRAFT");
        if ("ARCHIVED".equals(status)) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "归档状态必须通过 archive 接口进入");
        }
        Map<String, Object> sourceSummary = safeObject(command.sourceSummary());
        crossWpReferenceService.validateSceneSourceSummary(projectId, sourceSummary);
        List<CreateUiE2eSceneCommand.SceneStepPayload> stepPayloads = validateSteps(command.steps());
        Instant now = Instant.now();
        String actor = actorResolver.currentActor();
        UiE2eScene scene = new UiE2eScene(
                UUID.randomUUID(),
                projectId,
                boundedNullable(command.applicationId(), 64),
                boundedNullable(command.environmentId(), 64),
                code,
                boundedText(command.name(), 128),
                status,
                normalizeRiskLevel(command.riskLevel()),
                json(sourceSummary),
                json(normalizedTags(command.tags())),
                actor,
                actor,
                null,
                now,
                now
        );
        try {
            repository.insertScene(scene);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.CONFLICT, "UI/E2E scene code 已存在");
        }
        repository.replaceSceneSteps(scene.id(), sceneSteps(scene, stepPayloads, actor, now));
        auditScene(scene, stepPayloads.size(), "ui_e2e.scene.created");
        return detail(scene);
    }

    @Transactional(readOnly = true)
    public PageResponse<UiE2eSceneSummaryResponse> scenes(UiE2eScenePageRequest request) {
        assertEnabled();
        UiE2eSceneQuery query = normalizeQuery(request.toQuery());
        List<UiE2eSceneSummaryResponse> items = repository.scenes(query).stream()
                .map(this::summary)
                .toList();
        return PageResponse.of(items, request.getIndex(), request.getSize(), repository.countScenes(query));
    }

    @Transactional(readOnly = true)
    public UiE2eSceneDetailResponse scene(UUID id) {
        assertEnabled();
        return detail(requireScene(id));
    }

    /**
     * Applies partial updates while preserving immutable identifiers and re-validating the full step/source state.
     * Archived scenes stay read-only so downstream execution and audit semantics remain stable.
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public UiE2eSceneDetailResponse updateScene(UUID id, UpdateUiE2eSceneCommand command) {
        assertEnabled();
        UiE2eScene existing = requireScene(id);
        assertMutable(existing);
        String projectId = existing.projectId();
        Map<String, Object> sourceSummary = command.sourceSummary() == null
                ? readMap(existing.sourceSummaryJson())
                : safeObject(command.sourceSummary());
        crossWpReferenceService.validateSceneSourceSummary(projectId, sourceSummary);
        List<UiE2eSceneStep> currentSteps = repository.sceneSteps(existing.id());
        List<CreateUiE2eSceneCommand.SceneStepPayload> stepPayloads = command.steps() == null
                ? currentSteps.stream().map(this::payload).toList()
                : validateSteps(command.steps());
        Instant now = Instant.now();
        UiE2eScene updated = new UiE2eScene(
                existing.id(),
                projectId,
                command.applicationId() == null ? existing.applicationId() : boundedNullable(command.applicationId(), 64),
                command.environmentId() == null ? existing.environmentId() : boundedNullable(command.environmentId(), 64),
                existing.code(),
                StringUtils.hasText(command.name()) ? boundedText(command.name(), 128) : existing.name(),
                command.status() == null ? existing.status() : normalizeWritableStatus(command.status(), existing.status()),
                command.riskLevel() == null ? existing.riskLevel() : normalizeRiskLevel(command.riskLevel()),
                json(sourceSummary),
                command.tags() == null ? existing.tagsJson() : json(normalizedTags(command.tags())),
                existing.createdBy(),
                actorResolver.currentActor(),
                existing.archivedAt(),
                existing.createdAt(),
                now
        );
        repository.updateScene(updated);
        repository.replaceSceneSteps(updated.id(), sceneSteps(updated, stepPayloads, updated.updatedBy(), now));
        auditScene(updated, stepPayloads.size(), "ui_e2e.scene.updated");
        return detail(updated);
    }

    /**
     * Archives a scene through the dedicated transition entrypoint so callers cannot bypass lifecycle policy with patch.
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public UiE2eSceneDetailResponse archiveScene(UUID id) {
        assertEnabled();
        UiE2eScene existing = requireScene(id);
        if ("ARCHIVED".equals(existing.status())) {
            return detail(existing);
        }
        Instant now = Instant.now();
        UiE2eScene archived = new UiE2eScene(
                existing.id(),
                existing.projectId(),
                existing.applicationId(),
                existing.environmentId(),
                existing.code(),
                existing.name(),
                "ARCHIVED",
                existing.riskLevel(),
                existing.sourceSummaryJson(),
                existing.tagsJson(),
                existing.createdBy(),
                actorResolver.currentActor(),
                now,
                existing.createdAt(),
                now
        );
        repository.archiveScene(archived);
        auditScene(archived, repository.sceneSteps(archived.id()).size(), "ui_e2e.scene.archived");
        return detail(archived);
    }

    @Transactional(readOnly = true)
    public String sceneProjectScopeId(UUID id) {
        return repository.sceneProjectScopeId(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "UI/E2E 场景不存在"));
    }

    private UiE2eScene requireScene(UUID id) {
        return repository.scene(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "UI/E2E 场景不存在"));
    }

    private void auditScene(UiE2eScene scene, int stepCount, String action) {
        contextClient.writeAuditEvent(action, "UI_E2E_SCENE", scene.id().toString(), scene.projectId(), "SUCCEEDED", Map.of(
                "status", scene.status(),
                "riskLevel", scene.riskLevel(),
                "stepCount", stepCount,
                "sourceSummaryKeyCount", readMap(scene.sourceSummaryJson()).size(),
                "tagCount", readStringList(scene.tagsJson()).size()
        ));
    }

    private UiE2eSceneDetailResponse detail(UiE2eScene scene) {
        List<UiE2eSceneStepResponse> steps = repository.sceneSteps(scene.id()).stream()
                .map(this::stepResponse)
                .toList();
        return new UiE2eSceneDetailResponse(
                scene.id(),
                scene.projectId(),
                scene.applicationId(),
                scene.environmentId(),
                scene.code(),
                scene.name(),
                scene.status(),
                scene.riskLevel(),
                readStringList(scene.tagsJson()),
                readMap(scene.sourceSummaryJson()),
                steps,
                // These policy flags tell the frontend what WP7 deliberately does not allow yet.
                Map.of(
                        "mutable", !"ARCHIVED".equals(scene.status()),
                        "executable", "APPROVED".equals(scene.status()),
                        "rawDomSnapshotStored", false,
                        "crossWpDirectTableReadAllowed", false
                ),
                scene.archivedAt(),
                scene.createdAt(),
                scene.updatedAt()
        );
    }

    private UiE2eSceneSummaryResponse summary(UiE2eScene scene) {
        return new UiE2eSceneSummaryResponse(
                scene.id(),
                scene.projectId(),
                scene.applicationId(),
                scene.environmentId(),
                scene.code(),
                scene.name(),
                scene.status(),
                scene.riskLevel(),
                readStringList(scene.tagsJson()),
                readMap(scene.sourceSummaryJson()),
                repository.sceneSteps(scene.id()).size(),
                scene.archivedAt(),
                scene.createdAt(),
                scene.updatedAt()
        );
    }

    private UiE2eSceneStepResponse stepResponse(UiE2eSceneStep step) {
        return new UiE2eSceneStepResponse(
                step.id(),
                step.stepOrder(),
                step.stepType(),
                readMap(step.actionSummaryJson()),
                readMap(step.locatorStrategyJson()),
                readMap(step.assertionSummaryJson()),
                readMap(step.waitPolicyJson()),
                step.createdAt(),
                step.updatedAt()
        );
    }

    private CreateUiE2eSceneCommand.SceneStepPayload payload(UiE2eSceneStep step) {
        return new CreateUiE2eSceneCommand.SceneStepPayload(
                step.stepType(),
                readMap(step.actionSummaryJson()),
                readMap(step.locatorStrategyJson()),
                readMap(step.assertionSummaryJson()),
                readMap(step.waitPolicyJson())
        );
    }

    /**
     * Rebuilds the full ordered step template list on each write so archived or stale step rows never linger.
     */
    private List<UiE2eSceneStep> sceneSteps(
            UiE2eScene scene,
            List<CreateUiE2eSceneCommand.SceneStepPayload> payloads,
            String actor,
            Instant now
    ) {
        List<UiE2eSceneStep> steps = new ArrayList<>();
        for (int i = 0; i < payloads.size(); i++) {
            CreateUiE2eSceneCommand.SceneStepPayload payload = payloads.get(i);
            steps.add(new UiE2eSceneStep(
                    UUID.randomUUID(),
                    scene.id(),
                    scene.projectId(),
                    i + 1,
                    normalizeStepType(payload.stepType()),
                    json(safeObject(payload.actionSummary())),
                    json(safeObject(payload.locatorStrategy())),
                    json(safeObject(payload.assertionSummary())),
                    json(safeObject(payload.waitPolicy())),
                    actor,
                    actor,
                    now,
                    now
            ));
        }
        return List.copyOf(steps);
    }

    private List<CreateUiE2eSceneCommand.SceneStepPayload> validateSteps(
            List<CreateUiE2eSceneCommand.SceneStepPayload> steps
    ) {
        if (steps == null || steps.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "场景至少需要一个步骤");
        }
        List<CreateUiE2eSceneCommand.SceneStepPayload> normalized = new ArrayList<>();
        for (CreateUiE2eSceneCommand.SceneStepPayload step : steps) {
            if (step == null) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "场景步骤不能为空");
            }
            normalized.add(new CreateUiE2eSceneCommand.SceneStepPayload(
                    normalizeStepType(step.stepType()),
                    safeObject(step.actionSummary()),
                    safeObject(step.locatorStrategy()),
                    safeObject(step.assertionSummary()),
                    safeObject(step.waitPolicy())
            ));
        }
        return List.copyOf(normalized);
    }

    private UiE2eSceneQuery normalizeQuery(UiE2eSceneQuery query) {
        return new UiE2eSceneQuery(
                boundedNullable(query.projectId(), 64),
                boundedNullable(query.applicationId(), 64),
                boundedNullable(query.environmentId(), 64),
                query.status() == null ? null : normalizeSceneStatus(query.status()),
                query.riskLevel() == null ? null : normalizeRiskLevel(query.riskLevel()),
                boundedNullable(query.tag(), 32),
                boundedNullable(query.keyword(), 128),
                Math.max(query.offset(), 0),
                Math.min(Math.max(query.limit(), 1), 100)
        );
    }

    private void assertMutable(UiE2eScene scene) {
        if ("ARCHIVED".equals(scene.status())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "已归档场景不可修改");
        }
    }

    private void assertEnabled() {
        if (!properties.enabled()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "WP7 UI/E2E 控制面已关闭");
        }
    }

    private String boundedCode(String code) {
        String normalized = boundedText(code, 128);
        if (!CODE_PATTERN.matcher(normalized).matches()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "scene code 格式非法");
        }
        return normalized;
    }

    private String normalizeWritableStatus(String status, String defaultValue) {
        String normalized = normalizeSceneStatus(status == null ? defaultValue : status);
        if (!WRITABLE_STATUSES.contains(normalized)) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "归档状态必须通过 archive 接口进入");
        }
        return normalized;
    }

    private String normalizeSceneStatus(String status) {
        String normalized = boundedText(status, 32).toUpperCase(Locale.ROOT);
        if (!SCENE_STATUSES.contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "scene status 不合法");
        }
        return normalized;
    }

    private String normalizeRiskLevel(String riskLevel) {
        String normalized = StringUtils.hasText(riskLevel) ? boundedText(riskLevel, 32).toUpperCase(Locale.ROOT) : "MEDIUM";
        if (!RISK_LEVELS.contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "scene riskLevel 不合法");
        }
        return normalized;
    }

    private String normalizeStepType(String stepType) {
        String normalized = boundedText(stepType, 32).toUpperCase(Locale.ROOT);
        if (!STEP_TYPES.contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "scene stepType 不合法");
        }
        return normalized;
    }

    private List<String> normalizedTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String tag : tags) {
            if (!StringUtils.hasText(tag)) {
                continue;
            }
            normalized.add(boundedText(tag, 32));
        }
        return List.copyOf(normalized);
    }

    private String boundedText(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "必填字段不能为空");
        }
        String trimmed = value.trim();
        return trimmed.length() > maxLength ? trimmed.substring(0, maxLength) : trimmed;
    }

    private String boundedNullable(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > maxLength ? trimmed.substring(0, maxLength) : trimmed;
    }

    private Map<String, Object> safeObject(Map<String, Object> value) {
        if (value == null || value.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, Object> sanitized = new LinkedHashMap<>();
        value.forEach((key, item) -> {
            if (key != null && item != null) {
                sanitized.put(key, item);
            }
        });
        return sanitized.isEmpty() ? Map.of() : Map.copyOf(sanitized);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "UI/E2E 数据无法序列化");
        }
    }

    private Map<String, Object> readMap(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "UI/E2E 数据无法解析");
        }
    }

    private List<String> readStringList(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, STRING_LIST_TYPE);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "UI/E2E 标签无法解析");
        }
    }
}
