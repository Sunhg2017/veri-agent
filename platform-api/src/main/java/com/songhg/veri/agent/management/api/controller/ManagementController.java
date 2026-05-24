package com.songhg.veri.agent.management.api.controller;

import com.songhg.veri.agent.authorization.application.PermissionCodes;
import com.songhg.veri.agent.authorization.application.RequirePermission;
import com.songhg.veri.agent.auth.application.AuthUserPrincipal;
import com.songhg.veri.agent.authorization.application.AuthorizationService;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.openapi.ApiVersion;
import com.songhg.veri.agent.management.application.AuditLogQuery;
import com.songhg.veri.agent.management.application.AuditOutboxQuery;
import com.songhg.veri.agent.management.application.ManagementConsoleService;
import com.songhg.veri.agent.management.application.AuditLogPageRequest;
import com.songhg.veri.agent.management.application.AuditOutboxPageRequest;
import com.songhg.veri.agent.management.application.CreateApplicationRequest;
import com.songhg.veri.agent.management.application.CreateEnvironmentRequest;
import com.songhg.veri.agent.management.application.CreateIntegrationRequest;
import com.songhg.veri.agent.management.application.CreateNamedRequest;
import com.songhg.veri.agent.management.application.CreateProjectRequest;
import com.songhg.veri.agent.management.application.CreateRoleRequest;
import com.songhg.veri.agent.management.application.CreateSecretReferenceRequest;
import com.songhg.veri.agent.management.application.CreateSettingRequest;
import com.songhg.veri.agent.management.application.DisableSecretReferenceRequest;
import com.songhg.veri.agent.management.application.ManagementPageRequest;
import com.songhg.veri.agent.management.application.ProjectMemberRequest;
import com.songhg.veri.agent.management.application.ResetPasswordRequest;
import com.songhg.veri.agent.management.application.RoleBindingRequest;
import com.songhg.veri.agent.management.application.RotateSecretReferenceRequest;
import com.songhg.veri.agent.management.application.ScopedUserRoleRequest;
import com.songhg.veri.agent.management.application.StatusChangeRequest;
import com.songhg.veri.agent.management.application.UpdateApplicationRequest;
import com.songhg.veri.agent.management.application.UpdateDepartmentRequest;
import com.songhg.veri.agent.management.application.UpdateEnvironmentRequest;
import com.songhg.veri.agent.management.application.UpdateIntegrationRequest;
import com.songhg.veri.agent.management.application.UpdateProjectRequest;
import com.songhg.veri.agent.management.application.UpdateRoleRequest;
import com.songhg.veri.agent.management.application.UpdateSettingRequest;
import com.songhg.veri.agent.management.application.UpdateUserRequest;
import com.songhg.veri.agent.management.application.ApplicationView;
import com.songhg.veri.agent.management.application.AuditLogView;
import com.songhg.veri.agent.management.application.AuditOutboxView;
import com.songhg.veri.agent.management.application.DepartmentView;
import com.songhg.veri.agent.management.application.EnvironmentConnectivityCheckView;
import com.songhg.veri.agent.management.application.EnvironmentView;
import com.songhg.veri.agent.management.application.IntegrationView;
import com.songhg.veri.agent.management.application.PermissionView;
import com.songhg.veri.agent.management.application.ProjectMemberView;
import com.songhg.veri.agent.management.application.ProjectView;
import com.songhg.veri.agent.management.application.RoleDetailView;
import com.songhg.veri.agent.management.application.RoleView;
import com.songhg.veri.agent.management.application.ScopedUserRoleView;
import com.songhg.veri.agent.management.application.SecretReferenceView;
import com.songhg.veri.agent.management.application.SettingView;
import com.songhg.veri.agent.management.application.UserView;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@ApiVersion
@RestController
@RequestMapping("/api/v1/management")
public class ManagementController {

    private final ManagementConsoleService consoleService;
    private final AuthorizationService authorizationService;

    public ManagementController(
            ManagementConsoleService consoleService,
            AuthorizationService authorizationService
    ) {
        this.consoleService = consoleService;
        this.authorizationService = authorizationService;
    }

    @GetMapping("/departments")
    @RequirePermission(PermissionCodes.DEPARTMENT_READ)
    public PageResponse<DepartmentView> departments(
            @Valid ManagementPageRequest pageRequest,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return consoleService.departments(pageRequest.toPageQuery());
    }

    @PostMapping("/departments")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(PermissionCodes.DEPARTMENT_CREATE)
    public DepartmentView createDepartment(
            @Valid @RequestBody CreateNamedRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return consoleService.createDepartment(normalize(request), principal);
    }

    @GetMapping("/departments/{key}")
    @RequirePermission(PermissionCodes.DEPARTMENT_READ)
    public DepartmentView department(
            @PathVariable String key,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return consoleService.department(key.trim());
    }

    @PatchMapping("/departments/{key}")
    @RequirePermission(PermissionCodes.DEPARTMENT_EDIT)
    public DepartmentView updateDepartment(
            @PathVariable String key,
            @Valid @RequestBody UpdateDepartmentRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return consoleService.updateDepartment(key.trim(), request, principal);
    }

    @PatchMapping("/departments/{key}/status")
    public DepartmentView changeDepartmentStatus(
            @PathVariable String key,
            @Valid @RequestBody StatusChangeRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, PermissionCodes.departmentStatusPermission(request.status()));
        return consoleService.changeDepartmentStatus(key.trim(), request.status(), principal);
    }

    @GetMapping("/users")
    @RequirePermission(PermissionCodes.USER_READ)
    public PageResponse<UserView> users(
            @Valid ManagementPageRequest pageRequest,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return consoleService.users(pageRequest.toPageQuery());
    }

    @GetMapping("/users/{username}")
    @RequirePermission(PermissionCodes.USER_READ)
    public UserView user(
            @PathVariable String username,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return consoleService.user(username.trim());
    }

    @PatchMapping("/users/{username}")
    @RequirePermission(PermissionCodes.USER_EDIT)
    public UserView updateUser(
            @PathVariable String username,
            @Valid @RequestBody UpdateUserRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return consoleService.updateUser(username.trim(), request, principal);
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(PermissionCodes.USER_CREATE)
    public UserView createUser(
            @Valid @RequestBody CreateNamedRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return consoleService.createUser(normalize(request), principal);
    }

    @PostMapping("/users/{username}/enable")
    @RequirePermission(PermissionCodes.USER_ENABLE)
    public UserView enableUser(
            @PathVariable String username,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return consoleService.enableUser(username.trim(), principal);
    }

    @PostMapping("/users/{username}/disable")
    @RequirePermission(PermissionCodes.USER_DISABLE)
    public UserView disableUser(
            @PathVariable String username,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return consoleService.disableUser(username.trim(), principal);
    }

    @PostMapping("/users/{username}/lock")
    @RequirePermission(PermissionCodes.USER_LOCK)
    public UserView lockUser(
            @PathVariable String username,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return consoleService.lockUser(username.trim(), principal);
    }

    @PostMapping("/users/{username}/unlock")
    @RequirePermission(PermissionCodes.USER_UNLOCK)
    public UserView unlockUser(
            @PathVariable String username,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return consoleService.unlockUser(username.trim(), principal);
    }

    @PostMapping("/users/{username}/reset-password")
    @RequirePermission(PermissionCodes.USER_RESET_PASSWORD)
    public UserView resetUserPassword(
            @PathVariable String username,
            @Valid @RequestBody ResetPasswordRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return consoleService.resetUserPassword(username.trim(), request.newPassword(), principal);
    }

    @GetMapping("/roles")
    @RequirePermission(PermissionCodes.ROLE_READ)
    public PageResponse<RoleView> roles(
            @Valid ManagementPageRequest pageRequest,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return consoleService.roles(pageRequest.toPageQuery());
    }

    @GetMapping("/permissions")
    @RequirePermission(PermissionCodes.ROLE_READ)
    public PageResponse<PermissionView> permissions(
            @Valid ManagementPageRequest pageRequest,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return consoleService.permissions(pageRequest.toPageQuery());
    }

    @GetMapping("/roles/{code}")
    @RequirePermission(PermissionCodes.ROLE_READ)
    public RoleDetailView role(
            @PathVariable String code,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return consoleService.role(code.trim());
    }

    @PostMapping("/roles")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(PermissionCodes.ROLE_CREATE)
    public RoleDetailView createRole(
            @Valid @RequestBody CreateRoleRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        Set<String> assignablePermissions = authorizationService.permissions(principal);
        return consoleService.createRole(request, assignablePermissions, principal);
    }

    @PatchMapping("/roles/{code}")
    @RequirePermission(PermissionCodes.ROLE_EDIT)
    public RoleDetailView updateRole(
            @PathVariable String code,
            @Valid @RequestBody UpdateRoleRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        Set<String> assignablePermissions = authorizationService.permissions(principal);
        return consoleService.updateRole(code.trim(), request, assignablePermissions, principal);
    }

    @PatchMapping("/roles/{code}/status")
    @RequirePermission(PermissionCodes.ROLE_EDIT)
    public RoleDetailView changeRoleStatus(
            @PathVariable String code,
            @Valid @RequestBody StatusChangeRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return consoleService.changeRoleStatus(code.trim(), request.status(), principal);
    }

    @PostMapping("/users/{username}/roles")
    @RequirePermission(PermissionCodes.USER_ASSIGN_ROLE)
    @RequirePermission(PermissionCodes.ROLE_BIND)
    public UserView assignUserRole(
            @PathVariable String username,
            @Valid @RequestBody RoleBindingRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return consoleService.assignUserRole(username.trim(), request.roleCode().trim(), principal);
    }

    @PostMapping("/users/{username}/roles/unassign")
    @RequirePermission(PermissionCodes.USER_ASSIGN_ROLE)
    @RequirePermission(PermissionCodes.ROLE_UNBIND)
    public UserView unassignUserRole(
            @PathVariable String username,
            @Valid @RequestBody RoleBindingRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return consoleService.unassignUserRole(username.trim(), request.roleCode().trim(), principal);
    }

    @GetMapping("/projects")
    @RequirePermission(PermissionCodes.PROJECT_READ)
    public PageResponse<ProjectView> projects(
            @Valid ManagementPageRequest pageRequest,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return consoleService.projects(pageRequest.toPageQuery(), principal);
    }

    @PostMapping("/projects")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(PermissionCodes.PROJECT_CREATE)
    public ProjectView createProject(
            @Valid @RequestBody CreateProjectRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return consoleService.createProject(request, principal);
    }

    @GetMapping("/projects/{key}")
    @RequirePermission(PermissionCodes.PROJECT_READ)
    public ProjectView project(
            @PathVariable String key,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return consoleService.project(key.trim());
    }

    @PatchMapping("/projects/{key}")
    @RequirePermission(PermissionCodes.PROJECT_EDIT)
    public ProjectView updateProject(
            @PathVariable String key,
            @Valid @RequestBody UpdateProjectRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return consoleService.updateProject(key.trim(), request, principal);
    }

    @PatchMapping("/projects/{key}/status")
    public ProjectView changeProjectStatus(
            @PathVariable String key,
            @Valid @RequestBody StatusChangeRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, PermissionCodes.projectStatusPermission(request.status()));
        return consoleService.changeProjectStatus(key.trim(), request.status(), principal);
    }

    @GetMapping("/projects/{key}/members")
    @RequirePermission(PermissionCodes.PROJECT_READ)
    public PageResponse<ProjectMemberView> projectMembers(
            @PathVariable String key,
            @Valid ManagementPageRequest pageRequest,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return consoleService.projectMembers(key.trim(), pageRequest.toPageQuery());
    }

    @PostMapping("/projects/{key}/members")
    @RequirePermission(PermissionCodes.PROJECT_MEMBER_MANAGE)
    @RequirePermission(PermissionCodes.ROLE_BIND)
    public ProjectMemberView addProjectMember(
            @PathVariable String key,
            @Valid @RequestBody ProjectMemberRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return consoleService.addProjectMember(key.trim(), request, principal);
    }

    @PostMapping("/projects/{key}/members/{username}/remove")
    @RequirePermission(PermissionCodes.PROJECT_MEMBER_MANAGE)
    @RequirePermission(PermissionCodes.ROLE_UNBIND)
    public ProjectMemberView removeProjectMember(
            @PathVariable String key,
            @PathVariable String username,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return consoleService.removeProjectMember(key.trim(), username.trim(), principal);
    }

    @GetMapping("/applications")
    @RequirePermission(PermissionCodes.APPLICATION_READ)
    public PageResponse<ApplicationView> applications(
            @Valid ManagementPageRequest pageRequest,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return consoleService.applications(pageRequest.toPageQuery(), principal);
    }

    @PostMapping("/applications")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(PermissionCodes.APPLICATION_CREATE)
    public ApplicationView createApplication(
            @Valid @RequestBody CreateApplicationRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return consoleService.createApplication(request, principal);
    }

    @GetMapping("/applications/{key}")
    @RequirePermission(PermissionCodes.APPLICATION_READ)
    public ApplicationView application(
            @PathVariable String key,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return consoleService.application(key.trim());
    }

    @PatchMapping("/applications/{key}")
    @RequirePermission(PermissionCodes.APPLICATION_EDIT)
    public ApplicationView updateApplication(
            @PathVariable String key,
            @Valid @RequestBody UpdateApplicationRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return consoleService.updateApplication(key.trim(), request, principal);
    }

    @PatchMapping("/applications/{key}/status")
    public ApplicationView changeApplicationStatus(
            @PathVariable String key,
            @Valid @RequestBody StatusChangeRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, PermissionCodes.applicationStatusPermission(request.status()));
        return consoleService.changeApplicationStatus(key.trim(), request.status(), principal);
    }

    @GetMapping("/applications/{key}/owners")
    @RequirePermission(PermissionCodes.APPLICATION_READ)
    public PageResponse<ScopedUserRoleView> applicationOwners(
            @PathVariable String key,
            @Valid ManagementPageRequest pageRequest,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return consoleService.applicationOwners(key.trim(), pageRequest.toPageQuery());
    }

    @PostMapping("/applications/{key}/owners")
    @RequirePermission(PermissionCodes.APPLICATION_OWNER_MANAGE)
    @RequirePermission(PermissionCodes.ROLE_BIND)
    public ScopedUserRoleView addApplicationOwner(
            @PathVariable String key,
            @Valid @RequestBody ScopedUserRoleRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return consoleService.addApplicationOwner(key.trim(), request, principal);
    }

    @PostMapping("/applications/{key}/owners/{username}/remove")
    @RequirePermission(PermissionCodes.APPLICATION_OWNER_MANAGE)
    @RequirePermission(PermissionCodes.ROLE_UNBIND)
    public ScopedUserRoleView removeApplicationOwner(
            @PathVariable String key,
            @PathVariable String username,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return consoleService.removeApplicationOwner(key.trim(), username.trim(), principal);
    }

    @GetMapping("/environments")
    @RequirePermission(PermissionCodes.ENVIRONMENT_READ)
    public PageResponse<EnvironmentView> environments(
            @Valid ManagementPageRequest pageRequest,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return consoleService.environments(pageRequest.toPageQuery(), principal);
    }

    @PostMapping("/environments")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(PermissionCodes.ENVIRONMENT_CREATE)
    public EnvironmentView createEnvironment(
            @Valid @RequestBody CreateEnvironmentRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return consoleService.createEnvironment(request, principal);
    }

    @GetMapping("/environments/{key}")
    @RequirePermission(PermissionCodes.ENVIRONMENT_READ)
    public EnvironmentView environment(
            @PathVariable String key,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return consoleService.environment(key.trim());
    }

    @PatchMapping("/environments/{key}")
    @RequirePermission(PermissionCodes.ENVIRONMENT_EDIT)
    public EnvironmentView updateEnvironment(
            @PathVariable String key,
            @Valid @RequestBody UpdateEnvironmentRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return consoleService.updateEnvironment(key.trim(), request, principal);
    }

    @PatchMapping("/environments/{key}/status")
    public EnvironmentView changeEnvironmentStatus(
            @PathVariable String key,
            @Valid @RequestBody StatusChangeRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, PermissionCodes.environmentStatusPermission(request.status()));
        return consoleService.changeEnvironmentStatus(key.trim(), request.status(), principal);
    }

    @GetMapping("/environments/{key}/connectivity-check")
    @RequirePermission(PermissionCodes.ENVIRONMENT_READ)
    public EnvironmentConnectivityCheckView environmentConnectivityCheck(
            @PathVariable String key,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return consoleService.environmentConnectivityCheck(key.trim());
    }

    @PostMapping("/environments/{key}/connectivity-check")
    @RequirePermission(PermissionCodes.ENVIRONMENT_EDIT)
    public EnvironmentConnectivityCheckView checkEnvironmentConnectivity(
            @PathVariable String key,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return consoleService.checkEnvironmentConnectivity(key.trim(), principal);
    }

    @GetMapping("/environments/{key}/users")
    @RequirePermission(PermissionCodes.ENVIRONMENT_READ)
    public PageResponse<ScopedUserRoleView> environmentUsers(
            @PathVariable String key,
            @Valid ManagementPageRequest pageRequest,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return consoleService.environmentUsers(key.trim(), pageRequest.toPageQuery());
    }

    @PostMapping("/environments/{key}/users")
    @RequirePermission(PermissionCodes.ENVIRONMENT_USER_MANAGE)
    @RequirePermission(PermissionCodes.ROLE_BIND)
    public ScopedUserRoleView addEnvironmentUser(
            @PathVariable String key,
            @Valid @RequestBody ScopedUserRoleRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return consoleService.addEnvironmentUser(key.trim(), request, principal);
    }

    @PostMapping("/environments/{key}/users/{username}/remove")
    @RequirePermission(PermissionCodes.ENVIRONMENT_USER_MANAGE)
    @RequirePermission(PermissionCodes.ROLE_UNBIND)
    public ScopedUserRoleView removeEnvironmentUser(
            @PathVariable String key,
            @PathVariable String username,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return consoleService.removeEnvironmentUser(key.trim(), username.trim(), principal);
    }

    @GetMapping("/integrations")
    @RequirePermission(PermissionCodes.CONFIG_READ)
    public PageResponse<IntegrationView> integrations(
            @Valid ManagementPageRequest pageRequest,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return consoleService.integrations(pageRequest.toPageQuery());
    }

    @PostMapping("/integrations")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(PermissionCodes.CONFIG_EDIT)
    public IntegrationView createIntegration(
            @Valid @RequestBody CreateIntegrationRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return consoleService.createIntegration(request, principal);
    }

    @GetMapping("/integrations/{key}")
    @RequirePermission(PermissionCodes.CONFIG_READ)
    public IntegrationView integration(
            @PathVariable String key,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return consoleService.integration(key.trim());
    }

    @PatchMapping("/integrations/{key}")
    @RequirePermission(PermissionCodes.CONFIG_EDIT)
    public IntegrationView updateIntegration(
            @PathVariable String key,
            @Valid @RequestBody UpdateIntegrationRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return consoleService.updateIntegration(key.trim(), request, principal);
    }

    @PatchMapping("/integrations/{key}/status")
    @RequirePermission(PermissionCodes.CONFIG_EDIT)
    public IntegrationView changeIntegrationStatus(
            @PathVariable String key,
            @Valid @RequestBody StatusChangeRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return consoleService.changeIntegrationStatus(key.trim(), request.status(), principal);
    }

    @GetMapping("/audit-logs")
    @RequirePermission(PermissionCodes.AUDIT_READ)
    public PageResponse<AuditLogView> auditLogs(
            @Valid AuditLogPageRequest pageRequest,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return consoleService.auditLogs(
                pageRequest.toPageQuery(),
                AuditLogQuery.of(
                        pageRequest.getSearch(),
                        pageRequest.getActor(),
                        pageRequest.getAction(),
                        pageRequest.getResourceType(),
                        pageRequest.getResult(),
                        pageRequest.getStartTime(),
                        pageRequest.getEndTime()
                ),
                principal
        );
    }

    @GetMapping(value = "/audit-logs/export", produces = "text/csv")
    @RequirePermission(PermissionCodes.AUDIT_READ)
    @RequirePermission(PermissionCodes.AUDIT_EXPORT)
    public ResponseEntity<String> exportAuditLogs(
            @Valid AuditLogPageRequest pageRequest,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        String csv = consoleService.exportAuditLogsCsv(
                AuditLogQuery.of(
                        pageRequest.getSearch(),
                        pageRequest.getActor(),
                        pageRequest.getAction(),
                        pageRequest.getResourceType(),
                        pageRequest.getResult(),
                        pageRequest.getStartTime(),
                        pageRequest.getEndTime()
                ),
                principal
        );
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"wp1-audit-logs.csv\"")
                .body(csv);
    }

    @GetMapping("/audit-outbox")
    @RequirePermission(PermissionCodes.AUDIT_READ)
    public PageResponse<AuditOutboxView> auditOutbox(
            @Valid AuditOutboxPageRequest pageRequest,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return consoleService.auditOutbox(
                pageRequest.toPageQuery(),
                AuditOutboxQuery.of(
                        pageRequest.getSearch(),
                        pageRequest.getStatus(),
                        pageRequest.getTraceId()
                ),
                principal
        );
    }

    @GetMapping("/settings")
    @RequirePermission(PermissionCodes.CONFIG_READ)
    public PageResponse<SettingView> settings(
            @Valid ManagementPageRequest pageRequest,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return consoleService.settings(pageRequest.toPageQuery());
    }

    @PostMapping("/settings")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(PermissionCodes.CONFIG_EDIT)
    public SettingView createSetting(
            @Valid @RequestBody CreateSettingRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return consoleService.createSetting(request, principal);
    }

    @GetMapping("/settings/{key}")
    @RequirePermission(PermissionCodes.CONFIG_READ)
    public SettingView setting(
            @PathVariable String key,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return consoleService.setting(key.trim());
    }

    @PatchMapping("/settings/{key}")
    @RequirePermission(PermissionCodes.CONFIG_EDIT)
    public SettingView updateSetting(
            @PathVariable String key,
            @Valid @RequestBody UpdateSettingRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return consoleService.updateSetting(key.trim(), request, principal);
    }

    @PatchMapping("/settings/{key}/status")
    @RequirePermission(PermissionCodes.CONFIG_EDIT)
    public SettingView changeSettingStatus(
            @PathVariable String key,
            @Valid @RequestBody StatusChangeRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return consoleService.changeSettingStatus(key.trim(), request.status(), principal);
    }

    @GetMapping("/secrets")
    @RequirePermission(PermissionCodes.SECRET_READ)
    public PageResponse<SecretReferenceView> secrets(
            @Valid ManagementPageRequest pageRequest,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return consoleService.secrets(pageRequest.toPageQuery());
    }

    @PostMapping("/secrets")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(PermissionCodes.SECRET_MANAGE)
    public SecretReferenceView createSecret(
            @Valid @RequestBody CreateSecretReferenceRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return consoleService.createSecret(request, principal);
    }

    @PostMapping("/secrets/rotate")
    @RequirePermission(PermissionCodes.SECRET_ROTATE)
    public SecretReferenceView rotateSecret(
            @Valid @RequestBody RotateSecretReferenceRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return consoleService.rotateSecret(request, principal);
    }

    @PostMapping("/secrets/disable")
    @RequirePermission(PermissionCodes.SECRET_DISABLE)
    public SecretReferenceView disableSecret(
            @Valid @RequestBody DisableSecretReferenceRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return consoleService.disableSecret(request, principal);
    }

    private String normalize(CreateNamedRequest request) {
        return request.name().trim();
    }

}
