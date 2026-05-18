package com.songhg.veri.agent.asset.api.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record UpdateTestCaseStepsRequest(
        @NotEmpty @Valid List<StepItem> steps
) {
    public record StepItem(String action, String expectedResult) {
    }
}
