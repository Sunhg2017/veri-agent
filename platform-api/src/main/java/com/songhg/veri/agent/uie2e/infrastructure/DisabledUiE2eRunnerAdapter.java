package com.songhg.veri.agent.uie2e.infrastructure;

import com.songhg.veri.agent.uie2e.application.port.UiE2eRunnerPort;
public class DisabledUiE2eRunnerAdapter implements UiE2eRunnerPort {

    @Override
    public RunnerValidation validate(RunnerValidationRequest request) {
        return new RunnerValidation(true, null, null);
    }

    @Override
    public RunnerRunResult run(RunnerRunRequest request) {
        java.util.Map<String, Object> executionSummary = new java.util.LinkedHashMap<>();
        executionSummary.put("aggregateOnly", true);
        executionSummary.put("browserTypes", request == null || request.browserTypes() == null || request.browserTypes().isEmpty()
                ? java.util.List.of("CHROMIUM")
                : request.browserTypes());
        executionSummary.put("browserCount", request == null || request.browserTypes() == null || request.browserTypes().isEmpty()
                ? 1
                : request.browserTypes().size());
        executionSummary.put("parallelExecutionEnabled", request != null && request.browserTypes() != null && request.browserTypes().size() > 1);
        executionSummary.put("visualRegressionEnabled", request != null && request.visualRegressionEnabled());
        if (request != null && request.baselineRunId() != null) {
            executionSummary.put("visualBaselineRunId", request.baselineRunId().toString());
        }
        if (request != null && request.visualMismatchThreshold() != null) {
            executionSummary.put("visualMismatchThreshold", request.visualMismatchThreshold());
        }
        return new RunnerRunResult(
                "BLOCKED",
                "DISABLED",
                "UI_E2E_RUNNER_DISABLED",
                "WP7 runner is disabled by default",
                java.util.List.of(),
                java.util.List.of(),
                java.util.Map.copyOf(executionSummary)
        );
    }

    @Override
    public RunnerCancelResult cancel(java.util.UUID runId) {
        return new RunnerCancelResult(false, "UI_E2E_RUNNER_DISABLED", "WP7 runner is disabled by default");
    }
}
