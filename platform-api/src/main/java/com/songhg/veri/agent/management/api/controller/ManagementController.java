package com.songhg.veri.agent.management.api.controller;

import com.songhg.veri.agent.auth.application.AuthUserPrincipal;
import com.songhg.veri.agent.authorization.application.AuthorizationService;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.openapi.ApiVersion;
import com.songhg.veri.agent.management.application.AuditLogQuery;
import com.songhg.veri.agent.management.application.AuditOutboxQuery;
import com.songhg.veri.agent.management.application.ManagementConsoleService;
import com.songhg.veri.agent.management.api.request.AuditLogPageRequest;
import com.songhg.veri.agent.management.api.request.AuditOutboxPageRequest;
import com.songhg.veri.agent.management.api.request.CreateApplicationRequest;
import com.songhg.veri.agent.management.api.request.CreateEnvironmentRequest;
import com.songhg.veri.agent.management.api.request.CreateIntegrationRequest;
import com.songhg.veri.agent.management.api.request.CreateNamedRequest;
import com.songhg.veri.agent.management.api.request.CreateProjectRequest;
import com.songhg.veri.agent.management.api.request.CreateRoleRequest;
import com.songhg.veri.agent.management.api.request.CreateSecretReferenceRequest;
import com.songhg.veri.agent.management.api.request.CreateSettingRequest;
import com.songhg.veri.agent.management.api.request.DisableSecretReferenceRequest;
import com.songhg.veri.agent.management.api.request.ManagementPageRequest;
import com.songhg.veri.agent.management.api.request.ProjectMemberRequest;
import com.songhg.veri.agent.management.api.request.ResetPasswordRequest;
import com.songhg.veri.agent.management.api.request.RoleBindingRequest;
import com.songhg.veri.agent.management.api.request.RotateSecretReferenceRequest;
import com.songhg.veri.agent.management.api.request.ScopedUserRoleRequest;
import com.songhg.veri.agent.management.api.request.StatusChangeRequest;
import com.songhg.veri.agent.management.api.request.UpdateApplicationRequest;
import com.songhg.veri.agent.management.api.request.UpdateDepartmentRequest;
import com.songhg.veri.agent.management.api.request.UpdateEnvironmentRequest;
import com.songhg.veri.agent.management.api.request.UpdateIntegrationRequest;
import com.songhg.veri.agent.management.api.request.UpdateProjectRequest;
import com.songhg.veri.agent.management.api.request.UpdateRoleRequest;
import com.songhg.veri.agent.management.api.request.UpdateSettingRequest;
import com.songhg.veri.agent.management.api.request.UpdateUserRequest;
import com.songhg.veri.agent.management.api.response.ApplicationView;
import com.songhg.veri.agent.management.api.response.AuditLogView;
import com.songhg.veri.agent.management.api.response.AuditOutboxView;
import com.songhg.veri.agent.management.api.response.DepartmentView;
import com.songhg.veri.agent.management.api.response.EnvironmentConnectivityCheckView;
import com.songhg.veri.agent.management.api.response.EnvironmentView;
import com.songhg.veri.agent.management.api.response.IntegrationView;
import com.songhg.veri.agent.management.api.response.PermissionView;
import com.songhg.veri.agent.management.api.response.ProjectMemberView;
import com.songhg.veri.agent.management.api.response.ProjectView;
import com.songhg.veri.agent.management.api.response.RoleDetailView;
import com.songhg.veri.agent.management.api.response.RoleView;
import com.songhg.veri.agent.management.api.response.ScopedUserRoleView;
import com.songhg.veri.agent.management.api.response.SecretReferenceView;
import com.songhg.veri.agent.management.api.response.SettingView;
import com.songhg.veri.agent.management.api.response.UserView;
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
    public PageResponse<DepartmentView> departments(
            @Valid ManagementPageRequest pageRequest,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "department:read");
        return consoleService.departments(pageRequest.toPageQuery());
    }

    @PostMapping("/departments")
    @ResponseStatus(HttpStatus.CREATED)
    public DepartmentView createDepartment(
            @Valid @RequestBody CreateNamedRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "department:create");
        return consoleService.createDepartment(normalize(request), principal);
    }

    @GetMapping("/departments/{key}")
    public DepartmentView department(
            @PathVariable String key,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "department:read");
        return consoleService.department(key.trim());
    }

    @PatchMapping("/departments/{key}")
    public DepartmentView updateDepartment(
            @PathVariable String key,
            @Valid @RequestBody UpdateDepartmentRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "department:edit");
        return consoleService.updateDepartment(key.trim(), request, principal);
    }

    @PatchMapping("/departments/{key}/status")
    public DepartmentView changeDepartmentStatus(
            @PathVariable String key,
            @Valid @RequestBody StatusChangeRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        if ("DISABLED".equals(request.status())) {
            authorizationService.require(principal, "department:disable");
        } else {
            authorizationService.require(principal, "department:enable");
        }
        return consoleService.changeDepartmentStatus(key.trim(), request.status(), principal);
    }

    @GetMapping("/users")
    public PageResponse<UserView> users(
            @Valid ManagementPageRequest pageRequest,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "user:read");
        return consoleService.users(pageRequest.toPageQuery());
    }

    @GetMapping("/users/{username}")
    public UserView user(
            @PathVariable String username,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "user:read");
        return consoleService.user(username.trim());
    }

    @PatchMapping("/users/{username}")
    public UserView updateUser(
            @PathVariable String username,
            @Valid @RequestBody UpdateUserRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "user:edit");
        return consoleService.updateUser(username.trim(), request, principal);
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public UserView createUser(
            @Valid @RequestBody CreateNamedRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "user:create");
        return consoleService.createUser(normalize(request), principal);
    }

    @PostMapping("/users/{username}/enable")
    public UserView enableUser(
            @PathVariable String username,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "user:enable");
        return consoleService.enableUser(username.trim(), principal);
    }

    @PostMapping("/users/{username}/disable")
    public UserView disableUser(
            @PathVariable String username,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "user:disable");
        return consoleService.disableUser(username.trim(), principal);
    }

    @PostMapping("/users/{username}/lock")
    public UserView lockUser(
            @PathVariable String username,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "user:lock");
        return consoleService.lockUser(username.trim(), principal);
    }

    @PostMapping("/users/{username}/unlock")
    public UserView unlockUser(
            @PathVariable String username,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "user:unlock");
        return consoleService.unlockUser(username.trim(), principal);
    }

    @PostMapping("/users/{username}/reset-password")
    public UserView resetUserPassword(
            @PathVariable String username,
            @Valid @RequestBody ResetPasswordRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "user:reset_password");
        return consoleService.resetUserPassword(username.trim(), request.newPassword(), principal);
    }

    @GetMapping("/roles")
    public PageResponse<RoleView> roles(
            @Valid ManagementPageRequest pageRequest,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "role:read");
        return consoleService.roles(pageRequest.toPageQuery());
    }

    @GetMapping("/permissions")
    public PageResponse<PermissionView> permissions(
            @Valid ManagementPageRequest pageRequest,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "role:read");
        return consoleService.permissions(pageRequest.toPageQuery());
    }

    @GetMapping("/roles/{code}")
    public RoleDetailView role(
            @PathVariable String code,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "role:read");
        return consoleService.role(code.trim());
    }

    @PostMapping("/roles")
    @ResponseStatus(HttpStatus.CREATED)
    public RoleDetailView createRole(
            @Valid @RequestBody CreateRoleRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "role:create");
        Set<String> assignablePermissions = authorizationService.permissions(principal);
        return consoleService.createRole(request, assignablePermissions, principal);
    }

    @PatchMapping("/roles/{code}")
    public RoleDetailView updateRole(
            @PathVariable String code,
            @Valid @RequestBody UpdateRoleRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "role:edit");
        Set<String> assignablePermissions = authorizationService.permissions(principal);
        return consoleService.updateRole(code.trim(), request, assignablePermissions, principal);
    }

    @PatchMapping("/roles/{code}/status")
    public RoleDetailView changeRoleStatus(
            @PathVariable String code,
            @Valid @RequestBody StatusChangeRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "role:edit");
        return consoleService.changeRoleStatus(code.trim(), request.status(), principal);
    }

    @PostMapping("/users/{username}/roles")
    public UserView assignUserRole(
            @PathVariable String username,
            @Valid @RequestBody RoleBindingRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "user:assign_role");
        authorizationService.require(principal, "role:bind");
        return consoleService.assignUserRole(username.trim(), request.roleCode().trim(), principal);
    }

    @PostMapping("/users/{username}/roles/unassign")
    public UserView unassignUserRole(
            @PathVariable String username,
            @Valid @RequestBody RoleBindingRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "user:assign_role");
        authorizationService.require(principal, "role:unbind");
        return consoleService.unassignUserRole(username.trim(), request.roleCode().trim(), principal);
    }

    @GetMapping("/projects")
    public PageResponse<ProjectView> projects(
            @Valid ManagementPageRequest pageRequest,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "project:read");
        return consoleService.projects(pageRequest.toPageQuery(), principal);
    }

    @PostMapping("/projects")
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectView createProject(
            @Valid @RequestBody CreateProjectRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "project:create");
        return consoleService.createProject(request, principal);
    }

    @GetMapping("/projects/{key}")
    public ProjectView project(
            @PathVariable String key,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "project:read");
        return consoleService.project(key.trim());
    }

    @PatchMapping("/projects/{key}")
    public ProjectView updateProject(
            @PathVariable String key,
            @Valid @RequestBody UpdateProjectRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "project:edit");
        return consoleService.updateProject(key.trim(), request, principal);
    }

    @PatchMapping("/projects/{key}/status")
    public ProjectView changeProjectStatus(
            @PathVariable String key,
            @Valid @RequestBody StatusChangeRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        if ("ARCHIVED".equals(request.status())) {
            authorizationService.require(principal, "project:archive");
        } else if ("DISABLED".equals(request.status())) {
            authorizationService.require(principal, "project:disable");
        } else {
            authorizationService.require(principal, "project:edit");
        }
        return consoleService.changeProjectStatus(key.trim(), request.status(), principal);
    }

    @GetMapping("/projects/{key}/members")
    public PageResponse<ProjectMemberView> projectMembers(
            @PathVariable String key,
            @Valid ManagementPageRequest pageRequest,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "project:read");
        return consoleService.projectMembers(key.trim(), pageRequest.toPageQuery());
    }

    @PostMapping("/projects/{key}/members")
    public ProjectMemberView addProjectMember(
            @PathVariable String key,
            @Valid @RequestBody ProjectMemberRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "project:member_manage");
        authorizationService.require(principal, "role:bind");
        return consoleService.addProjectMember(key.trim(), request, principal);
    }

    @PostMapping("/projects/{key}/members/{username}/remove")
    public ProjectMemberView removeProjectMember(
            @PathVariable String key,
            @PathVariable String username,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "project:member_manage");
        authorizationService.require(principal, "role:unbind");
        return consoleService.removeProjectMember(key.trim(), username.trim(), principal);
    }

    @GetMapping("/applications")
    public PageResponse<ApplicationView> applications(
            @Valid ManagementPageRequest pageRequest,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "application:read");
        return consoleService.applications(pageRequest.toPageQuery(), principal);
    }

    @PostMapping("/applications")
    @ResponseStatus(HttpStatus.CREATED)
    public ApplicationView createApplication(
            @Valid @RequestBody CreateApplicationRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "application:create");
        return consoleService.createApplication(request, principal);
    }

    @GetMapping("/applications/{key}")
    public ApplicationView application(
            @PathVariable String key,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "application:read");
        return consoleService.application(key.trim());
    }

    @PatchMapping("/applications/{key}")
    public ApplicationView updateApplication(
            @PathVariable String key,
            @Valid @RequestBody UpdateApplicationRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "application:edit");
        return consoleService.updateApplication(key.trim(), request, principal);
    }

    @PatchMapping("/applications/{key}/status")
    public ApplicationView changeApplicationStatus(
            @PathVariable String key,
            @Valid @RequestBody StatusChangeRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        if ("DISABLED".equals(request.status())) {
            authorizationService.require(principal, "application:disable");
        } else {
            authorizationService.require(principal, "application:edit");
        }
        return consoleService.changeApplicationStatus(key.trim(), request.status(), principal);
    }

    @GetMapping("/applications/{key}/owners")
    public PageResponse<ScopedUserRoleView> applicationOwners(
            @PathVariable String key,
            @Valid ManagementPageRequest pageRequest,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "application:read");
        return consoleService.applicationOwners(key.trim(), pageRequest.toPageQuery());
    }

    @PostMapping("/applications/{key}/owners")
    public ScopedUserRoleView addApplicationOwner(
            @PathVariable String key,
            @Valid @RequestBody ScopedUserRoleRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "application:owner_manage");
        authorizationService.require(principal, "role:bind");
        return consoleService.addApplicationOwner(key.trim(), request, principal);
    }

    @PostMapping("/applications/{key}/owners/{username}/remove")
    public ScopedUserRoleView removeApplicationOwner(
            @PathVariable String key,
            @PathVariable String username,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "application:owner_manage");
        authorizationService.require(principal, "role:unbind");
        return consoleService.removeApplicationOwner(key.trim(), username.trim(), principal);
    }

    @GetMapping("/environments")
    public PageResponse<EnvironmentView> environments(
            @Valid ManagementPageRequest pageRequest,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "environment:read");
        return consoleService.environments(pageRequest.toPageQuery(), principal);
    }

    @PostMapping("/environments")
    @ResponseStatus(HttpStatus.CREATED)
    public EnvironmentView createEnvironment(
            @Valid @RequestBody CreateEnvironmentRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "environment:create");
        return consoleService.createEnvironment(request, principal);
    }

    @GetMapping("/environments/{key}")
    public EnvironmentView environment(
            @PathVariable String key,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "environment:read");
        return consoleService.environment(key.trim());
    }

    @PatchMapping("/environments/{key}")
    public EnvironmentView updateEnvironment(
            @PathVariable String key,
            @Valid @RequestBody UpdateEnvironmentRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "environment:edit");
        return consoleService.updateEnvironment(key.trim(), request, principal);
    }

    @PatchMapping("/environments/{key}/status")
    public EnvironmentView changeEnvironmentStatus(
            @PathVariable String key,
            @Valid @RequestBody StatusChangeRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        if ("DISABLED".equals(request.status())) {
            authorizationService.require(principal, "environment:disable");
        } else {
            authorizationService.require(principal, "environment:edit");
        }
        return consoleService.changeEnvironmentStatus(key.trim(), request.status(), principal);
    }

    @GetMapping("/environments/{key}/connectivity-check")
    public EnvironmentConnectivityCheckView environmentConnectivityCheck(
            @PathVariable String key,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "environment:read");
        return consoleService.environmentConnectivityCheck(key.trim());
    }

    @PostMapping("/environments/{key}/connectivity-check")
    public EnvironmentConnectivityCheckView checkEnvironmentConnectivity(
            @PathVariable String key,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "environment:edit");
        return consoleService.checkEnvironmentConnectivity(key.trim(), principal);
    }

    @GetMapping("/environments/{key}/users")
    public PageResponse<ScopedUserRoleView> environmentUsers(
            @PathVariable String key,
            @Valid ManagementPageRequest pageRequest,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "environment:read");
        return consoleService.environmentUsers(key.trim(), pageRequest.toPageQuery());
    }

    @PostMapping("/environments/{key}/users")
    public ScopedUserRoleView addEnvironmentUser(
            @PathVariable String key,
            @Valid @RequestBody ScopedUserRoleRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "environment:user_manage");
        authorizationService.require(principal, "role:bind");
        return consoleService.addEnvironmentUser(key.trim(), request, principal);
    }

    @PostMapping("/environments/{key}/users/{username}/remove")
    public ScopedUserRoleView removeEnvironmentUser(
            @PathVariable String key,
            @PathVariable String username,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "environment:user_manage");
        authorizationService.require(principal, "role:unbind");
        return consoleService.removeEnvironmentUser(key.trim(), username.trim(), principal);
    }

    @GetMapping("/integrations")
    public PageResponse<IntegrationView> integrations(
            @Valid ManagementPageRequest pageRequest,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "config:read");
        return consoleService.integrations(pageRequest.toPageQuery());
    }

    @PostMapping("/integrations")
    @ResponseStatus(HttpStatus.CREATED)
    public IntegrationView createIntegration(
            @Valid @RequestBody CreateIntegrationRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "config:edit");
        return consoleService.createIntegration(request, principal);
    }

    @GetMapping("/integrations/{key}")
    public IntegrationView integration(
            @PathVariable String key,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "config:read");
        return consoleService.integration(key.trim());
    }

    @PatchMapping("/integrations/{key}")
    public IntegrationView updateIntegration(
            @PathVariable String key,
            @Valid @RequestBody UpdateIntegrationRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "config:edit");
        return consoleService.updateIntegration(key.trim(), request, principal);
    }

    @PatchMapping("/integrations/{key}/status")
    public IntegrationView changeIntegrationStatus(
            @PathVariable String key,
            @Valid @RequestBody StatusChangeRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "config:edit");
        return consoleService.changeIntegrationStatus(key.trim(), request.status(), principal);
    }

    @GetMapping("/audit-logs")
    public PageResponse<AuditLogView> auditLogs(
            @Valid AuditLogPageRequest pageRequest,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "audit:read");
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
    public ResponseEntity<String> exportAuditLogs(
            @Valid AuditLogPageRequest pageRequest,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "audit:read");
        authorizationService.require(principal, "audit:export");
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
    public PageResponse<AuditOutboxView> auditOutbox(
            @Valid AuditOutboxPageRequest pageRequest,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "audit:read");
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
    public PageResponse<SettingView> settings(
            @Valid ManagementPageRequest pageRequest,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "config:read");
        return consoleService.settings(pageRequest.toPageQuery());
    }

    @PostMapping("/settings")
    @ResponseStatus(HttpStatus.CREATED)
    public SettingView createSetting(
            @Valid @RequestBody CreateSettingRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "config:edit");
        return consoleService.createSetting(request, principal);
    }

    @GetMapping("/settings/{key}")
    public SettingView setting(
            @PathVariable String key,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "config:read");
        return consoleService.setting(key.trim());
    }

    @PatchMapping("/settings/{key}")
    public SettingView updateSetting(
            @PathVariable String key,
            @Valid @RequestBody UpdateSettingRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "config:edit");
        return consoleService.updateSetting(key.trim(), request, principal);
    }

    @PatchMapping("/settings/{key}/status")
    public SettingView changeSettingStatus(
            @PathVariable String key,
            @Valid @RequestBody StatusChangeRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "config:edit");
        return consoleService.changeSettingStatus(key.trim(), request.status(), principal);
    }

    @GetMapping("/secrets")
    public PageResponse<SecretReferenceView> secrets(
            @Valid ManagementPageRequest pageRequest,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "secret:read");
        return consoleService.secrets(pageRequest.toPageQuery());
    }

    @PostMapping("/secrets")
    @ResponseStatus(HttpStatus.CREATED)
    public SecretReferenceView createSecret(
            @Valid @RequestBody CreateSecretReferenceRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "secret:manage");
        return consoleService.createSecret(request, principal);
    }

    @PostMapping("/secrets/rotate")
    public SecretReferenceView rotateSecret(
            @Valid @RequestBody RotateSecretReferenceRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "secret:rotate");
        return consoleService.rotateSecret(request, principal);
    }

    @PostMapping("/secrets/disable")
    public SecretReferenceView disableSecret(
            @Valid @RequestBody DisableSecretReferenceRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "secret:disable");
        return consoleService.disableSecret(request, principal);
    }

    private String normalize(CreateNamedRequest request) {
        return request.name().trim();
    }

}
