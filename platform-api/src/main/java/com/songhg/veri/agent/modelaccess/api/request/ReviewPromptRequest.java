package com.songhg.veri.agent.modelaccess.api.request;

import jakarta.validation.constraints.Size;

public record ReviewPromptRequest(
        @Size(max = 512) String reviewNote
) {
}
