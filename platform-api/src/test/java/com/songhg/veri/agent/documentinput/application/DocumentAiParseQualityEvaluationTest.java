package com.songhg.veri.agent.documentinput.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.documentinput.domain.DocumentFieldMapping;
import com.songhg.veri.agent.documentinput.domain.DocumentSourceType;
import com.songhg.veri.agent.documentinput.domain.ParsedRequirementDraft;
import java.io.InputStream;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentAiParseQualityEvaluationTest {

    private static final double MIN_TITLE_RECALL = 0.80;
    private static final double MIN_PRIORITY_ACCURACY = 0.80;
    private static final double MIN_ACCEPTANCE_COVERAGE = 0.75;

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
        assertThat(corpus).hasSizeGreaterThanOrEqualTo(5);
        Map<String, QualityStats> statsByType = new LinkedHashMap<>();
        QualityStats totalStats = new QualityStats("ALL");

        for (JsonNode scenario : corpus) {
            String sourceTypeName = scenario.path("sourceType").asText("CUSTOM_API");
            DocumentSourceType sourceType = DocumentSourceType.valueOf(sourceTypeName);
            QualityStats typeStats = statsByType.computeIfAbsent(sourceTypeName, QualityStats::new);
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
                "WP4 AI parse quality eval type=%s titleRecall=%.2f priorityAccuracy=%.2f acceptanceCoverage=%.2f cases=%d%n",
                stats.sourceType,
                ratio(stats.titleMatches, stats.expectedCount),
                ratio(stats.priorityMatches, stats.expectedCount),
                ratio(stats.acceptanceCovered, stats.expectedCount),
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
        private int expectedCount;
        private int titleMatches;
        private int priorityMatches;
        private int acceptanceCovered;

        private QualityStats(String sourceType) {
            this.sourceType = sourceType;
        }
    }
}
