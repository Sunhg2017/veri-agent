import { describe, expect, it } from 'vitest';
import type { CurrentUser } from './api/auth';
import {
  canAccessPage,
  canUseButton,
  hasPermission,
  resourceCreatePermissions,
  userLifecyclePermission
} from './permissions';

function user(permissions: string[]): CurrentUser {
  return {
    user_id: 'user-1',
    username: 'tester',
    display_name: 'Tester',
    must_change_password: false,
    roles: ['Tester'],
    permissions
  };
}

describe('WP1 permission helpers', () => {
  it('keeps overview accessible before login and hides protected pages', () => {
    expect(canAccessPage(null, 'overview')).toBe(true);
    expect(canAccessPage(null, 'users')).toBe(true);
    expect(canAccessPage(user([]), 'overview')).toBe(true);
    expect(canAccessPage(user([]), 'users')).toBe(false);
  });

  it('maps read permissions to protected management pages', () => {
    const currentUser = user(['user:read', 'audit:read', 'config:read']);

    expect(canAccessPage(currentUser, 'users')).toBe(true);
    expect(canAccessPage(currentUser, 'audit')).toBe(true);
    expect(canAccessPage(currentUser, 'settings')).toBe(true);
    expect(canAccessPage(currentUser, 'projects')).toBe(false);
  });

  it('maps creatable resources to backend permission names', () => {
    expect(resourceCreatePermissions.departments).toBe('department:create');
    expect(resourceCreatePermissions.users).toBe('user:create');
    expect(resourceCreatePermissions.projects).toBe('project:create');
    expect(resourceCreatePermissions.applications).toBe('application:create');
    expect(resourceCreatePermissions.environments).toBe('environment:create');
  });

  it('recognizes resource collaboration permissions', () => {
    const currentUser = user(['project:member_manage', 'application:owner_manage', 'environment:user_manage']);

    expect(hasPermission(currentUser, 'project:member_manage')).toBe(true);
    expect(hasPermission(currentUser, 'application:owner_manage')).toBe(true);
    expect(hasPermission(currentUser, 'environment:user_manage')).toBe(true);
  });

  it('recognizes resource lifecycle permissions', () => {
    const currentUser = user([
      'department:edit',
      'department:enable',
      'department:disable',
      'user:edit',
      'project:edit',
      'project:archive',
      'project:disable',
      'application:edit',
      'application:disable',
      'environment:edit',
      'environment:disable',
      'config:edit'
    ]);

    expect(hasPermission(currentUser, 'department:edit')).toBe(true);
    expect(hasPermission(currentUser, 'department:enable')).toBe(true);
    expect(hasPermission(currentUser, 'department:disable')).toBe(true);
    expect(hasPermission(currentUser, 'user:edit')).toBe(true);
    expect(hasPermission(currentUser, 'project:edit')).toBe(true);
    expect(hasPermission(currentUser, 'project:archive')).toBe(true);
    expect(hasPermission(currentUser, 'project:disable')).toBe(true);
    expect(hasPermission(currentUser, 'application:edit')).toBe(true);
    expect(hasPermission(currentUser, 'application:disable')).toBe(true);
    expect(hasPermission(currentUser, 'environment:edit')).toBe(true);
    expect(hasPermission(currentUser, 'environment:disable')).toBe(true);
    expect(hasPermission(currentUser, 'config:edit')).toBe(true);
  });

  it('maps button permissions to any allowed backend action', () => {
    expect(canUseButton(user(['project:member_manage']), 'project:member')).toBe(true);
    expect(canUseButton(user(['role:bind']), 'project:member')).toBe(true);
    expect(canUseButton(user(['project:read']), 'project:member')).toBe(false);
    expect(canUseButton(user(['audit:export']), 'audit:export')).toBe(true);
  });

  it('maps user lifecycle actions to required permissions', () => {
    expect(userLifecyclePermission('enable')).toBe('user:enable');
    expect(userLifecyclePermission('unlock')).toBe('user:enable');
    expect(userLifecyclePermission('disable')).toBe('user:disable');
    expect(userLifecyclePermission('lock')).toBe('user:lock');
    expect(userLifecyclePermission('reset-password')).toBe('user:reset_password');
    expect(userLifecyclePermission('assign-role')).toBe('user:assign_role');
    expect(userLifecyclePermission('unassign-role')).toBe('user:assign_role');
  });

  it('checks optional user permission lists defensively', () => {
    expect(hasPermission(null, 'user:read')).toBe(false);
    expect(hasPermission(user([]), 'user:read')).toBe(false);
    expect(hasPermission(user(['user:read']), 'user:read')).toBe(true);
  });
});
