package com.songhg.veri.agent.execution.application;

import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.execution.application.port.ExecutionRepository;
import com.songhg.veri.agent.execution.application.query.ExecutionRunLogQuery;
import com.songhg.veri.agent.execution.application.view.ExecutionRunLogEntryResponse;
import com.songhg.veri.agent.execution.domain.ExecutionPlanNode;
import com.songhg.veri.agent.execution.domain.ExecutionNodeRun;
import com.songhg.veri.agent.execution.domain.ExecutionRunLogEntry;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Reads persisted WP9 execution logs for workbench history and SSE reconnect backfill.
 */
class ExecutionRunLogHistoryService {

    private final ExecutionRepository repository;
    private final ExecutionRunJsonSupport jsonSupport;

    ExecutionRunLogHistoryService(ExecutionRepository repository, ExecutionRunJsonSupport jsonSupport) {
        this.repository = repository;
        this.jsonSupport = jsonSupport;
    }

    PageResponse<ExecutionRunLogEntryResponse> history(UUID runId, ExecutionRunLogQuery query) {
        Map<UUID, String> nodeKeyByNodeRunId = nodeKeyByNodeRunId(runId);
        List<ExecutionRunLogEntryResponse> items = repository.runLogs(runId, query).stream()
                .map(entry -> toResponse(entry, nodeKeyByNodeRunId))
                .toList();
        return PageResponse.of(items, query.offset() / Math.max(1, query.limit()), query.limit(), repository.countRunLogs(runId, query));
    }

    List<ExecutionRunLogEntryResponse> recentHistory(UUID runId, int limit) {
        ExecutionRunLogQuery query = new ExecutionRunLogQuery(null, Math.max(1, limit), 0);
        Map<UUID, String> nodeKeyByNodeRunId = nodeKeyByNodeRunId(runId);
        return repository.runLogs(runId, query).stream()
                .map(entry -> toResponse(entry, nodeKeyByNodeRunId))
                .toList();
    }

    private Map<UUID, String> nodeKeyByNodeRunId(UUID runId) {
        List<ExecutionNodeRun> nodeRuns = repository.nodeRuns(runId);
        Map<UUID, ExecutionPlanNode> planNodeById = repository.run(runId)
                .map(run -> repository.planNodes(run.planId()).stream()
                        .collect(Collectors.toMap(ExecutionPlanNode::id, node -> node)))
                .orElse(Map.of());
        return nodeRuns.stream()
                .collect(Collectors.toMap(
                        ExecutionNodeRun::id,
                        nodeRun -> {
                            ExecutionPlanNode planNode = planNodeById.get(nodeRun.planNodeId());
                            return planNode == null ? null : planNode.nodeKey();
                        },
                        (left, right) -> left
                ));
    }

    private ExecutionRunLogEntryResponse toResponse(ExecutionRunLogEntry entry, Map<UUID, String> nodeKeyByNodeRunId) {
        return new ExecutionRunLogEntryResponse(
                entry.id(),
                entry.runId(),
                entry.nodeRunId(),
                entry.nodeRunId() == null ? null : nodeKeyByNodeRunId.get(entry.nodeRunId()),
                entry.level(),
                entry.stage(),
                entry.message(),
                jsonSupport.readMap(entry.metadataJson()),
                entry.eventAt(),
                entry.createdAt()
        );
    }
}
