package com.songhg.veri.agent.documentinput.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateFieldMappingRequest(
        @Size(max = 128)
        String name,
        String itemPath,
        @NotBlank
        String titlePath,
        String descriptionPath,
        String priorityPath,
        String acceptanceCriteriaPath,
        String tagsPath
) {
}
