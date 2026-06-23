package com.songhg.veri.agent.apiautomation.infrastructure;

import com.songhg.veri.agent.apiautomation.application.port.ApiAutomationRunnerPort;
import com.songhg.veri.agent.apiautomation.domain.ApiAutomationScriptBundle;
import java.util.List;
import java.util.UUID;

public class DisabledApiAutomationRunnerAdapter implements ApiAutomationRunnerPort {

    @Override
    public RunnerValidation validateBundle(ApiAutomationScriptBundle bundle) {
        return new RunnerValidation(true, null, null);
    }

    @Override
    public RunnerRunResult run(RunnerRunRequest request) {
        return new RunnerRunResult(
                "BLOCKED",
                "DISABLED",
                "RUNNER_DISABLED",
                "WP6 runner is disabled by default",
                List.of()
        );
    }

    @Override
    public RunnerCancelResult cancel(RunnerCancelRequest request) {
        return new RunnerCancelResult(false, "RUNNER_DISABLED", "WP6 runner is disabled by default");
    }

    @Override
    public RunnerCancelResult cancel(UUID runId) {
        return cancel(new RunnerCancelRequest(runId, null, null));
    }
}
