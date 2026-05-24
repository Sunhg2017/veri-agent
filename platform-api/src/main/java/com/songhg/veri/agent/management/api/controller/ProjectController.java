package com.songhg.veri.agent.management.api.controller;

import com.songhg.veri.agent.auth.application.AuthUserPrincipal;
import com.songhg.veri.agent.authorization.application.PermissionCodes;
import com.songhg.veri.agent.authorization.application.RequirePermission;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.openapi.ApiVersion;
import com.songhg.veri.agent.management.api.mapper.ManagementApiMapper;
import com.songhg.veri.agent.management.api.request.CreateProjectRequest;
import com.songhg.veri.agent.management.api.request.ManagementPageRequest;
import com.songhg.veri.agent.management.api.request.ProjectMemberRequest;
import com.songhg.veri.agent.management.api.request.StatusChangeRequest;
import com.songhg.veri.agent.management.api.request.UpdateProjectRequest;
import com.songhg.veri.agent.management.api.response.ProjectMemberView;
import com.songhg.veri.agent.management.api.response.ProjectView;
import com.songhg.veri.agent.management.application.port.ProjectOperations;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Project management endpoint. Payload-dependent status authorization is handled below the HTTP
 * boundary so the resource controller only translates requests and responses.
 */
@ApiVersion
@RestController
@RequestMapping("/api/v1/management")
public class ProjectController {

    private final ProjectOperations projectOperations;
    private final ManagementApiMapper mapper;

    public ProjectController(
            ProjectOperations projectOperations,
            ManagementApiMapper mapper
    ) {
        this.projectOperations = projectOperations;
        this.mapper = mapper;
    }

    @GetMapping("/projects")
    @RequirePermission(PermissionCodes.PROJECT_READ)
    public PageResponse<ProjectView> projects(
            @Valid ManagementPageRequest pageRequest,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return mapper.toProjectPage(projectOperations.projects(pageRequest.toPageQuery(), principal));
    }

    @PostMapping("/projects")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(PermissionCodes.PROJECT_CREATE)
    public ProjectView createProject(
            @Valid @RequestBody CreateProjectRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return mapper.toResponse(projectOperations.createProject(mapper.toCommand(request), principal));
    }

    @GetMapping("/projects/{key}")
    @RequirePermission(PermissionCodes.PROJECT_READ)
    public ProjectView project(
            @PathVariable String key,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return mapper.toResponse(projectOperations.project(key.trim()));
    }

    @PatchMapping("/projects/{key}")
    @RequirePermission(PermissionCodes.PROJECT_EDIT)
    public ProjectView updateProject(
            @PathVariable String key,
            @Valid @RequestBody UpdateProjectRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return mapper.toResponse(projectOperations.updateProject(key.trim(), mapper.toCommand(request), principal));
    }

    @PatchMapping("/projects/{key}/status")
    public ProjectView changeProjectStatus(
            @PathVariable String key,
            @Valid @RequestBody StatusChangeRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return mapper.toResponse(projectOperations.changeProjectStatus(key.trim(), request.status(), principal));
    }

    @GetMapping("/projects/{key}/members")
    @RequirePermission(PermissionCodes.PROJECT_READ)
    public PageResponse<ProjectMemberView> projectMembers(
            @PathVariable String key,
            @Valid ManagementPageRequest pageRequest,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return mapper.toProjectMemberPage(projectOperations.projectMembers(key.trim(), pageRequest.toPageQuery()));
    }

    @PostMapping("/projects/{key}/members")
    @RequirePermission(PermissionCodes.PROJECT_MEMBER_MANAGE)
    @RequirePermission(PermissionCodes.ROLE_BIND)
    public ProjectMemberView addProjectMember(
            @PathVariable String key,
            @Valid @RequestBody ProjectMemberRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return mapper.toResponse(projectOperations.addProjectMember(key.trim(), mapper.toCommand(request), principal));
    }

    @PostMapping("/projects/{key}/members/{username}/remove")
    @RequirePermission(PermissionCodes.PROJECT_MEMBER_MANAGE)
    @RequirePermission(PermissionCodes.ROLE_UNBIND)
    public ProjectMemberView removeProjectMember(
            @PathVariable String key,
            @PathVariable String username,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return mapper.toResponse(projectOperations.removeProjectMember(key.trim(), username.trim(), principal));
    }
}
