package com.songhg.veri.agent.modelaccess.application.command;

/**
 * Application command for creating a prompt template version.
 */
public record CreatePromptCommand(
        String promptKey,
        String name,
        String content,
        String changeNote,
        Boolean highRisk,
        Boolean activate
) {
}
