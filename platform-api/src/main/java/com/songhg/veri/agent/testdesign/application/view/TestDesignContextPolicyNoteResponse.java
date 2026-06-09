package com.songhg.veri.agent.testdesign.application.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * Single WP5 context policy approval timeline note.
 */
public record TestDesignContextPolicyNoteResponse(
        @Schema(description = "备注 ID")
        UUID id,
        @Schema(description = "策略覆盖记录 ID")
        UUID overrideId,
        @Schema(description = "备注类型")
        String noteType,
        @Schema(description = "备注正文")
        String noteText,
        @Schema(description = "创建人")
        String createdBy,
        @Schema(description = "创建时间")
        Instant createdAt
) {
}
