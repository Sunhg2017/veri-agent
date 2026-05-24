package com.songhg.veri.agent.management.api.mapper;

import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.management.api.request.CreateApplicationRequest;
import com.songhg.veri.agent.management.api.request.CreateEnvironmentRequest;
import com.songhg.veri.agent.management.api.request.CreateIntegrationRequest;
import com.songhg.veri.agent.management.api.request.CreateProjectRequest;
import com.songhg.veri.agent.management.api.request.CreateRoleRequest;
import com.songhg.veri.agent.management.api.request.CreateSecretReferenceRequest;
import com.songhg.veri.agent.management.api.request.CreateSettingRequest;
import com.songhg.veri.agent.management.api.request.DisableSecretReferenceRequest;
import com.songhg.veri.agent.management.api.request.ProjectMemberRequest;
import com.songhg.veri.agent.management.api.request.RotateSecretReferenceRequest;
import com.songhg.veri.agent.management.api.request.ScopedUserRoleRequest;
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
import com.songhg.veri.agent.management.api.response.EnvironmentConnectivityEndpointView;
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
import java.util.function.Function;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * Keeps management HTTP DTO conversion at the API boundary so controllers do not leak transport
 * records into application services or persistence mappers.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ManagementApiMapper {

    com.songhg.veri.agent.management.application.command.CreateApplicationRequest toCommand(
            CreateApplicationRequest request
    );

    com.songhg.veri.agent.management.application.command.UpdateApplicationRequest toCommand(
            UpdateApplicationRequest request
    );

    com.songhg.veri.agent.management.application.command.CreateEnvironmentRequest toCommand(
            CreateEnvironmentRequest request
    );

    com.songhg.veri.agent.management.application.command.UpdateEnvironmentRequest toCommand(
            UpdateEnvironmentRequest request
    );

    com.songhg.veri.agent.management.application.command.CreateIntegrationRequest toCommand(
            CreateIntegrationRequest request
    );

    com.songhg.veri.agent.management.application.command.UpdateIntegrationRequest toCommand(
            UpdateIntegrationRequest request
    );

    com.songhg.veri.agent.management.application.command.CreateProjectRequest toCommand(CreateProjectRequest request);

    com.songhg.veri.agent.management.application.command.UpdateProjectRequest toCommand(UpdateProjectRequest request);

    com.songhg.veri.agent.management.application.command.CreateRoleRequest toCommand(CreateRoleRequest request);

    com.songhg.veri.agent.management.application.command.UpdateRoleRequest toCommand(UpdateRoleRequest request);

    com.songhg.veri.agent.management.application.command.ProjectMemberRequest toCommand(ProjectMemberRequest request);

    com.songhg.veri.agent.management.application.command.ScopedUserRoleRequest toCommand(
            ScopedUserRoleRequest request
    );

    com.songhg.veri.agent.management.application.command.CreateSettingRequest toCommand(CreateSettingRequest request);

    com.songhg.veri.agent.management.application.command.UpdateSettingRequest toCommand(UpdateSettingRequest request);

    com.songhg.veri.agent.management.application.command.UpdateDepartmentRequest toCommand(
            UpdateDepartmentRequest request
    );

    com.songhg.veri.agent.management.application.command.UpdateUserRequest toCommand(UpdateUserRequest request);

    com.songhg.veri.agent.management.application.command.CreateSecretReferenceRequest toCommand(
            CreateSecretReferenceRequest request
    );

    com.songhg.veri.agent.management.application.command.RotateSecretReferenceRequest toCommand(
            RotateSecretReferenceRequest request
    );

    com.songhg.veri.agent.management.application.command.DisableSecretReferenceRequest toCommand(
            DisableSecretReferenceRequest request
    );

    DepartmentView toResponse(com.songhg.veri.agent.management.application.view.DepartmentView view);

    UserView toResponse(com.songhg.veri.agent.management.application.view.UserView view);

    RoleView toResponse(com.songhg.veri.agent.management.application.view.RoleView view);

    RoleDetailView toResponse(com.songhg.veri.agent.management.application.view.RoleDetailView view);

    PermissionView toResponse(com.songhg.veri.agent.management.application.view.PermissionView view);

    ProjectView toResponse(com.songhg.veri.agent.management.application.view.ProjectView view);

    ProjectMemberView toResponse(com.songhg.veri.agent.management.application.view.ProjectMemberView view);

    ApplicationView toResponse(com.songhg.veri.agent.management.application.view.ApplicationView view);

    EnvironmentView toResponse(com.songhg.veri.agent.management.application.view.EnvironmentView view);

    EnvironmentConnectivityCheckView toResponse(
            com.songhg.veri.agent.management.application.view.EnvironmentConnectivityCheckView view
    );

    EnvironmentConnectivityEndpointView toResponse(
            com.songhg.veri.agent.management.application.view.EnvironmentConnectivityEndpointView view
    );

    ScopedUserRoleView toResponse(com.songhg.veri.agent.management.application.view.ScopedUserRoleView view);

    IntegrationView toResponse(com.songhg.veri.agent.management.application.view.IntegrationView view);

    AuditLogView toResponse(com.songhg.veri.agent.management.application.view.AuditLogView view);

    AuditOutboxView toResponse(com.songhg.veri.agent.management.application.view.AuditOutboxView view);

    SettingView toResponse(com.songhg.veri.agent.management.application.view.SettingView view);

    SecretReferenceView toResponse(com.songhg.veri.agent.management.application.view.SecretReferenceView view);

    default PageResponse<DepartmentView> toDepartmentPage(
            PageResponse<com.songhg.veri.agent.management.application.view.DepartmentView> page
    ) {
        return mapPage(page, item -> toResponse(item));
    }

    default PageResponse<UserView> toUserPage(
            PageResponse<com.songhg.veri.agent.management.application.view.UserView> page
    ) {
        return mapPage(page, item -> toResponse(item));
    }

    default PageResponse<RoleView> toRolePage(
            PageResponse<com.songhg.veri.agent.management.application.view.RoleView> page
    ) {
        return mapPage(page, item -> toResponse(item));
    }

    default PageResponse<PermissionView> toPermissionPage(
            PageResponse<com.songhg.veri.agent.management.application.view.PermissionView> page
    ) {
        return mapPage(page, item -> toResponse(item));
    }

    default PageResponse<ProjectView> toProjectPage(
            PageResponse<com.songhg.veri.agent.management.application.view.ProjectView> page
    ) {
        return mapPage(page, item -> toResponse(item));
    }

    default PageResponse<ProjectMemberView> toProjectMemberPage(
            PageResponse<com.songhg.veri.agent.management.application.view.ProjectMemberView> page
    ) {
        return mapPage(page, item -> toResponse(item));
    }

    default PageResponse<ApplicationView> toApplicationPage(
            PageResponse<com.songhg.veri.agent.management.application.view.ApplicationView> page
    ) {
        return mapPage(page, item -> toResponse(item));
    }

    default PageResponse<ScopedUserRoleView> toScopedUserRolePage(
            PageResponse<com.songhg.veri.agent.management.application.view.ScopedUserRoleView> page
    ) {
        return mapPage(page, item -> toResponse(item));
    }

    default PageResponse<EnvironmentView> toEnvironmentPage(
            PageResponse<com.songhg.veri.agent.management.application.view.EnvironmentView> page
    ) {
        return mapPage(page, item -> toResponse(item));
    }

    default PageResponse<IntegrationView> toIntegrationPage(
            PageResponse<com.songhg.veri.agent.management.application.view.IntegrationView> page
    ) {
        return mapPage(page, item -> toResponse(item));
    }

    default PageResponse<AuditLogView> toAuditLogPage(
            PageResponse<com.songhg.veri.agent.management.application.view.AuditLogView> page
    ) {
        return mapPage(page, item -> toResponse(item));
    }

    default PageResponse<AuditOutboxView> toAuditOutboxPage(
            PageResponse<com.songhg.veri.agent.management.application.view.AuditOutboxView> page
    ) {
        return mapPage(page, item -> toResponse(item));
    }

    default PageResponse<SettingView> toSettingPage(
            PageResponse<com.songhg.veri.agent.management.application.view.SettingView> page
    ) {
        return mapPage(page, item -> toResponse(item));
    }

    default PageResponse<SecretReferenceView> toSecretReferencePage(
            PageResponse<com.songhg.veri.agent.management.application.view.SecretReferenceView> page
    ) {
        return mapPage(page, item -> toResponse(item));
    }

    private <S, T> PageResponse<T> mapPage(PageResponse<S> page, Function<S, T> mapper) {
        return PageResponse.of(
                page.items().stream().map(mapper).toList(),
                page.index(),
                page.size(),
                page.total()
        );
    }
}
