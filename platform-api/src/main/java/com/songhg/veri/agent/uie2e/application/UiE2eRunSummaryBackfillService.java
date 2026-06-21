package com.songhg.veri.agent.uie2e.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.uie2e.application.command.BackfillUiE2eRunSummaryCommand;
import com.songhg.veri.agent.uie2e.application.port.UiE2eRepository;
import com.songhg.veri.agent.uie2e.application.query.UiE2eRunQuery;
import com.songhg.veri.agent.uie2e.application.view.UiE2eRunDetailResponse;
import com.songhg.veri.agent.uie2e.application.view.UiE2eRunSummaryBackfillItemResponse;
import com.songhg.veri.agent.uie2e.application.view.UiE2eRunSummaryBackfillResponse;
import com.songhg.veri.agent.uie2e.domain.UiE2eRun;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class UiE2eRunSummaryBackfillService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final UiE2eRepository repository;
    private final UiE2eRunService runService;
    private final ObjectMapper objectMapper;

    public UiE2eRunSummaryBackfillService(
            UiE2eRepository repository,
            UiE2eRunService runService,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.runService = runService;
        this.objectMapper = objectMapper;
    }

    /**
     * Recomputes aggregate-only execution summaries for existing runs so older data can be backfilled after summary
     * schema or aggregation logic evolves.
     */
    public UiE2eRunSummaryBackfillResponse backfill(BackfillUiE2eRunSummaryCommand command) {
        if (command == null || !StringUtils.hasText(command.projectId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "backfill projectId 不能为空");
        }
        List<UUID> targetRunIds = resolvedRunIds(command);
        List<UiE2eRunSummaryBackfillItemResponse> items = targetRunIds.stream()
                .map(runId -> backfillItem(command.projectId(), runId))
                .toList();
        int updatedCount = (int) items.stream().filter(UiE2eRunSummaryBackfillItemResponse::updated).count();
        int failedCount = (int) items.stream().filter(item -> item.errorCode() != null).count();
        int unchangedCount = items.size() - updatedCount - failedCount;
        return new UiE2eRunSummaryBackfillResponse(
                command.projectId(),
                targetRunIds.size(),
                updatedCount,
                unchangedCount,
                failedCount,
                items
        );
    }

    private UiE2eRunSummaryBackfillItemResponse backfillItem(String projectId, UUID runId) {
        UiE2eRun run = repository.run(runId).orElse(null);
        if (run == null) {
            return new UiE2eRunSummaryBackfillItemResponse(runId, null, null, false, 0, 0, "UI_E2E_RUN_NOT_FOUND", "UI/E2E run 不存在");
        }
        if (!Objects.equals(projectId, run.projectId())) {
            return new UiE2eRunSummaryBackfillItemResponse(runId, run.sceneId(), run.status(), false, 0, 0, "UI_E2E_RESOURCE_SCOPE_DENIED", "run 不属于当前项目");
        }
        try {
            UiE2eRunDetailResponse detail = runService.run(runId);
            String nextExecutionSummaryJson = objectMapper.writeValueAsString(detail.executionSummary());
            boolean updated = !mapsEqual(run.executionSummaryJson(), nextExecutionSummaryJson);
            if (updated) {
                repository.updateRun(new UiE2eRun(
                        run.id(),
                        run.sceneId(),
                        run.bundleId(),
                        run.projectId(),
                        run.status(),
                        run.requestKey(),
                        run.runnerMode(),
                        run.baseUrlDigest(),
                        run.accountLeaseRef(),
                        run.accountSummaryJson(),
                        nextExecutionSummaryJson,
                        run.failureCode(),
                        run.failureSummary(),
                        run.traceId(),
                        run.createdBy(),
                        run.startedAt(),
                        run.finishedAt(),
                        run.createdAt(),
                        Instant.now()
                ));
            }
            return new UiE2eRunSummaryBackfillItemResponse(
                    runId,
                    run.sceneId(),
                    run.status(),
                    updated,
                    detail.stepResults().size(),
                    detail.artifacts().size(),
                    null,
                    null
            );
        } catch (JsonProcessingException exception) {
            return new UiE2eRunSummaryBackfillItemResponse(runId, run.sceneId(), run.status(), false, 0, 0, ErrorCode.INTERNAL_ERROR.name(), exception.getMessage());
        } catch (BusinessException exception) {
            return new UiE2eRunSummaryBackfillItemResponse(
                    runId,
                    run.sceneId(),
                    run.status(),
                    false,
                    0,
                    0,
                    exception.getErrorCode().name(),
                    exception.getMessage()
            );
        }
    }

    private List<UUID> resolvedRunIds(BackfillUiE2eRunSummaryCommand command) {
        if (command.runIds() != null && !command.runIds().isEmpty()) {
            Set<UUID> ordered = new LinkedHashSet<>();
            command.runIds().stream().filter(Objects::nonNull).forEach(ordered::add);
            return List.copyOf(ordered);
        }
        int limit = command.limit() == null || command.limit() <= 0 ? 50 : Math.min(command.limit(), 200);
        return repository.runs(new UiE2eRunQuery(command.projectId(), null, null, null, null, 0, limit)).stream()
                .map(UiE2eRun::id)
                .toList();
    }

    private boolean mapsEqual(String currentJson, String nextJson) throws JsonProcessingException {
        return Objects.equals(readMap(currentJson), readMap(nextJson));
    }

    private Map<String, Object> readMap(String json) throws JsonProcessingException {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        return objectMapper.readValue(json, MAP_TYPE);
    }
}
