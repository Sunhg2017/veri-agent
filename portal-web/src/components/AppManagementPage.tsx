import {
  AppWindow,
  Archive,
  CheckCircle2,
  DatabaseZap,
  GitBranch,
  KeyRound,
  Link2,
  LockKeyhole,
  Pencil,
  Power,
  ScrollText,
  ServerCog,
  Settings,
  ShieldCheck,
  type LucideIcon
} from 'lucide-react';
import { useEffect, useState } from 'react';
import type * as React from 'react';
import type { CurrentUser } from '../api/auth';
import {
  addApplicationOwner,
  addEnvironmentUser,
  addProjectMember,
  changeApplicationStatus,
  changeDepartmentStatus,
  changeEnvironmentStatus,
  changeIntegrationStatus,
  changeProjectStatus,
  changeSettingStatus,
  fetchApplication,
  fetchApplicationOwners,
  fetchDepartment,
  fetchEnvironment,
  fetchEnvironmentUsers,
  fetchIntegration,
  fetchProject,
  fetchProjectMembers,
  fetchRole,
  fetchSetting,
  fetchUser,
  removeApplicationOwner,
  removeEnvironmentUser,
  removeProjectMember,
  runEnvironmentConnectivityCheck,
  updateApplication,
  updateDepartment,
  updateEnvironment,
  updateIntegration,
  updateProject,
  updateSetting,
  updateUser,
  type ApplicationView,
  type AuditLogView,
  type AuditOutboxFilters,
  type AuditOutboxView,
  type CreatableManagementResource,
  type DepartmentView,
  type EnvironmentConnectivityCheckView,
  type EnvironmentView,
  type IntegrationView,
  type ManagementData,
  type PageResponse as ManagementPageResponse,
  type PermissionView,
  type ProjectMemberView,
  type ProjectView,
  type RoleDetailView,
  type RoleView,
  type ScopedUserRoleView,
  type SettingView,
  type UserView
} from '../api/management';
import {
  canUseButton,
  hasPermission,
  type PageKey,
  type UserLifecycleAction
} from '../permissions';

function optionalBoolean(value: string | undefined): boolean | undefined {
  if (value === 'true') return true;
  if (value === 'false') return false;
  return undefined;
}

/* ===================== Management Pages ===================== */

export interface ManagementPageProps {
  page: PageKey;
  data: ManagementData;
  loadState: { loading: boolean; error?: string };
  signedIn: boolean;
  currentUser: CurrentUser | null;
  onCreate: (resource: CreatableManagementResource, label: string, name: string) => Promise<void>;
  onUserLifecycleAction: (username: string, action: UserLifecycleAction, roleCode?: string) => Promise<void>;
  onResetPassword: (username: string) => void;
  auditExportState: { loading: boolean; error?: string };
  onAuditExport: () => Promise<void>;
  auditOutboxFilters: AuditOutboxFilters;
  auditOutboxLoad: { loading: boolean; error?: string };
  onAuditOutboxFiltersChange: (filters: AuditOutboxFilters) => void;
  onAuditOutboxRefresh: (filters?: AuditOutboxFilters) => Promise<void>;
  onRefresh: () => void;
}

export function ManagementPage(props: ManagementPageProps) {
  const { page, data, loadState, signedIn, currentUser } = props;

  // ===================== Organizations
  if (page === 'organizations') {
    return (
      <DataSection
        eyebrow="组织架构"
        title="部门结构"
        icon={GitBranch}
        action="新增部门"
        createResource="departments"
        columns={['部门', '上级部门', '负责人', '成员', '状态']}
        rows={data.departments.map((d: DepartmentView) => [d.name, d.parent, d.lead, d.members, d.status])}
        loadState={loadState}
        signedIn={signedIn}
        canCreate={hasPermission(currentUser, 'department:create')}
        onCreate={props.onCreate}
        onRefresh={props.onRefresh}
        sidePanel={
          <ResourceLifecyclePanel<DepartmentView>
            title="部门详情"
            resourceLabel="部门"
            emptyLabel="暂无部门"
            resources={data.departments.map((d) => d.name)}
            fields={[{ key: 'name', label: '部门名称', placeholder: '输入部门名称' }]}
            signedIn={signedIn}
            canEdit={hasPermission(currentUser, 'department:edit')}
            statusOptions={[
              hasPermission(currentUser, 'department:enable') ? { value: 'ENABLED', label: '启用部门', icon: Power } as StatusOption : undefined,
              hasPermission(currentUser, 'department:disable') ? { value: 'DISABLED', label: '停用部门', icon: Power } as StatusOption : undefined,
            ].filter(Boolean) as StatusOption[]}
            fetchDetail={fetchDepartment}
            updateDetail={(key, draft) => updateDepartment(key, { name: draft.name })}
            changeStatus={changeDepartmentStatus}
            detailTitle={(d) => d.name}
            draftFromDetail={(d) => ({ name: d.name })}
            detailRows={(d) => [['上级部门', d.parent], ['负责人', d.lead], ['成员数', d.members], ['状态', d.status]]}
            onChanged={props.onRefresh}
          />
        }
      />
    );
  }

  // ===================== Users
  if (page === 'users') {
    const canEnable = hasPermission(currentUser, 'user:enable');
    const canDisable = hasPermission(currentUser, 'user:disable');
    const canLock = hasPermission(currentUser, 'user:lock');
    const canEdit = hasPermission(currentUser, 'user:edit');
    const canResetPassword = hasPermission(currentUser, 'user:reset_password');
    const canAssignRole = hasPermission(currentUser, 'user:assign_role') && hasPermission(currentUser, 'role:bind');
    const canUnassignRole = hasPermission(currentUser, 'user:assign_role') && hasPermission(currentUser, 'role:unbind');
    const canMutateUsers = canEnable || canDisable || canLock || canResetPassword || canAssignRole || canUnassignRole;
    return (
      <DataSection
        eyebrow="账号管理"
        title="账号与角色"
        icon={ShieldCheck}
        action="邀请用户"
        createResource="users"
        columns={canMutateUsers ? ['用户名', '角色', '部门', '状态', '最近访问', '操作'] : ['用户名', '角色', '部门', '状态', '最近访问']}
        rows={data.users.map((item: UserView) => [
          item.username,
          item.role,
          item.department,
          <span key={`${item.username}-status`}>
            <StatusBadge status={item.status} />
          </span>,
          item.last_seen,
          ...(canMutateUsers ? [(
            <div className="row-actions" key={`${item.username}-actions`} style={{ justifyContent: 'flex-start' }}>
              <RoleBindingControls
                username={item.username}
                roles={data.roles}
                loading={loadState.loading}
                signedIn={signedIn}
                canAssign={canAssignRole}
                canUnassign={canUnassignRole}
                onAction={props.onUserLifecycleAction}
              />
              {canEnable && (
                <button className="btn btn-xs btn-secondary" disabled={!signedIn || loadState.loading}
                  onClick={() => props.onUserLifecycleAction(item.username, item.status === '已锁定' ? 'unlock' : 'enable')}>
                  <CheckCircle2 size={13} />{item.status === '已锁定' ? '解锁' : '启用'}
                </button>
              )}
              {canLock && (
                <button className="btn btn-xs btn-secondary" disabled={!signedIn || loadState.loading || item.username === currentUser?.username || item.status === '已锁定'}
                  onClick={() => props.onUserLifecycleAction(item.username, 'lock')}>
                  <LockKeyhole size={13} />锁定
                </button>
              )}
              {canDisable && (
                <button className="btn btn-xs btn-secondary" disabled={!signedIn || loadState.loading || item.username === currentUser?.username}
                  onClick={() => props.onUserLifecycleAction(item.username, 'disable')}>
                  <LockKeyhole size={13} />停用
                </button>
              )}
              {canResetPassword && (
                <button className="btn btn-xs btn-secondary" disabled={!signedIn || loadState.loading}
                  onClick={() => props.onResetPassword(item.username)}>
                  <KeyRound size={13} />重置
                </button>
              )}
            </div>
          )] : [])
        ])}
        loadState={loadState}
        signedIn={signedIn}
        canCreate={hasPermission(currentUser, 'user:create')}
        onCreate={props.onCreate}
        onRefresh={props.onRefresh}
        sidePanel={
          <ResourceLifecyclePanel<UserView>
            title="用户详情"
            resourceLabel="用户"
            emptyLabel="暂无用户"
            resources={data.users.map((u) => u.username)}
            fields={[{ key: 'display_name', label: '显示名称', placeholder: '输入显示名称' }, { key: 'email', label: '邮箱', placeholder: 'user@example.com' }]}
            signedIn={signedIn}
            canEdit={canEdit}
            statusOptions={[]}
            fetchDetail={fetchUser}
            updateDetail={(key, draft) => updateUser(key, draft)}
            changeStatus={() => Promise.resolve()}
            detailTitle={(d) => d.display_name || d.username}
            draftFromDetail={(d) => ({ display_name: d.display_name, email: d.email })}
            detailRows={(d) => [['账号', d.username], ['邮箱', d.email || '-'], ['角色', d.role], ['部门', d.department], ['状态', d.status], ['最近访问', d.last_seen]]}
            onChanged={props.onRefresh}
          />
        }
      />
    );
  }

  // ===================== Roles
  if (page === 'roles') {
    return (
      <DataSection
        eyebrow="RBAC"
        title="角色定义"
        icon={ShieldCheck}
        action="刷新"
        columns={['角色', '作用域', '状态', '说明']}
        rows={data.roles.map((item: RoleView) => [
          <span key={item.code}>
            <div style={{ fontWeight: 600, fontSize: 14 }}>{item.name}</div>
            <div className="text-tertiary text-xs">{item.code}</div>
          </span>,
          roleScope(item),
          <StatusBadge key={`${item.code}-st`} status={item.status} />,
          roleDescription(item) || '-'
        ])}
        loadState={loadState}
        signedIn={signedIn}
        onRefresh={props.onRefresh}
        sidePanel={
          <RoleDefinitionPanel
            roles={data.roles}
            permissions={data.permissions}
            signedIn={signedIn}
            currentUser={currentUser}
            onChanged={props.onRefresh}
          />
        }
      />
    );
  }

  // ===================== Projects
  if (page === 'projects') {
    const sensitivityOptions = [
      { value: '', label: '保持不变' }, { value: 'PUBLIC', label: 'PUBLIC' },
      { value: 'INTERNAL', label: 'INTERNAL' }, { value: 'CONFIDENTIAL', label: 'CONFIDENTIAL' },
      { value: 'STRICT', label: 'STRICT' }
    ];
    const publicModelOptions = [
      { value: '', label: '保持不变' }, { value: 'true', label: '允许公有云模型' }, { value: 'false', label: '禁用公有云模型' }
    ];
    return (
      <DataSection
        eyebrow="工作空间"
        title="项目空间"
        icon={DatabaseZap}
        action="创建项目"
        createResource="projects"
        columns={['项目', '归属部门', '负责人', '应用数', '状态']}
        rows={data.projects.map((item: ProjectView) => [item.name, item.department, item.owner, item.apps, <StatusBadge key={item.name} status={item.status} />])}
        loadState={loadState}
        signedIn={signedIn}
        canCreate={hasPermission(currentUser, 'project:create')}
        onCreate={props.onCreate}
        onRefresh={props.onRefresh}
        sidePanel={
          <>
            <ResourceLifecyclePanel<ProjectView>
              title="项目详情"
              resourceLabel="项目"
              emptyLabel="暂无项目"
              resources={data.projects.map((p) => p.name)}
              fields={[
                { key: 'name', label: '项目名称', placeholder: '输入项目名称' },
                { key: 'sensitivity_level', label: '敏感级别', kind: 'select' as const, options: sensitivityOptions },
                { key: 'allow_public_model', label: '公有云模型', kind: 'public-model' as const, options: publicModelOptions }
              ]}
              signedIn={signedIn}
              canEdit={hasPermission(currentUser, 'project:edit')}
              statusOptions={[
                hasPermission(currentUser, 'project:edit') ? { value: 'ACTIVE', label: '设为进行中', icon: Power } as StatusOption : undefined,
                hasPermission(currentUser, 'project:edit') ? { value: 'PREPARING', label: '设为规划中', icon: Pencil } as StatusOption : undefined,
                hasPermission(currentUser, 'project:archive') ? { value: 'ARCHIVED', label: '归档项目', icon: Archive } as StatusOption : undefined,
                hasPermission(currentUser, 'project:disable') ? { value: 'DISABLED', label: '停用项目', icon: Power } as StatusOption : undefined
              ].filter(Boolean) as StatusOption[]}
              fetchDetail={fetchProject}
              updateDetail={(key, draft) => updateProject(key, {
                name: draft.name,
                sensitivity_level: draft.sensitivity_level,
                allow_public_model: optionalBoolean(draft.allow_public_model)
              })}
              changeStatus={changeProjectStatus}
              detailTitle={(d) => d.name}
              draftFromDetail={(d) => ({ name: d.name })}
              detailRows={(d) => [['归属部门', d.department], ['负责人', d.owner], ['应用数', d.apps], ['状态', d.status]]}
              onChanged={props.onRefresh}
            />
            <ScopedRolePanel
              title="项目成员"
              resourceLabel="项目"
              emptyLabel="暂无项目"
              resources={data.projects.map((p) => p.name)}
              roles={['ProjectOwner', 'Tester', 'Developer', 'Auditor']}
              signedIn={signedIn}
              canManage={hasPermission(currentUser, 'project:member_manage')}
              fetchMembers={fetchProjectMembers}
              addMember={addProjectMember}
              removeMember={removeProjectMember}
              onChanged={props.onRefresh}
            />
          </>
        }
      />
    );
  }

  // ===================== Applications
  if (page === 'applications') {
    const sensitivityOptions = [
      { value: '', label: '保持不变' }, { value: 'PUBLIC', label: 'PUBLIC' },
      { value: 'INTERNAL', label: 'INTERNAL' }, { value: 'CONFIDENTIAL', label: 'CONFIDENTIAL' },
      { value: 'STRICT', label: 'STRICT' }
    ];
    const publicModelOptions = [
      { value: '', label: '保持不变' }, { value: 'true', label: '允许公有云模型' }, { value: 'false', label: '禁用公有云模型' }
    ];
    const appTypeOptions = [
      { value: '', label: '保持不变' }, { value: 'Web', label: 'Web' }, { value: 'Backend', label: 'Backend' },
      { value: 'Frontend', label: 'Frontend' }, { value: 'Mobile', label: 'Mobile' }, { value: 'Service', label: 'Service' }, { value: 'API', label: 'API' }
    ];
    return (
      <DataSection
        eyebrow="应用管理"
        title="应用清单"
        icon={AppWindow}
        action="登记应用"
        createResource="applications"
        columns={['应用', '类型', '负责团队', '版本', '状态']}
        rows={data.applications.map((item: ApplicationView) => [item.name, item.type, item.owner, item.version, <StatusBadge key={item.name} status={item.status} />])}
        loadState={loadState}
        signedIn={signedIn}
        canCreate={hasPermission(currentUser, 'application:create')}
        onCreate={props.onCreate}
        onRefresh={props.onRefresh}
        sidePanel={
          <>
            <ResourceLifecyclePanel<ApplicationView>
              title="应用详情"
              resourceLabel="应用"
              emptyLabel="暂无应用"
              resources={data.applications.map((a) => a.name)}
              fields={[
                { key: 'name', label: '应用名称', placeholder: '输入应用名称' },
                { key: 'app_type', label: '应用类型', kind: 'select' as const, options: appTypeOptions },
                { key: 'default_web_url', label: 'Web URL', placeholder: 'https://web.example.test' },
                { key: 'default_api_base_url', label: 'API Base URL', placeholder: 'https://api.example.test' },
                { key: 'sensitivity_level', label: '敏感级别', kind: 'select' as const, options: sensitivityOptions },
                { key: 'allow_public_model', label: '公有云模型', kind: 'public-model' as const, options: publicModelOptions }
              ]}
              signedIn={signedIn}
              canEdit={hasPermission(currentUser, 'application:edit')}
              statusOptions={[
                hasPermission(currentUser, 'application:edit') ? { value: 'ENABLED', label: '启用应用', icon: Power } as StatusOption : undefined,
                hasPermission(currentUser, 'application:disable') ? { value: 'DISABLED', label: '停用应用', icon: Power } as StatusOption : undefined,
              ].filter(Boolean) as StatusOption[]}
              fetchDetail={fetchApplication}
              updateDetail={(key, draft) => updateApplication(key, {
                name: draft.name, app_type: draft.app_type, default_web_url: draft.default_web_url,
                default_api_base_url: draft.default_api_base_url, sensitivity_level: draft.sensitivity_level,
                allow_public_model: optionalBoolean(draft.allow_public_model)
              })}
              changeStatus={changeApplicationStatus}
              detailTitle={(d) => d.name}
              draftFromDetail={(d) => ({ name: d.name, app_type: d.type })}
              detailRows={(d) => [['类型', d.type], ['负责团队', d.owner], ['版本', d.version], ['状态', d.status]]}
              onChanged={props.onRefresh}
            />
            <ScopedRolePanel
              title="应用负责人"
              resourceLabel="应用"
              emptyLabel="暂无应用"
              resources={data.applications.map((a) => a.name)}
              roles={['AppOwner']}
              signedIn={signedIn}
              canManage={hasPermission(currentUser, 'application:owner_manage')}
              fetchMembers={fetchApplicationOwners}
              addMember={(rk, un) => addApplicationOwner(rk, un)}
              removeMember={removeApplicationOwner}
              onChanged={props.onRefresh}
            />
          </>
        }
      />
    );
  }

  // ===================== Environments
  if (page === 'environments') {
    const envTypeOptions = [
      { value: '', label: '保持不变' }, { value: 'DEV', label: 'DEV' }, { value: 'TEST', label: 'TEST' },
      { value: 'STAGING', label: 'STAGING' }, { value: 'PREPROD', label: 'PREPROD' }, { value: 'PROD', label: 'PROD' }
    ];
    return (
      <DataSection
        eyebrow="环境管理"
        title="环境配置"
        icon={ServerCog}
        action="新增环境"
        createResource="environments"
        columns={['环境', '集群', 'Endpoint', '状态']}
        rows={data.environments.map((item: EnvironmentView) => [item.name, item.cluster, item.endpoint, <StatusBadge key={item.name} status={item.status} />])}
        loadState={loadState}
        signedIn={signedIn}
        canCreate={hasPermission(currentUser, 'environment:create')}
        onCreate={props.onCreate}
        onRefresh={props.onRefresh}
        sidePanel={
          <>
            <ResourceLifecyclePanel<EnvironmentView>
              title="环境详情"
              resourceLabel="环境"
              emptyLabel="暂无环境"
              resources={data.environments.map((e) => e.name)}
              fields={[
                { key: 'name', label: '环境名称', placeholder: '输入环境名称' },
                { key: 'env_type', label: '环境类型', kind: 'select' as const, options: envTypeOptions },
                { key: 'web_url', label: 'Web URL', placeholder: 'https://web.env.test' },
                { key: 'api_base_url', label: 'API Base URL', placeholder: 'https://api.env.test' }
              ]}
              signedIn={signedIn}
              canEdit={hasPermission(currentUser, 'environment:edit')}
              statusOptions={[
                hasPermission(currentUser, 'environment:edit') ? { value: 'ENABLED', label: '启用环境', icon: Power } as StatusOption : undefined,
                hasPermission(currentUser, 'environment:disable') ? { value: 'DISABLED', label: '停用环境', icon: Power } as StatusOption : undefined,
              ].filter(Boolean) as StatusOption[]}
              fetchDetail={fetchEnvironment}
              updateDetail={(key, draft) => updateEnvironment(key, {
                name: draft.name, env_type: draft.env_type, web_url: draft.web_url, api_base_url: draft.api_base_url
              })}
              changeStatus={changeEnvironmentStatus}
              detailTitle={(d) => d.name}
              draftFromDetail={(d) => ({ name: d.name, api_base_url: d.endpoint })}
              detailRows={(d) => [['集群', d.cluster], ['Endpoint', d.endpoint], ['状态', d.status]]}
              onChanged={props.onRefresh}
            />
            <EnvironmentConnectivityPanel
              resources={data.environments.map((e) => e.name)}
              signedIn={signedIn}
              canRun={hasPermission(currentUser, 'environment:edit')}
              onChanged={props.onRefresh}
            />
            <ScopedRolePanel
              title="环境授权用户"
              resourceLabel="环境"
              emptyLabel="暂无环境"
              resources={data.environments.map((e) => e.name)}
              roles={['Developer', 'Tester']}
              signedIn={signedIn}
              canManage={hasPermission(currentUser, 'environment:user_manage')}
              fetchMembers={fetchEnvironmentUsers}
              addMember={addEnvironmentUser}
              removeMember={removeEnvironmentUser}
              onChanged={props.onRefresh}
            />
          </>
        }
      />
    );
  }

  // ===================== Integrations
  if (page === 'integrations') {
    return (
      <DataSection
        eyebrow="集成管理"
        title="集成配置"
        icon={Link2}
        action="新增集成"
        createResource="integrations"
        columns={['集成', '类别', '范围', '状态']}
        rows={data.integrations.map((item: IntegrationView) => [item.name, item.category, item.scope, <StatusBadge key={item.name} status={item.status} />])}
        loadState={loadState}
        signedIn={signedIn}
        canCreate={hasPermission(currentUser, 'config:edit')}
        onCreate={props.onCreate}
        onRefresh={props.onRefresh}
        sidePanel={
          <ResourceLifecyclePanel<IntegrationView>
            title="集成详情"
            resourceLabel="集成"
            emptyLabel="暂无集成"
            resources={data.integrations.map((i) => i.name)}
            fields={[
              { key: 'name', label: '集成名称', placeholder: '输入集成名称' },
              { key: 'category', label: '类别', placeholder: '代码仓库 / CI/CD / 通知' },
              { key: 'scope', label: '范围', placeholder: '全局 / 平台级 / 项目级' }
            ]}
            signedIn={signedIn}
            canEdit={hasPermission(currentUser, 'config:edit')}
            statusOptions={[
              hasPermission(currentUser, 'config:edit') ? { value: 'ENABLED', label: '启用集成', icon: Power } as StatusOption : undefined,
              hasPermission(currentUser, 'config:edit') ? { value: 'DISABLED', label: '停用集成', icon: Power } as StatusOption : undefined,
            ].filter(Boolean) as StatusOption[]}
            fetchDetail={fetchIntegration}
            updateDetail={(key, draft) => updateIntegration(key, {
              name: draft.name, category: draft.category, scope: draft.scope
            })}
            changeStatus={changeIntegrationStatus}
            detailTitle={(d) => d.name}
            draftFromDetail={(d) => ({ name: d.name, category: d.category, scope: d.scope })}
            detailRows={(d) => [['类别', d.category], ['范围', d.scope], ['状态', d.status]]}
            onChanged={props.onRefresh}
          />
        }
      />
    );
  }

  // ===================== Audit
  if (page === 'audit') {
    return <AuditPage {...props} />;
  }

  // ===================== Settings
  if (page === 'settings') {
    return (
      <DataSection
        eyebrow="系统配置"
        title="系统设置"
        icon={Settings}
        action="刷新"
        columns={['配置项', '键', '值', '状态']}
        rows={data.settings.map((item: SettingView) => [item.name, item.key, item.value, <StatusBadge key={item.key} status={item.status} />])}
        loadState={loadState}
        signedIn={signedIn}
        onRefresh={props.onRefresh}
        sidePanel={
          <ResourceLifecyclePanel<SettingView>
            title="设置详情"
            resourceLabel="设置"
            emptyLabel="暂无设置"
            resources={data.settings.map((s) => s.key)}
            fields={[{ key: 'value', label: '值', placeholder: '输入新值' }]}
            signedIn={signedIn}
            canEdit={hasPermission(currentUser, 'config:edit')}
            statusOptions={[
              hasPermission(currentUser, 'config:edit') ? { value: 'ENABLED', label: '启用', icon: Power } as StatusOption : undefined,
              hasPermission(currentUser, 'config:edit') ? { value: 'DISABLED', label: '停用', icon: Power } as StatusOption : undefined,
            ].filter(Boolean) as StatusOption[]}
            fetchDetail={fetchSetting}
            updateDetail={(key, draft) => updateSetting(key, { value: draft.value })}
            changeStatus={changeSettingStatus}
            detailTitle={(d) => d.name}
            draftFromDetail={(d) => ({ value: d.value })}
            detailRows={(d) => [['键', d.key], ['值', d.value], ['状态', d.status]]}
            onChanged={props.onRefresh}
          />
        }
      />
    );
  }

  return null;
}

function StatusBadge({ status }: { status: string }) {
  const map: Record<string, string> = {
    '同步正常': 'badge-success',
    '已连接': 'badge-success',
    'ENABLED': 'badge-success',
    'ACTIVE': 'badge-success',
    '进行中': 'badge-success',
    '已启用': 'badge-success',
    '解锁': 'badge-success',
    '已解锁': 'badge-success',
    '正常': 'badge-success',
    'DISABLED': 'badge-warning',
    '停用': 'badge-danger',
    '已停用': 'badge-danger',
    '锁定': 'badge-danger',
    '已锁定': 'badge-danger',
    'PREPARING': 'badge-warning',
    'ARCHIVED': 'badge-neutral',
    '规划中': 'badge-warning',
    'DISCONNECTED': 'badge-warning',
    'DRAFT': 'badge-warning'
  };
  const cls = map[status] || 'badge-neutral';
  return <span className={`badge ${cls}`}>{status}</span>;
}

/* ===================== DataSection (table + create + side panel) ===================== */

interface DataSectionProps {
  eyebrow: string;
  title: string;
  icon: LucideIcon;
  action: string;
  createResource?: CreatableManagementResource;
  columns: string[];
  rows: (string | number | React.ReactNode)[][];
  loadState: { loading: boolean; error?: string };
  signedIn: boolean;
  canCreate?: boolean;
  onCreate?: (resource: CreatableManagementResource, label: string, name: string) => Promise<void>;
  onRefresh?: () => void;
  sidePanel?: React.ReactNode;
}

function DataSection(props: DataSectionProps) {
  const [quickCreateValue, setQuickCreateValue] = useState('');
  const Icon = props.icon;

  return (
    <div className="content-grid">
      <div className="panel">
        <div className="panel-header">
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 4 }}>
              <div className="section-icon" style={{ width: 32, height: 32 }}><Icon size={17} /></div>
              <div>
                <div className="text-tertiary text-xs font-semibold" style={{ marginBottom: 2 }}>{props.eyebrow}</div>
                <h2 className="panel-title">{props.title}</h2>
              </div>
            </div>
          </div>
          <div className="toolbar-actions management-toolbar-actions">
            {props.canCreate && props.onCreate && (
              <div className="management-quick-create">
                <input
                  type="text"
                  placeholder={`输入${props.action.replace('新增', '').replace('创建', '').replace('邀请', '') || '名称'}`}
                  value={quickCreateValue}
                  onChange={(e) => setQuickCreateValue(e.target.value)}
                  disabled={!props.signedIn}
                />
                <button
                  className="btn btn-primary btn-sm"
                  disabled={!props.signedIn || props.loadState.loading || !quickCreateValue.trim()}
                  onClick={() => { props.onCreate!(props.createResource!, props.action, quickCreateValue); setQuickCreateValue(''); }}
                >
                  {props.action}
                </button>
              </div>
            )}
            {props.onRefresh && (
              <button className="btn btn-secondary btn-sm" onClick={props.onRefresh} disabled={props.loadState.loading}>
                刷新
              </button>
            )}
          </div>
        </div>
        <div className="panel-body">
          {props.loadState.error && (
            <div className="notice error" style={{ marginBottom: 16 }}>{props.loadState.error}</div>
          )}
          {props.rows.length === 0 && !props.loadState.loading ? (
            <div className="empty-state">
              <div className="empty-state-icon"><Icon size={32} opacity={0.4} /></div>
              <strong>暂无数据</strong>
              <span>当前没有{props.title}，请先创建或同步数据。</span>
            </div>
          ) : (
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>{props.columns.map((col) => <th key={col}>{col}</th>)}</tr>
                </thead>
                <tbody>
                  {props.loadState.loading ? (
                    <tr><td colSpan={props.columns.length}><div className="skeleton skeleton-text" style={{ margin: '8px 0' }} /></td></tr>
                  ) : (
                    props.rows.map((row, idx) => (
                      <tr key={idx}>{row.map((cell, ci) => <td key={ci}>{cell}</td>)}</tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </div>

      {props.sidePanel && (
        <div className="side-stack">
          {props.sidePanel}
        </div>
      )}
    </div>
  );
}

/* ===================== Resource Lifecycle Panel ===================== */

interface StatusOption {
  value: string;
  label: string;
  icon: LucideIcon;
}

interface ResourceLifecyclePanelProps<T> {
  title: string;
  resourceLabel: string;
  emptyLabel: string;
  resources: string[];
  fields: { key: string; label: string; placeholder?: string; kind?: 'text' | 'select' | 'public-model'; options?: Array<{ value: string; label: string }> }[];
  signedIn: boolean;
  canEdit: boolean;
  statusOptions: StatusOption[];
  fetchDetail: (key: string) => Promise<{ data: T }>;
  updateDetail: (resourceKey: string, draft: Record<string, string>) => Promise<unknown>;
  changeStatus: (resourceKey: string, status: string) => Promise<unknown>;
  detailTitle: (detail: T) => string;
  draftFromDetail: (detail: T) => Record<string, string>;
  detailRows: (detail: T) => Array<[string, string | number | React.ReactNode | null | undefined]>;
  onChanged: () => void;
}

function ResourceLifecyclePanel<T extends object>(props: ResourceLifecyclePanelProps<T>) {
  const [selectedKey, setSelectedKey] = useState('');
  const [detail, setDetail] = useState<T | null>(null);
  const [editDraft, setEditDraft] = useState<Record<string, string> | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!selectedKey) { setDetail(null); setEditDraft(null); return; }
    setLoading(true);
    setError('');
    props.fetchDetail(selectedKey)
      .then((r) => { setDetail(r.data); setEditDraft(props.draftFromDetail(r.data)); setLoading(false); })
      .catch((err: unknown) => { setError(err instanceof Error ? err.message : '加载失败'); setLoading(false); });
  }, [selectedKey, props]);

  async function save() {
    if (!selectedKey || !editDraft) return;
    setLoading(true);
    setError('');
    try {
      await props.updateDetail(selectedKey, editDraft);
      setDetail(null); setEditDraft(null); setSelectedKey('');
      props.onChanged();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : '保存失败');
      setLoading(false);
    }
  }

  async function changeStatus(status: string) {
    if (!selectedKey) return;
    setLoading(true);
    setError('');
    try {
      await props.changeStatus(selectedKey, status);
      setDetail(null); setEditDraft(null); setSelectedKey('');
      props.onChanged();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : '状态变更失败');
      setLoading(false);
    }
  }

  return (
    <div className="panel">
      <div className="panel-body">
        <div className="management-side-heading">
          <div className="text-tertiary text-xs font-semibold">详情</div>
          <h3 className="panel-title">{props.title}</h3>
        </div>

        <div className="field management-select-field">
          <select
            value={selectedKey}
            onChange={(e) => setSelectedKey(e.target.value)}
            disabled={!props.signedIn}
          >
            <option value="">选择{props.resourceLabel}...</option>
            {props.resources.map((r) => (
              <option key={r} value={r}>{r}</option>
            ))}
          </select>
        </div>

        {loading && <div className="skeleton skeleton-text" />}
        {error && <div className="notice error" style={{ marginBottom: 12 }}>{error}</div>}

        {detail && !loading && (
          <>
            <div className="divider" />
            <div className="detail-grid">
              {props.detailRows(detail).map(([label, value]) => (
                <div className="detail-row" key={label}>
                  <span className="detail-label">{label}</span>
                  <span className="detail-value">{value || '-'}</span>
                </div>
              ))}
            </div>

            {props.canEdit && editDraft && (
              <>
                <div className="divider" />
                <div className="management-edit-form">
                  {props.fields.map((field) => (
                    <div className="field" key={field.key}>
                      <label className="field-label">{field.label}</label>
                      {field.kind === 'select' || field.kind === 'public-model' ? (
                        <select
                          value={editDraft[field.key] ?? ''}
                          onChange={(e) => setEditDraft((d) => d ? { ...d, [field.key]: e.target.value } : d)}
                        >
                          {(field.options ?? []).map((opt) => (
                            <option key={opt.value} value={opt.value}>{opt.label}</option>
                          ))}
                        </select>
                      ) : (
                        <input
                          type="text"
                          value={editDraft[field.key] ?? ''}
                          onChange={(e) => setEditDraft((d) => d ? { ...d, [field.key]: e.target.value } : d)}
                          placeholder={field.placeholder}
                        />
                      )}
                    </div>
                  ))}
                  <button className="btn btn-primary btn-sm" onClick={save} disabled={loading}>
                    {loading ? '保存中...' : '保存修改'}
                  </button>
                </div>
              </>
            )}

            {props.statusOptions.length > 0 && (
              <>
                <div className="divider" />
                <div className="management-status-actions">
                  {props.statusOptions.map((opt) => {
                    const OptIcon = opt.icon;
                    return (
                      <button key={opt.value} className="btn btn-secondary btn-sm" onClick={() => changeStatus(opt.value)} disabled={loading}>
                        <OptIcon size={14} />{opt.label}
                      </button>
                    );
                  })}
                </div>
              </>
            )}
          </>
        )}
      </div>
    </div>
  );
}

/* ===================== Role Binding Controls ===================== */

function RoleBindingControls(props: {
  username: string;
  roles: RoleView[];
  loading: boolean;
  signedIn: boolean;
  canAssign: boolean;
  canUnassign: boolean;
  onAction: (username: string, action: UserLifecycleAction, roleCode?: string) => Promise<void>;
}) {
  const [selectedRole, setSelectedRole] = useState('');
  if (!props.canAssign && !props.canUnassign) return null;
  return (
    <div className="role-binding-controls">
      {props.canAssign && (
        <>
          <select
            value={selectedRole}
            onChange={(e) => setSelectedRole(e.target.value)}
            disabled={!props.signedIn}
          >
            <option value="">角色</option>
            {props.roles.map((r) => <option key={r.code} value={r.code}>{r.name}</option>)}
          </select>
          <button
            className="btn btn-xs btn-secondary"
            disabled={!props.signedIn || props.loading || !selectedRole}
            onClick={() => { props.onAction(props.username, 'assign-role', selectedRole); setSelectedRole(''); }}
          >
            分配
          </button>
        </>
      )}
      {props.canUnassign && selectedRole && (
        <button
          className="btn btn-xs btn-secondary"
          disabled={!props.signedIn || props.loading}
          onClick={() => { props.onAction(props.username, 'unassign-role', selectedRole); setSelectedRole(''); }}
        >
          解绑
        </button>
      )}
    </div>
  );
}

/* ===================== Scoped Role Panel ===================== */

type ScopedMemberView = ScopedUserRoleView | ProjectMemberView;
type ScopedMembersPayload = ScopedMemberView[] | ManagementPageResponse<ScopedMemberView>;

function scopedMembers(payload: ScopedMembersPayload): ScopedMemberView[] {
  return Array.isArray(payload) ? payload : payload.items;
}

function ScopedRolePanel(props: {
  title: string;
  resourceLabel: string;
  emptyLabel: string;
  resources: string[];
  roles: string[];
  signedIn: boolean;
  canManage: boolean;
  fetchMembers: (key: string) => Promise<{ data: ScopedMembersPayload }>;
  addMember: (resourceKey: string, username: string, roleCode: string) => Promise<unknown>;
  removeMember: (resourceKey: string, username: string) => Promise<unknown>;
  onChanged: () => void;
}) {
  const [selectedKey, setSelectedKey] = useState('');
  const [members, setMembers] = useState<ScopedMemberView[]>([]);
  const [username, setUsername] = useState('');
  const [roleCode, setRoleCode] = useState(props.roles[0] ?? '');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!selectedKey) { setMembers([]); return; }
    setLoading(true);
    setError('');
    props.fetchMembers(selectedKey)
      .then((r) => { setMembers(scopedMembers(r.data)); setLoading(false); })
      .catch((err: unknown) => { setError(err instanceof Error ? err.message : '加载失败'); setLoading(false); });
  }, [selectedKey, props]);

  async function add() {
    if (!selectedKey || !username.trim()) return;
    setLoading(true);
    setError('');
    try {
      await props.addMember(selectedKey, username.trim(), roleCode || props.roles[0] || '');
      setUsername('');
      props.onChanged();
      const r = await props.fetchMembers(selectedKey);
      setMembers(scopedMembers(r.data));
      setLoading(false);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : '添加失败');
      setLoading(false);
    }
  }

  async function remove(username: string) {
    if (!selectedKey) return;
    setLoading(true);
    setError('');
    try {
      await props.removeMember(selectedKey, username);
      props.onChanged();
      const r = await props.fetchMembers(selectedKey);
      setMembers(scopedMembers(r.data));
      setLoading(false);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : '移除失败');
      setLoading(false);
    }
  }

  return (
    <div className="panel">
      <div className="panel-body">
        <div style={{ marginBottom: 12 }}>
          <div className="text-tertiary text-xs font-semibold" style={{ marginBottom: 2 }}>成员</div>
          <h3 className="panel-title" style={{ fontSize: 15 }}>{props.title}</h3>
        </div>

        <div className="field" style={{ marginBottom: 12 }}>
          <select
            value={selectedKey}
            onChange={(e) => setSelectedKey(e.target.value)}
            disabled={!props.signedIn}
            style={{ width: '100%', minHeight: 38, border: '1px solid var(--border)', borderRadius: 'var(--radius-sm)', padding: '0 10px', fontSize: 13 }}
          >
            <option value="">选择{props.resourceLabel}...</option>
            {props.resources.map((r) => <option key={r} value={r}>{r}</option>)}
          </select>
        </div>

        {error && <div className="notice error" style={{ marginBottom: 12 }}>{error}</div>}

        {selectedKey && (
          <>
            {loading ? (
              <div className="skeleton skeleton-text" />
            ) : members.length === 0 ? (
              <div className="empty-state" style={{ padding: '12px 0' }}>
                <span>暂无成员</span>
              </div>
            ) : (
              <div style={{ display: 'grid', gap: 8 }}>
                {members.map((m) => (
                  <div key={m.username} style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 8, padding: '6px 0', borderBottom: '1px solid var(--border-light)' }}>
                    <div>
                      <div style={{ fontSize: 14, fontWeight: 600 }}>{m.display_name || m.username}</div>
                      <div className="text-tertiary text-xs">{m.role}</div>
                    </div>
                    {props.canManage && (
                      <button className="btn btn-xs btn-secondary" onClick={() => remove(m.username)} disabled={loading}>移除</button>
                    )}
                  </div>
                ))}
              </div>
            )}

            {props.canManage && (
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 110px auto', gap: 6, marginTop: 12 }}>
                <input
                  type="text"
                  placeholder="输入用户名"
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  style={{ flex: 1, minHeight: 34, border: '1px solid var(--border)', borderRadius: 'var(--radius-sm)', padding: '0 10px', fontSize: 13 }}
                />
                <select
                  value={roleCode}
                  onChange={(e) => setRoleCode(e.target.value)}
                  style={{ minHeight: 34, border: '1px solid var(--border)', borderRadius: 'var(--radius-sm)', padding: '0 8px', fontSize: 13 }}
                >
                  {props.roles.map((role) => <option key={role} value={role}>{role}</option>)}
                </select>
                <button className="btn btn-primary btn-sm" onClick={add} disabled={loading || !username.trim()}>添加</button>
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
}

/* ===================== Environment Connectivity Panel ===================== */

function EnvironmentConnectivityPanel(props: {
  resources: string[];
  signedIn: boolean;
  canRun: boolean;
  onChanged: () => void;
}) {
  const [selectedKey, setSelectedKey] = useState('');
  const [result, setResult] = useState<EnvironmentConnectivityCheckView | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => { setResult(null); setError(''); }, [selectedKey]);

  async function check() {
    if (!selectedKey) return;
    setLoading(true);
    setError('');
    try {
      const r = await runEnvironmentConnectivityCheck(selectedKey);
      setResult(r.data);
      setLoading(false);
      props.onChanged();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : '连通性检查失败');
      setLoading(false);
    }
  }

  return (
    <div className="panel">
      <div className="panel-body">
        <div style={{ marginBottom: 12 }}>
          <div className="text-tertiary text-xs font-semibold" style={{ marginBottom: 2 }}>连通性</div>
          <h3 className="panel-title" style={{ fontSize: 15 }}>环境连通性检查</h3>
        </div>

        <div className="field" style={{ marginBottom: 12 }}>
          <select
            value={selectedKey}
            onChange={(e) => setSelectedKey(e.target.value)}
            disabled={!props.signedIn}
            style={{ width: '100%', minHeight: 38, border: '1px solid var(--border)', borderRadius: 'var(--radius-sm)', padding: '0 10px', fontSize: 13 }}
          >
            <option value="">选择环境...</option>
            {props.resources.map((r) => <option key={r} value={r}>{r}</option>)}
          </select>
        </div>

        {props.canRun && selectedKey && (
          <button className="btn btn-primary btn-sm" onClick={check} disabled={loading} style={{ width: '100%', marginBottom: 12 }}>
            {loading ? '检查中...' : '执行连通性检查'}
          </button>
        )}

        {error && <div className="notice error" style={{ marginBottom: 12 }}>{error}</div>}

        {result && (
          <div style={{ padding: 12, background: 'var(--bg)', borderRadius: 'var(--radius-sm)', display: 'grid', gap: 8 }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 8 }}>
              <StatusBadge status={result.status} />
              <span className="text-tertiary text-xs">{result.latencyMs ?? '-'}ms</span>
            </div>
            <p className="text-secondary text-sm" style={{ lineHeight: 1.5, overflowWrap: 'anywhere' }}>{result.message}</p>
          </div>
        )}
      </div>
    </div>
  );
}

/* ===================== Role Definition Panel ===================== */

function RoleDefinitionPanel(props: {
  roles: RoleView[];
  permissions: PermissionView[];
  signedIn: boolean;
  currentUser: CurrentUser | null;
  onChanged: () => void;
}) {
  const [selectedCode, setSelectedCode] = useState('');
  const [detail, setDetail] = useState<RoleDetailView | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const rolePermissions = detail?.permissionCodes ?? detail?.permission_codes ?? [];

  useEffect(() => {
    if (!selectedCode) { setDetail(null); return; }
    setLoading(true);
    setError('');
    fetchRole(selectedCode)
      .then((r) => { setDetail(r.data); setLoading(false); })
      .catch((err: unknown) => { setError(err instanceof Error ? err.message : '加载失败'); setLoading(false); });
  }, [selectedCode]);

  return (
    <div className="panel">
      <div className="panel-body">
        <div style={{ marginBottom: 12 }}>
          <div className="text-tertiary text-xs font-semibold" style={{ marginBottom: 2 }}>角色定义</div>
          <h3 className="panel-title" style={{ fontSize: 15 }}>角色详情</h3>
        </div>

        <div className="field" style={{ marginBottom: 12 }}>
          <select
            value={selectedCode}
            onChange={(e) => setSelectedCode(e.target.value)}
            disabled={!props.signedIn}
            style={{ width: '100%', minHeight: 38, border: '1px solid var(--border)', borderRadius: 'var(--radius-sm)', padding: '0 10px', fontSize: 13 }}
          >
            <option value="">选择角色...</option>
            {props.roles.map((r) => <option key={r.code} value={r.code}>{r.name} ({r.code})</option>)}
          </select>
        </div>

        {loading && <div className="skeleton skeleton-text" />}
        {error && <div className="notice error" style={{ marginBottom: 12 }}>{error}</div>}

        {detail && !loading && (
          <>
            <div className="divider" />
            <div className="detail-grid">
              <div className="detail-row">
                <span className="detail-label">名称</span>
                <span className="detail-value">{detail.name}</span>
              </div>
              <div className="detail-row">
                <span className="detail-label">编码</span>
                <span className="detail-value">{detail.code}</span>
              </div>
              <div className="detail-row">
                <span className="detail-label">状态</span>
                <span className="detail-value"><StatusBadge status={detail.status} /></span>
              </div>
              {detail.description && (
                <div className="detail-row">
                  <span className="detail-label">说明</span>
                  <span className="detail-value">{detail.description}</span>
                </div>
              )}
            </div>

            <div className="divider" />
            <div className="text-tertiary text-xs font-semibold" style={{ marginBottom: 8 }}>权限点 ({rolePermissions.length})</div>
            {rolePermissions.length > 0 ? (
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: 4 }}>
                {rolePermissions.map((p: string) => (
                  <span key={p} className="badge badge-info">{p}</span>
                ))}
              </div>
            ) : (
              <div className="text-tertiary text-sm">暂无权限点</div>
            )}
          </>
        )}
      </div>
    </div>
  );
}

/* ===================== Audit Page ===================== */

function AuditPage(props: ManagementPageProps) {
  const { data, loadState, signedIn, currentUser, auditExportState, onAuditExport } = props;

  const canExport = canUseButton(currentUser, 'audit:export');
  const canViewOutbox = hasPermission(currentUser, 'audit:read');

  return (
    <div className="content-grid">
      <div className="panel">
        <div className="panel-header">
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 4 }}>
              <div className="section-icon" style={{ width: 32, height: 32 }}><ScrollText size={17} /></div>
              <div>
                <div className="text-tertiary text-xs font-semibold" style={{ marginBottom: 2 }}>审计</div>
                <h2 className="panel-title">审计日志</h2>
              </div>
            </div>
          </div>
          <div className="toolbar-actions">
            <button className="btn btn-secondary btn-sm" onClick={onAuditExport} disabled={!signedIn || auditExportState.loading || !canExport}>
              {auditExportState.loading ? '导出中...' : '导出 CSV'}
            </button>
            <button className="btn btn-secondary btn-sm" onClick={props.onRefresh} disabled={loadState.loading}>刷新</button>
          </div>
        </div>
        <div className="panel-body">
          {loadState.error && <div className="notice error" style={{ marginBottom: 16 }}>{loadState.error}</div>}
          {data.auditLogs.length === 0 && !loadState.loading ? (
            <div className="empty-state">
              <div className="empty-state-icon"><ScrollText size={32} opacity={0.4} /></div>
              <strong>暂无审计日志</strong>
              <span>系统操作日志将在此处显示。</span>
            </div>
          ) : (
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>时间</th>
                    <th>操作人</th>
                    <th>动作</th>
                    <th>资源</th>
                    <th>结果</th>
                    <th>详情</th>
                  </tr>
                </thead>
                <tbody>
                  {loadState.loading ? (
                    <tr><td colSpan={6}><div className="skeleton skeleton-text" style={{ margin: '8px 0' }} /></td></tr>
                  ) : (
                    data.auditLogs.map((log: AuditLogView, idx: number) => (
                      <tr key={idx}>
                        <td className="text-sm">{log.time}</td>
                        <td>{log.actor}</td>
                        <td>{log.action}</td>
                        <td>
                          <div style={{ fontWeight: 600 }}>{log.target}</div>
                        </td>
                        <td><StatusBadge status={log.result} /></td>
                        <td className="text-sm text-secondary" style={{ maxWidth: 200, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{log.target || '-'}</td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </div>

      <div className="side-stack">
        {canViewOutbox && <AuditOutboxPanel {...props} />}
      </div>
    </div>
  );
}

/* ===================== Audit Outbox Panel ===================== */

function AuditOutboxPanel(props: ManagementPageProps) {
  return (
    <div className="panel">
      <div className="panel-body">
        <div style={{ marginBottom: 12 }}>
          <div className="text-tertiary text-xs font-semibold" style={{ marginBottom: 2 }}>待处理</div>
          <h3 className="panel-title" style={{ fontSize: 15 }}>Audit Outbox</h3>
        </div>

        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8, marginBottom: 12 }}>
          <select
            value={props.auditOutboxFilters.status}
            onChange={(e) => props.onAuditOutboxFiltersChange({ ...props.auditOutboxFilters, status: e.target.value })}
            style={{ width: '100%', minHeight: 34, border: '1px solid var(--border)', borderRadius: 'var(--radius-sm)', padding: '0 8px', fontSize: 12 }}
          >
            <option value="">全部状态</option>
            <option value="PENDING">待处理</option>
            <option value="SUCCESS">成功</option>
            <option value="FAILED">失败</option>
          </select>
          <input
            type="text" placeholder="Trace ID"
            value={props.auditOutboxFilters.traceId}
            onChange={(e) => props.onAuditOutboxFiltersChange({ ...props.auditOutboxFilters, traceId: e.target.value })}
            style={{ width: '100%', minHeight: 34, border: '1px solid var(--border)', borderRadius: 'var(--radius-sm)', padding: '0 8px', fontSize: 12 }}
          />
        </div>
        <input
          type="text" placeholder="搜索资源 ID..."
          value={props.auditOutboxFilters.search}
          onChange={(e) => props.onAuditOutboxFiltersChange({ ...props.auditOutboxFilters, search: e.target.value })}
          style={{ width: '100%', minHeight: 34, border: '1px solid var(--border)', borderRadius: 'var(--radius-sm)', padding: '0 10px', fontSize: 13, marginBottom: 12 }}
        />

        <button className="btn btn-secondary btn-sm" onClick={() => props.onAuditOutboxRefresh()} disabled={props.auditOutboxLoad.loading} style={{ width: '100%', marginBottom: 12 }}>
          {props.auditOutboxLoad.loading ? '加载中...' : '查询'}
        </button>

        {props.auditOutboxLoad.error && (
          <div className="notice error" style={{ marginBottom: 12 }}>{props.auditOutboxLoad.error}</div>
        )}

        <div style={{ display: 'grid', gap: 8 }}>
          {props.data.auditOutbox.length === 0 && !props.auditOutboxLoad.loading ? (
            <div className="text-tertiary text-sm">暂无待处理事件</div>
          ) : (
            props.data.auditOutbox.map((item: AuditOutboxView, idx: number) => (
              <div key={idx} style={{ padding: '8px 10px', border: '1px solid var(--border-light)', borderRadius: 'var(--radius-sm)' }}>
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 8 }}>
                  <strong style={{ fontSize: 13 }}>{item.eventAction}</strong>
                  <StatusBadge status={item.status} />
                </div>
                <div className="text-tertiary text-xs" style={{ marginTop: 4 }}>
                  {item.resourceType}/{item.resourceId}
                </div>
                {item.lastError && (
                  <div className="text-xs" style={{ color: 'var(--danger)', marginTop: 4 }}>{item.lastError}</div>
                )}
              </div>
            ))
          )}
        </div>
      </div>
    </div>
  );
}

function roleScope(role: RoleView): string {
  if (role.scope_type) {
    return role.scope_type.charAt(0).toUpperCase() + role.scope_type.slice(1).toLowerCase();
  }
  return 'Platform';
}

function roleDescription(role: RoleView): string {
  return role.description || '';
}
