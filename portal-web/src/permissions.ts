import type { CurrentUser } from './api/auth';
import type { CreatableManagementResource } from './api/management';

export type PageKey =
  | 'overview'
  | 'organizations'
  | 'users'
  | 'projects'
  | 'applications'
  | 'environments'
  | 'integrations'
  | 'audit'
  | 'settings';

export type Permission =
  | 'department:read'
  | 'department:create'
  | 'department:edit'
  | 'department:enable'
  | 'department:disable'
  | 'user:read'
  | 'user:create'
  | 'user:edit'
  | 'user:enable'
  | 'user:disable'
  | 'user:lock'
  | 'user:assign_role'
  | 'user:reset_password'
  | 'role:read'
  | 'role:bind'
  | 'role:unbind'
  | 'project:read'
  | 'project:create'
  | 'project:edit'
  | 'project:archive'
  | 'project:disable'
  | 'project:member_manage'
  | 'application:read'
  | 'application:create'
  | 'application:edit'
  | 'application:disable'
  | 'application:owner_manage'
  | 'environment:read'
  | 'environment:create'
  | 'environment:edit'
  | 'environment:disable'
  | 'environment:user_manage'
  | 'config:read'
  | 'config:edit'
  | 'audit:read'
  | 'audit:export';

export type ButtonKey =
  | 'department:create'
  | 'department:edit'
  | 'department:status'
  | 'user:create'
  | 'user:edit'
  | 'user:lifecycle'
  | 'user:role'
  | 'project:create'
  | 'project:edit'
  | 'project:member'
  | 'application:create'
  | 'application:edit'
  | 'application:owner'
  | 'environment:create'
  | 'environment:edit'
  | 'environment:user'
  | 'config:edit'
  | 'audit:export';

export type UserLifecycleAction = 'enable' | 'disable' | 'lock' | 'unlock' | 'reset-password' | 'assign-role' | 'unassign-role';

export const pageReadPermissions: Partial<Record<PageKey, Permission>> = {
  organizations: 'department:read',
  users: 'user:read',
  projects: 'project:read',
  applications: 'application:read',
  environments: 'environment:read',
  integrations: 'config:read',
  audit: 'audit:read',
  settings: 'config:read'
};

export const resourceCreatePermissions: Record<CreatableManagementResource, Permission> = {
  departments: 'department:create',
  users: 'user:create',
  projects: 'project:create',
  applications: 'application:create',
  environments: 'environment:create'
};

export const buttonPermissions: Record<ButtonKey, Permission[]> = {
  'department:create': ['department:create'],
  'department:edit': ['department:edit'],
  'department:status': ['department:enable', 'department:disable'],
  'user:create': ['user:create'],
  'user:edit': ['user:edit'],
  'user:lifecycle': ['user:enable', 'user:disable', 'user:lock', 'user:reset_password'],
  'user:role': ['user:assign_role', 'role:bind', 'role:unbind'],
  'project:create': ['project:create'],
  'project:edit': ['project:edit', 'project:archive', 'project:disable'],
  'project:member': ['project:member_manage', 'role:bind', 'role:unbind'],
  'application:create': ['application:create'],
  'application:edit': ['application:edit', 'application:disable'],
  'application:owner': ['application:owner_manage', 'role:bind', 'role:unbind'],
  'environment:create': ['environment:create'],
  'environment:edit': ['environment:edit', 'environment:disable'],
  'environment:user': ['environment:user_manage', 'role:bind', 'role:unbind'],
  'config:edit': ['config:edit'],
  'audit:export': ['audit:export']
};

export function hasPermission(user: CurrentUser | null, permission: Permission) {
  return user?.permissions?.includes(permission) ?? false;
}

export function canAccessPage(user: CurrentUser | null, page: PageKey) {
  if (page === 'overview' || !user) {
    return true;
  }
  const permission = pageReadPermissions[page];
  return permission ? hasPermission(user, permission) : true;
}

export function canUseButton(user: CurrentUser | null, button: ButtonKey) {
  return buttonPermissions[button].some((permission) => hasPermission(user, permission));
}

export function userLifecyclePermission(action: UserLifecycleAction): Permission {
  if (action === 'enable') return 'user:enable';
  if (action === 'unlock') return 'user:enable';
  if (action === 'disable') return 'user:disable';
  if (action === 'lock') return 'user:lock';
  if (action === 'reset-password') return 'user:reset_password';
  return 'user:assign_role';
}
