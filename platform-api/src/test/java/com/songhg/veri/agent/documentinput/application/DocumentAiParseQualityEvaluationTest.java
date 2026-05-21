package com.songhg.veri.agent.documentinput.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.documentinput.domain.DocumentFieldMapping;
import com.songhg.veri.agent.documentinput.domain.DocumentSourceType;
import com.songhg.veri.agent.documentinput.domain.ParsedRequirementDraft;
import java.io.InputStream;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentAiParseQualityEvaluationTest {

    private static final int MIN_CORPUS_SIZE = 12;
    private static final int MIN_CASES_PER_SOURCE_TYPE = 2;
    private static final double MIN_TITLE_RECALL = 0.80;
    private static final double MIN_PRIORITY_ACCURACY = 0.80;
    private static final double MIN_ACCEPTANCE_COVERAGE = 0.75;
    private static final String CORPUS_VERSION = "wp4-c1-2026-05-22";
    private static final Set<String> REQUIRED_INDUSTRIES = Set.of(
            "platform",
            "finance",
            "retail",
            "healthcare",
            "manufacturing",
            "logistics"
    );
    private static final Set<String> REQUIRED_COVERAGE_TAGS = Set.of(
            "long-document",
            "table-requirement",
            "ambiguous-priority",
            "abnormal-format",
            "low-confidence"
    );

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DocumentRequirementParser parser = new DocumentRequirementParser(objectMapper);

    @Test
    void modelParseFixtureCorpusMeetsQualityGate() throws Exception {
        InputStream input = getClass().getResourceAsStream("/wp4-ai-parse-eval/corpus.json");
        assertThat(input).isNotNull();
        JsonNode corpus;
        try (input) {
            corpus = objectMapper.readTree(input);
        }
        assertThat(corpus).hasSizeGreaterThanOrEqualTo(MIN_CORPUS_SIZE);
        Map<String, QualityStats> statsByType = new LinkedHashMap<>();
        QualityStats totalStats = new QualityStats("ALL");
        Set<String> caseIds = new LinkedHashSet<>();
        Set<String> industries = new LinkedHashSet<>();
        Set<String> coverageTags = new LinkedHashSet<>();

        for (JsonNode scenario : corpus) {
            assertScenarioMetadata(scenario, caseIds, industries, coverageTags);
            String sourceTypeName = scenario.path("sourceType").asText("CUSTOM_API");
            DocumentSourceType sourceType = DocumentSourceType.valueOf(sourceTypeName);
            QualityStats typeStats = statsByType.computeIfAbsent(sourceTypeName, QualityStats::new);
            totalStats.caseCount++;
            typeStats.caseCount++;
            List<ParsedRequirementDraft> parsed = parser.parse(
                    sourceType,
                    null,
                    scenario.path("modelResponse").toString(),
                    modelMapping()
            );
            for (JsonNode expected : scenario.path("expected")) {
                totalStats.expectedCount++;
                typeStats.expectedCount++;
                ParsedRequirementDraft matched = findByTitle(parsed, expected.path("title").asText());
                if (matched == null) {
                    continue;
                }
                totalStats.titleMatches++;
                typeStats.titleMatches++;
                if (expected.path("priority").asText().equals(matched.priority())) {
                    totalStats.priorityMatches++;
                    typeStats.priorityMatches++;
                }
                if (matched.acceptanceCriteria() != null && !matched.acceptanceCriteria().isBlank()) {
                    totalStats.acceptanceCovered++;
                    typeStats.acceptanceCovered++;
                }
            }
        }

        String promptKey = corpus.get(0).path("promptKey").asText();
        String promptVersion = corpus.get(0).path("promptVersion").asText();
        assertThat(promptKey).isEqualTo("wp4-document-requirement-parse");
        assertThat(promptVersion).isEqualTo("v1");
        assertThat(industries)
                .as("WP4-C1 corpus industry coverage")
                .containsAll(REQUIRED_INDUSTRIES);
        assertThat(coverageTags)
                .as("WP4-C1 corpus scenario coverage")
                .containsAll(REQUIRED_COVERAGE_TAGS);
        System.out.printf(
                "WP4 AI parse quality eval: promptKey=%s promptVersion=%s parserVersion=rule-json-v1%n",
                promptKey,
                promptVersion
        );
        printStats(totalStats);
        statsByType.values().forEach(this::printStats);

        assertThat(statsByType.keySet())
                .containsExactlyInAnyOrder("TEXT", "MARKDOWN", "WORD", "PDF", "OCR", "CUSTOM_API");
        assertStats(totalStats);
        statsByType.values().forEach(this::assertStats);
        statsByType.values().forEach(stats -> assertThat(stats.caseCount)
                .as("case count for " + stats.sourceType)
                .isGreaterThanOrEqualTo(MIN_CASES_PER_SOURCE_TYPE));
    }

    private void assertScenarioMetadata(
            JsonNode scenario,
            Set<String> caseIds,
            Set<String> industries,
            Set<String> coverageTags
    ) {
        String caseId = scenario.path("caseId").asText();
        assertThat(caseId).as("caseId").isNotBlank();
        assertThat(caseIds.add(caseId)).as("unique caseId " + caseId).isTrue();
        assertThat(scenario.path("corpusVersion").asText()).as("corpusVersion for " + caseId)
                .isEqualTo(CORPUS_VERSION);
        assertThat(scenario.path("industry").asText()).as("industry for " + caseId).isNotBlank();
        assertThat(scenario.path("difficulty").asText()).as("difficulty for " + caseId)
                .isIn("basic", "medium", "hard");
        assertThat(scenario.path("coverageTags")).as("coverageTags for " + caseId).isNotEmpty();
        assertThat(scenario.path("promptKey").asText()).as("promptKey for " + caseId)
                .isEqualTo("wp4-document-requirement-parse");
        assertThat(scenario.path("promptVersion").asText()).as("promptVersion for " + caseId)
                .isEqualTo("v1");

        industries.add(scenario.path("industry").asText());
        scenario.path("coverageTags").forEach(tag -> coverageTags.add(tag.asText()));
    }

    private ParsedRequirementDraft findByTitle(List<ParsedRequirementDraft> parsed, String expectedTitle) {
        return parsed.stream()
                .filter(draft -> expectedTitle.equalsIgnoreCase(draft.title()))
                .findFirst()
                .orElse(null);
    }

    private double ratio(int numerator, int denominator) {
        return denominator == 0 ? 0.0 : (double) numerator / denominator;
    }

    private void printStats(QualityStats stats) {
        System.out.printf(
                "WP4 AI parse quality eval type=%s titleRecall=%.2f priorityAccuracy=%.2f acceptanceCoverage=%.2f scenarios=%d expectedRequirements=%d%n",
                stats.sourceType,
                ratio(stats.titleMatches, stats.expectedCount),
                ratio(stats.priorityMatches, stats.expectedCount),
                ratio(stats.acceptanceCovered, stats.expectedCount),
                stats.caseCount,
                stats.expectedCount
        );
    }

    private void assertStats(QualityStats stats) {
        assertThat(ratio(stats.titleMatches, stats.expectedCount))
                .as("title recall for " + stats.sourceType)
                .isGreaterThanOrEqualTo(MIN_TITLE_RECALL);
        assertThat(ratio(stats.priorityMatches, stats.expectedCount))
                .as("priority accuracy for " + stats.sourceType)
                .isGreaterThanOrEqualTo(MIN_PRIORITY_ACCURACY);
        assertThat(ratio(stats.acceptanceCovered, stats.expectedCount))
                .as("acceptance coverage for " + stats.sourceType)
                .isGreaterThanOrEqualTo(MIN_ACCEPTANCE_COVERAGE);
    }

    private DocumentFieldMapping modelMapping() {
        Instant now = Instant.now();
        return new DocumentFieldMapping(
                UUID.fromString("00000000-0000-0000-0000-0000000004a1"),
                "wp4-ai-eval",
                "WP4 AI evaluation mapping",
                "requirements",
                "title",
                "description",
                "priority",
                "acceptanceCriteria",
                "tags",
                now,
                now
        );
    }

    private static class QualityStats {

        private final String sourceType;
        private int caseCount;
        private int expectedCount;
        private int titleMatches;
        private int priorityMatches;
        private int acceptanceCovered;

        private QualityStats(String sourceType) {
            this.sourceType = sourceType;
        }
    }
}
