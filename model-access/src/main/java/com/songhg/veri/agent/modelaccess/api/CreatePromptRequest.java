package com.songhg.veri.agent.modelaccess.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreatePromptRequest(
        @NotBlank @JsonProperty("prompt_key") String promptKey,
        @NotBlank String name,
        @NotBlank @Size(max = 12000) String content,
        @JsonProperty("change_note") String changeNote,
        Boolean activate
) {
}
