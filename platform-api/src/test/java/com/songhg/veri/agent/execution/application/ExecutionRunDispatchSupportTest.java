package com.songhg.veri.agent.execution.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.apiautomation.application.ApiAutomationService;
import com.songhg.veri.agent.apiautomation.application.command.CreateApiAutomationRunCommand;
import com.songhg.veri.agent.apiautomation.application.view.ApiAutomationRunDetailResponse;
import com.songhg.veri.agent.apiautomation.application.view.ApiAutomationRunResponse;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.execution.application.command.DispatchExecutionNodeRunCommand;
import com.songhg.veri.agent.execution.application.view.ExecutionRunDetailResponse;
import com.songhg.veri.agent.execution.config.ExecutionProperties;
import com.songhg.veri.agent.execution.domain.ExecutionNodeRun;
import com.songhg.veri.agent.execution.domain.ExecutionPlan;
import com.songhg.veri.agent.execution.domain.ExecutionPlanNode;
import com.songhg.veri.agent.execution.domain.ExecutionQueueClaim;
import com.songhg.veri.agent.execution.domain.ExecutionRun;
import com.songhg.veri.agent.execution.infrastructure.InMemoryExecutionRepository;
import com.songhg.veri.agent.integration.application.view.PlatformContext;
import com.songhg.veri.agent.management.application.port.ManagementStore;
import com.songhg.veri.agent.management.application.port.ManagementStoreRows.EnvironmentRuntimeRef;
import com.songhg.veri.agent.testdata.application.TestAccountLeaseService;
import com.songhg.veri.agent.testdata.application.TestAccountPoolService;
import com.songhg.veri.agent.testdata.application.TestDataActorResolver;
import com.songhg.veri.agent.testdata.application.TestDataCrossWpReferenceService;
import com.songhg.veri.agent.testdata.application.TestDataPlatformContextClient;
import com.songhg.veri.agent.testdata.application.command.CreateTestAccountPoolCommand;
import com.songhg.veri.agent.testdata.application.command.UpsertTestPooledAccountCommand;
import com.songhg.veri.agent.testdata.config.TestDataProperties;
import com.songhg.veri.agent.testdata.infrastructure.InMemoryTestDataRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
    private final TestDataFixture testDataFixture = testDataFixture();
    private final ExecutionAccountLeaseSupport accountLeaseSupport = new ExecutionAccountLeaseSupport(
            repository,
            testDataFixture.crossWpService(),
            properties(),
            jsonSupport
    );
    private final ExecutionRunQueueSupport queueSupport = new ExecutionRunQueueSupport(
            repository,
            contextClient,
            properties(),
            jsonSupport,
            responseMapper,
            accountLeaseSupport
    );
    private final ExecutionRunDispatchSupport support = new ExecutionRunDispatchSupport(
            repository,
            apiAutomationService,
            managementStore,
            properties(),
            jsonSupport,
            accountLeaseSupport,
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

    @Test
    void dispatchClaimedApiTestNodeAcquiresAndReleasesWp8AccountLease() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID bundleId = UUID.randomUUID();
        UUID caseId = UUID.randomUUID();
        UUID wp6RunId = UUID.randomUUID();
        UUID poolId = seedAccountPool(projectId);
        SeededDispatchNode seed = seedClaimedApiNode(projectId, bundleId, caseId, Map.of(
                "accountPoolRef", poolId.toString(),
                "applicationId", "app-alpha",
                "environmentId", "env-staging",
                "roleTags", List.of("ADMIN"),
                "ttlSeconds", 90
        ));
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

        assertThat(response.status()).isEqualTo("SUCCEEDED");
        assertThat(response.resultSummary())
                .containsEntry("accountLeaseReleaseReady", true)
                .containsEntry("accountLeaseReleaseSuccessCount", 1)
                .containsEntry("accountLeaseReleaseAccountStatus", "AVAILABLE");
        assertThat(response.nodes()).singleElement().satisfies(node -> {
            assertThat(node.resultSummary())
                    .containsEntry("accountLeaseRequired", true)
                    .containsEntry("accountLeaseAcquired", true)
                    .containsEntry("accountLeaseStatus", "RELEASED")
                    .containsEntry("accountPoolRef", poolId.toString())
                    .containsEntry("accountLeaseTokenStored", false)
                    .containsEntry("accountLeaseSecretPlaintextStored", false)
                    .containsEntry("accountLeaseReleaseSucceeded", true)
                    .containsEntry("accountLeaseReleaseAccountStatus", "AVAILABLE");
            assertThat(String.valueOf(node.resultSummary()))
                    .doesNotContain("secret://wp8/accounts/admin-01")
                    .doesNotContain("leaseTokenDigest");
        });
    }

    @Test
    void dispatchFailureAfterLeaseAcquireClosesNodeAndReleasesAccountLocked() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID bundleId = UUID.randomUUID();
        UUID caseId = UUID.randomUUID();
        UUID poolId = seedAccountPool(projectId);
        SeededDispatchNode seed = seedClaimedApiNode(projectId, bundleId, caseId, Map.of(
                "accountPoolRef", poolId.toString(),
                "applicationId", "app-alpha",
                "environmentId", "env-staging",
                "roleTags", List.of("ADMIN"),
                "ttlSeconds", 90
        ));
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
        when(apiAutomationService.createRun(any()))
                .thenThrow(new BusinessException(
                        ErrorCode.SECRET_PROVIDER_ERROR,
                        "secret://wp6/runtime-token failed at https://api.example.test/private token=leak"
                ));

        assertThatThrownBy(() -> support.dispatchClaimedApiTestNodeRun(new DispatchExecutionNodeRunCommand(
                seed.nodeRun().id(),
                CLAIM_TOKEN,
                null,
                null,
                null,
                null,
                null
        ))).isInstanceOf(BusinessException.class);

        assertThat(repository.run(seed.runId())).hasValueSatisfying(run -> {
            assertThat(run.status()).isEqualTo("FAILED");
            Map<String, Object> summary = jsonSupport.readMap(run.resultSummaryJson());
            assertThat(summary)
                    .containsEntry("accountLeaseReleaseReady", true)
                    .containsEntry("accountLeaseReleaseSuccessCount", 1)
                    .containsEntry("accountLeaseReleaseAccountStatus", "LOCKED");
        });
        assertThat(repository.nodeRun(seed.nodeRun().id())).hasValueSatisfying(nodeRun -> {
            assertThat(nodeRun.status()).isEqualTo("FAILED");
            assertThat(nodeRun.errorCode()).isEqualTo("EXECUTION_NODE_DISPATCH_FAILED");
            assertThat(nodeRun.errorSummary())
                    .doesNotContain("secret://wp6/runtime-token")
                    .doesNotContain("https://api.example.test/private")
                    .doesNotContain("leak");
            Map<String, Object> summary = jsonSupport.readMap(nodeRun.resultSummaryJson());
            assertThat(summary)
                    .containsEntry("accountLeaseRequired", true)
                    .containsEntry("accountLeaseAcquired", true)
                    .containsEntry("accountLeaseStatus", "RELEASED")
                    .containsEntry("accountLeaseReleaseSucceeded", true)
                    .containsEntry("accountLeaseReleaseAccountStatus", "LOCKED")
                    .containsEntry("wp6DispatchFailed", true);
            assertThat(String.valueOf(summary))
                    .doesNotContain("secret://wp6/runtime-token")
                    .doesNotContain("https://api.example.test/private")
                    .doesNotContain("token=leak");
        });
        assertThat(repository.queueClaimByToken(CLAIM_TOKEN)).hasValueSatisfying(claim ->
                assertThat(claim.status()).isEqualTo("COMPLETED"));
    }

    @Test
    void invalidRuntimeBaseUrlDoesNotConsumeClaimOrAcquireLease() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID bundleId = UUID.randomUUID();
        UUID caseId = UUID.randomUUID();
        UUID poolId = seedAccountPool(projectId);
        SeededDispatchNode seed = seedClaimedApiNode(projectId, bundleId, caseId, Map.of(
                "accountPoolRef", poolId.toString(),
                "applicationId", "app-alpha",
                "environmentId", "env-staging",
                "roleTags", List.of("ADMIN"),
                "ttlSeconds", 90
        ));

        assertThatThrownBy(() -> support.dispatchClaimedApiTestNodeRun(new DispatchExecutionNodeRunCommand(
                seed.nodeRun().id(),
                CLAIM_TOKEN,
                "https://api.example.test/runtime?token=must-not-store",
                null,
                null,
                null,
                null
        ))).isInstanceOf(BusinessException.class)
                .hasMessageContaining("baseUrl 不允许携带 userInfo/query/fragment");

        verify(apiAutomationService, never()).createRun(any());
        assertThat(repository.run(seed.runId())).hasValueSatisfying(run ->
                assertThat(run.status()).isEqualTo("RUNNING"));
        assertThat(repository.nodeRun(seed.nodeRun().id())).hasValueSatisfying(nodeRun -> {
            assertThat(nodeRun.status()).isEqualTo("RUNNING");
            Map<String, Object> summary = jsonSupport.readMap(nodeRun.resultSummaryJson());
            assertThat(summary)
                    .containsEntry("runnerDispatched", false)
                    .doesNotContainKey("accountLeaseAcquired")
                    .doesNotContainKey("wp6DispatchFailed");
        });
        assertThat(repository.queueClaimByToken(CLAIM_TOKEN)).hasValueSatisfying(claim ->
                assertThat(claim.status()).isEqualTo("CLAIMED"));
    }

    private SeededDispatchNode seedClaimedApiNode(UUID projectId, UUID bundleId, UUID caseId)
            throws JsonProcessingException {
        return seedClaimedApiNode(projectId, bundleId, caseId, null);
    }

    private SeededDispatchNode seedClaimedApiNode(
            UUID projectId,
            UUID bundleId,
            UUID caseId,
            Map<String, Object> accountLease
    ) throws JsonProcessingException {
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
        Map<String, Object> input = new java.util.LinkedHashMap<>(Map.of(
                "apiAutomationBundleId", bundleId.toString(),
                "baseUrlRef", "env:staging",
                "caseIds", List.of(caseId.toString()),
                "runtimeSecretRefs", List.of("secret://wp6/runtime-token")
        ));
        if (accountLease != null) {
            input.put("accountLease", accountLease);
        }
        repository.replacePlanNodes(planId, List.of(new ExecutionPlanNode(
                planNodeId,
                planId,
                "api-smoke",
                "API_TEST",
                "",
                objectMapper.writeValueAsString(input),
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
        return new SeededDispatchNode(runId, nodeRun);
    }

    private UUID seedAccountPool(UUID projectId) {
        var pool = testDataFixture.poolService().createAccountPool(new CreateTestAccountPoolCommand(
                projectId.toString(),
                "app-alpha",
                "env-staging",
                "pool-" + UUID.randomUUID(),
                "Pool alpha",
                "READY",
                Map.of("sharing", "EXCLUSIVE"),
                120
        ));
        testDataFixture.poolService().addAccount(pool.id(), new UpsertTestPooledAccountCommand(
                "admin-01",
                "Admin 01",
                "AVAILABLE",
                List.of("ADMIN"),
                Map.of("applicationId", "app-alpha"),
                "secret://wp8/accounts/admin-01",
                "HEALTHY",
                null
        ));
        return pool.id();
    }

    private TestDataFixture testDataFixture() {
        InMemoryTestDataRepository testDataRepository = new InMemoryTestDataRepository();
        TestDataPlatformContextClient testDataContextClient = mock(TestDataPlatformContextClient.class);
        when(testDataContextClient.projectContext(anyString())).thenAnswer(invocation -> {
            String projectId = invocation.getArgument(0);
            return new PlatformContext(
                    "PROJECT",
                    projectId,
                    "ACTIVE",
                    "INTERNAL",
                    false,
                    List.of("apps", "environments", "configs"),
                    Instant.now()
            );
        });
        doNothing().when(testDataContextClient).writeAuditEvent(
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyMap()
        );
        TestDataActorResolver actorResolver = mock(TestDataActorResolver.class);
        when(actorResolver.currentActor()).thenReturn("wp9-test");
        TestDataProperties testDataProperties = new TestDataProperties(true, 10, 512, 120, 300, false, true);
        TestAccountPoolService poolService = new TestAccountPoolService(
                testDataRepository,
                testDataContextClient,
                actorResolver,
                testDataProperties,
                objectMapper
        );
        TestAccountLeaseService leaseService = new TestAccountLeaseService(
                testDataRepository,
                testDataContextClient,
                actorResolver,
                testDataProperties,
                objectMapper
        );
        TestDataCrossWpReferenceService crossWpService = new TestDataCrossWpReferenceService(
                leaseService,
                testDataRepository,
                testDataContextClient,
                testDataProperties,
                objectMapper
        );
        return new TestDataFixture(poolService, crossWpService);
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

    private record SeededDispatchNode(UUID runId, ExecutionNodeRun nodeRun) {
    }

    private record TestDataFixture(
            TestAccountPoolService poolService,
            TestDataCrossWpReferenceService crossWpService
    ) {
    }
}
