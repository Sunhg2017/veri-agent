package com.songhg.veri.agent.uie2e.application;

import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.common.util.SensitiveTextSanitizer;
import com.songhg.veri.agent.uie2e.application.command.UpsertUiE2eFlakyMarkCommand;
import com.songhg.veri.agent.uie2e.application.port.UiE2eRepository;
import com.songhg.veri.agent.uie2e.application.query.UiE2eFlakyMarkPageRequest;
import com.songhg.veri.agent.uie2e.application.query.UiE2eFlakyMarkQuery;
import com.songhg.veri.agent.uie2e.application.view.UiE2eFlakyMarkResponse;
import com.songhg.veri.agent.uie2e.config.UiE2eProperties;
import com.songhg.veri.agent.uie2e.domain.UiE2eFlakyMark;
import com.songhg.veri.agent.uie2e.domain.UiE2eRun;
import com.songhg.veri.agent.uie2e.domain.UiE2eScene;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class UiE2eFlakyMarkService {

    private static final Set<String> FLAKY_STATUSES = Set.of("NONE", "FLAKY_CANDIDATE", "CONFIRMED_FLAKY", "WAIVED");

    private final UiE2eRepository repository;
    private final UiE2eActorResolver actorResolver;
    private final UiE2ePlatformContextClient contextClient;
    private final UiE2eProperties properties;

    public UiE2eFlakyMarkService(
            UiE2eRepository repository,
            UiE2eActorResolver actorResolver,
            UiE2ePlatformContextClient contextClient,
            UiE2eProperties properties
    ) {
        this.repository = repository;
        this.actorResolver = actorResolver;
        this.contextClient = contextClient;
        this.properties = properties;
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public UiE2eFlakyMarkResponse upsert(UpsertUiE2eFlakyMarkCommand command) {
        assertEnabled();
        if (command == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "flaky mark 请求不能为空");
        }
        if (command.sceneId() == null && command.runId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "sceneId 和 runId 不能同时为空");
        }
        UiE2eScene scene = command.sceneId() == null ? null : requireScene(command.sceneId());
        UiE2eRun run = command.runId() == null ? null : requireRun(command.runId());
        String projectId = normalizeProjectScope(command.projectId(), scene, run);
        if (scene == null && run != null) {
            scene = requireScene(run.sceneId());
        }
        if (scene != null && run != null && !scene.id().equals(run.sceneId())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "UI_E2E_RESOURCE_SCOPE_DENIED");
        }
        Instant now = Instant.now();
        String actor = actorResolver.currentActor();
        UiE2eFlakyMark existing = existingMark(scene, run);
        UiE2eFlakyMark flakyMark = new UiE2eFlakyMark(
                existing == null ? UUID.randomUUID() : existing.id(),
                projectId,
                scene == null ? null : scene.id(),
                run == null ? null : run.id(),
                normalizeStatus(command.status()),
                boundedReasonCode(command.reasonCode()),
                boundedReasonSummary(command.reasonSummary()),
                existing == null ? actor : existing.createdBy(),
                actor,
                existing == null ? now : existing.createdAt(),
                now
        );
        repository.upsertFlakyMark(flakyMark);
        UiE2eFlakyMark persisted = persistedMark(scene, run);
        java.util.Map<String, Object> auditPayload = new java.util.LinkedHashMap<>();
        auditPayload.put("status", persisted.status());
        auditPayload.put("sceneId", persisted.sceneId() == null ? null : persisted.sceneId().toString());
        auditPayload.put("runId", persisted.runId() == null ? null : persisted.runId().toString());
        auditPayload.put("reasonCode", persisted.reasonCode());
        auditPayload.put("reasonSummaryPresent", StringUtils.hasText(persisted.reasonSummary()));
        contextClient.writeAuditEvent(
                "ui_e2e.flaky.marked",
                "UI_E2E_FLAKY_MARK",
                persisted.id().toString(),
                projectId,
                "SUCCEEDED",
                auditPayload
        );
        return response(persisted, scene, run);
    }

    @Transactional(readOnly = true)
    public PageResponse<UiE2eFlakyMarkResponse> flakyMarks(UiE2eFlakyMarkPageRequest request) {
        assertEnabled();
        UiE2eFlakyMarkPageRequest effectiveRequest = request == null ? new UiE2eFlakyMarkPageRequest() : request;
        UiE2eFlakyMarkQuery query = normalizeQuery(effectiveRequest.toQuery());
        List<UiE2eFlakyMarkResponse> items = repository.flakyMarks(query).stream()
                .map(mark -> response(mark, mark.sceneId() == null ? null : requireScene(mark.sceneId()),
                        mark.runId() == null ? null : requireRun(mark.runId())))
                .toList();
        return PageResponse.of(items, effectiveRequest.getIndex(), effectiveRequest.getSize(), repository.countFlakyMarks(query));
    }

    @Transactional(readOnly = true)
    public String flakyProjectScopeId(UUID sceneId, UUID runId) {
        if (runId != null) {
            return requireRun(runId).projectId();
        }
        if (sceneId != null) {
            return requireScene(sceneId).projectId();
        }
        throw new BusinessException(ErrorCode.NOT_FOUND, "UI/E2E flaky mark 作用域不存在");
    }

    private UiE2eFlakyMark existingMark(UiE2eScene scene, UiE2eRun run) {
        if (run != null) {
            return repository.flakyMarkByRun(run.id())
                    .or(() -> scene == null ? java.util.Optional.empty() : repository.flakyMarkByScene(scene.id()))
                    .orElse(null);
        }
        if (scene != null) {
            return repository.flakyMarkByScene(scene.id()).orElse(null);
        }
        return null;
    }

    private UiE2eFlakyMark persistedMark(UiE2eScene scene, UiE2eRun run) {
        if (run != null) {
            return repository.flakyMarkByRun(run.id())
                    .or(() -> scene == null ? java.util.Optional.empty() : repository.flakyMarkByScene(scene.id()))
                    .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_STATE, "UI_E2E_FLAKY_MARK_PERSIST_FAILED"));
        }
        if (scene != null) {
            return repository.flakyMarkByScene(scene.id())
                    .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_STATE, "UI_E2E_FLAKY_MARK_PERSIST_FAILED"));
        }
        throw new BusinessException(ErrorCode.INVALID_STATE, "UI_E2E_FLAKY_MARK_PERSIST_FAILED");
    }

    private UiE2eFlakyMarkResponse response(UiE2eFlakyMark flakyMark, UiE2eScene scene, UiE2eRun run) {
        return new UiE2eFlakyMarkResponse(
                flakyMark.id(),
                flakyMark.projectId(),
                flakyMark.sceneId(),
                scene == null ? null : scene.code(),
                scene == null ? null : scene.name(),
                flakyMark.runId(),
                run == null ? null : run.status(),
                flakyMark.status(),
                flakyMark.reasonCode(),
                flakyMark.reasonSummary(),
                flakyMark.createdBy(),
                flakyMark.updatedBy(),
                flakyMark.createdAt(),
                flakyMark.updatedAt()
        );
    }

    private UiE2eScene requireScene(UUID sceneId) {
        return repository.scene(sceneId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "UI/E2E 场景不存在"));
    }

    private UiE2eRun requireRun(UUID runId) {
        return repository.run(runId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "UI/E2E run 不存在"));
    }

    private UiE2eFlakyMarkQuery normalizeQuery(UiE2eFlakyMarkQuery query) {
        return new UiE2eFlakyMarkQuery(
                boundedNullable(query.projectId(), 64),
                query.sceneId(),
                query.runId(),
                query.status() == null ? null : normalizeStatus(query.status()),
                boundedNullable(query.keyword(), 128),
                Math.max(query.offset(), 0),
                Math.min(Math.max(query.limit(), 1), 100)
        );
    }

    private String normalizeProjectScope(String commandProjectId, UiE2eScene scene, UiE2eRun run) {
        String normalized = boundedNullable(commandProjectId, 64);
        if (scene != null && normalized != null && !normalized.equals(scene.projectId())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "UI_E2E_RESOURCE_SCOPE_DENIED");
        }
        if (run != null && normalized != null && !normalized.equals(run.projectId())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "UI_E2E_RESOURCE_SCOPE_DENIED");
        }
        if (scene != null) {
            contextClient.projectContext(scene.projectId());
            return scene.projectId();
        }
        if (run != null) {
            contextClient.projectContext(run.projectId());
            return run.projectId();
        }
        if (normalized == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "projectId 不能为空");
        }
        return contextClient.projectContext(normalized).resourceId();
    }

    private String normalizeStatus(String status) {
        String normalized = boundedNullable(status, 32);
        if (!StringUtils.hasText(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "flaky status 不能为空");
        }
        normalized = normalized.toUpperCase(Locale.ROOT);
        if (!FLAKY_STATUSES.contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "flaky status 不合法");
        }
        return normalized;
    }

    private String boundedReasonCode(String reasonCode) {
        String normalized = boundedNullable(reasonCode, 64);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT).replace('-', '_');
    }

    private String boundedReasonSummary(String reasonSummary) {
        return SensitiveTextSanitizer.sanitizedEvidenceText(reasonSummary, 512);
    }

    private String boundedNullable(String value, int maxLength) {
        return SensitiveTextSanitizer.boundedNullableText(value, maxLength);
    }

    private void assertEnabled() {
        if (!properties.enabled()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "WP7 UI/E2E 控制面已关闭");
        }
    }
}
