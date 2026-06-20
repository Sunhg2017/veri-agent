package com.songhg.veri.agent.execution.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.common.util.SensitiveTextSanitizer;
import com.songhg.veri.agent.execution.application.view.ExecutionNodeRunResponse;
import com.songhg.veri.agent.execution.application.view.ExecutionRunArtifactResponse;
import com.songhg.veri.agent.execution.application.view.ExecutionRunDetailResponse;
import com.songhg.veri.agent.execution.application.view.ExecutionRunSummaryResponse;
import com.songhg.veri.agent.execution.domain.ExecutionNodeRun;
import com.songhg.veri.agent.execution.domain.ExecutionPlanNode;
import com.songhg.veri.agent.execution.domain.ExecutionRun;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.util.StringUtils;

/**
 * Maps WP9 execution run snapshots into sanitized response DTOs.
 */
final class ExecutionRunResponseMapper {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    ExecutionRunResponseMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    ExecutionRunDetailResponse toDetail(
            ExecutionRun run,
            boolean idempotentReplay,
            List<ExecutionNodeRun> nodeRuns,
            List<ExecutionPlanNode> planNodes
    ) {
        return toDetail(run, idempotentReplay, nodeRuns, planNodes, List.of());
    }

    ExecutionRunDetailResponse toDetail(
            ExecutionRun run,
            boolean idempotentReplay,
            List<ExecutionNodeRun> nodeRuns,
            List<ExecutionPlanNode> planNodes,
            List<ExecutionRunArtifactResponse> artifacts
    ) {
        Map<UUID, ExecutionPlanNode> nodeById = planNodes.stream()
                .collect(Collectors.toMap(ExecutionPlanNode::id, Function.identity()));
        return new ExecutionRunDetailResponse(
                run.id(),
                run.planId(),
                run.projectId(),
                run.status(),
                run.triggerType(),
                run.requestKey(),
                run.sourceEventId(),
                run.attempt(),
                run.traceId(),
                readMap(run.resultSummaryJson()),
                run.errorCode(),
                run.errorSummary(),
                nodeRuns.stream()
                        .map(nodeRun -> toNodeRunResponse(nodeRun, nodeById.get(nodeRun.planNodeId())))
                        .toList(),
                artifacts == null ? List.of() : List.copyOf(artifacts),
                idempotentReplay,
                run.createdBy(),
                run.startedAt(),
                run.finishedAt(),
                run.createdAt(),
                run.updatedAt()
        );
    }

    ExecutionRunSummaryResponse toSummary(ExecutionRun run, int nodeCount) {
        return new ExecutionRunSummaryResponse(
                run.id(),
                run.planId(),
                run.projectId(),
                run.status(),
                run.triggerType(),
                run.requestKey(),
                run.attempt(),
                run.traceId(),
                readMap(run.resultSummaryJson()),
                nodeCount,
                run.createdBy(),
                run.startedAt(),
                run.finishedAt(),
                run.createdAt(),
                run.updatedAt()
        );
    }

    Map<String, Integer> nodeStatusCounts(ExecutionRunDetailResponse detail) {
        return detail.nodes().stream()
                .collect(Collectors.toMap(
                        ExecutionNodeRunResponse::status,
                        ignored -> 1,
                        Integer::sum,
                        LinkedHashMap::new
                ));
    }

    Map<String, Object> runExportRedactionPolicy() {
        return Map.of(
                "rawOutputExported", false,
                "stdoutStderrExported", false,
                "rawRequestResponseExported", false,
                "rawBaseUrlExported", false,
                "secretRefsExported", false,
                "claimTokenExported", false,
                "artifactManifestExported", true,
                "rawArtifactDownloadExported", false,
                "triggerPayloadExported", false,
                "onlySanitizedSummariesExported", true
        );
    }

    private ExecutionNodeRunResponse toNodeRunResponse(ExecutionNodeRun nodeRun, ExecutionPlanNode planNode) {
        return new ExecutionNodeRunResponse(
                nodeRun.id(),
                nodeRun.planNodeId(),
                planNode == null ? null : planNode.nodeKey(),
                planNode == null ? null : planNode.nodeType(),
                nodeRun.status(),
                nodeRun.attempt(),
                nodeRun.runnerType(),
                nodeRun.externalRunId(),
                nodeRun.errorCode(),
                nodeRun.errorSummary(),
                readMap(nodeRun.resultSummaryJson()),
                nodeRun.heartbeatAt(),
                nodeRun.queuedAt(),
                nodeRun.startedAt(),
                nodeRun.finishedAt(),
                nodeRun.createdAt(),
                nodeRun.updatedAt()
        );
    }

    private Map<String, Object> readMap(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException exception) {
            // Corrupted persisted summaries are still exported as aggregate-safe evidence, never as raw JSON payload.
            return SensitiveTextSanitizer.unreadableMap();
        }
    }
}
