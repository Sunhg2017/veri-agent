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
    const currentUser = user([
      'user:read',
      'role:read',
      'audit:read',
      'config:read',
      'requirementInput:read',
      'asset:read',
      'testDesign:read',
      'apiAutomation:read',
      'execution:read',
      'testData:read',
      'report:read',
      'modelAccess:read'
    ]);

    expect(canAccessPage(currentUser, 'users')).toBe(true);
    expect(canAccessPage(currentUser, 'roles')).toBe(true);
    expect(canAccessPage(currentUser, 'audit')).toBe(true);
    expect(canAccessPage(currentUser, 'settings')).toBe(true);
    expect(canAccessPage(currentUser, 'document-input')).toBe(true);
    expect(canAccessPage(currentUser, 'asset-library')).toBe(true);
    expect(canAccessPage(currentUser, 'test-design')).toBe(true);
    expect(canAccessPage(currentUser, 'api-automation')).toBe(true);
    expect(canAccessPage(currentUser, 'execution')).toBe(true);
    expect(canAccessPage(currentUser, 'test-data')).toBe(true);
    expect(canAccessPage(currentUser, 'reports')).toBe(true);
    expect(canAccessPage(currentUser, 'model-access')).toBe(true);
    expect(canAccessPage(currentUser, 'projects')).toBe(false);
  });

  it('requires WP4 read permission for the document input console', () => {
    expect(canAccessPage(user(['config:read']), 'document-input')).toBe(false);
    expect(canAccessPage(user(['requirementInput:read']), 'document-input')).toBe(true);
  });

  it('requires WP3 asset read permission for the asset workbench', () => {
    expect(canAccessPage(user(['requirementInput:read']), 'asset-library')).toBe(false);
    expect(canAccessPage(user(['asset:read']), 'asset-library')).toBe(true);
  });

  it('requires WP5 test design read permission for the test design workbench', () => {
    expect(canAccessPage(user(['asset:read']), 'test-design')).toBe(false);
    expect(canAccessPage(user(['testDesign:read']), 'test-design')).toBe(true);
  });

  it('requires WP6 API automation read permission for the API automation workbench', () => {
    expect(canAccessPage(user(['testDesign:read']), 'api-automation')).toBe(false);
    expect(canAccessPage(user(['apiAutomation:read']), 'api-automation')).toBe(true);
  });

  it('requires WP9 execution read permission for the execution workbench', () => {
    expect(canAccessPage(user(['apiAutomation:read']), 'execution')).toBe(false);
    expect(canAccessPage(user(['execution:read']), 'execution')).toBe(true);
  });

  it('requires WP8 test data read permission for the test data workbench', () => {
    expect(canAccessPage(user(['execution:read']), 'test-data')).toBe(false);
    expect(canAccessPage(user(['testData:read']), 'test-data')).toBe(true);
  });

  it('requires WP10 report read permission for the report workbench', () => {
    expect(canAccessPage(user(['execution:read']), 'reports')).toBe(false);
    expect(canAccessPage(user(['report:read']), 'reports')).toBe(true);
  });

  it('requires WP2 model access read permission for the model access console', () => {
    expect(canAccessPage(user(['asset:read']), 'model-access')).toBe(false);
    expect(canAccessPage(user(['modelAccess:read']), 'model-access')).toBe(true);
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
      'role:create',
      'role:edit',
      'project:edit',
      'project:archive',
      'project:disable',
      'application:edit',
      'application:disable',
      'environment:edit',
      'environment:disable',
      'config:edit',
      'secret:reference',
      'secret:read',
      'secret:manage',
      'secret:rotate',
      'secret:disable'
    ]);

    expect(hasPermission(currentUser, 'department:edit')).toBe(true);
    expect(hasPermission(currentUser, 'department:enable')).toBe(true);
    expect(hasPermission(currentUser, 'department:disable')).toBe(true);
    expect(hasPermission(currentUser, 'user:edit')).toBe(true);
    expect(hasPermission(currentUser, 'role:create')).toBe(true);
    expect(hasPermission(currentUser, 'role:edit')).toBe(true);
    expect(hasPermission(currentUser, 'project:edit')).toBe(true);
    expect(hasPermission(currentUser, 'project:archive')).toBe(true);
    expect(hasPermission(currentUser, 'project:disable')).toBe(true);
    expect(hasPermission(currentUser, 'application:edit')).toBe(true);
    expect(hasPermission(currentUser, 'application:disable')).toBe(true);
    expect(hasPermission(currentUser, 'environment:edit')).toBe(true);
    expect(hasPermission(currentUser, 'environment:disable')).toBe(true);
    expect(hasPermission(currentUser, 'config:edit')).toBe(true);
    expect(hasPermission(currentUser, 'secret:reference')).toBe(true);
    expect(hasPermission(currentUser, 'secret:read')).toBe(true);
    expect(hasPermission(currentUser, 'secret:manage')).toBe(true);
    expect(hasPermission(currentUser, 'secret:rotate')).toBe(true);
    expect(hasPermission(currentUser, 'secret:disable')).toBe(true);
  });

  it('uses AND logic for compound button permissions', () => {
    // 'project:member' requires project:member_manage AND (role:bind OR role:unbind)
    expect(canUseButton(user(['project:member_manage', 'role:bind']), 'project:member')).toBe(true);
    expect(canUseButton(user(['project:member_manage', 'role:unbind']), 'project:member')).toBe(true);
    // A user with only one of the two required permissions cannot use the button
    expect(canUseButton(user(['project:member_manage']), 'project:member')).toBe(false);
    expect(canUseButton(user(['role:bind']), 'project:member')).toBe(false);
    // Unrelated permission should not grant access
    expect(canUseButton(user(['project:read']), 'project:member')).toBe(false);
  });

  it('uses OR logic for single-permission buttons', () => {
    expect(canUseButton(user(['audit:export']), 'audit:export')).toBe(true);
    expect(canUseButton(user(['config:read']), 'audit:export')).toBe(false);
    expect(canUseButton(user(['role:create']), 'role:create')).toBe(true);
    expect(canUseButton(user(['role:read']), 'role:create')).toBe(false);
    expect(canUseButton(user(['role:edit']), 'role:edit')).toBe(true);
    expect(canUseButton(user(['role:edit']), 'role:status')).toBe(true);
    expect(canUseButton(user(['asset:manage']), 'asset:requirement_create')).toBe(true);
    expect(canUseButton(user(['asset:manage']), 'asset:requirement_edit')).toBe(true);
    expect(canUseButton(user(['asset:manage']), 'asset:api_create')).toBe(true);
    expect(canUseButton(user(['asset:manage']), 'asset:api_edit')).toBe(true);
    expect(canUseButton(user(['asset:manage']), 'asset:page_create')).toBe(true);
    expect(canUseButton(user(['asset:manage']), 'asset:page_edit')).toBe(true);
    expect(canUseButton(user(['asset:manage']), 'asset:flow_create')).toBe(true);
    expect(canUseButton(user(['asset:manage']), 'asset:flow_edit')).toBe(true);
    expect(canUseButton(user(['asset:manage']), 'asset:case_create')).toBe(true);
    expect(canUseButton(user(['asset:manage']), 'asset:case_edit')).toBe(true);
    expect(canUseButton(user(['asset:review']), 'asset:requirement_review')).toBe(true);
    expect(canUseButton(user(['asset:review']), 'asset:case_review')).toBe(true);
    expect(canUseButton(user(['asset:read']), 'asset:requirement_edit')).toBe(false);
    expect(canUseButton(user(['asset:read']), 'asset:api_edit')).toBe(false);
    expect(canUseButton(user(['asset:read']), 'asset:page_edit')).toBe(false);
    expect(canUseButton(user(['asset:read']), 'asset:flow_edit')).toBe(false);
    expect(canUseButton(user(['asset:read']), 'asset:case_edit')).toBe(false);
    expect(canUseButton(user(['asset:export']), 'asset:export')).toBe(true);
    expect(canUseButton(user(['testDesign:generate']), 'testDesign:generate')).toBe(true);
    expect(canUseButton(user(['testDesign:read']), 'testDesign:generate')).toBe(false);
    expect(canUseButton(user(['testDesign:review']), 'testDesign:review')).toBe(true);
    expect(canUseButton(user(['testDesign:publish']), 'testDesign:publish')).toBe(true);
    expect(canUseButton(user(['testDesign:export']), 'testDesign:export')).toBe(true);
    expect(canUseButton(user(['testDesign:policy_manage']), 'testDesign:policy_manage')).toBe(true);
    expect(canUseButton(user(['testDesign:read']), 'testDesign:policy_manage')).toBe(false);
    expect(canUseButton(user(['apiAutomation:import']), 'apiAutomation:import')).toBe(true);
    expect(canUseButton(user(['apiAutomation:read']), 'apiAutomation:import')).toBe(false);
    expect(canUseButton(user(['apiAutomation:generate']), 'apiAutomation:generate')).toBe(true);
    expect(canUseButton(user(['apiAutomation:review']), 'apiAutomation:review')).toBe(true);
    expect(canUseButton(user(['apiAutomation:execute']), 'apiAutomation:execute')).toBe(true);
    expect(canUseButton(user(['apiAutomation:export']), 'apiAutomation:export')).toBe(true);
    expect(canUseButton(user(['execution:manage']), 'execution:manage')).toBe(true);
    expect(canUseButton(user(['execution:read']), 'execution:manage')).toBe(false);
    expect(canUseButton(user(['execution:trigger']), 'execution:trigger')).toBe(true);
    expect(canUseButton(user(['execution:read']), 'execution:trigger')).toBe(false);
    expect(canUseButton(user(['execution:admin']), 'execution:admin')).toBe(true);
    expect(canUseButton(user(['execution:export']), 'execution:export')).toBe(true);
    expect(canUseButton(user(['testData:manage']), 'testData:manage')).toBe(true);
    expect(canUseButton(user(['testData:read']), 'testData:manage')).toBe(false);
    expect(canUseButton(user(['testData:lease']), 'testData:lease')).toBe(true);
    expect(canUseButton(user(['testData:cleanup']), 'testData:cleanup')).toBe(true);
    expect(canUseButton(user(['testData:export']), 'testData:export')).toBe(true);
    expect(canUseButton(user(['report:generate']), 'report:generate')).toBe(true);
    expect(canUseButton(user(['report:read']), 'report:generate')).toBe(false);
    expect(canUseButton(user(['report:diagnose']), 'report:diagnose')).toBe(true);
    expect(canUseButton(user(['report:export']), 'report:export')).toBe(true);
    expect(canUseButton(user(['report:manage']), 'report:manage')).toBe(true);
    expect(canUseButton(user(['modelAccess:manage']), 'modelAccess:provider_manage')).toBe(true);
    expect(canUseButton(user(['modelAccess:manage']), 'modelAccess:prompt_manage')).toBe(true);
    expect(canUseButton(user(['modelAccess:manage']), 'modelAccess:policy_manage')).toBe(true);
    expect(canUseButton(user(['modelAccess:read']), 'modelAccess:provider_manage')).toBe(false);
    expect(canUseButton(user(['modelAccess:read']), 'modelAccess:policy_manage')).toBe(false);
    expect(canUseButton(user(['modelAccess:export']), 'modelAccess:export')).toBe(true);
    expect(canUseButton(user(['secret:manage']), 'secret:create')).toBe(true);
    expect(canUseButton(user(['secret:read']), 'secret:create')).toBe(false);
    expect(canUseButton(user(['secret:rotate']), 'secret:rotate')).toBe(true);
    expect(canUseButton(user(['secret:disable']), 'secret:disable')).toBe(true);
  });

  it('handles status-change buttons with groups', () => {
    // department:status → shown if user can enable OR disable
    expect(canUseButton(user(['department:enable']), 'department:status')).toBe(true);
    expect(canUseButton(user(['department:disable']), 'department:status')).toBe(true);
    expect(canUseButton(user(['department:read']), 'department:status')).toBe(false);
  });

  it('handles lifecycle buttons correctly', () => {
    // user:lifecycle → shown if user can perform ANY lifecycle action
    expect(canUseButton(user(['user:enable']), 'user:lifecycle')).toBe(true);
    expect(canUseButton(user(['user:lock']), 'user:lifecycle')).toBe(true);
    expect(canUseButton(user(['user:unlock']), 'user:lifecycle')).toBe(true);
    expect(canUseButton(user(['user:disable']), 'user:lifecycle')).toBe(true);
    expect(canUseButton(user(['user:reset_password']), 'user:lifecycle')).toBe(true);
    expect(canUseButton(user(['user:read']), 'user:lifecycle')).toBe(false);
  });

  it('handles compound role binding buttons', () => {
    // 'user:role' requires user:assign_role AND (role:bind OR role:unbind)
    expect(canUseButton(user(['user:assign_role', 'role:bind']), 'user:role')).toBe(true);
    expect(canUseButton(user(['user:assign_role', 'role:unbind']), 'user:role')).toBe(true);
    expect(canUseButton(user(['user:assign_role']), 'user:role')).toBe(false);
    expect(canUseButton(user(['role:bind']), 'user:role')).toBe(false);
  });

  it('handles compound app owner button', () => {
    // 'application:owner' requires app:owner_manage AND (role:bind OR role:unbind)
    expect(canUseButton(user(['application:owner_manage', 'role:bind']), 'application:owner')).toBe(true);
    expect(canUseButton(user(['application:owner_manage']), 'application:owner')).toBe(false);
  });

  it('handles compound environment user button', () => {
    // 'environment:user' requires env:user_manage AND (role:bind OR role:unbind)
    expect(canUseButton(user(['environment:user_manage', 'role:unbind']), 'environment:user')).toBe(true);
    expect(canUseButton(user(['environment:user_manage']), 'environment:user')).toBe(false);
  });

  it('maps user lifecycle actions to required permissions', () => {
    expect(userLifecyclePermission('enable')).toBe('user:enable');
    expect(userLifecyclePermission('unlock')).toBe('user:unlock');
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
