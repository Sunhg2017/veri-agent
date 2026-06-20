package com.songhg.veri.agent.uie2e.application.port;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface UiE2eRunnerPort {

    RunnerValidation validate(RunnerValidationRequest request);

    RunnerRunResult run(RunnerRunRequest request);

    RunnerCancelResult cancel(UUID runId);

    record RunnerValidationRequest(
            UUID sceneId,
            UUID bundleId,
            String projectId,
            String baseUrl,
            String accountLeaseRef,
            Map<String, Object> accountSummary,
            List<String> browserTypes,
            boolean visualRegressionEnabled,
            UUID baselineRunId,
            Double visualMismatchThreshold
    ) {

        public RunnerValidationRequest(
                UUID sceneId,
                UUID bundleId,
                String projectId,
                String baseUrl,
                String accountLeaseRef,
                Map<String, Object> accountSummary
        ) {
            this(sceneId, bundleId, projectId, baseUrl, accountLeaseRef, accountSummary, List.of(), false, null, null);
        }
    }

    record RunnerRunRequest(
            UUID runId,
            UUID sceneId,
            UUID bundleId,
            String projectId,
            String baseUrl,
            String accountLeaseRef,
            Map<String, Object> accountSummary,
            List<String> browserTypes,
            boolean visualRegressionEnabled,
            UUID baselineRunId,
            Double visualMismatchThreshold
    ) {

        public RunnerRunRequest(
                UUID runId,
                UUID sceneId,
                UUID bundleId,
                String projectId,
                String baseUrl,
                String accountLeaseRef,
                Map<String, Object> accountSummary
        ) {
            this(runId, sceneId, bundleId, projectId, baseUrl, accountLeaseRef, accountSummary, List.of(), false, null, null);
        }
    }

    record RunnerValidation(
            boolean accepted,
            String errorCode,
            String errorSummary
    ) {
    }

    record RunnerRunResult(
            String status,
            String runnerMode,
            String failureCode,
            String failureSummary,
            List<RunnerStepResult> stepResults,
            List<RunnerArtifactManifest> artifacts,
            Map<String, Object> executionSummary
    ) {

        public RunnerRunResult(
                String status,
                String runnerMode,
                String failureCode,
                String failureSummary,
                List<RunnerStepResult> stepResults,
                List<RunnerArtifactManifest> artifacts
        ) {
            this(status, runnerMode, failureCode, failureSummary, stepResults, artifacts, Map.of());
        }
    }

    record RunnerStepResult(
            UUID sceneStepId,
            int stepOrder,
            String status,
            int durationMs,
            String failureBucket,
            String errorCode,
            Map<String, Object> summary
    ) {
    }

    record RunnerArtifactManifest(
            String artifactType,
            String storageRef,
            String artifactDigest,
            long sizeBytes,
            Map<String, Object> redactionFlags,
            String captureStatus
    ) {
    }

    record RunnerCancelResult(
            boolean accepted,
            String errorCode,
            String errorSummary
    ) {
    }
}
