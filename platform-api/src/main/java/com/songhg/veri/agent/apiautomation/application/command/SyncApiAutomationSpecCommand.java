package com.songhg.veri.agent.apiautomation.application.command;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public record SyncApiAutomationSpecCommand(
        @Schema(description = "可选 endpoint snapshot ID 列表；为空时同步全部可同步差异")
        List<UUID> endpointIds,
        @Schema(description = "是否同步 CHANGED endpoint；默认 true")
        Boolean includeChanged
) {
    public Set<UUID> endpointIdSet() {
        if (endpointIds == null || endpointIds.isEmpty()) {
            return Set.of();
        }
        return endpointIds.stream().collect(Collectors.toUnmodifiableSet());
    }

    public boolean shouldIncludeChanged() {
        return includeChanged == null || includeChanged;
    }
}
