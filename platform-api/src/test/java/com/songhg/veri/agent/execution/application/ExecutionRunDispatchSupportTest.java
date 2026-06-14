package com.songhg.veri.agent.execution.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.apiautomation.application.ApiAutomationService;
import com.songhg.veri.agent.apiautomation.application.command.CreateApiAutomationRunCommand;
import com.songhg.veri.agent.apiautomation.application.view.ApiAutomationRunDetailResponse;
import com.songhg.veri.agent.apiautomation.application.view.ApiAutomationRunResponse;
import com.songhg.veri.agent.execution.application.command.DispatchExecutionNodeRunCommand;
import com.songhg.veri.agent.execution.application.view.ExecutionRunDetailResponse;
import com.songhg.veri.agent.execution.config.ExecutionProperties;
import com.songhg.veri.agent.execution.domain.ExecutionNodeRun;
import com.songhg.veri.agent.execution.domain.ExecutionPlan;
import com.songhg.veri.agent.execution.domain.ExecutionPlanNode;
import com.songhg.veri.agent.execution.domain.ExecutionQueueClaim;
import com.songhg.veri.agent.execution.domain.ExecutionRun;
import com.songhg.veri.agent.execution.infrastructure.InMemoryExecutionRepository;
import com.songhg.veri.agent.management.application.port.ManagementStore;
import com.songhg.veri.agent.management.application.port.ManagementStoreRows.EnvironmentRuntimeRef;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExecutionRunDispatchSupportTest {

    private static final String CLAIM_TOKEN = "wp9_claim_dispatch_token";

    private final InMemoryExecutionRepository repository = new InMemoryExecutionRepository();
    private final ApiAutomationService apiAutomationService = mock(ApiAutomationService.class);
    private final ManagementStore managementStore = mock(ManagementStore.class);
    private final ExecutionPlatformContextClient contextClient = mock(ExecutionPlatformContextClient.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutionRunJsonSupport jsonSupport = new ExecutionRunJsonSupport(objectMapper);
    private final ExecutionRunResponseMapper responseMapper = new ExecutionRunResponseMapper(objectMapper);
    private final ExecutionRunQueueSupport queueSupport = new ExecutionRunQueueSupport(
            repository,
            contextClient,
            properties(),
            jsonSupport,
            responseMapper
    );
    private final ExecutionRunDispatchSupport support = new ExecutionRunDispatchSupport(
            repository,
            apiAutomationService,
            managementStore,
            properties(),
            jsonSupport,
            queueSupport,
            responseMapper,
            new DirectTransactionBridge()
    );

    @Test
    void dispatchClaimedApiTestNodeResolvesRuntimeRefsWithoutPersistingSecrets() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID bundleId = UUID.randomUUID();
        UUID caseId = UUID.randomUUID();
        UUID wp6RunId = UUID.randomUUID();
        SeededDispatchNode seed = seedClaimedApiNode(projectId, bundleId, caseId);
        when(managementStore.findEnvironmentRuntimeRef(argThat(params -> "staging".equals(params.get("keyword"))
                && projectId.equals(params.get("projectId")))))
                .thenReturn(new EnvironmentRuntimeRef(
                        UUID.randomUUID(),
                        projectId,
                        "staging",
                        "Staging",
                        "https://api.example.test/runtime",
                        "ENABLED"
                ));
        when(apiAutomationService.createRun(any())).thenReturn(wp6Run(wp6RunId, bundleId, "PASSED"));

        ExecutionRunDetailResponse response = support.dispatchClaimedApiTestNodeRun(new DispatchExecutionNodeRunCommand(
                seed.nodeRun().id(),
                CLAIM_TOKEN,
                null,
                null,
                null,
                null,
                null
        ));

        ArgumentCaptor<CreateApiAutomationRunCommand> commandCaptor =
                ArgumentCaptor.forClass(CreateApiAutomationRunCommand.class);
        verify(apiAutomationService).createRun(commandCaptor.capture());
        CreateApiAutomationRunCommand command = commandCaptor.getValue();
        assertThat(command.bundleId()).isEqualTo(bundleId);
        assertThat(command.environmentId()).isEqualTo("staging");
        assertThat(command.baseUrl()).isEqualTo("https://api.example.test/runtime");
        assertThat(command.caseIds()).containsExactly(caseId);
        assertThat(command.secretRefs()).containsExactly("secret://wp6/runtime-token");
        assertThat(command.timeoutSeconds()).isEqualTo(120);

        assertThat(response.status()).isEqualTo("SUCCEEDED");
        assertThat(response.nodes()).singleElement().satisfies(node -> {
            assertThat(node.id()).isEqualTo(seed.nodeRun().id());
            assertThat(node.status()).isEqualTo("SUCCEEDED");
            assertThat(node.externalRunId()).isEqualTo(wp6RunId.toString());
            assertThat(node.resultSummary())
                    .containsEntry("runnerDispatched", true)
                    .containsEntry("wp6DispatchReady", true)
                    .containsEntry("baseUrlSource", "PLAN_BASE_URL_REF")
                    .containsEntry("runtimeSecretRefCount", 1)
                    .containsEntry("rawBaseUrlStored", false)
                    .containsEntry("secretRefsStored", false)
                    .containsEntry("requestResponseStored", false);
            assertThat(String.valueOf(node.resultSummary().get("baseUrlRefDigest"))).startsWith("sha256:");
            assertThat((List<?>) node.resultSummary().get("runtimeSecretRefDigests"))
                    .singleElement()
                    .asString()
                    .startsWith("sha256:");
            assertThat(String.valueOf(node.resultSummary()))
                    .doesNotContain("https://api.example.test/runtime")
                    .doesNotContain("secret://wp6/runtime-token");
        });
        assertThat(repository.queueClaimByToken(CLAIM_TOKEN)).hasValueSatisfying(claim ->
                assertThat(claim.status()).isEqualTo("COMPLETED"));
    }

    private SeededDispatchNode seedClaimedApiNode(UUID projectId, UUID bundleId, UUID caseId)
            throws JsonProcessingException {
        UUID planId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        UUID planNodeId = UUID.randomUUID();
        Instant now = Instant.now();
        repository.insertPlan(new ExecutionPlan(
                planId,
                projectId.toString(),
                "Release API smoke",
                "READY",
                "qa",
                "{}",
                "dag-digest",
                null,
                "tester",
                "tester",
                null,
                now.minusSeconds(120),
                now.minusSeconds(120)
        ));
        repository.replacePlanNodes(planId, List.of(new ExecutionPlanNode(
                planNodeId,
                planId,
                "api-smoke",
                "API_TEST",
                "",
                objectMapper.writeValueAsString(Map.of(
                        "apiAutomationBundleId", bundleId.toString(),
                        "baseUrlRef", "env:staging",
                        "caseIds", List.of(caseId.toString()),
                        "runtimeSecretRefs", List.of("secret://wp6/runtime-token")
                )),
                "FAIL_FAST",
                120,
                "{}",
                now.minusSeconds(120),
                now.minusSeconds(120)
        )));
        ExecutionRun run = new ExecutionRun(
                runId,
                planId,
                projectId.toString(),
                "RUNNING",
                "MANUAL",
                "request-key",
                null,
                1,
                "trc_run",
                "{\"nodeCount\":1,\"runnerDispatched\":false}",
                null,
                null,
                "tester",
                now.minusSeconds(60),
                null,
                now.minusSeconds(90),
                now.minusSeconds(20)
        );
        ExecutionNodeRun nodeRun = new ExecutionNodeRun(
                UUID.randomUUID(),
                runId,
                planNodeId,
                "RUNNING",
                1,
                "WP6_API",
                null,
                null,
                null,
                "{\"runnerDispatched\":false}",
                now.minusSeconds(5),
                now.minusSeconds(60),
                now.minusSeconds(30),
                null,
                now.minusSeconds(90),
                now.minusSeconds(5)
        );
        repository.insertRun(run);
        repository.insertNodeRuns(List.of(nodeRun));
        repository.tryInsertQueueClaim(new ExecutionQueueClaim(
                UUID.randomUUID(),
                nodeRun.id(),
                CLAIM_TOKEN,
                "worker-a",
                now.minusSeconds(20),
                now.minusSeconds(10),
                now.plusSeconds(60),
                "CLAIMED",
                now.minusSeconds(20),
                now.minusSeconds(10)
        ));
        return new SeededDispatchNode(nodeRun);
    }

    private ApiAutomationRunDetailResponse wp6Run(UUID runId, UUID bundleId, String status) {
        Instant now = Instant.now();
        return new ApiAutomationRunDetailResponse(
                new ApiAutomationRunResponse(
                        runId,
                        "project-alpha",
                        bundleId,
                        "staging",
                        "base-url-digest",
                        "api.example.test",
                        status,
                        120,
                        1,
                        "trc_wp6",
                        "MANAGED",
                        null,
                        null,
                        now.minusSeconds(5),
                        now,
                        now.minusSeconds(10),
                        now
                ),
                List.of()
        );
    }

    private ExecutionProperties properties() {
        return new ExecutionProperties(
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
                30,
                300,
                50
        );
    }

    private static final class DirectTransactionBridge implements ExecutionRunDispatchSupport.TransactionBridge {

        @Override
        public <T> T inExecutionTransaction(Supplier<T> action) {
            return action.get();
        }
    }

    private record SeededDispatchNode(ExecutionNodeRun nodeRun) {
    }
}
