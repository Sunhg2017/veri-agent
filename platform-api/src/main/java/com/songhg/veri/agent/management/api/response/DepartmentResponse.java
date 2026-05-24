package com.songhg.veri.agent.management.api.response;

public record DepartmentResponse(
        String name,
        String parent,
        String lead,
        int members,
        String status
) {
}
