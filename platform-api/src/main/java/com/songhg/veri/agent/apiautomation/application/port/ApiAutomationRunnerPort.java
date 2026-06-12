package com.songhg.veri.agent.apiautomation.application.port;

import com.songhg.veri.agent.apiautomation.domain.ApiAutomationCase;
import com.songhg.veri.agent.apiautomation.domain.ApiAutomationScriptBundle;
import java.util.List;
import java.util.UUID;

public interface ApiAutomationRunnerPort {

    RunnerValidation validateBundle(ApiAutomationScriptBundle bundle);

    RunnerRunResult run(RunnerRunRequest request);

    RunnerCancelResult cancel(UUID runId);

    record RunnerValidation(
            boolean accepted,
            String errorCode,
            String errorSummary
    ) {
    }

    record RunnerRunRequest(
            UUID runId,
            ApiAutomationScriptBundle bundle,
            List<ApiAutomationCase> cases,
            String baseUrl,
            int timeoutSeconds,
            List<String> secretRefDigests,
            List<RunnerSecret> secrets
    ) {
    }

    record RunnerSecret(
            String headerName,
            String secretRefDigest,
            String value
    ) {
        @Override
        public String toString() {
            return "RunnerSecret[headerName=%s, secretRefDigest=%s, value=****]"
                    .formatted(headerName, secretRefDigest);
        }
    }

    record RunnerRunResult(
            String status,
            String runnerMode,
            String errorCode,
            String errorSummary,
            List<RunnerCaseResult> caseResults
    ) {
    }

    record RunnerCaseResult(
            UUID caseId,
            String status,
            int durationMs,
            String assertionSummaryJson,
            String errorCode,
            String errorSummary
    ) {
    }

    record RunnerCancelResult(
            boolean accepted,
            String errorCode,
            String errorSummary
    ) {
    }
}
