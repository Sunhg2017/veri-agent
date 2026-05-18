package com.songhg.veri.agent.management.api.request;

import jakarta.validation.constraints.Size;

public record UpdateDepartmentRequest(
        @Size(max = 64)
        String name
) {
}
