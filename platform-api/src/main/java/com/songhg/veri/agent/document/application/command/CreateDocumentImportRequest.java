package com.songhg.veri.agent.document.application.command;

import com.songhg.veri.agent.document.domain.DocumentSourceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateDocumentImportRequest(
        @NotBlank
        String projectId,
        @NotNull
        DocumentSourceType sourceType,
        String title,
        String sourceRef,
        String sourceUrl,
        @NotBlank
        String content,
        UUID mappingId,
        UUID sourceId
) {
}
