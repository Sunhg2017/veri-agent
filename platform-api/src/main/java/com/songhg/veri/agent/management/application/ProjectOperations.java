package com.songhg.veri.agent.management.application;

import com.songhg.veri.agent.auth.application.AuthUserPrincipal;
import com.songhg.veri.agent.common.api.PageQuery;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.management.application.CreateProjectRequest;
import com.songhg.veri.agent.management.application.ProjectMemberRequest;
import com.songhg.veri.agent.management.application.UpdateProjectRequest;
import com.songhg.veri.agent.management.application.ProjectMemberView;
import com.songhg.veri.agent.management.application.ProjectView;

public interface ProjectOperations {

    PageResponse<ProjectView> projects(PageQuery pageQuery, AuthUserPrincipal actor);

    ProjectView project(String key);

    ProjectView createProject(CreateProjectRequest request, AuthUserPrincipal actor);

    ProjectView updateProject(String key, UpdateProjectRequest request, AuthUserPrincipal actor);

    ProjectView changeProjectStatus(String key, String status, AuthUserPrincipal actor);

    PageResponse<ProjectMemberView> projectMembers(String projectKey, PageQuery pageQuery);

    ProjectMemberView addProjectMember(String projectKey, ProjectMemberRequest request, AuthUserPrincipal actor);

    ProjectMemberView removeProjectMember(String projectKey, String username, AuthUserPrincipal actor);
}
