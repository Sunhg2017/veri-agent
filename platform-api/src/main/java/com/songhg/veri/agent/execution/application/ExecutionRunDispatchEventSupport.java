package com.songhg.veri.agent.execution.application;

import com.songhg.veri.agent.apiautomation.application.view.ApiAutomationRunDetailResponse;
import com.songhg.veri.agent.execution.application.port.ExecutionRepository;
import com.songhg.veri.agent.execution.application.view.ExecutionRunDetailResponse;
import com.songhg.veri.agent.execution.domain.ExecutionNodeRun;
import com.songhg.veri.agent.execution.domain.ExecutionRun;
import com.songhg.veri.agent.uie2e.application.view.UiE2eRunDetailResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Small helper that keeps dispatch-stage SSE publishing out of the main dispatch support class so core orchestration
 * logic stays below the repository line-count guard.
 */
final class ExecutionRunDispatchEventSupport {

    private final ExecutionRepository repository;
    private final ExecutionRunResponseMapper responseMapper;
    private final ExecutionRunEventPublisher eventPublisher;

    ExecutionRunDispatchEventSupport(
            ExecutionRepository repository,
            ExecutionRunResponseMapper responseMapper,
            ExecutionRunEventPublisher eventPublisher
    ) {
        this.repository = repository;
        this.responseMapper = responseMapper;
        this.eventPublisher = eventPublisher;
    }

    void publish(UUID nodeRunId, String level, String stage, String message, Map<String, Object> metadata) {
        if (eventPublisher == null || nodeRunId == null) {
            return;
        }
        repository.nodeRun(nodeRunId)
                .flatMap(nodeRun -> repository.run(nodeRun.runId()).map(run -> Map.entry(nodeRun, run)))
                .ifPresent(entry -> eventPublisher.publish(
                        detail(entry.getValue()),
                        level,
                        stage,
                        message,
                        entry.getKey().id(),
                        metadata
                ));
    }

    String terminalEventLevel(String status) {
        return switch (status) {
            case "SUCCEEDED" -> "SUCCESS";
            case "BLOCKED", "CANCELED", "TIMEOUT" -> "WARN";
            default -> "ERROR";
        };
    }

    String terminalMessage(String status) {
        return switch (status) {
            case "SUCCEEDED" -> "Runner dispatch completed successfully";
            case "BLOCKED" -> "Runner dispatch completed with blocked state";
            case "TIMEOUT" -> "Runner dispatch completed with timeout";
            case "CANCELED" -> "Runner dispatch completed with cancel state";
            default -> "Runner dispatch completed with failed state";
        };
    }

    void publishWp6DispatchStarted(ApiTestDispatchPreparation preparation) {
        publish(
                preparation.nodeRunId(),
                "INFO",
                "dispatch.wp6.started",
                "Dispatching node to WP6 runner",
                metadata(
                        "bundleId", stringify(preparation.bundleId()),
                        "environmentId", preparation.environmentId(),
                        "runtimeCaseIdsProvided", preparation.runtimeCaseIdsProvided(),
                        "runtimeSecretRefsProvided", preparation.runtimeSecretRefsProvided()
                )
        );
    }

    void publishWp7DispatchStarted(UiTestDispatchPreparation preparation) {
        publish(
                preparation.nodeRunId(),
                "INFO",
                "dispatch.wp7.started",
                "Dispatching node to WP7 runner",
                metadata(
                        "sceneId", stringify(preparation.sceneId()),
                        "bundleId", stringify(preparation.bundleId()),
                        "environmentId", preparation.environmentId()
                )
        );
    }

    void publishWp7FollowUpPoll(UiTestFollowUpPreparation preparation) {
        publish(
                preparation.nodeRunId(),
                "INFO",
                "dispatch.wp7.follow-up",
                "Polling WP7 runner follow-up snapshot",
                metadata("wp7RunId", stringify(preparation.wp7RunId()))
        );
    }

    void publishWp6DispatchCompleted(ExecutionNodeRun completed, String targetStatus, ApiAutomationRunDetailResponse wp6Run) {
        publish(
                completed.id(),
                terminalEventLevel(targetStatus),
                "dispatch.wp6.completed",
                terminalMessage(targetStatus),
                metadata(
                        "wp6Status", wp6Run.run().status(),
                        "wp6RunId", stringify(wp6Run.run().id()),
                        "wp6RunnerMode", wp6Run.run().runnerMode()
                )
        );
    }

    void publishWp7FollowUpRequired(ExecutionNodeRun waiting, UiE2eRunDetailResponse wp7Run) {
        publish(
                waiting.id(),
                "INFO",
                "dispatch.wp7.follow-up-pending",
                "WP7 runner still executing; follow-up required",
                metadata("wp7Status", wp7Run.status(), "wp7RunId", stringify(wp7Run.id()))
        );
    }

    void publishWp7DispatchCompleted(ExecutionNodeRun completed, String targetStatus, UiE2eRunDetailResponse wp7Run) {
        publish(
                completed.id(),
                terminalEventLevel(targetStatus),
                "dispatch.wp7.completed",
                terminalMessage(targetStatus),
                metadata("wp7Status", wp7Run.status(), "wp7RunId", stringify(wp7Run.id()))
        );
    }

    void publishWp7FollowUpStillActive(ExecutionNodeRun nodeRun, UiE2eRunDetailResponse wp7Run) {
        publish(
                nodeRun.id(),
                "INFO",
                "dispatch.wp7.follow-up-pending",
                "WP7 follow-up snapshot still active",
                metadata("wp7Status", wp7Run.status(), "wp7RunId", stringify(wp7Run.id()))
        );
    }

    void publishWp7FollowUpCompleted(ExecutionNodeRun completed, String targetStatus, UiE2eRunDetailResponse wp7Run) {
        publish(
                completed.id(),
                terminalEventLevel(targetStatus),
                "dispatch.wp7.follow-up-completed",
                terminalMessage(targetStatus),
                metadata("wp7Status", wp7Run.status(), "wp7RunId", stringify(wp7Run.id()))
        );
    }

    void publishWp6DispatchFailed(ApiTestDispatchPreparation preparation, String sourceErrorCode) {
        publish(
                preparation.nodeRunId(),
                "ERROR",
                "dispatch.wp6.failed",
                "WP6 dispatch failed",
                metadata("sourceErrorCode", sourceErrorCode)
        );
    }

    void publishWp7DispatchFailed(UiTestDispatchPreparation preparation, String sourceErrorCode) {
        publish(
                preparation.nodeRunId(),
                "ERROR",
                "dispatch.wp7.failed",
                "WP7 dispatch failed",
                metadata("sourceErrorCode", sourceErrorCode)
        );
    }

    Map<String, Object> metadata(Object... keyValues) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        for (int index = 0; index + 1 < keyValues.length; index += 2) {
            Object key = keyValues[index];
            Object value = keyValues[index + 1];
            if (key instanceof String metadataKey && value != null) {
                metadata.put(metadataKey, value);
            }
        }
        return metadata.isEmpty() ? Map.of() : metadata;
    }

    private String stringify(UUID id) {
        return id == null ? null : id.toString();
    }

    private ExecutionRunDetailResponse detail(ExecutionRun run) {
        return responseMapper.toDetail(
                run,
                false,
                repository.nodeRuns(run.id()),
                repository.planNodes(run.planId())
        );
    }
}
