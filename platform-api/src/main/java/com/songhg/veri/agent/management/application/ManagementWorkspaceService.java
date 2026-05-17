package com.songhg.veri.agent.management.application;

import com.songhg.veri.agent.auth.application.AuthUserPrincipal;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.management.api.ApplicationView;
import com.songhg.veri.agent.management.api.AuditLogView;
import com.songhg.veri.agent.management.api.CreateApplicationRequest;
import com.songhg.veri.agent.management.api.CreateEnvironmentRequest;
import com.songhg.veri.agent.management.api.CreateIntegrationRequest;
import com.songhg.veri.agent.management.api.CreateProjectRequest;
import com.songhg.veri.agent.management.api.CreateSettingRequest;
import com.songhg.veri.agent.management.api.DepartmentView;
import com.songhg.veri.agent.management.api.EnvironmentView;
import com.songhg.veri.agent.management.api.IntegrationView;
import com.songhg.veri.agent.management.api.ProjectView;
import com.songhg.veri.agent.management.api.ProjectMemberRequest;
import com.songhg.veri.agent.management.api.ProjectMemberView;
import com.songhg.veri.agent.management.api.RoleView;
import com.songhg.veri.agent.management.api.ScopedUserRoleRequest;
import com.songhg.veri.agent.management.api.ScopedUserRoleView;
import com.songhg.veri.agent.management.api.SettingView;
import com.songhg.veri.agent.management.api.UpdateDepartmentRequest;
import com.songhg.veri.agent.management.api.UpdateApplicationRequest;
import com.songhg.veri.agent.management.api.UpdateEnvironmentRequest;
import com.songhg.veri.agent.management.api.UpdateIntegrationRequest;
import com.songhg.veri.agent.management.api.UpdateProjectRequest;
import com.songhg.veri.agent.management.api.UpdateSettingRequest;
import com.songhg.veri.agent.management.api.UpdateUserRequest;
import com.songhg.veri.agent.management.api.UserView;

public interface ManagementWorkspaceService {

    PageResponse<DepartmentView> departments(int page, int pageSize, String search);

    DepartmentView createDepartment(String name, AuthUserPrincipal actor);

    DepartmentView department(String key);

    DepartmentView updateDepartment(String key, UpdateDepartmentRequest request, AuthUserPrincipal actor);

    DepartmentView changeDepartmentStatus(String key, String status, AuthUserPrincipal actor);

    PageResponse<UserView> users(int page, int pageSize, String search);

    UserView user(String username);

    UserView createUser(String username, AuthUserPrincipal actor);

    UserView updateUser(String username, UpdateUserRequest request, AuthUserPrincipal actor);

    UserView enableUser(String username, AuthUserPrincipal actor);

    UserView disableUser(String username, AuthUserPrincipal actor);

    UserView lockUser(String username, AuthUserPrincipal actor);

    UserView unlockUser(String username, AuthUserPrincipal actor);

    UserView resetUserPassword(String username, String newPassword, AuthUserPrincipal actor);

    PageResponse<RoleView> roles(int page, int pageSize, String search);

    UserView assignUserRole(String username, String roleCode, AuthUserPrincipal actor);

    UserView unassignUserRole(String username, String roleCode, AuthUserPrincipal actor);

    PageResponse<ProjectView> projects(int page, int pageSize, String search, AuthUserPrincipal actor);

    ProjectView project(String key);

    ProjectView createProject(CreateProjectRequest request, AuthUserPrincipal actor);

    ProjectView updateProject(String key, UpdateProjectRequest request, AuthUserPrincipal actor);

    ProjectView changeProjectStatus(String key, String status, AuthUserPrincipal actor);

    PageResponse<ProjectMemberView> projectMembers(String projectKey, int page, int pageSize, String search);

    ProjectMemberView addProjectMember(String projectKey, ProjectMemberRequest request, AuthUserPrincipal actor);

    ProjectMemberView removeProjectMember(String projectKey, String username, AuthUserPrincipal actor);

    PageResponse<ApplicationView> applications(int page, int pageSize, String search, AuthUserPrincipal actor);

    ApplicationView application(String key);

    ApplicationView createApplication(CreateApplicationRequest request, AuthUserPrincipal actor);

    ApplicationView updateApplication(String key, UpdateApplicationRequest request, AuthUserPrincipal actor);

    ApplicationView changeApplicationStatus(String key, String status, AuthUserPrincipal actor);

    PageResponse<ScopedUserRoleView> applicationOwners(String applicationKey, int page, int pageSize, String search);

    ScopedUserRoleView addApplicationOwner(String applicationKey, ScopedUserRoleRequest request, AuthUserPrincipal actor);

    ScopedUserRoleView removeApplicationOwner(String applicationKey, String username, AuthUserPrincipal actor);

    PageResponse<EnvironmentView> environments(int page, int pageSize, String search, AuthUserPrincipal actor);

    EnvironmentView environment(String key);

    EnvironmentView createEnvironment(CreateEnvironmentRequest request, AuthUserPrincipal actor);

    EnvironmentView updateEnvironment(String key, UpdateEnvironmentRequest request, AuthUserPrincipal actor);

    EnvironmentView changeEnvironmentStatus(String key, String status, AuthUserPrincipal actor);

    PageResponse<ScopedUserRoleView> environmentUsers(String environmentKey, int page, int pageSize, String search);

    ScopedUserRoleView addEnvironmentUser(String environmentKey, ScopedUserRoleRequest request, AuthUserPrincipal actor);

    ScopedUserRoleView removeEnvironmentUser(String environmentKey, String username, AuthUserPrincipal actor);

    PageResponse<IntegrationView> integrations(int page, int pageSize, String search);

    IntegrationView integration(String key);

    IntegrationView createIntegration(CreateIntegrationRequest request, AuthUserPrincipal actor);

    IntegrationView updateIntegration(String key, UpdateIntegrationRequest request, AuthUserPrincipal actor);

    IntegrationView changeIntegrationStatus(String key, String status, AuthUserPrincipal actor);

    PageResponse<AuditLogView> auditLogs(int page, int pageSize, AuditLogQuery query, AuthUserPrincipal actor);

    PageResponse<SettingView> settings(int page, int pageSize, String search);

    SettingView setting(String key);

    SettingView createSetting(CreateSettingRequest request, AuthUserPrincipal actor);

    SettingView updateSetting(String key, UpdateSettingRequest request, AuthUserPrincipal actor);

    SettingView changeSettingStatus(String key, String status, AuthUserPrincipal actor);
}
