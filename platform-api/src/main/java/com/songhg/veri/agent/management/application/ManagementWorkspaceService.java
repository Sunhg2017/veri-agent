package com.songhg.veri.agent.management.application;

import com.songhg.veri.agent.auth.application.AuthUserPrincipal;
import com.songhg.veri.agent.common.api.PageQuery;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.management.api.response.ApplicationView;
import com.songhg.veri.agent.management.api.response.AuditLogView;
import com.songhg.veri.agent.management.api.response.AuditOutboxView;
import com.songhg.veri.agent.management.api.request.CreateApplicationRequest;
import com.songhg.veri.agent.management.api.request.CreateEnvironmentRequest;
import com.songhg.veri.agent.management.api.request.CreateIntegrationRequest;
import com.songhg.veri.agent.management.api.request.CreateProjectRequest;
import com.songhg.veri.agent.management.api.request.CreateSettingRequest;
import com.songhg.veri.agent.management.api.response.DepartmentView;
import com.songhg.veri.agent.management.api.response.EnvironmentView;
import com.songhg.veri.agent.management.api.response.IntegrationView;
import com.songhg.veri.agent.management.api.response.ProjectView;
import com.songhg.veri.agent.management.api.request.ProjectMemberRequest;
import com.songhg.veri.agent.management.api.response.ProjectMemberView;
import com.songhg.veri.agent.management.api.response.RoleView;
import com.songhg.veri.agent.management.api.request.ScopedUserRoleRequest;
import com.songhg.veri.agent.management.api.response.ScopedUserRoleView;
import com.songhg.veri.agent.management.api.response.SettingView;
import com.songhg.veri.agent.management.api.request.UpdateDepartmentRequest;
import com.songhg.veri.agent.management.api.request.UpdateApplicationRequest;
import com.songhg.veri.agent.management.api.request.UpdateEnvironmentRequest;
import com.songhg.veri.agent.management.api.request.UpdateIntegrationRequest;
import com.songhg.veri.agent.management.api.request.UpdateProjectRequest;
import com.songhg.veri.agent.management.api.request.UpdateSettingRequest;
import com.songhg.veri.agent.management.api.request.UpdateUserRequest;
import com.songhg.veri.agent.management.api.response.UserView;

public interface ManagementWorkspaceService {

    PageResponse<DepartmentView> departments(PageQuery pageQuery);

    DepartmentView createDepartment(String name, AuthUserPrincipal actor);

    DepartmentView department(String key);

    DepartmentView updateDepartment(String key, UpdateDepartmentRequest request, AuthUserPrincipal actor);

    DepartmentView changeDepartmentStatus(String key, String status, AuthUserPrincipal actor);

    PageResponse<UserView> users(PageQuery pageQuery);

    UserView user(String username);

    UserView createUser(String username, AuthUserPrincipal actor);

    UserView updateUser(String username, UpdateUserRequest request, AuthUserPrincipal actor);

    UserView enableUser(String username, AuthUserPrincipal actor);

    UserView disableUser(String username, AuthUserPrincipal actor);

    UserView lockUser(String username, AuthUserPrincipal actor);

    UserView unlockUser(String username, AuthUserPrincipal actor);

    UserView resetUserPassword(String username, String newPassword, AuthUserPrincipal actor);

    PageResponse<RoleView> roles(PageQuery pageQuery);

    UserView assignUserRole(String username, String roleCode, AuthUserPrincipal actor);

    UserView unassignUserRole(String username, String roleCode, AuthUserPrincipal actor);

    PageResponse<ProjectView> projects(PageQuery pageQuery, AuthUserPrincipal actor);

    ProjectView project(String key);

    ProjectView createProject(CreateProjectRequest request, AuthUserPrincipal actor);

    ProjectView updateProject(String key, UpdateProjectRequest request, AuthUserPrincipal actor);

    ProjectView changeProjectStatus(String key, String status, AuthUserPrincipal actor);

    PageResponse<ProjectMemberView> projectMembers(String projectKey, PageQuery pageQuery);

    ProjectMemberView addProjectMember(String projectKey, ProjectMemberRequest request, AuthUserPrincipal actor);

    ProjectMemberView removeProjectMember(String projectKey, String username, AuthUserPrincipal actor);

    PageResponse<ApplicationView> applications(PageQuery pageQuery, AuthUserPrincipal actor);

    ApplicationView application(String key);

    ApplicationView createApplication(CreateApplicationRequest request, AuthUserPrincipal actor);

    ApplicationView updateApplication(String key, UpdateApplicationRequest request, AuthUserPrincipal actor);

    ApplicationView changeApplicationStatus(String key, String status, AuthUserPrincipal actor);

    PageResponse<ScopedUserRoleView> applicationOwners(String applicationKey, PageQuery pageQuery);

    ScopedUserRoleView addApplicationOwner(String applicationKey, ScopedUserRoleRequest request, AuthUserPrincipal actor);

    ScopedUserRoleView removeApplicationOwner(String applicationKey, String username, AuthUserPrincipal actor);

    PageResponse<EnvironmentView> environments(PageQuery pageQuery, AuthUserPrincipal actor);

    EnvironmentView environment(String key);

    EnvironmentView createEnvironment(CreateEnvironmentRequest request, AuthUserPrincipal actor);

    EnvironmentView updateEnvironment(String key, UpdateEnvironmentRequest request, AuthUserPrincipal actor);

    EnvironmentView changeEnvironmentStatus(String key, String status, AuthUserPrincipal actor);

    PageResponse<ScopedUserRoleView> environmentUsers(String environmentKey, PageQuery pageQuery);

    ScopedUserRoleView addEnvironmentUser(String environmentKey, ScopedUserRoleRequest request, AuthUserPrincipal actor);

    ScopedUserRoleView removeEnvironmentUser(String environmentKey, String username, AuthUserPrincipal actor);

    PageResponse<IntegrationView> integrations(PageQuery pageQuery);

    IntegrationView integration(String key);

    IntegrationView createIntegration(CreateIntegrationRequest request, AuthUserPrincipal actor);

    IntegrationView updateIntegration(String key, UpdateIntegrationRequest request, AuthUserPrincipal actor);

    IntegrationView changeIntegrationStatus(String key, String status, AuthUserPrincipal actor);

    PageResponse<AuditLogView> auditLogs(PageQuery pageQuery, AuditLogQuery query, AuthUserPrincipal actor);

    String exportAuditLogsCsv(AuditLogQuery query, AuthUserPrincipal actor);

    PageResponse<AuditOutboxView> auditOutbox(PageQuery pageQuery, AuditOutboxQuery query, AuthUserPrincipal actor);

    PageResponse<SettingView> settings(PageQuery pageQuery);

    SettingView setting(String key);

    SettingView createSetting(CreateSettingRequest request, AuthUserPrincipal actor);

    SettingView updateSetting(String key, UpdateSettingRequest request, AuthUserPrincipal actor);

    SettingView changeSettingStatus(String key, String status, AuthUserPrincipal actor);
}
