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
import { dictionaryLabel } from '../platform/dictionaries';
import { translate } from '../platform/i18n';
import { NativeSelect } from './ui';

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
        eyebrow={translate('auto.k0229')}
        title={translate('auto.k0230')}
        icon={GitBranch}
        action={translate('auto.k0231')}
        createResource="departments"
        columns={[translate('auto.k0232'), translate('auto.k0233'), translate('auto.k0234'), translate('auto.k0235'), translate('auto.k0182')]}
        rows={data.departments.map((d: DepartmentView) => [d.name, d.parent, d.lead, d.members, d.status])}
        loadState={loadState}
        signedIn={signedIn}
        canCreate={hasPermission(currentUser, 'department:create')}
        onCreate={props.onCreate}
        onRefresh={props.onRefresh}
        sidePanel={
          <ResourceLifecyclePanel<DepartmentView>
            title={translate('auto.k0236')}
            resourceLabel={translate('auto.k0232')}
            emptyLabel={translate('auto.k0237')}
            resources={data.departments.map((d) => d.name)}
            fields={[{ key: 'name', label: translate('auto.k0238'), placeholder: translate('auto.k0239') }]}
            signedIn={signedIn}
            canEdit={hasPermission(currentUser, 'department:edit')}
            statusOptions={[
              hasPermission(currentUser, 'department:enable') ? { value: 'ENABLED', label: translate('auto.k0240'), icon: Power } as StatusOption : undefined,
              hasPermission(currentUser, 'department:disable') ? { value: 'DISABLED', label: translate('auto.k0241'), icon: Power } as StatusOption : undefined,
            ].filter(Boolean) as StatusOption[]}
            fetchDetail={fetchDepartment}
            updateDetail={(key, draft) => updateDepartment(key, { name: draft.name })}
            changeStatus={changeDepartmentStatus}
            detailTitle={(d) => d.name}
            draftFromDetail={(d) => ({ name: d.name })}
            detailRows={(d) => [[translate('auto.k0233'), d.parent], [translate('auto.k0234'), d.lead], [translate('auto.k0242'), d.members], [translate('auto.k0182'), d.status]]}
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
        eyebrow={translate('auto.k0243')}
        title={translate('auto.k0244')}
        icon={ShieldCheck}
        action={translate('auto.k0245')}
        createResource="users"
        columns={canMutateUsers ? [translate('auto.k0246'), translate('auto.k0247'), translate('auto.k0232'), translate('auto.k0182'), translate('auto.k0248'), translate('auto.k0249')] : [translate('auto.k0246'), translate('auto.k0247'), translate('auto.k0232'), translate('auto.k0182'), translate('auto.k0248')]}
        rows={data.users.map((item: UserView) => [
          item.username,
          item.role,
          item.department,
          <span key={`${item.username}-status`}>
            <StatusBadge status={item.status} />
          </span>,
          item.last_seen,
          ...(canMutateUsers ? [(
            <div className="row-actions row-actions-start" key={`${item.username}-actions`}>
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
                  onClick={() => props.onUserLifecycleAction(item.username, item.status === translate('auto.k0068') ? 'unlock' : 'enable')}>
                  <CheckCircle2 size={13} />{item.status === translate('auto.k0068') ? translate('auto.k0250') : translate('auto.k0251')}
                </button>
              )}
              {canLock && (
                <button className="btn btn-xs btn-secondary" disabled={!signedIn || loadState.loading || item.username === currentUser?.username || item.status === translate('auto.k0068')}
                  onClick={() => props.onUserLifecycleAction(item.username, 'lock')}>
                  <LockKeyhole size={13} />{translate('auto.k0252')}</button>
              )}
              {canDisable && (
                <button className="btn btn-xs btn-secondary" disabled={!signedIn || loadState.loading || item.username === currentUser?.username}
                  onClick={() => props.onUserLifecycleAction(item.username, 'disable')}>
                  <LockKeyhole size={13} />{translate('auto.k0253')}</button>
              )}
              {canResetPassword && (
                <button className="btn btn-xs btn-secondary" disabled={!signedIn || loadState.loading}
                  onClick={() => props.onResetPassword(item.username)}>
                  <KeyRound size={13} />{translate('auto.k0254')}</button>
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
            title={translate('auto.k0255')}
            resourceLabel={translate('auto.k0256')}
            emptyLabel={translate('auto.k0257')}
            resources={data.users.map((u) => u.username)}
            fields={[{ key: 'display_name', label: translate('auto.k0258'), placeholder: translate('auto.k0259') }, { key: 'email', label: translate('auto.k0260'), placeholder: 'user@example.com' }]}
            signedIn={signedIn}
            canEdit={canEdit}
            statusOptions={[]}
            fetchDetail={fetchUser}
            updateDetail={(key, draft) => updateUser(key, draft)}
            changeStatus={() => Promise.resolve()}
            detailTitle={(d) => d.display_name || d.username}
            draftFromDetail={(d) => ({ display_name: d.display_name, email: d.email })}
            detailRows={(d) => [[translate('auto.k0261'), d.username], [translate('auto.k0260'), d.email || '-'], [translate('auto.k0247'), d.role], [translate('auto.k0232'), d.department], [translate('auto.k0182'), d.status], [translate('auto.k0248'), d.last_seen]]}
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
        title={translate('auto.k0262')}
        icon={ShieldCheck}
        action={translate('auto.k0170')}
        columns={[translate('auto.k0247'), translate('auto.k0263'), translate('auto.k0182'), translate('auto.k0264')]}
        rows={data.roles.map((item: RoleView) => [
          <span key={item.code}>
            <div className="management-primary-text">{item.name}</div>
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
      { value: '', label: translate('auto.k0265') }, { value: 'PUBLIC', label: 'PUBLIC' },
      { value: 'INTERNAL', label: 'INTERNAL' }, { value: 'CONFIDENTIAL', label: 'CONFIDENTIAL' },
      { value: 'STRICT', label: 'STRICT' }
    ];
    const publicModelOptions = [
      { value: '', label: translate('auto.k0265') }, { value: 'true', label: translate('auto.k0266') }, { value: 'false', label: translate('auto.k0267') }
    ];
    return (
      <DataSection
        eyebrow={translate('auto.k0268')}
        title={translate('auto.k0027')}
        icon={DatabaseZap}
        action={translate('auto.k0269')}
        createResource="projects"
        columns={[translate('auto.k0176'), translate('auto.k0270'), translate('auto.k0234'), translate('auto.k0271'), translate('auto.k0182')]}
        rows={data.projects.map((item: ProjectView) => [item.name, item.department, item.owner, item.apps, <StatusBadge key={item.name} status={item.status} />])}
        loadState={loadState}
        signedIn={signedIn}
        canCreate={hasPermission(currentUser, 'project:create')}
        onCreate={props.onCreate}
        onRefresh={props.onRefresh}
        sidePanel={
          <>
            <ResourceLifecyclePanel<ProjectView>
              title={translate('auto.k0272')}
              resourceLabel={translate('auto.k0176')}
              emptyLabel={translate('auto.k0273')}
              resources={data.projects.map((p) => p.name)}
              fields={[
                { key: 'name', label: translate('auto.k0274'), placeholder: translate('auto.k0275') },
                { key: 'sensitivity_level', label: translate('auto.k0276'), kind: 'select' as const, options: sensitivityOptions },
                { key: 'allow_public_model', label: translate('auto.k0277'), kind: 'public-model' as const, options: publicModelOptions }
              ]}
              signedIn={signedIn}
              canEdit={hasPermission(currentUser, 'project:edit')}
              statusOptions={[
                hasPermission(currentUser, 'project:edit') ? { value: 'ACTIVE', label: translate('auto.k0278'), icon: Power } as StatusOption : undefined,
                hasPermission(currentUser, 'project:edit') ? { value: 'PREPARING', label: translate('auto.k0279'), icon: Pencil } as StatusOption : undefined,
                hasPermission(currentUser, 'project:archive') ? { value: 'ARCHIVED', label: translate('auto.k0280'), icon: Archive } as StatusOption : undefined,
                hasPermission(currentUser, 'project:disable') ? { value: 'DISABLED', label: translate('auto.k0281'), icon: Power } as StatusOption : undefined
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
              detailRows={(d) => [[translate('auto.k0270'), d.department], [translate('auto.k0234'), d.owner], [translate('auto.k0271'), d.apps], [translate('auto.k0182'), d.status]]}
              onChanged={props.onRefresh}
            />
            <ScopedRolePanel
              title={translate('auto.k0282')}
              resourceLabel={translate('auto.k0176')}
              emptyLabel={translate('auto.k0273')}
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
      { value: '', label: translate('auto.k0265') }, { value: 'PUBLIC', label: 'PUBLIC' },
      { value: 'INTERNAL', label: 'INTERNAL' }, { value: 'CONFIDENTIAL', label: 'CONFIDENTIAL' },
      { value: 'STRICT', label: 'STRICT' }
    ];
    const publicModelOptions = [
      { value: '', label: translate('auto.k0265') }, { value: 'true', label: translate('auto.k0266') }, { value: 'false', label: translate('auto.k0267') }
    ];
    const appTypeOptions = [
      { value: '', label: translate('auto.k0265') }, { value: 'Web', label: 'Web' }, { value: 'Backend', label: 'Backend' },
      { value: 'Frontend', label: 'Frontend' }, { value: 'Mobile', label: 'Mobile' }, { value: 'Service', label: 'Service' }, { value: 'API', label: 'API' }
    ];
    return (
      <DataSection
        eyebrow={translate('auto.k0029')}
        title={translate('auto.k0283')}
        icon={AppWindow}
        action={translate('auto.k0284')}
        createResource="applications"
        columns={[translate('auto.k0285'), translate('auto.k0286'), translate('auto.k0287'), translate('auto.k0178'), translate('auto.k0182')]}
        rows={data.applications.map((item: ApplicationView) => [item.name, item.type, item.owner, item.version, <StatusBadge key={item.name} status={item.status} />])}
        loadState={loadState}
        signedIn={signedIn}
        canCreate={hasPermission(currentUser, 'application:create')}
        onCreate={props.onCreate}
        onRefresh={props.onRefresh}
        sidePanel={
          <>
            <ResourceLifecyclePanel<ApplicationView>
              title={translate('auto.k0288')}
              resourceLabel={translate('auto.k0285')}
              emptyLabel={translate('auto.k0289')}
              resources={data.applications.map((a) => a.name)}
              fields={[
                { key: 'name', label: translate('auto.k0290'), placeholder: translate('auto.k0291') },
                { key: 'app_type', label: translate('auto.k0292'), kind: 'select' as const, options: appTypeOptions },
                { key: 'default_web_url', label: 'Web URL', placeholder: 'https://web.example.test' },
                { key: 'default_api_base_url', label: 'API Base URL', placeholder: 'https://api.example.test' },
                { key: 'sensitivity_level', label: translate('auto.k0276'), kind: 'select' as const, options: sensitivityOptions },
                { key: 'allow_public_model', label: translate('auto.k0277'), kind: 'public-model' as const, options: publicModelOptions }
              ]}
              signedIn={signedIn}
              canEdit={hasPermission(currentUser, 'application:edit')}
              statusOptions={[
                hasPermission(currentUser, 'application:edit') ? { value: 'ENABLED', label: translate('auto.k0293'), icon: Power } as StatusOption : undefined,
                hasPermission(currentUser, 'application:disable') ? { value: 'DISABLED', label: translate('auto.k0294'), icon: Power } as StatusOption : undefined,
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
              detailRows={(d) => [[translate('auto.k0286'), d.type], [translate('auto.k0287'), d.owner], [translate('auto.k0178'), d.version], [translate('auto.k0182'), d.status]]}
              onChanged={props.onRefresh}
            />
            <ScopedRolePanel
              title={translate('auto.k0295')}
              resourceLabel={translate('auto.k0285')}
              emptyLabel={translate('auto.k0289')}
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
      { value: '', label: translate('auto.k0265') }, { value: 'DEV', label: 'DEV' }, { value: 'TEST', label: 'TEST' },
      { value: 'STAGING', label: 'STAGING' }, { value: 'PREPROD', label: 'PREPROD' }, { value: 'PROD', label: 'PROD' }
    ];
    return (
      <DataSection
        eyebrow={translate('auto.k0031')}
        title={translate('auto.k0296')}
        icon={ServerCog}
        action={translate('auto.k0297')}
        createResource="environments"
        columns={[translate('auto.k0215'), translate('auto.k0298'), 'Endpoint', translate('auto.k0182')]}
        rows={data.environments.map((item: EnvironmentView) => [item.name, item.cluster, item.endpoint, <StatusBadge key={item.name} status={item.status} />])}
        loadState={loadState}
        signedIn={signedIn}
        canCreate={hasPermission(currentUser, 'environment:create')}
        onCreate={props.onCreate}
        onRefresh={props.onRefresh}
        sidePanel={
          <>
            <ResourceLifecyclePanel<EnvironmentView>
              title={translate('auto.k0299')}
              resourceLabel={translate('auto.k0215')}
              emptyLabel={translate('auto.k0300')}
              resources={data.environments.map((e) => e.name)}
              fields={[
                { key: 'name', label: translate('auto.k0301'), placeholder: translate('auto.k0302') },
                { key: 'env_type', label: translate('auto.k0303'), kind: 'select' as const, options: envTypeOptions },
                { key: 'web_url', label: 'Web URL', placeholder: 'https://web.env.test' },
                { key: 'api_base_url', label: 'API Base URL', placeholder: 'https://api.env.test' }
              ]}
              signedIn={signedIn}
              canEdit={hasPermission(currentUser, 'environment:edit')}
              statusOptions={[
                hasPermission(currentUser, 'environment:edit') ? { value: 'ENABLED', label: translate('auto.k0304'), icon: Power } as StatusOption : undefined,
                hasPermission(currentUser, 'environment:disable') ? { value: 'DISABLED', label: translate('auto.k0305'), icon: Power } as StatusOption : undefined,
              ].filter(Boolean) as StatusOption[]}
              fetchDetail={fetchEnvironment}
              updateDetail={(key, draft) => updateEnvironment(key, {
                name: draft.name, env_type: draft.env_type, web_url: draft.web_url, api_base_url: draft.api_base_url
              })}
              changeStatus={changeEnvironmentStatus}
              detailTitle={(d) => d.name}
              draftFromDetail={(d) => ({ name: d.name, api_base_url: d.endpoint })}
              detailRows={(d) => [[translate('auto.k0298'), d.cluster], ['Endpoint', d.endpoint], [translate('auto.k0182'), d.status]]}
              onChanged={props.onRefresh}
            />
            <EnvironmentConnectivityPanel
              resources={data.environments.map((e) => e.name)}
              signedIn={signedIn}
              canRun={hasPermission(currentUser, 'environment:edit')}
              onChanged={props.onRefresh}
            />
            <ScopedRolePanel
              title={translate('auto.k0306')}
              resourceLabel={translate('auto.k0215')}
              emptyLabel={translate('auto.k0300')}
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
        eyebrow={translate('auto.k0307')}
        title={translate('auto.k0033')}
        icon={Link2}
        action={translate('auto.k0308')}
        createResource="integrations"
        columns={[translate('auto.k0309'), translate('auto.k0310'), translate('auto.k0196'), translate('auto.k0182')]}
        rows={data.integrations.map((item: IntegrationView) => [item.name, item.category, item.scope, <StatusBadge key={item.name} status={item.status} />])}
        loadState={loadState}
        signedIn={signedIn}
        canCreate={hasPermission(currentUser, 'config:edit')}
        onCreate={props.onCreate}
        onRefresh={props.onRefresh}
        sidePanel={
          <ResourceLifecyclePanel<IntegrationView>
            title={translate('auto.k0311')}
            resourceLabel={translate('auto.k0309')}
            emptyLabel={translate('auto.k0312')}
            resources={data.integrations.map((i) => i.name)}
            fields={[
              { key: 'name', label: translate('auto.k0313'), placeholder: translate('auto.k0314') },
              { key: 'category', label: translate('auto.k0310'), placeholder: translate('auto.k0315') },
              { key: 'scope', label: translate('auto.k0196'), placeholder: translate('auto.k0316') }
            ]}
            signedIn={signedIn}
            canEdit={hasPermission(currentUser, 'config:edit')}
            statusOptions={[
              hasPermission(currentUser, 'config:edit') ? { value: 'ENABLED', label: translate('auto.k0317'), icon: Power } as StatusOption : undefined,
              hasPermission(currentUser, 'config:edit') ? { value: 'DISABLED', label: translate('auto.k0318'), icon: Power } as StatusOption : undefined,
            ].filter(Boolean) as StatusOption[]}
            fetchDetail={fetchIntegration}
            updateDetail={(key, draft) => updateIntegration(key, {
              name: draft.name, category: draft.category, scope: draft.scope
            })}
            changeStatus={changeIntegrationStatus}
            detailTitle={(d) => d.name}
            draftFromDetail={(d) => ({ name: d.name, category: d.category, scope: d.scope })}
            detailRows={(d) => [[translate('auto.k0310'), d.category], [translate('auto.k0196'), d.scope], [translate('auto.k0182'), d.status]]}
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
        eyebrow={translate('auto.k0319')}
        title={translate('auto.k0037')}
        icon={Settings}
        action={translate('auto.k0170')}
        columns={[translate('auto.k0320'), translate('auto.k0321'), translate('auto.k0322'), translate('auto.k0182')]}
        rows={data.settings.map((item: SettingView) => [item.name, item.key, item.value, <StatusBadge key={item.key} status={item.status} />])}
        loadState={loadState}
        signedIn={signedIn}
        onRefresh={props.onRefresh}
        sidePanel={
          <ResourceLifecyclePanel<SettingView>
            title={translate('auto.k0323')}
            resourceLabel={translate('auto.k0324')}
            emptyLabel={translate('auto.k0325')}
            resources={data.settings.map((s) => s.key)}
            fields={[{ key: 'value', label: translate('auto.k0322'), placeholder: translate('auto.k0326') }]}
            signedIn={signedIn}
            canEdit={hasPermission(currentUser, 'config:edit')}
            statusOptions={[
              hasPermission(currentUser, 'config:edit') ? { value: 'ENABLED', label: translate('auto.k0251'), icon: Power } as StatusOption : undefined,
              hasPermission(currentUser, 'config:edit') ? { value: 'DISABLED', label: translate('auto.k0253'), icon: Power } as StatusOption : undefined,
            ].filter(Boolean) as StatusOption[]}
            fetchDetail={fetchSetting}
            updateDetail={(key, draft) => updateSetting(key, { value: draft.value })}
            changeStatus={changeSettingStatus}
            detailTitle={(d) => d.name}
            draftFromDetail={(d) => ({ value: d.value })}
            detailRows={(d) => [[translate('auto.k0321'), d.key], [translate('auto.k0322'), d.value], [translate('auto.k0182'), d.status]]}
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
    [translate('auto.k0375')]: 'badge-success',
    [translate('auto.k0380')]: 'badge-success',
    'ENABLED': 'badge-success',
    'ACTIVE': 'badge-success',
    [translate('auto.k0377')]: 'badge-success',
    [translate('auto.k0065')]: 'badge-success',
    [translate('auto.k0250')]: 'badge-success',
    [translate('auto.k0066')]: 'badge-success',
    [translate('auto.k0095')]: 'badge-success',
    'DISABLED': 'badge-warning',
    [translate('auto.k0253')]: 'badge-danger',
    [translate('auto.k0067')]: 'badge-danger',
    [translate('auto.k0252')]: 'badge-danger',
    [translate('auto.k0068')]: 'badge-danger',
    'PREPARING': 'badge-warning',
    'ARCHIVED': 'badge-neutral',
    [translate('auto.k2605')]: 'badge-warning',
    'DISCONNECTED': 'badge-warning',
    'DRAFT': 'badge-warning'
  };
  const cls = map[status] || 'badge-neutral';
  return <span className={`badge ${cls}`} title={status}>{dictionaryLabel(status)}</span>;
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
            <div className="management-section-heading">
              <div className="section-icon management-section-icon"><Icon size={17} /></div>
              <div>
                <div className="text-tertiary text-xs font-semibold management-eyebrow">{props.eyebrow}</div>
                <h2 className="panel-title">{props.title}</h2>
              </div>
            </div>
          </div>
          <div className="toolbar-actions management-toolbar-actions">
            {props.canCreate && props.onCreate && (
              <div className="management-quick-create">
                <input
                  type="text"
                  placeholder={translate('auto.k0327', {
                    value0: props.action
                      .replace(translate('auto.k0894'), '')
                      .replace(translate('auto.k0862'), '')
                      .replace(translate('auto.k2606'), '')
                      || translate('auto.k0177')
                  })}
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
                {translate('auto.k0170')}</button>
            )}
          </div>
        </div>
        <div className="panel-body">
          {props.loadState.error && (
            <div className="notice error management-notice">{props.loadState.error}</div>
          )}
          {props.rows.length === 0 && !props.loadState.loading ? (
            <div className="empty-state">
              <div className="empty-state-icon"><Icon size={32} opacity={0.4} /></div>
              <strong>{translate('auto.k0328')}</strong>
              <span>{translate('auto.k0329')}{props.title}{translate('auto.k0330')}</span>
            </div>
          ) : (
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>{props.columns.map((col) => <th key={col}>{col}</th>)}</tr>
                </thead>
                <tbody>
                  {props.loadState.loading ? (
                    <tr><td colSpan={props.columns.length}><div className="skeleton skeleton-text management-skeleton-row" /></td></tr>
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
      .catch((err: unknown) => { setError(err instanceof Error ? err.message : translate('auto.k0049')); setLoading(false); });
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
      setError(err instanceof Error ? err.message : translate('auto.k0331'));
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
      setError(err instanceof Error ? err.message : translate('auto.k0332'));
      setLoading(false);
    }
  }

  return (
    <div className="panel">
      <div className="panel-body">
        <div className="management-side-heading">
          <div className="text-tertiary text-xs font-semibold">{translate('auto.k0333')}</div>
          <h3 className="panel-title">{props.title}</h3>
        </div>

        <div className="field management-select-field">
          <NativeSelect
            value={selectedKey}
            onChange={(e) => setSelectedKey(e.target.value)}
            disabled={!props.signedIn}
          >
            <option value="">{translate('auto.k0334')}{props.resourceLabel}...</option>
            {props.resources.map((r) => (
              <option key={r} value={r}>{r}</option>
            ))}
          </NativeSelect>
        </div>

        {loading && <div className="skeleton skeleton-text" />}
        {error && <div className="notice error management-notice-sm">{error}</div>}

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
                        <NativeSelect
                          value={editDraft[field.key] ?? ''}
                          onChange={(e) => setEditDraft((d) => d ? { ...d, [field.key]: e.target.value } : d)}
                        >
                          {(field.options ?? []).map((opt) => (
                            <option key={opt.value} value={opt.value}>{opt.label}</option>
                          ))}
                        </NativeSelect>
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
                    {loading ? translate('auto.k0335') : translate('auto.k0336')}
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
          <NativeSelect
            value={selectedRole}
            onChange={(e) => setSelectedRole(e.target.value)}
            disabled={!props.signedIn}
          >
            <option value="">{translate('auto.k0247')}</option>
            {props.roles.map((r) => <option key={r.code} value={r.code}>{r.name}</option>)}
          </NativeSelect>
          <button
            className="btn btn-xs btn-secondary"
            disabled={!props.signedIn || props.loading || !selectedRole}
            onClick={() => { props.onAction(props.username, 'assign-role', selectedRole); setSelectedRole(''); }}
          >
            {translate('auto.k0337')}</button>
        </>
      )}
      {props.canUnassign && selectedRole && (
        <button
          className="btn btn-xs btn-secondary"
          disabled={!props.signedIn || props.loading}
          onClick={() => { props.onAction(props.username, 'unassign-role', selectedRole); setSelectedRole(''); }}
        >
          {translate('auto.k0338')}</button>
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
      .catch((err: unknown) => { setError(err instanceof Error ? err.message : translate('auto.k0049')); setLoading(false); });
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
      setError(err instanceof Error ? err.message : translate('auto.k0339'));
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
      setError(err instanceof Error ? err.message : translate('auto.k0340'));
      setLoading(false);
    }
  }

  return (
    <div className="panel">
      <div className="panel-body">
        <div className="management-side-heading">
          <div className="text-tertiary text-xs font-semibold management-eyebrow">{translate('auto.k0235')}</div>
          <h3 className="panel-title management-side-title">{props.title}</h3>
        </div>

        <div className="field management-select-field">
          <NativeSelect
            value={selectedKey}
            onChange={(e) => setSelectedKey(e.target.value)}
            disabled={!props.signedIn}
            className="management-full-control"
          >
            <option value="">{translate('auto.k0334')}{props.resourceLabel}...</option>
            {props.resources.map((r) => <option key={r} value={r}>{r}</option>)}
          </NativeSelect>
        </div>

        {error && <div className="notice error management-notice-sm">{error}</div>}

        {selectedKey && (
          <>
            {loading ? (
              <div className="skeleton skeleton-text" />
            ) : members.length === 0 ? (
              <div className="empty-state management-empty-compact">
                <span>{translate('auto.k0341')}</span>
              </div>
            ) : (
              <div className="management-item-list">
                {members.map((m) => (
                  <div key={m.username} className="management-list-row">
                    <div>
                      <div className="management-primary-text">{m.display_name || m.username}</div>
                      <div className="text-tertiary text-xs">{m.role}</div>
                    </div>
                    {props.canManage && (
                      <button className="btn btn-xs btn-secondary" onClick={() => remove(m.username)} disabled={loading}>{translate('auto.k0342')}</button>
                    )}
                  </div>
                ))}
              </div>
            )}

            {props.canManage && (
              <div className="management-member-add-grid">
                <input
                  type="text"
                  placeholder={translate('auto.k0343')}
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  className="management-compact-control"
                />
                <NativeSelect
                  value={roleCode}
                  onChange={(e) => setRoleCode(e.target.value)}
                  className="management-compact-control"
                >
                  {props.roles.map((role) => <option key={role} value={role}>{role}</option>)}
                </NativeSelect>
                <button className="btn btn-primary btn-sm" onClick={add} disabled={loading || !username.trim()}>{translate('auto.k0344')}</button>
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
      setError(err instanceof Error ? err.message : translate('auto.k0345'));
      setLoading(false);
    }
  }

  return (
    <div className="panel">
      <div className="panel-body">
        <div className="management-side-heading">
          <div className="text-tertiary text-xs font-semibold management-eyebrow">{translate('auto.k0346')}</div>
          <h3 className="panel-title management-side-title">{translate('auto.k0347')}</h3>
        </div>

        <div className="field management-select-field">
          <NativeSelect
            value={selectedKey}
            onChange={(e) => setSelectedKey(e.target.value)}
            disabled={!props.signedIn}
            className="management-full-control"
          >
            <option value="">{translate('auto.k0348')}</option>
            {props.resources.map((r) => <option key={r} value={r}>{r}</option>)}
          </NativeSelect>
        </div>

        {props.canRun && selectedKey && (
          <button className="btn btn-primary btn-sm management-full-action" onClick={check} disabled={loading}>
            {loading ? translate('auto.k0349') : translate('auto.k0350')}
          </button>
        )}

        {error && <div className="notice error management-notice-sm">{error}</div>}

        {result && (
          <div className="management-result-card">
            <div className="management-result-head">
              <StatusBadge status={result.status} />
              <span className="text-tertiary text-xs">{result.latencyMs ?? '-'}ms</span>
            </div>
            <p className="text-secondary text-sm management-wrap-text">{result.message}</p>
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
      .catch((err: unknown) => { setError(err instanceof Error ? err.message : translate('auto.k0049')); setLoading(false); });
  }, [selectedCode]);

  return (
    <div className="panel">
      <div className="panel-body">
        <div className="management-side-heading">
          <div className="text-tertiary text-xs font-semibold management-eyebrow">{translate('auto.k0262')}</div>
          <h3 className="panel-title management-side-title">{translate('auto.k0351')}</h3>
        </div>

        <div className="field management-select-field">
          <NativeSelect
            value={selectedCode}
            onChange={(e) => setSelectedCode(e.target.value)}
            disabled={!props.signedIn}
            className="management-full-control"
          >
            <option value="">{translate('auto.k0352')}</option>
            {props.roles.map((r) => <option key={r.code} value={r.code}>{r.name} ({r.code})</option>)}
          </NativeSelect>
        </div>

        {loading && <div className="skeleton skeleton-text" />}
        {error && <div className="notice error management-notice-sm">{error}</div>}

        {detail && !loading && (
          <>
            <div className="divider" />
            <div className="detail-grid">
              <div className="detail-row">
                <span className="detail-label">{translate('auto.k0177')}</span>
                <span className="detail-value">{detail.name}</span>
              </div>
              <div className="detail-row">
                <span className="detail-label">{translate('auto.k0353')}</span>
                <span className="detail-value">{detail.code}</span>
              </div>
              <div className="detail-row">
                <span className="detail-label">{translate('auto.k0182')}</span>
                <span className="detail-value"><StatusBadge status={detail.status} /></span>
              </div>
              {detail.description && (
                <div className="detail-row">
                  <span className="detail-label">{translate('auto.k0264')}</span>
                  <span className="detail-value">{detail.description}</span>
                </div>
              )}
            </div>

            <div className="divider" />
            <div className="text-tertiary text-xs font-semibold management-permission-heading">{translate('auto.k0354')}{rolePermissions.length})</div>
            {rolePermissions.length > 0 ? (
              <div className="management-chip-list">
                {rolePermissions.map((p: string) => (
                  <span key={p} className="badge badge-info">{p}</span>
                ))}
              </div>
            ) : (
              <div className="text-tertiary text-sm">{translate('auto.k0355')}</div>
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
            <div className="management-section-heading">
              <div className="section-icon management-section-icon"><ScrollText size={17} /></div>
              <div>
                <div className="text-tertiary text-xs font-semibold management-eyebrow">{translate('auto.k0356')}</div>
                <h2 className="panel-title">{translate('auto.k0035')}</h2>
              </div>
            </div>
          </div>
          <div className="toolbar-actions">
            <button className="btn btn-secondary btn-sm" onClick={onAuditExport} disabled={!signedIn || auditExportState.loading || !canExport}>
              {auditExportState.loading ? translate('auto.k0357') : translate('auto.k0358')}
            </button>
            <button className="btn btn-secondary btn-sm" onClick={props.onRefresh} disabled={loadState.loading}>{translate('auto.k0170')}</button>
          </div>
        </div>
        <div className="panel-body">
          {loadState.error && <div className="notice error management-notice">{loadState.error}</div>}
          {data.auditLogs.length === 0 && !loadState.loading ? (
            <div className="empty-state">
              <div className="empty-state-icon"><ScrollText size={32} opacity={0.4} /></div>
              <strong>{translate('auto.k0359')}</strong>
              <span>{translate('auto.k0360')}</span>
            </div>
          ) : (
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>{translate('auto.k0361')}</th>
                    <th>{translate('auto.k0362')}</th>
                    <th>{translate('auto.k0363')}</th>
                    <th>{translate('auto.k0364')}</th>
                    <th>{translate('auto.k0365')}</th>
                    <th>{translate('auto.k0333')}</th>
                  </tr>
                </thead>
                <tbody>
                  {loadState.loading ? (
                    <tr><td colSpan={6}><div className="skeleton skeleton-text management-skeleton-row" /></td></tr>
                  ) : (
                    data.auditLogs.map((log: AuditLogView, idx: number) => (
                      <tr key={idx}>
                        <td className="text-sm">{log.time}</td>
                        <td>{log.actor}</td>
                        <td>{log.action}</td>
                        <td>
                          <div className="management-primary-text">{log.target}</div>
                        </td>
                        <td><StatusBadge status={log.result} /></td>
                        <td className="text-sm text-secondary management-ellipsis-cell">{log.target || '-'}</td>
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
        <div className="management-side-heading">
          <div className="text-tertiary text-xs font-semibold management-eyebrow">{translate('auto.k0366')}</div>
          <h3 className="panel-title management-side-title">Audit Outbox</h3>
        </div>

        <div className="management-outbox-filter-grid">
          <NativeSelect
            value={props.auditOutboxFilters.status}
            onChange={(e) => props.onAuditOutboxFiltersChange({ ...props.auditOutboxFilters, status: e.target.value })}
            className="management-compact-control"
          >
            <option value="">{translate('auto.k0367')}</option>
            <option value="PENDING">{translate('auto.k0366')}</option>
            <option value="SUCCESS">{translate('auto.k0368')}</option>
            <option value="FAILED">{translate('auto.k0369')}</option>
          </NativeSelect>
          <input
            type="text" placeholder="Trace ID"
            value={props.auditOutboxFilters.traceId}
            onChange={(e) => props.onAuditOutboxFiltersChange({ ...props.auditOutboxFilters, traceId: e.target.value })}
            className="management-compact-control"
          />
        </div>
        <input
          type="text" placeholder={translate('auto.k0370')}
          value={props.auditOutboxFilters.search}
          onChange={(e) => props.onAuditOutboxFiltersChange({ ...props.auditOutboxFilters, search: e.target.value })}
          className="management-compact-control management-search-control"
        />

        <button className="btn btn-secondary btn-sm management-full-action" onClick={() => props.onAuditOutboxRefresh()} disabled={props.auditOutboxLoad.loading}>
          {props.auditOutboxLoad.loading ? translate('auto.k0371') : translate('auto.k0372')}
        </button>

        {props.auditOutboxLoad.error && (
          <div className="notice error management-notice-sm">{props.auditOutboxLoad.error}</div>
        )}

        <div className="management-item-list">
          {props.data.auditOutbox.length === 0 && !props.auditOutboxLoad.loading ? (
            <div className="text-tertiary text-sm">{translate('auto.k0373')}</div>
          ) : (
            props.data.auditOutbox.map((item: AuditOutboxView, idx: number) => (
              <div key={idx} className="management-outbox-item">
                <div className="management-result-head">
                  <strong className="management-outbox-title">{item.eventAction}</strong>
                  <StatusBadge status={item.status} />
                </div>
                <div className="text-tertiary text-xs management-meta-line">
                  {item.resourceType}/{item.resourceId}
                </div>
                {item.lastError && (
                  <div className="text-xs management-error-line">{item.lastError}</div>
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
