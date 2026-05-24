package com.songhg.veri.agent.asset.application;

public record TestCaseStepResponse(
        int stepOrder,
        String action,
        String expectedResult
) {
}
