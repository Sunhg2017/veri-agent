package com.songhg.veri.agent.management.application.port;

import com.songhg.veri.agent.auth.application.AuthUserPrincipal;
import com.songhg.veri.agent.common.api.PageQuery;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.management.application.command.CreateProjectRequest;
import com.songhg.veri.agent.management.application.command.ProjectMemberRequest;
import com.songhg.veri.agent.management.application.command.UpdateProjectRequest;
import com.songhg.veri.agent.management.application.view.ProjectMemberView;
import com.songhg.veri.agent.management.application.view.ProjectView;

/**
 * Project management use cases. Projects anchor resource-scoped permissions, so member and status
 * operations must stay auditable and consistent across local and database implementations.
 */
public interface ProjectOperations {

    /**
     * Lists projects visible to the actor.
     */
    PageResponse<ProjectView> projects(PageQuery pageQuery, AuthUserPrincipal actor);

    /**
     * Returns one project by key.
     */
    ProjectView project(String key);

    /**
     * Creates a project and initializes its management metadata.
     */
    ProjectView createProject(CreateProjectRequest request, AuthUserPrincipal actor);

    /**
     * Updates project metadata without changing member bindings.
     */
    ProjectView updateProject(String key, UpdateProjectRequest request, AuthUserPrincipal actor);

    /**
     * Applies the requested project lifecycle status after status-specific authorization.
     */
    ProjectView changeProjectStatus(String key, String status, AuthUserPrincipal actor);

    /**
     * Lists project members for role-scope review.
     */
    PageResponse<ProjectMemberView> projectMembers(String projectKey, PageQuery pageQuery);

    /**
     * Adds a project member and binds the requested project-scoped role.
     */
    ProjectMemberView addProjectMember(String projectKey, ProjectMemberRequest request, AuthUserPrincipal actor);

    /**
     * Removes a project member binding while keeping the user account intact.
     */
    ProjectMemberView removeProjectMember(String projectKey, String username, AuthUserPrincipal actor);
}
