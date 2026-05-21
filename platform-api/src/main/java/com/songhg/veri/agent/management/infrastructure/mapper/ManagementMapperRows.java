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

    public record EnvironmentConnectivityTargetRow(
            UUID id,
            String name,
            String status,
            String webUrl,
            String apiBaseUrl,
            String healthCheckJson
    ) {
    }

    public record IntegrationRow(String configKey, String key, String name, String category, String scope, String status) {
    }

    public record SettingRow(String configKey, String scopeType, String displayName, String value, String status) {
    }

    public record SecretProviderRow(UUID id, String providerCode, String providerType, String status) {
    }

    public record SecretReferenceRow(
            UUID id,
            String secretRef,
            String providerCode,
            String providerType,
            String purpose,
            String scopeType,
            UUID scopeId,
            String secretVersion,
            String status
    ) {
    }
}
