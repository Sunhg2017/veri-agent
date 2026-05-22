package com.songhg.veri.agent.modelaccess.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class ModelEvaluationRunner {

    private static final String ALL_TASKS = "ALL";

    private final ObjectMapper objectMapper;

    public ModelEvaluationRunner(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public JsonNode loadCorpus(String resourcePath) throws IOException {
        InputStream input = getClass().getResourceAsStream(resourcePath);
        if (input == null) {
            throw new IllegalArgumentException("Model evaluation corpus not found: " + resourcePath);
        }
        try (input) {
            return objectMapper.readTree(input);
        }
    }

    public EvaluationSummary evaluate(JsonNode corpus, String expectedCorpusVersion, String taskTypeFilter) {
        return evaluate(corpus, expectedCorpusVersion, taskTypeFilter, scenario -> responseText(scenario.path("modelResponse")));
    }

    public EvaluationSummary evaluate(
            JsonNode corpus,
            String expectedCorpusVersion,
            String taskTypeFilter,
            Function<JsonNode, String> responseResolver
    ) {
        if (!corpus.isArray()) {
            throw new IllegalArgumentException("Model evaluation corpus must be a JSON array");
        }

        String normalizedFilter = normalizeTaskTypeFilter(taskTypeFilter);
        Map<String, QualityStats> statsByTask = new LinkedHashMap<>();
        Set<String> caseIds = new LinkedHashSet<>();
        Set<String> promptBindings = new LinkedHashSet<>();
        Set<String> providerGroups = new LinkedHashSet<>();
        int selectedScenarios = 0;

        for (JsonNode scenario : corpus) {
            ScenarioMetadata metadata = assertScenarioMetadata(scenario, expectedCorpusVersion, caseIds);
            promptBindings.add(metadata.promptKey() + ":" + metadata.promptVersion());
            providerGroups.add(metadata.providerGroup());
            if (!ALL_TASKS.equals(normalizedFilter) && !metadata.taskType().equals(normalizedFilter)) {
                continue;
            }
            selectedScenarios++;
            String output = responseResolver.apply(scenario);
            QualityStats stats = statsByTask.computeIfAbsent(metadata.taskType(), QualityStats::new);
            stats.recordScenario(scenario, output);
        }

        if (selectedScenarios == 0) {
            throw new IllegalArgumentException("No model evaluation scenarios matched taskType=" + normalizedFilter);
        }

        QualityStats total = new QualityStats(ALL_TASKS);
        statsByTask.values().forEach(total::merge);
        return new EvaluationSummary(
                expectedCorpusVersion,
                normalizedFilter,
                selectedScenarios,
                List.copyOf(statsByTask.values()),
                total,
                Set.copyOf(promptBindings),
                Set.copyOf(providerGroups)
        );
    }

    private ScenarioMetadata assertScenarioMetadata(JsonNode scenario, String expectedCorpusVersion, Set<String> caseIds) {
        String caseId = requiredText(scenario, "caseId");
        if (!caseIds.add(caseId)) {
            throw new IllegalArgumentException("Duplicate model evaluation caseId: " + caseId);
        }
        String corpusVersion = requiredText(scenario, "corpusVersion");
        if (!expectedCorpusVersion.equals(corpusVersion)) {
            throw new IllegalArgumentException("Unexpected corpusVersion for " + caseId + ": " + corpusVersion);
        }
        String taskType = requiredText(scenario, "taskType");
        String promptKey = requiredText(scenario, "promptKey");
        String promptVersion = requiredText(scenario, "promptVersion");
        String providerGroup = requiredText(scenario, "providerGroup");
        JsonNode expected = scenario.path("expected");
        if (!expected.path("requiredTerms").isArray() || expected.path("requiredTerms").isEmpty()) {
            throw new IllegalArgumentException("requiredTerms must be a non-empty array for " + caseId);
        }
        if (expected.has("forbiddenTerms") && !expected.path("forbiddenTerms").isArray()) {
            throw new IllegalArgumentException("forbiddenTerms must be an array for " + caseId);
        }
        return new ScenarioMetadata(caseId, taskType, promptKey, promptVersion, providerGroup);
    }

    private String normalizeTaskTypeFilter(String taskTypeFilter) {
        if (taskTypeFilter == null || taskTypeFilter.isBlank() || ALL_TASKS.equalsIgnoreCase(taskTypeFilter.trim())) {
            return ALL_TASKS;
        }
        return taskTypeFilter.trim();
    }

    private static String requiredText(JsonNode node, String field) {
        String value = node.path(field).asText("");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " is required in model evaluation corpus");
        }
        return value.trim();
    }

    private static String responseText(JsonNode response) {
        if (response.isMissingNode() || response.isNull()) {
            return "";
        }
        return response.isTextual() ? response.asText() : response.toString();
    }

    private static boolean containsIgnoreCase(String output, String term) {
        return output.toLowerCase(Locale.ROOT).contains(term.toLowerCase(Locale.ROOT));
    }

    private record ScenarioMetadata(
            String caseId,
            String taskType,
            String promptKey,
            String promptVersion,
            String providerGroup
    ) {
    }

    public record EvaluationSummary(
            String corpusVersion,
            String taskTypeFilter,
            int scenarioCount,
            List<QualityStats> taskStats,
            QualityStats totalStats,
            Set<String> promptBindings,
            Set<String> providerGroups
    ) {
    }

    public static class QualityStats {

        private final String taskType;
        private int scenarioCount;
        private int passedScenarios;
        private int requiredTermCount;
        private int requiredTermMatches;
        private int forbiddenTermCount;
        private int forbiddenTermMatches;
        private final List<String> failures = new ArrayList<>();

        private QualityStats(String taskType) {
            this.taskType = taskType;
        }

        private void recordScenario(JsonNode scenario, String output) {
            scenarioCount++;
            String caseId = scenario.path("caseId").asText("unknown-case");
            boolean scenarioPassed = true;
            for (JsonNode termNode : scenario.path("expected").path("requiredTerms")) {
                String term = termNode.asText("");
                if (term.isBlank()) {
                    continue;
                }
                requiredTermCount++;
                if (containsIgnoreCase(output, term)) {
                    requiredTermMatches++;
                } else {
                    scenarioPassed = false;
                    failures.add(caseId + " missing required term: " + term);
                }
            }
            for (JsonNode termNode : scenario.path("expected").path("forbiddenTerms")) {
                String term = termNode.asText("");
                if (term.isBlank()) {
                    continue;
                }
                forbiddenTermCount++;
                if (containsIgnoreCase(output, term)) {
                    scenarioPassed = false;
                    forbiddenTermMatches++;
                    failures.add(caseId + " contains forbidden term: " + term);
                }
            }
            if (scenarioPassed) {
                passedScenarios++;
            }
        }

        private void merge(QualityStats other) {
            scenarioCount += other.scenarioCount;
            passedScenarios += other.passedScenarios;
            requiredTermCount += other.requiredTermCount;
            requiredTermMatches += other.requiredTermMatches;
            forbiddenTermCount += other.forbiddenTermCount;
            forbiddenTermMatches += other.forbiddenTermMatches;
            failures.addAll(other.failures);
        }

        public String taskType() {
            return taskType;
        }

        public int scenarioCount() {
            return scenarioCount;
        }

        public int passedScenarios() {
            return passedScenarios;
        }

        public int requiredTermCount() {
            return requiredTermCount;
        }

        public int requiredTermMatches() {
            return requiredTermMatches;
        }

        public int forbiddenTermCount() {
            return forbiddenTermCount;
        }

        public int forbiddenTermMatches() {
            return forbiddenTermMatches;
        }

        public double scenarioPassRate() {
            return ratio(passedScenarios, scenarioCount);
        }

        public double requiredTermRecall() {
            return ratio(requiredTermMatches, requiredTermCount);
        }

        public double forbiddenTermCleanRate() {
            return forbiddenTermCount == 0 ? 1.0 : ratio(forbiddenTermCount - forbiddenTermMatches, forbiddenTermCount);
        }

        public List<String> failures() {
            return List.copyOf(failures);
        }

        private double ratio(int numerator, int denominator) {
            return denominator == 0 ? 0.0 : (double) numerator / denominator;
        }
    }
}
