package com.songhg.veri.agent.modelaccess.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ModelQualityEvaluationServiceTest {

    private final ModelQualityEvaluationService service = new ModelQualityEvaluationService(new ObjectMapper());

    @Test
    void returnsAggregateSummaryForAllTasks() {
        var summary = service.evaluationSummary("ALL");

        assertThat(summary.corpusVersion()).isEqualTo(ModelQualityEvaluationService.CORPUS_VERSION);
        assertThat(summary.taskTypeFilter()).isEqualTo("ALL");
        assertThat(summary.scenarioCount()).isEqualTo(6);
        assertThat(summary.totalStats().passed()).isTrue();
        assertThat(summary.taskStats()).hasSize(3);
        assertThat(summary.promptBindings()).contains("case-design:v1", "defect-triage:v1", "requirement-summary:v1");
        assertThat(summary.providerGroups()).contains("local-echo", "openai-compatible-fixture");
    }

    @Test
    void filtersOneTaskType() {
        var summary = service.evaluationSummary("case-design");

        assertThat(summary.taskTypeFilter()).isEqualTo("case-design");
        assertThat(summary.scenarioCount()).isEqualTo(2);
        assertThat(summary.taskStats()).singleElement().satisfies(item -> {
            assertThat(item.taskType()).isEqualTo("case-design");
            assertThat(item.passed()).isTrue();
        });
    }
}
