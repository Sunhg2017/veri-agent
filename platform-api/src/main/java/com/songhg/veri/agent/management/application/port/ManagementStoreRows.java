package com.songhg.veri.agent.management.application.port;

import java.util.UUID;

/**
 * Lightweight persistence rows returned by {@link ManagementStore}. They represent identifiers and
 * status codes needed by use-case services, not HTTP response DTOs.
 */
public final class ManagementStoreRows {

    private ManagementStoreRows() {
    }

    public record DepartmentRef(
            /** 部门主键 ID */
            UUID id,
            /** 部门名称 */
            String name,
            /** 部门状态 */
            String status
    ) {
    }

    public record RoleRow(
            /** 角色主键 ID */
            UUID id,
            /** 角色编码 */
            String code,
            /** 角色名称 */
            String name,
            /** 角色适用范围类型 */
            String scopeType,
            /** 是否系统角色 */
            boolean system,
            /** 是否内置角色 */
            boolean builtin,
            /** 角色状态 */
            String status,
            /** 角色说明 */
            String description,
            /** 角色版本号，用于并发控制 */
            long version
    ) {
    }

    public record ProjectRef(
            /** 项目主键 ID */
            UUID id,
            /** 项目名称 */
            String name,
            /** 项目状态 */
            String status
    ) {
    }

    public record ApplicationRef(
            /** 应用主键 ID */
            UUID id,
            /** 应用名称 */
            String name,
            /** 应用状态 */
            String status,
            /** 所属项目 ID */
            UUID projectId,
            /** 所属项目名称 */
            String projectName
    ) {
    }

    public record EnvironmentRef(
            /** 环境主键 ID */
            UUID id,
            /** 环境名称 */
            String name,
            /** 环境状态 */
            String status,
            /** 所属项目 ID */
            UUID projectId,
            /** 所属项目名称 */
            String projectName
    ) {
    }

    public record EnvironmentRuntimeRef(
            /** 环境主键 ID */
            UUID id,
            /** 所属项目 ID，用于跨工作包执行调度的 scope 校验 */
            UUID projectId,
            /** 环境业务编码 */
            String code,
            /** 环境名称 */
            String name,
            /** 环境 API 基础地址 */
            String apiBaseUrl,
            /** 环境状态 */
            String status
    ) {
    }

    public record EnvironmentConnectivityTargetRow(
            /** 环境主键 ID */
            UUID id,
            /** 环境名称 */
            String name,
            /** 环境状态 */
            String status,
            /** 环境 Web 访问地址 */
            String webUrl,
            /** 环境 API 基础地址 */
            String apiBaseUrl,
            /** 健康检查配置 JSON */
            String healthCheckJson
    ) {
    }

    public record IntegrationRow(
            /** 集成配置键 */
            String configKey,
            /** 集成业务键 */
            String key,
            /** 集成名称 */
            String name,
            /** 集成分类 */
            String category,
            /** 集成适用范围 */
            String scope,
            /** 集成状态 */
            String status
    ) {
    }

    public record SettingRow(
            /** 设置配置键 */
            String configKey,
            /** 设置范围类型 */
            String scopeType,
            /** 设置展示名称 */
            String displayName,
            /** 设置值 */
            String value,
            /** 设置状态 */
            String status
    ) {
    }

    public record SecretProviderRow(
            /** 密钥提供方主键 ID */
            UUID id,
            /** 密钥提供方编码 */
            String providerCode,
            /** 密钥提供方类型 */
            String providerType,
            /** 密钥提供方状态 */
            String status
    ) {
    }

    public record SecretReferenceRow(
            /** 密钥引用主键 ID */
            UUID id,
            /** 密钥引用编码 */
            String secretRef,
            /** 密钥提供方编码 */
            String providerCode,
            /** 密钥提供方类型 */
            String providerType,
            /** 密钥用途 */
            String purpose,
            /** 密钥适用范围类型 */
            String scopeType,
            /** 密钥适用范围 ID */
            UUID scopeId,
            /** 密钥版本 */
            String secretVersion,
            /** 密钥引用状态 */
            String status
    ) {
    }
}
