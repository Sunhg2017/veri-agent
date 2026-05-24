package com.songhg.veri.agent.asset.application.view;

public record TestCaseStepResponse(
        int stepOrder,
        String action,
        String expectedResult
) {
}
