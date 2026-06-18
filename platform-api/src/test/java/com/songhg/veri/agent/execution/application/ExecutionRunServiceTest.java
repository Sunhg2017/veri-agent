package com.songhg.veri.agent.execution.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.apiautomation.application.ApiAutomationService;
import com.songhg.veri.agent.apiautomation.application.view.ApiAutomationRunDetailResponse;
import com.songhg.veri.agent.apiautomation.application.view.ApiAutomationRunResponse;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.execution.application.port.ExecutionRepository;
import com.songhg.veri.agent.execution.application.view.ExecutionRunDetailResponse;
import com.songhg.veri.agent.execution.config.ExecutionProperties;
import com.songhg.veri.agent.execution.domain.ExecutionNodeRun;
import com.songhg.veri.agent.execution.domain.ExecutionPlanNode;
import com.songhg.veri.agent.execution.domain.ExecutionRun;
import com.songhg.veri.agent.management.application.port.ManagementStore;
import com.songhg.veri.agent.notification.application.AsyncTaskNotificationService;
import com.songhg.veri.agent.testdata.application.TestDataCrossWpReferenceService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExecutionRunServiceTest {

    private final ExecutionRepository repository = mock(ExecutionRepository.class);
    private final ExecutionDagValidator dagValidator = mock(ExecutionDagValidator.class);
    private final ExecutionPlatformContextClient contextClient = mock(ExecutionPlatformContextClient.class);
    private final ExecutionActorResolver actorResolver = mock(ExecutionActorResolver.class);
    private final ApiAutomationService apiAutomationService = mock(ApiAutomationService.class);
    private final AsyncTaskNotificationService notificationService = mock(AsyncTaskNotificationService.class);
    private final ExecutionRunService service = new ExecutionRunService(
            repository,
            dagValidator,
            contextClient,
            actorResolver,
            apiAutomationService,
            EmptyObjectProvider.of(),
            EmptyObjectProvider.of(),
            new ObjectMapper(),
            new ExecutionProperties(
                    false,
                    false,
                    false,
                    300,
                    60,
                    5000,
                    30000,
                    "wp9-test-worker",
                    4,
                    2,
                    4,
                    180,
                    1800,
                    50
            ),
            notificationService,
            EmptyObjectProvider.of()
    );

    @Test
    void cancelRunPersistsAcceptedRunnerCancelSummary() {
        UUID runId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        UUID apiNodeId = UUID.randomUUID();
        UUID reportNodeId = UUID.randomUUID();
        UUID wp6RunId = UUID.randomUUID();
        AtomicReference<ExecutionRun> runState = new AtomicReference<>(activeRun(runId, planId));
        AtomicReference<List<ExecutionNodeRun>> nodeState = new AtomicReference<>(List.of(
                nodeRun(runId, apiNodeId, "RUNNING", "WP6_API", wp6RunId.toString()),
                nodeRun(runId, reportNodeId, "QUEUED", "REPORT", null)
        ));
        stubMutableRunState(runId, planId, apiNodeId, reportNodeId, runState, nodeState);
        when(apiAutomationService.cancelRun(wp6RunId)).thenReturn(wp6Run(wp6RunId, "CANCELED", null));

        ExecutionRunDetailResponse response = service.cancelRun(runId);

        assertThat(response.status()).isEqualTo("CANCELED");
        assertThat(response.resultSummary())
                .containsEntry("canceled", true)
                .containsEntry("canceledNodeCount", 2)
                .containsEntry("runnerCancelAttempted", true)
                .containsEntry("runnerCancelAttemptCount", 1)
                .containsEntry("runnerCancelAcceptedCount", 1)
                .containsEntry("runnerCancelFailedCount", 0)
                .containsEntry("runnerDispatched", false);
        assertThat(response.errorCode()).isEqualTo("EXECUTION_RUN_CANCELED");
        assertThat(response.errorSummary()).isEqualTo("Execution run canceled with best-effort runner cancellation");
        assertThat(response.nodes()).hasSize(2);
        assertThat(response.nodes()).allSatisfy(node -> {
            assertThat(node.status()).isEqualTo("CANCELED");
            assertThat(node.resultSummary()).containsEntry("canceled", true);
            assertThat(node.resultSummary()).containsEntry("runnerDispatched", false);
        });
        assertThat(response.nodes())
                .filteredOn(node -> apiNodeId.equals(node.planNodeId()))
                .singleElement()
                .satisfies(node -> {
                    assertThat(node.resultSummary()).containsEntry("runnerCancelAttempted", true);
                    assertThat(node.resultSummary()).containsEntry("runnerCancelAccepted", true);
                });
        assertThat(response.nodes())
                .filteredOn(node -> reportNodeId.equals(node.planNodeId()))
                .singleElement()
                .satisfies(node -> {
                    assertThat(node.resultSummary()).containsEntry("runnerCancelAttempted", false);
                    assertThat(node.resultSummary()).containsEntry("runnerCancelAccepted", false);
                });
        verify(apiAutomationService).cancelRun(wp6RunId);
        verify(contextClient).writeAuditEvent(
                eq("execution.run.canceled"),
                eq("EXECUTION_RUN"),
                eq(runId.toString()),
                eq("project-alpha"),
                eq("SUCCESS"),
                any()
        );
    }

    @Test
    void cancelRunRecordsSanitizedRunnerCancelFailureAndStillClosesWp9Run() {
        UUID runId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        UUID apiNodeId = UUID.randomUUID();
        UUID wp6RunId = UUID.randomUUID();
        AtomicReference<ExecutionRun> runState = new AtomicReference<>(activeRun(runId, planId));
        AtomicReference<List<ExecutionNodeRun>> nodeState = new AtomicReference<>(List.of(
                nodeRun(runId, apiNodeId, "RUNNING", "WP6_API", wp6RunId.toString())
        ));
        stubMutableRunState(runId, planId, apiNodeId, UUID.randomUUID(), runState, nodeState);
        when(apiAutomationService.cancelRun(wp6RunId))
                .thenThrow(new BusinessException(ErrorCode.SECRET_PROVIDER_ERROR,
                        "runner failed token=secret-value at https://api.example.test"));

        ExecutionRunDetailResponse response = service.cancelRun(runId);

        assertThat(response.status()).isEqualTo("CANCELED");
        assertThat(response.resultSummary())
                .containsEntry("canceled", true)
                .containsEntry("canceledNodeCount", 1)
                .containsEntry("runnerCancelAttempted", true)
                .containsEntry("runnerCancelAttemptCount", 1)
                .containsEntry("runnerCancelAcceptedCount", 0)
                .containsEntry("runnerCancelFailedCount", 1)
                .containsEntry("runnerDispatched", false);
        assertThat(response.errorSummary()).isEqualTo("Execution run canceled with best-effort runner cancellation");
        assertThat(response.nodes()).singleElement().satisfies(node -> {
            assertThat(node.status()).isEqualTo("CANCELED");
            assertThat(node.resultSummary())
                    .containsEntry("runnerCancelAttempted", true)
                    .containsEntry("runnerCancelAccepted", false)
                    .containsEntry("runnerCancelErrorCode", "SECRET_PROVIDER_ERROR")
                    .containsEntry("runnerDispatched", false);
            assertThat(String.valueOf(node.resultSummary().get("runnerCancelErrorSummary")))
                    .contains("[REDACTED]")
                    .doesNotContain("secret-value")
                    .doesNotContain("https://api.example.test");
        });

        ArgumentCaptor<List<ExecutionNodeRun>> nodeRunsCaptor = ArgumentCaptor.forClass(List.class);
        verify(repository).updateNodeRuns(nodeRunsCaptor.capture());
        assertThat(nodeRunsCaptor.getValue()).singleElement().satisfies(nodeRun -> {
            assertThat(nodeRun.errorSummary()).isEqualTo("Execution node canceled with best-effort runner cancellation");
            assertThat(nodeRun.resultSummaryJson())
                    .doesNotContain("secret-value")
                    .doesNotContain("https://api.example.test");
        });
    }

    @Test
    void cancelTerminalRunDoesNotCallRunnerOrMutateState() {
        UUID runId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        ExecutionRun terminalRun = new ExecutionRun(
                runId,
                planId,
                "project-alpha",
                "SUCCEEDED",
                "MANUAL",
                "request-key",
                null,
                1,
                "trc_run",
                "{\"runnerDispatched\":true}",
                null,
                null,
                "tester",
                Instant.now().minusSeconds(60),
                Instant.now().minusSeconds(10),
                Instant.now().minusSeconds(120),
                Instant.now().minusSeconds(10)
        );
        when(repository.run(runId)).thenReturn(Optional.of(terminalRun));
        when(repository.nodeRuns(runId)).thenReturn(List.of());
        when(repository.planNodes(planId)).thenReturn(List.of());

        ExecutionRunDetailResponse response = service.cancelRun(runId);

        assertThat(response.status()).isEqualTo("SUCCEEDED");
        verify(apiAutomationService, never()).cancelRun(any());
        verify(repository, never()).updateRun(any());
        verify(repository, never()).updateNodeRuns(any());
    }

    private void stubMutableRunState(
            UUID runId,
            UUID planId,
            UUID apiNodeId,
            UUID reportNodeId,
            AtomicReference<ExecutionRun> runState,
            AtomicReference<List<ExecutionNodeRun>> nodeState
    ) {
        when(repository.run(runId)).thenAnswer(ignored -> Optional.of(runState.get()));
        when(repository.nodeRuns(runId)).thenAnswer(ignored -> nodeState.get());
        when(repository.planNodes(planId)).thenReturn(List.of(
                planNode(apiNodeId, "api-smoke", "API_TEST"),
                planNode(reportNodeId, "report", "REPORT_HANDOFF")
        ));
        doAnswer(invocation -> {
            runState.set(invocation.getArgument(0));
            return null;
        }).when(repository).updateRun(any());
        doAnswer(invocation -> {
            nodeState.set(invocation.getArgument(0));
            return null;
        }).when(repository).updateNodeRuns(any());
    }

    private ExecutionRun activeRun(UUID id, UUID planId) {
        Instant now = Instant.now();
        return new ExecutionRun(
                id,
                planId,
                "project-alpha",
                "RUNNING",
                "MANUAL",
                "request-key",
                null,
                1,
                "trc_run",
                "{\"nodeCount\":2,\"runnerDispatched\":true}",
                null,
                null,
                "tester",
                now.minusSeconds(60),
                null,
                now.minusSeconds(120),
                now.minusSeconds(30)
        );
    }

    private ExecutionNodeRun nodeRun(UUID runId, UUID planNodeId, String status, String runnerType, String externalRunId) {
        Instant now = Instant.now();
        return new ExecutionNodeRun(
                UUID.randomUUID(),
                runId,
                planNodeId,
                status,
                1,
                runnerType,
                externalRunId,
                null,
                null,
                "{\"runnerDispatched\":" + (externalRunId != null) + "}",
                now.minusSeconds(20),
                now.minusSeconds(50),
                now.minusSeconds(40),
                null,
                now.minusSeconds(80),
                now.minusSeconds(20)
        );
    }

    private ExecutionPlanNode planNode(UUID id, String nodeKey, String nodeType) {
        Instant now = Instant.now();
        return new ExecutionPlanNode(
                id,
                UUID.randomUUID(),
                nodeKey,
                nodeType,
                "",
                "{}",
                "FAIL_FAST",
                180,
                "{}",
                now,
                now
        );
    }

    private ApiAutomationRunDetailResponse wp6Run(UUID id, String status, String errorSummary) {
        Instant now = Instant.now();
        return new ApiAutomationRunDetailResponse(
                new ApiAutomationRunResponse(
                        id,
                        "project-alpha",
                        UUID.randomUUID(),
                        "staging",
                        "base-url-digest",
                        "api.example.test",
                        status,
                        180,
                        1,
                        "trc_wp6",
                        "MANAGED",
                        null,
                        errorSummary,
                        now.minusSeconds(10),
                        now,
                        now.minusSeconds(20),
                        now
                ),
                List.of()
        );
    }

    private record EmptyObjectProvider<T>() implements ObjectProvider<T> {

        private static <T> EmptyObjectProvider<T> of() {
            return new EmptyObjectProvider<>();
        }

        @Override
        public T getObject(Object... args) {
            return getObject();
        }

        @Override
        public T getIfAvailable() {
            return null;
        }

        @Override
        public T getIfUnique() {
            return null;
        }

        @Override
        public T getObject() {
            throw new IllegalStateException("No object available");
        }
    }
}
