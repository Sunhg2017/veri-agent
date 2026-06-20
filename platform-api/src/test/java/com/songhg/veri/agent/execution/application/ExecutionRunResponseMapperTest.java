package com.songhg.veri.agent.execution.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.execution.application.view.ExecutionRunArtifactResponse;
import com.songhg.veri.agent.execution.application.view.ExecutionRunDetailResponse;
import com.songhg.veri.agent.execution.application.view.ExecutionRunSummaryResponse;
import com.songhg.veri.agent.execution.domain.ExecutionNodeRun;
import com.songhg.veri.agent.execution.domain.ExecutionPlanNode;
import com.songhg.veri.agent.execution.domain.ExecutionRun;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExecutionRunResponseMapperTest {

    private final ExecutionRunResponseMapper mapper = new ExecutionRunResponseMapper(new ObjectMapper());

    @Test
    void mapsRunDetailWithPlanNodeMetadataAndUnreadableSummaryFallback() {
        UUID runId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        UUID apiNodeId = UUID.randomUUID();
        ExecutionRun run = run(runId, planId, "{\"nodeCount\":2,\"runnerDispatched\":true}");
        ExecutionNodeRun apiNodeRun = nodeRun(runId, apiNodeId, "SUCCEEDED", "{\"durationMs\":120}");
        ExecutionNodeRun orphanNodeRun = nodeRun(runId, UUID.randomUUID(), "FAILED", "{not-json");
        ExecutionPlanNode apiNode = planNode(apiNodeId, planId, "api-smoke", "API_TEST");
        ExecutionRunArtifactResponse artifact = new ExecutionRunArtifactResponse(
                UUID.randomUUID(),
                apiNodeRun.id(),
                apiNodeId,
                "api-smoke",
                "API_TEST",
                "WP6_API",
                "WP6_API_AUTOMATION",
                "LOG",
                "artifact-digest",
                128,
                "CAPTURED",
                false,
                Map.of("aggregateOnly", true),
                Instant.EPOCH,
                Instant.EPOCH
        );

        ExecutionRunDetailResponse response = mapper.toDetail(
                run,
                true,
                List.of(apiNodeRun, orphanNodeRun),
                List.of(apiNode),
                List.of(artifact)
        );

        assertThat(response.id()).isEqualTo(runId);
        assertThat(response.idempotentReplay()).isTrue();
        assertThat(response.resultSummary())
                .containsEntry("nodeCount", 2)
                .containsEntry("runnerDispatched", true);
        assertThat(response.nodes()).hasSize(2);
        assertThat(response.artifacts()).singleElement().satisfies(item -> {
            assertThat(item.sourceType()).isEqualTo("WP6_API_AUTOMATION");
            assertThat(item.artifactType()).isEqualTo("LOG");
        });
        assertThat(response.nodes().getFirst()).satisfies(node -> {
            assertThat(node.nodeKey()).isEqualTo("api-smoke");
            assertThat(node.nodeType()).isEqualTo("API_TEST");
            assertThat(node.resultSummary()).containsEntry("durationMs", 120);
        });
        assertThat(response.nodes().get(1)).satisfies(node -> {
            assertThat(node.nodeKey()).isNull();
            assertThat(node.nodeType()).isNull();
            assertThat(node.resultSummary()).containsEntry("unreadable", true);
        });
    }

    @Test
    void mapsRunSummaryAndExportHelpersWithoutRawEvidence() {
        ExecutionRun run = run(UUID.randomUUID(), UUID.randomUUID(), "{not-json");
        ExecutionRunSummaryResponse summary = mapper.toSummary(run, 3);
        ExecutionRunDetailResponse detail = mapper.toDetail(
                run,
                false,
                List.of(
                        nodeRun(run.id(), UUID.randomUUID(), "SUCCEEDED", "{}"),
                        nodeRun(run.id(), UUID.randomUUID(), "SUCCEEDED", "{}"),
                        nodeRun(run.id(), UUID.randomUUID(), "FAILED", "{}")
                ),
                List.of()
        );

        assertThat(summary.resultSummary()).containsEntry("unreadable", true);
        assertThat(summary.nodeCount()).isEqualTo(3);
        assertThat(mapper.nodeStatusCounts(detail))
                .containsEntry("SUCCEEDED", 2)
                .containsEntry("FAILED", 1);
        assertThat(mapper.runExportRedactionPolicy())
                .containsEntry("rawOutputExported", false)
                .containsEntry("stdoutStderrExported", false)
                .containsEntry("rawRequestResponseExported", false)
                .containsEntry("secretRefsExported", false)
                .containsEntry("claimTokenExported", false)
                .containsEntry("artifactManifestExported", true)
                .containsEntry("rawArtifactDownloadExported", false)
                .containsEntry("onlySanitizedSummariesExported", true);
    }

    private ExecutionRun run(UUID id, UUID planId, String resultSummaryJson) {
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
                resultSummaryJson,
                null,
                null,
                "tester",
                Instant.EPOCH,
                null,
                Instant.EPOCH,
                Instant.EPOCH
        );
    }

    private ExecutionNodeRun nodeRun(UUID runId, UUID planNodeId, String status, String resultSummaryJson) {
        return new ExecutionNodeRun(
                UUID.randomUUID(),
                runId,
                planNodeId,
                status,
                1,
                "WP6_API",
                null,
                null,
                null,
                resultSummaryJson,
                Instant.EPOCH,
                Instant.EPOCH,
                Instant.EPOCH,
                null,
                Instant.EPOCH,
                Instant.EPOCH
        );
    }

    private ExecutionPlanNode planNode(UUID id, UUID planId, String nodeKey, String nodeType) {
        return new ExecutionPlanNode(
                id,
                planId,
                nodeKey,
                nodeType,
                "",
                "{}",
                "FAIL_FAST",
                180,
                "{}",
                Instant.EPOCH,
                Instant.EPOCH
        );
    }
}
