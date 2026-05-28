package com.songhg.veri.agent.testdesign.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.modelaccess.application.port.ModelAccessRepository;
import com.songhg.veri.agent.modelaccess.application.port.ModelInvocationJobRepository;
import com.songhg.veri.agent.modelaccess.application.view.ModelInvocationJobRecord;
import com.songhg.veri.agent.modelaccess.domain.InvocationRecord;
import com.songhg.veri.agent.testdesign.application.view.TestDesignCandidateResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignModelObservationResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignPublishRecordResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignStepResponse;
import com.songhg.veri.agent.testdesign.application.view.TestDesignTaskResponse;
import com.songhg.veri.agent.testdesign.domain.TestDesignCandidate;
import com.songhg.veri.agent.testdesign.domain.TestDesignPublishRecord;
import com.songhg.veri.agent.testdesign.domain.TestDesignTask;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class TestDesignResponseMapper {

    private final ObjectMapper objectMapper;
    private final ModelAccessRepository modelAccessRepository;
    private final ModelInvocationJobRepository modelInvocationJobRepository;

    public TestDesignResponseMapper(
            ObjectMapper objectMapper,
            ModelAccessRepository modelAccessRepository,
            ModelInvocationJobRepository modelInvocationJobRepository
    ) {
        this.objectMapper = objectMapper;
        this.modelAccessRepository = modelAccessRepository;
        this.modelInvocationJobRepository = modelInvocationJobRepository;
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
                task.inputDigest(),
                modelObservation(task),
                jsonMap(task.contextSummaryJson()),
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

    private TestDesignModelObservationResponse modelObservation(TestDesignTask task) {
        UUID invocationId = task.modelInvocationId();
        if (invocationId == null) {
            return null;
        }
        Optional<InvocationRecord> invocation = modelAccessRepository.invocation(invocationId);
        Optional<ModelInvocationJobRecord> job = modelInvocationJobRepository.jobByInvocationId(invocationId);
        if (invocation.isEmpty()) {
            return unavailableModelObservation(task, job.orElse(null));
        }
        return modelObservation(invocation.get(), job.orElse(null));
    }

    /**
     * Maps WP2 invocation metadata into the WP5 task contract without copying prompt/request/response previews.
     */
    private static TestDesignModelObservationResponse modelObservation(
            InvocationRecord record,
            ModelInvocationJobRecord job
    ) {
        return new TestDesignModelObservationResponse(
                record.id(),
                job == null ? null : job.jobId(),
                job == null ? null : job.traceId(),
                true,
                record.status() == null ? null : record.status().name(),
                record.providerName(),
                record.modelName(),
                record.routingRuleName(),
                record.routingGroup(),
                record.modelCapability(),
                record.fallbackUsed(),
                record.inputTokens(),
                record.outputTokens(),
                record.totalCost(),
                record.latencyMs(),
                record.errorCode(),
                safeText(record.errorMessage()),
                record.actorService(),
                record.createdAt()
        );
    }

    private static TestDesignModelObservationResponse unavailableModelObservation(
            TestDesignTask task,
            ModelInvocationJobRecord job
    ) {
        return new TestDesignModelObservationResponse(
                task.modelInvocationId(),
                job == null ? null : job.jobId(),
                job == null ? null : job.traceId(),
                false,
                job == null ? "NOT_FOUND" : job.status().name(),
                task.modelProviderName(),
                task.modelName(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                job == null ? "MODEL_INVOCATION_NOT_FOUND" : job.errorCode(),
                safeText(job == null ? "模型调用日志暂不可用" : job.errorMessage()),
                job == null ? null : job.actorService(),
                job == null ? null : job.createdAt()
        );
    }

    private Map<String, Object> jsonMap(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(rawValue, new TypeReference<>() {
            });
        } catch (JsonProcessingException exception) {
            return Map.of();
        }
    }

    private static String text(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? null : node.asText();
    }

    private static String safeText(String value) {
        return TestDesignSensitiveText.redact(value);
    }
}
