package com.songhg.veri.agent.modelaccess.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreatePromptRequest(
        @NotBlank String promptKey,
        @NotBlank String name,
        @NotBlank @Size(max = 12000) String content,
        String changeNote,
        Boolean activate
) {
}
