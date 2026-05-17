package com.songhg.veri.agent.management.api;

import com.songhg.veri.agent.auth.application.AuthUserPrincipal;
import com.songhg.veri.agent.authorization.application.AuthorizationService;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.management.application.AuditLogQuery;
import com.songhg.veri.agent.management.application.ManagementWorkspaceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/management")
public class ManagementController {

    private final ManagementWorkspaceService workspaceService;
    private final AuthorizationService authorizationService;

    public ManagementController(
            ManagementWorkspaceService workspaceService,
            AuthorizationService authorizationService
    ) {
        this.workspaceService = workspaceService;
        this.authorizationService = authorizationService;
    }

    @GetMapping("/departments")
    public PageResponse<DepartmentView> departments(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = "") String search,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "department:read");
        return workspaceService.departments(page(page), pageSize(pageSize), search);
    }

    @PostMapping("/departments")
    @ResponseStatus(HttpStatus.CREATED)
    public DepartmentView createDepartment(
            @Valid @RequestBody CreateNamedRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "department:create");
        return workspaceService.createDepartment(normalize(request), principal);
    }

    @GetMapping("/departments/{key}")
    public DepartmentView department(
            @PathVariable String key,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "department:read");
        return workspaceService.department(key.trim());
    }

    @PatchMapping("/departments/{key}")
    public DepartmentView updateDepartment(
            @PathVariable String key,
            @Valid @RequestBody UpdateDepartmentRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "department:edit");
        return workspaceService.updateDepartment(key.trim(), request, principal);
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
        return workspaceService.changeDepartmentStatus(key.trim(), request.status(), principal);
    }

    @GetMapping("/users")
    public PageResponse<UserView> users(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = "") String search,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "user:read");
        return workspaceService.users(page(page), pageSize(pageSize), search);
    }

    @GetMapping("/users/{username}")
    public UserView user(
            @PathVariable String username,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "user:read");
        return workspaceService.user(username.trim());
    }

    @PatchMapping("/users/{username}")
    public UserView updateUser(
            @PathVariable String username,
            @Valid @RequestBody UpdateUserRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "user:edit");
        return workspaceService.updateUser(username.trim(), request, principal);
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public UserView createUser(
            @Valid @RequestBody CreateNamedRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "user:create");
        return workspaceService.createUser(normalize(request), principal);
    }

    @PostMapping("/users/{username}/enable")
    public UserView enableUser(
            @PathVariable String username,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "user:enable");
        return workspaceService.enableUser(username.trim(), principal);
    }

    @PostMapping("/users/{username}/disable")
    public UserView disableUser(
            @PathVariable String username,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "user:disable");
        return workspaceService.disableUser(username.trim(), principal);
    }

    @PostMapping("/users/{username}/lock")
    public UserView lockUser(
            @PathVariable String username,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "user:lock");
        return workspaceService.lockUser(username.trim(), principal);
    }

    @PostMapping("/users/{username}/unlock")
    public UserView unlockUser(
            @PathVariable String username,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "user:unlock");
        return workspaceService.unlockUser(username.trim(), principal);
    }

    @PostMapping("/users/{username}/reset-password")
    public UserView resetUserPassword(
            @PathVariable String username,
            @Valid @RequestBody ResetPasswordRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "user:reset_password");
        return workspaceService.resetUserPassword(username.trim(), request.newPassword(), principal);
    }

    @GetMapping("/roles")
    public PageResponse<RoleView> roles(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = "") String search,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "role:read");
        return workspaceService.roles(page(page), pageSize(pageSize), search);
    }

    @PostMapping("/users/{username}/roles")
    public UserView assignUserRole(
            @PathVariable String username,
            @Valid @RequestBody RoleBindingRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "user:assign_role");
        authorizationService.require(principal, "role:bind");
        return workspaceService.assignUserRole(username.trim(), request.roleCode().trim(), principal);
    }

    @PostMapping("/users/{username}/roles/unassign")
    public UserView unassignUserRole(
            @PathVariable String username,
            @Valid @RequestBody RoleBindingRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "user:assign_role");
        authorizationService.require(principal, "role:unbind");
        return workspaceService.unassignUserRole(username.trim(), request.roleCode().trim(), principal);
    }

    @GetMapping("/projects")
    public PageResponse<ProjectView> projects(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = "") String search,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "project:read");
        return workspaceService.projects(page(page), pageSize(pageSize), search, principal);
    }

    @PostMapping("/projects")
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectView createProject(
            @Valid @RequestBody CreateProjectRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "project:create");
        return workspaceService.createProject(request, principal);
    }

    @GetMapping("/projects/{key}")
    public ProjectView project(
            @PathVariable String key,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "project:read");
        return workspaceService.project(key.trim());
    }

    @PatchMapping("/projects/{key}")
    public ProjectView updateProject(
            @PathVariable String key,
            @Valid @RequestBody UpdateProjectRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "project:edit");
        return workspaceService.updateProject(key.trim(), request, principal);
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
        return workspaceService.changeProjectStatus(key.trim(), request.status(), principal);
    }

    @GetMapping("/projects/{key}/members")
    public PageResponse<ProjectMemberView> projectMembers(
            @PathVariable String key,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = "") String search,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "project:read");
        return workspaceService.projectMembers(key.trim(), page(page), pageSize(pageSize), search);
    }

    @PostMapping("/projects/{key}/members")
    public ProjectMemberView addProjectMember(
            @PathVariable String key,
            @Valid @RequestBody ProjectMemberRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "project:member_manage");
        authorizationService.require(principal, "role:bind");
        return workspaceService.addProjectMember(key.trim(), request, principal);
    }

    @PostMapping("/projects/{key}/members/{username}/remove")
    public ProjectMemberView removeProjectMember(
            @PathVariable String key,
            @PathVariable String username,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "project:member_manage");
        authorizationService.require(principal, "role:unbind");
        return workspaceService.removeProjectMember(key.trim(), username.trim(), principal);
    }

    @GetMapping("/applications")
    public PageResponse<ApplicationView> applications(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = "") String search,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "application:read");
        return workspaceService.applications(page(page), pageSize(pageSize), search, principal);
    }

    @PostMapping("/applications")
    @ResponseStatus(HttpStatus.CREATED)
    public ApplicationView createApplication(
            @Valid @RequestBody CreateApplicationRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "application:create");
        return workspaceService.createApplication(request, principal);
    }

    @GetMapping("/applications/{key}")
    public ApplicationView application(
            @PathVariable String key,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "application:read");
        return workspaceService.application(key.trim());
    }

    @PatchMapping("/applications/{key}")
    public ApplicationView updateApplication(
            @PathVariable String key,
            @Valid @RequestBody UpdateApplicationRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "application:edit");
        return workspaceService.updateApplication(key.trim(), request, principal);
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
        return workspaceService.changeApplicationStatus(key.trim(), request.status(), principal);
    }

    @GetMapping("/applications/{key}/owners")
    public PageResponse<ScopedUserRoleView> applicationOwners(
            @PathVariable String key,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = "") String search,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "application:read");
        return workspaceService.applicationOwners(key.trim(), page(page), pageSize(pageSize), search);
    }

    @PostMapping("/applications/{key}/owners")
    public ScopedUserRoleView addApplicationOwner(
            @PathVariable String key,
            @Valid @RequestBody ScopedUserRoleRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "application:owner_manage");
        authorizationService.require(principal, "role:bind");
        return workspaceService.addApplicationOwner(key.trim(), request, principal);
    }

    @PostMapping("/applications/{key}/owners/{username}/remove")
    public ScopedUserRoleView removeApplicationOwner(
            @PathVariable String key,
            @PathVariable String username,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "application:owner_manage");
        authorizationService.require(principal, "role:unbind");
        return workspaceService.removeApplicationOwner(key.trim(), username.trim(), principal);
    }

    @GetMapping("/environments")
    public PageResponse<EnvironmentView> environments(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = "") String search,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "environment:read");
        return workspaceService.environments(page(page), pageSize(pageSize), search, principal);
    }

    @PostMapping("/environments")
    @ResponseStatus(HttpStatus.CREATED)
    public EnvironmentView createEnvironment(
            @Valid @RequestBody CreateEnvironmentRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "environment:create");
        return workspaceService.createEnvironment(request, principal);
    }

    @GetMapping("/environments/{key}")
    public EnvironmentView environment(
            @PathVariable String key,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "environment:read");
        return workspaceService.environment(key.trim());
    }

    @PatchMapping("/environments/{key}")
    public EnvironmentView updateEnvironment(
            @PathVariable String key,
            @Valid @RequestBody UpdateEnvironmentRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "environment:edit");
        return workspaceService.updateEnvironment(key.trim(), request, principal);
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
        return workspaceService.changeEnvironmentStatus(key.trim(), request.status(), principal);
    }

    @GetMapping("/environments/{key}/users")
    public PageResponse<ScopedUserRoleView> environmentUsers(
            @PathVariable String key,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = "") String search,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "environment:read");
        return workspaceService.environmentUsers(key.trim(), page(page), pageSize(pageSize), search);
    }

    @PostMapping("/environments/{key}/users")
    public ScopedUserRoleView addEnvironmentUser(
            @PathVariable String key,
            @Valid @RequestBody ScopedUserRoleRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "environment:user_manage");
        authorizationService.require(principal, "role:bind");
        return workspaceService.addEnvironmentUser(key.trim(), request, principal);
    }

    @PostMapping("/environments/{key}/users/{username}/remove")
    public ScopedUserRoleView removeEnvironmentUser(
            @PathVariable String key,
            @PathVariable String username,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "environment:user_manage");
        authorizationService.require(principal, "role:unbind");
        return workspaceService.removeEnvironmentUser(key.trim(), username.trim(), principal);
    }

    @GetMapping("/integrations")
    public PageResponse<IntegrationView> integrations(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = "") String search,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "config:read");
        return workspaceService.integrations(page(page), pageSize(pageSize), search);
    }

    @PostMapping("/integrations")
    @ResponseStatus(HttpStatus.CREATED)
    public IntegrationView createIntegration(
            @Valid @RequestBody CreateIntegrationRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "config:edit");
        return workspaceService.createIntegration(request, principal);
    }

    @GetMapping("/integrations/{key}")
    public IntegrationView integration(
            @PathVariable String key,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "config:read");
        return workspaceService.integration(key.trim());
    }

    @PatchMapping("/integrations/{key}")
    public IntegrationView updateIntegration(
            @PathVariable String key,
            @Valid @RequestBody UpdateIntegrationRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "config:edit");
        return workspaceService.updateIntegration(key.trim(), request, principal);
    }

    @PatchMapping("/integrations/{key}/status")
    public IntegrationView changeIntegrationStatus(
            @PathVariable String key,
            @Valid @RequestBody StatusChangeRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "config:edit");
        return workspaceService.changeIntegrationStatus(key.trim(), request.status(), principal);
    }

    @GetMapping("/audit-logs")
    public PageResponse<AuditLogView> auditLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "") String actor,
            @RequestParam(defaultValue = "") String action,
            @RequestParam(name = "resource_type", defaultValue = "") String resourceType,
            @RequestParam(defaultValue = "") String result,
            @RequestParam(name = "start_time", defaultValue = "") String startTime,
            @RequestParam(name = "end_time", defaultValue = "") String endTime,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "audit:read");
        return workspaceService.auditLogs(
                page(page),
                pageSize(pageSize),
                AuditLogQuery.of(search, actor, action, resourceType, result, startTime, endTime),
                principal
        );
    }

    @GetMapping("/settings")
    public PageResponse<SettingView> settings(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = "") String search,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "config:read");
        return workspaceService.settings(page(page), pageSize(pageSize), search);
    }

    @PostMapping("/settings")
    @ResponseStatus(HttpStatus.CREATED)
    public SettingView createSetting(
            @Valid @RequestBody CreateSettingRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "config:edit");
        return workspaceService.createSetting(request, principal);
    }

    @GetMapping("/settings/{key}")
    public SettingView setting(
            @PathVariable String key,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "config:read");
        return workspaceService.setting(key.trim());
    }

    @PatchMapping("/settings/{key}")
    public SettingView updateSetting(
            @PathVariable String key,
            @Valid @RequestBody UpdateSettingRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "config:edit");
        return workspaceService.updateSetting(key.trim(), request, principal);
    }

    @PatchMapping("/settings/{key}/status")
    public SettingView changeSettingStatus(
            @PathVariable String key,
            @Valid @RequestBody StatusChangeRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        authorizationService.require(principal, "config:edit");
        return workspaceService.changeSettingStatus(key.trim(), request.status(), principal);
    }

    private String normalize(CreateNamedRequest request) {
        return request.name().trim();
    }

    private int page(int page) {
        return Math.max(1, page);
    }

    private int pageSize(int pageSize) {
        return Math.min(100, Math.max(1, pageSize));
    }
}
