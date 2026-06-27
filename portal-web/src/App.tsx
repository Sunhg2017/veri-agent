import {
  Activity,
  AppWindow,
  Archive,
  BookOpen,
  CheckCircle2,
  ChevronDown,
  ChevronRight,
  ClipboardList,
  DatabaseZap,
  FileText,
  GitBranch,
  KeyRound,
  LayoutDashboard,
  Link2,
  LogOut,
  MonitorPlay,
  Moon,
  ServerCog,
  Settings,
  ShieldCheck,
  Sparkles,
  ScrollText,
  WalletCards,
  UsersRound,
  type LucideIcon
} from 'lucide-react';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useQuery } from '@tanstack/react-query';
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
import { Spinner } from './components/ui/State';
import { useConfirmDialog } from './components/ui/ConfirmDialog';
import { useAppSessionStore } from './platform/appStore';
import {
  loginSchema,
  passwordChangeSchema,
  resetPasswordSchema,
  type LoginFormValues,
  type PasswordChangeFormValues,
  type ResetPasswordFormValues
} from './platform/forms';
import { queryClient } from './platform/queryClient';
import { useThemeStore } from './platform/themeStore';
import { translate } from './platform/i18n';

/* ===================== Constants and types ===================== */

const initialLoginForm: LoginFormValues = { username: '', password: '' };
const initialPasswordForm: PasswordChangeFormValues = { oldPassword: '', newPassword: '', confirmPassword: '' };
const initialResetPasswordForm: ResetPasswordFormValues = { username: '', newPassword: '', confirmPassword: '' };

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

interface SidebarGroupDefinition {
  key: string;
  label: string;
  description: string;
  icon: LucideIcon;
  pageKeys: PageKey[];
}

const pages: PageDefinition[] = [
  {
    key: 'overview',
    label: translate('auto.k0001'),
    title: translate('auto.k0001'),
    description: translate('auto.k0002'),
    icon: LayoutDashboard
  },
  {
    key: 'document-input',
    label: translate('auto.k0003'),
    title: translate('auto.k0003'),
    description: translate('auto.k0004'),
    icon: FileText
  },
  {
    key: 'asset-library',
    label: translate('auto.k0005'),
    title: translate('auto.k0005'),
    description: translate('auto.k0006'),
    icon: Archive
  },
  {
    key: 'test-design',
    label: translate('auto.k0007'),
    title: translate('auto.k0007'),
    description: translate('auto.k0008'),
    icon: Sparkles
  },
  {
    key: 'api-automation',
    label: translate('auto.k0009'),
    title: translate('auto.k0009'),
    description: translate('auto.k0010'),
    icon: ClipboardList
  },
  {
    key: 'ui-e2e',
    label: 'UI E2E',
    title: 'UI E2E',
    description: translate('auto.k0011'),
    icon: MonitorPlay
  },
  {
    key: 'execution',
    label: translate('auto.k0012'),
    title: translate('auto.k0012'),
    description: translate('auto.k0013'),
    icon: GitBranch
  },
  {
    key: 'test-data',
    label: translate('auto.k0014'),
    title: translate('auto.k0014'),
    description: translate('auto.k0015'),
    icon: KeyRound
  },
  {
    key: 'reports',
    label: translate('auto.k0016'),
    title: translate('auto.k0016'),
    description: translate('auto.k0017'),
    icon: ScrollText
  },
  {
    key: 'model-access',
    label: translate('auto.k0018'),
    title: translate('auto.k0018'),
    description: translate('auto.k0019'),
    icon: Activity
  },
  {
    key: 'organizations',
    label: translate('auto.k0020'),
    title: translate('auto.k0020'),
    description: translate('auto.k0021'),
    icon: GitBranch
  },
  {
    key: 'users',
    label: translate('auto.k0022'),
    title: translate('auto.k0023'),
    description: translate('auto.k0024'),
    icon: UsersRound
  },
  {
    key: 'roles',
    label: translate('auto.k0025'),
    title: translate('auto.k0025'),
    description: translate('auto.k0026'),
    icon: ShieldCheck
  },
  {
    key: 'projects',
    label: translate('auto.k0027'),
    title: translate('auto.k0027'),
    description: translate('auto.k0028'),
    icon: DatabaseZap
  },
  {
    key: 'applications',
    label: translate('auto.k0029'),
    title: translate('auto.k0029'),
    description: translate('auto.k0030'),
    icon: AppWindow
  },
  {
    key: 'environments',
    label: translate('auto.k0031'),
    title: translate('auto.k0031'),
    description: translate('auto.k0032'),
    icon: ServerCog
  },
  {
    key: 'integrations',
    label: translate('auto.k0033'),
    title: translate('auto.k0033'),
    description: translate('auto.k0034'),
    icon: Link2
  },
  {
    key: 'audit',
    label: translate('auto.k0035'),
    title: translate('auto.k0035'),
    description: translate('auto.k0036'),
    icon: ScrollText
  },
  {
    key: 'settings',
    label: translate('auto.k0037'),
    title: translate('auto.k0037'),
    description: translate('auto.k0038'),
    icon: Settings
  }
];

const sidebarGroups: SidebarGroupDefinition[] = [
  {
    key: 'workbench',
    label: translate('auto.k0039'),
    description: translate('auto.k0040'),
    icon: Sparkles,
    pageKeys: ['document-input', 'asset-library', 'test-design']
  },
  {
    key: 'delivery',
    label: translate('auto.k0041'),
    description: translate('auto.k0042'),
    icon: GitBranch,
    pageKeys: ['api-automation', 'ui-e2e', 'execution', 'test-data', 'reports']
  },
  {
    key: 'organization',
    label: translate('auto.k0043'),
    description: translate('auto.k0044'),
    icon: UsersRound,
    pageKeys: ['organizations', 'users', 'roles', 'projects']
  },
  {
    key: 'platform',
    label: translate('auto.k0045'),
    description: translate('auto.k0046'),
    icon: Settings,
    pageKeys: ['applications', 'environments', 'integrations', 'audit', 'settings', 'model-access']
  }
];

const sidebarGroupKeyByPageKey: Partial<Record<PageKey, string>> = Object.fromEntries(
  sidebarGroups.flatMap((group) => group.pageKeys.map((pageKey) => [pageKey, group.key] as const))
);

const emptyManagementData: ManagementData = {
  departments: [], users: [], roles: [], permissions: [],
  projects: [], applications: [], environments: [], integrations: [],
  auditLogs: [], auditOutbox: [], settings: [], secrets: []
};

/* ===================== Page Routing ===================== */

function activePageFromPath(pathname: string): PageKey {
  const pageKey = pathname.replace(/^\/+/, '').split('/')[0];
  return pages.some((p) => p.key === pageKey) ? (pageKey as PageKey) : 'overview';
}

/* ===================== Main App ===================== */

export function App() {
  const { i18n, t } = useTranslation();
  const location = useLocation();
  const navigate = useNavigate();
  const confirm = useConfirmDialog();
  const toggleThemeMode = useThemeStore((state) => state.toggleMode);
  const resolvedThemeMode = useThemeStore((state) => state.resolvedMode);
  const sessionUser = useAppSessionStore((state) => state.currentUser);
  const setSessionUser = useAppSessionStore((state) => state.setCurrentUser);

  // -- Auth state --
  const activePage = activePageFromPath(location.pathname);
  const [loginState, setLoginState] = useState<LoginState>({ status: 'idle' });
  const currentUser = sessionUser;
  const [passwordDialogOpen, setPasswordDialogOpen] = useState(false);
  const [passwordDialogState, setPasswordDialogState] = useState<PasswordDialogState>({ status: 'idle' });
  const [resetPasswordDialogOpen, setResetPasswordDialogOpen] = useState(false);
  const [resetPasswordDialogState, setResetPasswordDialogState] = useState<PasswordDialogState>({ status: 'idle' });

  // -- Data state --
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
  const loginForm = useForm<LoginFormValues>({
    defaultValues: initialLoginForm,
    resolver: zodResolver(loginSchema)
  });
  const passwordForm = useForm<PasswordChangeFormValues>({
    defaultValues: initialPasswordForm,
    resolver: zodResolver(passwordChangeSchema)
  });
  const resetPasswordForm = useForm<ResetPasswordFormValues>({
    defaultValues: initialResetPasswordForm,
    resolver: zodResolver(resetPasswordSchema)
  });
  const healthQuery = useQuery({
    queryFn: async () => {
      const response = await fetchHealth();
      return response.data;
    },
    queryKey: ['platform-health']
  });
  const currentUserQuery = useQuery({
    enabled: Boolean(getAuthToken()),
    queryFn: async () => {
      const response = await fetchCurrentUser();
      return response.data;
    },
    queryKey: ['current-user'],
    retry: 0
  });
  const health = useMemo(
    () => ({
      data: healthQuery.data,
      error: healthQuery.error instanceof Error ? healthQuery.error.message : undefined,
      loading: healthQuery.isLoading
    }),
    [healthQuery.data, healthQuery.error, healthQuery.isLoading]
  );

  const visiblePages = useMemo(() => pages.filter((p) => canAccessPage(currentUser, p.key)), [currentUser]);
  const visiblePagesByKey = useMemo(
    () => new Map(visiblePages.map((page) => [page.key, page] as const)),
    [visiblePages]
  );
  const visibleSidebarGroups = useMemo(
    () => sidebarGroups
      .map((group) => ({
        ...group,
        pages: group.pageKeys
          .map((pageKey) => visiblePagesByKey.get(pageKey))
          .filter((page): page is PageDefinition => Boolean(page))
      }))
      .filter((group) => group.pages.length > 0),
    [visiblePagesByKey]
  );
  const activeDefinition = pages.find((p) => p.key === activePage) ?? pages[0];
  const passwordChangeRequired = Boolean(currentUser?.must_change_password);
  const [openSidebarGroupKey, setOpenSidebarGroupKey] = useState<string | null>(() => sidebarGroupKeyByPageKey[activePage] ?? null);
  const activeSidebarGroupKey = sidebarGroupKeyByPageKey[activePage] ?? null;
  const activeSidebarGroupLabel = sidebarGroups.find((group) => group.key === activeSidebarGroupKey)?.label ?? translate('auto.k0047');

  useEffect(() => {
    if (location.pathname === '/' || location.pathname === '') {
      navigate('/overview', { replace: true });
    }
  }, [location.pathname, navigate]);

  useEffect(() => {
    setOpenSidebarGroupKey(activeSidebarGroupKey);
  }, [activePage]);

  useEffect(() => {
    if (openSidebarGroupKey && !visibleSidebarGroups.some((group) => group.key === openSidebarGroupKey)) {
      setOpenSidebarGroupKey(null);
    }
  }, [openSidebarGroupKey, visibleSidebarGroups]);

  useEffect(() => {
    if (currentUserQuery.data) {
      setSessionUser(currentUserQuery.data);
    }
  }, [currentUserQuery.data, setSessionUser]);

  useEffect(() => {
    if (currentUserQuery.isError) {
      clearAuthToken();
      setSessionUser(null);
    }
  }, [currentUserQuery.isError, setSessionUser]);

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
            addToast('info', translate('auto.k0048'));
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
      setManagementLoad({ loading: false, error: err instanceof Error ? err.message : translate('auto.k0049') });
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
      setAuditOutboxLoad({ loading: false, error: err instanceof Error ? err.message : translate('auto.k0049') });
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

  const onLogin = loginForm.handleSubmit(async (values) => {
    setLoginState({ status: 'loading' });
    try {
      const payload: LoginPayload = { username: values.username.trim(), password: values.password };
      const r = await loginRequest(payload);
      setAuthToken(r.data.access_token);
      setRefreshToken(r.data.refresh_token);
      setSessionId(r.data.session_id);
      const userR = await fetchCurrentUser();
      setSessionUser(userR.data);
      queryClient.setQueryData(['current-user'], userR.data);
      loginForm.reset(initialLoginForm);
      setLoginState({ status: 'idle' });
      addToast('success', translate('auto.k0050'));
    } catch (err: unknown) {
      clearAuthToken();
      setSessionUser(null);
      if (err instanceof ApiError) {
        setLoginState({ status: 'error', message: err.message });
      } else {
        setLoginState({ status: 'error', message: translate('auto.k0051') });
      }
    }
  }, (errors) => {
    setLoginState({ status: 'error', message: errors.username?.message ?? errors.password?.message ?? t('validation.loginRequired') });
  });

  function resetSignedInState() {
    clearAuthToken();
    setSessionUser(null);
    queryClient.clear();
    setManagementData(emptyManagementData);
    setManagementLoad({ loading: false });
    setAuditExportState({ loading: false });
    setAuditOutboxFilters({ status: '', traceId: '', search: '' });
    setAuditOutboxLoad({ loading: false });
    setNotificationOpen(false);
    setNotificationItems([]);
    setNotificationUnreadCount(0);
    setNotificationLoad({ loading: false, loaded: false });
    loginForm.reset(initialLoginForm);
    setPasswordDialogOpen(false);
    passwordForm.reset(initialPasswordForm);
    setPasswordDialogState({ status: 'idle' });
  }

  async function onLogout() {
    const confirmed = await confirm({
      confirmLabel: translate('auto.k0052'),
      description: translate('auto.k0053'),
      title: translate('auto.k0054'),
      tone: 'danger'
    });
    if (!confirmed) {
      return;
    }
    try { await logoutRequest(); } catch { /* ignore */ } finally {
      resetSignedInState();
      addToast('info', translate('auto.k0055'));
    }
  }

  const onChangePassword = passwordForm.handleSubmit(async ({ oldPassword, newPassword }) => {
    if (!currentUser) return;
    setPasswordDialogState({ status: 'submitting' });
    try {
      await changePassword({ old_password: oldPassword, new_password: newPassword });
      resetSignedInState();
      addToast('success', translate('auto.k0056'));
    } catch (err: unknown) {
      if (err instanceof ApiError) {
        setPasswordDialogState({ status: 'error', message: err.message });
      } else {
        setPasswordDialogState({ status: 'error', message: translate('auto.k0057') });
      }
    }
  }, (errors) => {
    setPasswordDialogState({
      status: 'error',
      message: errors.oldPassword?.message ?? errors.newPassword?.message ?? errors.confirmPassword?.message ?? t('validation.passwordComplete')
    });
  });

  /* ---------- Management actions ---------- */

  async function onCreateManagementItem(resource: CreatableManagementResource, _label: string, rawName: string) {
    if (!currentUser) return;
    const name = rawName.trim();
    if (!name) return;
    setManagementLoad({ loading: true });
    try {
      await createManagementItem(resource, name);
      addToast('success', translate('auto.k0058', { value0: _label, value1: name }));
      await refreshManagementData();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : translate('auto.k0059');
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
      a.href = url; a.download = r.filename ?? translate('auto.k0060');
      a.click();
      URL.revokeObjectURL(url);
      setAuditExportState({ loading: false });
      addToast('success', translate('auto.k0061'));
      await refreshManagementData();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : translate('auto.k0062');
      setAuditExportState({ loading: false, error: msg });
      addToast('error', msg);
    }
  }

  async function onUserLifecycleAction(username: string, action: UserLifecycleAction, roleCodeInput = '') {
    if (!currentUser) return;
    if ((action === 'disable' || action === 'lock') && username === currentUser.username) {
      addToast('error', action === 'disable' ? translate('auto.k0063') : translate('auto.k0064'));
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
        enable: translate('auto.k0065'), unlock: translate('auto.k0066'), disable: translate('auto.k0067'), lock: translate('auto.k0068'),
        'reset-password': translate('auto.k0069'), 'assign-role': translate('auto.k0070'), 'unassign-role': translate('auto.k0071')
      };
      addToast('success', translate('auto.k0072', { value0: username, value1: actionLabels[action] ?? translate('auto.k2600') }));
      await refreshManagementData();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : translate('auto.k0073');
      setManagementLoad({ loading: false, error: msg });
      addToast('error', msg);
    }
  }

  function openResetPasswordDialog(username: string) {
    setResetPasswordDialogOpen(true);
    resetPasswordForm.reset({ username, newPassword: '', confirmPassword: '' });
    setResetPasswordDialogState({ status: 'idle' });
  }

  const onResetPassword = resetPasswordForm.handleSubmit(async ({ username, newPassword }) => {
    if (!currentUser) return;
    setResetPasswordDialogState({ status: 'submitting' });
    try {
      await resetUserPassword(username, newPassword);
      setResetPasswordDialogOpen(false);
      resetPasswordForm.reset(initialResetPasswordForm);
      setResetPasswordDialogState({ status: 'idle' });
      addToast('success', translate('auto.k0074', { value0: username }));
      await refreshManagementData();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : translate('auto.k0075');
      setResetPasswordDialogState({ status: 'error', message: msg });
    }
  }, (errors) => {
    setResetPasswordDialogState({
      status: 'error',
      message: errors.newPassword?.message ?? errors.confirmPassword?.message ?? t('validation.passwordConfirmRequired')
    });
  });

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
      addToast('error', err instanceof Error ? err.message : translate('auto.k0076'));
    }
  }

  async function onMarkAllNotificationsRead() {
    try {
      const response = await markAllNotificationsRead();
      const readAt = new Date().toISOString();
      setNotificationItems((prev) => prev.map((item) => item.unread ? { ...item, unread: false, readAt } : item));
      setNotificationUnreadCount(response.data.unreadCount);
    } catch (err: unknown) {
      addToast('error', err instanceof Error ? err.message : translate('auto.k0076'));
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
        <a className="topbar-link" href="#overview" aria-label={translate('auto.k0077')}>
          <BookOpen size={16} />
          <span>{t('actions.docs')}</span>
        </a>
        <button
          className="topbar-chip topbar-language"
          type="button"
          aria-label={translate('auto.k0078')}
          onClick={() => void i18n.changeLanguage(i18n.language === 'zh' ? 'en' : 'zh')}
        >
          <span className="language-flag" aria-hidden="true">CN</span>
          <span>{i18n.language === 'zh' ? 'ZH' : 'EN'}</span>
          <ChevronDown size={14} />
        </button>
        <HealthBadge health={health} />
        <div className="topbar-balance" aria-label={translate('auto.k0079')}>
          <WalletCards size={16} />
          <span>{t('app.enterpriseEdition')}</span>
        </div>
        {currentUser && (
          <div className="auth-panel">
            <div className="auth-avatar" aria-hidden="true">
              {(currentUser.display_name || currentUser.username || 'U').slice(0, 2).toUpperCase()}
            </div>
            <div className="auth-info">
              <strong>{currentUser.display_name || currentUser.username}</strong>
              <span>{currentUser.email || currentUser.roles?.join(' · ') || ''}</span>
            </div>
            <button className="btn btn-ghost btn-sm" onClick={onLogout} title={translate('auto.k0080')}>
              <LogOut size={15} />
              {t('actions.logout')}
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
        <div className="login-card" role="main" aria-labelledby="login-title">
          <div className="login-brand">
            <div className="brand-mark">VA</div>
            <div className="brand-name" id="login-title">{t('app.name')}</div>
            <div className="brand-subtitle">{t('auth.loginSubtitle')}</div>
          </div>
          <form className="login-form" onSubmit={onLogin}>
            <div className="field">
              <label className="field-label" htmlFor="login-username">{t('auth.account')}</label>
              <input
                id="login-username"
                type="text"
                placeholder={t('auth.usernamePlaceholder')}
                autoComplete="username"
                autoFocus
                {...loginForm.register('username', { onChange: () => setLoginState({ status: 'idle' }) })}
                aria-invalid={Boolean(loginForm.formState.errors.username)}
                required
              />
              {loginForm.formState.errors.username ? <span className="field-error">{loginForm.formState.errors.username.message}</span> : null}
            </div>
            <div className="field">
              <label className="field-label" htmlFor="login-password">{t('auth.password')}</label>
              <input
                id="login-password"
                type="password"
                placeholder={t('auth.passwordPlaceholder')}
                autoComplete="current-password"
                {...loginForm.register('password', { onChange: () => setLoginState({ status: 'idle' }) })}
                aria-invalid={Boolean(loginForm.formState.errors.password)}
                required
              />
              {loginForm.formState.errors.password ? <span className="field-error">{loginForm.formState.errors.password.message}</span> : null}
            </div>
            {loginState.status === 'error' && (
              <div className="login-error">{loginState.message}</div>
            )}
            <button
              className="btn btn-primary login-submit"
              type="submit"
            >
              {t('auth.login')}
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
        <div className="login-card login-loading-card">
          <div className="brand-mark">VA</div>
          <Spinner label={t('auth.validating')} />
        </div>
      </div>
    );
  }

  if (!canAccessPage(currentUser, activePage)) {
    return <Navigate to="/overview" replace />;
  }

  return (
    <div className="app-shell">
      <a className="skip-link" href="#main-content">{t('nav.skip')}</a>
      {/* Sidebar */}
      <aside className="sidebar" aria-label={translate('auto.k0081')}>
        <div className="brand">
          <div className="brand-mark">VA</div>
          <div className="brand-text">
            <div className="brand-name">{t('app.name')}</div>
            <div className="brand-subtitle">{t('app.subtitle')}</div>
          </div>
        </div>

        <nav className="nav-list" aria-label={translate('auto.k0082')}>
          <button
            className={`nav-item nav-home${activePage === 'overview' ? ' active' : ''}`}
            type="button"
            onClick={() => navigate('/overview')}
            aria-current={activePage === 'overview' ? 'page' : undefined}
          >
            <LayoutDashboard size={18} />
            <span>{translate('auto.k0001')}</span>
          </button>

          {visibleSidebarGroups.map((group) => {
            const GroupIcon = group.icon;
            const isOpen = openSidebarGroupKey === group.key;
            const isActiveGroup = group.pages.some((page) => page.key === activePage);
            return (
              <section key={group.key} className={`nav-group${isOpen ? ' open' : ''}${isActiveGroup ? ' active' : ''}`}>
                <button
                  className={`nav-group-toggle${isOpen ? ' open' : ''}${isActiveGroup ? ' active' : ''}`}
                  type="button"
                  onClick={() => setOpenSidebarGroupKey((prev) => (prev === group.key ? null : group.key))}
                  aria-expanded={isOpen}
                  aria-controls={`nav-group-${group.key}`}
                  title={group.description}
                >
                  <span className="nav-group-toggle-main">
                    <GroupIcon size={16} />
                    <span className="nav-group-toggle-text">
                      <span className="nav-group-label">{group.label}</span>
                      <span className="nav-group-description">{group.description}</span>
                    </span>
                  </span>
                  <span className="nav-group-toggle-meta">
                    <span className="nav-group-count">{group.pages.length}</span>
                    {isOpen ? <ChevronDown size={16} /> : <ChevronRight size={16} />}
                  </span>
                </button>
                {isOpen && (
                  <div className="nav-sublist" id={`nav-group-${group.key}`}>
                    {group.pages.map((page) => {
                      const Icon = page.icon;
                      const selected = activePage === page.key;
                      return (
                        <button
                          key={page.key}
                          className={`nav-subitem${selected ? ' active' : ''}`}
                          type="button"
                          onClick={() => navigate(`/${page.key}`)}
                          aria-current={selected ? 'page' : undefined}
                          title={page.description}
                        >
                          <Icon size={16} />
                          <span>{page.label}</span>
                        </button>
                      );
                    })}
                  </div>
                )}
              </section>
            );
          })}
        </nav>
        <div className="sidebar-footer">
          <button className="nav-item sidebar-mode-toggle" type="button" aria-label={translate('auto.k0083')} onClick={toggleThemeMode}>
            <Moon size={17} />
            <span>{resolvedThemeMode === 'dark' ? translate('auto.k0084') : translate('auto.k0085')}</span>
          </button>
        </div>
      </aside>

      {/* Main content */}
      <main className="workspace" id="main-content" role="main">
        <header className="topbar">
          <div className="topbar-info">
            <div className="topbar-kicker">
              <span>{activeSidebarGroupLabel}</span>
              <span>{activeDefinition.label}</span>
            </div>
            <h1>{activeDefinition.title}</h1>
            <p>{activeDefinition.description}</p>
          </div>
          {renderTopbarActions()}
        </header>

        <div className="page-frame">
          {renderWorkspacePage()}
        </div>
      </main>

      {/* Toasts */}
      {toastContainer}

      {/* Password change dialog */}
      {passwordDialogOpen && (
        <div className="modal-backdrop" onClick={passwordChangeRequired ? undefined : () => setPasswordDialogOpen(false)}>
          <div className="modal-panel" onClick={(e) => e.stopPropagation()}>
            <div className="modal-heading">
              <h2>{passwordChangeRequired ? t('auth.passwordInitialTitle') : t('auth.passwordChangeTitle')}</h2>
              <button className="btn btn-ghost btn-sm" onClick={passwordChangeRequired ? onLogout : () => setPasswordDialogOpen(false)} disabled={passwordDialogState.status === 'submitting'}>
                {passwordChangeRequired ? t('actions.logout') : t('actions.cancel')}
              </button>
            </div>
            <form className="modal-body" onSubmit={onChangePassword}>
              {passwordChangeRequired && (
                <div className="notice warning">{t('auth.initialPasswordNotice')}</div>
              )}
              <div className="field">
                <label className="field-label" htmlFor="current-password">{t('auth.currentPassword')}</label>
                <input
                  id="current-password"
                  type="password"
                  autoComplete="current-password"
                  placeholder={translate('auto.k0086')}
                  {...passwordForm.register('oldPassword', { onChange: () => setPasswordDialogState({ status: 'idle' }) })}
                  aria-invalid={Boolean(passwordForm.formState.errors.oldPassword)}
                  required
                />
                {passwordForm.formState.errors.oldPassword ? <span className="field-error">{passwordForm.formState.errors.oldPassword.message}</span> : null}
              </div>
              <div className="field">
                <label className="field-label" htmlFor="new-password">{translate('auto.k0087')}</label>
                <input
                  id="new-password"
                  type="password"
                  autoComplete="new-password"
                  placeholder={translate('auto.k0088')}
                  minLength={10}
                  {...passwordForm.register('newPassword', { onChange: () => setPasswordDialogState({ status: 'idle' }) })}
                  aria-invalid={Boolean(passwordForm.formState.errors.newPassword)}
                  required
                />
                {passwordForm.formState.errors.newPassword ? <span className="field-error">{passwordForm.formState.errors.newPassword.message}</span> : null}
              </div>
              <div className="field">
                <label className="field-label" htmlFor="confirm-new-password">{t('auth.passwordConfirm')}</label>
                <input
                  id="confirm-new-password"
                  type="password"
                  autoComplete="new-password"
                  placeholder={translate('auto.k0089')}
                  minLength={10}
                  {...passwordForm.register('confirmPassword', { onChange: () => setPasswordDialogState({ status: 'idle' }) })}
                  aria-invalid={Boolean(passwordForm.formState.errors.confirmPassword)}
                  required
                />
                {passwordForm.formState.errors.confirmPassword ? <span className="field-error">{passwordForm.formState.errors.confirmPassword.message}</span> : null}
              </div>
              {passwordDialogState.status === 'error' && (
                <div className="notice error">{passwordDialogState.message}</div>
              )}
              <div className="modal-footer modal-footer-compact">
                <button className="btn btn-primary" type="submit" disabled={passwordDialogState.status === 'submitting'}>
                  {passwordDialogState.status === 'submitting' ? translate('auto.k0090') : translate('auto.k0091')}
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
              <h2>{t('auth.passwordResetTitle')}</h2>
              <button className="btn btn-ghost btn-sm" onClick={() => setResetPasswordDialogOpen(false)} disabled={resetPasswordDialogState.status === 'submitting'}>{t('actions.cancel')}</button>
            </div>
            <form className="modal-body" onSubmit={onResetPassword}>
              <div className="field">
                <label className="field-label" htmlFor="reset-password-username">{t('auth.account')}</label>
                <input id="reset-password-username" type="text" {...resetPasswordForm.register('username')} disabled />
              </div>
              <div className="field">
                <label className="field-label" htmlFor="reset-password-new">{translate('auto.k0087')}</label>
                <input
                  id="reset-password-new"
                  type="password"
                  autoComplete="new-password"
                  placeholder={translate('auto.k0088')}
                  minLength={10}
                  {...resetPasswordForm.register('newPassword', { onChange: () => setResetPasswordDialogState({ status: 'idle' }) })}
                  aria-invalid={Boolean(resetPasswordForm.formState.errors.newPassword)}
                  required
                />
                {resetPasswordForm.formState.errors.newPassword ? <span className="field-error">{resetPasswordForm.formState.errors.newPassword.message}</span> : null}
              </div>
              <div className="field">
                <label className="field-label" htmlFor="reset-password-confirm">{t('auth.passwordConfirm')}</label>
                <input
                  id="reset-password-confirm"
                  type="password"
                  autoComplete="new-password"
                  placeholder={translate('auto.k0089')}
                  minLength={10}
                  {...resetPasswordForm.register('confirmPassword', { onChange: () => setResetPasswordDialogState({ status: 'idle' }) })}
                  aria-invalid={Boolean(resetPasswordForm.formState.errors.confirmPassword)}
                  required
                />
                {resetPasswordForm.formState.errors.confirmPassword ? <span className="field-error">{resetPasswordForm.formState.errors.confirmPassword.message}</span> : null}
              </div>
              {resetPasswordDialogState.status === 'error' && (
                <div className="notice error">{resetPasswordDialogState.message}</div>
              )}
              <div className="modal-footer modal-footer-compact">
                <button className="btn btn-primary" type="submit" disabled={resetPasswordDialogState.status === 'submitting'}>
                  {resetPasswordDialogState.status === 'submitting' ? translate('auto.k0090') : translate('auto.k0092')}
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
    return <div className="badge badge-neutral"><Activity size={13} />{translate('auto.k0093')}</div>;
  }
  if (health.error || !health.data) {
    return <div className="badge badge-danger"><Activity size={13} />{translate('auto.k0094')}</div>;
  }
  return <div className="badge badge-success"><CheckCircle2 size={13} />{translate('auto.k0095')}</div>;
}
