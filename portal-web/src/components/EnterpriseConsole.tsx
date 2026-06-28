import {
  ApiOutlined,
  AppstoreOutlined,
  AuditOutlined,
  BankOutlined,
  BarChartOutlined,
  BellOutlined,
  BookOutlined,
  BranchesOutlined,
  CloudServerOutlined,
  DatabaseOutlined,
  DeploymentUnitOutlined,
  FileTextOutlined,
  HomeOutlined,
  LinkOutlined,
  MonitorOutlined,
  ProjectOutlined,
  ReloadOutlined,
  SafetyCertificateOutlined,
  SettingOutlined,
  ThunderboltOutlined,
  UserOutlined
} from '@ant-design/icons';
import {
  App as AntApp,
  Avatar,
  Badge,
  Breadcrumb,
  Button,
  Card,
  Dropdown,
  Form,
  Input,
  Layout,
  List,
  Menu,
  Modal,
  Space,
  Typography,
  type MenuProps
} from 'antd';
import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useLocation, useNavigate } from 'react-router-dom';
import type { CurrentUser } from '../api/auth';
import { fetchHealth } from '../api/health';
import {
  assignUserRole,
  createManagementItem,
  disableUser,
  enableUser,
  exportAuditLogsCsv,
  fetchAuditOutbox,
  fetchManagementData,
  lockUser,
  resetUserPassword,
  unassignUserRole,
  unlockUser,
  type AuditOutboxFilters,
  type CreatableManagementResource,
  type ManagementData
} from '../api/management';
import {
  fetchNotifications,
  fetchUnreadNotificationCount,
  type UserNotification
} from '../api/notifications';
import { canAccessPage, type PageKey, type UserLifecycleAction } from '../permissions';
import { translate } from '../platform/i18n';
import { ApiAutomationWorkbench } from './ApiAutomationWorkbench';
import { AssetWorkbench } from './AssetWorkbench';
import { DocumentInputConsole } from './DocumentInputConsole';
import { ExecutionWorkbench } from './ExecutionWorkbench';
import { ManagementPage } from './AppManagementPage';
import { ModelAccessConsole } from './ModelAccessConsole';
import { OverviewPage } from './AppOverviewPage';
import { ReportsWorkbench } from './ReportsWorkbench';
import { TestDataWorkbench } from './TestDataWorkbench';
import { TestDesignWorkbench } from './TestDesignWorkbench';
import { UiE2eWorkbench } from './UiE2eWorkbench';

const { Header, Sider, Content } = Layout;
const { Title } = Typography;

type ResetPasswordForm = {
  confirmPassword: string;
  newPassword: string;
  username: string;
};

type PageDefinition = {
  group: string;
  icon: ReactNode;
  key: PageKey;
  label: string;
};

const pageDefinitions: PageDefinition[] = [
  { key: 'overview', label: translate('auto.k0001'), group: translate('auto.k2831'), icon: <HomeOutlined /> },
  { key: 'document-input', label: translate('auto.k0003'), group: translate('auto.k0039'), icon: <FileTextOutlined /> },
  { key: 'asset-library', label: translate('auto.k0005'), group: translate('auto.k0039'), icon: <BookOutlined /> },
  { key: 'test-design', label: translate('auto.k2817'), group: translate('auto.k0039'), icon: <ThunderboltOutlined /> },
  { key: 'api-automation', label: translate('auto.k0009'), group: translate('auto.k2832'), icon: <ApiOutlined /> },
  { key: 'ui-e2e', label: 'UI E2E', group: translate('auto.k2832'), icon: <MonitorOutlined /> },
  { key: 'execution', label: translate('auto.k0012'), group: translate('auto.k2832'), icon: <BranchesOutlined /> },
  { key: 'test-data', label: translate('auto.k0014'), group: translate('auto.k2832'), icon: <DatabaseOutlined /> },
  { key: 'reports', label: translate('auto.k2826'), group: translate('auto.k2832'), icon: <BarChartOutlined /> },
  { key: 'model-access', label: translate('auto.k0018'), group: translate('auto.k0045'), icon: <CloudServerOutlined /> },
  { key: 'organizations', label: translate('auto.k2827'), group: translate('auto.k0043'), icon: <BankOutlined /> },
  { key: 'users', label: translate('auto.k2828'), group: translate('auto.k0043'), icon: <UserOutlined /> },
  { key: 'roles', label: translate('auto.k2829'), group: translate('auto.k0043'), icon: <SafetyCertificateOutlined /> },
  { key: 'projects', label: translate('auto.k2830'), group: translate('auto.k0043'), icon: <ProjectOutlined /> },
  { key: 'applications', label: translate('auto.k0029'), group: translate('auto.k0045'), icon: <AppstoreOutlined /> },
  { key: 'environments', label: translate('auto.k0031'), group: translate('auto.k0045'), icon: <DeploymentUnitOutlined /> },
  { key: 'integrations', label: translate('auto.k0033'), group: translate('auto.k0045'), icon: <LinkOutlined /> },
  { key: 'audit', label: translate('auto.k0035'), group: translate('auto.k0045'), icon: <AuditOutlined /> },
  { key: 'settings', label: translate('auto.k0037'), group: translate('auto.k0045'), icon: <SettingOutlined /> }
];

const managementPageKeys: PageKey[] = [
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

export function EnterpriseConsole(props: {
  currentUser: CurrentUser;
  onChangePassword: () => void;
  onLogout: () => void;
  onToggleTheme: () => void;
  themeMode: 'dark' | 'light';
}) {
  const { message } = AntApp.useApp();
  const location = useLocation();
  const navigate = useNavigate();
  const activePage = activePageFromPath(location.pathname);
  const [managementData, setManagementData] = useState<ManagementData>(emptyManagementData);
  const [managementLoad, setManagementLoad] = useState<{ loading: boolean; error?: string }>({ loading: false });
  const [auditExportState, setAuditExportState] = useState<{ loading: boolean; error?: string }>({ loading: false });
  const [auditOutboxFilters, setAuditOutboxFilters] = useState<AuditOutboxFilters>({ status: '', traceId: '', search: '' });
  const [auditOutboxLoad, setAuditOutboxLoad] = useState<{ loading: boolean; error?: string }>({ loading: false });
  const [resetPasswordOpen, setResetPasswordOpen] = useState(false);
  const [resetPasswordSubmitting, setResetPasswordSubmitting] = useState(false);
  const [resetPasswordForm] = Form.useForm<ResetPasswordForm>();

  const visiblePages = useMemo(
    () => pageDefinitions.filter((page) => canAccessPage(props.currentUser, page.key)),
    [props.currentUser]
  );
  const activeDefinition = visiblePages.find((page) => page.key === activePage) ?? visiblePages[0] ?? pageDefinitions[0];
  const permissionKey = useMemo(() => (props.currentUser.permissions ?? []).join('|'), [props.currentUser.permissions]);
  const healthQuery = useQuery({
    queryFn: async () => {
      const response = await fetchHealth();
      return response.data;
    },
    queryKey: ['platform-health'],
    retry: 0
  });
  const notificationsQuery = useQuery({
    queryFn: async () => {
      const [list, unread] = await Promise.all([
        fetchNotifications({ size: 8 }),
        fetchUnreadNotificationCount()
      ]);
      return { items: list.data.items, unread: unread.data.unreadCount };
    },
    queryKey: ['enterprise-notifications', props.currentUser.user_id],
    retry: 0
  });
  const menuItems = useMemo(() => buildMenuItems(visiblePages), [visiblePages]);
  const selectedMenuKey = activeDefinition.key;
  const health = useMemo(
    () => ({
      data: healthQuery.data,
      error: healthQuery.error instanceof Error ? healthQuery.error.message : undefined,
      loading: healthQuery.isLoading
    }),
    [healthQuery.data, healthQuery.error, healthQuery.isLoading]
  );

  const refreshManagementData = useCallback(async () => {
    setManagementLoad({ loading: true });
    try {
      const response = await fetchManagementData(props.currentUser.permissions ?? []);
      setManagementData(response.data);
      setManagementLoad({ loading: false });
    } catch (error: unknown) {
      setManagementLoad({
        loading: false,
        error: error instanceof Error ? error.message : translate('auto.k0049')
      });
    }
  }, [permissionKey, props.currentUser.permissions]);

  const refreshAuditOutbox = useCallback(async (filters: AuditOutboxFilters = auditOutboxFilters) => {
    setAuditOutboxLoad({ loading: true });
    try {
      const response = await fetchAuditOutbox(filters);
      setManagementData((current) => ({ ...current, auditOutbox: response.data.items }));
      setAuditOutboxLoad({ loading: false });
    } catch (error: unknown) {
      setAuditOutboxLoad({
        loading: false,
        error: error instanceof Error ? error.message : translate('auto.k0049')
      });
    }
  }, [auditOutboxFilters]);

  useEffect(() => {
    void refreshManagementData();
  }, [props.currentUser.user_id, refreshManagementData]);

  useEffect(() => {
    if (location.pathname === '/' || location.pathname === '') {
      navigate('/overview', { replace: true });
    }
  }, [location.pathname, navigate]);

  async function onCreateManagementItem(resource: CreatableManagementResource, label: string, rawName: string) {
    const name = rawName.trim();
    if (!name) {
      return;
    }
    setManagementLoad({ loading: true });
    try {
      await createManagementItem(resource, name);
      void message.success(translate('auto.k0058', { value0: label, value1: name }));
      await refreshManagementData();
    } catch (error: unknown) {
      const errorMessage = error instanceof Error ? error.message : translate('auto.k0059');
      setManagementLoad({ loading: false, error: errorMessage });
      void message.error(errorMessage);
    }
  }

  async function onAuditExport() {
    setAuditExportState({ loading: true });
    try {
      const response = await exportAuditLogsCsv();
      const blob = new Blob([response.text], { type: response.contentType || 'text/csv;charset=utf-8' });
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement('a');
      anchor.href = url;
      anchor.download = response.filename ?? translate('auto.k0060');
      anchor.click();
      URL.revokeObjectURL(url);
      setAuditExportState({ loading: false });
      void message.success(translate('auto.k0061'));
      await refreshManagementData();
    } catch (error: unknown) {
      const errorMessage = error instanceof Error ? error.message : translate('auto.k0062');
      setAuditExportState({ loading: false, error: errorMessage });
      void message.error(errorMessage);
    }
  }

  async function onUserLifecycleAction(username: string, action: UserLifecycleAction, roleCodeInput = '') {
    if ((action === 'disable' || action === 'lock') && username === props.currentUser.username) {
      void message.error(action === 'disable' ? translate('auto.k0063') : translate('auto.k0064'));
      return;
    }

    if (action === 'reset-password') {
      openResetPasswordDialog(username);
      return;
    }

    let roleCode = '';
    if (action === 'assign-role' || action === 'unassign-role') {
      roleCode = roleCodeInput.trim();
      if (!roleCode) {
        return;
      }
    }

    setManagementLoad({ loading: true });
    try {
      const operations: Record<string, () => Promise<unknown>> = {
        enable: () => enableUser(username),
        unlock: () => unlockUser(username),
        disable: () => disableUser(username),
        lock: () => lockUser(username),
        'assign-role': () => assignUserRole(username, roleCode),
        'unassign-role': () => unassignUserRole(username, roleCode)
      };
      await operations[action]();
      const actionLabels: Record<string, string> = {
        enable: translate('auto.k0065'),
        unlock: translate('auto.k0066'),
        disable: translate('auto.k0067'),
        lock: translate('auto.k0068'),
        'reset-password': translate('auto.k0069'),
        'assign-role': translate('auto.k0070'),
        'unassign-role': translate('auto.k0071')
      };
      void message.success(translate('auto.k0072', { value0: username, value1: actionLabels[action] ?? translate('auto.k2600') }));
      await refreshManagementData();
    } catch (error: unknown) {
      const errorMessage = error instanceof Error ? error.message : translate('auto.k0073');
      setManagementLoad({ loading: false, error: errorMessage });
      void message.error(errorMessage);
    }
  }

  function openResetPasswordDialog(username: string) {
    resetPasswordForm.setFieldsValue({ username, newPassword: '', confirmPassword: '' });
    setResetPasswordOpen(true);
  }

  async function onResetPassword(values: ResetPasswordForm) {
    if (values.newPassword !== values.confirmPassword) {
      resetPasswordForm.setFields([{ name: 'confirmPassword', errors: [translate('validation.passwordNewMismatch')] }]);
      return;
    }
    setResetPasswordSubmitting(true);
    try {
      await resetUserPassword(values.username, values.newPassword);
      setResetPasswordOpen(false);
      resetPasswordForm.resetFields();
      void message.success(translate('auto.k0074', { value0: values.username }));
      await refreshManagementData();
    } catch (error: unknown) {
      void message.error(error instanceof Error ? error.message : translate('auto.k0075'));
    } finally {
      setResetPasswordSubmitting(false);
    }
  }

  function renderWorkspacePage() {
    const signedIn = true;
    switch (activeDefinition.key) {
      case 'overview':
        return <OverviewPage health={health} managementData={managementData} />;
      case 'document-input':
        return <DocumentInputConsole signedIn={signedIn} currentUser={props.currentUser} />;
      case 'asset-library':
        return <AssetWorkbench signedIn={signedIn} currentUser={props.currentUser} />;
      case 'test-design':
        return <TestDesignWorkbench signedIn={signedIn} currentUser={props.currentUser} />;
      case 'api-automation':
        return <ApiAutomationWorkbench signedIn={signedIn} currentUser={props.currentUser} />;
      case 'ui-e2e':
        return <UiE2eWorkbench signedIn={signedIn} currentUser={props.currentUser} />;
      case 'execution':
        return <ExecutionWorkbench signedIn={signedIn} currentUser={props.currentUser} />;
      case 'test-data':
        return <TestDataWorkbench signedIn={signedIn} currentUser={props.currentUser} />;
      case 'reports':
        return <ReportsWorkbench signedIn={signedIn} currentUser={props.currentUser} />;
      case 'model-access':
        return <ModelAccessConsole signedIn={signedIn} currentUser={props.currentUser} />;
      default:
        return (
          <ManagementPage
            page={activeDefinition.key}
            data={managementData}
            loadState={managementLoad}
            signedIn={signedIn}
            currentUser={props.currentUser}
            onCreate={onCreateManagementItem}
            onUserLifecycleAction={onUserLifecycleAction}
            onResetPassword={openResetPasswordDialog}
            auditExportState={auditExportState}
            onAuditExport={onAuditExport}
            auditOutboxFilters={auditOutboxFilters}
            auditOutboxLoad={auditOutboxLoad}
            onAuditOutboxFiltersChange={setAuditOutboxFilters}
            onAuditOutboxRefresh={refreshAuditOutbox}
            onRefresh={() => void refreshManagementData()}
          />
        );
    }
  }

  return (
    <Layout className="va-console">
      <Sider className="va-console-sider" breakpoint="lg" collapsedWidth={72} width={280}>
        <div className="va-console-brand">
          <div className="va-console-brand-mark">VA</div>
          <div>
            <strong>Veri Agent</strong>
            <span>{translate('app.subtitle')}</span>
          </div>
        </div>
        <nav aria-label={translate('auto.k0082')}>
          <Menu
            className="va-console-menu"
            items={menuItems}
            mode="inline"
            selectedKeys={[selectedMenuKey]}
            onClick={({ key }) => navigate(`/${key}`)}
          />
        </nav>
      </Sider>
      <Layout>
        <Header className="va-console-header">
          <Breadcrumb
            className="va-console-breadcrumb"
            items={[
              { title: activeDefinition.group },
              { title: activeDefinition.label }
            ]}
          />
          <Space size="middle">
            <NotificationDropdown
              items={notificationsQuery.data?.items ?? []}
              loading={notificationsQuery.isLoading}
              unread={notificationsQuery.data?.unread ?? 0}
            />
            <Button aria-label={translate('auto.k0083')} onClick={props.onToggleTheme}>
              {props.themeMode === 'dark' ? translate('auto.k0085') : translate('auto.k0084')}
            </Button>
            <Dropdown
              menu={{
                items: [
                  { key: 'password', label: translate('auth.passwordChangeTitle'), onClick: props.onChangePassword },
                  { key: 'logout', danger: true, label: translate('auto.k0080'), onClick: props.onLogout }
                ]
              }}
            >
              <Button className="va-console-user" type="text">
                <Avatar size="small">{avatarText(props.currentUser)}</Avatar>
                <span>{props.currentUser.display_name || props.currentUser.username}</span>
              </Button>
            </Dropdown>
          </Space>
        </Header>
        <Content className="va-console-content">
          <section className="va-console-hero">
            <div>
              <Title level={2}>{activeDefinition.label}</Title>
            </div>
            {(activeDefinition.key === 'overview' || managementPageKeys.includes(activeDefinition.key)) ? (
              <Button icon={<ReloadOutlined />} loading={managementLoad.loading || healthQuery.isFetching} onClick={() => {
                void refreshManagementData();
                if (activeDefinition.key === 'overview') {
                  void healthQuery.refetch();
                }
              }}>
                {translate('auto.k2833')}
              </Button>
            ) : null}
          </section>

          <div className="va-console-function-workspace">
            {renderWorkspacePage()}
          </div>
        </Content>
      </Layout>
      <ResetPasswordModal
        form={resetPasswordForm}
        open={resetPasswordOpen}
        submitting={resetPasswordSubmitting}
        onCancel={() => setResetPasswordOpen(false)}
        onSubmit={(values) => void onResetPassword(values)}
      />
    </Layout>
  );
}

function ResetPasswordModal(props: {
  form: ReturnType<typeof Form.useForm<ResetPasswordForm>>[0];
  open: boolean;
  submitting: boolean;
  onCancel: () => void;
  onSubmit: (values: ResetPasswordForm) => void;
}) {
  return (
    <Modal
      centered
      footer={null}
      mask={{ closable: !props.submitting }}
      open={props.open}
      title={translate('auth.passwordResetTitle')}
      onCancel={props.onCancel}
    >
      <Form form={props.form} layout="vertical" requiredMark={false} onFinish={props.onSubmit}>
        <Form.Item name="username" label={translate('auth.account')}>
          <Input disabled />
        </Form.Item>
        <Form.Item
          name="newPassword"
          label={translate('auto.k0087')}
          rules={[{ min: 10, message: translate('validation.passwordNewMin') }, { required: true, message: translate('auto.k0087') }]}
        >
          <Input.Password autoComplete="new-password" />
        </Form.Item>
        <Form.Item
          name="confirmPassword"
          label={translate('auth.passwordConfirm')}
          rules={[{ required: true, message: translate('validation.passwordConfirmRequired') }]}
        >
          <Input.Password autoComplete="new-password" />
        </Form.Item>
        <div className="va-modal-actions">
          <Button disabled={props.submitting} onClick={props.onCancel}>{translate('actions.cancel')}</Button>
          <Button htmlType="submit" loading={props.submitting} type="primary">{translate('auth.resetSubmit')}</Button>
        </div>
      </Form>
    </Modal>
  );
}

function NotificationDropdown(props: { items: UserNotification[]; loading: boolean; unread: number }) {
  return (
    <Dropdown
      trigger={['click']}
      popupRender={() => (
        <Card className="va-notification-card" title={translate('auto.k2840')}>
          <List
            dataSource={props.items}
            loading={props.loading}
            locale={{ emptyText: translate('auto.k2841') }}
            renderItem={(item) => (
              <List.Item>
                <List.Item.Meta
                  title={<Space><span>{item.title}</span>{item.unread ? <Badge status="processing" /> : null}</Space>}
                  description={item.body}
                />
              </List.Item>
            )}
          />
        </Card>
      )}
    >
      <Button aria-label={translate('auto.k2840')} icon={<BellOutlined />} type="text">
        <Badge count={props.unread} size="small" />
      </Button>
    </Dropdown>
  );
}

function buildMenuItems(pages: PageDefinition[]): MenuProps['items'] {
  const groups = Array.from(new Set(pages.map((page) => page.group)));
  return groups.map((group) => ({
    key: group,
    label: group,
    type: 'group',
    children: pages
      .filter((page) => page.group === group)
      .map((page) => ({
        icon: page.icon,
        key: page.key,
        label: page.label
      }))
  }));
}

function activePageFromPath(pathname: string): PageKey {
  const pageKey = pathname.replace(/^\/+/, '').split('/')[0];
  return pageDefinitions.some((page) => page.key === pageKey) ? pageKey as PageKey : 'overview';
}

function avatarText(user: CurrentUser) {
  return (user.display_name || user.username || 'U').slice(0, 2).toUpperCase();
}
