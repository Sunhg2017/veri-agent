package com.songhg.veri.agent.uie2e.application;

import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.uie2e.application.command.BatchCreateUiE2eRunCommand;
import com.songhg.veri.agent.uie2e.application.command.CreateUiE2eRunCommand;
import com.songhg.veri.agent.uie2e.application.port.UiE2eRepository;
import com.songhg.veri.agent.uie2e.application.view.UiE2eBatchRunItemResponse;
import com.songhg.veri.agent.uie2e.application.view.UiE2eBatchRunResponse;
import com.songhg.veri.agent.uie2e.application.view.UiE2eRunDetailResponse;
import com.songhg.veri.agent.uie2e.domain.UiE2eBundle;
import com.songhg.veri.agent.uie2e.domain.UiE2eScene;
import com.songhg.veri.agent.uie2e.config.UiE2eProperties;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class UiE2eBatchRunService {

    private final UiE2eRepository repository;
    private final UiE2eRunService runService;
    private final UiE2eProperties properties;

    public UiE2eBatchRunService(
            UiE2eRepository repository,
            UiE2eRunService runService,
            UiE2eProperties properties
    ) {
        this.repository = repository;
        this.runService = runService;
        this.properties = properties;
    }

    /**
     * Expands one batch request into per-scene run requests so operators can reuse the normal create-run guardrails
     * and still receive partial success details for every requested scene.
     */
    public UiE2eBatchRunResponse createRuns(BatchCreateUiE2eRunCommand command) {
        if (command == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "batch run 请求不能为空");
        }
        List<UUID> sceneIds = uniqueSceneIds(command.sceneIds());
        int maxBatchSize = properties.effectiveMaxScenesPerRun();
        if (sceneIds.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "UI_E2E_BATCH_SCENES_REQUIRED");
        }
        if (sceneIds.size() > maxBatchSize) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "UI_E2E_BATCH_SIZE_EXCEEDED");
        }
        List<UiE2eBatchRunItemResponse> items = sceneIds.stream()
                .map(sceneId -> createRunItem(command, sceneId))
                .toList();
        int createdCount = (int) items.stream().filter(item -> "CREATED".equals(item.outcome())).count();
        int replayedCount = (int) items.stream().filter(item -> "REPLAYED".equals(item.outcome())).count();
        int failedCount = (int) items.stream().filter(item -> "FAILED".equals(item.outcome())).count();
        return new UiE2eBatchRunResponse(
                command.projectId(),
                sceneIds.size(),
                createdCount,
                replayedCount,
                failedCount,
                items
        );
    }

    private UiE2eBatchRunItemResponse createRunItem(BatchCreateUiE2eRunCommand command, UUID sceneId) {
        UiE2eScene scene = repository.scene(sceneId).orElse(null);
        if (scene == null) {
            return new UiE2eBatchRunItemResponse(sceneId, null, null, "FAILED", "UI_E2E_SCENE_NOT_FOUND", "UI/E2E 场景不存在", null);
        }
        if (!Objects.equals(scene.projectId(), command.projectId())) {
            return new UiE2eBatchRunItemResponse(sceneId, scene.code(), null, "FAILED", "UI_E2E_RESOURCE_SCOPE_DENIED", "场景不属于当前项目", null);
        }
        UiE2eBundle bundle = repository.sceneBundles(sceneId).stream()
                .filter(item -> "APPROVED".equals(item.status()))
                .findFirst()
                .orElse(null);
        if (bundle == null) {
            return new UiE2eBatchRunItemResponse(sceneId, scene.code(), null, "FAILED", "UI_E2E_BUNDLE_NOT_READY", "未找到 APPROVED 脚本包", null);
        }
        try {
            UiE2eRunDetailResponse run = runService.createRun(new CreateUiE2eRunCommand(
                    command.projectId(),
                    sceneId,
                    bundle.id(),
                    command.environmentId(),
                    command.baseUrlRef(),
                    command.accountLeaseRef(),
                    batchRequestKey(command.requestKeyPrefix(), sceneId),
                    command.reason(),
                    command.browsers(),
                    command.visualRegressionEnabled(),
                    command.baselineRunId(),
                    command.visualMismatchThreshold()
            ));
            return new UiE2eBatchRunItemResponse(
                    sceneId,
                    scene.code(),
                    bundle.id(),
                    run.idempotentReplay() ? "REPLAYED" : "CREATED",
                    null,
                    null,
                    run
            );
        } catch (BusinessException exception) {
            return new UiE2eBatchRunItemResponse(
                    sceneId,
                    scene.code(),
                    bundle.id(),
                    "FAILED",
                    exception.getErrorCode().name(),
                    exception.getMessage(),
                    null
            );
        } catch (RuntimeException exception) {
            return new UiE2eBatchRunItemResponse(
                    sceneId,
                    scene.code(),
                    bundle.id(),
                    "FAILED",
                    ErrorCode.INTERNAL_ERROR.name(),
                    exception.getMessage(),
                    null
            );
        }
    }

    private List<UUID> uniqueSceneIds(List<UUID> sceneIds) {
        if (sceneIds == null || sceneIds.isEmpty()) {
            return List.of();
        }
        Set<UUID> ordered = new LinkedHashSet<>();
        for (UUID sceneId : sceneIds) {
            if (sceneId != null) {
                ordered.add(sceneId);
            }
        }
        return List.copyOf(ordered);
    }

    private String batchRequestKey(String requestKeyPrefix, UUID sceneId) {
        if (!StringUtils.hasText(requestKeyPrefix) || sceneId == null) {
            return null;
        }
        String normalizedPrefix = requestKeyPrefix.trim().replaceAll("[^A-Za-z0-9_.:-]", "-");
        if (!StringUtils.hasText(normalizedPrefix)) {
            return null;
        }
        int maxPrefixLength = Math.max(1, 128 - 1 - 36);
        String safePrefix = normalizedPrefix.length() > maxPrefixLength
                ? normalizedPrefix.substring(0, maxPrefixLength)
                : normalizedPrefix;
        return (safePrefix + "-" + sceneId.toString()).toUpperCase(Locale.ROOT);
    }
}
