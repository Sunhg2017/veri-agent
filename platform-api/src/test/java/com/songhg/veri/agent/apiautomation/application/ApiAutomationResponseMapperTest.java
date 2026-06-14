package com.songhg.veri.agent.apiautomation.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.apiautomation.application.view.ApiAutomationGenerationTaskResponse;
import com.songhg.veri.agent.apiautomation.application.view.ApiAutomationRunResultResponse;
import com.songhg.veri.agent.apiautomation.domain.ApiAutomationGenerationTask;
import com.songhg.veri.agent.apiautomation.domain.ApiAutomationRunResult;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ApiAutomationResponseMapperTest {

    private final ApiAutomationResponseMapper mapper = new ApiAutomationResponseMapper(new ObjectMapper());

    @Test
    void mapsGenerationTaskWithCoverageTypesAndAggregateInputSummary() {
        ApiAutomationGenerationTask task = new ApiAutomationGenerationTask(
                UUID.randomUUID(),
                "project-alpha",
                UUID.randomUUID(),
                "request-key",
                "request-digest",
                "MODEL_WITH_FALLBACK",
                "[\"SMOKE\",\"FUNCTIONAL\"]",
                "COMPLETED",
                "wp6-api-automation-v1",
                "7",
                "model-invocation-id",
                false,
                2,
                4,
                "{\"aggregateOnly\":true,\"apiCount\":2}",
                null,
                "tester",
                "tester",
                Instant.EPOCH,
                Instant.EPOCH
        );

        ApiAutomationGenerationTaskResponse response = mapper.toGenerationTaskResponse(task);

        assertThat(response.coverageTypes()).containsExactly("SMOKE", "FUNCTIONAL");
        assertThat(response.inputSummary())
                .containsEntry("aggregateOnly", true)
                .containsEntry("apiCount", 2);
    }

    @Test
    void mapsUnreadableRunResultSummaryAsAggregateOnlyEvidence() {
        ApiAutomationRunResult result = new ApiAutomationRunResult(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "FAILED",
                125,
                "{not-json",
                "ASSERTION_FAILED",
                "status mismatch",
                Instant.EPOCH,
                Instant.EPOCH
        );

        ApiAutomationRunResultResponse response = mapper.toRunResultResponse(result);

        assertThat(response.assertionSummary())
                .containsEntry("parseSummaryUnreadable", true)
                .containsEntry("aggregateOnly", true);
    }

    @Test
    void runExportHelpersKeepCountsAndRedactionPolicyAggregateOnly() {
        List<ApiAutomationRunResult> results = List.of(
                runResult("PASSED"),
                runResult("PASSED"),
                runResult("FAILED")
        );

        assertThat(mapper.resultCounts(results))
                .containsEntry("PASSED", 2)
                .containsEntry("FAILED", 1);
        assertThat(mapper.runExportRedactionPolicy())
                .containsEntry("rawRequestResponseExported", false)
                .containsEntry("stdoutStderrExported", false)
                .containsEntry("secretValuesExported", false)
                .containsEntry("assertionSummaryAggregateOnly", true);
    }

    private ApiAutomationRunResult runResult(String status) {
        return new ApiAutomationRunResult(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                status,
                10,
                "{}",
                null,
                null,
                Instant.EPOCH,
                Instant.EPOCH
        );
    }
}
