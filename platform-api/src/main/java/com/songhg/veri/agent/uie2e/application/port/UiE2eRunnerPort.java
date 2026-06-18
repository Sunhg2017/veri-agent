package com.songhg.veri.agent.uie2e.application.port;

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
            Map<String, Object> accountSummary
    ) {
    }

    record RunnerRunRequest(
            UUID runId,
            UUID sceneId,
            UUID bundleId,
            String projectId,
            String baseUrl,
            String accountLeaseRef,
            Map<String, Object> accountSummary
    ) {
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
            String failureSummary
    ) {
    }

    record RunnerCancelResult(
            boolean accepted,
            String errorCode,
            String errorSummary
    ) {
    }
}
