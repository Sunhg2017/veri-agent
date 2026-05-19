package com.songhg.veri.agent.documentinput.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.documentinput.domain.DocumentFieldMapping;
import com.songhg.veri.agent.documentinput.domain.DocumentSourceType;
import com.songhg.veri.agent.documentinput.domain.ParsedRequirementDraft;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
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
        int expectedCount = 0;
        int titleMatches = 0;
        int priorityMatches = 0;
        int acceptanceCovered = 0;

        for (JsonNode scenario : corpus) {
            List<ParsedRequirementDraft> parsed = parser.parse(
                    DocumentSourceType.CUSTOM_API,
                    null,
                    scenario.path("modelResponse").toString(),
                    modelMapping()
            );
            for (JsonNode expected : scenario.path("expected")) {
                expectedCount++;
                ParsedRequirementDraft matched = findByTitle(parsed, expected.path("title").asText());
                if (matched == null) {
                    continue;
                }
                titleMatches++;
                if (expected.path("priority").asText().equals(matched.priority())) {
                    priorityMatches++;
                }
                if (matched.acceptanceCriteria() != null && !matched.acceptanceCriteria().isBlank()) {
                    acceptanceCovered++;
                }
            }
        }

        double titleRecall = ratio(titleMatches, expectedCount);
        double priorityAccuracy = ratio(priorityMatches, expectedCount);
        double acceptanceCoverage = ratio(acceptanceCovered, expectedCount);
        System.out.printf(
                "WP4 AI parse quality eval: titleRecall=%.2f priorityAccuracy=%.2f acceptanceCoverage=%.2f cases=%d%n",
                titleRecall,
                priorityAccuracy,
                acceptanceCoverage,
                expectedCount
        );

        assertThat(titleRecall).isGreaterThanOrEqualTo(MIN_TITLE_RECALL);
        assertThat(priorityAccuracy).isGreaterThanOrEqualTo(MIN_PRIORITY_ACCURACY);
        assertThat(acceptanceCoverage).isGreaterThanOrEqualTo(MIN_ACCEPTANCE_COVERAGE);
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
}
