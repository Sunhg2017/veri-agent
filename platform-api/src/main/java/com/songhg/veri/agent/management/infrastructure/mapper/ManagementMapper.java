package com.songhg.veri.agent.management.infrastructure.mapper;

import com.songhg.veri.agent.management.api.response.ApplicationView;
import com.songhg.veri.agent.management.api.response.AuditLogView;
import com.songhg.veri.agent.management.api.response.AuditOutboxView;
import com.songhg.veri.agent.management.api.response.DepartmentView;
import com.songhg.veri.agent.management.api.response.EnvironmentView;
import com.songhg.veri.agent.management.api.response.IntegrationView;
import com.songhg.veri.agent.management.api.response.ProjectMemberView;
import com.songhg.veri.agent.management.api.response.ProjectView;
import com.songhg.veri.agent.management.api.response.RoleView;
import com.songhg.veri.agent.management.api.response.ScopedUserRoleView;
import com.songhg.veri.agent.management.api.response.UserView;
import com.songhg.veri.agent.management.infrastructure.mapper.ManagementMapperRows.ApplicationRef;
import com.songhg.veri.agent.management.infrastructure.mapper.ManagementMapperRows.DepartmentRef;
import com.songhg.veri.agent.management.infrastructure.mapper.ManagementMapperRows.EnvironmentConnectivityTargetRow;
import com.songhg.veri.agent.management.infrastructure.mapper.ManagementMapperRows.EnvironmentRef;
import com.songhg.veri.agent.management.infrastructure.mapper.ManagementMapperRows.IntegrationRow;
import com.songhg.veri.agent.management.infrastructure.mapper.ManagementMapperRows.ProjectRef;
import com.songhg.veri.agent.management.infrastructure.mapper.ManagementMapperRows.SettingRow;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ManagementMapper {

    List<DepartmentView> listDepartments(Map<String, Object> params);

    long countDepartments(Map<String, Object> params);

    int insertDepartment(Map<String, Object> params);

    int updateDepartment(Map<String, Object> params);

    int changeDepartmentStatus(Map<String, Object> params);

    List<UserView> listUsers(Map<String, Object> params);

    long countUsers(Map<String, Object> params);

    int insertUser(Map<String, Object> params);

    int updateUser(Map<String, Object> params);

    int enableUser(Map<String, Object> params);

    int disableUser(Map<String, Object> params);

    int lockUser(Map<String, Object> params);

    int unlockUser(Map<String, Object> params);

    int resetUserPassword(Map<String, Object> params);

    List<RoleView> listRoles(Map<String, Object> params);

    long countRoles(Map<String, Object> params);

    int assignUserRole(Map<String, Object> params);

    int unassignUserRole(Map<String, Object> params);

    List<ProjectView> listProjects(Map<String, Object> params);

    long countProjects(Map<String, Object> params);

    int insertProject(Map<String, Object> params);

    int updateProject(Map<String, Object> params);

    int changeProjectStatus(Map<String, Object> params);

    List<ProjectMemberView> listProjectMembers(Map<String, Object> params);

    long countProjectMembers(Map<String, Object> params);

    int upsertProjectMember(Map<String, Object> params);

    int deleteProjectMember(Map<String, Object> params);

    int disableProjectRoleBindings(Map<String, Object> params);

    List<ApplicationView> listApplications(Map<String, Object> params);

    long countApplications(Map<String, Object> params);

    int insertApplication(Map<String, Object> params);

    int updateApplication(Map<String, Object> params);

    int changeApplicationStatus(Map<String, Object> params);

    List<EnvironmentView> listEnvironments(Map<String, Object> params);

    long countEnvironments(Map<String, Object> params);

    int insertEnvironment(Map<String, Object> params);

    int updateEnvironment(Map<String, Object> params);

    int changeEnvironmentStatus(Map<String, Object> params);

    EnvironmentConnectivityTargetRow findEnvironmentConnectivityTarget(Map<String, Object> params);

    int updateEnvironmentHealthCheck(Map<String, Object> params);

    List<IntegrationView> listIntegrations(Map<String, Object> params);

    long countIntegrations(Map<String, Object> params);

    List<AuditLogView> listAuditLogs(Map<String, Object> params);

    long countAuditLogs(Map<String, Object> params);

    List<AuditOutboxView> listAuditOutbox(Map<String, Object> params);

    long countAuditOutbox(Map<String, Object> params);

    List<SettingRow> listSettings(Map<String, Object> params);

    long countSettings(Map<String, Object> params);

    int insertConfig(Map<String, Object> params);

    int updateIntegration(Map<String, Object> params);

    int updateSetting(Map<String, Object> params);

    int changeConfigStatus(Map<String, Object> params);

    int insertProjectOwner(Map<String, Object> params);

    int insertDepartmentManager(Map<String, Object> params);

    UUID findDefaultProjectId(Map<String, Object> params);

    int insertDefaultProject(Map<String, Object> params);

    DepartmentRef findDepartmentRef(Map<String, Object> params);

    ProjectRef findProjectRef(Map<String, Object> params);

    ApplicationRef findApplicationRef(Map<String, Object> params);

    EnvironmentRef findEnvironmentRef(Map<String, Object> params);

    DepartmentView findDepartmentView(Map<String, Object> params);

    ProjectView findProjectView(Map<String, Object> params);

    ApplicationView findApplicationView(Map<String, Object> params);

    EnvironmentView findEnvironmentView(Map<String, Object> params);

    ProjectMemberView findProjectMemberByUsername(Map<String, Object> params);

    List<ScopedUserRoleView> listScopedUserRoles(Map<String, Object> params);

    long countScopedUserRoles(Map<String, Object> params);

    ScopedUserRoleView findScopedUserRoleByUsername(Map<String, Object> params);

    ApplicationRef findApplicationRefInProject(Map<String, Object> params);

    UUID findRoleId(Map<String, Object> params);

    int bindRoleIfPresent(Map<String, Object> params);

    int bindProjectRole(Map<String, Object> params);

    int bindScopedRole(Map<String, Object> params);

    int disableScopedRoles(Map<String, Object> params);

    int insertAuditLog(Map<String, Object> params);

    UserView findUserByUsername(Map<String, Object> params);

    IntegrationRow findIntegrationRow(Map<String, Object> params);

    SettingRow findSettingRow(Map<String, Object> params);

    UUID findUserId(Map<String, Object> params);

    int bumpUserAuthVersion(Map<String, Object> params);
}
