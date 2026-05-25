package com.songhg.veri.agent.testdesign.application.command;

public record TestDesignCandidateActionCommand(
        Long version,
        String reason,
        String comment
) {
}
