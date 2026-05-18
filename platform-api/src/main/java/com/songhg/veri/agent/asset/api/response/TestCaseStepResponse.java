package com.songhg.veri.agent.asset.api.response;

public record TestCaseStepResponse(
        int stepOrder,
        String action,
        String expectedResult
) {
}
