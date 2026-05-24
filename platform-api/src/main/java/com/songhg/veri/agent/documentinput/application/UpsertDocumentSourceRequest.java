package com.songhg.veri.agent.documentinput.application;

import com.songhg.veri.agent.documentinput.domain.DocumentSourceStatus;
import com.songhg.veri.agent.documentinput.domain.DocumentSourceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record UpsertDocumentSourceRequest(
        @NotBlank
        @Size(max = 64)
        String sourceCode,
        @NotBlank
        @Size(max = 128)
        String name,
        @NotNull
        DocumentSourceType sourceType,
        DocumentSourceStatus status,
        String endpointUrl,
        String defaultProjectId,
        UUID mappingId,
        @Size(max = 128)
        String secretRef,
        @Size(max = 32)
        String eventVersion,
        @Size(max = 64)
        String mappingVersion,
        String description
) {
}
