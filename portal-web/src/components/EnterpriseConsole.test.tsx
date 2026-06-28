import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import { App as AntApp } from 'antd';
import { HashRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { CurrentUser } from '../api/auth';
import type { ManagementData } from '../api/management';
import type { PageKey } from '../permissions';
import { EnterpriseConsole } from './EnterpriseConsole';

vi.mock('../api/health', () => ({
  fetchHealth: vi.fn().mockResolvedValue({
    code: 'OK',
    data: { service: 'platform-api', status: 'UP' },
    message: 'OK',
    trace_id: 'trace-health'
  })
}));

vi.mock('../api/notifications', () => ({
  fetchNotifications: vi.fn().mockResolvedValue({
    code: 'OK',
    data: { items: [] },
    message: 'OK',
    trace_id: 'trace-notifications'
  }),
  fetchUnreadNotificationCount: vi.fn().mockResolvedValue({
    code: 'OK',
    data: { unreadCount: 0 },
    message: 'OK',
    trace_id: 'trace-unread'
  })
}));

vi.mock('../api/management', async () => {
  const actual = await vi.importActual<typeof import('../api/management')>('../api/management');
  const emptyManagementData: ManagementData = {
    applications: [],
    auditLogs: [],
    auditOutbox: [],
    departments: [],
    environments: [],
    integrations: [],
    permissions: [],
    projects: [],
    roles: [],
    secrets: [],
    settings: [],
    users: []
  };
  return {
    ...actual,
    fetchManagementData: vi.fn().mockResolvedValue({
      code: 'OK',
      data: emptyManagementData,
      message: 'OK',
      trace_id: 'trace-management'
    })
  };
});

vi.mock('./AppOverviewPage', () => ({
  OverviewPage: () => <div data-testid="real-page-overview">overview workbench</div>
}));

vi.mock('./DocumentInputConsole', () => ({
  DocumentInputConsole: () => <div data-testid="real-page-document-input">document input actions</div>
}));

vi.mock('./AssetWorkbench', () => ({
  AssetWorkbench: () => <div data-testid="real-page-asset-library">asset actions</div>
}));

vi.mock('./TestDesignWorkbench', () => ({
  TestDesignWorkbench: () => <div data-testid="real-page-test-design">test design actions</div>
}));

vi.mock('./ApiAutomationWorkbench', () => ({
  ApiAutomationWorkbench: () => <div data-testid="real-page-api-automation">api automation actions</div>
}));

vi.mock('./UiE2eWorkbench', () => ({
  UiE2eWorkbench: () => <div data-testid="real-page-ui-e2e">ui e2e actions</div>
}));

vi.mock('./ExecutionWorkbench', () => ({
  ExecutionWorkbench: () => <div data-testid="real-page-execution">execution actions</div>
}));

vi.mock('./TestDataWorkbench', () => ({
  TestDataWorkbench: () => <div data-testid="real-page-test-data">test data actions</div>
}));

vi.mock('./ReportsWorkbench', () => ({
  ReportsWorkbench: () => <div data-testid="real-page-reports">report actions</div>
}));

vi.mock('./ModelAccessConsole', () => ({
  ModelAccessConsole: () => <div data-testid="real-page-model-access">model access actions</div>
}));

vi.mock('./AppManagementPage', () => ({
  ManagementPage: (props: { page: PageKey }) => <div data-testid={`real-page-${props.page}`}>management actions</div>
}));

const allPermissions: NonNullable<CurrentUser['permissions']> = [
  'department:read',
  'department:create',
  'department:edit',
  'department:enable',
  'department:disable',
  'user:read',
  'user:create',
  'user:edit',
  'user:enable',
  'user:disable',
  'user:lock',
  'user:unlock',
  'user:assign_role',
  'user:reset_password',
  'role:read',
  'role:create',
  'role:edit',
  'role:bind',
  'role:unbind',
  'project:read',
  'project:create',
  'project:edit',
  'project:archive',
  'project:disable',
  'project:member_manage',
  'application:read',
  'application:create',
  'application:edit',
  'application:disable',
  'application:owner_manage',
  'environment:read',
  'environment:create',
  'environment:edit',
  'environment:disable',
  'environment:user_manage',
  'config:read',
  'config:edit',
  'secret:reference',
  'secret:read',
  'secret:manage',
  'secret:rotate',
  'secret:disable',
  'audit:read',
  'audit:export',
  'requirementInput:read',
  'requirementInput:manage',
  'requirementInput:import',
  'requirementInput:candidate_review',
  'requirementInput:publish',
  'requirementInput:webhook_replay',
  'asset:read',
  'asset:manage',
  'asset:review',
  'asset:export',
  'testDesign:read',
  'testDesign:generate',
  'testDesign:review',
  'testDesign:publish',
  'testDesign:export',
  'testDesign:policy_manage',
  'apiAutomation:read',
  'apiAutomation:import',
  'apiAutomation:generate',
  'apiAutomation:review',
  'apiAutomation:execute',
  'apiAutomation:export',
  'uiE2e:read',
  'uiE2e:manage',
  'uiE2e:review',
  'uiE2e:execute',
  'uiE2e:export',
  'uiE2e:flaky',
  'execution:read',
  'execution:manage',
  'execution:trigger',
  'execution:admin',
  'execution:export',
  'testData:read',
  'testData:manage',
  'testData:lease',
  'testData:cleanup',
  'testData:export',
  'report:read',
  'report:generate',
  'report:diagnose',
  'report:export',
  'report:manage',
  'modelAccess:read',
  'modelAccess:manage',
  'modelAccess:export'
];

const currentUser: CurrentUser = {
  display_name: 'Admin',
  must_change_password: false,
  permissions: allPermissions,
  roles: ['ADMIN'],
  user_id: 'u-1',
  username: 'admin'
};

const routedPages: PageKey[] = [
  'overview',
  'document-input',
  'asset-library',
  'test-design',
  'api-automation',
  'ui-e2e',
  'execution',
  'test-data',
  'reports',
  'model-access',
  'organizations',
  'users',
  'roles',
  'projects',
  'applications',
  'environments',
  'integrations',
  'audit',
  'settings'
];

function renderConsole(page: PageKey) {
  window.location.hash = `#/${page}`;
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false }
    }
  });

  return render(
    <QueryClientProvider client={queryClient}>
      <HashRouter>
        <AntApp>
          <EnterpriseConsole
            currentUser={currentUser}
            themeMode="light"
            onChangePassword={vi.fn()}
            onLogout={vi.fn()}
            onToggleTheme={vi.fn()}
          />
        </AntApp>
      </HashRouter>
    </QueryClientProvider>
  );
}

describe('EnterpriseConsole routing', () => {
  beforeEach(() => {
    window.location.hash = '#/overview';
  });

  it.each(routedPages)('renders the real feature workspace for %s', async (page) => {
    renderConsole(page);
    await waitFor(() => expect(screen.getByTestId(`real-page-${page}`)).toBeInTheDocument());
  });
});
