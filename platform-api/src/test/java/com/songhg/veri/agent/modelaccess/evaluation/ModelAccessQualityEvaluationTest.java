package com.songhg.veri.agent.modelaccess.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ModelAccessQualityEvaluationTest {

    private static final String CORPUS_PATH = "/wp2-model-eval/corpus.json";
    private static final String CORPUS_VERSION = "wp2-d1-2026-05-22";
    private static final String TASK_TYPE_PROPERTY = "wp2.model.eval.taskType";
    private static final int MIN_ALL_SCENARIOS = 6;
    private static final double MIN_SCENARIO_PASS_RATE = 1.0;
    private static final double MIN_REQUIRED_TERM_RECALL = 0.90;
    private static final double MIN_FORBIDDEN_TERM_CLEAN_RATE = 1.0;
    private static final Set<String> REQUIRED_TASK_TYPES = Set.of(
            "case-design",
            "defect-triage",
            "requirement-summary"
    );

    private final ModelEvaluationRunner runner = new ModelEvaluationRunner(new ObjectMapper());

    @Test
    void genericModelEvaluationCorpusMeetsQualityGate() throws Exception {
        JsonNode corpus = runner.loadCorpus(CORPUS_PATH);
        String taskType = System.getProperty(TASK_TYPE_PROPERTY, "ALL");
        ModelEvaluationRunner.EvaluationSummary summary = runner.evaluate(corpus, CORPUS_VERSION, taskType);

        System.out.printf(
                "WP2 model quality eval: corpusVersion=%s taskType=%s scenarios=%d promptBindings=%s providerGroups=%s%n",
                summary.corpusVersion(),
                summary.taskTypeFilter(),
                summary.scenarioCount(),
                summary.promptBindings(),
                summary.providerGroups()
        );
        printStats(summary.totalStats());
        summary.taskStats().forEach(this::printStats);

        if ("ALL".equals(summary.taskTypeFilter())) {
            assertThat(summary.scenarioCount()).isGreaterThanOrEqualTo(MIN_ALL_SCENARIOS);
            assertThat(summary.taskStats())
                    .extracting(ModelEvaluationRunner.QualityStats::taskType)
                    .containsExactlyInAnyOrderElementsOf(REQUIRED_TASK_TYPES);
        } else {
            assertThat(summary.taskStats())
                    .extracting(ModelEvaluationRunner.QualityStats::taskType)
                    .containsExactly(summary.taskTypeFilter());
        }

        assertThat(summary.promptBindings()).contains("case-design:v1", "defect-triage:v1", "requirement-summary:v1");
        assertThat(summary.providerGroups()).contains("local-echo", "openai-compatible-fixture");
        assertStats(summary.totalStats());
        summary.taskStats().forEach(this::assertStats);
    }

    private void printStats(ModelEvaluationRunner.QualityStats stats) {
        System.out.printf(
                "WP2 model quality eval taskType=%s scenarioPassRate=%.2f requiredTermRecall=%.2f forbiddenTermCleanRate=%.2f scenarios=%d requiredTerms=%d%n",
                stats.taskType(),
                stats.scenarioPassRate(),
                stats.requiredTermRecall(),
                stats.forbiddenTermCleanRate(),
                stats.scenarioCount(),
                stats.requiredTermCount()
        );
    }

    private void assertStats(ModelEvaluationRunner.QualityStats stats) {
        assertThat(stats.scenarioPassRate())
                .as("scenario pass rate for " + stats.taskType() + " failures=" + stats.failures())
                .isGreaterThanOrEqualTo(MIN_SCENARIO_PASS_RATE);
        assertThat(stats.requiredTermRecall())
                .as("required term recall for " + stats.taskType() + " failures=" + stats.failures())
                .isGreaterThanOrEqualTo(MIN_REQUIRED_TERM_RECALL);
        assertThat(stats.forbiddenTermCleanRate())
                .as("forbidden term clean rate for " + stats.taskType() + " failures=" + stats.failures())
                .isGreaterThanOrEqualTo(MIN_FORBIDDEN_TERM_CLEAN_RATE);
    }
}
