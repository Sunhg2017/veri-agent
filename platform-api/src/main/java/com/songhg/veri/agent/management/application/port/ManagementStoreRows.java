package com.songhg.veri.agent.management.application.port;

import java.util.UUID;

/**
 * Lightweight persistence rows returned by {@link ManagementStore}. They represent identifiers and
 * status codes needed by use-case services, not HTTP response DTOs.
 */
public final class ManagementStoreRows {

    private ManagementStoreRows() {
    }

    public record DepartmentRef(UUID id, String name, String status) {
    }

    public record RoleRow(
            UUID id,
            String code,
            String name,
            String scopeType,
            boolean system,
            boolean builtin,
            String status,
            String description,
            long version
    ) {
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
