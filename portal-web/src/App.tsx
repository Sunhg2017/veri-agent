import {
  Activity,
  AppWindow,
  Archive,
  CheckCircle2,
  ClipboardList,
  DatabaseZap,
  FileText,
  GitBranch,
  KeyRound,
  LayoutDashboard,
  Link2,
  LogOut,
  MonitorPlay,
  ServerCog,
  Settings,
  ShieldCheck,
  Sparkles,
  ScrollText,
  UsersRound,
  type LucideIcon
} from 'lucide-react';
import { useCallback, useEffect, useMemo, useRef, useState, type FormEvent } from 'react';
import {
  changePassword,
  fetchCurrentUser,
  login as loginRequest,
  logout as logoutRequest,
  type CurrentUser,
  type LoginPayload
} from './api/auth';
import { ApiError, clearAuthToken, getAuthToken, setAuthToken, setRefreshToken, setSessionId } from './api/client';
import { fetchHealth, type HealthResult } from './api/health';
import {
  fetchNotifications,
  fetchUnreadNotificationCount,
  markAllNotificationsRead,
  markNotificationRead,
  subscribeNotificationStream,
  type NotificationStreamEvent,
  type UserNotification
} from './api/notifications';
import { AssetWorkbench } from './components/AssetWorkbench';
import { ApiAutomationWorkbench } from './components/ApiAutomationWorkbench';
import { DocumentInputConsole } from './components/DocumentInputConsole';
import { ExecutionWorkbench } from './components/ExecutionWorkbench';
import { ModelAccessConsole } from './components/ModelAccessConsole';
import { ManagementPage } from './components/AppManagementPage';
import { NotificationCenter } from './components/NotificationCenter';
import { OverviewPage } from './components/AppOverviewPage';
import { ReportsWorkbench } from './components/ReportsWorkbench';
import { TestDesignWorkbench } from './components/TestDesignWorkbench';
import { TestDataWorkbench } from './components/TestDataWorkbench';
import { UiE2eWorkbench } from './components/UiE2eWorkbench';
import { useToast } from './components/Toast';
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
} from './api/management';
import {
  canAccessPage,
  type PageKey,
  type UserLifecycleAction
} from './permissions';

/* ===================== 常量 & 类型 ===================== */

const initialLoginForm: LoginPayload = { username: '', password: '' };

type PasswordForm = {
  oldPassword: string;
  newPassword: string;
  confirmPassword: string;
};

type ResetPasswordForm = {
  username: string;
  newPassword: string;
  confirmPassword: string;
};

const initialPasswordForm: PasswordForm = { oldPassword: '', newPassword: '', confirmPassword: '' };
const initialResetPasswordForm: ResetPasswordForm = { username: '', newPassword: '', confirmPassword: '' };

type LoginState =
  | { status: 'idle' }
  | { status: 'loading' }
  | { status: 'error'; message: string };

type PasswordDialogState =
  | { status: 'idle' }
  | { status: 'submitting' }
  | { status: 'error'; message: string };

interface PageDefinition {
  key: PageKey;
  label: string;
  title: string;
  description: string;
  icon: LucideIcon;
}

const pages: PageDefinition[] = [
  {
    key: 'overview',
    label: '系统概览',
    title: '系统概览',
    description: '查看平台健康状态、资源摘要和 WP1-WP4 控制台入口。',
    icon: LayoutDashboard
  },
  {
    key: 'document-input',
    label: '文档输入',
    title: '文档输入',
    description: '管理文档源、字段映射与文本/Markdown 导入，查看解析出的需求数量。',
    icon: FileText
  },
  {
    key: 'asset-library',
    label: '资产库',
    title: '资产库',
    description: '管理 WP3 需求资产，查看来源追踪和后续资产类型入口。',
    icon: Archive
  },
  {
    key: 'test-design',
    label: '用例生成',
    title: '用例生成',
    description: '基于 WP3 需求生成候选测试用例，完成评审后发布到资产库。',
    icon: Sparkles
  },
  {
    key: 'api-automation',
    label: '接口自动化',
    title: '接口自动化',
    description: '导入 OpenAPI 规格，解析接口摘要并维护 endpoint snapshot。',
    icon: ClipboardList
  },
  {
    key: 'ui-e2e',
    label: 'UI E2E',
    title: 'UI E2E',
    description: '管理 WP7 场景、脚本包、运行摘要和 flaky 治理。',
    icon: MonitorPlay
  },
  {
    key: 'execution',
    label: '执行编排',
    title: '执行编排',
    description: '管理执行计划、DAG 校验、运行状态、触发配置和调度摘要。',
    icon: GitBranch
  },
  {
    key: 'test-data',
    label: '测试数据',
    title: '测试数据',
    description: '维护数据集、账号池、租借记录和清理任务。',
    icon: KeyRound
  },
  {
    key: 'reports',
    label: '报告诊断',
    title: '报告诊断',
    description: '生成 WP10 报告快照，查看失败诊断、缺陷草稿和脱敏导出摘要。',
    icon: ScrollText
  },
  {
    key: 'model-access',
    label: '模型接入',
    title: '模型接入',
    description: '管理 WP2 模型供应商、Prompt 版本、调用日志与成本。',
    icon: Activity
  },
  {
    key: 'organizations',
    label: '组织部门',
    title: '组织部门',
    description: '维护部门层级、负责人和成员规模。',
    icon: GitBranch
  },
  {
    key: 'users',
    label: '用户权限',
    title: '用户与权限',
    description: '集中查看用户、角色、权限策略与账号状态。',
    icon: UsersRound
  },
  {
    key: 'roles',
    label: '角色治理',
    title: '角色治理',
    description: '维护自定义角色定义、作用域和权限点集合。',
    icon: ShieldCheck
  },
  {
    key: 'projects',
    label: '项目空间',
    title: '项目空间',
    description: '组织测试项目空间、协作成员与资源配额。',
    icon: DatabaseZap
  },
  {
    key: 'applications',
    label: '应用管理',
    title: '应用管理',
    description: '登记被测应用、责任团队、版本流和接入状态。',
    icon: AppWindow
  },
  {
    key: 'environments',
    label: '环境管理',
    title: '环境管理',
    description: '配置测试、预发、生产等环境与访问策略。',
    icon: ServerCog
  },
  {
    key: 'integrations',
    label: '集成配置',
    title: '集成配置',
    description: '维护代码仓库、CI、消息通知和缺陷系统等外部集成。',
    icon: Link2
  },
  {
    key: 'audit',
    label: '审计日志',
    title: '审计日志',
    description: '追踪关键配置、权限变更与初始化操作。',
    icon: ScrollText
  },
  {
    key: 'settings',
    label: '系统设置',
    title: '系统设置',
    description: '管理平台级安全策略、保留周期和运行参数。',
    icon: Settings
  }
];

const emptyManagementData: ManagementData = {
  departments: [], users: [], roles: [], permissions: [],
  projects: [], applications: [], environments: [], integrations: [],
  auditLogs: [], auditOutbox: [], settings: [], secrets: []
};

/* ===================== Page Routing ===================== */

function activePageFromHash(): PageKey {
  const pageKey = window.location.hash.replace(/^#\/?/, '').split('/')[0];
  return pages.some((p) => p.key === pageKey) ? (pageKey as PageKey) : 'overview';
}

function navigateToPage(page: PageKey) {
  window.location.hash = `#${page}`;
}

/* ===================== Main App ===================== */

export function App() {
  // -- Auth state --
  const [activePage, setActivePage] = useState<PageKey>(() => activePageFromHash());
  const [loginForm, setLoginForm] = useState<LoginPayload>(initialLoginForm);
  const [loginState, setLoginState] = useState<LoginState>({ status: 'idle' });
  const [currentUser, setCurrentUser] = useState<CurrentUser | null>(null);
  const [passwordDialogOpen, setPasswordDialogOpen] = useState(false);
  const [passwordForm, setPasswordForm] = useState<PasswordForm>(initialPasswordForm);
  const [passwordDialogState, setPasswordDialogState] = useState<PasswordDialogState>({ status: 'idle' });
  const [resetPasswordDialogOpen, setResetPasswordDialogOpen] = useState(false);
  const [resetPasswordForm, setResetPasswordForm] = useState<ResetPasswordForm>(initialResetPasswordForm);
  const [resetPasswordDialogState, setResetPasswordDialogState] = useState<PasswordDialogState>({ status: 'idle' });

  // -- Data state --
  const [health, setHealth] = useState<{ loading: boolean; data?: HealthResult; error?: string }>({ loading: true });
  const [managementData, setManagementData] = useState<ManagementData>(emptyManagementData);
  const [managementLoad, setManagementLoad] = useState<{ loading: boolean; error?: string }>({ loading: false });
  const [auditExportState, setAuditExportState] = useState<{ loading: boolean; error?: string }>({ loading: false });
  const [auditOutboxFilters, setAuditOutboxFilters] = useState<AuditOutboxFilters>({ status: '', traceId: '', search: '' });
  const [auditOutboxLoad, setAuditOutboxLoad] = useState<{ loading: boolean; error?: string }>({ loading: false });
  const [notificationOpen, setNotificationOpen] = useState(false);
  const [notificationItems, setNotificationItems] = useState<UserNotification[]>([]);
  const [notificationUnreadCount, setNotificationUnreadCount] = useState(0);
  const [notificationLoad, setNotificationLoad] = useState<{ loading: boolean; loaded: boolean }>({ loading: false, loaded: false });
  const notificationStreamRetryRef = useRef<number | null>(null);

  // -- Toast --
  const { addToast, toastContainer } = useToast();

  const visiblePages = useMemo(() => pages.filter((p) => canAccessPage(currentUser, p.key)), [currentUser]);
  const activeDefinition = pages.find((p) => p.key === activePage) ?? pages[0];
  const passwordChangeRequired = Boolean(currentUser?.must_change_password);

  /* ---------- Hash routing ---------- */

  useEffect(() => {
    function sync() { setActivePage(activePageFromHash()); }
    window.addEventListener('hashchange', sync);
    return () => window.removeEventListener('hashchange', sync);
  }, []);

  useEffect(() => {
    if (!canAccessPage(currentUser, activePage)) {
      window.history.replaceState(null, '', '#overview');
      setActivePage('overview');
    }
  }, [activePage, currentUser]);

  /* ---------- Initial data loading ---------- */

  useEffect(() => {
    let active = true;
    fetchHealth()
      .then((r) => { if (active) setHealth({ loading: false, data: r.data }); })
      .catch((err: unknown) => { if (active) setHealth({ loading: false, error: err instanceof Error ? err.message : 'Health check failed' }); });
    return () => { active = false; };
  }, []);

  useEffect(() => {
    if (!getAuthToken()) return;
    fetchCurrentUser()
      .then((r) => {
        setCurrentUser(r.data);
      })
      .catch(() => {
        clearAuthToken();
        setCurrentUser(null);
      });
  }, []);

  useEffect(() => {
    if (!currentUser || currentUser.must_change_password) {
      setManagementData(emptyManagementData);
      setManagementLoad({ loading: false });
      setAuditOutboxLoad({ loading: false });
      return;
    }
    void refreshManagementData();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [currentUser]);

  useEffect(() => {
    if (currentUser?.must_change_password) {
      setPasswordDialogOpen(true);
    }
  }, [currentUser?.must_change_password, currentUser?.user_id]);

  useEffect(() => {
    if (!currentUser || currentUser.must_change_password) {
      setNotificationOpen(false);
      setNotificationItems([]);
      setNotificationUnreadCount(0);
      setNotificationLoad({ loading: false, loaded: false });
      return;
    }
    void refreshUnreadNotificationCount();
    void refreshNotifications(true);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [currentUser]);

  useEffect(() => {
    if (!currentUser || currentUser.must_change_password) {
      return undefined;
    }
    const timer = window.setInterval(() => {
      void refreshUnreadNotificationCount();
      if (notificationOpen) {
        void refreshNotifications(true);
      }
    }, 30000);
    return () => window.clearInterval(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [currentUser, notificationOpen]);

  useEffect(() => {
    if (!currentUser || currentUser.must_change_password) {
      if (notificationStreamRetryRef.current !== null) {
        window.clearTimeout(notificationStreamRetryRef.current);
        notificationStreamRetryRef.current = null;
      }
      return undefined;
    }
    let disposed = false;
    let controller: AbortController | null = null;

    const connect = () => {
      if (disposed || !getAuthToken()) {
        return;
      }
      controller = new AbortController();
      void subscribeNotificationStream(
        (event) => {
          if (disposed) {
            return;
          }
          applyNotificationStreamEvent(event);
        },
        controller.signal
      )
        .then(() => {
          if (disposed || controller?.signal.aborted) {
            return;
          }
          notificationStreamRetryRef.current = window.setTimeout(() => {
            notificationStreamRetryRef.current = null;
            connect();
          }, 1000);
        })
        .catch((err: unknown) => {
          if (disposed || controller?.signal.aborted) {
            return;
          }
          if (err instanceof ApiError && err.code === 'SESSION_EXPIRED') {
            resetSignedInState();
            addToast('info', '登录已过期，请重新登录');
            return;
          }
          notificationStreamRetryRef.current = window.setTimeout(() => {
            notificationStreamRetryRef.current = null;
            connect();
          }, 3000);
        });
    };

    connect();
    return () => {
      disposed = true;
      controller?.abort();
      if (notificationStreamRetryRef.current !== null) {
        window.clearTimeout(notificationStreamRetryRef.current);
        notificationStreamRetryRef.current = null;
      }
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [currentUser?.user_id, currentUser?.must_change_password]);

  /* ---------- API helpers ---------- */

  const refreshManagementData = useCallback(async () => {
    if (!getAuthToken() || !currentUser || currentUser.must_change_password) {
      setManagementData(emptyManagementData);
      setManagementLoad({ loading: false });
      return;
    }
    setManagementLoad({ loading: true });
    try {
      const r = await fetchManagementData(currentUser.permissions ?? []);
      setManagementData(r.data);
      setManagementLoad({ loading: false });
    } catch (err: unknown) {
      setManagementLoad({ loading: false, error: err instanceof Error ? err.message : '加载失败' });
    }
  }, [currentUser]);

  const refreshAuditOutbox = useCallback(async (filters: AuditOutboxFilters = auditOutboxFilters) => {
    if (!getAuthToken() || !currentUser || currentUser.must_change_password) return;
    setAuditOutboxLoad({ loading: true });
    try {
      const r = await fetchAuditOutbox(filters);
      setManagementData((prev) => ({ ...prev, auditOutbox: r.data.items }));
      setAuditOutboxLoad({ loading: false });
    } catch (err: unknown) {
      setAuditOutboxLoad({ loading: false, error: err instanceof Error ? err.message : '加载失败' });
    }
  }, [auditOutboxFilters, currentUser]);

  const refreshUnreadNotificationCount = useCallback(async () => {
    if (!getAuthToken() || !currentUser || currentUser.must_change_password) {
      setNotificationUnreadCount(0);
      return;
    }
    try {
      const response = await fetchUnreadNotificationCount();
      setNotificationUnreadCount(response.data.unreadCount);
    } catch {
      setNotificationUnreadCount(0);
    }
  }, [currentUser]);

  const refreshNotifications = useCallback(async (force = false) => {
    if (!getAuthToken() || !currentUser || currentUser.must_change_password) {
      setNotificationItems([]);
      setNotificationUnreadCount(0);
      setNotificationLoad({ loading: false, loaded: false });
      return;
    }
    if (!force && notificationLoad.loading) {
      return;
    }
    setNotificationLoad((prev) => ({ loading: true, loaded: prev.loaded }));
    try {
      const [listResponse, unreadResponse] = await Promise.all([
        fetchNotifications({ size: 8 }),
        fetchUnreadNotificationCount()
      ]);
      setNotificationItems(listResponse.data.items);
      setNotificationUnreadCount(unreadResponse.data.unreadCount);
      setNotificationLoad({ loading: false, loaded: true });
    } catch {
      setNotificationLoad((prev) => ({ loading: false, loaded: prev.loaded }));
    }
  }, [currentUser, notificationLoad.loading]);

  const applyNotificationStreamEvent = useCallback((event: NotificationStreamEvent) => {
    if (event.type === 'unread-count' || event.type === 'connected') {
      setNotificationUnreadCount(event.unreadCount);
      return;
    }
    if (event.type === 'heartbeat') {
      return;
    }
    if (event.type === 'notification-created') {
      setNotificationUnreadCount(event.unreadCount);
      setNotificationItems((prev) => {
        const deduped = prev.filter((item) => item.id !== event.notification.id);
        return [event.notification, ...deduped].slice(0, 8);
      });
      setNotificationLoad((prev) => ({ ...prev, loaded: true }));
      return;
    }
    if (event.type === 'notification-read') {
      setNotificationUnreadCount(event.unreadCount);
      setNotificationItems((prev) => prev.map((item) =>
        item.id === event.notification.id ? event.notification : item
      ));
      return;
    }
    if (event.type === 'notification-read-all') {
      setNotificationUnreadCount(event.unreadCount);
      setNotificationItems((prev) => prev.map((item) =>
        item.unread ? { ...item, unread: false, readAt: event.readAt ?? item.readAt } : item
      ));
    }
  }, []);

  /* ---------- Auth actions ---------- */

  async function onLogin(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!loginForm.username.trim() || !loginForm.password) {
      setLoginState({ status: 'error', message: '请输入账号和密码' });
      return;
    }
    setLoginState({ status: 'loading' });
    try {
      const r = await loginRequest(loginForm);
      setAuthToken(r.data.access_token);
      setRefreshToken(r.data.refresh_token);
      setSessionId(r.data.session_id);
      const userR = await fetchCurrentUser();
      setCurrentUser(userR.data);
      setLoginForm(initialLoginForm);
      setLoginState({ status: 'idle' });
      addToast('success', '登录成功');
    } catch (err: unknown) {
      clearAuthToken();
      setCurrentUser(null);
      if (err instanceof ApiError) {
        setLoginState({ status: 'error', message: err.message });
      } else {
        setLoginState({ status: 'error', message: '登录失败，请检查网络连接' });
      }
    }
  }

  function resetSignedInState() {
    clearAuthToken();
    setCurrentUser(null);
    setManagementData(emptyManagementData);
    setManagementLoad({ loading: false });
    setAuditExportState({ loading: false });
    setAuditOutboxFilters({ status: '', traceId: '', search: '' });
    setAuditOutboxLoad({ loading: false });
    setNotificationOpen(false);
    setNotificationItems([]);
    setNotificationUnreadCount(0);
    setNotificationLoad({ loading: false, loaded: false });
    setLoginForm(initialLoginForm);
    setPasswordDialogOpen(false);
    setPasswordForm(initialPasswordForm);
    setPasswordDialogState({ status: 'idle' });
  }

  async function onLogout() {
    try { await logoutRequest(); } catch { /* ignore */ } finally {
      resetSignedInState();
      addToast('info', '已退出登录');
    }
  }

  async function onChangePassword(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!currentUser) return;
    const { oldPassword, newPassword, confirmPassword } = passwordForm;
    if (!oldPassword || !newPassword || !confirmPassword) {
      setPasswordDialogState({ status: 'error', message: '请填写完整密码信息' });
      return;
    }
    if (newPassword !== confirmPassword) {
      setPasswordDialogState({ status: 'error', message: '两次输入的新密码不一致' });
      return;
    }
    if (newPassword.length < 10) {
      setPasswordDialogState({ status: 'error', message: '新密码至少 10 位' });
      return;
    }
    if (oldPassword === newPassword) {
      setPasswordDialogState({ status: 'error', message: '新密码不能与旧密码相同' });
      return;
    }
    setPasswordDialogState({ status: 'submitting' });
    try {
      await changePassword({ old_password: oldPassword, new_password: newPassword });
      resetSignedInState();
      addToast('success', '密码已修改，请重新登录');
    } catch (err: unknown) {
      if (err instanceof ApiError) {
        setPasswordDialogState({ status: 'error', message: err.message });
      } else {
        setPasswordDialogState({ status: 'error', message: '密码修改失败' });
      }
    }
  }

  /* ---------- Management actions ---------- */

  async function onCreateManagementItem(resource: CreatableManagementResource, _label: string, rawName: string) {
    if (!currentUser) return;
    const name = rawName.trim();
    if (!name) return;
    setManagementLoad({ loading: true });
    try {
      await createManagementItem(resource, name);
      addToast('success', `${_label}「${name}」已创建`);
      await refreshManagementData();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : '创建失败';
      setManagementLoad({ loading: false, error: msg });
      addToast('error', msg);
    }
  }

  async function onAuditExport() {
    if (!currentUser) return;
    setAuditExportState({ loading: true });
    try {
      const r = await exportAuditLogsCsv();
      const blob = new Blob([r.text], { type: r.contentType || 'text/csv;charset=utf-8' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url; a.download = r.filename ?? '审计日志.csv';
      a.click();
      URL.revokeObjectURL(url);
      setAuditExportState({ loading: false });
      addToast('success', '审计日志已导出');
      await refreshManagementData();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : '导出失败';
      setAuditExportState({ loading: false, error: msg });
      addToast('error', msg);
    }
  }

  async function onUserLifecycleAction(username: string, action: UserLifecycleAction, roleCodeInput = '') {
    if (!currentUser) return;
    if ((action === 'disable' || action === 'lock') && username === currentUser.username) {
      addToast('error', action === 'disable' ? '不能停用当前登录账号' : '不能锁定当前登录账号');
      return;
    }

    if (action === 'reset-password') {
      openResetPasswordDialog(username);
      return;
    }

    let roleCode = '';
    if (action === 'assign-role' || action === 'unassign-role') {
      roleCode = roleCodeInput.trim();
      if (!roleCode) return;
    }

    setManagementLoad({ loading: true });
    try {
      const ops: Record<string, () => Promise<unknown>> = {
        enable: () => enableUser(username),
        unlock: () => unlockUser(username),
        disable: () => disableUser(username),
        lock: () => lockUser(username),
        'assign-role': () => assignUserRole(username, roleCode),
        'unassign-role': () => unassignUserRole(username, roleCode)
      };
      await ops[action]();
      const actionLabels: Record<string, string> = {
        enable: '已启用', unlock: '已解锁', disable: '已停用', lock: '已锁定',
        'reset-password': '密码已重置', 'assign-role': '角色已分配', 'unassign-role': '角色已解绑'
      };
      addToast('success', `${username} ${actionLabels[action] ?? '操作成功'}`);
      await refreshManagementData();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : '操作失败';
      setManagementLoad({ loading: false, error: msg });
      addToast('error', msg);
    }
  }

  function openResetPasswordDialog(username: string) {
    setResetPasswordDialogOpen(true);
    setResetPasswordForm({ username, newPassword: '', confirmPassword: '' });
    setResetPasswordDialogState({ status: 'idle' });
  }

  async function onResetPassword(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!currentUser) return;
    const { newPassword, confirmPassword } = resetPasswordForm;
    if (!newPassword || !confirmPassword) {
      setResetPasswordDialogState({ status: 'error', message: '请填写新密码和确认密码' });
      return;
    }
    if (newPassword !== confirmPassword) {
      setResetPasswordDialogState({ status: 'error', message: '两次输入的密码不一致' });
      return;
    }
    if (newPassword.length < 10) {
      setResetPasswordDialogState({ status: 'error', message: '密码至少 10 位' });
      return;
    }
    setResetPasswordDialogState({ status: 'submitting' });
    try {
      await resetUserPassword(resetPasswordForm.username, newPassword);
      setResetPasswordDialogOpen(false);
      setResetPasswordForm(initialResetPasswordForm);
      setResetPasswordDialogState({ status: 'idle' });
      addToast('success', `${resetPasswordForm.username} 密码已重置`);
      await refreshManagementData();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : '重置失败';
      setResetPasswordDialogState({ status: 'error', message: msg });
    }
  }

  async function onToggleNotifications() {
    const nextOpen = !notificationOpen;
    setNotificationOpen(nextOpen);
    if (nextOpen) {
      await refreshNotifications(true);
    }
  }

  async function onMarkNotificationRead(id: string) {
    try {
      const response = await markNotificationRead(id);
      setNotificationItems((prev) => prev.map((item) => item.id === id ? response.data : item));
      setNotificationUnreadCount((prev) => Math.max(0, prev - 1));
    } catch (err: unknown) {
      addToast('error', err instanceof Error ? err.message : '通知状态更新失败');
    }
  }

  async function onMarkAllNotificationsRead() {
    try {
      const response = await markAllNotificationsRead();
      const readAt = new Date().toISOString();
      setNotificationItems((prev) => prev.map((item) => item.unread ? { ...item, unread: false, readAt } : item));
      setNotificationUnreadCount(response.data.unreadCount);
    } catch (err: unknown) {
      addToast('error', err instanceof Error ? err.message : '通知状态更新失败');
    }
  }

  /* ---------- Render helpers ---------- */

  function renderWorkspacePage() {
    const signedIn = Boolean(currentUser);
    switch (activePage) {
      case 'overview':
        return <OverviewPage health={health} managementData={managementData} />;
      case 'document-input':
        return <DocumentInputConsole signedIn={signedIn} currentUser={currentUser} />;
      case 'asset-library':
        return <AssetWorkbench signedIn={signedIn} currentUser={currentUser} />;
      case 'test-design':
        return <TestDesignWorkbench signedIn={signedIn} currentUser={currentUser} />;
      case 'api-automation':
        return <ApiAutomationWorkbench signedIn={signedIn} currentUser={currentUser} />;
      case 'ui-e2e':
        return <UiE2eWorkbench signedIn={signedIn} currentUser={currentUser} />;
      case 'execution':
        return <ExecutionWorkbench signedIn={signedIn} currentUser={currentUser} />;
      case 'test-data':
        return <TestDataWorkbench signedIn={signedIn} currentUser={currentUser} />;
      case 'reports':
        return <ReportsWorkbench signedIn={signedIn} currentUser={currentUser} />;
      case 'model-access':
        return <ModelAccessConsole signedIn={signedIn} currentUser={currentUser} />;
      default:
        return (
          <ManagementPage
            page={activePage}
            data={managementData}
            loadState={managementLoad}
            signedIn={signedIn}
            currentUser={currentUser}
            onCreate={onCreateManagementItem}
            onUserLifecycleAction={onUserLifecycleAction}
            onResetPassword={openResetPasswordDialog}
            auditExportState={auditExportState}
            onAuditExport={onAuditExport}
            auditOutboxFilters={auditOutboxFilters}
            auditOutboxLoad={auditOutboxLoad}
            onAuditOutboxFiltersChange={setAuditOutboxFilters}
            onAuditOutboxRefresh={refreshAuditOutbox}
            onRefresh={refreshManagementData}
          />
        );
    }
  }

  function renderTopbarActions() {
    return (
      <div className="topbar-actions">
        <HealthBadge health={health} />
        {currentUser && (
          <NotificationCenter
            open={notificationOpen}
            loading={notificationLoad.loading}
            unreadCount={notificationUnreadCount}
            items={notificationItems}
            onToggle={() => void onToggleNotifications()}
            onClose={() => setNotificationOpen(false)}
            onMarkRead={(id) => void onMarkNotificationRead(id)}
            onMarkAllRead={() => void onMarkAllNotificationsRead()}
          />
        )}
        {currentUser && (
          <div className="auth-panel">
            <div className="auth-info">
              <strong>{currentUser.display_name || currentUser.username}</strong>
              <span>{currentUser.email || currentUser.roles?.join(' · ') || ''}</span>
            </div>
            <button className="btn btn-ghost btn-sm" onClick={onLogout} title="退出登录">
              <LogOut size={15} />
              退出
            </button>
          </div>
        )}
      </div>
    );
  }

  /* ---------- Render ---------- */

  // If not logged in, show full-page login
  if (!getAuthToken() && !currentUser && loginState.status !== 'loading') {
    return (
      <div className="login-page">
        <div className="login-card">
          <div className="login-brand">
            <div className="brand-mark" style={{ margin: '0 auto' }}>VA</div>
            <div className="brand-name">Veri Agent</div>
            <div className="brand-subtitle">测试平台 · 请登录</div>
          </div>
          <form className="login-form" onSubmit={onLogin}>
            <div className="field">
              <label className="field-label" htmlFor="login-username">账号</label>
              <input
                id="login-username"
                type="text"
                placeholder="请输入用户名"
                autoComplete="username"
                autoFocus
                value={loginForm.username}
                onChange={(e) => { setLoginForm((f) => ({ ...f, username: e.target.value })); setLoginState({ status: 'idle' }); }}
              />
            </div>
            <div className="field">
              <label className="field-label" htmlFor="login-password">密码</label>
              <input
                id="login-password"
                type="password"
                placeholder="请输入密码"
                autoComplete="current-password"
                value={loginForm.password}
                onChange={(e) => { setLoginForm((f) => ({ ...f, password: e.target.value })); setLoginState({ status: 'idle' }); }}
              />
            </div>
            {loginState.status === 'error' && (
              <div className="login-error">{loginState.message}</div>
            )}
            <button
              className="btn btn-primary"
              type="submit"
              style={{ minHeight: 44, fontSize: 16 }}
            >
              登 录
            </button>
          </form>
        </div>
      </div>
    );
  }

  // Still checking auth
  if (loginState.status === 'loading' && !currentUser) {
    return (
      <div className="login-page">
        <div className="login-card" style={{ textAlign: 'center' }}>
          <div className="brand-mark" style={{ margin: '0 auto 16px' }}>VA</div>
          <div style={{ color: 'var(--text-secondary)', fontSize: 14 }}>正在验证登录状态...</div>
        </div>
      </div>
    );
  }

  return (
    <div className="app-shell">
      {/* Sidebar */}
      <aside className="sidebar">
        <div className="brand">
          <div className="brand-mark">VA</div>
          <div className="brand-text">
            <div className="brand-name">Veri Agent</div>
            <div className="brand-subtitle">测试平台</div>
          </div>
        </div>

        <nav className="nav-list">
          {visiblePages.map((page) => {
            const Icon = page.icon;
            const selected = activePage === page.key;
            return (
              <button
                key={page.key}
                className={`nav-item${selected ? ' active' : ''}`}
                type="button"
                onClick={() => navigateToPage(page.key)}
                aria-current={selected ? 'page' : undefined}
              >
                <Icon size={18} />
                <span>{page.label}</span>
              </button>
            );
          })}
        </nav>
      </aside>

      {/* Main content */}
      <main className="workspace">
        <header className="topbar">
          <div className="topbar-info">
            <h1>{activeDefinition.title}</h1>
            <p>{activeDefinition.description}</p>
          </div>
          {renderTopbarActions()}
        </header>

        {renderWorkspacePage()}
      </main>

      {/* Toasts */}
      {toastContainer}

      {/* Password change dialog */}
      {passwordDialogOpen && (
        <div className="modal-backdrop" onClick={passwordChangeRequired ? undefined : () => setPasswordDialogOpen(false)}>
          <div className="modal-panel" onClick={(e) => e.stopPropagation()}>
            <div className="modal-heading">
              <h2>{passwordChangeRequired ? '请修改初始密码' : '修改密码'}</h2>
              <button className="btn btn-ghost btn-sm" onClick={passwordChangeRequired ? onLogout : () => setPasswordDialogOpen(false)} disabled={passwordDialogState.status === 'submitting'}>
                {passwordChangeRequired ? '退出登录' : '取消'}
              </button>
            </div>
            <form className="modal-body" onSubmit={onChangePassword}>
              {passwordChangeRequired && (
                <div className="notice warning">首次登录或密码已被管理员重置，请设置新密码后继续使用。</div>
              )}
              <div className="field">
                <label className="field-label">当前密码</label>
                <input type="password" autoComplete="current-password" placeholder="输入当前密码"
                  value={passwordForm.oldPassword}
                  onChange={(e) => { setPasswordForm((f) => ({ ...f, oldPassword: e.target.value })); setPasswordDialogState({ status: 'idle' }); }} />
              </div>
              <div className="field">
                <label className="field-label">新密码</label>
                <input type="password" autoComplete="new-password" placeholder="至少 10 位"
                  value={passwordForm.newPassword}
                  onChange={(e) => { setPasswordForm((f) => ({ ...f, newPassword: e.target.value })); setPasswordDialogState({ status: 'idle' }); }} />
              </div>
              <div className="field">
                <label className="field-label">确认新密码</label>
                <input type="password" autoComplete="new-password" placeholder="再次输入新密码"
                  value={passwordForm.confirmPassword}
                  onChange={(e) => { setPasswordForm((f) => ({ ...f, confirmPassword: e.target.value })); setPasswordDialogState({ status: 'idle' }); }} />
              </div>
              {passwordDialogState.status === 'error' && (
                <div className="notice error">{passwordDialogState.message}</div>
              )}
              <div className="modal-footer" style={{ padding: 0, marginTop: 4 }}>
                <button className="btn btn-primary" type="submit" disabled={passwordDialogState.status === 'submitting'}>
                  {passwordDialogState.status === 'submitting' ? '提交中...' : '确认修改'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Reset password dialog */}
      {resetPasswordDialogOpen && (
        <div className="modal-backdrop" onClick={() => resetPasswordDialogState.status !== 'submitting' && setResetPasswordDialogOpen(false)}>
          <div className="modal-panel" onClick={(e) => e.stopPropagation()}>
            <div className="modal-heading">
              <h2>重置密码</h2>
              <button className="btn btn-ghost btn-sm" onClick={() => setResetPasswordDialogOpen(false)} disabled={resetPasswordDialogState.status === 'submitting'}>取消</button>
            </div>
            <form className="modal-body" onSubmit={onResetPassword}>
              <div className="field">
                <label className="field-label">账号</label>
                <input type="text" value={resetPasswordForm.username} disabled />
              </div>
              <div className="field">
                <label className="field-label">新密码</label>
                <input type="password" autoComplete="new-password" placeholder="至少 10 位"
                  value={resetPasswordForm.newPassword}
                  onChange={(e) => { setResetPasswordForm((f) => ({ ...f, newPassword: e.target.value })); setResetPasswordDialogState({ status: 'idle' }); }} />
              </div>
              <div className="field">
                <label className="field-label">确认新密码</label>
                <input type="password" autoComplete="new-password" placeholder="再次输入新密码"
                  value={resetPasswordForm.confirmPassword}
                  onChange={(e) => { setResetPasswordForm((f) => ({ ...f, confirmPassword: e.target.value })); setResetPasswordDialogState({ status: 'idle' }); }} />
              </div>
              {resetPasswordDialogState.status === 'error' && (
                <div className="notice error">{resetPasswordDialogState.message}</div>
              )}
              <div className="modal-footer" style={{ padding: 0, marginTop: 4 }}>
                <button className="btn btn-primary" type="submit" disabled={resetPasswordDialogState.status === 'submitting'}>
                  {resetPasswordDialogState.status === 'submitting' ? '提交中...' : '确认重置'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}

function HealthBadge({ health }: { health: { loading: boolean; data?: HealthResult; error?: string } }) {
  if (health.loading) {
    return <div className="badge badge-neutral"><Activity size={13} />检查中</div>;
  }
  if (health.error || !health.data) {
    return <div className="badge badge-danger"><Activity size={13} />异常</div>;
  }
  return <div className="badge badge-success"><CheckCircle2 size={13} />正常</div>;
}
