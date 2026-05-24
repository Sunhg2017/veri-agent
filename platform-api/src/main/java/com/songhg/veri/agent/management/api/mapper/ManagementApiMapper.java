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
import com.songhg.veri.agent.management.api.response.ApplicationResponse;
import com.songhg.veri.agent.management.api.response.AuditLogResponse;
import com.songhg.veri.agent.management.api.response.AuditOutboxResponse;
import com.songhg.veri.agent.management.api.response.DepartmentResponse;
import com.songhg.veri.agent.management.api.response.EnvironmentConnectivityCheckResponse;
import com.songhg.veri.agent.management.api.response.EnvironmentConnectivityEndpointResponse;
import com.songhg.veri.agent.management.api.response.EnvironmentResponse;
import com.songhg.veri.agent.management.api.response.IntegrationResponse;
import com.songhg.veri.agent.management.api.response.PermissionResponse;
import com.songhg.veri.agent.management.api.response.ProjectMemberResponse;
import com.songhg.veri.agent.management.api.response.ProjectResponse;
import com.songhg.veri.agent.management.api.response.RoleDetailResponse;
import com.songhg.veri.agent.management.api.response.RoleResponse;
import com.songhg.veri.agent.management.api.response.ScopedUserRoleResponse;
import com.songhg.veri.agent.management.api.response.SecretReferenceResponse;
import com.songhg.veri.agent.management.api.response.SettingResponse;
import com.songhg.veri.agent.management.api.response.UserResponse;
import com.songhg.veri.agent.management.application.command.CreateApplicationCommand;
import com.songhg.veri.agent.management.application.command.CreateEnvironmentCommand;
import com.songhg.veri.agent.management.application.command.CreateIntegrationCommand;
import com.songhg.veri.agent.management.application.command.CreateProjectCommand;
import com.songhg.veri.agent.management.application.command.CreateRoleCommand;
import com.songhg.veri.agent.management.application.command.CreateSecretReferenceCommand;
import com.songhg.veri.agent.management.application.command.CreateSettingCommand;
import com.songhg.veri.agent.management.application.command.DisableSecretReferenceCommand;
import com.songhg.veri.agent.management.application.command.ProjectMemberCommand;
import com.songhg.veri.agent.management.application.command.RotateSecretReferenceCommand;
import com.songhg.veri.agent.management.application.command.ScopedUserRoleCommand;
import com.songhg.veri.agent.management.application.command.UpdateApplicationCommand;
import com.songhg.veri.agent.management.application.command.UpdateDepartmentCommand;
import com.songhg.veri.agent.management.application.command.UpdateEnvironmentCommand;
import com.songhg.veri.agent.management.application.command.UpdateIntegrationCommand;
import com.songhg.veri.agent.management.application.command.UpdateProjectCommand;
import com.songhg.veri.agent.management.application.command.UpdateRoleCommand;
import com.songhg.veri.agent.management.application.command.UpdateSettingCommand;
import com.songhg.veri.agent.management.application.command.UpdateUserCommand;
import com.songhg.veri.agent.management.application.view.ApplicationView;
import com.songhg.veri.agent.management.application.view.AuditLogView;
import com.songhg.veri.agent.management.application.view.AuditOutboxView;
import com.songhg.veri.agent.management.application.view.DepartmentView;
import com.songhg.veri.agent.management.application.view.EnvironmentConnectivityCheckView;
import com.songhg.veri.agent.management.application.view.EnvironmentConnectivityEndpointView;
import com.songhg.veri.agent.management.application.view.EnvironmentView;
import com.songhg.veri.agent.management.application.view.IntegrationView;
import com.songhg.veri.agent.management.application.view.PermissionView;
import com.songhg.veri.agent.management.application.view.ProjectMemberView;
import com.songhg.veri.agent.management.application.view.ProjectView;
import com.songhg.veri.agent.management.application.view.RoleDetailView;
import com.songhg.veri.agent.management.application.view.RoleView;
import com.songhg.veri.agent.management.application.view.ScopedUserRoleView;
import com.songhg.veri.agent.management.application.view.SecretReferenceView;
import com.songhg.veri.agent.management.application.view.SettingView;
import com.songhg.veri.agent.management.application.view.UserView;
import java.util.function.Function;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * Keeps management HTTP DTO conversion at the API boundary so controllers do not leak transport
 * records into application services or persistence mappers.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ManagementApiMapper {

    CreateApplicationCommand toCommand(CreateApplicationRequest request);

    UpdateApplicationCommand toCommand(UpdateApplicationRequest request);

    CreateEnvironmentCommand toCommand(CreateEnvironmentRequest request);

    UpdateEnvironmentCommand toCommand(UpdateEnvironmentRequest request);

    CreateIntegrationCommand toCommand(CreateIntegrationRequest request);

    UpdateIntegrationCommand toCommand(UpdateIntegrationRequest request);

    CreateProjectCommand toCommand(CreateProjectRequest request);

    UpdateProjectCommand toCommand(UpdateProjectRequest request);

    CreateRoleCommand toCommand(CreateRoleRequest request);

    UpdateRoleCommand toCommand(UpdateRoleRequest request);

    ProjectMemberCommand toCommand(ProjectMemberRequest request);

    ScopedUserRoleCommand toCommand(ScopedUserRoleRequest request);

    CreateSettingCommand toCommand(CreateSettingRequest request);

    UpdateSettingCommand toCommand(UpdateSettingRequest request);

    UpdateDepartmentCommand toCommand(UpdateDepartmentRequest request);

    UpdateUserCommand toCommand(UpdateUserRequest request);

    CreateSecretReferenceCommand toCommand(CreateSecretReferenceRequest request);

    RotateSecretReferenceCommand toCommand(RotateSecretReferenceRequest request);

    DisableSecretReferenceCommand toCommand(DisableSecretReferenceRequest request);

    DepartmentResponse toResponse(DepartmentView view);

    UserResponse toResponse(UserView view);

    RoleResponse toResponse(RoleView view);

    RoleDetailResponse toResponse(RoleDetailView view);

    PermissionResponse toResponse(PermissionView view);

    ProjectResponse toResponse(ProjectView view);

    ProjectMemberResponse toResponse(ProjectMemberView view);

    ApplicationResponse toResponse(ApplicationView view);

    EnvironmentResponse toResponse(EnvironmentView view);

    EnvironmentConnectivityCheckResponse toResponse(EnvironmentConnectivityCheckView view);

    EnvironmentConnectivityEndpointResponse toResponse(EnvironmentConnectivityEndpointView view);

    ScopedUserRoleResponse toResponse(ScopedUserRoleView view);

    IntegrationResponse toResponse(IntegrationView view);

    AuditLogResponse toResponse(AuditLogView view);

    AuditOutboxResponse toResponse(AuditOutboxView view);

    SettingResponse toResponse(SettingView view);

    SecretReferenceResponse toResponse(SecretReferenceView view);

    default PageResponse<DepartmentResponse> toDepartmentPage(PageResponse<DepartmentView> page) {
        return mapPage(page, item -> toResponse(item));
    }

    default PageResponse<UserResponse> toUserPage(PageResponse<UserView> page) {
        return mapPage(page, item -> toResponse(item));
    }

    default PageResponse<RoleResponse> toRolePage(PageResponse<RoleView> page) {
        return mapPage(page, item -> toResponse(item));
    }

    default PageResponse<PermissionResponse> toPermissionPage(PageResponse<PermissionView> page) {
        return mapPage(page, item -> toResponse(item));
    }

    default PageResponse<ProjectResponse> toProjectPage(PageResponse<ProjectView> page) {
        return mapPage(page, item -> toResponse(item));
    }

    default PageResponse<ProjectMemberResponse> toProjectMemberPage(PageResponse<ProjectMemberView> page) {
        return mapPage(page, item -> toResponse(item));
    }

    default PageResponse<ApplicationResponse> toApplicationPage(PageResponse<ApplicationView> page) {
        return mapPage(page, item -> toResponse(item));
    }

    default PageResponse<ScopedUserRoleResponse> toScopedUserRolePage(PageResponse<ScopedUserRoleView> page) {
        return mapPage(page, item -> toResponse(item));
    }

    default PageResponse<EnvironmentResponse> toEnvironmentPage(PageResponse<EnvironmentView> page) {
        return mapPage(page, item -> toResponse(item));
    }

    default PageResponse<IntegrationResponse> toIntegrationPage(PageResponse<IntegrationView> page) {
        return mapPage(page, item -> toResponse(item));
    }

    default PageResponse<AuditLogResponse> toAuditLogPage(PageResponse<AuditLogView> page) {
        return mapPage(page, item -> toResponse(item));
    }

    default PageResponse<AuditOutboxResponse> toAuditOutboxPage(PageResponse<AuditOutboxView> page) {
        return mapPage(page, item -> toResponse(item));
    }

    default PageResponse<SettingResponse> toSettingPage(PageResponse<SettingView> page) {
        return mapPage(page, item -> toResponse(item));
    }

    default PageResponse<SecretReferenceResponse> toSecretReferencePage(PageResponse<SecretReferenceView> page) {
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
