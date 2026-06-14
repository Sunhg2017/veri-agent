package com.songhg.veri.agent.execution.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.execution.application.command.CompleteExecutionNodeRunCommand;
import com.songhg.veri.agent.execution.application.view.ExecutionQueueRecoveryResponse;
import com.songhg.veri.agent.execution.application.view.ExecutionRunDetailResponse;
import com.songhg.veri.agent.execution.config.ExecutionProperties;
import com.songhg.veri.agent.execution.domain.ExecutionNodeRun;
import com.songhg.veri.agent.execution.domain.ExecutionPlan;
import com.songhg.veri.agent.execution.domain.ExecutionPlanNode;
import com.songhg.veri.agent.execution.domain.ExecutionQueueClaim;
import com.songhg.veri.agent.execution.domain.ExecutionRun;
import com.songhg.veri.agent.execution.infrastructure.InMemoryExecutionRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ExecutionRunQueueSupportTest {

    private static final String CLAIM_TOKEN = "wp9_claim_test_token";

    private final InMemoryExecutionRepository repository = new InMemoryExecutionRepository();
    private final ExecutionPlatformContextClient contextClient = mock(ExecutionPlatformContextClient.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutionRunJsonSupport jsonSupport = new ExecutionRunJsonSupport(objectMapper);
    private final ExecutionRunQueueSupport support = new ExecutionRunQueueSupport(
            repository,
            contextClient,
            properties(),
            jsonSupport,
            new ExecutionRunResponseMapper(objectMapper)
    );

    @Test
    void completeClaimedNodeRunDropsRunnerPayloadsAndSanitizesEvidence() {
        SeededNode seed = seedRunningNode(Instant.now().minusSeconds(30), 300);
        repository.tryInsertQueueClaim(claim(seed.nodeRun().id(), Instant.now().plusSeconds(60)));

        ExecutionRunDetailResponse response = support.completeClaimedNodeRun(new CompleteExecutionNodeRunCommand(
                seed.nodeRun().id(),
                CLAIM_TOKEN,
                "FAILED",
                "ASSERTION_FAILED",
                "runner failed token=secret-value at https://api.example.test/private",
                Map.of(
                        "safeText", "failed against https://api.example.test/private with token=secret-value",
                        "stdout", "raw stdout should not be stored",
                        "requestBody", "{\"password\":\"secret-value\"}",
                        "nested", Map.of(
                                "safeNested", "authorization=Bearer secret-value",
                                "responseBody", "{\"token\":\"secret-value\"}"
                        ),
                        "items", IntStream.range(0, 25).boxed().toList()
                )
        ));

        assertThat(response.status()).isEqualTo("FAILED");
        assertThat(response.errorCode()).isEqualTo("EXECUTION_RUN_FAILED");
        assertThat(response.nodes()).singleElement().satisfies(node -> {
            Map<String, Object> resultSummary = node.resultSummary();
            assertThat(node.status()).isEqualTo("FAILED");
            assertThat(node.errorCode()).isEqualTo("ASSERTION_FAILED");
            assertThat(node.errorSummary())
                    .doesNotContain("secret-value")
                    .doesNotContain("https://api.example.test/private");
            assertThat(resultSummary)
                    .containsEntry("completedStatus", "FAILED")
                    .containsEntry("rawOutputStored", false)
                    .containsEntry("runnerDispatched", false)
                    .doesNotContainKeys("stdout", "requestBody");
            assertThat(String.valueOf(resultSummary.get("safeText")))
                    .doesNotContain("secret-value")
                    .doesNotContain("https://api.example.test/private");
            assertThat(resultSummary.get("nested"))
                    .isInstanceOf(Map.class);
            Map<?, ?> nested = (Map<?, ?>) resultSummary.get("nested");
            assertThat(nested.containsKey("safeNested")).isTrue();
            assertThat(nested.containsKey("responseBody")).isFalse();
            assertThat(resultSummary.get("items"))
                    .isInstanceOf(List.class);
            List<?> items = (List<?>) resultSummary.get("items");
            assertThat(items)
                    .hasSize(21)
                    .last()
                    .isEqualTo("...");
        });
        assertThat(repository.queueClaimByToken(CLAIM_TOKEN)).hasValueSatisfying(claim ->
                assertThat(claim.status()).isEqualTo("COMPLETED"));
    }

    @Test
    void recoverExpiredQueueClaimRequeuesNodeBeforeExecutionTimeout() {
        SeededNode seed = seedRunningNode(Instant.now().minusSeconds(10), 300);
        repository.tryInsertQueueClaim(claim(seed.nodeRun().id(), Instant.now().minusSeconds(5)));

        ExecutionQueueRecoveryResponse response = support.recoverExpiredQueueClaims();

        assertThat(response.expiredClaimCount()).isEqualTo(1);
        assertThat(response.requeuedNodeCount()).isEqualTo(1);
        assertThat(response.timedOutNodeCount()).isZero();
        assertThat(response.aggregatedRunCount()).isEqualTo(1);
        assertThat(repository.queueClaimByToken(CLAIM_TOKEN)).hasValueSatisfying(claim ->
                assertThat(claim.status()).isEqualTo("EXPIRED"));
        assertThat(repository.nodeRun(seed.nodeRun().id())).hasValueSatisfying(nodeRun -> {
            assertThat(nodeRun.status()).isEqualTo("QUEUED");
            assertThat(jsonSupport.readMap(nodeRun.resultSummaryJson()))
                    .containsEntry("claimExpired", true)
                    .containsEntry("recoveryAction", "REQUEUED")
                    .containsEntry("runnerDispatched", false);
        });
        assertThat(repository.run(seed.run().id())).hasValueSatisfying(run ->
                assertThat(run.status()).isEqualTo("RUNNING"));
    }

    private SeededNode seedRunningNode(Instant startedAt, int timeoutSeconds) {
        UUID planId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        UUID planNodeId = UUID.randomUUID();
        Instant now = Instant.now();
        repository.insertPlan(new ExecutionPlan(
                planId,
                "project-alpha",
                "Release smoke",
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
                "{}",
                "FAIL_FAST",
                timeoutSeconds,
                "{}",
                now.minusSeconds(120),
                now.minusSeconds(120)
        )));
        ExecutionRun run = new ExecutionRun(
                runId,
                planId,
                "project-alpha",
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
                startedAt,
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
                startedAt,
                null,
                now.minusSeconds(90),
                now.minusSeconds(5)
        );
        repository.insertRun(run);
        repository.insertNodeRuns(List.of(nodeRun));
        return new SeededNode(run, nodeRun);
    }

    private ExecutionQueueClaim claim(UUID nodeRunId, Instant expiresAt) {
        Instant now = Instant.now();
        return new ExecutionQueueClaim(
                UUID.randomUUID(),
                nodeRunId,
                CLAIM_TOKEN,
                "worker-a",
                now.minusSeconds(20),
                now.minusSeconds(10),
                expiresAt,
                "CLAIMED",
                now.minusSeconds(20),
                now.minusSeconds(10)
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

    private record SeededNode(ExecutionRun run, ExecutionNodeRun nodeRun) {
    }
}
