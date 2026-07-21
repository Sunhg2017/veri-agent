import { requestJson, requestText, type ApiResponse } from './client';

export interface DepartmentView {
  name: string;
  parent: string;
  lead: string;
  members: number;
  status: string;
}

export interface UserView {
  username: string;
  display_name: string;
  email: string;
  role: string;
  department: string;
  status: string;
  last_seen: string;
}

export interface RoleView {
  code: string;
  name: string;
  scopeType?: string;
  scope_type?: string;
  status: string;
  description: string;
}

export interface PermissionView {
  code: string;
  resourceType?: string;
  resource_type?: string;
  action: string;
  scopeMask?: string;
  scope_mask?: string;
  description: string;
  status: string;
}

export interface RoleDetailView extends RoleView {
  system: boolean;
  builtin: boolean;
  version: number;
  permissionCodes?: string[];
  permission_codes?: string[];
}

export interface ProjectView {
  name: string;
  department: string;
  owner: string;
  apps: number;
  status: string;
}

export interface ApplicationView {
  name: string;
  type: string;
  owner: string;
  version: string;
  status: string;
}

export interface EnvironmentView {
  name: string;
  cluster: string;
  endpoint: string;
  status: string;
}

export interface EnvironmentConnectivityEndpointView {
  target: string;
  url: string;
  status: string;
  latencyMs?: number | null;
  statusCode?: number | null;
  message: string;
}

export interface EnvironmentConnectivityCheckView {
  environment: string;
  status: string;
  checkedAt: string;
  latencyMs?: number | null;
  message: string;
  traceId: string;
  endpoints: EnvironmentConnectivityEndpointView[];
}

export interface IntegrationView {
  key: string;
  name: string;
  category: string;
  scope: string;
  status: string;
}

export interface AuditLogView {
  time: string;
  actor: string;
  action: string;
  target: string;
  result: string;
}

export interface AuditOutboxView {
  id: string;
  traceId: string;
  idempotencyKey: string;
  status: string;
  retryCount: number;
  nextRetryAt: string;
  lockedAt: string;
  lockedBy: string;
  lastError: string;
  eventAction: string;
  resourceType: string;
  resourceId: string;
  result: string;
  createdAt: string;
  updatedAt: string;
}

export interface AuditLogExportFilters {
  search?: string;
  actor?: string;
  action?: string;
  resourceType?: string;
  result?: string;
  startTime?: string;
  endTime?: string;
}

export interface AuditOutboxFilters {
  search?: string;
  status?: string;
  traceId?: string;
}

export interface SettingView {
  key: string;
  name: string;
  value: string;
  scope: string;
  status: string;
}

export interface SecretReferenceView {
  id: string;
  secretRef: string;
  providerCode: string;
  providerType: string;
  purpose: string;
  scopeType: string;
  scopeId: string;
  maskedValue: string;
  secretVersion: string;
  status: string;
  rotatedAt: string;
  expiresAt: string;
  createdAt: string;
  updatedAt: string;
}

export interface ScopedUserRoleView {
  username: string;
  display_name: string;
  role: string;
  scope_type: string;
  status: string;
}

export interface ProjectMemberView {
  username: string;
  display_name: string;
  role: string;
  member_type: string;
  status: string;
}

export interface UpdateDepartmentPayload {
  name?: string;
}

export interface UpdateUserPayload {
  display_name?: string;
  email?: string;
}

export interface UpdateProjectPayload {
  name?: string;
  sensitivity_level?: string;
  allow_public_model?: boolean;
}

export interface UpdateApplicationPayload {
  name?: string;
  app_type?: string;
  default_web_url?: string;
  default_api_base_url?: string;
  sensitivity_level?: string;
  allow_public_model?: boolean;
}

export interface UpdateEnvironmentPayload {
  name?: string;
  env_type?: string;
  web_url?: string;
  api_base_url?: string;
}

export interface CreateIntegrationPayload {
  code?: string;
  name: string;
  category?: string;
  scope?: string;
}

export interface UpdateIntegrationPayload {
  name?: string;
  category?: string;
  scope?: string;
}

export interface CreateSettingPayload {
  key: string;
  name?: string;
  value: string;
  scope_type?: string;
}

export interface UpdateSettingPayload {
  name?: string;
  value?: string;
  scope_type?: string;
}

export interface CreateSecretReferencePayload {
  secret_ref: string;
  provider_code?: string;
  purpose: string;
  scope_type: string;
  scope_id: string;
  secret_value: string;
  secret_version?: string;
  expires_at?: string;
}

export interface RotateSecretReferencePayload {
  secret_ref: string;
  secret_value: string;
  secret_version?: string;
  expires_at?: string;
}

export interface CreateRolePayload {
  code: string;
  name: string;
  scopeType: string;
  description?: string;
  permissionCodes: string[];
}

export interface UpdateRolePayload {
  name?: string;
  scopeType?: string;
  description?: string;
  permissionCodes?: string[];
}

export interface ManagementData {
  departments: DepartmentView[];
  users: UserView[];
  roles: RoleView[];
  permissions: PermissionView[];
  projects: ProjectView[];
  applications: ApplicationView[];
  environments: EnvironmentView[];
  integrations: IntegrationView[];
  auditLogs: AuditLogView[];
  auditOutbox: AuditOutboxView[];
  settings: SettingView[];
  secrets: SecretReferenceView[];
}

export type CreatableManagementResource = 'departments' | 'users' | 'projects' | 'applications' | 'environments' | 'integrations';

const endpoints = {
  departments: '/api/v1/management/departments',
  users: '/api/v1/management/users',
  roles: '/api/v1/management/roles',
  permissions: '/api/v1/management/permissions',
  projects: '/api/v1/management/projects',
  applications: '/api/v1/management/applications',
  environments: '/api/v1/management/environments',
  integrations: '/api/v1/management/integrations',
  auditLogs: '/api/v1/management/audit-logs',
  auditLogsExport: '/api/v1/management/audit-logs/export',
  auditOutbox: '/api/v1/management/audit-outbox',
  settings: '/api/v1/management/settings',
  secrets: '/api/v1/management/secrets'
} as const;

export interface PageResponse<T> {
  items: T[];
  page: number;
  page_size: number;
  total: number;
}

type ReadPermission =
  | 'department:read'
  | 'user:read'
  | 'role:read'
  | 'project:read'
  | 'application:read'
  | 'environment:read'
  | 'config:read'
  | 'secret:read'
  | 'audit:read';

function canRead(permissions: string[] | undefined, permission: ReadPermission) {
  return permissions === undefined || permissions.includes(permission);
}

function skippedPage<T>(): Promise<ApiResponse<PageResponse<T>>> {
  return Promise.resolve({
    code: 'OK',
    message: 'skipped',
    trace_id: '',
    data: {
      items: [],
      page: 1,
      page_size: 20,
      total: 0
    }
  });
}

export async function fetchManagementData(permissions?: string[]): Promise<{ traceId: string; data: ManagementData }> {
  const [
    departments,
    users,
    roles,
    permissionCatalog,
    projects,
    applications,
    environments,
    integrations,
    auditLogs,
    auditOutbox,
    settings,
    secrets
  ] = await Promise.all([
    canRead(permissions, 'department:read') ? requestJson<PageResponse<DepartmentView>>(endpoints.departments) : skippedPage<DepartmentView>(),
    canRead(permissions, 'user:read') ? requestJson<PageResponse<UserView>>(endpoints.users) : skippedPage<UserView>(),
    canRead(permissions, 'role:read') ? requestJson<PageResponse<RoleView>>(`${endpoints.roles}?size=100`) : skippedPage<RoleView>(),
    canRead(permissions, 'role:read') ? requestJson<PageResponse<PermissionView>>(`${endpoints.permissions}?size=100`) : skippedPage<PermissionView>(),
    canRead(permissions, 'project:read') ? requestJson<PageResponse<ProjectView>>(endpoints.projects) : skippedPage<ProjectView>(),
    canRead(permissions, 'application:read') ? requestJson<PageResponse<ApplicationView>>(endpoints.applications) : skippedPage<ApplicationView>(),
    canRead(permissions, 'environment:read') ? requestJson<PageResponse<EnvironmentView>>(endpoints.environments) : skippedPage<EnvironmentView>(),
    canRead(permissions, 'config:read') ? requestJson<PageResponse<IntegrationView>>(endpoints.integrations) : skippedPage<IntegrationView>(),
    canRead(permissions, 'audit:read') ? requestJson<PageResponse<AuditLogView>>(endpoints.auditLogs) : skippedPage<AuditLogView>(),
    canRead(permissions, 'audit:read') ? requestJson<PageResponse<AuditOutboxView>>(endpoints.auditOutbox) : skippedPage<AuditOutboxView>(),
    canRead(permissions, 'config:read') ? requestJson<PageResponse<SettingView>>(endpoints.settings) : skippedPage<SettingView>(),
    canRead(permissions, 'secret:read') ? requestJson<PageResponse<SecretReferenceView>>(endpoints.secrets) : skippedPage<SecretReferenceView>()
  ]);

  return {
    traceId: [
      departments,
      users,
      roles,
      permissionCatalog,
      projects,
      applications,
      environments,
      integrations,
      auditLogs,
      auditOutbox,
      settings,
      secrets
    ].find((response) => response.trace_id)?.trace_id ?? '',
    data: {
      departments: departments.data.items,
      users: users.data.items,
      roles: roles.data.items,
      permissions: permissionCatalog.data.items,
      projects: projects.data.items,
      applications: applications.data.items,
      environments: environments.data.items,
      integrations: integrations.data.items,
      auditLogs: auditLogs.data.items,
      auditOutbox: auditOutbox.data.items,
      settings: settings.data.items,
      secrets: secrets.data.items
    }
  };
}

export function createManagementItem<T>(
  resource: CreatableManagementResource,
  name: string
): Promise<ApiResponse<T>> {
  return requestJson<T>(endpoints[resource], {
    method: 'POST',
    body: JSON.stringify({ name })
  });
}

export function auditLogExportPath(filters: AuditLogExportFilters = {}) {
  const params = new URLSearchParams();
  const entries: Array<[string, string | undefined]> = [
    ['search', filters.search],
    ['actor', filters.actor],
    ['action', filters.action],
    ['resourceType', filters.resourceType],
    ['result', filters.result],
    ['startTime', filters.startTime],
    ['endTime', filters.endTime]
  ];
  for (const [key, value] of entries) {
    const normalized = value?.trim();
    if (normalized) {
      params.set(key, normalized);
    }
  }
  const query = params.toString();
  return query ? `${endpoints.auditLogsExport}?${query}` : endpoints.auditLogsExport;
}

export async function exportAuditLogsCsv(filters: AuditLogExportFilters = {}) {
  return requestText(auditLogExportPath(filters));
}

export function auditOutboxPath(filters: AuditOutboxFilters = {}) {
  const params = new URLSearchParams();
  const entries: Array<[string, string | undefined]> = [
    ['search', filters.search],
    ['status', filters.status],
    ['traceId', filters.traceId]
  ];
  for (const [key, value] of entries) {
    const normalized = value?.trim();
    if (normalized) {
      params.set(key, normalized);
    }
  }
  const query = params.toString();
  return query ? `${endpoints.auditOutbox}?${query}` : endpoints.auditOutbox;
}

export async function fetchAuditOutbox(filters: AuditOutboxFilters = {}) {
  return requestJson<PageResponse<AuditOutboxView>>(auditOutboxPath(filters));
}

export function fetchDepartment(departmentKey: string): Promise<ApiResponse<DepartmentView>> {
  return requestJson<DepartmentView>(`/api/v1/management/departments/${encodeURIComponent(departmentKey)}`);
}

export function updateDepartment(departmentKey: string, payload: UpdateDepartmentPayload): Promise<ApiResponse<DepartmentView>> {
  return requestJson<DepartmentView>(`/api/v1/management/departments/${encodeURIComponent(departmentKey)}`, {
    method: 'PATCH',
    body: JSON.stringify(payload)
  });
}

export function changeDepartmentStatus(departmentKey: string, status: string): Promise<ApiResponse<DepartmentView>> {
  return requestJson<DepartmentView>(`/api/v1/management/departments/${encodeURIComponent(departmentKey)}/status`, {
    method: 'PATCH',
    body: JSON.stringify({ status })
  });
}

export function enableUser(username: string): Promise<ApiResponse<UserView>> {
  return requestJson<UserView>(`/api/v1/management/users/${encodeURIComponent(username)}/enable`, {
    method: 'POST'
  });
}

export function fetchUser(username: string): Promise<ApiResponse<UserView>> {
  return requestJson<UserView>(`/api/v1/management/users/${encodeURIComponent(username)}`);
}

export function updateUser(username: string, payload: UpdateUserPayload): Promise<ApiResponse<UserView>> {
  return requestJson<UserView>(`/api/v1/management/users/${encodeURIComponent(username)}`, {
    method: 'PATCH',
    body: JSON.stringify(payload)
  });
}

export function disableUser(username: string): Promise<ApiResponse<UserView>> {
  return requestJson<UserView>(`/api/v1/management/users/${encodeURIComponent(username)}/disable`, {
    method: 'POST'
  });
}

export function lockUser(username: string): Promise<ApiResponse<UserView>> {
  return requestJson<UserView>(`/api/v1/management/users/${encodeURIComponent(username)}/lock`, {
    method: 'POST'
  });
}

export function unlockUser(username: string): Promise<ApiResponse<UserView>> {
  return requestJson<UserView>(`/api/v1/management/users/${encodeURIComponent(username)}/unlock`, {
    method: 'POST'
  });
}

export function resetUserPassword(username: string, newPassword: string): Promise<ApiResponse<UserView>> {
  return requestJson<UserView>(`/api/v1/management/users/${encodeURIComponent(username)}/reset-password`, {
    method: 'POST',
    body: JSON.stringify({ new_password: newPassword })
  });
}

export function assignUserRole(username: string, roleCode: string): Promise<ApiResponse<UserView>> {
  return requestJson<UserView>(`/api/v1/management/users/${encodeURIComponent(username)}/roles`, {
    method: 'POST',
    body: JSON.stringify({ role_code: roleCode })
  });
}

export function unassignUserRole(username: string, roleCode: string): Promise<ApiResponse<UserView>> {
  return requestJson<UserView>(`/api/v1/management/users/${encodeURIComponent(username)}/roles/unassign`, {
    method: 'POST',
    body: JSON.stringify({ role_code: roleCode })
  });
}

export function fetchPermissions(): Promise<ApiResponse<PageResponse<PermissionView>>> {
  return requestJson<PageResponse<PermissionView>>(`${endpoints.permissions}?size=100`);
}

export function fetchRole(roleCode: string): Promise<ApiResponse<RoleDetailView>> {
  return requestJson<RoleDetailView>(`${endpoints.roles}/${encodeURIComponent(roleCode)}`);
}

export function createRole(payload: CreateRolePayload): Promise<ApiResponse<RoleDetailView>> {
  return requestJson<RoleDetailView>(endpoints.roles, {
    method: 'POST',
    body: JSON.stringify(payload)
  });
}

export function updateRole(roleCode: string, payload: UpdateRolePayload): Promise<ApiResponse<RoleDetailView>> {
  return requestJson<RoleDetailView>(`${endpoints.roles}/${encodeURIComponent(roleCode)}`, {
    method: 'PATCH',
    body: JSON.stringify(payload)
  });
}

export function changeRoleStatus(roleCode: string, status: string): Promise<ApiResponse<RoleDetailView>> {
  return requestJson<RoleDetailView>(`${endpoints.roles}/${encodeURIComponent(roleCode)}/status`, {
    method: 'PATCH',
    body: JSON.stringify({ status })
  });
}

export function fetchProjectMembers(projectKey: string): Promise<ApiResponse<PageResponse<ProjectMemberView>>> {
  return requestJson<PageResponse<ProjectMemberView>>(
    `/api/v1/management/projects/${encodeURIComponent(projectKey)}/members`
  );
}

export function addProjectMember(
  projectKey: string,
  username: string,
  roleCode: string
): Promise<ApiResponse<ProjectMemberView>> {
  return requestJson<ProjectMemberView>(`/api/v1/management/projects/${encodeURIComponent(projectKey)}/members`, {
    method: 'POST',
    body: JSON.stringify({ username, role_code: roleCode })
  });
}

export function removeProjectMember(
  projectKey: string,
  username: string
): Promise<ApiResponse<ProjectMemberView>> {
  return requestJson<ProjectMemberView>(
    `/api/v1/management/projects/${encodeURIComponent(projectKey)}/members/${encodeURIComponent(username)}/remove`,
    { method: 'POST' }
  );
}

export function fetchProject(projectKey: string): Promise<ApiResponse<ProjectView>> {
  return requestJson<ProjectView>(`/api/v1/management/projects/${encodeURIComponent(projectKey)}`);
}

export function updateProject(projectKey: string, payload: UpdateProjectPayload): Promise<ApiResponse<ProjectView>> {
  return requestJson<ProjectView>(`/api/v1/management/projects/${encodeURIComponent(projectKey)}`, {
    method: 'PATCH',
    body: JSON.stringify(payload)
  });
}

export function changeProjectStatus(projectKey: string, status: string): Promise<ApiResponse<ProjectView>> {
  return requestJson<ProjectView>(`/api/v1/management/projects/${encodeURIComponent(projectKey)}/status`, {
    method: 'PATCH',
    body: JSON.stringify({ status })
  });
}

export function fetchApplicationOwners(applicationKey: string): Promise<ApiResponse<PageResponse<ScopedUserRoleView>>> {
  return requestJson<PageResponse<ScopedUserRoleView>>(
    `/api/v1/management/applications/${encodeURIComponent(applicationKey)}/owners`
  );
}

export function addApplicationOwner(
  applicationKey: string,
  username: string
): Promise<ApiResponse<ScopedUserRoleView>> {
  return requestJson<ScopedUserRoleView>(`/api/v1/management/applications/${encodeURIComponent(applicationKey)}/owners`, {
    method: 'POST',
    body: JSON.stringify({ username, role_code: 'AppOwner' })
  });
}

export function removeApplicationOwner(
  applicationKey: string,
  username: string
): Promise<ApiResponse<ScopedUserRoleView>> {
  return requestJson<ScopedUserRoleView>(
    `/api/v1/management/applications/${encodeURIComponent(applicationKey)}/owners/${encodeURIComponent(username)}/remove`,
    { method: 'POST' }
  );
}

export function fetchApplication(applicationKey: string): Promise<ApiResponse<ApplicationView>> {
  return requestJson<ApplicationView>(`/api/v1/management/applications/${encodeURIComponent(applicationKey)}`);
}

export function updateApplication(applicationKey: string, payload: UpdateApplicationPayload): Promise<ApiResponse<ApplicationView>> {
  return requestJson<ApplicationView>(`/api/v1/management/applications/${encodeURIComponent(applicationKey)}`, {
    method: 'PATCH',
    body: JSON.stringify(payload)
  });
}

export function changeApplicationStatus(applicationKey: string, status: string): Promise<ApiResponse<ApplicationView>> {
  return requestJson<ApplicationView>(`/api/v1/management/applications/${encodeURIComponent(applicationKey)}/status`, {
    method: 'PATCH',
    body: JSON.stringify({ status })
  });
}

export function fetchEnvironmentUsers(environmentKey: string): Promise<ApiResponse<PageResponse<ScopedUserRoleView>>> {
  return requestJson<PageResponse<ScopedUserRoleView>>(
    `/api/v1/management/environments/${encodeURIComponent(environmentKey)}/users`
  );
}

export function fetchEnvironment(environmentKey: string): Promise<ApiResponse<EnvironmentView>> {
  return requestJson<EnvironmentView>(`/api/v1/management/environments/${encodeURIComponent(environmentKey)}`);
}

export function updateEnvironment(environmentKey: string, payload: UpdateEnvironmentPayload): Promise<ApiResponse<EnvironmentView>> {
  return requestJson<EnvironmentView>(`/api/v1/management/environments/${encodeURIComponent(environmentKey)}`, {
    method: 'PATCH',
    body: JSON.stringify(payload)
  });
}

export function changeEnvironmentStatus(environmentKey: string, status: string): Promise<ApiResponse<EnvironmentView>> {
  return requestJson<EnvironmentView>(`/api/v1/management/environments/${encodeURIComponent(environmentKey)}/status`, {
    method: 'PATCH',
    body: JSON.stringify({ status })
  });
}

export function fetchEnvironmentConnectivityCheck(
  environmentKey: string
): Promise<ApiResponse<EnvironmentConnectivityCheckView>> {
  return requestJson<EnvironmentConnectivityCheckView>(
    `/api/v1/management/environments/${encodeURIComponent(environmentKey)}/connectivity-check`
  );
}

export function runEnvironmentConnectivityCheck(
  environmentKey: string
): Promise<ApiResponse<EnvironmentConnectivityCheckView>> {
  return requestJson<EnvironmentConnectivityCheckView>(
    `/api/v1/management/environments/${encodeURIComponent(environmentKey)}/connectivity-check`,
    { method: 'POST' }
  );
}

export function addEnvironmentUser(
  environmentKey: string,
  username: string,
  roleCode: string
): Promise<ApiResponse<ScopedUserRoleView>> {
  return requestJson<ScopedUserRoleView>(`/api/v1/management/environments/${encodeURIComponent(environmentKey)}/users`, {
    method: 'POST',
    body: JSON.stringify({ username, role_code: roleCode })
  });
}

export function removeEnvironmentUser(
  environmentKey: string,
  username: string
): Promise<ApiResponse<ScopedUserRoleView>> {
  return requestJson<ScopedUserRoleView>(
    `/api/v1/management/environments/${encodeURIComponent(environmentKey)}/users/${encodeURIComponent(username)}/remove`,
    { method: 'POST' }
  );
}

export function createIntegration(payload: CreateIntegrationPayload): Promise<ApiResponse<IntegrationView>> {
  return requestJson<IntegrationView>(endpoints.integrations, {
    method: 'POST',
    body: JSON.stringify(payload)
  });
}

export function fetchIntegration(integrationKey: string): Promise<ApiResponse<IntegrationView>> {
  return requestJson<IntegrationView>(`/api/v1/management/integrations/${encodeURIComponent(integrationKey)}`);
}

export function updateIntegration(
  integrationKey: string,
  payload: UpdateIntegrationPayload
): Promise<ApiResponse<IntegrationView>> {
  return requestJson<IntegrationView>(`/api/v1/management/integrations/${encodeURIComponent(integrationKey)}`, {
    method: 'PATCH',
    body: JSON.stringify(payload)
  });
}

export function changeIntegrationStatus(integrationKey: string, status: string): Promise<ApiResponse<IntegrationView>> {
  return requestJson<IntegrationView>(`/api/v1/management/integrations/${encodeURIComponent(integrationKey)}/status`, {
    method: 'PATCH',
    body: JSON.stringify({ status })
  });
}

export function createSetting(payload: CreateSettingPayload): Promise<ApiResponse<SettingView>> {
  return requestJson<SettingView>(endpoints.settings, {
    method: 'POST',
    body: JSON.stringify(payload)
  });
}

export function fetchSetting(settingKey: string): Promise<ApiResponse<SettingView>> {
  return requestJson<SettingView>(`/api/v1/management/settings/${encodeURIComponent(settingKey)}`);
}

export function updateSetting(
  settingKey: string,
  payload: UpdateSettingPayload
): Promise<ApiResponse<SettingView>> {
  return requestJson<SettingView>(`/api/v1/management/settings/${encodeURIComponent(settingKey)}`, {
    method: 'PATCH',
    body: JSON.stringify(payload)
  });
}

export function changeSettingStatus(settingKey: string, status: string): Promise<ApiResponse<SettingView>> {
  return requestJson<SettingView>(`/api/v1/management/settings/${encodeURIComponent(settingKey)}/status`, {
    method: 'PATCH',
    body: JSON.stringify({ status })
  });
}

export interface SecretListQuery {
  /** 分页页码，从 0 开始（对齐后端 BasePageRequest.index） */
  index?: number;
  /** 每页条数，1-100 */
  size?: number;
  /** 搜索关键字（secretRef 模糊匹配） */
  search?: string;
}

export function listSecrets(query: SecretListQuery = {}): Promise<ApiResponse<PageResponse<SecretReferenceView>>> {
  const params = new URLSearchParams();
  if (query.index !== undefined) {
    params.set('index', String(query.index));
  }
  if (query.size !== undefined) {
    params.set('size', String(query.size));
  }
  if (query.search) {
    params.set('search', query.search);
  }
  const qs = params.toString();
  return requestJson<PageResponse<SecretReferenceView>>(`${endpoints.secrets}${qs ? `?${qs}` : ''}`);
}

export function createSecretReference(payload: CreateSecretReferencePayload): Promise<ApiResponse<SecretReferenceView>> {
  return requestJson<SecretReferenceView>(endpoints.secrets, {
    method: 'POST',
    body: JSON.stringify({
      secretRef: payload.secret_ref,
      providerCode: payload.provider_code,
      purpose: payload.purpose,
      scopeType: payload.scope_type,
      scopeId: payload.scope_id,
      value: payload.secret_value,
      secretVersion: payload.secret_version,
      expiresAt: payload.expires_at
    })
  });
}

export function rotateSecretReference(payload: RotateSecretReferencePayload): Promise<ApiResponse<SecretReferenceView>> {
  return requestJson<SecretReferenceView>(`${endpoints.secrets}/rotate`, {
    method: 'POST',
    body: JSON.stringify({
      secretRef: payload.secret_ref,
      value: payload.secret_value,
      secretVersion: payload.secret_version,
      expiresAt: payload.expires_at
    })
  });
}

export function disableSecretReference(secretRef: string): Promise<ApiResponse<SecretReferenceView>> {
  return requestJson<SecretReferenceView>(`${endpoints.secrets}/disable`, {
    method: 'POST',
    body: JSON.stringify({ secretRef })
  });
}
