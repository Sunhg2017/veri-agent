package com.songhg.veri.agent.apiautomation.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.apiautomation.application.view.ApiAutomationCaseResponse;
import com.songhg.veri.agent.apiautomation.application.view.ApiAutomationEndpointSnapshotResponse;
import com.songhg.veri.agent.apiautomation.application.view.ApiAutomationGenerationTaskDetailResponse;
import com.songhg.veri.agent.apiautomation.application.view.ApiAutomationGenerationTaskResponse;
import com.songhg.veri.agent.apiautomation.application.view.ApiAutomationRunDetailResponse;
import com.songhg.veri.agent.apiautomation.application.view.ApiAutomationRunResponse;
import com.songhg.veri.agent.apiautomation.application.view.ApiAutomationRunResultResponse;
import com.songhg.veri.agent.apiautomation.application.view.ApiAutomationScriptBundleResponse;
import com.songhg.veri.agent.apiautomation.application.view.ApiAutomationSpecDetailResponse;
import com.songhg.veri.agent.apiautomation.application.view.ApiAutomationSpecResponse;
import com.songhg.veri.agent.apiautomation.domain.ApiAutomationCase;
import com.songhg.veri.agent.apiautomation.domain.ApiAutomationEndpointSnapshot;
import com.songhg.veri.agent.apiautomation.domain.ApiAutomationGenerationTask;
import com.songhg.veri.agent.apiautomation.domain.ApiAutomationRun;
import com.songhg.veri.agent.apiautomation.domain.ApiAutomationRunResult;
import com.songhg.veri.agent.apiautomation.domain.ApiAutomationScriptBundle;
import com.songhg.veri.agent.apiautomation.domain.ApiAutomationSpec;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.util.StringUtils;

/**
 * Maps WP6 API automation domain snapshots into aggregate-only response DTOs.
 */
final class ApiAutomationResponseMapper {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    ApiAutomationResponseMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    ApiAutomationSpecDetailResponse toDetail(ApiAutomationSpec spec, List<ApiAutomationEndpointSnapshot> endpoints) {
        return new ApiAutomationSpecDetailResponse(
                toSpecResponse(spec),
                readSummary(spec.parseSummaryJson()),
                endpoints.stream().map(this::toEndpointResponse).toList()
        );
    }

    ApiAutomationGenerationTaskDetailResponse toGenerationTaskDetail(
            ApiAutomationGenerationTask task,
            List<ApiAutomationCase> cases,
            List<ApiAutomationScriptBundle> scriptBundles
    ) {
        return new ApiAutomationGenerationTaskDetailResponse(
                toGenerationTaskResponse(task),
                cases.stream().map(this::toAutomationCaseResponse).toList(),
                scriptBundles.stream().map(this::toScriptBundleResponse).toList()
        );
    }

    ApiAutomationGenerationTaskResponse toGenerationTaskResponse(ApiAutomationGenerationTask task) {
        return new ApiAutomationGenerationTaskResponse(
                task.id(),
                task.projectId(),
                task.specId(),
                task.requestKey(),
                task.requestDigest(),
                task.generationMode(),
                readStringList(task.coverageTypesJson()),
                task.status(),
                task.promptKey(),
                task.promptVersion(),
                task.modelInvocationId(),
                task.fallbackUsed(),
                task.apiCount(),
                task.caseCount(),
                readSummary(task.inputSummaryJson()),
                task.errorSummary(),
                task.createdAt(),
                task.updatedAt()
        );
    }

    ApiAutomationCaseResponse toAutomationCaseResponse(ApiAutomationCase automationCase) {
        return new ApiAutomationCaseResponse(
                automationCase.id(),
                automationCase.endpointSnapshotId(),
                automationCase.assetApiId(),
                automationCase.assetTestCaseId(),
                automationCase.title(),
                automationCase.httpMethod(),
                automationCase.path(),
                automationCase.coverageType(),
                automationCase.expectedStatus(),
                readSummary(automationCase.assertionSummaryJson()),
                readSummary(automationCase.requestTemplateJson()),
                automationCase.source(),
                automationCase.status(),
                automationCase.createdAt(),
                automationCase.updatedAt()
        );
    }

    ApiAutomationScriptBundleResponse toScriptBundleResponse(ApiAutomationScriptBundle bundle) {
        return new ApiAutomationScriptBundleResponse(
                bundle.id(),
                bundle.projectId(),
                bundle.taskId(),
                bundle.status(),
                bundle.bundleDigest(),
                bundle.fileCount(),
                readSummary(bundle.fileTreeSummaryJson()),
                readSummary(bundle.dependencySummaryJson()),
                bundle.staticCheckStatus(),
                readSummary(bundle.staticCheckSummaryJson()),
                bundle.reviewNote(),
                bundle.submittedBy(),
                bundle.approvedBy(),
                bundle.submittedAt(),
                bundle.approvedAt(),
                bundle.rejectedAt(),
                bundle.createdAt(),
                bundle.updatedAt()
        );
    }

    ApiAutomationRunDetailResponse toRunDetail(ApiAutomationRun run, List<ApiAutomationRunResult> results) {
        return new ApiAutomationRunDetailResponse(
                toRunResponse(run),
                results.stream().map(this::toRunResultResponse).toList()
        );
    }

    ApiAutomationRunResponse toRunResponse(ApiAutomationRun run) {
        return new ApiAutomationRunResponse(
                run.id(),
                run.projectId(),
                run.bundleId(),
                run.environmentId(),
                run.baseUrlDigest(),
                run.baseUrlHost(),
                run.status(),
                run.timeoutSeconds(),
                run.caseCount(),
                run.traceId(),
                run.runnerMode(),
                run.errorCode(),
                run.errorSummary(),
                run.startedAt(),
                run.completedAt(),
                run.createdAt(),
                run.updatedAt()
        );
    }

    ApiAutomationRunResultResponse toRunResultResponse(ApiAutomationRunResult result) {
        return new ApiAutomationRunResultResponse(
                result.id(),
                result.runId(),
                result.caseId(),
                result.status(),
                result.durationMs(),
                readSummary(result.assertionSummaryJson()),
                result.errorCode(),
                result.errorSummary(),
                result.createdAt(),
                result.updatedAt()
        );
    }

    Map<String, Integer> resultCounts(List<ApiAutomationRunResult> results) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        results.forEach(result -> counts.merge(result.status(), 1, Integer::sum));
        return counts;
    }

    Map<String, Object> runExportRedactionPolicy() {
        return Map.of(
                "rawBaseUrlExported", false,
                "baseUrlDigestExported", true,
                "baseUrlHostExported", true,
                "rawRequestResponseExported", false,
                "stdoutStderrExported", false,
                "secretValuesExported", false,
                "assertionSummaryAggregateOnly", true
        );
    }

    ApiAutomationSpecResponse toSpecResponse(ApiAutomationSpec spec) {
        return new ApiAutomationSpecResponse(
                spec.id(),
                spec.projectId(),
                spec.sourceType(),
                spec.sourceRef(),
                spec.name(),
                spec.versionLabel(),
                spec.specDigest(),
                spec.contentSizeBytes(),
                spec.status(),
                spec.parserVersion(),
                spec.endpointCount(),
                spec.parseErrorSummary(),
                spec.parsedAt(),
                spec.createdAt(),
                spec.updatedAt()
        );
    }

    ApiAutomationEndpointSnapshotResponse toEndpointResponse(ApiAutomationEndpointSnapshot snapshot) {
        return new ApiAutomationEndpointSnapshotResponse(
                snapshot.id(),
                snapshot.serviceName(),
                snapshot.operationId(),
                snapshot.httpMethod(),
                snapshot.path(),
                snapshot.summary(),
                snapshot.tags(),
                snapshot.parameterCount(),
                snapshot.requestBodyPresent(),
                snapshot.responseStatuses(),
                snapshot.schemaDigest(),
                snapshot.diffStatus(),
                snapshot.assetApiId(),
                readSummary(snapshot.diffSummaryJson()),
                snapshot.lastDiffAt(),
                snapshot.syncedAt(),
                snapshot.syncErrorSummary()
        );
    }

    private Map<String, Object> readSummary(String value) {
        if (!StringUtils.hasText(value)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(value, MAP_TYPE);
        } catch (JsonProcessingException exception) {
            // Malformed persisted summaries must not expose raw payloads; surface aggregate-safe evidence only.
            return Map.of("parseSummaryUnreadable", true, "aggregateOnly", true);
        }
    }

    private List<String> readStringList(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(value, STRING_LIST_TYPE);
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }
}
