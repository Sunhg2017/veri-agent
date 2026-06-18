package com.songhg.veri.agent.uie2e.infrastructure;

import com.songhg.veri.agent.uie2e.application.port.UiE2eRunnerPort;
import org.springframework.stereotype.Component;

@Component
public class DisabledUiE2eRunnerAdapter implements UiE2eRunnerPort {

    @Override
    public RunnerValidation validate(RunnerValidationRequest request) {
        return new RunnerValidation(true, null, null);
    }

    @Override
    public RunnerRunResult run(RunnerRunRequest request) {
        return new RunnerRunResult(
                "BLOCKED",
                "DISABLED",
                "UI_E2E_RUNNER_DISABLED",
                "WP7 runner is disabled by default"
        );
    }

    @Override
    public RunnerCancelResult cancel(java.util.UUID runId) {
        return new RunnerCancelResult(false, "UI_E2E_RUNNER_DISABLED", "WP7 runner is disabled by default");
    }
}
