package com.songhg.veri.agent.execution.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.apiautomation.application.ApiAutomationService;
import com.songhg.veri.agent.apiautomation.application.view.ApiAutomationRunDetailResponse;
import com.songhg.veri.agent.apiautomation.application.view.ApiAutomationRunResponse;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.execution.application.command.DispatchExecutionNodeRunCommand;
import com.songhg.veri.agent.execution.application.port.ExecutionRepository;
import com.songhg.veri.agent.execution.application.view.ExecutionRunDetailResponse;
import com.songhg.veri.agent.execution.config.ExecutionProperties;
import com.songhg.veri.agent.execution.domain.ExecutionNodeRun;
import com.songhg.veri.agent.execution.domain.ExecutionPlanNode;
import com.songhg.veri.agent.execution.domain.ExecutionRun;
import com.songhg.veri.agent.management.application.port.ManagementStore;
import com.songhg.veri.agent.notification.application.AsyncTaskNotificationService;
import com.songhg.veri.agent.testdata.application.TestDataCrossWpReferenceService;
import com.songhg.veri.agent.uie2e.application.UiE2eRunService;
import com.songhg.veri.agent.uie2e.application.command.CancelUiE2eRunCommand;
import com.songhg.veri.agent.uie2e.application.view.UiE2eArtifactManifestResponse;
import com.songhg.veri.agent.uie2e.application.view.UiE2eRunDetailResponse;
import java.nio.charset.StandardCharsets;
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
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExecutionRunServiceTest {

    private final ExecutionRepository repository = mock(ExecutionRepository.class);
    private final ExecutionDagValidator dagValidator = mock(ExecutionDagValidator.class);
    private final ExecutionPlatformContextClient contextClient = mock(ExecutionPlatformContextClient.class);
    private final ExecutionActorResolver actorResolver = mock(ExecutionActorResolver.class);
    private final ApiAutomationService apiAutomationService = mock(ApiAutomationService.class);
    private final UiE2eRunService uiE2eRunService = mock(UiE2eRunService.class);
    private final AsyncTaskNotificationService notificationService = mock(AsyncTaskNotificationService.class);
    private final ExecutionRunStreamService runStreamService = mock(ExecutionRunStreamService.class);
    private final ExecutionRunService service = new ExecutionRunService(
            repository,
            dagValidator,
            contextClient,
            actorResolver,
            apiAutomationService,
            EmptyObjectProvider.of(),
            EmptyObjectProvider.of(),
            SingleObjectProvider.of(uiE2eRunService),
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
            EmptyObjectProvider.of(),
            SingleObjectProvider.of(runStreamService)
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
    void dispatchClaimedNodeRunRoutesWp7RunnerTypesToUiDispatch() {
        UUID nodeRunId = UUID.randomUUID();
        DispatchExecutionNodeRunCommand command = new DispatchExecutionNodeRunCommand(
                nodeRunId,
                "claim-token",
                null,
                null,
                null,
                null,
                null
        );
        when(repository.nodeRun(nodeRunId)).thenReturn(Optional.of(nodeRun(UUID.randomUUID(), UUID.randomUUID(), "RUNNING", "WP7_UI", null)));
        ExecutionRunService routingService = spy(new ExecutionRunService(
                repository,
                dagValidator,
                contextClient,
                actorResolver,
                apiAutomationService,
                EmptyObjectProvider.of(),
                EmptyObjectProvider.of(),
                SingleObjectProvider.of(uiE2eRunService),
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
                EmptyObjectProvider.of(),
                SingleObjectProvider.of(runStreamService)
        ));
        ExecutionRunDetailResponse expected = mock(ExecutionRunDetailResponse.class);
        doReturn(expected).when(routingService).dispatchClaimedUiTestNodeRun(command);

        assertThat(routingService.dispatchClaimedNodeRun(command)).isSameAs(expected);

        verify(routingService).dispatchClaimedUiTestNodeRun(command);
        verify(routingService, never()).dispatchClaimedApiTestNodeRun(command);
    }

    @Test
    void dispatchClaimedNodeRunFallsBackToApiDispatchForNonWp7Nodes() {
        UUID nodeRunId = UUID.randomUUID();
        DispatchExecutionNodeRunCommand command = new DispatchExecutionNodeRunCommand(
                nodeRunId,
                "claim-token",
                null,
                null,
                null,
                null,
                null
        );
        when(repository.nodeRun(nodeRunId)).thenReturn(Optional.of(nodeRun(UUID.randomUUID(), UUID.randomUUID(), "RUNNING", "WP6_API", null)));
        ExecutionRunService routingService = spy(new ExecutionRunService(
                repository,
                dagValidator,
                contextClient,
                actorResolver,
                apiAutomationService,
                EmptyObjectProvider.of(),
                EmptyObjectProvider.of(),
                SingleObjectProvider.of(uiE2eRunService),
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
                EmptyObjectProvider.of(),
                SingleObjectProvider.of(runStreamService)
        ));
        ExecutionRunDetailResponse expected = mock(ExecutionRunDetailResponse.class);
        doReturn(expected).when(routingService).dispatchClaimedApiTestNodeRun(command);

        assertThat(routingService.dispatchClaimedNodeRun(command)).isSameAs(expected);

        verify(routingService).dispatchClaimedApiTestNodeRun(command);
        verify(routingService, never()).dispatchClaimedUiTestNodeRun(command);
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
    void cancelRunPropagatesWp7RunnerCancelForDispatchedUiNode() {
        UUID runId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        UUID uiNodeId = UUID.randomUUID();
        UUID wp7RunId = UUID.randomUUID();
        AtomicReference<ExecutionRun> runState = new AtomicReference<>(activeRun(runId, planId));
        AtomicReference<List<ExecutionNodeRun>> nodeState = new AtomicReference<>(List.of(
                nodeRun(runId, uiNodeId, "RUNNING", "WP7_UI", wp7RunId.toString())
        ));
        stubMutableRunState(runId, planId, uiNodeId, UUID.randomUUID(), runState, nodeState);
        when(uiE2eRunService.cancelRun(eq(wp7RunId), any(CancelUiE2eRunCommand.class)))
                .thenReturn(wp7Run(wp7RunId, "CANCELED"));

        ExecutionRunDetailResponse response = service.cancelRun(runId);

        assertThat(response.status()).isEqualTo("CANCELED");
        assertThat(response.resultSummary())
                .containsEntry("runnerCancelAttempted", true)
                .containsEntry("runnerCancelAcceptedCount", 1)
                .containsEntry("runnerCancelFailedCount", 0);
        assertThat(response.nodes()).singleElement().satisfies(node -> {
            assertThat(node.status()).isEqualTo("CANCELED");
            assertThat(node.resultSummary())
                    .containsEntry("runnerCancelAttempted", true)
                    .containsEntry("runnerCancelAccepted", true);
        });
        verify(uiE2eRunService).cancelRun(eq(wp7RunId), any(CancelUiE2eRunCommand.class));
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

    @Test
    void runDetailFederatesWp7ArtifactsWithoutLeakingStorageRefs() {
        UUID runId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        UUID uiNodeId = UUID.randomUUID();
        UUID wp7RunId = UUID.randomUUID();
        UUID sourceArtifactId = UUID.randomUUID();
        ExecutionNodeRun nodeRun = nodeRun(runId, uiNodeId, "SUCCEEDED", "WP7_UI", wp7RunId.toString());
        when(repository.run(runId)).thenReturn(Optional.of(activeRun(runId, planId)));
        when(repository.nodeRuns(runId)).thenReturn(List.of(nodeRun));
        when(repository.planNodes(planId)).thenReturn(List.of(planNode(uiNodeId, "ui-smoke", "UI_TEST")));
        when(uiE2eRunService.run(wp7RunId)).thenReturn(wp7RunWithArtifact(wp7RunId, "SUCCEEDED", sourceArtifactId));

        ExecutionRunDetailResponse response = service.run(runId);

        assertThat(response.artifacts()).singleElement().satisfies(artifact -> {
            assertThat(artifact.nodeRunId()).isEqualTo(nodeRun.id());
            assertThat(artifact.planNodeId()).isEqualTo(uiNodeId);
            assertThat(artifact.nodeKey()).isEqualTo("ui-smoke");
            assertThat(artifact.nodeType()).isEqualTo("UI_TEST");
            assertThat(artifact.runnerType()).isEqualTo("WP7_UI");
            assertThat(artifact.sourceType()).isEqualTo("WP7_UI_E2E");
            assertThat(artifact.artifactType()).isEqualTo("LOG");
            assertThat(artifact.downloadReady()).isTrue();
            assertThat(artifact.redactionFlags()).containsEntry("rawArtifactDownloadReady", true);
            assertThat(artifact.redactionFlags().values())
                    .allSatisfy(value -> assertThat(String.valueOf(value)).doesNotContain("artifact://"));
        });
        verify(uiE2eRunService).run(wp7RunId);
    }

    @Test
    void downloadArtifactDelegatesToWp7ArtifactDownload() {
        UUID runId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        UUID uiNodeId = UUID.randomUUID();
        UUID wp7RunId = UUID.randomUUID();
        UUID sourceArtifactId = UUID.randomUUID();
        ExecutionNodeRun nodeRun = nodeRun(runId, uiNodeId, "SUCCEEDED", "WP7_UI", wp7RunId.toString());
        when(repository.run(runId)).thenReturn(Optional.of(activeRun(runId, planId)));
        when(repository.nodeRuns(runId)).thenReturn(List.of(nodeRun));
        when(repository.planNodes(planId)).thenReturn(List.of(planNode(uiNodeId, "ui-smoke", "UI_TEST")));
        when(uiE2eRunService.run(wp7RunId)).thenReturn(wp7RunWithArtifact(wp7RunId, "SUCCEEDED", sourceArtifactId));
        when(uiE2eRunService.downloadArtifact(wp7RunId, sourceArtifactId))
                .thenReturn(new UiE2eRunService.DownloadableArtifact(
                        "runner.log",
                        "text/plain",
                        "wp7 artifact body".getBytes(StandardCharsets.UTF_8)
                ));

        ExecutionRunDetailResponse response = service.run(runId);
        ExecutionRunService.DownloadableArtifact downloaded = service.downloadArtifact(
                runId,
                response.artifacts().getFirst().id()
        );

        assertThat(downloaded.fileName()).isEqualTo("runner.log");
        assertThat(downloaded.contentType()).isEqualTo("text/plain");
        assertThat(new String(downloaded.content(), StandardCharsets.UTF_8)).isEqualTo("wp7 artifact body");
        verify(uiE2eRunService).downloadArtifact(wp7RunId, sourceArtifactId);
        verify(contextClient).writeAuditEvent(
                eq("execution.run.artifact.downloaded"),
                eq("EXECUTION_RUN"),
                eq(runId.toString()),
                eq("project-alpha"),
                eq("SUCCESS"),
                any()
        );
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

    private UiE2eRunDetailResponse wp7Run(UUID id, String status) {
        Instant now = Instant.now();
        return new UiE2eRunDetailResponse(
                id,
                "project-alpha",
                UUID.randomUUID(),
                "portal-login",
                "Portal Login",
                "APPROVED",
                UUID.randomUUID(),
                "APPROVED",
                status,
                "wp9-ui-request",
                "MANAGED",
                null,
                null,
                "trc_wp7",
                Map.of("accountLeaseRef", UUID.randomUUID().toString()),
                Map.of(),
                List.of(),
                List.of(),
                null,
                now.minusSeconds(10),
                "CANCELED".equals(status) ? now : null,
                now.minusSeconds(20),
                now,
                false
        );
    }

    private UiE2eRunDetailResponse wp7RunWithArtifact(UUID id, String status, UUID artifactId) {
        Instant now = Instant.now();
        return new UiE2eRunDetailResponse(
                id,
                "project-alpha",
                UUID.randomUUID(),
                "portal-login",
                "Portal Login",
                "APPROVED",
                UUID.randomUUID(),
                "APPROVED",
                status,
                "wp9-ui-request",
                "MANAGED",
                null,
                null,
                "trc_wp7",
                Map.of("accountLeaseRef", UUID.randomUUID().toString()),
                Map.of("artifactManifestCount", 1),
                List.of(),
                List.of(new UiE2eArtifactManifestResponse(
                        artifactId,
                        "LOG",
                        "artifact://ui-e2e/" + artifactId,
                        "artifact-digest",
                        42,
                        Map.of("rawArtifactStored", true, "rawArtifactDownloadReady", true),
                        "CAPTURED",
                        now.minusSeconds(5),
                        now
                )),
                null,
                now.minusSeconds(10),
                "CANCELED".equals(status) ? now : null,
                now.minusSeconds(20),
                now,
                false
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

    private record SingleObjectProvider<T>(T value) implements ObjectProvider<T> {

        private static <T> SingleObjectProvider<T> of(T value) {
            return new SingleObjectProvider<>(value);
        }

        @Override
        public T getObject(Object... args) {
            return value;
        }

        @Override
        public T getIfAvailable() {
            return value;
        }

        @Override
        public T getIfUnique() {
            return value;
        }

        @Override
        public T getObject() {
            return value;
        }
    }
}
