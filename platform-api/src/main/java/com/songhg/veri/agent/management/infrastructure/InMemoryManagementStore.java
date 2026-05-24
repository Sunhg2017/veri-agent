package com.songhg.veri.agent.management.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.authorization.application.PermissionCodes;
import com.songhg.veri.agent.common.audit.AuditLogWriter;
import com.songhg.veri.agent.common.audit.InMemoryAuditLogWriter;
import com.songhg.veri.agent.management.application.port.ManagementStore;
import com.songhg.veri.agent.management.application.port.ManagementStoreRows.ApplicationRef;
import com.songhg.veri.agent.management.application.port.ManagementStoreRows.DepartmentRef;
import com.songhg.veri.agent.management.application.port.ManagementStoreRows.EnvironmentConnectivityTargetRow;
import com.songhg.veri.agent.management.application.port.ManagementStoreRows.EnvironmentRef;
import com.songhg.veri.agent.management.application.port.ManagementStoreRows.IntegrationRow;
import com.songhg.veri.agent.management.application.port.ManagementStoreRows.ProjectRef;
import com.songhg.veri.agent.management.application.port.ManagementStoreRows.RoleRow;
import com.songhg.veri.agent.management.application.port.ManagementStoreRows.SecretProviderRow;
import com.songhg.veri.agent.management.application.port.ManagementStoreRows.SecretReferenceRow;
import com.songhg.veri.agent.management.application.port.ManagementStoreRows.SettingRow;
import com.songhg.veri.agent.management.application.view.ApplicationView;
import com.songhg.veri.agent.management.application.view.AuditLogView;
import com.songhg.veri.agent.management.application.view.AuditOutboxView;
import com.songhg.veri.agent.management.application.view.DepartmentView;
import com.songhg.veri.agent.management.application.view.EnvironmentView;
import com.songhg.veri.agent.management.application.view.IntegrationView;
import com.songhg.veri.agent.management.application.view.PermissionView;
import com.songhg.veri.agent.management.application.view.ProjectMemberView;
import com.songhg.veri.agent.management.application.view.ProjectView;
import com.songhg.veri.agent.management.application.view.RoleView;
import com.songhg.veri.agent.management.application.view.ScopedUserRoleView;
import com.songhg.veri.agent.management.application.view.SecretReferenceView;
import com.songhg.veri.agent.management.application.view.UserView;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

/**
 * Local-profile store adapter for management screens and controller tests. It intentionally mirrors
 * the {@link ManagementStore} contract used by MyBatis so local and db profiles share one set of
 * management business services.
 */
@Profile("local")
@Component
public class InMemoryManagementStore implements ManagementStore {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ObjectMapper objectMapper;
    private final List<DepartmentRecord> departments = new ArrayList<>();
    private final List<UserRecord> users = new ArrayList<>();
    private final List<RoleRecord> roles = new ArrayList<>();
    private final List<PermissionRecord> permissions = new ArrayList<>();
    private final List<RoleBindingRecord> roleBindings = new ArrayList<>();
    private final List<ProjectRecord> projects = new ArrayList<>();
    private final List<ProjectMemberRecord> projectMembers = new ArrayList<>();
    private final List<ApplicationRecord> applications = new ArrayList<>();
    private final List<EnvironmentRecord> environments = new ArrayList<>();
    private final List<ConfigRecord> configs = new ArrayList<>();
    private final List<AuditEntry> seededAuditLogs = new ArrayList<>();
    private final List<AuditOutboxView> auditOutbox = new ArrayList<>();
    private final List<SecretProviderRecord> secretProviders = new ArrayList<>();
    private final List<SecretReferenceRecord> secretReferences = new ArrayList<>();
    private final Map<UUID, Map<String, Object>> localSecretStores = new LinkedHashMap<>();

    public InMemoryManagementStore(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        seedDepartments();
        seedPermissions();
        seedRoles();
        seedUsers();
        seedProjects();
        seedApplications();
        seedEnvironments();
        seedConfigs();
        seedAudit();
        seedSecrets();
    }

    @Override
    public synchronized List<DepartmentView> listDepartments(Map<String, Object> params) {
        return page(departments.stream()
                .map(this::departmentView)
                .filter(view -> matches(view, params))
                .toList(), params);
    }

    @Override
    public synchronized long countDepartments(Map<String, Object> params) {
        return listDepartments(unpaged(params)).size();
    }

    @Override
    public synchronized int insertDepartment(Map<String, Object> params) {
        String code = text(params, "code");
        String name = text(params, "name");
        if (departments.stream().anyMatch(department -> department.code.equals(code) || department.name.equals(name))) {
            throw new DuplicateKeyException("department duplicated");
        }
        departments.add(0, new DepartmentRecord(uuid(params, "deptId"), code, name, "总部", 0, "ENABLED", "平台管理员"));
        return 1;
    }

    @Override
    public synchronized int updateDepartment(Map<String, Object> params) {
        DepartmentRecord department = departmentById(uuid(params, "deptId"));
        String name = nullableText(params, "name");
        if (name != null) {
            department.name = name;
        }
        return 1;
    }

    @Override
    public synchronized int changeDepartmentStatus(Map<String, Object> params) {
        departmentById(uuid(params, "deptId")).status = text(params, "status");
        return 1;
    }

    @Override
    public synchronized List<UserView> listUsers(Map<String, Object> params) {
        return page(users.stream()
                .map(this::userView)
                .filter(view -> matches(view, params))
                .toList(), params);
    }

    @Override
    public synchronized long countUsers(Map<String, Object> params) {
        return listUsers(unpaged(params)).size();
    }

    @Override
    public synchronized int insertUser(Map<String, Object> params) {
        String username = text(params, "username");
        if (users.stream().anyMatch(user -> user.username.equals(username))) {
            throw new DuplicateKeyException("user duplicated");
        }
        users.add(0, new UserRecord(uuid(params, "userId"), username, username, "", "质量工程中心", "PENDING_ACTIVATION"));
        return 1;
    }

    @Override
    public synchronized int updateUser(Map<String, Object> params) {
        UserRecord user = userByUsername(text(params, "username"));
        user.displayName = coalesce(nullableText(params, "displayName"), user.displayName);
        user.email = coalesce(nullableText(params, "email"), user.email);
        return 1;
    }

    @Override
    public synchronized int enableUser(Map<String, Object> params) {
        return changeUserStatus(text(params, "username"), "ENABLED");
    }

    @Override
    public synchronized int disableUser(Map<String, Object> params) {
        return changeUserStatus(text(params, "username"), "DISABLED");
    }

    @Override
    public synchronized int lockUser(Map<String, Object> params) {
        return changeUserStatus(text(params, "username"), "LOCKED");
    }

    @Override
    public synchronized int unlockUser(Map<String, Object> params) {
        return changeUserStatus(text(params, "username"), "ENABLED");
    }

    @Override
    public synchronized int resetUserPassword(Map<String, Object> params) {
        return changeUserStatus(text(params, "username"), "ENABLED");
    }

    @Override
    public synchronized List<RoleView> listRoles(Map<String, Object> params) {
        return page(roles.stream()
                .sorted(Comparator.comparing((RoleRecord role) -> !role.system).thenComparing(role -> role.createdOrder))
                .map(this::roleView)
                .filter(view -> matches(view, params))
                .toList(), params);
    }

    @Override
    public synchronized long countRoles(Map<String, Object> params) {
        return listRoles(unpaged(params)).size();
    }

    @Override
    public synchronized List<PermissionView> listPermissions(Map<String, Object> params) {
        return page(permissions.stream()
                .map(permission -> new PermissionView(
                        permission.code,
                        permission.resourceType,
                        permission.action,
                        "PLATFORM,PROJECT,APPLICATION,ENVIRONMENT",
                        "",
                        "ENABLED".equals(permission.status) ? "启用" : "已停用"
                ))
                .filter(view -> matches(view, params))
                .toList(), params);
    }

    @Override
    public synchronized long countPermissions(Map<String, Object> params) {
        return listPermissions(unpaged(params)).size();
    }

    @Override
    public synchronized RoleRow findRoleRow(Map<String, Object> params) {
        String code = text(params, "roleCode");
        return roles.stream()
                .filter(role -> role.code.equals(code))
                .findFirst()
                .map(this::roleRow)
                .orElse(null);
    }

    @Override
    public synchronized List<String> listRolePermissionCodes(Map<String, Object> params) {
        RoleRecord role = roleById(uuid(params, "roleId"));
        return List.copyOf(role.permissionCodes);
    }

    @Override
    @SuppressWarnings("unchecked")
    public synchronized List<String> listEnabledPermissionCodes(Map<String, Object> params) {
        List<String> requested = (List<String>) params.getOrDefault("permissionCodes", List.of());
        LinkedHashSet<String> enabled = new LinkedHashSet<>(permissions.stream()
                .filter(permission -> "ENABLED".equals(permission.status))
                .map(permission -> permission.code)
                .toList());
        return requested.stream()
                .filter(enabled::contains)
                .sorted()
                .toList();
    }

    @Override
    public synchronized int insertRole(Map<String, Object> params) {
        String code = text(params, "code");
        if (roles.stream().anyMatch(role -> role.code.equals(code))) {
            throw new DuplicateKeyException("role duplicated");
        }
        roles.add(new RoleRecord(
                uuid(params, "roleId"),
                code,
                text(params, "name"),
                text(params, "scopeType"),
                false,
                false,
                "ENABLED",
                coalesce(nullableText(params, "description"), ""),
                0,
                new ArrayList<>(),
                roles.size()
        ));
        return 1;
    }

    @Override
    public synchronized int updateRole(Map<String, Object> params) {
        RoleRecord role = roleById(uuid(params, "roleId"));
        role.name = coalesce(nullableText(params, "name"), role.name);
        role.scopeType = coalesce(nullableText(params, "scopeType"), role.scopeType);
        role.description = coalesce(nullableText(params, "description"), role.description);
        role.version++;
        return 1;
    }

    @Override
    public synchronized int changeRoleStatus(Map<String, Object> params) {
        RoleRecord role = roleById(uuid(params, "roleId"));
        role.status = text(params, "status");
        role.version++;
        return 1;
    }

    @Override
    public synchronized int softDeleteRolePermissions(Map<String, Object> params) {
        roleById(uuid(params, "roleId")).permissionCodes.clear();
        return 1;
    }

    @Override
    @SuppressWarnings("unchecked")
    public synchronized int insertRolePermissions(Map<String, Object> params) {
        RoleRecord role = roleById(uuid(params, "roleId"));
        role.permissionCodes.addAll((List<String>) params.getOrDefault("permissionCodes", List.of()));
        return role.permissionCodes.size();
    }

    @Override
    public synchronized int bumpUsersAuthVersionByRole(Map<String, Object> params) {
        return 1;
    }

    @Override
    public synchronized int assignUserRole(Map<String, Object> params) {
        UUID userId = uuid(params, "userId");
        String roleCode = text(params, "roleCode");
        bindRole(userId, uuid(params, "roleId"), roleCode, "PLATFORM", null);
        return 1;
    }

    @Override
    public synchronized int unassignUserRole(Map<String, Object> params) {
        UUID userId = uuid(params, "userId");
        String roleCode = text(params, "roleCode");
        int before = roleBindings.size();
        roleBindings.removeIf(binding -> binding.userId.equals(userId)
                && binding.roleCode.equals(roleCode)
                && "PLATFORM".equals(binding.scopeType));
        return before - roleBindings.size();
    }

    @Override
    public synchronized List<ProjectView> listProjects(Map<String, Object> params) {
        return page(projects.stream()
                .map(this::projectView)
                .filter(view -> matches(view, params))
                .toList(), params);
    }

    @Override
    public synchronized long countProjects(Map<String, Object> params) {
        return listProjects(unpaged(params)).size();
    }

    @Override
    public synchronized int insertProject(Map<String, Object> params) {
        String code = text(params, "code");
        String name = text(params, "name");
        if (projects.stream().anyMatch(project -> project.code.equals(code) || project.name.equals(name))) {
            throw new DuplicateKeyException("project duplicated");
        }
        projects.add(0, new ProjectRecord(uuid(params, "projectId"), code, name, "质量工程中心", "平台管理员", 0, "PREPARING"));
        return 1;
    }

    @Override
    public synchronized int updateProject(Map<String, Object> params) {
        ProjectRecord project = projectById(uuid(params, "projectId"));
        project.name = coalesce(nullableText(params, "name"), project.name);
        return 1;
    }

    @Override
    public synchronized int changeProjectStatus(Map<String, Object> params) {
        projectById(uuid(params, "projectId")).status = text(params, "status");
        return 1;
    }

    @Override
    public synchronized List<ProjectMemberView> listProjectMembers(Map<String, Object> params) {
        UUID projectId = uuid(params, "projectId");
        return page(projectMembers.stream()
                .filter(member -> member.projectId.equals(projectId))
                .map(this::projectMemberView)
                .filter(view -> matches(view, params))
                .toList(), params);
    }

    @Override
    public synchronized long countProjectMembers(Map<String, Object> params) {
        return listProjectMembers(unpaged(params)).size();
    }

    @Override
    public synchronized int upsertProjectMember(Map<String, Object> params) {
        UUID projectId = uuid(params, "projectId");
        UUID userId = uuid(params, "userId");
        projectMembers.removeIf(member -> member.projectId.equals(projectId) && member.userId.equals(userId));
        projectMembers.add(0, new ProjectMemberRecord(projectId, userId, text(params, "memberType"), "ENABLED"));
        return 1;
    }

    @Override
    public synchronized int deleteProjectMember(Map<String, Object> params) {
        UUID projectId = uuid(params, "projectId");
        UUID userId = uuid(params, "userId");
        int before = projectMembers.size();
        projectMembers.removeIf(member -> member.projectId.equals(projectId) && member.userId.equals(userId));
        return before - projectMembers.size();
    }

    @Override
    public synchronized int disableProjectRoleBindings(Map<String, Object> params) {
        UUID projectId = uuid(params, "projectId");
        UUID userId = uuid(params, "userId");
        int before = roleBindings.size();
        roleBindings.removeIf(binding -> binding.userId.equals(userId)
                && "PROJECT".equals(binding.scopeType)
                && Objects.equals(projectId, binding.scopeId));
        return before - roleBindings.size();
    }

    @Override
    public synchronized List<ApplicationView> listApplications(Map<String, Object> params) {
        return page(applications.stream()
                .map(this::applicationView)
                .filter(view -> matches(view, params))
                .toList(), params);
    }

    @Override
    public synchronized long countApplications(Map<String, Object> params) {
        return listApplications(unpaged(params)).size();
    }

    @Override
    public synchronized int insertApplication(Map<String, Object> params) {
        String code = text(params, "code");
        String name = text(params, "name");
        if (applications.stream().anyMatch(application -> application.code.equals(code) || application.name.equals(name))) {
            throw new DuplicateKeyException("application duplicated");
        }
        ProjectRecord project = projectById(uuid(params, "projectId"));
        applications.add(0, new ApplicationRecord(
                uuid(params, "appId"),
                code,
                name,
                text(params, "appType"),
                project.id,
                "v0",
                "ENABLED",
                nullableText(params, "defaultWebUrl"),
                nullableText(params, "defaultApiBaseUrl")
        ));
        project.apps++;
        return 1;
    }

    @Override
    public synchronized int updateApplication(Map<String, Object> params) {
        ApplicationRecord application = applicationById(uuid(params, "applicationId"));
        application.name = coalesce(nullableText(params, "name"), application.name);
        application.appType = coalesce(nullableText(params, "appType"), application.appType);
        application.defaultWebUrl = coalesce(nullableText(params, "defaultWebUrl"), application.defaultWebUrl);
        application.defaultApiBaseUrl = coalesce(nullableText(params, "defaultApiBaseUrl"), application.defaultApiBaseUrl);
        return 1;
    }

    @Override
    public synchronized int changeApplicationStatus(Map<String, Object> params) {
        applicationById(uuid(params, "applicationId")).status = text(params, "status");
        return 1;
    }

    @Override
    public synchronized List<EnvironmentView> listEnvironments(Map<String, Object> params) {
        return page(environments.stream()
                .map(this::environmentView)
                .filter(view -> matches(view, params))
                .toList(), params);
    }

    @Override
    public synchronized long countEnvironments(Map<String, Object> params) {
        return listEnvironments(unpaged(params)).size();
    }

    @Override
    public synchronized int insertEnvironment(Map<String, Object> params) {
        String code = text(params, "code");
        String name = text(params, "name");
        if (environments.stream().anyMatch(environment -> environment.code.equals(code) || environment.name.equals(name))) {
            throw new DuplicateKeyException("environment duplicated");
        }
        environments.add(0, new EnvironmentRecord(
                uuid(params, "envId"),
                code,
                name,
                uuid(params, "projectId"),
                optionalUuid(params, "appId"),
                text(params, "scopeType"),
                text(params, "envType"),
                nullableText(params, "webUrl"),
                text(params, "endpoint"),
                "ENABLED",
                "{}"
        ));
        return 1;
    }

    @Override
    public synchronized int updateEnvironment(Map<String, Object> params) {
        EnvironmentRecord environment = environmentById(uuid(params, "environmentId"));
        environment.name = coalesce(nullableText(params, "name"), environment.name);
        environment.envType = coalesce(nullableText(params, "envType"), environment.envType);
        environment.webUrl = coalesce(nullableText(params, "webUrl"), environment.webUrl);
        environment.apiBaseUrl = coalesce(nullableText(params, "apiBaseUrl"), environment.apiBaseUrl);
        return 1;
    }

    @Override
    public synchronized int changeEnvironmentStatus(Map<String, Object> params) {
        environmentById(uuid(params, "environmentId")).status = text(params, "status");
        return 1;
    }

    @Override
    public synchronized EnvironmentConnectivityTargetRow findEnvironmentConnectivityTarget(Map<String, Object> params) {
        EnvironmentRecord environment = environmentByKeyword(text(params, "keyword"));
        if (environment == null) {
            return null;
        }
        return new EnvironmentConnectivityTargetRow(
                environment.id,
                environment.name,
                environment.status,
                environment.webUrl,
                environment.apiBaseUrl,
                environment.healthCheckJson
        );
    }

    @Override
    public synchronized int updateEnvironmentHealthCheck(Map<String, Object> params) {
        environmentById(uuid(params, "environmentId")).healthCheckJson = text(params, "healthCheckJson");
        return 1;
    }

    @Override
    public synchronized List<IntegrationView> listIntegrations(Map<String, Object> params) {
        return page(configs.stream()
                .filter(config -> config.configKey.startsWith("integration."))
                .map(this::integrationRow)
                .map(row -> new IntegrationView(row.key(), row.name(), row.category(), row.scope(), row.status()))
                .filter(view -> matches(view, params))
                .toList(), params);
    }

    @Override
    public synchronized long countIntegrations(Map<String, Object> params) {
        return listIntegrations(unpaged(params)).size();
    }

    @Override
    public synchronized List<AuditLogView> listAuditLogs(Map<String, Object> params) {
        return page(auditEntries().stream()
                .filter(entry -> matchesAudit(entry, params))
                .map(AuditEntry::view)
                .toList(), params);
    }

    @Override
    public synchronized long countAuditLogs(Map<String, Object> params) {
        return listAuditLogs(unpaged(params)).size();
    }

    @Override
    public synchronized List<AuditOutboxView> listAuditOutbox(Map<String, Object> params) {
        return page(auditOutbox.stream()
                .filter(outbox -> matchesAuditOutbox(outbox, params))
                .toList(), params);
    }

    @Override
    public synchronized long countAuditOutbox(Map<String, Object> params) {
        return listAuditOutbox(unpaged(params)).size();
    }

    @Override
    public synchronized List<SettingRow> listSettings(Map<String, Object> params) {
        return page(configs.stream()
                .filter(config -> !config.configKey.startsWith("integration."))
                .filter(config -> "ENABLED".equals(config.status))
                .map(this::settingRow)
                .filter(row -> matches(row.configKey() + row.displayName(), params))
                .toList(), params);
    }

    @Override
    public synchronized long countSettings(Map<String, Object> params) {
        return listSettings(unpaged(params)).size();
    }

    @Override
    public synchronized List<SecretReferenceView> listSecretReferences(Map<String, Object> params) {
        return page(secretReferences.stream()
                .map(this::secretReferenceView)
                .filter(view -> matches(view, params))
                .toList(), params);
    }

    @Override
    public synchronized long countSecretReferences(Map<String, Object> params) {
        return listSecretReferences(unpaged(params)).size();
    }

    @Override
    public synchronized int insertConfig(Map<String, Object> params) {
        String configKey = text(params, "configKey");
        if (configs.stream().anyMatch(config -> config.configKey.equals(configKey))) {
            throw new DuplicateKeyException("config duplicated");
        }
        configs.add(0, new ConfigRecord(configKey, text(params, "scopeType"), text(params, "valueJson"), "ENABLED"));
        return 1;
    }

    @Override
    public synchronized SecretProviderRow findSecretProviderForManage(Map<String, Object> params) {
        String providerCode = nullableText(params, "providerCode");
        return secretProviders.stream()
                .filter(provider -> "ENABLED".equals(provider.status))
                .filter(provider -> providerCode == null || providerCode.isBlank()
                        ? provider.defaultProvider
                        : provider.providerCode.equals(providerCode))
                .findFirst()
                .map(provider -> new SecretProviderRow(provider.id, provider.providerCode, provider.providerType, provider.status))
                .orElse(null);
    }

    @Override
    public synchronized SecretReferenceRow findSecretReferenceRow(Map<String, Object> params) {
        SecretReferenceRecord secret = secretReferenceByRef(nullableText(params, "secretRef"));
        if (secret == null) {
            return null;
        }
        SecretProviderRecord provider = secretProviderById(secret.providerId);
        return new SecretReferenceRow(
                secret.id,
                secret.secretRef,
                provider.providerCode,
                provider.providerType,
                secret.purpose,
                secret.scopeType,
                secret.scopeId,
                secret.secretVersion,
                secret.status
        );
    }

    @Override
    public synchronized SecretReferenceView findSecretReferenceView(Map<String, Object> params) {
        SecretReferenceRecord secret = secretReferenceByRef(nullableText(params, "secretRef"));
        return secret == null ? null : secretReferenceView(secret);
    }

    @Override
    public synchronized int insertSecretReference(Map<String, Object> params) {
        String secretRef = text(params, "secretRef");
        if (secretReferences.stream().anyMatch(secret -> secret.secretRef.equals(secretRef))) {
            throw new DuplicateKeyException("secret duplicated");
        }
        String now = now();
        secretReferences.add(0, new SecretReferenceRecord(
                uuid(params, "secretRefId"),
                uuid(params, "providerId"),
                secretRef,
                text(params, "purpose"),
                text(params, "scopeType"),
                uuid(params, "scopeId"),
                text(params, "maskedValue"),
                text(params, "secretVersion"),
                "ACTIVE",
                now,
                instantText(params.get("expiresAt")),
                now,
                now
        ));
        return 1;
    }

    @Override
    public synchronized int insertSecretLocalStore(Map<String, Object> params) {
        localSecretStores.put(uuid(params, "secretRefId"), new LinkedHashMap<>(params));
        return 1;
    }

    @Override
    public synchronized int updateSecretReferenceRotation(Map<String, Object> params) {
        SecretReferenceRecord secret = secretReferenceById(uuid(params, "secretRefId"));
        secret.maskedValue = text(params, "maskedValue");
        secret.secretVersion = text(params, "secretVersion");
        secret.status = "ACTIVE";
        secret.rotatedAt = now();
        secret.expiresAt = coalesce(instantText(params.get("expiresAt")), secret.expiresAt);
        secret.updatedAt = now();
        return 1;
    }

    @Override
    public synchronized int upsertSecretLocalStoreRotation(Map<String, Object> params) {
        localSecretStores.put(uuid(params, "secretRefId"), new LinkedHashMap<>(params));
        return 1;
    }

    @Override
    public synchronized int revokeSecretReference(Map<String, Object> params) {
        SecretReferenceRecord secret = secretReferenceById(uuid(params, "secretRefId"));
        secret.status = "REVOKED";
        secret.updatedAt = now();
        return 1;
    }

    @Override
    public synchronized int revokeSecretLocalStore(Map<String, Object> params) {
        Map<String, Object> material = localSecretStores.get(uuid(params, "secretRefId"));
        if (material != null) {
            material.put("status", "REVOKED");
        }
        return 1;
    }

    @Override
    public synchronized int updateIntegration(Map<String, Object> params) {
        configByKey(text(params, "configKey")).valueJson = text(params, "valueJson");
        return 1;
    }

    @Override
    public synchronized int updateSetting(Map<String, Object> params) {
        ConfigRecord config = configByKey(text(params, "configKey"));
        config.scopeType = text(params, "scopeType");
        config.valueJson = text(params, "valueJson");
        return 1;
    }

    @Override
    public synchronized int changeConfigStatus(Map<String, Object> params) {
        configByKey(text(params, "configKey")).status = text(params, "status");
        return 1;
    }

    @Override
    public synchronized int insertProjectOwner(Map<String, Object> params) {
        UUID projectId = uuid(params, "projectId");
        UUID actorId = uuid(params, "actorId");
        ensureSyntheticActor(actorId);
        projectMembers.removeIf(member -> member.projectId.equals(projectId) && member.userId.equals(actorId));
        projectMembers.add(0, new ProjectMemberRecord(projectId, actorId, "OWNER", "ENABLED"));
        return 1;
    }

    @Override
    public synchronized int insertDepartmentManager(Map<String, Object> params) {
        UUID actorId = uuid(params, "actorId");
        ensureSyntheticActor(actorId);
        departmentById(uuid(params, "deptId")).lead = userById(actorId).displayName;
        return 1;
    }

    @Override
    public synchronized UUID findDefaultProjectId(Map<String, Object> params) {
        return projects.stream()
                .filter(project -> "default-project".equals(project.code))
                .map(project -> project.id)
                .findFirst()
                .orElse(null);
    }

    @Override
    public synchronized int insertDefaultProject(Map<String, Object> params) {
        if (findDefaultProjectId(params) != null) {
            return 0;
        }
        projects.add(0, new ProjectRecord(uuid(params, "projectId"), "default-project", "默认项目", "质量工程中心", "平台管理员", 0, "ACTIVE"));
        return 1;
    }

    @Override
    public synchronized DepartmentRef findDepartmentRef(Map<String, Object> params) {
        DepartmentRecord department = departmentByKeyword(nullableText(params, "keyword"));
        return department == null ? null : new DepartmentRef(department.id, department.name, department.status);
    }

    @Override
    public synchronized ProjectRef findProjectRef(Map<String, Object> params) {
        ProjectRecord project = projectByKeyword(nullableText(params, "keyword"));
        return project == null ? null : new ProjectRef(project.id, project.name, project.status);
    }

    @Override
    public synchronized ApplicationRef findApplicationRef(Map<String, Object> params) {
        ApplicationRecord application = applicationByKeyword(nullableText(params, "keyword"));
        return application == null ? null : applicationRef(application);
    }

    @Override
    public synchronized EnvironmentRef findEnvironmentRef(Map<String, Object> params) {
        EnvironmentRecord environment = environmentByKeyword(nullableText(params, "keyword"));
        return environment == null ? null : environmentRef(environment);
    }

    @Override
    public synchronized DepartmentView findDepartmentView(Map<String, Object> params) {
        DepartmentRecord department = departmentByKeyword(nullableText(params, "keyword"));
        return department == null ? null : departmentView(department);
    }

    @Override
    public synchronized ProjectView findProjectView(Map<String, Object> params) {
        ProjectRecord project = projectByKeyword(nullableText(params, "keyword"));
        return project == null ? null : projectView(project);
    }

    @Override
    public synchronized ApplicationView findApplicationView(Map<String, Object> params) {
        ApplicationRecord application = applicationByKeyword(nullableText(params, "keyword"));
        return application == null ? null : applicationView(application);
    }

    @Override
    public synchronized EnvironmentView findEnvironmentView(Map<String, Object> params) {
        EnvironmentRecord environment = environmentByKeyword(nullableText(params, "keyword"));
        return environment == null ? null : environmentView(environment);
    }

    @Override
    public synchronized ProjectMemberView findProjectMemberByUsername(Map<String, Object> params) {
        UUID projectId = uuid(params, "projectId");
        String username = text(params, "username");
        return projectMembers.stream()
                .filter(member -> member.projectId.equals(projectId))
                .filter(member -> userById(member.userId).username.equals(username))
                .findFirst()
                .map(this::projectMemberView)
                .orElse(null);
    }

    @Override
    public synchronized List<ScopedUserRoleView> listScopedUserRoles(Map<String, Object> params) {
        UUID scopeId = uuid(params, "scopeId");
        String scopeType = text(params, "scopeType");
        String roleCode = coalesce(nullableText(params, "roleCode"), "");
        return page(roleBindings.stream()
                .filter(binding -> binding.scopeId != null && binding.scopeId.equals(scopeId))
                .filter(binding -> binding.scopeType.equals(scopeType))
                .filter(binding -> roleCode.isBlank() || binding.roleCode.equals(roleCode))
                .map(this::scopedUserRoleView)
                .filter(view -> matches(view, params))
                .toList(), params);
    }

    @Override
    public synchronized long countScopedUserRoles(Map<String, Object> params) {
        return listScopedUserRoles(unpaged(params)).size();
    }

    @Override
    public synchronized ScopedUserRoleView findScopedUserRoleByUsername(Map<String, Object> params) {
        String username = text(params, "username");
        return listScopedUserRoles(unpaged(params)).stream()
                .filter(view -> view.username().equals(username))
                .findFirst()
                .orElse(null);
    }

    @Override
    public synchronized ApplicationRef findApplicationRefInProject(Map<String, Object> params) {
        UUID projectId = uuid(params, "projectId");
        String applicationKey = text(params, "application");
        return applications.stream()
                .filter(application -> application.projectId.equals(projectId))
                .filter(application -> application.code.equals(applicationKey) || application.name.equals(applicationKey))
                .findFirst()
                .map(this::applicationRef)
                .orElse(null);
    }

    @Override
    public synchronized UUID findRoleId(Map<String, Object> params) {
        String roleCode = text(params, "roleCode");
        return roles.stream()
                .filter(role -> role.code.equals(roleCode))
                .filter(role -> "ENABLED".equals(role.status))
                .map(role -> role.id)
                .findFirst()
                .orElse(null);
    }

    @Override
    public synchronized int bindRoleIfPresent(Map<String, Object> params) {
        UUID roleId = optionalUuid(params, "roleId");
        if (roleId == null) {
            return 0;
        }
        bindRole(uuid(params, "userId"), roleId, text(params, "roleCode"), text(params, "scopeType"), optionalUuid(params, "scopeId"));
        return 1;
    }

    @Override
    public synchronized int bindProjectRole(Map<String, Object> params) {
        bindRole(uuid(params, "userId"), uuid(params, "roleId"), text(params, "roleCode"), "PROJECT", uuid(params, "projectId"));
        return 1;
    }

    @Override
    public synchronized int bindScopedRole(Map<String, Object> params) {
        bindRole(uuid(params, "userId"), uuid(params, "roleId"), text(params, "roleCode"), text(params, "scopeType"), uuid(params, "scopeId"));
        return 1;
    }

    @Override
    public synchronized int disableScopedRoles(Map<String, Object> params) {
        UUID userId = uuid(params, "userId");
        String scopeType = text(params, "scopeType");
        UUID scopeId = uuid(params, "scopeId");
        String roleCode = coalesce(nullableText(params, "roleCode"), "");
        int before = roleBindings.size();
        roleBindings.removeIf(binding -> binding.userId.equals(userId)
                && binding.scopeType.equals(scopeType)
                && Objects.equals(binding.scopeId, scopeId)
                && (roleCode.isBlank() || binding.roleCode.equals(roleCode)));
        return before - roleBindings.size();
    }

    @Override
    public synchronized int insertAuditLog(Map<String, Object> params) {
        seededAuditLogs.add(0, new AuditEntry(
                OffsetDateTime.now(),
                "system",
                text(params, "action"),
                text(params, "resourceType"),
                text(params, "resourceId"),
                text(params, "resourceId"),
                "SUCCESS"
        ));
        return 1;
    }

    @Override
    public synchronized UserView findUserByUsername(Map<String, Object> params) {
        UserRecord user = userByUsernameOrNull(text(params, "username"));
        return user == null ? null : userView(user);
    }

    @Override
    public synchronized IntegrationRow findIntegrationRow(Map<String, Object> params) {
        String key = text(params, "key");
        return configs.stream()
                .filter(config -> config.configKey.startsWith("integration."))
                .map(this::integrationRow)
                .filter(row -> row.key().equals(key) || row.name().equals(key))
                .findFirst()
                .orElse(null);
    }

    @Override
    public synchronized SettingRow findSettingRow(Map<String, Object> params) {
        String key = text(params, "key");
        return configs.stream()
                .filter(config -> !config.configKey.startsWith("integration."))
                .map(this::settingRow)
                .filter(row -> row.configKey().equals(key) || row.displayName().equals(key))
                .findFirst()
                .orElse(null);
    }

    @Override
    public synchronized UUID findUserId(Map<String, Object> params) {
        UserRecord user = userByUsernameOrNull(text(params, "username"));
        return user == null ? null : user.id;
    }

    @Override
    public synchronized int bumpUserAuthVersion(Map<String, Object> params) {
        return 1;
    }

    private void seedDepartments() {
        departments.add(new DepartmentRecord(UUID.randomUUID(), "quality", "质量工程中心", "总部", 68, "ENABLED", "邵敏"));
        departments.add(new DepartmentRecord(UUID.randomUUID(), "automation", "自动化平台组", "质量工程中心", 16, "ENABLED", "何序"));
        departments.add(new DepartmentRecord(UUID.randomUUID(), "business-acceptance", "业务验收组", "质量工程中心", 23, "PENDING", "赵文"));
    }

    private void seedPermissions() {
        PermissionCodes.ALL.forEach(code -> {
            String[] parts = code.split(":", 2);
            permissions.add(new PermissionRecord(code, parts[0], parts.length > 1 ? parts[1] : "", "ENABLED"));
        });
    }

    private void seedRoles() {
        addRole("SuperAdmin", "超级管理员", "PLATFORM", true, true, "平台初始化、组织治理、平台审计", PermissionCodes.ALL);
        addRole("PlatformAdmin", "平台管理员", "PLATFORM", true, true, "组织、用户、项目、应用、环境、权限、审计管理", PermissionCodes.ALL);
        addRole("DepartmentManager", "部门管理员", "DEPARTMENT", true, true, "部门内资源管理", List.of(PermissionCodes.DEPARTMENT_READ));
        addRole("ProjectOwner", "项目负责人", "PROJECT", true, true, "项目资源管理", List.of(PermissionCodes.PROJECT_READ));
        addRole("AppOwner", "应用负责人", "APPLICATION", true, true, "应用资源管理", List.of(PermissionCodes.APPLICATION_READ));
        addRole("Tester", "测试工程师", "ENVIRONMENT", true, true, "授权范围只读和启用环境使用", List.of(
                PermissionCodes.PROJECT_READ,
                PermissionCodes.APPLICATION_READ,
                PermissionCodes.ENVIRONMENT_READ,
                PermissionCodes.ENVIRONMENT_USE
        ));
        addRole("Developer", "研发工程师", "ENVIRONMENT", true, true, "环境研发访问", List.of(PermissionCodes.ENVIRONMENT_READ));
        addRole("Auditor", "审计员", "PLATFORM", true, true, "审计查看", List.of(PermissionCodes.AUDIT_READ, PermissionCodes.AUDIT_EXPORT));
    }

    private void seedUsers() {
        UserRecord admin = new UserRecord(UUID.randomUUID(), "admin_user", "平台管理员", "admin@example.com", "质量工程中心", "ENABLED");
        UserRecord shao = new UserRecord(UUID.randomUUID(), "shao.min", "邵敏", "shao.min@example.com", "质量工程中心", "ENABLED");
        UserRecord he = new UserRecord(UUID.randomUUID(), "he.xu", "何序", "he.xu@example.com", "自动化平台组", "ENABLED");
        UserRecord zhao = new UserRecord(UUID.randomUUID(), "zhao.wen", "赵文", "zhao.wen@example.com", "业务验收组", "PENDING_ACTIVATION");
        users.add(admin);
        users.add(shao);
        users.add(he);
        users.add(zhao);
        bindRole(admin.id, roleByCode("SuperAdmin").id, "SuperAdmin", "PLATFORM", null);
        bindRole(shao.id, roleByCode("PlatformAdmin").id, "PlatformAdmin", "PLATFORM", null);
        bindRole(he.id, roleByCode("ProjectOwner").id, "ProjectOwner", "PLATFORM", null);
        bindRole(zhao.id, roleByCode("Auditor").id, "Auditor", "PLATFORM", null);
    }

    private void seedProjects() {
        projects.add(new ProjectRecord(UUID.randomUUID(), "checkout-regression", "Checkout Regression", "自动化平台组", "何序", 4, "ACTIVE"));
        projects.add(new ProjectRecord(UUID.randomUUID(), "mobile-smoke", "Mobile Smoke", "端体验组", "陈乔", 2, "PREPARING"));
        projects.add(new ProjectRecord(UUID.randomUUID(), "api-stability", "API Stability", "质量工程中心", "平台组", 6, "ACTIVE"));
    }

    private void seedApplications() {
        ProjectRecord firstProject = projects.get(0);
        applications.add(new ApplicationRecord(UUID.randomUUID(), "veri-agent-api", "veri-agent-api", "Backend", firstProject.id, "v0.3.2", "ENABLED", null, "api.dev.local"));
        applications.add(new ApplicationRecord(UUID.randomUUID(), "portal-web", "portal-web", "Frontend", firstProject.id, "v0.1.0", "ENABLED", null, "portal.dev.local"));
        applications.add(new ApplicationRecord(UUID.randomUUID(), "mobile-client", "mobile-client", "Mobile", projects.get(1).id, "v2.8.1", "DISABLED", null, "mobile.dev.local"));
    }

    private void seedEnvironments() {
        environments.add(new EnvironmentRecord(UUID.randomUUID(), "dev", "dev", projects.get(0).id, null, "PROJECT", "DEV", "", "api.dev.local", "ENABLED", "{}"));
        environments.add(new EnvironmentRecord(UUID.randomUUID(), "staging", "staging", projects.get(0).id, null, "PROJECT", "STAGING", "", "api.stg.local", "ENABLED", "{}"));
        environments.add(new EnvironmentRecord(UUID.randomUUID(), "prod", "prod", projects.get(0).id, null, "PROJECT", "PROD", "", "api.veri-agent.local", "ENABLED", "{}"));
    }

    private void seedConfigs() {
        configs.add(new ConfigRecord("password.min_length", "SYSTEM", settingJson("密码最小长度", "10 位"), "ENABLED"));
        configs.add(new ConfigRecord("audit.retention_days", "SYSTEM", settingJson("审计日志保留", "365 天"), "ENABLED"));
        configs.add(new ConfigRecord("audit.retention_cleanup_enabled", "SYSTEM", settingJson("审计保留清理", "false"), "DISABLED"));
        configs.add(new ConfigRecord("audit.retention_min_days", "SYSTEM", settingJson("审计最小保留", "30 天"), "ENABLED"));
        configs.add(new ConfigRecord("project.default_status", "PROJECT", settingJson("默认项目状态", "规划中"), "ENABLED"));
        configs.add(new ConfigRecord("integration.github-enterprise", "SYSTEM", integrationJson("GitHub Enterprise", "代码仓库", "全局"), "ENABLED"));
        configs.add(new ConfigRecord("integration.jenkins", "SYSTEM", integrationJson("Jenkins", "CI/CD", "平台级"), "ENABLED"));
        configs.add(new ConfigRecord("integration.feishu-bot", "SYSTEM", integrationJson("Feishu Bot", "通知", "项目级"), "ENABLED"));
    }

    private void seedAudit() {
        seededAuditLogs.add(new AuditEntry(displayTime("2026-05-16 10:31"), "system", "健康检查", "platform", "platform-api", "platform-api", "SUCCESS"));
        seededAuditLogs.add(new AuditEntry(displayTime("2026-05-16 09:48"), "shao.min", "创建部门", "department", "dept-qa", "端体验组", "SUCCESS"));
        seededAuditLogs.add(new AuditEntry(displayTime("2026-05-15 18:12"), "he.xu", "更新角色", "rbac_role", "ProjectOwner", "ProjectOwner", "SUCCESS"));
        auditOutbox.add(new AuditOutboxView(
                "8f57078c-4a7f-4b80-bf72-7ef03d252001",
                "trc_outbox_pending",
                "audit:pending:001",
                "PENDING",
                1,
                "2026-05-21 10:05",
                "",
                "",
                "",
                "创建部门",
                "department",
                "dept-qa",
                "SUCCESS",
                "2026-05-21 10:00",
                "2026-05-21 10:00"
        ));
        auditOutbox.add(new AuditOutboxView(
                "8f57078c-4a7f-4b80-bf72-7ef03d252002",
                "trc_outbox_failed",
                "audit:failed:001",
                "FAILED",
                4,
                "2026-05-21 10:30",
                "",
                "wp1-audit-worker-1",
                "insert audit_log timeout",
                "重置密码",
                "user",
                "tester.lifecycle",
                "SUCCESS",
                "2026-05-21 09:45",
                "2026-05-21 09:58"
        ));
    }

    private void seedSecrets() {
        secretProviders.add(new SecretProviderRecord(UUID.randomUUID(), "local", "LOCAL_ENCRYPTED", "ENABLED", true));
    }

    private void addRole(String code, String name, String scopeType, boolean system, boolean builtin, String description, List<String> permissionCodes) {
        roles.add(new RoleRecord(
                UUID.randomUUID(),
                code,
                name,
                scopeType,
                system,
                builtin,
                "ENABLED",
                description,
                0,
                new ArrayList<>(permissionCodes),
                roles.size()
        ));
    }

    private DepartmentView departmentView(DepartmentRecord department) {
        return new DepartmentView(
                department.name,
                department.parent,
                department.lead,
                department.members,
                departmentStatusName(department.status)
        );
    }

    private UserView userView(UserRecord user) {
        return new UserView(
                user.username,
                user.displayName,
                user.email,
                roleNames(user.id, "PLATFORM", null),
                user.department,
                userStatusName(user.status),
                "PENDING_ACTIVATION".equals(user.status) ? "尚未登录" : "今天 10:24"
        );
    }

    private RoleView roleView(RoleRecord role) {
        return new RoleView(role.code, role.name, role.scopeType, enabledStatusName(role.status), role.description);
    }

    private RoleRow roleRow(RoleRecord role) {
        return new RoleRow(role.id, role.code, role.name, role.scopeType, role.system, role.builtin, role.status, role.description, role.version);
    }

    private ProjectView projectView(ProjectRecord project) {
        return new ProjectView(project.name, project.department, project.owner, project.apps, projectStatusName(project.status));
    }

    private ProjectMemberView projectMemberView(ProjectMemberRecord member) {
        UserRecord user = userById(member.userId);
        return new ProjectMemberView(
                user.username,
                user.displayName,
                roleNames(user.id, "PROJECT", member.projectId),
                member.memberType,
                enabledStatusName(member.status)
        );
    }

    private ApplicationView applicationView(ApplicationRecord application) {
        ProjectRecord project = projectById(application.projectId);
        return new ApplicationView(application.name, application.appType, project.name, application.version, applicationStatusName(application.status));
    }

    private EnvironmentView environmentView(EnvironmentRecord environment) {
        ProjectRecord project = projectById(environment.projectId);
        return new EnvironmentView(environment.name, project.name, coalesce(environment.apiBaseUrl, environment.code + ".local"), environmentStatusName(environment.status));
    }

    private ScopedUserRoleView scopedUserRoleView(RoleBindingRecord binding) {
        UserRecord user = userById(binding.userId);
        return new ScopedUserRoleView(user.username, user.displayName, binding.roleCode, binding.scopeType, enabledStatusName(binding.status));
    }

    private SecretReferenceView secretReferenceView(SecretReferenceRecord secret) {
        SecretProviderRecord provider = secretProviderById(secret.providerId);
        return new SecretReferenceView(
                secret.id.toString(),
                secret.secretRef,
                provider.providerCode,
                provider.providerType,
                secret.purpose,
                secret.scopeType,
                secret.scopeId.toString(),
                secret.maskedValue,
                secret.secretVersion,
                secret.status,
                secret.rotatedAt,
                secret.expiresAt,
                secret.createdAt,
                secret.updatedAt
        );
    }

    private ApplicationRef applicationRef(ApplicationRecord application) {
        ProjectRecord project = projectById(application.projectId);
        return new ApplicationRef(application.id, application.name, application.status, project.id, project.name);
    }

    private EnvironmentRef environmentRef(EnvironmentRecord environment) {
        ProjectRecord project = projectById(environment.projectId);
        return new EnvironmentRef(environment.id, environment.name, environment.status, project.id, project.name);
    }

    private IntegrationRow integrationRow(ConfigRecord config) {
        JsonNode json = json(config.valueJson);
        String key = config.configKey.replace("integration.", "");
        return new IntegrationRow(
                config.configKey,
                key,
                jsonText(json, "name", key),
                jsonText(json, "category", "未分类"),
                jsonText(json, "scope", "平台级"),
                configStatusName(config.status)
        );
    }

    private SettingRow settingRow(ConfigRecord config) {
        JsonNode json = json(config.valueJson);
        return new SettingRow(
                config.configKey,
                config.scopeType,
                jsonText(json, "_display_name", ""),
                jsonText(json, "_value", config.valueJson),
                configStatusName(config.status)
        );
    }

    private List<AuditEntry> auditEntries() {
        List<AuditEntry> entries = new ArrayList<>();
        entries.addAll(InMemoryAuditLogWriter.records().stream().map(this::auditRecordEntry).toList());
        entries.addAll(seededAuditLogs);
        return entries;
    }

    private AuditEntry auditRecordEntry(AuditLogWriter.AuditRecord record) {
        return new AuditEntry(
                OffsetDateTime.now(),
                record.actor() == null ? "system" : record.actor().username(),
                record.action(),
                record.resourceType(),
                record.resourceId(),
                coalesce(record.targetName(), record.resourceId()),
                record.result()
        );
    }

    private boolean matchesAudit(AuditEntry entry, Map<String, Object> params) {
        if (!matches(entry.view(), params)) {
            return false;
        }
        if (!matchesValue(entry.actor, nullableText(params, "actor"))) {
            return false;
        }
        if (!matchesValue(entry.action, nullableText(params, "action"))) {
            return false;
        }
        if (!matchesValue(entry.resourceType, nullableText(params, "resourceType"))) {
            return false;
        }
        if (!matchesValue(entry.result, nullableText(params, "result"))) {
            return false;
        }
        OffsetDateTime startTime = (OffsetDateTime) params.get("startTime");
        OffsetDateTime endTime = (OffsetDateTime) params.get("endTime");
        if (startTime != null && entry.time.isBefore(startTime)) {
            return false;
        }
        return endTime == null || !entry.time.isAfter(endTime);
    }

    private boolean matchesAuditOutbox(AuditOutboxView outbox, Map<String, Object> params) {
        if (!matches(outbox, params)) {
            return false;
        }
        if (!matchesValue(outbox.status(), nullableText(params, "status"))) {
            return false;
        }
        return matchesValue(outbox.traceId(), nullableText(params, "traceId"));
    }

    private void bindRole(UUID userId, UUID roleId, String roleCode, String scopeType, UUID scopeId) {
        roleBindings.removeIf(binding -> binding.userId.equals(userId)
                && binding.roleId.equals(roleId)
                && binding.scopeType.equals(scopeType)
                && Objects.equals(binding.scopeId, scopeId));
        roleBindings.add(0, new RoleBindingRecord(userId, roleId, roleCode, scopeType, scopeId, "ENABLED"));
    }

    private String roleNames(UUID userId, String scopeType, UUID scopeId) {
        List<String> names = roleBindings.stream()
                .filter(binding -> binding.userId.equals(userId))
                .filter(binding -> binding.scopeType.equals(scopeType))
                .filter(binding -> Objects.equals(binding.scopeId, scopeId))
                .map(binding -> binding.roleCode)
                .distinct()
                .sorted()
                .toList();
        return names.isEmpty() ? "未分配" : String.join(" / ", names);
    }

    private int changeUserStatus(String username, String status) {
        UserRecord user = userByUsernameOrNull(username);
        if (user == null) {
            return 0;
        }
        user.status = status;
        return 1;
    }

    private void ensureSyntheticActor(UUID actorId) {
        if (users.stream().noneMatch(user -> user.id.equals(actorId))) {
            users.add(new UserRecord(actorId, "admin_user", "平台管理员", "admin@example.com", "质量工程中心", "ENABLED"));
        }
    }

    private DepartmentRecord departmentById(UUID id) {
        return departments.stream()
                .filter(department -> department.id.equals(id))
                .findFirst()
                .orElseThrow();
    }

    private DepartmentRecord departmentByKeyword(String keyword) {
        return departments.stream()
                .filter(department -> department.id.toString().equals(keyword)
                        || department.code.equals(keyword)
                        || department.name.equals(keyword))
                .findFirst()
                .orElse(null);
    }

    private UserRecord userById(UUID id) {
        return users.stream()
                .filter(user -> user.id.equals(id))
                .findFirst()
                .orElseGet(() -> new UserRecord(id, "unknown", "未知用户", "", "未分配", "ENABLED"));
    }

    private UserRecord userByUsername(String username) {
        UserRecord user = userByUsernameOrNull(username);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        return user;
    }

    private UserRecord userByUsernameOrNull(String username) {
        return users.stream()
                .filter(user -> user.username.equals(username))
                .findFirst()
                .orElse(null);
    }

    private RoleRecord roleById(UUID id) {
        return roles.stream()
                .filter(role -> role.id.equals(id))
                .findFirst()
                .orElseThrow();
    }

    private RoleRecord roleByCode(String code) {
        return roles.stream()
                .filter(role -> role.code.equals(code))
                .findFirst()
                .orElseThrow();
    }

    private ProjectRecord projectById(UUID id) {
        return projects.stream()
                .filter(project -> project.id.equals(id))
                .findFirst()
                .orElseThrow();
    }

    private ProjectRecord projectByKeyword(String keyword) {
        return projects.stream()
                .filter(project -> project.id.toString().equals(keyword)
                        || project.code.equals(keyword)
                        || project.name.equals(keyword))
                .findFirst()
                .orElse(null);
    }

    private ApplicationRecord applicationById(UUID id) {
        return applications.stream()
                .filter(application -> application.id.equals(id))
                .findFirst()
                .orElseThrow();
    }

    private ApplicationRecord applicationByKeyword(String keyword) {
        return applications.stream()
                .filter(application -> application.id.toString().equals(keyword)
                        || application.code.equals(keyword)
                        || application.name.equals(keyword))
                .findFirst()
                .orElse(null);
    }

    private EnvironmentRecord environmentById(UUID id) {
        return environments.stream()
                .filter(environment -> environment.id.equals(id))
                .findFirst()
                .orElseThrow();
    }

    private EnvironmentRecord environmentByKeyword(String keyword) {
        return environments.stream()
                .filter(environment -> environment.id.toString().equals(keyword)
                        || environment.code.equals(keyword)
                        || environment.name.equals(keyword))
                .findFirst()
                .orElse(null);
    }

    private ConfigRecord configByKey(String key) {
        return configs.stream()
                .filter(config -> config.configKey.equals(key))
                .findFirst()
                .orElseThrow();
    }

    private SecretProviderRecord secretProviderById(UUID id) {
        return secretProviders.stream()
                .filter(provider -> provider.id.equals(id))
                .findFirst()
                .orElseThrow();
    }

    private SecretReferenceRecord secretReferenceById(UUID id) {
        return secretReferences.stream()
                .filter(secret -> secret.id.equals(id))
                .findFirst()
                .orElseThrow();
    }

    private SecretReferenceRecord secretReferenceByRef(String secretRef) {
        return secretReferences.stream()
                .filter(secret -> secret.secretRef.equals(secretRef))
                .findFirst()
                .orElse(null);
    }

    private JsonNode json(String raw) {
        try {
            return objectMapper.readTree(raw);
        } catch (Exception exception) {
            return objectMapper.createObjectNode();
        }
    }

    private String jsonText(JsonNode node, String field, String fallback) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? fallback : value.asText(fallback);
    }

    private String integrationJson(String name, String category, String scope) {
        return "{\"name\":\"" + escapeJson(name) + "\","
                + "\"category\":\"" + escapeJson(category) + "\","
                + "\"scope\":\"" + escapeJson(scope) + "\"}";
    }

    private String settingJson(String name, String value) {
        return "{\"_display_name\":\"" + escapeJson(name) + "\","
                + "\"_value\":\"" + escapeJson(value) + "\"}";
    }

    private String escapeJson(String value) {
        return String.valueOf(value == null ? "" : value)
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }

    private boolean matches(Object value, Map<String, Object> params) {
        String search = coalesce(nullableText(params, "search"), "");
        if (search.isBlank()) {
            return true;
        }
        return String.valueOf(value).toLowerCase().contains(search.toLowerCase());
    }

    private boolean matchesValue(String value, String expected) {
        return expected == null || expected.isBlank() || expected.equalsIgnoreCase(value);
    }

    private <T> List<T> page(List<T> items, Map<String, Object> params) {
        int limit = number(params, "limit", items.size());
        int offset = number(params, "offset", 0);
        int from = Math.min(offset, items.size());
        int to = Math.min(from + limit, items.size());
        return items.subList(from, to);
    }

    private Map<String, Object> unpaged(Map<String, Object> params) {
        Map<String, Object> copy = new LinkedHashMap<>(params);
        copy.put("limit", Integer.MAX_VALUE);
        copy.put("offset", 0);
        return copy;
    }

    private UUID uuid(Map<String, Object> params, String key) {
        Object value = params.get(key);
        if (value instanceof UUID uuid) {
            return uuid;
        }
        return UUID.fromString(String.valueOf(value));
    }

    private UUID optionalUuid(Map<String, Object> params, String key) {
        Object value = params.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof UUID uuid) {
            return uuid;
        }
        String text = String.valueOf(value);
        return text.isBlank() ? null : UUID.fromString(text);
    }

    private String text(Map<String, Object> params, String key) {
        String value = nullableText(params, key);
        return value == null ? "" : value;
    }

    private String nullableText(Map<String, Object> params, String key) {
        Object value = params.get(key);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isBlank() ? null : text;
    }

    private int number(Map<String, Object> params, String key, int fallback) {
        Object value = params.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return value == null ? fallback : Integer.parseInt(String.valueOf(value));
    }

    private OffsetDateTime displayTime(String value) {
        return LocalDateTime.parse(value, TIME_FORMAT).atOffset(ZoneOffset.ofHours(8));
    }

    private String now() {
        return LocalDateTime.now().format(TIME_FORMAT);
    }

    private String instantText(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Instant instant) {
            return instant.toString();
        }
        return String.valueOf(value);
    }

    private String coalesce(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String departmentStatusName(String status) {
        return switch (status) {
            case "ENABLED" -> "同步正常";
            case "DISABLED" -> "已停用";
            default -> "待确认";
        };
    }

    private String userStatusName(String status) {
        return switch (status) {
            case "ENABLED" -> "启用";
            case "DISABLED" -> "已停用";
            case "LOCKED" -> "已锁定";
            default -> "待激活";
        };
    }

    private String enabledStatusName(String status) {
        return "ENABLED".equals(status) ? "启用" : "已停用";
    }

    private String projectStatusName(String status) {
        return switch (status) {
            case "PREPARING" -> "规划中";
            case "ACTIVE" -> "进行中";
            case "ARCHIVED" -> "已归档";
            case "DISABLED" -> "已停用";
            default -> status;
        };
    }

    private String applicationStatusName(String status) {
        return "ENABLED".equals(status) ? "已接入" : "已停用";
    }

    private String environmentStatusName(String status) {
        return "ENABLED".equals(status) ? "可用" : "已停用";
    }

    private String configStatusName(String status) {
        return "ENABLED".equals(status) ? "已启用" : "已停用";
    }

    private record PermissionRecord(String code, String resourceType, String action, String status) {
    }

    private record AuditEntry(
            OffsetDateTime time,
            String actor,
            String action,
            String resourceType,
            String resourceId,
            String target,
            String result
    ) {
        AuditLogView view() {
            return new AuditLogView(time.format(TIME_FORMAT), actor, action, target, switch (result) {
                case "SUCCESS" -> "成功";
                case "DENIED" -> "拒绝";
                case "FAILED" -> "失败";
                default -> result;
            });
        }
    }

    private static final class DepartmentRecord {
        private final UUID id;
        private final String code;
        private String name;
        private final String parent;
        private final int members;
        private String status;
        private String lead;

        private DepartmentRecord(UUID id, String code, String name, String parent, int members, String status, String lead) {
            this.id = id;
            this.code = code;
            this.name = name;
            this.parent = parent;
            this.members = members;
            this.status = status;
            this.lead = lead;
        }
    }

    private static final class UserRecord {
        private final UUID id;
        private final String username;
        private String displayName;
        private String email;
        private final String department;
        private String status;

        private UserRecord(UUID id, String username, String displayName, String email, String department, String status) {
            this.id = id;
            this.username = username;
            this.displayName = displayName;
            this.email = email;
            this.department = department;
            this.status = status;
        }
    }

    private static final class RoleRecord {
        private final UUID id;
        private final String code;
        private String name;
        private String scopeType;
        private final boolean system;
        private final boolean builtin;
        private String status;
        private String description;
        private long version;
        private final List<String> permissionCodes;
        private final int createdOrder;

        private RoleRecord(UUID id, String code, String name, String scopeType, boolean system, boolean builtin,
                           String status, String description, long version, List<String> permissionCodes, int createdOrder) {
            this.id = id;
            this.code = code;
            this.name = name;
            this.scopeType = scopeType;
            this.system = system;
            this.builtin = builtin;
            this.status = status;
            this.description = description;
            this.version = version;
            this.permissionCodes = permissionCodes;
            this.createdOrder = createdOrder;
        }
    }

    private static final class RoleBindingRecord {
        private final UUID userId;
        private final UUID roleId;
        private final String roleCode;
        private final String scopeType;
        private final UUID scopeId;
        private final String status;

        private RoleBindingRecord(UUID userId, UUID roleId, String roleCode, String scopeType, UUID scopeId, String status) {
            this.userId = userId;
            this.roleId = roleId;
            this.roleCode = roleCode;
            this.scopeType = scopeType;
            this.scopeId = scopeId;
            this.status = status;
        }
    }

    private static final class ProjectRecord {
        private final UUID id;
        private final String code;
        private String name;
        private final String department;
        private final String owner;
        private int apps;
        private String status;

        private ProjectRecord(UUID id, String code, String name, String department, String owner, int apps, String status) {
            this.id = id;
            this.code = code;
            this.name = name;
            this.department = department;
            this.owner = owner;
            this.apps = apps;
            this.status = status;
        }
    }

    private static final class ProjectMemberRecord {
        private final UUID projectId;
        private final UUID userId;
        private final String memberType;
        private final String status;

        private ProjectMemberRecord(UUID projectId, UUID userId, String memberType, String status) {
            this.projectId = projectId;
            this.userId = userId;
            this.memberType = memberType;
            this.status = status;
        }
    }

    private static final class ApplicationRecord {
        private final UUID id;
        private final String code;
        private String name;
        private String appType;
        private final UUID projectId;
        private final String version;
        private String status;
        private String defaultWebUrl;
        private String defaultApiBaseUrl;

        private ApplicationRecord(UUID id, String code, String name, String appType, UUID projectId, String version,
                                  String status, String defaultWebUrl, String defaultApiBaseUrl) {
            this.id = id;
            this.code = code;
            this.name = name;
            this.appType = appType;
            this.projectId = projectId;
            this.version = version;
            this.status = status;
            this.defaultWebUrl = defaultWebUrl;
            this.defaultApiBaseUrl = defaultApiBaseUrl;
        }
    }

    private static final class EnvironmentRecord {
        private final UUID id;
        private final String code;
        private String name;
        private final UUID projectId;
        private final UUID applicationId;
        private final String scopeType;
        private String envType;
        private String webUrl;
        private String apiBaseUrl;
        private String status;
        private String healthCheckJson;

        private EnvironmentRecord(UUID id, String code, String name, UUID projectId, UUID applicationId,
                                  String scopeType, String envType, String webUrl, String apiBaseUrl,
                                  String status, String healthCheckJson) {
            this.id = id;
            this.code = code;
            this.name = name;
            this.projectId = projectId;
            this.applicationId = applicationId;
            this.scopeType = scopeType;
            this.envType = envType;
            this.webUrl = webUrl;
            this.apiBaseUrl = apiBaseUrl;
            this.status = status;
            this.healthCheckJson = healthCheckJson;
        }
    }

    private static final class ConfigRecord {
        private final String configKey;
        private String scopeType;
        private String valueJson;
        private String status;

        private ConfigRecord(String configKey, String scopeType, String valueJson, String status) {
            this.configKey = configKey;
            this.scopeType = scopeType;
            this.valueJson = valueJson;
            this.status = status;
        }
    }

    private record SecretProviderRecord(UUID id, String providerCode, String providerType, String status, boolean defaultProvider) {
    }

    private static final class SecretReferenceRecord {
        private final UUID id;
        private final UUID providerId;
        private final String secretRef;
        private final String purpose;
        private final String scopeType;
        private final UUID scopeId;
        private String maskedValue;
        private String secretVersion;
        private String status;
        private String rotatedAt;
        private String expiresAt;
        private final String createdAt;
        private String updatedAt;

        private SecretReferenceRecord(UUID id, UUID providerId, String secretRef, String purpose, String scopeType,
                                      UUID scopeId, String maskedValue, String secretVersion, String status,
                                      String rotatedAt, String expiresAt, String createdAt, String updatedAt) {
            this.id = id;
            this.providerId = providerId;
            this.secretRef = secretRef;
            this.purpose = purpose;
            this.scopeType = scopeType;
            this.scopeId = scopeId;
            this.maskedValue = maskedValue;
            this.secretVersion = secretVersion;
            this.status = status;
            this.rotatedAt = rotatedAt;
            this.expiresAt = expiresAt;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
        }
    }
}
