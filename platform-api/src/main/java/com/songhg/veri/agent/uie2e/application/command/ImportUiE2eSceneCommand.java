package com.songhg.veri.agent.uie2e.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ImportUiE2eSceneCommand(
        @NotBlank @Size(max = 64) String projectId,
        @Size(max = 64) String applicationId,
        @Size(max = 64) String environmentId,
        @NotBlank @Size(max = 32) String sourceType,
        @NotBlank @Size(max = 200000) String content,
        @Size(max = 128) String codeHint,
        @Size(max = 128) String nameHint,
        List<@Size(max = 32) String> tags
) {
}
