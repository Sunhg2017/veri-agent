package com.songhg.veri.agent.management.infrastructure;

import com.songhg.veri.agent.auth.application.AuthUserPrincipal;
import com.songhg.veri.agent.common.api.PageQuery;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.audit.AuditLogWriter;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.management.api.request.CreateProjectRequest;
import com.songhg.veri.agent.management.api.request.ProjectMemberRequest;
import com.songhg.veri.agent.management.api.request.UpdateProjectRequest;
import com.songhg.veri.agent.management.api.response.ProjectMemberView;
import com.songhg.veri.agent.management.api.response.ProjectView;
import com.songhg.veri.agent.management.api.response.UserView;
import java.util.ArrayList;
import java.util.List;

final class InMemoryManagementProjectService {

    private final List<ProjectView> projects = new ArrayList<>();
    private final List<ProjectMemberView> projectMembers = new ArrayList<>();
    private final InMemoryManagementUserService userService;
    private final AuditLogWriter auditLogWriter;

    InMemoryManagementProjectService(InMemoryManagementUserService userService, AuditLogWriter auditLogWriter) {
        this.userService = userService;
        this.auditLogWriter = auditLogWriter;
        projects.addAll(List.of(
                new ProjectView("Checkout Regression", "自动化平台组", "何序", 4, "进行中"),
                new ProjectView("Mobile Smoke", "端体验组", "陈乔", 2, "规划中"),
                new ProjectView("API Stability", "质量工程中心", "平台组", 6, "进行中")
        ));
    }

    PageResponse<ProjectView> projects(PageQuery pageQuery, AuthUserPrincipal actor) {
        return page(projects, pageQuery);
    }

    ProjectView project(String key) {
        return requireProject(key);
    }

    ProjectView createProject(CreateProjectRequest request, AuthUserPrincipal actor) {
        String name = request.name().trim();
        ProjectView view = new ProjectView(name, "质量工程中心", actor.displayName(), 0, "规划中");
        projects.add(0, view);
        audit(actor, "创建项目", name);
        return view;
    }

    ProjectView updateProject(String key, UpdateProjectRequest request, AuthUserPrincipal actor) {
        ProjectView current = requireProject(key);
        if ("已归档".equals(current.status()) || "已停用".equals(current.status())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "当前项目状态不允许编辑");
        }
        ProjectView updated = replaceProject(
                current.name(),
                new ProjectView(
                        trimOrDefault(request.name(), current.name()),
                        current.department(),
                        current.owner(),
                        current.apps(),
                        current.status()
                )
        );
        audit(actor, "更新项目", updated.name());
        return updated;
    }

    ProjectView changeProjectStatus(String key, String status, AuthUserPrincipal actor) {
        ProjectView current = requireProject(key);
        String nextStatusCode = normalizeProjectStatus(status);
        ensureProjectStatusTransition(actor, current.name(), projectStatusCode(current.status()), nextStatusCode);
        String nextStatus = switch (nextStatusCode) {
            case "PREPARING" -> "规划中";
            case "ACTIVE" -> "进行中";
            case "ARCHIVED" -> "已归档";
            case "DISABLED" -> "已停用";
            default -> throw new BusinessException(ErrorCode.VALIDATION_ERROR, "项目状态不支持");
        };
        ProjectView updated = replaceProject(
                current.name(),
                new ProjectView(current.name(), current.department(), current.owner(), current.apps(), nextStatus)
        );
        audit(actor, projectStatusAction(nextStatusCode), updated.name());
        return updated;
    }

    PageResponse<ProjectMemberView> projectMembers(String projectKey, PageQuery pageQuery) {
        requireProject(projectKey);
        return page(projectMembers, pageQuery);
    }

    ProjectMemberView addProjectMember(String projectKey, ProjectMemberRequest request, AuthUserPrincipal actor) {
        requireProject(projectKey);
        UserView user = userService.requireUser(request.username().trim());
        projectMembers.removeIf(member -> member.username().equals(user.username()));
        ProjectMemberView view = new ProjectMemberView(
                user.username(),
                user.username(),
                request.roleCode().trim(),
                memberTypeForRole(request.roleCode().trim()),
                "启用"
        );
        projectMembers.add(0, view);
        audit(actor, "添加项目成员", projectKey + ":" + user.username());
        return view;
    }

    ProjectMemberView removeProjectMember(String projectKey, String username, AuthUserPrincipal actor) {
        requireProject(projectKey);
        ProjectMemberView current = projectMembers.stream()
                .filter(member -> member.username().equals(username))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "项目成员不存在"));
        projectMembers.removeIf(member -> member.username().equals(username));
        ProjectMemberView removed = new ProjectMemberView(
                current.username(),
                current.displayName(),
                current.role(),
                current.memberType(),
                "已移除"
        );
        audit(actor, "移除项目成员", projectKey + ":" + username);
        return removed;
    }

    private ProjectView requireProject(String key) {
        return projects.stream()
                .filter(project -> project.name().equals(key))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "项目不存在"));
    }

    private ProjectView replaceProject(String key, ProjectView updated) {
        for (int index = 0; index < projects.size(); index++) {
            ProjectView current = projects.get(index);
            if (current.name().equals(key)) {
                projects.set(index, updated);
                return updated;
            }
        }
        throw new BusinessException(ErrorCode.NOT_FOUND, "项目不存在");
    }

    private String projectStatusAction(String status) {
        return switch (status) {
            case "ARCHIVED" -> "归档项目";
            case "DISABLED" -> "停用项目";
            case "ACTIVE", "PREPARING" -> "恢复项目";
            default -> "更新项目状态";
        };
    }

    private String normalizeProjectStatus(String status) {
        String normalized = status == null ? "" : status.trim().toUpperCase();
        if (!List.of("PREPARING", "ACTIVE", "ARCHIVED", "DISABLED").contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "项目状态不支持");
        }
        return normalized;
    }

    private String projectStatusCode(String status) {
        return switch (status) {
            case "规划中" -> "PREPARING";
            case "进行中" -> "ACTIVE";
            case "已归档" -> "ARCHIVED";
            case "已停用" -> "DISABLED";
            default -> "";
        };
    }

    private void ensureProjectStatusTransition(
            AuthUserPrincipal actor,
            String projectName,
            String currentStatus,
            String nextStatus
    ) {
        if (currentStatus.equals(nextStatus)) {
            return;
        }
        boolean allowed = switch (currentStatus) {
            case "PREPARING" -> List.of("ACTIVE", "DISABLED").contains(nextStatus);
            case "ACTIVE" -> List.of("ARCHIVED", "DISABLED").contains(nextStatus);
            case "ARCHIVED" -> List.of("ACTIVE", "DISABLED").contains(nextStatus);
            case "DISABLED" -> List.of("PREPARING", "ACTIVE").contains(nextStatus);
            default -> false;
        };
        if (!allowed) {
            auditDenied(actor, "项目状态拒绝", projectName, "项目状态流不允许: " + currentStatus + "->" + nextStatus);
            throw new BusinessException(ErrorCode.INVALID_STATE, "项目状态不允许从 " + currentStatus + " 流转到 " + nextStatus);
        }
    }

    private String memberTypeForRole(String roleCode) {
        return "ProjectOwner".equals(roleCode) ? "OWNER" : "MEMBER";
    }

    private String trimOrDefault(String value, String defaultValue) {
        if (value == null || value.trim().isBlank()) {
            return defaultValue;
        }
        return value.trim();
    }

    private <T> PageResponse<T> page(List<T> source, PageQuery pageQuery) {
        String keyword = pageQuery.search().toLowerCase();
        List<T> filtered = source.stream()
                .filter(item -> keyword.isBlank() || item.toString().toLowerCase().contains(keyword))
                .toList();
        int from = Math.min(pageQuery.offset(), filtered.size());
        int to = Math.min(from + pageQuery.size(), filtered.size());
        return PageResponse.of(filtered.subList(from, to), pageQuery.index(), pageQuery.size(), filtered.size());
    }

    private void audit(AuthUserPrincipal actor, String action, String target) {
        auditLogWriter.record(AuditLogWriter.success(
                actor, action, "management", target, target
        ));
    }

    private void auditDenied(AuthUserPrincipal actor, String action, String target, String reason) {
        auditLogWriter.record(AuditLogWriter.denied(
                actor, action, "management", target, target, reason
        ));
    }
}
