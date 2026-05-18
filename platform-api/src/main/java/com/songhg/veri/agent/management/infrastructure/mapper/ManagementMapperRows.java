package com.songhg.veri.agent.management.infrastructure.mapper;

import java.util.UUID;

public final class ManagementMapperRows {

    private ManagementMapperRows() {
    }

    public record DepartmentRef(UUID id, String name, String status) {
    }

    public record ProjectRef(UUID id, String name, String status) {
    }

    public record ApplicationRef(UUID id, String name, String status, UUID projectId, String projectName) {
    }

    public record EnvironmentRef(UUID id, String name, String status, UUID projectId, String projectName) {
    }

    public record IntegrationRow(String configKey, String key, String name, String category, String scope, String status) {
    }

    public record SettingRow(String configKey, String scopeType, String displayName, String value, String status) {
    }
}
