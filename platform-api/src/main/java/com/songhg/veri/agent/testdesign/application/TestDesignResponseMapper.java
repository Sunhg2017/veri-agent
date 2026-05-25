package com.songhg.veri.agent.testdesign.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.testdesign.application.view.TestDesignCandidateResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignPublishRecordResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignStepResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignTaskResponse;
import com.songhg.veri.agent.testdesign.domain.TestDesignCandidate;
import com.songhg.veri.agent.testdesign.domain.TestDesignPublishRecord;
import com.songhg.veri.agent.testdesign.domain.TestDesignTask;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class TestDesignResponseMapper {

    private final ObjectMapper objectMapper;

    public TestDesignResponseMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public TestDesignTaskResponse toTaskResponse(TestDesignTask task) {
        return new TestDesignTaskResponse(
                task.id(),
                task.projectId(),
                task.title(),
                task.status(),
                uuidList(task.requirementIds()),
                stringList(task.coverageTypes()),
                task.promptKey(),
                task.promptVersion(),
                task.modelInvocationId(),
                task.modelProviderName(),
                task.modelName(),
                task.totalRequirements(),
                task.generatedCount(),
                task.confirmedCount(),
                task.publishedCount(),
                task.errorMessage(),
                task.requestedBy(),
                task.idempotencyKey(),
                task.createdAt(),
                task.updatedAt()
        );
    }

    public TestDesignCandidateResponse toCandidateResponse(TestDesignCandidate candidate) {
        return new TestDesignCandidateResponse(
                candidate.id(),
                candidate.taskId(),
                candidate.projectId(),
                candidate.requirementId(),
                candidate.apiId(),
                candidate.title(),
                candidate.description(),
                candidate.coverageType(),
                candidate.priority(),
                candidate.status(),
                candidate.preconditions(),
                steps(candidate.stepsJson()),
                candidate.expectedResult(),
                stringList(candidate.tags()),
                candidate.duplicateKey(),
                candidate.confidence(),
                candidate.promptKey(),
                candidate.promptVersion(),
                candidate.modelInvocationId(),
                candidate.modelProviderName(),
                candidate.modelName(),
                candidate.assetCaseId(),
                candidate.reviewComment(),
                candidate.rejectedReason(),
                candidate.ignoredReason(),
                candidate.errorMessage(),
                candidate.confirmedBy(),
                candidate.confirmedAt(),
                candidate.version(),
                candidate.createdAt(),
                candidate.updatedAt()
        );
    }

    public TestDesignPublishRecordResponse toPublishRecordResponse(
            TestDesignPublishRecord record,
            TestDesignCandidate candidate
    ) {
        return new TestDesignPublishRecordResponse(
                record.id(),
                record.taskId(),
                record.candidateId(),
                candidate == null ? null : candidate.title(),
                record.projectId(),
                record.requirementId(),
                record.assetCaseId(),
                record.dryRun(),
                record.action(),
                record.result(),
                record.errorMessage(),
                record.publishedBy(),
                record.createdAt()
        );
    }

    public List<TestDesignStepResponse> steps(String stepsJson) {
        if (!StringUtils.hasText(stepsJson)) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(stepsJson);
            if (!root.isArray()) {
                return List.of();
            }
            java.util.ArrayList<TestDesignStepResponse> steps = new java.util.ArrayList<>();
            for (JsonNode item : root) {
                steps.add(new TestDesignStepResponse(
                        item.path("stepOrder").asInt(steps.size()),
                        text(item.path("action")),
                        text(item.path("expectedResult"))
                ));
            }
            return List.copyOf(steps);
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }

    public String stepsJson(List<TestDesignStepResponse> steps) {
        try {
            return objectMapper.writeValueAsString(steps == null ? List.of() : steps);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize test design steps", exception);
        }
    }

    private static List<String> stringList(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        return Arrays.stream(value.replace('，', ',').split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private static List<UUID> uuidList(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        return stringList(value).stream()
                .map(UUID::fromString)
                .toList();
    }

    private static String text(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? null : node.asText();
    }
}
