package com.songhg.veri.agent.testdesign.application.view;

public record TestDesignStepResponse(
        int stepOrder,
        String action,
        String expectedResult
) {
}
