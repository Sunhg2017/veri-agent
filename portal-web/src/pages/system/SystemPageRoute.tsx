import { useManagementConsole } from '../../layouts/managementConsoleContext';
import { useAppSessionStore } from '../../platform/appStore';
import type { PageKey } from '../../permissions';
import type { ManagementPageProps } from '../../components/management/shared';
import { ApplicationsPage } from './ApplicationsPage';
import { AuditPage } from './AuditPage';
import { EnvironmentsPage } from './EnvironmentsPage';
import { IntegrationsPage } from './IntegrationsPage';
import { OrganizationsPage } from './OrganizationsPage';
import { ProjectsPage } from './ProjectsPage';
import { RolesPage } from './RolesPage';
import { SettingsPage } from './SettingsPage';
import { UsersPage } from './UsersPage';

/** 系统管理页路由包装：从 ConsoleLayout 上下文注入管理域数据与操作 */
export function SystemPageRoute(props: { page: PageKey }) {
  const management = useManagementConsole();
  const currentUser = useAppSessionStore((state) => state.currentUser);

  const pageProps: ManagementPageProps = {
    page: props.page,
    data: management.data,
    loadState: management.loadState,
    signedIn: true,
    currentUser,
    onCreate: management.onCreate,
    onUserLifecycleAction: management.onUserLifecycleAction,
    onResetPassword: management.onResetPassword,
    auditExportState: management.auditExportState,
    onAuditExport: management.onAuditExport,
    auditOutboxFilters: management.auditOutboxFilters,
    auditOutboxLoad: management.auditOutboxLoad,
    onAuditOutboxFiltersChange: management.onAuditOutboxFiltersChange,
    onAuditOutboxRefresh: management.onAuditOutboxRefresh,
    onRefresh: management.onRefresh
  };

  switch (props.page) {
    case 'organizations':
      return <OrganizationsPage {...pageProps} />;
    case 'users':
      return <UsersPage {...pageProps} />;
    case 'roles':
      return <RolesPage {...pageProps} />;
    case 'projects':
      return <ProjectsPage {...pageProps} />;
    case 'applications':
      return <ApplicationsPage {...pageProps} />;
    case 'environments':
      return <EnvironmentsPage {...pageProps} />;
    case 'integrations':
      return <IntegrationsPage {...pageProps} />;
    case 'audit':
      return <AuditPage {...pageProps} />;
    case 'settings':
      return <SettingsPage {...pageProps} />;
    default:
      return null;
  }
}
