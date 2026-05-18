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
  | 'user:unlock'
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

/**
 * Button permission rules.
 *
 * Each entry is an array of "operation groups". An operation group is an
 * array of permissions where ALL must be satisfied for that operation.
 * The button is shown if the user qualifies for ANY operation group.
 *
 * For simple single-permission buttons, just wrap one permission in one group:
 *   'department:create': [['department:create']]
 *
 * For compound operations requiring AND:
 *   'user:role': [
 *     ['user:assign_role', 'role:bind'],     // assign-role
 *     ['user:assign_role', 'role:unbind'],   // unassign-role
 *   ]
 *   → shown if user has (assign_role AND bind) OR (assign_role AND unbind)
 */
const buttonPermissionGroups: Record<ButtonKey, Permission[][]> = {
  // Single-permission (OR-equivalent — any one group suffices)
  'department:create': [['department:create']],
  'department:edit': [['department:edit']],
  'department:status': [['department:enable'], ['department:disable']],
  'user:create': [['user:create']],
  'user:edit': [['user:edit']],
  'user:lifecycle': [['user:enable'], ['user:disable'], ['user:lock'], ['user:unlock'], ['user:reset_password']],
  'project:create': [['project:create']],
  'project:edit': [['project:edit'], ['project:archive'], ['project:disable']],
  'application:create': [['application:create']],
  'application:edit': [['application:edit'], ['application:disable']],
  'environment:create': [['environment:create']],
  'environment:edit': [['environment:edit'], ['environment:disable']],
  'config:edit': [['config:edit']],
  'audit:export': [['audit:export']],

  // Compound operations requiring ALL permissions in a group (AND logic)
  'user:role': [
    ['user:assign_role', 'role:bind'],     // assign-role
    ['user:assign_role', 'role:unbind'],   // unassign-role
  ],
  'project:member': [
    ['project:member_manage', 'role:bind'],   // add member
    ['project:member_manage', 'role:unbind'], // remove member
  ],
  'application:owner': [
    ['application:owner_manage', 'role:bind'],   // add owner
    ['application:owner_manage', 'role:unbind'], // remove owner
  ],
  'environment:user': [
    ['environment:user_manage', 'role:bind'],   // add user
    ['environment:user_manage', 'role:unbind'], // remove user
  ],
};

/** Legacy flat list kept for backward-compatible access patterns. */
export const buttonPermissions: Record<ButtonKey, Permission[]> = {} as Record<ButtonKey, Permission[]>;
for (const [key, groups] of Object.entries(buttonPermissionGroups)) {
  const flat: Permission[] = [];
  for (const group of groups) {
    for (const p of group) {
      if (!flat.includes(p)) flat.push(p);
    }
  }
  (buttonPermissions as Record<string, Permission[]>)[key] = flat;
}

export function hasPermission(user: CurrentUser | null, permission: Permission) {
  return user?.permissions?.includes(permission) ?? false;
}

function hasAllPermissions(user: CurrentUser | null, permissions: Permission[]) {
  return permissions.every((p) => hasPermission(user, p));
}

export function canAccessPage(user: CurrentUser | null, page: PageKey) {
  if (page === 'overview' || !user) {
    return true;
  }
  const permission = pageReadPermissions[page];
  return permission ? hasPermission(user, permission) : true;
}

/**
 * Returns true if the user satisfies ALL permissions in AT LEAST ONE
 * operation group for the given button.
 *
 * This fixes the previous bug where .some() (OR logic) was used for
 * compound operations (e.g., 'user:role' requires user:assign_role AND
 * role:bind/unbind — having only one of them is insufficient).
 */
export function canUseButton(user: CurrentUser | null, button: ButtonKey) {
  const groups = buttonPermissionGroups[button];
  return groups.some((group) => hasAllPermissions(user, group));
}

export function userLifecyclePermission(action: UserLifecycleAction): Permission {
  if (action === 'enable') return 'user:enable';
  if (action === 'unlock') return 'user:unlock';
  if (action === 'disable') return 'user:disable';
  if (action === 'lock') return 'user:lock';
  if (action === 'reset-password') return 'user:reset_password';
  return 'user:assign_role';
}
