package com.songhg.veri.agent.modelaccess.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

public record ReviewPromptRequest(
        @Schema(description = "评审说明")
        @Size(max = 512) String reviewNote
) {
}
