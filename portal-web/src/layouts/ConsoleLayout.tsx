import { BellOutlined, MoonOutlined, SunOutlined } from '@ant-design/icons';
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
  type MenuProps
} from 'antd';
import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import type { CurrentUser } from '../api/auth';
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
import { fetchNotifications, fetchUnreadNotificationCount, type UserNotification } from '../api/notifications';
import type { UserLifecycleAction } from '../permissions';
import { translate } from '../platform/i18n';
import { filterNavigationByPermission, resolveBreadcrumbs, resolveMenuState, type NavNode } from '../app/navigation';
import { ManagementConsoleContext, type ManagementConsoleValue } from './managementConsoleContext';

const { Header, Sider, Content } = Layout;

type ResetPasswordForm = {
  confirmPassword: string;
  newPassword: string;
  username: string;
};

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

/**
 * 企业级控制台外壳：左侧分组导航 + 顶栏（面包屑/通知/主题/用户）+ 内容区。
 * 管理域数据（用户/角色/项目等）在此统一加载，通过 Context 分发给系统管理页面。
 */
export function ConsoleLayout(props: {
  currentUser: CurrentUser;
  onChangePassword: () => void;
  onLogout: () => void;
  onToggleTheme: () => void;
  themeMode: 'dark' | 'light';
}) {
  const { message } = AntApp.useApp();
  const location = useLocation();
  const navigate = useNavigate();
  const [managementData, setManagementData] = useState<ManagementData>(emptyManagementData);
  const [managementLoad, setManagementLoad] = useState<{ loading: boolean; error?: string }>({ loading: false });
  const [auditExportState, setAuditExportState] = useState<{ loading: boolean; error?: string }>({ loading: false });
  const [auditOutboxFilters, setAuditOutboxFilters] = useState<AuditOutboxFilters>({ status: '', traceId: '', search: '' });
  const [auditOutboxLoad, setAuditOutboxLoad] = useState<{ loading: boolean; error?: string }>({ loading: false });
  const [resetPasswordOpen, setResetPasswordOpen] = useState(false);
  const [resetPasswordSubmitting, setResetPasswordSubmitting] = useState(false);
  const [resetPasswordForm] = Form.useForm<ResetPasswordForm>();

  const visibleGroups = useMemo(() => filterNavigationByPermission(props.currentUser), [props.currentUser]);
  const menuState = useMemo(() => resolveMenuState(location.pathname), [location.pathname]);
  const breadcrumbs = useMemo(() => resolveBreadcrumbs(location.pathname), [location.pathname]);
  const [openKeys, setOpenKeys] = useState<string[]>(menuState.openKeys);

  useEffect(() => {
    setOpenKeys((current) => Array.from(new Set([...current, ...menuState.openKeys])));
  }, [menuState.openKeys]);

  const notificationsQuery = useQuery({
    queryFn: async () => {
      const [list, unread] = await Promise.all([fetchNotifications({ size: 8 }), fetchUnreadNotificationCount()]);
      return { items: list.data.items, unread: unread.data.unreadCount };
    },
    queryKey: ['enterprise-notifications', props.currentUser.user_id],
    retry: 0
  });

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
  }, [props.currentUser.permissions]);

  const refreshAuditOutbox = useCallback(
    async (filters: AuditOutboxFilters = auditOutboxFilters) => {
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
    },
    [auditOutboxFilters]
  );

  useEffect(() => {
    void refreshManagementData();
  }, [props.currentUser.user_id, refreshManagementData]);

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

  const menuItems = useMemo<MenuProps['items']>(() => buildMenuItems(visibleGroups), [visibleGroups]);

  const managementValue = useMemo<ManagementConsoleValue>(
    () => ({
      data: managementData,
      loadState: managementLoad,
      auditExportState,
      auditOutboxFilters,
      auditOutboxLoad,
      onCreate: onCreateManagementItem,
      onUserLifecycleAction,
      onResetPassword: openResetPasswordDialog,
      onAuditExport,
      onAuditOutboxFiltersChange: setAuditOutboxFilters,
      onAuditOutboxRefresh: refreshAuditOutbox,
      onRefresh: () => void refreshManagementData()
    }),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [managementData, managementLoad, auditExportState, auditOutboxFilters, auditOutboxLoad]
  );

  return (
    <Layout className="console-layout">
      <Sider className="console-sider" breakpoint="lg" collapsedWidth={64} width={240} theme="light">
        <div className="console-sider-brand">
          <div className="console-sider-mark">VA</div>
          <span className="console-sider-name">Veri Agent</span>
        </div>
        <Menu
          className="console-sider-menu"
          items={menuItems}
          mode="inline"
          openKeys={openKeys}
          selectedKeys={[menuState.selectedKey]}
          onOpenChange={(keys) => setOpenKeys(keys as string[])}
          onClick={({ key, keyPath }) => {
            const target = findPathByMenuKey(visibleGroups, key as string, keyPath as string[]);
            if (target) {
              navigate(target);
            }
          }}
        />
      </Sider>
      <Layout>
        <Header className="console-header">
          <Breadcrumb items={breadcrumbs.map((title) => ({ title }))} />
          <div className="console-header-actions">
            <NotificationDropdown
              items={notificationsQuery.data?.items ?? []}
              loading={notificationsQuery.isLoading}
              unread={notificationsQuery.data?.unread ?? 0}
            />
            <Button
              aria-label={translate('actions.theme')}
              icon={props.themeMode === 'dark' ? <SunOutlined /> : <MoonOutlined />}
              type="text"
              onClick={props.onToggleTheme}
            />
            <Dropdown
              menu={{
                items: [
                  { key: 'password', label: translate('auth.passwordChangeTitle'), onClick: props.onChangePassword },
                  { key: 'logout', danger: true, label: translate('auto.k0080'), onClick: props.onLogout }
                ]
              }}
            >
              <div className="console-user-trigger">
                <Avatar size={28} style={{ background: '#2f54eb', fontSize: 12 }}>
                  {avatarText(props.currentUser)}
                </Avatar>
                <span className="console-user-name">{props.currentUser.display_name || props.currentUser.username}</span>
              </div>
            </Dropdown>
          </div>
        </Header>
        <Content className="console-content">
          <div className="console-content-inner">
            <ManagementConsoleContext.Provider value={managementValue}>
              <Outlet />
            </ManagementConsoleContext.Provider>
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
        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
          <Button disabled={props.submitting} onClick={props.onCancel}>
            {translate('actions.cancel')}
          </Button>
          <Button htmlType="submit" loading={props.submitting} type="primary">
            {translate('auth.resetSubmit')}
          </Button>
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
        <Card style={{ width: 360 }} title={translate('auto.k2840')}>
          <List
            dataSource={props.items}
            loading={props.loading}
            locale={{ emptyText: translate('auto.k2841') }}
            renderItem={(item) => (
              <List.Item>
                <List.Item.Meta
                  title={
                    <Space>
                      <span>{item.title}</span>
                      {item.unread ? <Badge status="processing" /> : null}
                    </Space>
                  }
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

function buildMenuItems(groups: ReturnType<typeof filterNavigationByPermission>): MenuProps['items'] {
  const items: NonNullable<MenuProps['items']> = [];
  for (const group of groups) {
    items.push({ key: `group-${group.key}`, label: group.label, type: 'group' });
    for (const item of group.items) {
      items.push(toMenuItem(item));
    }
  }
  return items;
}

function toMenuItem(node: NavNode): NonNullable<MenuProps['items']>[number] {
  if (node.children && node.children.length > 0) {
    return {
      key: node.key,
      icon: node.icon,
      label: node.label,
      children: node.children.map((child) => ({ key: child.key, label: child.label }))
    };
  }
  return { key: node.key, icon: node.icon, label: node.label };
}

function findPathByMenuKey(
  groups: ReturnType<typeof filterNavigationByPermission>,
  key: string,
  keyPath: string[]
): string | null {
  for (const group of groups) {
    for (const item of group.items) {
      if (item.children) {
        const child = item.children.find((candidate) => candidate.key === key);
        if (child?.path) {
          return child.path;
        }
      }
      if (item.key === key && item.path) {
        return item.path;
      }
    }
  }
  // 点击子菜单标题时跳转到第一个子页面
  if (keyPath.length > 0) {
    const parentKey = keyPath[keyPath.length - 1];
    for (const group of groups) {
      for (const item of group.items) {
        if (item.key === parentKey && item.children && item.children.length > 0) {
          return item.children[0].path ?? null;
        }
      }
    }
  }
  return null;
}

function avatarText(user: CurrentUser) {
  return (user.display_name || user.username || 'U').slice(0, 2).toUpperCase();
}
