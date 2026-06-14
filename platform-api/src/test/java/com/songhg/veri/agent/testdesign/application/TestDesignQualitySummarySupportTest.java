package com.songhg.veri.agent.testdesign.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.modelaccess.infrastructure.InMemoryModelAccessRepository;
import com.songhg.veri.agent.modelaccess.infrastructure.InMemoryModelInvocationJobRepository;
import com.songhg.veri.agent.testdesign.application.view.TestDesignQualityDistributionItemResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignQualityMetricResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignQualityReadinessCheckResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignQualityReadinessResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignQualitySummaryResponse;
import com.songhg.veri.agent.testdesign.config.TestDesignProperties;
import com.songhg.veri.agent.testdesign.domain.TestDesignCandidate;
import com.songhg.veri.agent.testdesign.domain.TestDesignCandidateStatus;
import com.songhg.veri.agent.testdesign.domain.TestDesignTask;
import com.songhg.veri.agent.testdesign.domain.TestDesignTaskStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class TestDesignQualitySummarySupportTest {

    private final TestDesignProperties properties = properties();
    private final TestDesignResponseMapper responseMapper = new TestDesignResponseMapper(
            new ObjectMapper().findAndRegisterModules(),
            new InMemoryModelAccessRepository(),
            new InMemoryModelInvocationJobRepository(),
            properties
    );
    private final TestDesignQualitySummarySupport support =
            new TestDesignQualitySummarySupport(responseMapper, properties);

    @Test
    void summarizesAggregateCountersAndReadinessWithoutCandidateText() {
        UUID taskId = UUID.randomUUID();
        UUID requirementId = UUID.randomUUID();
        TestDesignTask task = task(taskId, "raw prompt token=secret-value model input");
        Instant generatedAt = Instant.parse("2026-06-14T10:00:00Z");

        TestDesignQualitySummaryResponse summary = support.qualitySummary(task, List.of(
                candidate(taskId, requirementId, "Generated", TestDesignCandidateStatus.GENERATED.name(),
                        completeSteps(), "最终预期", "duplicate-key", 0.95D, null),
                candidate(taskId, null, "Edited", TestDesignCandidateStatus.EDITED.name(),
                        incompleteSteps(), null, "duplicate-key", 0.70D, null),
                candidate(taskId, requirementId, " ", TestDesignCandidateStatus.CONFIRMED.name(),
                        completeSteps(), "最终预期", "unique-key", 0.90D, null),
                candidate(taskId, requirementId, "Failed", TestDesignCandidateStatus.FAILED.name(),
                        null, "最终预期", null, 0.60D, "publish failed")
        ), generatedAt);

        assertThat(summary.total()).isEqualTo(4);
        assertThat(summary.reviewableCount()).isEqualTo(2);
        assertThat(summary.publishableCount()).isEqualTo(2);
        assertThat(summary.failedCount()).isEqualTo(1);
        assertThat(summary.confirmedCount()).isEqualTo(1);
        assertThat(summary.stepCompleteCount()).isEqualTo(2);
        assertThat(summary.expectedCompleteCount()).isEqualTo(3);
        assertThat(summary.lowConfidenceCount()).isEqualTo(2);
        assertThat(summary.errorCount()).isEqualTo(1);
        assertThat(summary.missingRequirementCount()).isEqualTo(1);
        assertThat(summary.missingTitleCount()).isEqualTo(1);
        assertThat(summary.duplicateKeyCollisionCount()).isEqualTo(2);
        assertThat(summary.generatedAt()).isEqualTo(generatedAt);
        assertThat(summary.taskTitle())
                .doesNotContain("raw prompt")
                .doesNotContain("model input")
                .doesNotContain("secret-value");

        assertThat(summary.readiness().status()).isEqualTo("BLOCKED");
        assertThat(summary.readiness().blockingCount()).isEqualTo(5);
        assertThat(summary.readiness().warningCount()).isEqualTo(2);
        assertThat(summary.readiness().checks())
                .extracting(TestDesignQualityReadinessCheckResponse::code,
                        TestDesignQualityReadinessCheckResponse::status)
                .contains(tuple("stepComplete", "FAILED"), tuple("lowConfidence", "FAILED"));

        assertThat(summary.metrics())
                .extracting(TestDesignQualityMetricResponse::code,
                        TestDesignQualityMetricResponse::count,
                        TestDesignQualityMetricResponse::percent)
                .contains(tuple("stepComplete", 2L, 50.0D), tuple("expectedComplete", 3L, 75.0D));
        assertThat(summary.distributions().get("status"))
                .extracting(TestDesignQualityDistributionItemResponse::label,
                        TestDesignQualityDistributionItemResponse::count,
                        TestDesignQualityDistributionItemResponse::percent)
                .contains(
                        tuple(TestDesignCandidateStatus.CONFIRMED.name(), 1L, 25.0D),
                        tuple(TestDesignCandidateStatus.EDITED.name(), 1L, 25.0D),
                        tuple(TestDesignCandidateStatus.FAILED.name(), 1L, 25.0D),
                        tuple(TestDesignCandidateStatus.GENERATED.name(), 1L, 25.0D)
                );
    }

    @Test
    void readinessPassesWhenAggregateCountersMeetConfiguredThresholds() {
        TestDesignQualityReadinessResponse readiness = support.qualityReadiness(
                3,
                3,
                3,
                0,
                0,
                0,
                0,
                0
        );

        assertThat(readiness.status()).isEqualTo("PASSED");
        assertThat(readiness.blockingCount()).isZero();
        assertThat(readiness.warningCount()).isZero();
        assertThat(readiness.checks())
                .extracting(TestDesignQualityReadinessCheckResponse::status)
                .containsOnly("PASSED");
    }

    private static TestDesignTask task(UUID taskId, String title) {
        Instant now = Instant.parse("2026-06-14T09:00:00Z");
        return new TestDesignTask(
                taskId,
                "project-wp5",
                title,
                TestDesignTaskStatus.SUCCEEDED.name(),
                "",
                "SMOKE",
                "wp5-test-design-v1",
                "1.0.0",
                null,
                null,
                null,
                1,
                4,
                1,
                0,
                null,
                "tester",
                "idem-key",
                "request-digest",
                "input-digest",
                "{}",
                now,
                now
        );
    }

    private static TestDesignCandidate candidate(
            UUID taskId,
            UUID requirementId,
            String title,
            String status,
            String stepsJson,
            String expectedResult,
            String duplicateKey,
            double confidence,
            String errorMessage
    ) {
        Instant now = Instant.parse("2026-06-14T09:05:00Z");
        return new TestDesignCandidate(
                UUID.randomUUID(),
                taskId,
                "project-wp5",
                requirementId,
                null,
                title,
                "候选正文不会进入质量摘要",
                "SMOKE",
                "HIGH",
                status,
                "前置条件",
                stepsJson,
                expectedResult,
                "wp5",
                duplicateKey,
                confidence,
                "wp5-test-design-v1",
                "1.0.0",
                null,
                null,
                "RULE_TEMPLATE",
                null,
                null,
                null,
                null,
                errorMessage,
                null,
                null,
                1L,
                now,
                now
        );
    }

    private static String completeSteps() {
        return "[{\"action\":\"执行操作\",\"expectedResult\":\"看到结果\"}]";
    }

    private static String incompleteSteps() {
        return "[{\"action\":\"执行操作\",\"expectedResult\":\"\"}]";
    }

    private static TestDesignProperties properties() {
        return new TestDesignProperties(
                "test-design-token",
                true,
                "RULE_TEMPLATE",
                "wp5-test-design-v1",
                "1.0.0",
                false,
                20,
                3,
                5,
                5,
                5,
                240,
                240,
                240,
                100,
                true,
                true,
                true,
                100,
                600,
                120,
                true,
                100,
                600,
                120,
                100D,
                100D,
                20D,
                0D,
                0,
                0,
                0,
                false,
                0.86D,
                0.90D,
                true,
                50,
                180,
                false,
                true
        );
    }
}
