package com.songhg.veri.agent.management.infrastructure;

import com.songhg.veri.agent.auth.application.AuthUserPrincipal;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.common.trace.TraceContext;
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
import com.songhg.veri.agent.management.application.AuditLogQuery;
import com.songhg.veri.agent.management.application.ManagementWorkspaceService;
import java.sql.Types;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Profile("db")
@Service
public class PostgresManagementWorkspaceService implements ManagementWorkspaceService {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    public PostgresManagementWorkspaceService(
            NamedParameterJdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public PageResponse<DepartmentView> departments(int page, int pageSize, String search) {
        List<DepartmentView> items = jdbcTemplate.query("""
                select
                    d.name,
                    coalesce(parent.name, '总部') as parent_name,
                    coalesce((
                        select string_agg(u.display_name, ', ' order by u.display_name)
                        from base_department_manager manager
                        join iam_user u on u.id = manager.user_id
                            and u.deleted_at is null
                        where manager.dept_id = d.id
                          and manager.status = 'ENABLED'
                          and manager.deleted_at is null
                    ), '未设置') as lead,
                    (
                        select count(*)::int
                        from base_department_member member
                        where member.dept_id = d.id
                          and member.status = 'ENABLED'
                          and member.deleted_at is null
                    ) as members,
                    case d.status when 'ENABLED' then '同步正常' else '已停用' end as status_name
                from base_department d
                left join base_department parent on parent.id = d.parent_id
                    and parent.deleted_at is null
                where d.deleted_at is null
                  and (:search = '' or d.name ilike :searchPattern)
                order by d.created_at desc
                limit :limit offset :offset
                """,
                pageParams(page, pageSize, search),
                (rs, rowNum) -> new DepartmentView(
                        rs.getString("name"),
                        rs.getString("parent_name"),
                        rs.getString("lead"),
                        rs.getInt("members"),
                        rs.getString("status_name")
                )
        );
        long total = count("""
                select count(*)
                from base_department d
                where d.deleted_at is null
                  and (:search = '' or d.name ilike :searchPattern)
                """, search);
        return PageResponse.of(items, page, pageSize, total);
    }

    @Override
    @Transactional
    public DepartmentView createDepartment(String name, AuthUserPrincipal actor) {
        UUID deptId = UUID.randomUUID();
        String code = nextCode("dept");
        try {
            jdbcTemplate.update("""
                    insert into base_department (
                        id,
                        code,
                        name,
                        path,
                        level,
                        status,
                        created_by,
                        updated_by
                    )
                    values (
                        :deptId,
                        :code,
                        :name,
                        :path,
                        1,
                        'ENABLED',
                        :actorId,
                        :actorId
                    )
                    """,
                    params(actor)
                            .addValue("deptId", deptId)
                            .addValue("code", code)
                            .addValue("name", name)
                            .addValue("path", "/" + deptId)
            );
            insertDepartmentManager(deptId, actor);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.CONFLICT, "部门名称或编码已存在");
        }
        audit(actor, "创建部门", "department", deptId.toString(), name);
        return new DepartmentView(name, "总部", actor.displayName(), 0, "同步正常");
    }

    @Override
    public DepartmentView department(String key) {
        return departmentByKey(key);
    }

    @Override
    @Transactional
    public DepartmentView updateDepartment(String key, UpdateDepartmentRequest request, AuthUserPrincipal actor) {
        DepartmentRef department = resolveDepartmentStrict(key);
        ensureEnabled(department.status(), "当前部门状态不允许编辑");
        try {
            jdbcTemplate.update("""
                    update base_department
                    set name = coalesce(:name, name),
                        updated_by = :actorId,
                        updated_at = now(),
                        version = version + 1
                    where id = :deptId
                      and deleted_at is null
                    """,
                    params(actor)
                            .addValue("deptId", department.id())
                            .addValue("name", blankToNull(request.name()))
            );
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.CONFLICT, "部门名称已存在");
        }
        DepartmentView updated = departmentByKey(department.id().toString());
        audit(actor, "更新部门", "department", department.id().toString(), updated.name());
        return updated;
    }

    @Override
    @Transactional
    public DepartmentView changeDepartmentStatus(String key, String status, AuthUserPrincipal actor) {
        DepartmentRef department = resolveDepartmentStrict(key);
        String nextStatus = normalizeEnabledStatus(status, "部门状态不支持");
        jdbcTemplate.update("""
                update base_department
                set status = :status,
                    updated_by = :actorId,
                    updated_at = now(),
                    version = version + 1
                where id = :deptId
                  and deleted_at is null
                """,
                params(actor)
                        .addValue("deptId", department.id())
                        .addValue("status", nextStatus)
        );
        DepartmentView updated = departmentByKey(department.id().toString());
        audit(actor, "ENABLED".equals(nextStatus) ? "启用部门" : "停用部门", "department", department.id().toString(), updated.name());
        return updated;
    }

    @Override
    public PageResponse<UserView> users(int page, int pageSize, String search) {
        List<UserView> items = jdbcTemplate.query("""
                select
                    u.username,
                    u.display_name,
                    coalesce(u.email, '') as email,
                    coalesce(string_agg(distinct b.role_code, ' / ' order by b.role_code), '未分配') as role_names,
                    coalesce(primary_dept.name, '未分配') as department_name,
                    case u.status
                        when 'ENABLED' then '启用'
                        when 'PENDING_ACTIVATION' then '待激活'
                        when 'LOCKED' then '已锁定'
                        else '已停用'
                    end as status_name,
                    coalesce(to_char(u.last_login_at, 'YYYY-MM-DD HH24:MI'), '尚未登录') as last_seen
                from iam_user u
                left join rbac_role_binding b on b.subject_type = 'USER'
                    and b.subject_id = u.id
                    and b.status = 'ENABLED'
                    and b.deleted_at is null
                    and (b.expires_at is null or b.expires_at > now())
                left join base_department_member primary_member on primary_member.user_id = u.id
                    and primary_member.is_primary = true
                    and primary_member.status = 'ENABLED'
                    and primary_member.deleted_at is null
                left join base_department primary_dept on primary_dept.id = primary_member.dept_id
                    and primary_dept.deleted_at is null
                where u.deleted_at is null
                  and (:search = '' or u.username ilike :searchPattern or u.display_name ilike :searchPattern)
                group by u.id, primary_dept.name
                order by u.created_at desc
                limit :limit offset :offset
                """,
                pageParams(page, pageSize, search),
                (rs, rowNum) -> new UserView(
                        rs.getString("username"),
                        rs.getString("display_name"),
                        rs.getString("email"),
                        rs.getString("role_names"),
                        rs.getString("department_name"),
                        rs.getString("status_name"),
                        rs.getString("last_seen")
                )
        );
        long total = count("""
                select count(*)
                from iam_user u
                where u.deleted_at is null
                  and (:search = '' or u.username ilike :searchPattern or u.display_name ilike :searchPattern)
                """, search);
        return PageResponse.of(items, page, pageSize, total);
    }

    @Override
    public UserView user(String username) {
        return userByUsername(username);
    }

    @Override
    @Transactional
    public UserView createUser(String username, AuthUserPrincipal actor) {
        UUID userId = UUID.randomUUID();
        try {
            jdbcTemplate.update("""
                    insert into iam_user (
                        id,
                        username,
                        display_name,
                        status,
                        created_by,
                        updated_by
                    )
                    values (
                        :userId,
                        :username,
                        :username,
                        'PENDING_ACTIVATION',
                        :actorId,
                        :actorId
                    )
                    """,
                    params(actor)
                            .addValue("userId", userId)
                            .addValue("username", username)
            );
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.CONFLICT, "用户账号已存在");
        }
        bindRoleIfPresent(userId, "Tester", "PLATFORM", null, actor);
        audit(actor, "邀请用户", "user", userId.toString(), username);
        return new UserView(username, username, "", "Tester", "未分配", "待激活", "尚未登录");
    }

    @Override
    @Transactional
    public UserView updateUser(String username, UpdateUserRequest request, AuthUserPrincipal actor) {
        try {
            int rows = jdbcTemplate.update("""
                    update iam_user
                    set display_name = coalesce(:displayName, display_name),
                        email = coalesce(:email, email),
                        updated_by = :actorId,
                        updated_at = now(),
                        version = version + 1
                    where username = :username
                      and deleted_at is null
                    """,
                    params(actor)
                            .addValue("username", username)
                            .addValue("displayName", blankToNull(request.displayName()))
                            .addValue("email", blankToNull(request.email()))
            );
            ensureUserUpdated(rows);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.CONFLICT, "用户邮箱已存在");
        }
        UserView updated = userByUsername(username);
        audit(actor, "更新用户", "user", username, updated.username());
        return updated;
    }

    @Override
    @Transactional
    public UserView enableUser(String username, AuthUserPrincipal actor) {
        int rows = jdbcTemplate.update("""
                update iam_user
                set status = 'ENABLED',
                    updated_by = :actorId,
                    updated_at = now()
                where username = :username
                  and deleted_at is null
                """,
                params(actor).addValue("username", username)
        );
        ensureUserUpdated(rows);
        audit(actor, "启用用户", "user", username, username);
        return userByUsername(username);
    }

    @Override
    @Transactional
    public UserView disableUser(String username, AuthUserPrincipal actor) {
        if (actor.username().equals(username)) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "不能停用当前登录账号");
        }
        int rows = jdbcTemplate.update("""
                update iam_user
                set status = 'DISABLED',
                    auth_version = auth_version + 1,
                    updated_by = :actorId,
                    updated_at = now()
                where username = :username
                  and deleted_at is null
                """,
                params(actor).addValue("username", username)
        );
        ensureUserUpdated(rows);
        audit(actor, "停用用户", "user", username, username);
        return userByUsername(username);
    }

    @Override
    @Transactional
    public UserView lockUser(String username, AuthUserPrincipal actor) {
        if (actor.username().equals(username)) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "不能锁定当前登录账号");
        }
        int rows = jdbcTemplate.update("""
                update iam_user
                set status = 'LOCKED',
                    auth_version = auth_version + 1,
                    updated_by = :actorId,
                    updated_at = now()
                where username = :username
                  and deleted_at is null
                """,
                params(actor).addValue("username", username)
        );
        ensureUserUpdated(rows);
        audit(actor, "锁定用户", "user", username, username);
        return userByUsername(username);
    }

    @Override
    @Transactional
    public UserView unlockUser(String username, AuthUserPrincipal actor) {
        int rows = jdbcTemplate.update("""
                update iam_user
                set status = 'ENABLED',
                    auth_version = auth_version + 1,
                    updated_by = :actorId,
                    updated_at = now()
                where username = :username
                  and deleted_at is null
                """,
                params(actor).addValue("username", username)
        );
        ensureUserUpdated(rows);
        audit(actor, "解锁用户", "user", username, username);
        return userByUsername(username);
    }

    @Override
    @Transactional
    public UserView resetUserPassword(String username, String newPassword, AuthUserPrincipal actor) {
        int rows = jdbcTemplate.update("""
                update iam_user
                set password_hash = :passwordHash,
                    must_change_password = true,
                    status = 'ENABLED',
                    auth_version = auth_version + 1,
                    updated_by = :actorId,
                    updated_at = now()
                where username = :username
                  and deleted_at is null
                """,
                params(actor)
                        .addValue("username", username)
                        .addValue("passwordHash", passwordEncoder.encode(newPassword))
        );
        ensureUserUpdated(rows);
        audit(actor, "重置密码", "user", username, username);
        return userByUsername(username);
    }

    @Override
    public PageResponse<RoleView> roles(int page, int pageSize, String search) {
        List<RoleView> items = jdbcTemplate.query("""
                select
                    code,
                    name,
                    scope_type,
                    case status when 'ENABLED' then '启用' else '已停用' end as status_name,
                    coalesce(description, '') as description
                from rbac_role
                where deleted_at is null
                  and (:search = '' or code ilike :searchPattern or name ilike :searchPattern)
                order by is_system desc, created_at asc
                limit :limit offset :offset
                """,
                pageParams(page, pageSize, search),
                (rs, rowNum) -> new RoleView(
                        rs.getString("code"),
                        rs.getString("name"),
                        rs.getString("scope_type"),
                        rs.getString("status_name"),
                        rs.getString("description")
                )
        );
        long total = count("""
                select count(*)
                from rbac_role
                where deleted_at is null
                  and (:search = '' or code ilike :searchPattern or name ilike :searchPattern)
                """, search);
        return PageResponse.of(items, page, pageSize, total);
    }

    @Override
    @Transactional
    public UserView assignUserRole(String username, String roleCode, AuthUserPrincipal actor) {
        UUID userId = requireUserId(username);
        UUID roleId = requireRoleId(roleCode);
        jdbcTemplate.update("""
                insert into rbac_role_binding (
                    subject_type,
                    subject_id,
                    role_id,
                    role_code,
                    scope_type,
                    scope_id,
                    status,
                    created_by,
                    updated_by
                )
                values (
                    'USER',
                    :userId,
                    :roleId,
                    :roleCode,
                    'PLATFORM',
                    null,
                    'ENABLED',
                    :actorId,
                    :actorId
                )
                on conflict do nothing
                """,
                params(actor)
                        .addValue("userId", userId)
                        .addValue("roleId", roleId)
                        .addValue("roleCode", roleCode)
        );
        bumpUserAuthVersion(userId, actor);
        audit(actor, "分配角色", "rbac_role_binding", userId + ":" + roleCode, username + ":" + roleCode);
        return userByUsername(username);
    }

    @Override
    @Transactional
    public UserView unassignUserRole(String username, String roleCode, AuthUserPrincipal actor) {
        UUID userId = requireUserId(username);
        int rows = jdbcTemplate.update("""
                update rbac_role_binding
                set deleted_by = :actorId,
                    deleted_at = now(),
                    updated_by = :actorId,
                    updated_at = now()
                where subject_type = 'USER'
                  and subject_id = :userId
                  and role_code = :roleCode
                  and deleted_at is null
                """,
                params(actor)
                        .addValue("userId", userId)
                        .addValue("roleCode", roleCode)
        );
        if (rows > 0) {
            bumpUserAuthVersion(userId, actor);
        }
        audit(actor, "解绑角色", "rbac_role_binding", userId + ":" + roleCode, username + ":" + roleCode);
        return userByUsername(username);
    }

    @Override
    public PageResponse<ProjectView> projects(int page, int pageSize, String search, AuthUserPrincipal actor) {
        MapSqlParameterSource scopeParams = scopedPageParams(page, pageSize, search, actor);
        List<ProjectView> items = jdbcTemplate.query("""
                select
                    p.name,
                    coalesce(primary_dept.name, '未分配') as department_name,
                    coalesce((
                        select string_agg(u.display_name, ', ' order by u.display_name)
                        from base_project_member member
                        join iam_user u on u.id = member.user_id
                            and u.deleted_at is null
                        where member.project_id = p.id
                          and member.member_type = 'OWNER'
                          and member.status = 'ENABLED'
                          and member.deleted_at is null
                    ), '未设置') as owner_name,
                    (
                        select count(*)::int
                        from base_application app
                        where app.project_id = p.id
                          and app.deleted_at is null
                    ) as apps,
                    case p.status
                        when 'ACTIVE' then '进行中'
                        when 'PREPARING' then '规划中'
                        when 'ARCHIVED' then '已归档'
                        else '已停用'
                    end as status_name
                from base_project p
                left join base_project_department pd on pd.project_id = p.id
                    and pd.is_primary = true
                    and pd.status = 'ENABLED'
                    and pd.deleted_at is null
                left join base_department primary_dept on primary_dept.id = pd.dept_id
                    and primary_dept.deleted_at is null
                where p.deleted_at is null
                  and (:search = '' or p.name ilike :searchPattern or p.code ilike :searchPattern)
                  and (
                    :platformScope = true
                    or exists (
                        select 1
                        from rbac_role_binding b
                        where b.subject_type = 'USER'
                          and b.subject_id = :actorId
                          and b.status = 'ENABLED'
                          and b.deleted_at is null
                          and (b.expires_at is null or b.expires_at > now())
                          and (
                            (b.scope_type = 'PROJECT' and b.scope_id = p.id)
                            or (b.scope_type = 'DEPARTMENT' and exists (
                                select 1
                                from base_project_department scoped_pd
                                where scoped_pd.project_id = p.id
                                  and scoped_pd.dept_id = b.scope_id
                                  and scoped_pd.status = 'ENABLED'
                                  and scoped_pd.deleted_at is null
                            ))
                          )
                    )
                  )
                order by p.created_at desc
                limit :limit offset :offset
                """,
                scopeParams,
                (rs, rowNum) -> new ProjectView(
                        rs.getString("name"),
                        rs.getString("department_name"),
                        rs.getString("owner_name"),
                        rs.getInt("apps"),
                        rs.getString("status_name")
                )
        );
        long total = count("""
                select count(*)
                from base_project p
                where p.deleted_at is null
                  and (:search = '' or p.name ilike :searchPattern or p.code ilike :searchPattern)
                  and (
                    :platformScope = true
                    or exists (
                        select 1
                        from rbac_role_binding b
                        where b.subject_type = 'USER'
                          and b.subject_id = :actorId
                          and b.status = 'ENABLED'
                          and b.deleted_at is null
                          and (b.expires_at is null or b.expires_at > now())
                          and (
                            (b.scope_type = 'PROJECT' and b.scope_id = p.id)
                            or (b.scope_type = 'DEPARTMENT' and exists (
                                select 1
                                from base_project_department scoped_pd
                                where scoped_pd.project_id = p.id
                                  and scoped_pd.dept_id = b.scope_id
                                  and scoped_pd.status = 'ENABLED'
                                  and scoped_pd.deleted_at is null
                            ))
                          )
                    )
                  )
                """, scopeParams);
        return PageResponse.of(items, page, pageSize, total);
    }

    @Override
    public ProjectView project(String key) {
        return projectByKey(key);
    }

    @Override
    @Transactional
    public ProjectView createProject(CreateProjectRequest request, AuthUserPrincipal actor) {
        UUID projectId = UUID.randomUUID();
        String name = request.name().trim();
        String code = normalizedOrGeneratedCode(request.code(), "prj");
        String sensitivityLevel = normalizedOrDefault(request.sensitivityLevel(), "INTERNAL");
        boolean allowPublicModel = Boolean.TRUE.equals(request.allowPublicModel());
        try {
            jdbcTemplate.update("""
                    insert into base_project (
                        id,
                        code,
                        name,
                        status,
                        sensitivity_level,
                        allow_public_model,
                        created_by,
                        updated_by
                    )
                    values (
                        :projectId,
                        :code,
                        :name,
                        'PREPARING',
                        :sensitivityLevel,
                        :allowPublicModel,
                        :actorId,
                        :actorId
                    )
                    """,
                    params(actor)
                            .addValue("projectId", projectId)
                            .addValue("code", code)
                            .addValue("name", name)
                            .addValue("sensitivityLevel", sensitivityLevel)
                            .addValue("allowPublicModel", allowPublicModel)
            );
            insertProjectOwner(projectId, actor);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.CONFLICT, "项目编码已存在");
        }
        audit(actor, "创建项目", "project", projectId.toString(), name);
        return new ProjectView(name, "未分配", actor.displayName(), 0, "规划中");
    }

    @Override
    @Transactional
    public ProjectView updateProject(String key, UpdateProjectRequest request, AuthUserPrincipal actor) {
        ProjectRef project = resolveProjectStrict(key);
        ensureProjectEditable(project.status());
        try {
            jdbcTemplate.update("""
                    update base_project
                    set name = coalesce(:name, name),
                        sensitivity_level = coalesce(:sensitivityLevel, sensitivity_level),
                        allow_public_model = coalesce(:allowPublicModel, allow_public_model),
                        updated_by = :actorId,
                        updated_at = now(),
                        version = version + 1
                    where id = :projectId
                      and deleted_at is null
                    """,
                    params(actor)
                            .addValue("projectId", project.id())
                            .addValue("name", blankToNull(request.name()))
                            .addValue("sensitivityLevel", blankToNull(request.sensitivityLevel()))
                            .addValue("allowPublicModel", request.allowPublicModel())
            );
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.CONFLICT, "项目编码或名称已存在");
        }
        ProjectView updated = projectByKey(project.id().toString());
        audit(actor, "更新项目", "project", project.id().toString(), updated.name());
        return updated;
    }

    @Override
    @Transactional
    public ProjectView changeProjectStatus(String key, String status, AuthUserPrincipal actor) {
        ProjectRef project = resolveProjectStrict(key);
        String nextStatus = normalizeProjectStatus(status);
        ensureProjectStatusTransition(project.status(), nextStatus);
        jdbcTemplate.update("""
                update base_project
                set status = :status,
                    updated_by = :actorId,
                    updated_at = now(),
                    version = version + 1
                where id = :projectId
                  and deleted_at is null
                """,
                params(actor)
                        .addValue("projectId", project.id())
                        .addValue("status", nextStatus)
        );
        ProjectView updated = projectByKey(project.id().toString());
        audit(actor, projectStatusAction(nextStatus), "project", project.id().toString(), updated.name());
        return updated;
    }

    @Override
    public PageResponse<ProjectMemberView> projectMembers(String projectKey, int page, int pageSize, String search) {
        ProjectRef project = resolveProjectStrict(projectKey);
        List<ProjectMemberView> items = jdbcTemplate.query("""
                select
                    u.username,
                    u.display_name,
                    coalesce(string_agg(distinct b.role_code, ' / ' order by b.role_code), '-') as role_names,
                    member.member_type,
                    case member.status when 'ENABLED' then '启用' else '已停用' end as status_name
                from base_project_member member
                join iam_user u on u.id = member.user_id
                    and u.deleted_at is null
                left join rbac_role_binding b on b.subject_type = 'USER'
                    and b.subject_id = u.id
                    and b.scope_type = 'PROJECT'
                    and b.scope_id = member.project_id
                    and b.status = 'ENABLED'
                    and b.deleted_at is null
                    and (b.expires_at is null or b.expires_at > now())
                where member.project_id = :projectId
                  and member.deleted_at is null
                  and (:search = '' or u.username ilike :searchPattern or u.display_name ilike :searchPattern or b.role_code ilike :searchPattern)
                group by u.id, member.member_type, member.status, member.created_at
                order by member.created_at desc
                limit :limit offset :offset
                """,
                pageParams(page, pageSize, search)
                        .addValue("projectId", project.id()),
                (rs, rowNum) -> new ProjectMemberView(
                        rs.getString("username"),
                        rs.getString("display_name"),
                        rs.getString("role_names"),
                        rs.getString("member_type"),
                        rs.getString("status_name")
                )
        );
        long total = count("""
                select count(*)
                from base_project_member member
                join iam_user u on u.id = member.user_id
                    and u.deleted_at is null
                where member.project_id = :projectId
                  and member.deleted_at is null
                  and (:search = '' or u.username ilike :searchPattern or u.display_name ilike :searchPattern)
                """,
                pageParams(page, pageSize, search)
                        .addValue("projectId", project.id()));
        return PageResponse.of(items, page, pageSize, total);
    }

    @Override
    @Transactional
    public ProjectMemberView addProjectMember(String projectKey, ProjectMemberRequest request, AuthUserPrincipal actor) {
        ProjectRef project = resolveProjectStrict(projectKey);
        ensureProjectEditable(project.status());
        String username = request.username().trim();
        String roleCode = request.roleCode().trim();
        UUID userId = requireUserId(username);
        UUID roleId = requireRoleId(roleCode);
        String memberType = memberTypeForRole(roleCode);
        jdbcTemplate.update("""
                insert into base_project_member (
                    project_id,
                    user_id,
                    member_type,
                    status,
                    created_by,
                    updated_by
                )
                values (
                    :projectId,
                    :userId,
                    :memberType,
                    'ENABLED',
                    :actorId,
                    :actorId
                )
                on conflict (project_id, user_id) where deleted_at is null
                do update set
                    member_type = excluded.member_type,
                    status = 'ENABLED',
                    updated_by = :actorId,
                    updated_at = now(),
                    version = base_project_member.version + 1
                """,
                params(actor)
                        .addValue("projectId", project.id())
                        .addValue("userId", userId)
                        .addValue("memberType", memberType)
        );
        bindProjectRole(userId, roleId, roleCode, project.id(), actor);
        bumpUserAuthVersion(userId, actor);
        ProjectMemberView view = projectMemberByUsername(project.id(), username);
        audit(actor, "添加项目成员", "project_member", project.id() + ":" + userId, project.name() + ":" + username);
        return view;
    }

    @Override
    @Transactional
    public ProjectMemberView removeProjectMember(String projectKey, String username, AuthUserPrincipal actor) {
        ProjectRef project = resolveProjectStrict(projectKey);
        UUID userId = requireUserId(username);
        ProjectMemberView current = projectMemberByUsername(project.id(), username);
        int rows = jdbcTemplate.update("""
                update base_project_member
                set status = 'DISABLED',
                    deleted_by = :actorId,
                    deleted_at = now(),
                    updated_by = :actorId,
                    updated_at = now(),
                    version = version + 1
                where project_id = :projectId
                  and user_id = :userId
                  and deleted_at is null
                """,
                params(actor)
                        .addValue("projectId", project.id())
                        .addValue("userId", userId)
        );
        if (rows == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "项目成员不存在");
        }
        jdbcTemplate.update("""
                update rbac_role_binding
                set status = 'DISABLED',
                    deleted_by = :actorId,
                    deleted_at = now(),
                    updated_by = :actorId,
                    updated_at = now(),
                    version = version + 1
                where subject_type = 'USER'
                  and subject_id = :userId
                  and scope_type = 'PROJECT'
                  and scope_id = :projectId
                  and deleted_at is null
                """,
                params(actor)
                        .addValue("projectId", project.id())
                        .addValue("userId", userId)
        );
        bumpUserAuthVersion(userId, actor);
        audit(actor, "移除项目成员", "project_member", project.id() + ":" + userId, project.name() + ":" + username);
        return new ProjectMemberView(current.username(), current.displayName(), current.role(), current.memberType(), "已移除");
    }

    @Override
    public PageResponse<ApplicationView> applications(int page, int pageSize, String search, AuthUserPrincipal actor) {
        MapSqlParameterSource scopeParams = scopedPageParams(page, pageSize, search, actor);
        List<ApplicationView> items = jdbcTemplate.query("""
                select
                    app.name,
                    app.app_type,
                    coalesce((
                        select string_agg(u.display_name, ', ' order by u.display_name)
                        from rbac_role_binding b
                        join iam_user u on u.id = b.subject_id
                            and u.deleted_at is null
                        where b.subject_type = 'USER'
                          and b.scope_type = 'APPLICATION'
                          and b.scope_id = app.id
                          and b.role_code = 'AppOwner'
                          and b.status = 'ENABLED'
                          and b.deleted_at is null
                          and (b.expires_at is null or b.expires_at > now())
                    ), p.name) as owner_name,
                    'v' || app.version as version_name,
                    case app.status when 'ENABLED' then '已接入' else '已停用' end as status_name
                from base_application app
                join base_project p on p.id = app.project_id
                    and p.deleted_at is null
                where app.deleted_at is null
                  and (:search = '' or app.name ilike :searchPattern or app.code ilike :searchPattern)
                  and (
                    :platformScope = true
                    or exists (
                        select 1
                        from rbac_role_binding b
                        where b.subject_type = 'USER'
                          and b.subject_id = :actorId
                          and b.status = 'ENABLED'
                          and b.deleted_at is null
                          and (b.expires_at is null or b.expires_at > now())
                          and (
                            (b.scope_type = 'APPLICATION' and b.scope_id = app.id)
                            or (b.scope_type = 'PROJECT' and b.scope_id = app.project_id)
                          )
                    )
                  )
                order by app.created_at desc
                limit :limit offset :offset
                """,
                scopeParams,
                (rs, rowNum) -> new ApplicationView(
                        rs.getString("name"),
                        rs.getString("app_type"),
                        rs.getString("owner_name"),
                        rs.getString("version_name"),
                        rs.getString("status_name")
                )
        );
        long total = count("""
                select count(*)
                from base_application app
                where app.deleted_at is null
                  and (:search = '' or app.name ilike :searchPattern or app.code ilike :searchPattern)
                  and (
                    :platformScope = true
                    or exists (
                        select 1
                        from rbac_role_binding b
                        where b.subject_type = 'USER'
                          and b.subject_id = :actorId
                          and b.status = 'ENABLED'
                          and b.deleted_at is null
                          and (b.expires_at is null or b.expires_at > now())
                          and (
                            (b.scope_type = 'APPLICATION' and b.scope_id = app.id)
                            or (b.scope_type = 'PROJECT' and b.scope_id = app.project_id)
                          )
                    )
                  )
                """, scopeParams);
        return PageResponse.of(items, page, pageSize, total);
    }

    @Override
    public ApplicationView application(String key) {
        return applicationByKey(key);
    }

    @Override
    @Transactional
    public ApplicationView createApplication(CreateApplicationRequest request, AuthUserPrincipal actor) {
        String name = request.name().trim();
        String appType = normalizedOrDefault(request.appType(), "Web");
        String code = normalizedOrGeneratedCode(request.code(), "app");
        String sensitivityLevel = normalizedOrDefault(request.sensitivityLevel(), "INTERNAL");
        boolean allowPublicModel = Boolean.TRUE.equals(request.allowPublicModel());
        ProjectRef project = resolveProject(request.project(), actor);
        ensureProjectEditable(project.status());
        UUID appId = UUID.randomUUID();
        try {
            jdbcTemplate.update("""
                    insert into base_application (
                        id,
                        project_id,
                        code,
                        name,
                        app_type,
                        default_web_url,
                        default_api_base_url,
                        sensitivity_level,
                        allow_public_model,
                        status,
                        created_by,
                        updated_by
                    )
                    values (
                        :appId,
                        :projectId,
                        :code,
                        :name,
                        :appType,
                        :defaultWebUrl,
                        :defaultApiBaseUrl,
                        :sensitivityLevel,
                        :allowPublicModel,
                        'ENABLED',
                        :actorId,
                        :actorId
                    )
                    """,
                    params(actor)
                            .addValue("appId", appId)
                            .addValue("projectId", project.id())
                            .addValue("code", code)
                            .addValue("name", name)
                            .addValue("appType", appType)
                            .addValue("defaultWebUrl", blankToNull(request.defaultWebUrl()))
                            .addValue("defaultApiBaseUrl", blankToNull(request.defaultApiBaseUrl()))
                            .addValue("sensitivityLevel", sensitivityLevel)
                            .addValue("allowPublicModel", allowPublicModel)
            );
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.CONFLICT, "应用编码已存在");
        }
        audit(actor, "登记应用", "application", appId.toString(), name);
        return new ApplicationView(name, appType, project.name(), "v0", "已接入");
    }

    @Override
    @Transactional
    public ApplicationView updateApplication(String key, UpdateApplicationRequest request, AuthUserPrincipal actor) {
        ApplicationRef application = resolveApplicationStrict(key);
        ensureEnabled(application.status(), "当前应用状态不允许编辑");
        try {
            jdbcTemplate.update("""
                    update base_application
                    set name = coalesce(:name, name),
                        app_type = coalesce(:appType, app_type),
                        default_web_url = coalesce(:defaultWebUrl, default_web_url),
                        default_api_base_url = coalesce(:defaultApiBaseUrl, default_api_base_url),
                        sensitivity_level = coalesce(:sensitivityLevel, sensitivity_level),
                        allow_public_model = coalesce(:allowPublicModel, allow_public_model),
                        updated_by = :actorId,
                        updated_at = now(),
                        version = version + 1
                    where id = :applicationId
                      and deleted_at is null
                    """,
                    params(actor)
                            .addValue("applicationId", application.id())
                            .addValue("name", blankToNull(request.name()))
                            .addValue("appType", blankToNull(request.appType()))
                            .addValue("defaultWebUrl", blankToNull(request.defaultWebUrl()))
                            .addValue("defaultApiBaseUrl", blankToNull(request.defaultApiBaseUrl()))
                            .addValue("sensitivityLevel", blankToNull(request.sensitivityLevel()))
                            .addValue("allowPublicModel", request.allowPublicModel())
            );
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.CONFLICT, "应用编码或名称已存在");
        }
        ApplicationView updated = applicationByKey(application.id().toString());
        audit(actor, "更新应用", "application", application.id().toString(), updated.name());
        return updated;
    }

    @Override
    @Transactional
    public ApplicationView changeApplicationStatus(String key, String status, AuthUserPrincipal actor) {
        ApplicationRef application = resolveApplicationStrict(key);
        String nextStatus = normalizeEnabledStatus(status, "应用状态不支持");
        jdbcTemplate.update("""
                update base_application
                set status = :status,
                    updated_by = :actorId,
                    updated_at = now(),
                    version = version + 1
                where id = :applicationId
                  and deleted_at is null
                """,
                params(actor)
                        .addValue("applicationId", application.id())
                        .addValue("status", nextStatus)
        );
        ApplicationView updated = applicationByKey(application.id().toString());
        audit(actor, "ENABLED".equals(nextStatus) ? "启用应用" : "停用应用", "application", application.id().toString(), updated.name());
        return updated;
    }

    @Override
    public PageResponse<ScopedUserRoleView> applicationOwners(String applicationKey, int page, int pageSize, String search) {
        ApplicationRef application = resolveApplicationStrict(applicationKey);
        return scopedUserRoles(application.id(), "APPLICATION", "AppOwner", page, pageSize, search);
    }

    @Override
    @Transactional
    public ScopedUserRoleView addApplicationOwner(String applicationKey, ScopedUserRoleRequest request, AuthUserPrincipal actor) {
        ApplicationRef application = resolveApplicationStrict(applicationKey);
        ensureEnabled(application.status(), "当前应用状态不允许维护负责人");
        String roleCode = request.roleCode().trim();
        if (!"AppOwner".equals(roleCode)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "应用负责人只能绑定 AppOwner 角色");
        }
        String username = request.username().trim();
        UUID userId = requireUserId(username);
        UUID roleId = requireRoleId(roleCode);
        bindScopedRole(userId, roleId, roleCode, "APPLICATION", application.id(), actor);
        bumpUserAuthVersion(userId, actor);
        ScopedUserRoleView view = scopedUserRoleByUsername(application.id(), "APPLICATION", "AppOwner", username, "应用负责人不存在");
        audit(actor, "添加应用负责人", "application_owner", application.id() + ":" + userId, application.name() + ":" + username);
        return view;
    }

    @Override
    @Transactional
    public ScopedUserRoleView removeApplicationOwner(String applicationKey, String username, AuthUserPrincipal actor) {
        ApplicationRef application = resolveApplicationStrict(applicationKey);
        ScopedUserRoleView current = scopedUserRoleByUsername(application.id(), "APPLICATION", "AppOwner", username, "应用负责人不存在");
        UUID userId = requireUserId(username);
        disableScopedRoles(userId, "APPLICATION", application.id(), "AppOwner", actor);
        bumpUserAuthVersion(userId, actor);
        audit(actor, "移除应用负责人", "application_owner", application.id() + ":" + userId, application.name() + ":" + username);
        return new ScopedUserRoleView(current.username(), current.displayName(), current.role(), current.scopeType(), "已移除");
    }

    @Override
    public PageResponse<EnvironmentView> environments(int page, int pageSize, String search, AuthUserPrincipal actor) {
        MapSqlParameterSource scopeParams = scopedPageParams(page, pageSize, search, actor);
        List<EnvironmentView> items = jdbcTemplate.query("""
                select
                    env.name,
                    p.name as cluster_name,
                    coalesce(env.api_base_url, env.web_url, env.code || '.local') as endpoint,
                    case env.status when 'ENABLED' then '可用' else '已停用' end as status_name
                from base_environment env
                join base_project p on p.id = env.project_id
                    and p.deleted_at is null
                where env.deleted_at is null
                  and (:search = '' or env.name ilike :searchPattern or env.code ilike :searchPattern)
                  and (
                    :platformScope = true
                    or exists (
                        select 1
                        from rbac_role_binding b
                        where b.subject_type = 'USER'
                          and b.subject_id = :actorId
                          and b.status = 'ENABLED'
                          and b.deleted_at is null
                          and (b.expires_at is null or b.expires_at > now())
                          and (
                            (b.scope_type = 'ENVIRONMENT' and b.scope_id = env.id)
                            or (b.scope_type = 'APPLICATION' and b.scope_id = env.app_id)
                            or (b.scope_type = 'PROJECT' and b.scope_id = env.project_id)
                          )
                    )
                  )
                order by env.created_at desc
                limit :limit offset :offset
                """,
                scopeParams,
                (rs, rowNum) -> new EnvironmentView(
                        rs.getString("name"),
                        rs.getString("cluster_name"),
                        rs.getString("endpoint"),
                        rs.getString("status_name")
                )
        );
        long total = count("""
                select count(*)
                from base_environment env
                where env.deleted_at is null
                  and (:search = '' or env.name ilike :searchPattern or env.code ilike :searchPattern)
                  and (
                    :platformScope = true
                    or exists (
                        select 1
                        from rbac_role_binding b
                        where b.subject_type = 'USER'
                          and b.subject_id = :actorId
                          and b.status = 'ENABLED'
                          and b.deleted_at is null
                          and (b.expires_at is null or b.expires_at > now())
                          and (
                            (b.scope_type = 'ENVIRONMENT' and b.scope_id = env.id)
                            or (b.scope_type = 'APPLICATION' and b.scope_id = env.app_id)
                            or (b.scope_type = 'PROJECT' and b.scope_id = env.project_id)
                          )
                    )
                  )
                """, scopeParams);
        return PageResponse.of(items, page, pageSize, total);
    }

    @Override
    public EnvironmentView environment(String key) {
        return environmentByKey(key);
    }

    @Override
    @Transactional
    public EnvironmentView createEnvironment(CreateEnvironmentRequest request, AuthUserPrincipal actor) {
        String name = request.name().trim();
        ProjectRef project = resolveProject(request.project(), actor);
        ensureProjectEditable(project.status());
        String scopeType = normalizedOrDefault(
                request.scopeType(),
                blankToNull(request.application()) == null ? "PROJECT" : "APPLICATION"
        );
        String envType = normalizedOrDefault(request.envType(), "TEST");
        UUID appId = resolveEnvironmentApplicationId(request, project, scopeType);
        UUID envId = UUID.randomUUID();
        String code = normalizedOrGeneratedCode(request.code(), "env");
        String endpoint = normalizedOrDefault(request.apiBaseUrl(), code + ".local");
        try {
            jdbcTemplate.update("""
                    insert into base_environment (
                        id,
                        project_id,
                        app_id,
                        scope_type,
                        code,
                        name,
                        env_type,
                        web_url,
                        api_base_url,
                        status,
                        created_by,
                        updated_by
                    )
                    values (
                        :envId,
                        :projectId,
                        :appId,
                        :scopeType,
                        :code,
                        :name,
                        :envType,
                        :webUrl,
                        :endpoint,
                        'ENABLED',
                        :actorId,
                        :actorId
                    )
                    """,
                    params(actor)
                            .addValue("envId", envId)
                            .addValue("projectId", project.id())
                            .addValue("appId", appId)
                            .addValue("scopeType", scopeType)
                            .addValue("code", code)
                            .addValue("name", name)
                            .addValue("envType", envType)
                            .addValue("webUrl", blankToNull(request.webUrl()))
                            .addValue("endpoint", endpoint)
            );
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.CONFLICT, "环境编码已存在");
        }
        audit(actor, "新增环境", "environment", envId.toString(), name);
        return new EnvironmentView(name, project.name(), endpoint, "可用");
    }

    @Override
    @Transactional
    public EnvironmentView updateEnvironment(String key, UpdateEnvironmentRequest request, AuthUserPrincipal actor) {
        EnvironmentRef environment = resolveEnvironmentStrict(key);
        ensureEnabled(environment.status(), "当前环境状态不允许编辑");
        try {
            jdbcTemplate.update("""
                    update base_environment
                    set name = coalesce(:name, name),
                        env_type = coalesce(:envType, env_type),
                        web_url = coalesce(:webUrl, web_url),
                        api_base_url = coalesce(:apiBaseUrl, api_base_url),
                        updated_by = :actorId,
                        updated_at = now(),
                        version = version + 1
                    where id = :environmentId
                      and deleted_at is null
                    """,
                    params(actor)
                            .addValue("environmentId", environment.id())
                            .addValue("name", blankToNull(request.name()))
                            .addValue("envType", blankToNull(request.envType()))
                            .addValue("webUrl", blankToNull(request.webUrl()))
                            .addValue("apiBaseUrl", blankToNull(request.apiBaseUrl()))
            );
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.CONFLICT, "环境编码或名称已存在");
        }
        EnvironmentView updated = environmentByKey(environment.id().toString());
        audit(actor, "更新环境", "environment", environment.id().toString(), updated.name());
        return updated;
    }

    @Override
    @Transactional
    public EnvironmentView changeEnvironmentStatus(String key, String status, AuthUserPrincipal actor) {
        EnvironmentRef environment = resolveEnvironmentStrict(key);
        String nextStatus = normalizeEnabledStatus(status, "环境状态不支持");
        jdbcTemplate.update("""
                update base_environment
                set status = :status,
                    updated_by = :actorId,
                    updated_at = now(),
                    version = version + 1
                where id = :environmentId
                  and deleted_at is null
                """,
                params(actor)
                        .addValue("environmentId", environment.id())
                        .addValue("status", nextStatus)
        );
        EnvironmentView updated = environmentByKey(environment.id().toString());
        audit(actor, "ENABLED".equals(nextStatus) ? "启用环境" : "停用环境", "environment", environment.id().toString(), updated.name());
        return updated;
    }

    @Override
    public PageResponse<ScopedUserRoleView> environmentUsers(String environmentKey, int page, int pageSize, String search) {
        EnvironmentRef environment = resolveEnvironmentStrict(environmentKey);
        return scopedUserRoles(environment.id(), "ENVIRONMENT", "", page, pageSize, search);
    }

    @Override
    @Transactional
    public ScopedUserRoleView addEnvironmentUser(String environmentKey, ScopedUserRoleRequest request, AuthUserPrincipal actor) {
        EnvironmentRef environment = resolveEnvironmentStrict(environmentKey);
        ensureEnabled(environment.status(), "当前环境状态不允许维护授权用户");
        String roleCode = request.roleCode().trim();
        if (!List.of("Tester", "Developer", "Auditor").contains(roleCode)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "环境授权用户只能绑定 Tester、Developer 或 Auditor 角色");
        }
        String username = request.username().trim();
        UUID userId = requireUserId(username);
        UUID roleId = requireRoleId(roleCode);
        bindScopedRole(userId, roleId, roleCode, "ENVIRONMENT", environment.id(), actor);
        bumpUserAuthVersion(userId, actor);
        ScopedUserRoleView view = scopedUserRoleByUsername(environment.id(), "ENVIRONMENT", "", username, "环境授权用户不存在");
        audit(actor, "添加环境授权", "environment_user", environment.id() + ":" + userId, environment.name() + ":" + username);
        return view;
    }

    @Override
    @Transactional
    public ScopedUserRoleView removeEnvironmentUser(String environmentKey, String username, AuthUserPrincipal actor) {
        EnvironmentRef environment = resolveEnvironmentStrict(environmentKey);
        ScopedUserRoleView current = scopedUserRoleByUsername(environment.id(), "ENVIRONMENT", "", username, "环境授权用户不存在");
        UUID userId = requireUserId(username);
        disableScopedRoles(userId, "ENVIRONMENT", environment.id(), "", actor);
        bumpUserAuthVersion(userId, actor);
        audit(actor, "移除环境授权", "environment_user", environment.id() + ":" + userId, environment.name() + ":" + username);
        return new ScopedUserRoleView(current.username(), current.displayName(), current.role(), current.scopeType(), "已移除");
    }

    @Override
    public PageResponse<IntegrationView> integrations(int page, int pageSize, String search) {
        List<IntegrationView> items = jdbcTemplate.query("""
                select
                    replace(config_key, 'integration.', '') as integration_key,
                    coalesce(value_json ->> 'name', replace(config_key, 'integration.', '')) as integration_name,
                    coalesce(value_json ->> 'category', '未分类') as category,
                    coalesce(value_json ->> 'scope', '平台级') as scope_name,
                    case status when 'ENABLED' then '已启用' else '已停用' end as status_name
                from base_config
                where deleted_at is null
                  and scope_type = 'SYSTEM'
                  and config_key like 'integration.%'
                  and (:search = ''
                    or config_key ilike :searchPattern
                    or coalesce(value_json ->> 'name', '') ilike :searchPattern
                    or coalesce(value_json ->> 'category', '') ilike :searchPattern
                    or coalesce(value_json ->> 'scope', '') ilike :searchPattern)
                order by updated_at desc, created_at desc
                limit :limit offset :offset
                """,
                pageParams(page, pageSize, search),
                (rs, rowNum) -> new IntegrationView(
                        rs.getString("integration_key"),
                        rs.getString("integration_name"),
                        rs.getString("category"),
                        rs.getString("scope_name"),
                        rs.getString("status_name")
                )
        );
        long total = count("""
                select count(*)
                from base_config
                where deleted_at is null
                  and scope_type = 'SYSTEM'
                  and config_key like 'integration.%'
                  and (:search = ''
                    or config_key ilike :searchPattern
                    or coalesce(value_json ->> 'name', '') ilike :searchPattern
                    or coalesce(value_json ->> 'category', '') ilike :searchPattern
                    or coalesce(value_json ->> 'scope', '') ilike :searchPattern)
                """, search);
        return PageResponse.of(items, page, pageSize, total);
    }

    @Override
    public IntegrationView integration(String key) {
        return integrationRow(key).view();
    }

    @Override
    @Transactional
    public IntegrationView createIntegration(CreateIntegrationRequest request, AuthUserPrincipal actor) {
        String key = integrationKey(request.code());
        if (key.isBlank()) {
            key = nextCode("integration");
        }
        String name = defaultText(request.name(), key);
        String category = defaultText(request.category(), "未分类");
        String scope = defaultText(request.scope(), "平台级");
        String configKey = integrationConfigKey(key);
        try {
            jdbcTemplate.update("""
                    insert into base_config (
                        scope_type,
                        scope_id,
                        config_key,
                        value_kind,
                        value_json,
                        status,
                        created_by,
                        updated_by
                    )
                    values (
                        'SYSTEM',
                        null,
                        :configKey,
                        'PLAIN',
                        cast(:valueJson as jsonb),
                        'ENABLED',
                        :actorId,
                        :actorId
                    )
                    """,
                    params(actor)
                            .addValue("configKey", configKey)
                            .addValue("valueJson", integrationJson(name, category, scope))
            );
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(ErrorCode.CONFLICT, "集成配置已存在");
        }
        IntegrationView created = integrationRow(key).view();
        audit(actor, "登记集成", "integration", configKey, created.name());
        return created;
    }

    @Override
    @Transactional
    public IntegrationView updateIntegration(String key, UpdateIntegrationRequest request, AuthUserPrincipal actor) {
        IntegrationRow current = integrationRow(key);
        String name = defaultText(request.name(), current.view().name());
        String category = defaultText(request.category(), current.view().category());
        String scope = defaultText(request.scope(), current.view().scope());
        jdbcTemplate.update("""
                update base_config
                set value_json = cast(:valueJson as jsonb),
                    updated_by = :actorId,
                    updated_at = now(),
                    version = version + 1
                where config_key = :configKey
                  and deleted_at is null
                """,
                params(actor)
                        .addValue("configKey", current.configKey())
                        .addValue("valueJson", integrationJson(name, category, scope))
        );
        IntegrationView updated = integrationRow(current.view().key()).view();
        audit(actor, "更新集成", "integration", current.configKey(), updated.name());
        return updated;
    }

    @Override
    @Transactional
    public IntegrationView changeIntegrationStatus(String key, String status, AuthUserPrincipal actor) {
        if (!List.of("ENABLED", "DISABLED").contains(status)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "集成配置状态只支持 ENABLED 或 DISABLED");
        }
        IntegrationRow current = integrationRow(key);
        jdbcTemplate.update("""
                update base_config
                set status = :status,
                    updated_by = :actorId,
                    updated_at = now(),
                    version = version + 1
                where config_key = :configKey
                  and deleted_at is null
                """,
                params(actor)
                        .addValue("configKey", current.configKey())
                        .addValue("status", status)
        );
        IntegrationView updated = integrationRow(current.view().key()).view();
        audit(actor, "ENABLED".equals(status) ? "启用集成" : "停用集成", "integration", current.configKey(), updated.name());
        return updated;
    }

    @Override
    public PageResponse<AuditLogView> auditLogs(int page, int pageSize, AuditLogQuery query, AuthUserPrincipal actor) {
        List<AuditLogView> items = jdbcTemplate.query("""
                select
                    to_char(a.created_at, 'YYYY-MM-DD HH24:MI') as created_time,
                    coalesce(u.username, a.actor_service, 'system') as actor_name,
                    a.action,
                    coalesce(a.after_json ->> 'name', a.resource_id, '-') as target,
                    case a.result when 'SUCCESS' then '成功' when 'DENIED' then '拒绝' else '失败' end as result_name
                from audit_log a
                left join iam_user u on u.id = a.actor_user_id
                    and u.deleted_at is null
                where (:search = ''
                   or a.action ilike :searchPattern
                   or a.resource_id ilike :searchPattern
                   or coalesce(u.username, a.actor_service, 'system') ilike :searchPattern
                   or coalesce(a.after_json ->> 'name', '') ilike :searchPattern)
                  and (:actor = '' or coalesce(u.username, a.actor_service, 'system') = :actor)
                  and (:action = '' or a.action = :action)
                  and (:resourceType = '' or a.resource_type = :resourceType)
                  and (:result = '' or a.result = :result)
                  and (cast(:startTime as timestamptz) is null or a.created_at >= cast(:startTime as timestamptz))
                  and (cast(:endTime as timestamptz) is null or a.created_at <= cast(:endTime as timestamptz))
                  and (
                    :platformScope = true
                    or a.actor_user_id = :actorId
                    or exists (
                        select 1
                        from rbac_role_binding b
                        where b.subject_type = 'USER'
                          and b.subject_id = :actorId
                          and b.status = 'ENABLED'
                          and b.deleted_at is null
                          and (b.expires_at is null or b.expires_at > now())
                          and b.scope_type = a.scope_type
                          and b.scope_id = a.scope_id
                    )
                  )
                order by a.created_at desc
                limit :limit offset :offset
                """,
                scopedAuditLogParams(page, pageSize, query, actor),
                (rs, rowNum) -> new AuditLogView(
                        rs.getString("created_time"),
                        rs.getString("actor_name"),
                        rs.getString("action"),
                        rs.getString("target"),
                        rs.getString("result_name")
                )
        );
        long total = count("""
                select count(*)
                from audit_log a
                left join iam_user u on u.id = a.actor_user_id
                    and u.deleted_at is null
                where (:search = ''
                   or a.action ilike :searchPattern
                   or a.resource_id ilike :searchPattern
                   or coalesce(u.username, a.actor_service, 'system') ilike :searchPattern
                   or coalesce(a.after_json ->> 'name', '') ilike :searchPattern)
                  and (:actor = '' or coalesce(u.username, a.actor_service, 'system') = :actor)
                  and (:action = '' or a.action = :action)
                  and (:resourceType = '' or a.resource_type = :resourceType)
                  and (:result = '' or a.result = :result)
                  and (cast(:startTime as timestamptz) is null or a.created_at >= cast(:startTime as timestamptz))
                  and (cast(:endTime as timestamptz) is null or a.created_at <= cast(:endTime as timestamptz))
                  and (
                    :platformScope = true
                    or a.actor_user_id = :actorId
                    or exists (
                        select 1
                        from rbac_role_binding b
                        where b.subject_type = 'USER'
                          and b.subject_id = :actorId
                          and b.status = 'ENABLED'
                          and b.deleted_at is null
                          and (b.expires_at is null or b.expires_at > now())
                          and b.scope_type = a.scope_type
                          and b.scope_id = a.scope_id
                    )
                  )
                """, scopedAuditLogParams(page, pageSize, query, actor));
        return PageResponse.of(items, page, pageSize, total);
    }

    @Override
    public PageResponse<SettingView> settings(int page, int pageSize, String search) {
        List<SettingView> items = jdbcTemplate.query("""
                select
                    config_key,
                    coalesce(value_json ->> '_display_name', '') as display_name,
                    case
                        when value_json ? '_value' then value_json ->> '_value'
                        when value_json is null then coalesce(masked_value, '已配置')
                        else trim(both '"' from value_json::text)
                    end as config_value,
                    scope_type,
                    case status when 'ENABLED' then '已启用' else '已停用' end as status_name
                from base_config
                where deleted_at is null
                  and status = 'ENABLED'
                  and (:search = ''
                    or config_key ilike :searchPattern
                    or coalesce(value_json ->> '_display_name', '') ilike :searchPattern)
                order by config_key
                limit :limit offset :offset
                """,
                pageParams(page, pageSize, search),
                (rs, rowNum) -> new SettingView(
                        rs.getString("config_key"),
                        defaultText(rs.getString("display_name"), settingName(rs.getString("config_key"))),
                        rs.getString("config_value"),
                        scopeName(rs.getString("scope_type")),
                        rs.getString("status_name")
                )
        );
        long total = count("""
                select count(*)
                from base_config
                where deleted_at is null
                  and status = 'ENABLED'
                  and (:search = ''
                    or config_key ilike :searchPattern
                    or coalesce(value_json ->> '_display_name', '') ilike :searchPattern)
                """, search);
        return PageResponse.of(items, page, pageSize, total);
    }

    @Override
    public SettingView setting(String key) {
        return settingRow(key).view();
    }

    @Override
    @Transactional
    public SettingView createSetting(CreateSettingRequest request, AuthUserPrincipal actor) {
        String key = normalizeSearch(request.key());
        rejectSensitivePlainSetting(key, request.value());
        String scopeType = defaultText(request.scopeType(), "SYSTEM");
        String name = defaultText(request.name(), settingName(key));
        try {
            jdbcTemplate.update("""
                    insert into base_config (
                        scope_type,
                        scope_id,
                        config_key,
                        value_kind,
                        value_json,
                        status,
                        created_by,
                        updated_by
                    )
                    values (
                        :scopeType,
                        null,
                        :configKey,
                        'PLAIN',
                        cast(:valueJson as jsonb),
                        'ENABLED',
                        :actorId,
                        :actorId
                    )
                    """,
                    params(actor)
                            .addValue("scopeType", scopeType)
                            .addValue("configKey", key)
                            .addValue("valueJson", settingJson(name, request.value().trim()))
            );
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(ErrorCode.CONFLICT, "系统设置已存在");
        }
        SettingView created = settingRow(key).view();
        audit(actor, "创建设置", "config", key, created.name());
        return created;
    }

    @Override
    @Transactional
    public SettingView updateSetting(String key, UpdateSettingRequest request, AuthUserPrincipal actor) {
        SettingRow current = settingRow(key);
        String name = defaultText(request.name(), current.view().name());
        String value = defaultText(request.value(), current.view().value());
        rejectSensitivePlainSetting(current.view().key(), value);
        String scopeType = defaultText(request.scopeType(), current.scopeType());
        jdbcTemplate.update("""
                update base_config
                set scope_type = :scopeType,
                    value_json = cast(:valueJson as jsonb),
                    updated_by = :actorId,
                    updated_at = now(),
                    version = version + 1
                where config_key = :configKey
                  and deleted_at is null
                """,
                params(actor)
                        .addValue("scopeType", scopeType)
                        .addValue("configKey", current.view().key())
                        .addValue("valueJson", settingJson(name, value))
        );
        SettingView updated = settingRow(current.view().key()).view();
        audit(actor, "更新设置", "config", current.view().key(), updated.name());
        return updated;
    }

    private void rejectSensitivePlainSetting(String key, String value) {
        String normalizedKey = normalizeSearch(key).toLowerCase();
        String normalizedValue = normalizeSearch(value);
        boolean sensitiveKey = normalizedKey.matches(".*(password|passwd|pwd|secret|token|api[_.-]?key|cookie|credential|private[_.-]?key).*");
        if (sensitiveKey && !normalizedValue.matches("^(\\*+|已配置|secret-ref:.+|\\$\\{[A-Za-z0-9_]+})$")) {
            throw new BusinessException(ErrorCode.SECRET_POLICY_VIOLATION, "敏感配置必须使用密钥引用或掩码值");
        }
    }

    @Override
    @Transactional
    public SettingView changeSettingStatus(String key, String status, AuthUserPrincipal actor) {
        if (!List.of("ENABLED", "DISABLED").contains(status)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "系统设置状态只支持 ENABLED 或 DISABLED");
        }
        SettingRow current = settingRow(key);
        jdbcTemplate.update("""
                update base_config
                set status = :status,
                    updated_by = :actorId,
                    updated_at = now(),
                    version = version + 1
                where config_key = :configKey
                  and deleted_at is null
                """,
                params(actor)
                        .addValue("status", status)
                        .addValue("configKey", current.view().key())
        );
        SettingView updated = settingRow(current.view().key()).view();
        audit(actor, "ENABLED".equals(status) ? "启用设置" : "停用设置", "config", current.view().key(), updated.name());
        return updated;
    }

    private void insertProjectOwner(UUID projectId, AuthUserPrincipal actor) {
        jdbcTemplate.update("""
                insert into base_project_member (
                    project_id,
                    user_id,
                    member_type,
                    status,
                    created_by,
                    updated_by
                )
                values (
                    :projectId,
                    :actorId,
                    'OWNER',
                    'ENABLED',
                    :actorId,
                    :actorId
                )
                on conflict do nothing
                """,
                params(actor).addValue("projectId", projectId)
        );
    }

    private void insertDepartmentManager(UUID deptId, AuthUserPrincipal actor) {
        jdbcTemplate.update("""
                insert into base_department_manager (
                    dept_id,
                    user_id,
                    status,
                    created_by,
                    updated_by
                )
                values (
                    :deptId,
                    :actorId,
                    'ENABLED',
                    :actorId,
                    :actorId
                )
                on conflict do nothing
                """,
                params(actor).addValue("deptId", deptId)
        );
    }

    private UUID ensureDefaultProject(AuthUserPrincipal actor) {
        List<UUID> ids = jdbcTemplate.query("""
                select id
                from base_project
                where code = 'default-project'
                  and deleted_at is null
                limit 1
                """,
                Map.of(),
                (rs, rowNum) -> rs.getObject("id", UUID.class)
        );
        if (!ids.isEmpty()) {
            return ids.getFirst();
        }

        UUID projectId = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into base_project (
                    id,
                    code,
                    name,
                    status,
                    created_by,
                    updated_by
                )
                values (
                    :projectId,
                    'default-project',
                    '默认项目',
                    'ACTIVE',
                    :actorId,
                    :actorId
                )
                on conflict do nothing
                """,
                params(actor).addValue("projectId", projectId)
        );
        insertProjectOwner(projectId, actor);
        return jdbcTemplate.queryForObject("""
                select id
                from base_project
                where code = 'default-project'
                  and deleted_at is null
                """,
                Map.of(),
                UUID.class
        );
    }

    private ProjectRef resolveProject(String project, AuthUserPrincipal actor) {
        String keyword = blankToNull(project);
        if (keyword == null) {
            return new ProjectRef(ensureDefaultProject(actor), "默认项目", "ACTIVE");
        }
        return resolveProjectStrict(keyword);
    }

    private DepartmentRef resolveDepartmentStrict(String key) {
        String keyword = blankToNull(key);
        if (keyword == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "部门不存在");
        }
        List<DepartmentRef> departments = jdbcTemplate.query("""
                select id, name, status
                from base_department
                where deleted_at is null
                  and (
                      code = :keyword
                      or name = :keyword
                      or cast(id as text) = :keyword
                  )
                limit 1
                """,
                Map.of("keyword", keyword),
                (rs, rowNum) -> new DepartmentRef(
                        rs.getObject("id", UUID.class),
                        rs.getString("name"),
                        rs.getString("status")
                )
        );
        return departments.stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "部门不存在"));
    }

    private ProjectRef resolveProjectStrict(String key) {
        String keyword = blankToNull(key);
        if (keyword == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "项目不存在");
        }
        List<ProjectRef> projects = jdbcTemplate.query("""
                select id, name, status
                from base_project
                where deleted_at is null
                  and (
                      code = :keyword
                      or name = :keyword
                      or cast(id as text) = :keyword
                  )
                limit 1
                """,
                Map.of("keyword", keyword),
                (rs, rowNum) -> new ProjectRef(
                        rs.getObject("id", UUID.class),
                        rs.getString("name"),
                        rs.getString("status")
                )
        );
        return projects.stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "项目不存在"));
    }

    private ApplicationRef resolveApplicationStrict(String key) {
        String keyword = blankToNull(key);
        if (keyword == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "应用不存在");
        }
        List<ApplicationRef> applications = jdbcTemplate.query("""
                select app.id, app.name, app.status, app.project_id, p.name as project_name
                from base_application app
                join base_project p on p.id = app.project_id
                    and p.deleted_at is null
                where app.deleted_at is null
                  and (
                      app.code = :keyword
                      or app.name = :keyword
                      or cast(app.id as text) = :keyword
                  )
                order by app.created_at desc
                limit 1
                """,
                Map.of("keyword", keyword),
                (rs, rowNum) -> new ApplicationRef(
                        rs.getObject("id", UUID.class),
                        rs.getString("name"),
                        rs.getString("status"),
                        rs.getObject("project_id", UUID.class),
                        rs.getString("project_name")
                )
        );
        return applications.stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "应用不存在"));
    }

    private EnvironmentRef resolveEnvironmentStrict(String key) {
        String keyword = blankToNull(key);
        if (keyword == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "环境不存在");
        }
        List<EnvironmentRef> environments = jdbcTemplate.query("""
                select env.id, env.name, env.status, env.project_id, p.name as project_name
                from base_environment env
                join base_project p on p.id = env.project_id
                    and p.deleted_at is null
                where env.deleted_at is null
                  and (
                      env.code = :keyword
                      or env.name = :keyword
                      or cast(env.id as text) = :keyword
                  )
                order by env.created_at desc
                limit 1
                """,
                Map.of("keyword", keyword),
                (rs, rowNum) -> new EnvironmentRef(
                        rs.getObject("id", UUID.class),
                        rs.getString("name"),
                        rs.getString("status"),
                        rs.getObject("project_id", UUID.class),
                        rs.getString("project_name")
                )
        );
        return environments.stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "环境不存在"));
    }

    private DepartmentView departmentByKey(String key) {
        String keyword = blankToNull(key);
        if (keyword == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "部门不存在");
        }
        List<DepartmentView> departments = jdbcTemplate.query("""
                select
                    d.name,
                    coalesce(parent.name, '总部') as parent_name,
                    coalesce((
                        select string_agg(u.display_name, ', ' order by u.display_name)
                        from base_department_manager manager
                        join iam_user u on u.id = manager.user_id
                            and u.deleted_at is null
                        where manager.dept_id = d.id
                          and manager.status = 'ENABLED'
                          and manager.deleted_at is null
                    ), '未设置') as lead,
                    (
                        select count(*)::int
                        from base_department_member member
                        where member.dept_id = d.id
                          and member.status = 'ENABLED'
                          and member.deleted_at is null
                    ) as members,
                    case d.status when 'ENABLED' then '同步正常' else '已停用' end as status_name
                from base_department d
                left join base_department parent on parent.id = d.parent_id
                    and parent.deleted_at is null
                where d.deleted_at is null
                  and (
                      d.code = :keyword
                      or d.name = :keyword
                      or cast(d.id as text) = :keyword
                  )
                limit 1
                """,
                Map.of("keyword", keyword),
                (rs, rowNum) -> new DepartmentView(
                        rs.getString("name"),
                        rs.getString("parent_name"),
                        rs.getString("lead"),
                        rs.getInt("members"),
                        rs.getString("status_name")
                )
        );
        return departments.stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "部门不存在"));
    }

    private ProjectView projectByKey(String key) {
        String keyword = blankToNull(key);
        if (keyword == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "项目不存在");
        }
        List<ProjectView> projects = jdbcTemplate.query("""
                select
                    p.name,
                    coalesce(primary_dept.name, '未分配') as department_name,
                    coalesce((
                        select string_agg(u.display_name, ', ' order by u.display_name)
                        from base_project_member member
                        join iam_user u on u.id = member.user_id
                            and u.deleted_at is null
                        where member.project_id = p.id
                          and member.member_type = 'OWNER'
                          and member.status = 'ENABLED'
                          and member.deleted_at is null
                    ), '未设置') as owner_name,
                    (
                        select count(*)::int
                        from base_application app
                        where app.project_id = p.id
                          and app.deleted_at is null
                    ) as apps,
                    case p.status
                        when 'ACTIVE' then '进行中'
                        when 'PREPARING' then '规划中'
                        when 'ARCHIVED' then '已归档'
                        else '已停用'
                    end as status_name
                from base_project p
                left join base_project_department pd on pd.project_id = p.id
                    and pd.is_primary = true
                    and pd.status = 'ENABLED'
                    and pd.deleted_at is null
                left join base_department primary_dept on primary_dept.id = pd.dept_id
                    and primary_dept.deleted_at is null
                where p.deleted_at is null
                  and (
                      p.code = :keyword
                      or p.name = :keyword
                      or cast(p.id as text) = :keyword
                  )
                limit 1
                """,
                Map.of("keyword", keyword),
                (rs, rowNum) -> new ProjectView(
                        rs.getString("name"),
                        rs.getString("department_name"),
                        rs.getString("owner_name"),
                        rs.getInt("apps"),
                        rs.getString("status_name")
                )
        );
        return projects.stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "项目不存在"));
    }

    private ApplicationView applicationByKey(String key) {
        String keyword = blankToNull(key);
        if (keyword == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "应用不存在");
        }
        List<ApplicationView> applications = jdbcTemplate.query("""
                select
                    app.name,
                    app.app_type,
                    coalesce((
                        select string_agg(u.display_name, ', ' order by u.display_name)
                        from rbac_role_binding b
                        join iam_user u on u.id = b.subject_id
                            and u.deleted_at is null
                        where b.subject_type = 'USER'
                          and b.scope_type = 'APPLICATION'
                          and b.scope_id = app.id
                          and b.role_code = 'AppOwner'
                          and b.status = 'ENABLED'
                          and b.deleted_at is null
                          and (b.expires_at is null or b.expires_at > now())
                    ), p.name) as owner_name,
                    'v' || app.version as version_name,
                    case app.status when 'ENABLED' then '已接入' else '已停用' end as status_name
                from base_application app
                join base_project p on p.id = app.project_id
                    and p.deleted_at is null
                where app.deleted_at is null
                  and (
                      app.code = :keyword
                      or app.name = :keyword
                      or cast(app.id as text) = :keyword
                  )
                limit 1
                """,
                Map.of("keyword", keyword),
                (rs, rowNum) -> new ApplicationView(
                        rs.getString("name"),
                        rs.getString("app_type"),
                        rs.getString("owner_name"),
                        rs.getString("version_name"),
                        rs.getString("status_name")
                )
        );
        return applications.stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "应用不存在"));
    }

    private EnvironmentView environmentByKey(String key) {
        String keyword = blankToNull(key);
        if (keyword == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "环境不存在");
        }
        List<EnvironmentView> environments = jdbcTemplate.query("""
                select
                    env.name,
                    p.name as cluster_name,
                    coalesce(env.api_base_url, env.web_url, env.code || '.local') as endpoint,
                    case env.status when 'ENABLED' then '可用' else '已停用' end as status_name
                from base_environment env
                join base_project p on p.id = env.project_id
                    and p.deleted_at is null
                where env.deleted_at is null
                  and (
                      env.code = :keyword
                      or env.name = :keyword
                      or cast(env.id as text) = :keyword
                  )
                limit 1
                """,
                Map.of("keyword", keyword),
                (rs, rowNum) -> new EnvironmentView(
                        rs.getString("name"),
                        rs.getString("cluster_name"),
                        rs.getString("endpoint"),
                        rs.getString("status_name")
                )
        );
        return environments.stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "环境不存在"));
    }

    private ProjectMemberView projectMemberByUsername(UUID projectId, String username) {
        List<ProjectMemberView> members = jdbcTemplate.query("""
                select
                    u.username,
                    u.display_name,
                    coalesce(string_agg(distinct b.role_code, ' / ' order by b.role_code), '-') as role_names,
                    member.member_type,
                    case member.status when 'ENABLED' then '启用' else '已停用' end as status_name
                from base_project_member member
                join iam_user u on u.id = member.user_id
                    and u.deleted_at is null
                left join rbac_role_binding b on b.subject_type = 'USER'
                    and b.subject_id = u.id
                    and b.scope_type = 'PROJECT'
                    and b.scope_id = member.project_id
                    and b.status = 'ENABLED'
                    and b.deleted_at is null
                    and (b.expires_at is null or b.expires_at > now())
                where member.project_id = :projectId
                  and member.deleted_at is null
                  and u.username = :username
                group by u.id, member.member_type, member.status
                limit 1
                """,
                new MapSqlParameterSource()
                        .addValue("projectId", projectId)
                        .addValue("username", username),
                (rs, rowNum) -> new ProjectMemberView(
                        rs.getString("username"),
                        rs.getString("display_name"),
                        rs.getString("role_names"),
                        rs.getString("member_type"),
                        rs.getString("status_name")
                )
        );
        return members.stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "项目成员不存在"));
    }

    private PageResponse<ScopedUserRoleView> scopedUserRoles(
            UUID scopeId,
            String scopeType,
            String roleCode,
            int page,
            int pageSize,
            String search
    ) {
        MapSqlParameterSource params = pageParams(page, pageSize, search)
                .addValue("scopeId", scopeId)
                .addValue("scopeType", scopeType)
                .addValue("roleCode", normalizeSearch(roleCode));
        List<ScopedUserRoleView> items = jdbcTemplate.query("""
                select
                    u.username,
                    u.display_name,
                    coalesce(string_agg(distinct b.role_code, ' / ' order by b.role_code), '-') as role_names,
                    b.scope_type,
                    '启用' as status_name,
                    max(b.created_at) as latest_bound_at
                from rbac_role_binding b
                join iam_user u on u.id = b.subject_id
                    and u.deleted_at is null
                where b.subject_type = 'USER'
                  and b.scope_type = :scopeType
                  and b.scope_id = :scopeId
                  and b.status = 'ENABLED'
                  and b.deleted_at is null
                  and (b.expires_at is null or b.expires_at > now())
                  and (:roleCode = '' or b.role_code = :roleCode)
                  and (:search = '' or u.username ilike :searchPattern or u.display_name ilike :searchPattern or b.role_code ilike :searchPattern)
                group by u.id, b.scope_type
                order by latest_bound_at desc
                limit :limit offset :offset
                """,
                params,
                (rs, rowNum) -> new ScopedUserRoleView(
                        rs.getString("username"),
                        rs.getString("display_name"),
                        rs.getString("role_names"),
                        rs.getString("scope_type"),
                        rs.getString("status_name")
                )
        );
        long total = count("""
                select count(distinct u.id)
                from rbac_role_binding b
                join iam_user u on u.id = b.subject_id
                    and u.deleted_at is null
                where b.subject_type = 'USER'
                  and b.scope_type = :scopeType
                  and b.scope_id = :scopeId
                  and b.status = 'ENABLED'
                  and b.deleted_at is null
                  and (b.expires_at is null or b.expires_at > now())
                  and (:roleCode = '' or b.role_code = :roleCode)
                  and (:search = '' or u.username ilike :searchPattern or u.display_name ilike :searchPattern)
                """, params);
        return PageResponse.of(items, page, pageSize, total);
    }

    private ScopedUserRoleView scopedUserRoleByUsername(
            UUID scopeId,
            String scopeType,
            String roleCode,
            String username,
            String notFoundMessage
    ) {
        List<ScopedUserRoleView> members = jdbcTemplate.query("""
                select
                    u.username,
                    u.display_name,
                    coalesce(string_agg(distinct b.role_code, ' / ' order by b.role_code), '-') as role_names,
                    b.scope_type,
                    '启用' as status_name
                from rbac_role_binding b
                join iam_user u on u.id = b.subject_id
                    and u.deleted_at is null
                where b.subject_type = 'USER'
                  and b.scope_type = :scopeType
                  and b.scope_id = :scopeId
                  and b.status = 'ENABLED'
                  and b.deleted_at is null
                  and (b.expires_at is null or b.expires_at > now())
                  and (:roleCode = '' or b.role_code = :roleCode)
                  and u.username = :username
                group by u.id, b.scope_type
                limit 1
                """,
                new MapSqlParameterSource()
                        .addValue("scopeId", scopeId)
                        .addValue("scopeType", scopeType)
                        .addValue("roleCode", normalizeSearch(roleCode))
                        .addValue("username", username),
                (rs, rowNum) -> new ScopedUserRoleView(
                        rs.getString("username"),
                        rs.getString("display_name"),
                        rs.getString("role_names"),
                        rs.getString("scope_type"),
                        rs.getString("status_name")
                )
        );
        return members.stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, notFoundMessage));
    }

    private UUID resolveEnvironmentApplicationId(
            CreateEnvironmentRequest request,
            ProjectRef project,
            String scopeType
    ) {
        String application = blankToNull(request.application());
        if ("PROJECT".equals(scopeType)) {
            if (application != null) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "项目级环境不能绑定应用");
            }
            return null;
        }
        if (application == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "应用级环境必须指定应用");
        }
        List<ApplicationRef> applications = jdbcTemplate.query("""
                select id, name, status, project_id, name as project_name
                from base_application
                where project_id = :projectId
                  and deleted_at is null
                  and (code = :application or name = :application)
                limit 1
                """,
                new MapSqlParameterSource()
                        .addValue("projectId", project.id())
                        .addValue("application", application),
                (rs, rowNum) -> new ApplicationRef(
                        rs.getObject("id", UUID.class),
                        rs.getString("name"),
                        rs.getString("status"),
                        rs.getObject("project_id", UUID.class),
                        rs.getString("project_name")
                )
        );
        ApplicationRef app = applications.stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "应用不存在"));
        ensureEnabled(app.status(), "当前应用状态不允许新增专属环境");
        return app.id();
    }

    private String normalizedOrGeneratedCode(String value, String prefix) {
        return normalizedOrDefault(value, nextCode(prefix));
    }

    private String normalizedOrDefault(String value, String defaultValue) {
        String normalized = blankToNull(value);
        return normalized == null ? defaultValue : normalized;
    }

    private String blankToNull(String value) {
        if (value == null || value.trim().isBlank()) {
            return null;
        }
        return value.trim();
    }

    private void ensureProjectEditable(String status) {
        if ("ARCHIVED".equals(status) || "DISABLED".equals(status)) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "当前项目状态不允许新增或编辑资源");
        }
    }

    private void ensureEnabled(String status, String message) {
        if (!"ENABLED".equals(status)) {
            throw new BusinessException(ErrorCode.INVALID_STATE, message);
        }
    }

    private String normalizeProjectStatus(String status) {
        String normalized = status == null ? "" : status.trim().toUpperCase();
        if (!List.of("PREPARING", "ACTIVE", "ARCHIVED", "DISABLED").contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "项目状态不支持");
        }
        return normalized;
    }

    private String normalizeEnabledStatus(String status, String message) {
        String normalized = status == null ? "" : status.trim().toUpperCase();
        if (!List.of("ENABLED", "DISABLED").contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, message);
        }
        return normalized;
    }

    private void ensureProjectStatusTransition(String currentStatus, String nextStatus) {
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
            throw new BusinessException(ErrorCode.INVALID_STATE, "项目状态不允许从 " + currentStatus + " 流转到 " + nextStatus);
        }
    }

    private String projectStatusAction(String status) {
        return switch (status) {
            case "ARCHIVED" -> "归档项目";
            case "DISABLED" -> "停用项目";
            case "ACTIVE", "PREPARING" -> "恢复项目";
            default -> "更新项目状态";
        };
    }

    private String memberTypeForRole(String roleCode) {
        return "ProjectOwner".equals(roleCode) ? "OWNER" : "MEMBER";
    }

    private record DepartmentRef(UUID id, String name, String status) {
    }

    private record ProjectRef(UUID id, String name, String status) {
    }

    private record ApplicationRef(UUID id, String name, String status, UUID projectId, String projectName) {
    }

    private record EnvironmentRef(UUID id, String name, String status, UUID projectId, String projectName) {
    }

    private void bindRoleIfPresent(
            UUID userId,
            String roleCode,
            String scopeType,
            UUID scopeId,
            AuthUserPrincipal actor
    ) {
        List<UUID> roleIds = jdbcTemplate.query("""
                select id
                from rbac_role
                where code = :roleCode
                  and status = 'ENABLED'
                  and deleted_at is null
                limit 1
                """,
                Map.of("roleCode", roleCode),
                (rs, rowNum) -> rs.getObject("id", UUID.class)
        );
        if (roleIds.isEmpty()) {
            return;
        }
        jdbcTemplate.update("""
                insert into rbac_role_binding (
                    subject_type,
                    subject_id,
                    role_id,
                    role_code,
                    scope_type,
                    scope_id,
                    status,
                    created_by,
                    updated_by
                )
                values (
                    'USER',
                    :userId,
                    :roleId,
                    :roleCode,
                    :scopeType,
                    :scopeId,
                    'ENABLED',
                    :actorId,
                    :actorId
                )
                on conflict do nothing
                """,
                params(actor)
                        .addValue("userId", userId)
                        .addValue("roleId", roleIds.getFirst())
                        .addValue("roleCode", roleCode)
                        .addValue("scopeType", scopeType)
                        .addValue("scopeId", scopeId)
        );
    }

    private void bindProjectRole(
            UUID userId,
            UUID roleId,
            String roleCode,
            UUID projectId,
            AuthUserPrincipal actor
    ) {
        jdbcTemplate.update("""
                insert into rbac_role_binding (
                    subject_type,
                    subject_id,
                    role_id,
                    role_code,
                    scope_type,
                    scope_id,
                    status,
                    created_by,
                    updated_by
                )
                values (
                    'USER',
                    :userId,
                    :roleId,
                    :roleCode,
                    'PROJECT',
                    :projectId,
                    'ENABLED',
                    :actorId,
                    :actorId
                )
                on conflict (subject_type, subject_id, role_id, scope_type, coalesce(scope_id, '00000000-0000-0000-0000-000000000000'::uuid)) where deleted_at is null
                do update set
                    status = 'ENABLED',
                    updated_by = :actorId,
                    updated_at = now(),
                    version = rbac_role_binding.version + 1
                """,
                params(actor)
                        .addValue("userId", userId)
                        .addValue("roleId", roleId)
                        .addValue("roleCode", roleCode)
                        .addValue("projectId", projectId)
        );
    }

    private void bindScopedRole(
            UUID userId,
            UUID roleId,
            String roleCode,
            String scopeType,
            UUID scopeId,
            AuthUserPrincipal actor
    ) {
        jdbcTemplate.update("""
                insert into rbac_role_binding (
                    subject_type,
                    subject_id,
                    role_id,
                    role_code,
                    scope_type,
                    scope_id,
                    status,
                    created_by,
                    updated_by
                )
                values (
                    'USER',
                    :userId,
                    :roleId,
                    :roleCode,
                    :scopeType,
                    :scopeId,
                    'ENABLED',
                    :actorId,
                    :actorId
                )
                on conflict (subject_type, subject_id, role_id, scope_type, coalesce(scope_id, '00000000-0000-0000-0000-000000000000'::uuid)) where deleted_at is null
                do update set
                    status = 'ENABLED',
                    updated_by = :actorId,
                    updated_at = now(),
                    version = rbac_role_binding.version + 1
                """,
                params(actor)
                        .addValue("userId", userId)
                        .addValue("roleId", roleId)
                        .addValue("roleCode", roleCode)
                        .addValue("scopeType", scopeType)
                        .addValue("scopeId", scopeId)
        );
    }

    private void disableScopedRoles(
            UUID userId,
            String scopeType,
            UUID scopeId,
            String roleCode,
            AuthUserPrincipal actor
    ) {
        jdbcTemplate.update("""
                update rbac_role_binding
                set status = 'DISABLED',
                    deleted_by = :actorId,
                    deleted_at = now(),
                    updated_by = :actorId,
                    updated_at = now(),
                    version = version + 1
                where subject_type = 'USER'
                  and subject_id = :userId
                  and scope_type = :scopeType
                  and scope_id = :scopeId
                  and (:roleCode = '' or role_code = :roleCode)
                  and deleted_at is null
                """,
                params(actor)
                        .addValue("userId", userId)
                        .addValue("scopeType", scopeType)
                        .addValue("scopeId", scopeId)
                        .addValue("roleCode", normalizeSearch(roleCode))
        );
    }

    private void audit(
            AuthUserPrincipal actor,
            String action,
            String resourceType,
            String resourceId,
            String targetName
    ) {
        jdbcTemplate.update("""
                insert into audit_log (
                    trace_id,
                    actor_type,
                    actor_user_id,
                    action,
                    resource_type,
                    resource_id,
                    scope_type,
                    scope_id,
                    result,
                    after_json
                )
                values (
                    :traceId,
                    'USER',
                    :actorId,
                    :action,
                    :resourceType,
                    :resourceId,
                    'PLATFORM',
                    null,
                    'SUCCESS',
                    cast(:afterJson as jsonb)
                )
                """,
                params(actor)
                        .addValue("traceId", TraceContext.getTraceId())
                        .addValue("action", action)
                        .addValue("resourceType", resourceType)
                        .addValue("resourceId", resourceId)
                        .addValue("afterJson", "{\"name\":\"" + escapeJson(targetName) + "\"}")
        );
    }

    private UserView userByUsername(String username) {
        List<UserView> matched = jdbcTemplate.query("""
                select
                    u.username,
                    u.display_name,
                    coalesce(u.email, '') as email,
                    coalesce(string_agg(distinct b.role_code, ' / ' order by b.role_code), '未分配') as role_names,
                    coalesce(primary_dept.name, '未分配') as department_name,
                    case u.status
                        when 'ENABLED' then '启用'
                        when 'PENDING_ACTIVATION' then '待激活'
                        when 'LOCKED' then '已锁定'
                        else '已停用'
                    end as status_name,
                    coalesce(to_char(u.last_login_at, 'YYYY-MM-DD HH24:MI'), '尚未登录') as last_seen
                from iam_user u
                left join rbac_role_binding b on b.subject_type = 'USER'
                    and b.subject_id = u.id
                    and b.status = 'ENABLED'
                    and b.deleted_at is null
                    and (b.expires_at is null or b.expires_at > now())
                left join base_department_member primary_member on primary_member.user_id = u.id
                    and primary_member.is_primary = true
                    and primary_member.status = 'ENABLED'
                    and primary_member.deleted_at is null
                left join base_department primary_dept on primary_dept.id = primary_member.dept_id
                    and primary_dept.deleted_at is null
                where u.username = :username
                  and u.deleted_at is null
                group by u.id, primary_dept.name
                limit 1
                """,
                Map.of("username", username),
                (rs, rowNum) -> new UserView(
                        rs.getString("username"),
                        rs.getString("display_name"),
                        rs.getString("email"),
                        rs.getString("role_names"),
                        rs.getString("department_name"),
                        rs.getString("status_name"),
                        rs.getString("last_seen")
                )
        );
        return matched.stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用户不存在"));
    }

    private void ensureUserUpdated(int rows) {
        if (rows == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
    }

    private MapSqlParameterSource pageParams(int page, int pageSize, String search) {
        String keyword = normalizeSearch(search);
        return new MapSqlParameterSource()
                .addValue("search", keyword)
                .addValue("searchPattern", "%" + keyword + "%")
                .addValue("limit", pageSize)
                .addValue("offset", (page - 1) * pageSize);
    }

    private MapSqlParameterSource auditLogParams(int page, int pageSize, AuditLogQuery query) {
        return pageParams(page, pageSize, query.search())
                .addValue("actor", query.actor())
                .addValue("action", query.action())
                .addValue("resourceType", query.resourceType())
                .addValue("result", normalizeAuditResult(query.result()))
                .addValue("startTime", query.startTime(), Types.TIMESTAMP_WITH_TIMEZONE)
                .addValue("endTime", query.endTime(), Types.TIMESTAMP_WITH_TIMEZONE);
    }

    private MapSqlParameterSource scopedPageParams(int page, int pageSize, String search, AuthUserPrincipal actor) {
        return pageParams(page, pageSize, search)
                .addValue("actorId", actor.userId())
                .addValue("platformScope", hasPlatformScope(actor));
    }

    private MapSqlParameterSource scopedAuditLogParams(int page, int pageSize, AuditLogQuery query, AuthUserPrincipal actor) {
        return auditLogParams(page, pageSize, query)
                .addValue("actorId", actor.userId())
                .addValue("platformScope", hasPlatformScope(actor));
    }

    private boolean hasPlatformScope(AuthUserPrincipal actor) {
        return actor.roles().stream().anyMatch(role -> List.of("SuperAdmin", "PlatformAdmin", "Auditor").contains(role));
    }

    private long count(String sql, String search) {
        Long total = jdbcTemplate.queryForObject(
                sql,
                new MapSqlParameterSource()
                        .addValue("search", normalizeSearch(search))
                        .addValue("searchPattern", "%" + normalizeSearch(search) + "%"),
                Long.class
        );
        return total == null ? 0 : total;
    }

    private long count(String sql, MapSqlParameterSource params) {
        Long total = jdbcTemplate.queryForObject(sql, params, Long.class);
        return total == null ? 0 : total;
    }

    private <T> PageResponse<T> inMemoryPage(List<T> source, int page, int pageSize, String search) {
        String keyword = normalizeSearch(search).toLowerCase();
        List<T> filtered = source.stream()
                .filter(item -> keyword.isBlank() || item.toString().toLowerCase().contains(keyword))
                .toList();
        int from = Math.min((page - 1) * pageSize, filtered.size());
        int to = Math.min(from + pageSize, filtered.size());
        return PageResponse.of(filtered.subList(from, to), page, pageSize, filtered.size());
    }

    private String normalizeSearch(String search) {
        return search == null ? "" : search.trim();
    }

    private String normalizeAuditResult(String result) {
        return switch (normalizeSearch(result).toUpperCase()) {
            case "成功", "SUCCESS" -> "SUCCESS";
            case "拒绝", "DENIED" -> "DENIED";
            case "失败", "FAILED" -> "FAILED";
            default -> normalizeSearch(result).toUpperCase();
        };
    }

    private IntegrationRow integrationRow(String key) {
        String normalizedKey = normalizeSearch(key);
        List<IntegrationRow> rows = jdbcTemplate.query("""
                select
                    config_key,
                    replace(config_key, 'integration.', '') as integration_key,
                    coalesce(value_json ->> 'name', replace(config_key, 'integration.', '')) as integration_name,
                    coalesce(value_json ->> 'category', '未分类') as category,
                    coalesce(value_json ->> 'scope', '平台级') as scope_name,
                    case status when 'ENABLED' then '已启用' else '已停用' end as status_name
                from base_config
                where deleted_at is null
                  and scope_type = 'SYSTEM'
                  and config_key like 'integration.%'
                  and (
                    replace(config_key, 'integration.', '') = :key
                    or coalesce(value_json ->> 'name', '') = :key
                  )
                limit 1
                """,
                Map.of("key", normalizedKey),
                (rs, rowNum) -> new IntegrationRow(
                        rs.getString("config_key"),
                        new IntegrationView(
                                rs.getString("integration_key"),
                                rs.getString("integration_name"),
                                rs.getString("category"),
                                rs.getString("scope_name"),
                                rs.getString("status_name")
                        )
                )
        );
        return rows.stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "集成配置不存在"));
    }

    private SettingRow settingRow(String key) {
        String normalizedKey = normalizeSearch(key);
        List<SettingRow> rows = jdbcTemplate.query("""
                select
                    config_key,
                    scope_type,
                    coalesce(value_json ->> '_display_name', '') as display_name,
                    case
                        when value_json ? '_value' then value_json ->> '_value'
                        when value_json is null then coalesce(masked_value, '已配置')
                        else trim(both '"' from value_json::text)
                    end as config_value,
                    case status when 'ENABLED' then '已启用' else '已停用' end as status_name
                from base_config
                where deleted_at is null
                  and (
                    config_key = :key
                    or coalesce(value_json ->> '_display_name', '') = :key
                  )
                limit 1
                """,
                Map.of("key", normalizedKey),
                (rs, rowNum) -> new SettingRow(
                        rs.getString("scope_type"),
                        new SettingView(
                                rs.getString("config_key"),
                                defaultText(rs.getString("display_name"), settingName(rs.getString("config_key"))),
                                rs.getString("config_value"),
                                scopeName(rs.getString("scope_type")),
                                rs.getString("status_name")
                        )
                )
        );
        return rows.stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "系统设置不存在"));
    }

    private String integrationKey(String code) {
        return normalizeSearch(code).toLowerCase();
    }

    private String integrationConfigKey(String key) {
        return "integration." + key;
    }

    private String integrationJson(String name, String category, String scope) {
        return "{\"name\":\"" + escapeJson(name) + "\","
                + "\"category\":\"" + escapeJson(category) + "\","
                + "\"scope\":\"" + escapeJson(scope) + "\"}";
    }

    private String settingJson(String name, String value) {
        return "{\"_display_name\":\"" + escapeJson(name) + "\","
                + "\"_value\":\"" + escapeJson(value) + "\"}";
    }

    private String defaultText(String value, String fallback) {
        String normalized = value == null ? "" : value.trim();
        return normalized.isBlank() ? fallback : normalized;
    }

    private UUID requireUserId(String username) {
        List<UUID> userIds = jdbcTemplate.query("""
                select id
                from iam_user
                where username = :username
                  and deleted_at is null
                limit 1
                """,
                Map.of("username", username),
                (rs, rowNum) -> rs.getObject("id", UUID.class)
        );
        return userIds.stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用户不存在"));
    }

    private UUID requireRoleId(String roleCode) {
        List<UUID> roleIds = jdbcTemplate.query("""
                select id
                from rbac_role
                where code = :roleCode
                  and status = 'ENABLED'
                  and deleted_at is null
                limit 1
                """,
                Map.of("roleCode", roleCode),
                (rs, rowNum) -> rs.getObject("id", UUID.class)
        );
        return roleIds.stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "角色不存在"));
    }

    private void bumpUserAuthVersion(UUID userId, AuthUserPrincipal actor) {
        jdbcTemplate.update("""
                update iam_user
                set auth_version = auth_version + 1,
                    updated_by = :actorId,
                    updated_at = now()
                where id = :userId
                  and deleted_at is null
                """,
                params(actor).addValue("userId", userId)
        );
    }

    private MapSqlParameterSource params(AuthUserPrincipal actor) {
        return new MapSqlParameterSource().addValue("actorId", actor.userId());
    }

    private String nextCode(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record IntegrationRow(String configKey, IntegrationView view) {
    }

    private record SettingRow(String scopeType, SettingView view) {
    }

    private String settingName(String configKey) {
        return switch (configKey) {
            case "audit.retention_days" -> "审计日志保留";
            case "session.access_token_ttl_minutes" -> "访问令牌有效期";
            case "allow_public_model" -> "允许公有云模型";
            case "sensitivity_level" -> "默认敏感级别";
            case "default_resource_pool" -> "默认资源池";
            case "secret.default_provider" -> "默认密钥提供方";
            default -> configKey;
        };
    }

    private String scopeName(String scopeType) {
        return switch (scopeType) {
            case "SYSTEM" -> "平台级";
            case "PROJECT" -> "项目级";
            case "APPLICATION" -> "应用级";
            case "ENVIRONMENT" -> "环境级";
            default -> scopeType;
        };
    }
}
