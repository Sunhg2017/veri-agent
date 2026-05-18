package com.songhg.veri.agent.management.api.response;

public record DepartmentView(
        String name,
        String parent,
        String lead,
        int members,
        String status
) {
}
