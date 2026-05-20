import {
  Activity,
  AppWindow,
  Archive,
  Building2,
  CheckCircle2,
  ClipboardList,
  DatabaseZap,
  FileText,
  GitBranch,
  KeyRound,
  LayoutDashboard,
  Link2,
  LockKeyhole,
  LogIn,
  LogOut,
  Pencil,
  Power,
  Save,
  ScrollText,
  ServerCog,
  Settings,
  ShieldCheck,
  UserCog,
  UserPlus,
  UserMinus,
  UsersRound,
  type LucideIcon
} from 'lucide-react';
import { useCallback, useEffect, useMemo, useState, type FormEvent, type ReactNode } from 'react';
import {
  changePassword,
  fetchCurrentUser,
  login as loginRequest,
  logout as logoutRequest,
  type CurrentUser,
  type LoginPayload
} from './api/auth';
import { bootstrapSuperAdmin, type BootstrapPayload, type BootstrapResult } from './api/bootstrap';
import { ApiError, clearAuthToken, getAuthToken } from './api/client';
import { fetchHealth, type HealthResult } from './api/health';
import { AssetWorkbench } from './components/AssetWorkbench';
import { DocumentInputConsole } from './components/DocumentInputConsole';
import {
  assignUserRole,
  addApplicationOwner,
  addEnvironmentUser,
  addProjectMember,
  changeApplicationStatus,
  changeDepartmentStatus,
  changeEnvironmentStatus,
  changeIntegrationStatus,
  changeProjectStatus,
  changeSettingStatus,
  createIntegration,
  createManagementItem,
  createSetting,
  disableUser,
  enableUser,
  fetchApplication,
  fetchApplicationOwners,
  fetchDepartment,
  fetchEnvironment,
  fetchEnvironmentUsers,
  fetchIntegration,
  fetchProject,
  fetchProjectMembers,
  fetchSetting,
  fetchUser,
  fetchManagementData,
  lockUser,
  removeApplicationOwner,
  removeEnvironmentUser,
  removeProjectMember,
  resetUserPassword,
  unassignUserRole,
  unlockUser,
  updateApplication,
  updateDepartment,
  updateEnvironment,
  updateIntegration,
  updateProject,
  updateSetting,
  updateUser,
  type ApplicationView,
  type AuditLogView,
  type CreatableManagementResource,
  type DepartmentView,
  type EnvironmentView,
  type IntegrationView,
  type ManagementData,
  type ProjectMemberView,
  type ProjectView,
  type RoleView,
  type ScopedUserRoleView,
  type SettingView,
  type UserView
} from './api/management';
import {
  canAccessPage,
  hasPermission,
  resourceCreatePermissions,
  userLifecyclePermission,
  type PageKey,
  type UserLifecycleAction
} from './permissions';

const initialForm: BootstrapPayload = {
  bootstrap_token: '',
  username: '',
  password: '',
  display_name: '',
  email: ''
};

const initialLoginForm: LoginPayload = {
  username: '',
  password: ''
};

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

const initialPasswordForm: PasswordForm = {
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
};

const initialResetPasswordForm: ResetPasswordForm = {
  username: '',
  newPassword: '',
  confirmPassword: ''
};

type SubmitState =
  | { status: 'idle' }
  | { status: 'success'; traceId: string; data: BootstrapResult }
  | { status: 'error'; traceId: string; message: string; code: string; fields?: string[] };

type LoginState =
  | { status: 'idle' }
  | { status: 'loading' }
  | { status: 'success'; traceId: string }
  | { status: 'error'; traceId: string; message: string; code: string };

type PasswordDialogState =
  | { status: 'idle' }
  | { status: 'submitting' }
  | { status: 'error'; traceId: string; message: string; code: string };

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
    title: '平台初始化',
    description: '创建首个 SuperAdmin，确认后端健康状态，建立 WP1 管理后台入口。',
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
    key: 'organizations',
    label: '组织部门',
    title: '组织部门',
    description: '维护部门层级、负责人和成员规模，为权限分配提供组织上下文。',
    icon: GitBranch
  },
  {
    key: 'users',
    label: '用户与权限',
    title: '用户与权限',
    description: '集中查看用户、角色、权限策略与账号状态。',
    icon: UsersRound
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
  departments: [],
  users: [],
  roles: [],
  projects: [],
  applications: [],
  environments: [],
  integrations: [],
  auditLogs: [],
  settings: []
};

type ResourceDraft = Record<string, string>;

type ResourceEditField = {
  key: string;
  label: string;
  placeholder?: string;
  kind?: 'text' | 'select' | 'public-model';
  options?: Array<{ value: string; label: string }>;
};

type StatusOption = {
  value: string;
  label: string;
  icon: LucideIcon;
};

const sensitivityOptions = [
  { value: '', label: '保持不变' },
  { value: 'PUBLIC', label: 'PUBLIC' },
  { value: 'INTERNAL', label: 'INTERNAL' },
  { value: 'CONFIDENTIAL', label: 'CONFIDENTIAL' },
  { value: 'STRICT', label: 'STRICT' }
];

const publicModelOptions = [
  { value: '', label: '保持不变' },
  { value: 'true', label: '允许公有云模型' },
  { value: 'false', label: '禁用公有云模型' }
];

const appTypeOptions = [
  { value: '', label: '保持不变' },
  { value: 'Web', label: 'Web' },
  { value: 'Backend', label: 'Backend' },
  { value: 'Frontend', label: 'Frontend' },
  { value: 'Mobile', label: 'Mobile' },
  { value: 'Service', label: 'Service' },
  { value: 'API', label: 'API' }
];

const envTypeOptions = [
  { value: '', label: '保持不变' },
  { value: 'DEV', label: 'DEV' },
  { value: 'TEST', label: 'TEST' },
  { value: 'STAGING', label: 'STAGING' },
  { value: 'PREPROD', label: 'PREPROD' },
  { value: 'PROD', label: 'PROD' }
];

const projectEditFields: ResourceEditField[] = [
  { key: 'name', label: '项目名称', placeholder: '输入新项目名称' },
  { key: 'sensitivity_level', label: '敏感级别', kind: 'select', options: sensitivityOptions },
  { key: 'allow_public_model', label: '公有云模型', kind: 'public-model', options: publicModelOptions }
];

const applicationEditFields: ResourceEditField[] = [
  { key: 'name', label: '应用名称', placeholder: '输入新应用名称' },
  { key: 'app_type', label: '应用类型', kind: 'select', options: appTypeOptions },
  { key: 'default_web_url', label: '默认 Web URL', placeholder: 'https://web.example.test' },
  { key: 'default_api_base_url', label: '默认 API Base URL', placeholder: 'https://api.example.test' },
  { key: 'sensitivity_level', label: '敏感级别', kind: 'select', options: sensitivityOptions },
  { key: 'allow_public_model', label: '公有云模型', kind: 'public-model', options: publicModelOptions }
];

const environmentEditFields: ResourceEditField[] = [
  { key: 'name', label: '环境名称', placeholder: '输入新环境名称' },
  { key: 'env_type', label: '环境类型', kind: 'select', options: envTypeOptions },
  { key: 'web_url', label: 'Web URL', placeholder: 'https://web.env.test' },
  { key: 'api_base_url', label: 'API Base URL', placeholder: 'https://api.env.test' }
];

const departmentEditFields: ResourceEditField[] = [
  { key: 'name', label: '部门名称', placeholder: '输入新部门名称' }
];

const userEditFields: ResourceEditField[] = [
  { key: 'display_name', label: '显示名称', placeholder: '输入显示名称' },
  { key: 'email', label: '邮箱', placeholder: 'user@example.com' }
];

type ManagementLoadState = {
  loading: boolean;
  traceId?: string;
  error?: string;
};

function activePageFromHash(): PageKey {
  const pageKey = window.location.hash.replace(/^#\/?/, '').split('/')[0];
  return pages.some((page) => page.key === pageKey) ? (pageKey as PageKey) : 'overview';
}

function navigateToPage(page: PageKey, setPage: (page: PageKey) => void) {
  const hash = `#${page}`;
  if (window.location.hash === hash || window.location.hash.startsWith(`${hash}/`)) {
    setPage(page);
    return;
  }
  window.location.hash = hash;
}

export function App() {
  const [activePage, setActivePage] = useState<PageKey>(() => activePageFromHash());
  const [form, setForm] = useState<BootstrapPayload>(initialForm);
  const [submitting, setSubmitting] = useState(false);
  const [submitState, setSubmitState] = useState<SubmitState>({ status: 'idle' });
  const [loginForm, setLoginForm] = useState<LoginPayload>(initialLoginForm);
  const [loginState, setLoginState] = useState<LoginState>({ status: 'idle' });
  const [passwordDialogOpen, setPasswordDialogOpen] = useState(false);
  const [passwordForm, setPasswordForm] = useState<PasswordForm>(initialPasswordForm);
  const [passwordDialogState, setPasswordDialogState] = useState<PasswordDialogState>({ status: 'idle' });
  const [resetPasswordDialogOpen, setResetPasswordDialogOpen] = useState(false);
  const [resetPasswordForm, setResetPasswordForm] = useState<ResetPasswordForm>(initialResetPasswordForm);
  const [resetPasswordDialogState, setResetPasswordDialogState] = useState<PasswordDialogState>({ status: 'idle' });
  const [currentUser, setCurrentUser] = useState<CurrentUser | null>(null);
  const [managementData, setManagementData] = useState<ManagementData>(emptyManagementData);
  const [managementLoad, setManagementLoad] = useState<ManagementLoadState>({ loading: false });
  const [health, setHealth] = useState<{ loading: boolean; traceId?: string; data?: HealthResult; error?: string }>({
    loading: true
  });
  const visiblePages = useMemo(() => pages.filter((page) => canAccessPage(currentUser, page.key)), [currentUser]);

  useEffect(() => {
    function syncPageFromHash() {
      setActivePage(activePageFromHash());
    }

    window.addEventListener('hashchange', syncPageFromHash);
    return () => window.removeEventListener('hashchange', syncPageFromHash);
  }, []);

  useEffect(() => {
    let active = true;
    fetchHealth()
      .then((response) => {
        if (!active) return;
        setHealth({ loading: false, traceId: response.trace_id, data: response.data });
      })
      .catch((error: unknown) => {
        if (!active) return;
        const message = error instanceof Error ? error.message : '健康检查失败';
        setHealth({ loading: false, error: message });
      });
    return () => {
      active = false;
    };
  }, []);

  const refreshManagementData = useCallback(async () => {
    if (!getAuthToken() || !currentUser) {
      setManagementData(emptyManagementData);
      setManagementLoad({ loading: false });
      return;
    }

    setManagementLoad({ loading: true });
    try {
      const response = await fetchManagementData(currentUser.permissions ?? []);
      setManagementData(response.data);
      setManagementLoad({ loading: false, traceId: response.traceId });
    } catch (error: unknown) {
      const message = error instanceof Error ? error.message : '管理数据加载失败';
      setManagementLoad({ loading: false, error: message });
    }
  }, [currentUser]);

  useEffect(() => {
    if (!getAuthToken()) {
      return;
    }
    fetchCurrentUser()
      .then((response) => {
        setCurrentUser(response.data);
        setLoginState({ status: 'success', traceId: response.trace_id });
      })
      .catch(() => {
        clearAuthToken();
        setCurrentUser(null);
        setLoginState({ status: 'idle' });
      });
  }, []);

  useEffect(() => {
    if (!currentUser) {
      setManagementData(emptyManagementData);
      setManagementLoad({ loading: false });
      return;
    }
    void refreshManagementData();
  }, [currentUser, refreshManagementData]);

  useEffect(() => {
    if (!canAccessPage(currentUser, activePage)) {
      window.history.replaceState(null, '', '#overview');
      setActivePage('overview');
    }
  }, [activePage, currentUser]);

  const activeDefinition = pages.find((page) => page.key === activePage) ?? pages[0];
  const validationErrors = useMemo(() => validateForm(form), [form]);
  const canSubmit = validationErrors.length === 0 && !submitting;

  function updateField<K extends keyof BootstrapPayload>(key: K, value: BootstrapPayload[K]) {
    setForm((current) => ({ ...current, [key]: value }));
    setSubmitState({ status: 'idle' });
  }

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (validationErrors.length > 0) {
      setSubmitState({
        status: 'error',
        code: 'LOCAL_VALIDATION_ERROR',
        message: '请先修正表单字段',
        traceId: '',
        fields: validationErrors
      });
      return;
    }
    setSubmitting(true);
    try {
      const response = await bootstrapSuperAdmin(form);
      setSubmitState({ status: 'success', traceId: response.trace_id, data: response.data });
      setForm((current) => ({ ...current, password: '', bootstrap_token: '' }));
    } catch (error: unknown) {
      if (error instanceof ApiError) {
        const fields = error.detail?.field_errors?.map((item) => `${item.field}: ${item.reason}`);
        setSubmitState({
          status: 'error',
          code: error.code,
          message: error.message,
          traceId: error.traceId,
          fields
        });
      } else {
        const message = error instanceof Error ? error.message : '初始化请求失败';
        setSubmitState({ status: 'error', code: 'REQUEST_FAILED', message, traceId: '' });
      }
    } finally {
      setSubmitting(false);
    }
  }

  async function onLogin(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!loginForm.username.trim() || !loginForm.password) {
      setLoginState({ status: 'error', code: 'LOCAL_VALIDATION_ERROR', message: '请输入账号和密码', traceId: '' });
      return;
    }

    setLoginState({ status: 'loading' });
    try {
      const response = await loginRequest(loginForm);
      const currentUserResponse = await fetchCurrentUser();
      setCurrentUser(currentUserResponse.data);
      setLoginForm((current) => ({ ...current, password: '' }));
      setLoginState({ status: 'success', traceId: response.trace_id });
    } catch (error: unknown) {
      clearAuthToken();
      setCurrentUser(null);
      if (error instanceof ApiError) {
        setLoginState({
          status: 'error',
          code: error.code,
          message: error.message,
          traceId: error.traceId
        });
      } else {
        const message = error instanceof Error ? error.message : '登录失败';
        setLoginState({ status: 'error', code: 'REQUEST_FAILED', message, traceId: '' });
      }
    }
  }

  function resetSignedInState() {
    clearAuthToken();
    setCurrentUser(null);
    setManagementData(emptyManagementData);
    setManagementLoad({ loading: false });
    setLoginForm(initialLoginForm);
  }

  async function onLogout() {
    try {
      await logoutRequest();
    } catch {
      // Local logout should still clear the browser state if the server session has already expired.
    } finally {
      resetSignedInState();
      setLoginState({ status: 'idle' });
    }
  }

  function openPasswordDialog() {
    setPasswordDialogOpen(true);
    setPasswordForm(initialPasswordForm);
    setPasswordDialogState({ status: 'idle' });
  }

  function closePasswordDialog() {
    if (passwordDialogState.status === 'submitting') {
      return;
    }
    setPasswordDialogOpen(false);
    setPasswordForm(initialPasswordForm);
    setPasswordDialogState({ status: 'idle' });
  }

  async function onChangePassword(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!currentUser) {
      return;
    }
    const oldPassword = passwordForm.oldPassword;
    const newPassword = passwordForm.newPassword;
    if (!oldPassword || !newPassword || !passwordForm.confirmPassword) {
      setPasswordDialogState({ status: 'error', code: 'LOCAL_VALIDATION_ERROR', message: '请填写完整密码信息', traceId: '' });
      return;
    }
    if (newPassword !== passwordForm.confirmPassword) {
      setPasswordDialogState({ status: 'error', code: 'LOCAL_VALIDATION_ERROR', message: '两次输入的新密码不一致', traceId: '' });
      return;
    }
    if (newPassword.length < 10) {
      setPasswordDialogState({ status: 'error', code: 'LOCAL_VALIDATION_ERROR', message: '新密码至少 10 位', traceId: '' });
      return;
    }
    if (oldPassword === newPassword) {
      setPasswordDialogState({ status: 'error', code: 'LOCAL_VALIDATION_ERROR', message: '新密码不能与旧密码相同', traceId: '' });
      return;
    }
    setPasswordDialogState({ status: 'submitting' });
    try {
      const response = await changePassword({ old_password: oldPassword, new_password: newPassword });
      resetSignedInState();
      setPasswordDialogOpen(false);
      setPasswordForm(initialPasswordForm);
      setPasswordDialogState({ status: 'idle' });
      setLoginState({ status: 'error', code: 'PASSWORD_CHANGED', message: '密码已修改，请重新登录', traceId: response.trace_id });
    } catch (error: unknown) {
      if (error instanceof ApiError) {
        setPasswordDialogState({ status: 'error', code: error.code, message: error.message, traceId: error.traceId });
      } else {
        const message = error instanceof Error ? error.message : '密码修改失败';
        setPasswordDialogState({ status: 'error', code: 'REQUEST_FAILED', message, traceId: '' });
      }
    }
  }

  function openResetPasswordDialog(username: string) {
    setResetPasswordDialogOpen(true);
    setResetPasswordForm({ username, newPassword: '', confirmPassword: '' });
    setResetPasswordDialogState({ status: 'idle' });
  }

  function closeResetPasswordDialog() {
    if (resetPasswordDialogState.status === 'submitting') {
      return;
    }
    setResetPasswordDialogOpen(false);
    setResetPasswordForm(initialResetPasswordForm);
    setResetPasswordDialogState({ status: 'idle' });
  }

  async function onResetPassword(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!currentUser) {
      setResetPasswordDialogState({ status: 'error', code: 'UNAUTHORIZED', message: '请先登录后再操作', traceId: '' });
      return;
    }
    if (!hasPermission(currentUser, 'user:reset_password')) {
      setResetPasswordDialogState({ status: 'error', code: 'FORBIDDEN', message: '当前账号无重置密码权限', traceId: '' });
      return;
    }
    if (!resetPasswordForm.newPassword || !resetPasswordForm.confirmPassword) {
      setResetPasswordDialogState({ status: 'error', code: 'LOCAL_VALIDATION_ERROR', message: '请填写新密码和确认密码', traceId: '' });
      return;
    }
    if (resetPasswordForm.newPassword !== resetPasswordForm.confirmPassword) {
      setResetPasswordDialogState({ status: 'error', code: 'LOCAL_VALIDATION_ERROR', message: '两次输入的新密码不一致', traceId: '' });
      return;
    }
    if (resetPasswordForm.newPassword.length < 10) {
      setResetPasswordDialogState({ status: 'error', code: 'LOCAL_VALIDATION_ERROR', message: '新密码至少 10 位', traceId: '' });
      return;
    }

    setResetPasswordDialogState({ status: 'submitting' });
    try {
      await resetUserPassword(resetPasswordForm.username, resetPasswordForm.newPassword);
      setResetPasswordDialogOpen(false);
      setResetPasswordForm(initialResetPasswordForm);
      setResetPasswordDialogState({ status: 'idle' });
      await refreshManagementData();
    } catch (error: unknown) {
      if (error instanceof ApiError) {
        setResetPasswordDialogState({ status: 'error', code: error.code, message: error.message, traceId: error.traceId });
      } else {
        const message = error instanceof Error ? error.message : '重置密码失败';
        setResetPasswordDialogState({ status: 'error', code: 'REQUEST_FAILED', message, traceId: '' });
      }
    }
  }

  async function onCreateManagementItem(resource: CreatableManagementResource, label: string, rawName: string) {
    if (!currentUser) {
      setManagementLoad({ loading: false, error: '请先登录后再操作' });
      return;
    }
    if (!hasPermission(currentUser, resourceCreatePermissions[resource])) {
      setManagementLoad({ loading: false, error: `当前账号无${label}创建权限` });
      return;
    }

    const name = rawName.trim();
    if (!name) {
      return;
    }

    setManagementLoad({ loading: true });
    try {
      await createManagementItem(resource, name);
      await refreshManagementData();
    } catch (error: unknown) {
      const message = error instanceof Error ? error.message : '创建失败';
      setManagementLoad({ loading: false, error: message });
    }
  }

  async function onUserLifecycleAction(username: string, action: UserLifecycleAction, roleCodeInput = '') {
    if (!currentUser) {
      setManagementLoad({ loading: false, error: '请先登录后再操作' });
      return;
    }
    if (!hasPermission(currentUser, userLifecyclePermission(action))) {
      setManagementLoad({ loading: false, error: '当前账号无该账号操作权限' });
      return;
    }
    if (action === 'assign-role' && !hasPermission(currentUser, 'role:bind')) {
      setManagementLoad({ loading: false, error: '当前账号无角色分配权限' });
      return;
    }
    if (action === 'unassign-role' && !hasPermission(currentUser, 'role:unbind')) {
      setManagementLoad({ loading: false, error: '当前账号无角色解绑权限' });
      return;
    }

    if ((action === 'disable' || action === 'lock') && username === currentUser.username) {
      setManagementLoad({ loading: false, error: action === 'disable' ? '不能停用当前登录账号' : '不能锁定当前登录账号' });
      return;
    }

    let newPassword = '';
    if (action === 'reset-password') {
      newPassword = window.prompt(`为 ${username} 设置新密码`, '')?.trim() ?? '';
      if (!newPassword) {
        return;
      }
      if (newPassword.length < 10) {
        setManagementLoad({ loading: false, error: '新密码至少 10 位' });
        return;
      }
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
      if (action === 'enable') {
        await enableUser(username);
      } else if (action === 'unlock') {
        await unlockUser(username);
      } else if (action === 'disable') {
        await disableUser(username);
      } else if (action === 'lock') {
        await lockUser(username);
      } else if (action === 'reset-password') {
        await resetUserPassword(username, newPassword);
      } else if (action === 'assign-role') {
        await assignUserRole(username, roleCode);
      } else {
        await unassignUserRole(username, roleCode);
      }
      await refreshManagementData();
    } catch (error: unknown) {
      const message = error instanceof Error ? error.message : '账号操作失败';
      setManagementLoad({ loading: false, error: message });
    }
  }

  return (
    <div className="app-shell">
      <aside className="sidebar" aria-label="主导航">
        <div className="brand">
          <div className="brand-mark">VA</div>
          <div>
            <div className="brand-name">Veri Agent</div>
            <div className="brand-subtitle">测试平台控制台</div>
          </div>
        </div>

        <nav className="nav-list" aria-label="控制台页面">
          {visiblePages.map((page) => {
            const Icon = page.icon;
            const selected = activePage === page.key;
            return (
              <button
                className={`nav-item ${selected ? 'active' : ''}`}
                type="button"
                key={page.key}
                onClick={() => navigateToPage(page.key, setActivePage)}
                aria-current={selected ? 'page' : undefined}
              >
                <Icon size={18} />
                <span>{page.label}</span>
              </button>
            );
          })}
        </nav>
      </aside>

      <main className="workspace">
        <header className="topbar">
          <div>
            <h1>{activeDefinition.title}</h1>
            <p>{activeDefinition.description}</p>
          </div>
          <div className="topbar-actions">
            <HealthBadge health={health} />
            <AuthPanel
              currentUser={currentUser}
              loginForm={loginForm}
              loginState={loginState}
              onLogin={onLogin}
              onLogout={onLogout}
              onChangePassword={openPasswordDialog}
              updateLoginField={(key, value) => {
                setLoginForm((current) => ({ ...current, [key]: value }));
                setLoginState({ status: 'idle' });
              }}
            />
          </div>
        </header>

        {activePage === 'overview' ? (
          <OverviewPage
            form={form}
            health={health}
            managementData={managementData}
            canSubmit={canSubmit}
            submitting={submitting}
            submitState={submitState}
            validationErrors={validationErrors}
            onSubmit={onSubmit}
            updateField={updateField}
          />
        ) : (
          <ModulePage
            page={activePage}
            data={managementData}
            loadState={managementLoad}
            signedIn={Boolean(currentUser)}
            currentUser={currentUser}
            onCreate={onCreateManagementItem}
            onUserLifecycleAction={onUserLifecycleAction}
            onResetPassword={openResetPasswordDialog}
            onRefresh={refreshManagementData}
          />
        )}
      </main>

      {passwordDialogOpen && (
        <ChangePasswordDialog
          form={passwordForm}
          state={passwordDialogState}
          onCancel={closePasswordDialog}
          onSubmit={onChangePassword}
          updateField={(key, value) => {
            setPasswordForm((current) => ({ ...current, [key]: value }));
            setPasswordDialogState({ status: 'idle' });
          }}
        />
      )}

      {resetPasswordDialogOpen && (
        <ResetPasswordDialog
          form={resetPasswordForm}
          state={resetPasswordDialogState}
          onCancel={closeResetPasswordDialog}
          onSubmit={onResetPassword}
          updateField={(key, value) => {
            setResetPasswordForm((current) => ({ ...current, [key]: value }));
            setResetPasswordDialogState({ status: 'idle' });
          }}
        />
      )}
    </div>
  );
}

function OverviewPage(props: {
  form: BootstrapPayload;
  health: { loading: boolean; traceId?: string; data?: HealthResult; error?: string };
  managementData: ManagementData;
  canSubmit: boolean;
  submitting: boolean;
  submitState: SubmitState;
  validationErrors: string[];
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
  updateField: <K extends keyof BootstrapPayload>(key: K, value: BootstrapPayload[K]) => void;
}) {
  return (
    <div className="overview-layout">
      <section className="metrics-grid" aria-label="平台摘要">
        <MetricCard
          label="部门"
          value={String(props.managementData.departments.length)}
          detail={`${countByStatus(props.managementData.departments, '同步正常')} 个同步正常`}
          icon={Building2}
        />
        <MetricCard
          label="项目空间"
          value={String(props.managementData.projects.length)}
          detail={`${countByStatus(props.managementData.projects, '进行中')} 个进行中`}
          icon={DatabaseZap}
        />
        <MetricCard
          label="集成"
          value={String(props.managementData.integrations.length)}
          detail={`${countByStatus(props.managementData.integrations, '已连接')} 个已连接`}
          icon={Link2}
        />
        <MetricCard
          label="审计事件"
          value={String(props.managementData.auditLogs.length)}
          detail="当前工作区"
          icon={ClipboardList}
        />
      </section>

      <section className="content-grid">
        <section className="panel init-panel" id="bootstrap">
          <div className="section-heading">
            <div className="section-icon">
              <UserCog size={20} />
            </div>
            <div>
              <h2>超级管理员初始化</h2>
              <p>初始化入口只应在系统首次部署时使用，成功后重复提交会被后端拒绝。</p>
            </div>
          </div>

          <form className="form-grid" onSubmit={props.onSubmit}>
            <Field label="初始化令牌" htmlFor="bootstrap-token" required>
              <input
                id="bootstrap-token"
                type="password"
                autoComplete="off"
                value={props.form.bootstrap_token}
                onChange={(event) => props.updateField('bootstrap_token', event.target.value)}
                placeholder="输入 WP1_BOOTSTRAP_TOKEN"
              />
            </Field>
            <Field label="登录账号" htmlFor="username" required>
              <input
                id="username"
                value={props.form.username}
                onChange={(event) => props.updateField('username', event.target.value)}
                placeholder="admin_user"
              />
            </Field>
            <Field label="初始密码" htmlFor="password" required>
              <input
                id="password"
                type="password"
                autoComplete="new-password"
                value={props.form.password}
                onChange={(event) => props.updateField('password', event.target.value)}
                placeholder="至少 10 位"
              />
            </Field>
            <Field label="显示名称" htmlFor="display-name" required>
              <input
                id="display-name"
                value={props.form.display_name}
                onChange={(event) => props.updateField('display_name', event.target.value)}
                placeholder="平台管理员"
              />
            </Field>
            <Field label="邮箱" htmlFor="email">
              <input
                id="email"
                type="email"
                value={props.form.email}
                onChange={(event) => props.updateField('email', event.target.value)}
                placeholder="admin@example.com"
              />
            </Field>

            {props.submitState.status !== 'idle' && <SubmitNotice state={props.submitState} />}

            <div className="actions">
              <button className="primary-button" type="submit" disabled={!props.canSubmit}>
                <KeyRound size={17} />
                {props.submitting ? '正在初始化' : '创建 SuperAdmin'}
              </button>
              {props.submitState.status !== 'success' && props.validationErrors.length > 0 && (
                <span className="inline-hint">{props.validationErrors[0]}</span>
              )}
            </div>
          </form>
        </section>

        <aside className="panel detail-panel">
          <h2>当前准入状态</h2>
          <div className="status-list">
            <StatusItem label="后端健康检查" value={props.health.data?.status ?? (props.health.loading ? '检查中' : '异常')} />
            <StatusItem label="服务名称" value={props.health.data?.service ?? 'platform-api'} />
            <StatusItem label="Trace ID" value={props.health.traceId ?? '等待响应'} compact />
          </div>
          <div className="divider" />
          <h2>WP1 页面范围</h2>
          <div className="roadmap-list">
            <span>组织、用户、角色权限治理</span>
            <span>项目、应用、环境基础配置</span>
            <span>外部集成、审计日志、系统设置</span>
            <span>列表接口、创建动作和审计记录已接入</span>
          </div>
        </aside>
      </section>
    </div>
  );
}

function ModulePage(props: {
  page: PageKey;
  data: ManagementData;
  loadState: ManagementLoadState;
  signedIn: boolean;
  currentUser: CurrentUser | null;
  onCreate: (resource: CreatableManagementResource, label: string, name: string) => Promise<void>;
  onUserLifecycleAction: (username: string, action: UserLifecycleAction, roleCode?: string) => Promise<void>;
  onResetPassword: (username: string) => void;
  onRefresh: () => void;
}) {
  if (props.page === 'document-input') {
    return <DocumentInputConsole signedIn={props.signedIn} currentUser={props.currentUser} />;
  }

  if (props.page === 'asset-library') {
    return <AssetWorkbench signedIn={props.signedIn} currentUser={props.currentUser} />;
  }

  if (props.page === 'organizations') {
    return (
      <DataSection
        eyebrow="Organization"
        title="部门结构"
        icon={GitBranch}
        action="新增部门"
        createResource="departments"
        columns={['部门', '上级部门', '负责人', '成员', '状态']}
        rows={props.data.departments.map((item: DepartmentView) => [item.name, item.parent, item.lead, item.members, item.status])}
        loadState={props.loadState}
        signedIn={props.signedIn}
        canCreate={hasPermission(props.currentUser, 'department:create')}
        onCreate={props.onCreate}
        onRefresh={props.onRefresh}
        sidePanel={
          <ResourceLifecyclePanel<DepartmentView>
            title="部门详情"
            resourceLabel="部门"
            emptyLabel="暂无部门"
            resources={props.data.departments.map((item) => item.name)}
            fields={departmentEditFields}
            signedIn={props.signedIn}
            canEdit={hasPermission(props.currentUser, 'department:edit')}
            statusOptions={[
              hasPermission(props.currentUser, 'department:enable') ? { value: 'ENABLED', label: '启用部门', icon: Power } : undefined,
              hasPermission(props.currentUser, 'department:disable') ? { value: 'DISABLED', label: '停用部门', icon: Power } : undefined
            ].filter((option): option is StatusOption => Boolean(option))}
            fetchDetail={fetchDepartment}
            updateDetail={(resourceKey, draft) => updateDepartment(resourceKey, buildDepartmentUpdate(draft))}
            changeStatus={changeDepartmentStatus}
            detailTitle={(detail) => detail.name}
            draftFromDetail={(detail) => ({ name: detail.name })}
            detailRows={(detail) => [
              ['上级部门', detail.parent],
              ['负责人', detail.lead],
              ['成员数', detail.members],
              ['状态', detail.status]
            ]}
            onChanged={props.onRefresh}
          />
        }
      />
    );
  }

  if (props.page === 'users') {
    const canEnable = hasPermission(props.currentUser, 'user:enable');
    const canDisable = hasPermission(props.currentUser, 'user:disable');
    const canLock = hasPermission(props.currentUser, 'user:lock');
    const canEdit = hasPermission(props.currentUser, 'user:edit');
    const canResetPassword = hasPermission(props.currentUser, 'user:reset_password');
    const canAssignRole = hasPermission(props.currentUser, 'user:assign_role') && hasPermission(props.currentUser, 'role:bind');
    const canUnassignRole = hasPermission(props.currentUser, 'user:assign_role') && hasPermission(props.currentUser, 'role:unbind');
    const canMutateUsers = canEnable || canDisable || canLock || canResetPassword || canAssignRole || canUnassignRole;
    return (
      <DataSection
        eyebrow="Access"
        title="账号与角色"
        icon={ShieldCheck}
        action="邀请用户"
        createResource="users"
        columns={canMutateUsers ? ['用户名', '角色', '部门', '状态', '最近访问', '操作'] : ['用户名', '角色', '部门', '状态', '最近访问']}
        rows={props.data.users.map((item: UserView) => [
          item.username,
          item.role,
          item.department,
          item.status,
          item.last_seen,
          canMutateUsers ? (
            <div className="row-actions" key={`${item.username}-actions`}>
              <RoleBindingControls
                username={item.username}
                roles={props.data.roles}
                loading={props.loadState.loading}
                signedIn={props.signedIn}
                canAssign={canAssignRole}
                canUnassign={canUnassignRole}
                onAction={props.onUserLifecycleAction}
              />
              {canEnable && (
                <button
                  className="mini-button"
                  type="button"
                  disabled={!props.signedIn || props.loadState.loading}
                  onClick={() => props.onUserLifecycleAction(item.username, item.status === '已锁定' ? 'unlock' : 'enable')}
                  title={item.status === '已锁定' ? '解锁账号' : '启用账号'}
                >
                  <CheckCircle2 size={14} />
                  {item.status === '已锁定' ? '解锁' : '启用'}
                </button>
              )}
              {canLock && (
                <button
                  className="mini-button"
                  type="button"
                  disabled={!props.signedIn || props.loadState.loading || item.username === props.currentUser?.username || item.status === '已锁定'}
                  onClick={() => props.onUserLifecycleAction(item.username, 'lock')}
                  title="锁定账号"
                >
                  <LockKeyhole size={14} />
                  锁定
                </button>
              )}
              {canDisable && (
                <button
                  className="mini-button"
                  type="button"
                  disabled={!props.signedIn || props.loadState.loading || item.username === props.currentUser?.username}
                  onClick={() => props.onUserLifecycleAction(item.username, 'disable')}
                  title="停用账号"
                >
                  <LockKeyhole size={14} />
                  停用
                </button>
              )}
              {canResetPassword && (
                <button
                  className="mini-button"
                  type="button"
                  disabled={!props.signedIn || props.loadState.loading}
                  onClick={() => props.onResetPassword(item.username)}
                  title="重置密码"
                >
                  <KeyRound size={14} />
                  重置
                </button>
              )}
            </div>
          ) : undefined
        ].filter((cell) => cell !== undefined))}
        loadState={props.loadState}
        signedIn={props.signedIn}
        canCreate={hasPermission(props.currentUser, 'user:create')}
        onCreate={props.onCreate}
        onRefresh={props.onRefresh}
        sidePanel={
          <ResourceLifecyclePanel<UserView>
            title="用户详情"
            resourceLabel="用户"
            emptyLabel="暂无用户"
            resources={props.data.users.map((item) => item.username)}
            fields={userEditFields}
            signedIn={props.signedIn}
            canEdit={canEdit}
            statusOptions={[]}
            fetchDetail={fetchUser}
            updateDetail={(resourceKey, draft) => updateUser(resourceKey, buildUserUpdate(draft))}
            changeStatus={() => Promise.resolve()}
            detailTitle={(detail) => detail.display_name || detail.username}
            draftFromDetail={(detail) => ({
              display_name: detail.display_name,
              email: detail.email
            })}
            detailRows={(detail) => [
              ['账号', detail.username],
              ['邮箱', detail.email || '-'],
              ['角色', detail.role],
              ['部门', detail.department],
              ['状态', detail.status],
              ['最近访问', detail.last_seen]
            ]}
            onChanged={props.onRefresh}
          />
        }
      />
    );
  }

  if (props.page === 'projects') {
    return (
      <DataSection
        eyebrow="Workspace"
        title="项目空间"
        icon={DatabaseZap}
        action="创建项目"
        createResource="projects"
        columns={['项目', '归属部门', '负责人', '应用数', '状态']}
        rows={props.data.projects.map((item: ProjectView) => [item.name, item.department, item.owner, item.apps, item.status])}
        loadState={props.loadState}
        signedIn={props.signedIn}
        canCreate={hasPermission(props.currentUser, 'project:create')}
        onCreate={props.onCreate}
        onRefresh={props.onRefresh}
        sidePanel={
          <>
            <ResourceLifecyclePanel<ProjectView>
              title="项目详情"
              resourceLabel="项目"
              emptyLabel="暂无项目"
              resources={props.data.projects.map((item) => item.name)}
              fields={projectEditFields}
              signedIn={props.signedIn}
              canEdit={hasPermission(props.currentUser, 'project:edit')}
              statusOptions={[
                hasPermission(props.currentUser, 'project:edit') ? { value: 'ACTIVE', label: '设为进行中', icon: Power } : undefined,
                hasPermission(props.currentUser, 'project:edit') ? { value: 'PREPARING', label: '设为规划中', icon: Pencil } : undefined,
                hasPermission(props.currentUser, 'project:archive') ? { value: 'ARCHIVED', label: '归档项目', icon: Archive } : undefined,
                hasPermission(props.currentUser, 'project:disable') ? { value: 'DISABLED', label: '停用项目', icon: Power } : undefined
              ].filter((option): option is StatusOption => Boolean(option))}
              fetchDetail={fetchProject}
              updateDetail={(resourceKey, draft) => updateProject(resourceKey, buildProjectUpdate(draft))}
              changeStatus={changeProjectStatus}
              detailTitle={(detail) => detail.name}
              draftFromDetail={(detail) => ({ name: detail.name })}
              detailRows={(detail) => [
                ['归属部门', detail.department],
                ['负责人', detail.owner],
                ['应用数', detail.apps],
                ['状态', detail.status]
              ]}
              onChanged={props.onRefresh}
            />
            <ScopedRolePanel
              title="项目成员"
              resourceLabel="项目"
              emptyLabel="暂无项目"
              resources={props.data.projects.map((item) => item.name)}
              roles={['ProjectOwner', 'Tester', 'Developer', 'Auditor']}
              signedIn={props.signedIn}
              canManage={hasPermission(props.currentUser, 'project:member_manage')}
              fetchMembers={fetchProjectMembers}
              addMember={addProjectMember}
              removeMember={removeProjectMember}
              onChanged={props.onRefresh}
            />
          </>
        }
      />
    );
  }

  if (props.page === 'applications') {
    return (
      <DataSection
        eyebrow="Application"
        title="应用清单"
        icon={AppWindow}
        action="登记应用"
        createResource="applications"
        columns={['应用', '类型', '负责团队', '版本', '状态']}
        rows={props.data.applications.map((item: ApplicationView) => [item.name, item.type, item.owner, item.version, item.status])}
        loadState={props.loadState}
        signedIn={props.signedIn}
        canCreate={hasPermission(props.currentUser, 'application:create')}
        onCreate={props.onCreate}
        onRefresh={props.onRefresh}
        sidePanel={
          <>
            <ResourceLifecyclePanel<ApplicationView>
              title="应用详情"
              resourceLabel="应用"
              emptyLabel="暂无应用"
              resources={props.data.applications.map((item) => item.name)}
              fields={applicationEditFields}
              signedIn={props.signedIn}
              canEdit={hasPermission(props.currentUser, 'application:edit')}
              statusOptions={[
                hasPermission(props.currentUser, 'application:edit') ? { value: 'ENABLED', label: '启用应用', icon: Power } : undefined,
                hasPermission(props.currentUser, 'application:disable') ? { value: 'DISABLED', label: '停用应用', icon: Power } : undefined
              ].filter((option): option is StatusOption => Boolean(option))}
              fetchDetail={fetchApplication}
              updateDetail={(resourceKey, draft) => updateApplication(resourceKey, buildApplicationUpdate(draft))}
              changeStatus={changeApplicationStatus}
              detailTitle={(detail) => detail.name}
              draftFromDetail={(detail) => ({ name: detail.name, app_type: detail.type })}
              detailRows={(detail) => [
                ['类型', detail.type],
                ['负责团队', detail.owner],
                ['版本', detail.version],
                ['状态', detail.status]
              ]}
              onChanged={props.onRefresh}
            />
            <ScopedRolePanel
              title="应用负责人"
              resourceLabel="应用"
              emptyLabel="暂无应用"
              resources={props.data.applications.map((item) => item.name)}
              roles={['AppOwner']}
              signedIn={props.signedIn}
              canManage={hasPermission(props.currentUser, 'application:owner_manage')}
              fetchMembers={fetchApplicationOwners}
              addMember={(resourceKey, username) => addApplicationOwner(resourceKey, username)}
              removeMember={removeApplicationOwner}
              onChanged={props.onRefresh}
            />
          </>
        }
      />
    );
  }

  if (props.page === 'environments') {
    return (
      <DataSection
        eyebrow="Environment"
        title="环境配置"
        icon={ServerCog}
        action="新增环境"
        createResource="environments"
        columns={['环境', '集群', 'Endpoint', '状态']}
        rows={props.data.environments.map((item: EnvironmentView) => [item.name, item.cluster, item.endpoint, item.status])}
        loadState={props.loadState}
        signedIn={props.signedIn}
        canCreate={hasPermission(props.currentUser, 'environment:create')}
        onCreate={props.onCreate}
        onRefresh={props.onRefresh}
        sidePanel={
          <>
            <ResourceLifecyclePanel<EnvironmentView>
              title="环境详情"
              resourceLabel="环境"
              emptyLabel="暂无环境"
              resources={props.data.environments.map((item) => item.name)}
              fields={environmentEditFields}
              signedIn={props.signedIn}
              canEdit={hasPermission(props.currentUser, 'environment:edit')}
              statusOptions={[
                hasPermission(props.currentUser, 'environment:edit') ? { value: 'ENABLED', label: '启用环境', icon: Power } : undefined,
                hasPermission(props.currentUser, 'environment:disable') ? { value: 'DISABLED', label: '停用环境', icon: Power } : undefined
              ].filter((option): option is StatusOption => Boolean(option))}
              fetchDetail={fetchEnvironment}
              updateDetail={(resourceKey, draft) => updateEnvironment(resourceKey, buildEnvironmentUpdate(draft))}
              changeStatus={changeEnvironmentStatus}
              detailTitle={(detail) => detail.name}
              draftFromDetail={(detail) => ({ name: detail.name, api_base_url: detail.endpoint })}
              detailRows={(detail) => [
                ['集群', detail.cluster],
                ['Endpoint', detail.endpoint],
                ['状态', detail.status]
              ]}
              onChanged={props.onRefresh}
            />
            <ScopedRolePanel
              title="环境授权用户"
              resourceLabel="环境"
              emptyLabel="暂无环境"
              resources={props.data.environments.map((item) => item.name)}
              roles={['Tester', 'Developer', 'Auditor']}
              signedIn={props.signedIn}
              canManage={hasPermission(props.currentUser, 'environment:user_manage')}
              fetchMembers={fetchEnvironmentUsers}
              addMember={addEnvironmentUser}
              removeMember={removeEnvironmentUser}
              onChanged={props.onRefresh}
            />
          </>
        }
      />
    );
  }

  if (props.page === 'integrations') {
    return (
      <DataSection
        eyebrow="Integration"
        title="外部集成"
        icon={Link2}
        action="登记"
        columns={['集成名称', '类型', '范围', '状态']}
        rows={props.data.integrations.map((item: IntegrationView) => [item.name, item.category, item.scope, item.status])}
        loadState={props.loadState}
        signedIn={props.signedIn}
        onRefresh={props.onRefresh}
        sidePanel={
          <ResourceLifecyclePanel<IntegrationView>
            title="集成维护"
            resourceLabel="集成"
            emptyLabel="暂无集成"
            resources={props.data.integrations.map((item) => item.key)}
            fields={[
              { key: 'name', label: '名称', placeholder: '禅道' },
              { key: 'category', label: '类型', placeholder: '缺陷系统/通知/审批' },
              { key: 'scope', label: '范围', placeholder: '平台级/项目级' }
            ]}
            signedIn={props.signedIn}
            canEdit={hasPermission(props.currentUser, 'config:edit')}
            statusOptions={[
              hasPermission(props.currentUser, 'config:edit') ? { value: 'ENABLED', label: '启用集成', icon: Power } : undefined,
              hasPermission(props.currentUser, 'config:edit') ? { value: 'DISABLED', label: '停用集成', icon: Power } : undefined
            ].filter((option): option is StatusOption => Boolean(option))}
            fetchDetail={fetchIntegration}
            updateDetail={(resourceKey, draft) => updateIntegration(resourceKey, buildIntegrationUpdate(draft))}
            changeStatus={changeIntegrationStatus}
            detailTitle={(detail) => detail.name}
            draftFromDetail={(detail) => ({ name: detail.name, category: detail.category, scope: detail.scope })}
            detailRows={(detail) => [
              ['标识', detail.key],
              ['类型', detail.category],
              ['范围', detail.scope],
              ['状态', detail.status]
            ]}
            createLabel="登记集成"
            canCreate={hasPermission(props.currentUser, 'config:edit')}
            createFields={[
              { key: 'code', label: '标识', placeholder: 'zentao' },
              { key: 'name', label: '名称', placeholder: '禅道' },
              { key: 'category', label: '类型', placeholder: '缺陷系统' },
              { key: 'scope', label: '范围', placeholder: '项目级' }
            ]}
            createInitialDraft={{ category: '通知/审批', scope: '平台级' }}
            createDetail={(draft) => createIntegration(buildIntegrationCreate(draft))}
            onChanged={props.onRefresh}
          />
        }
      />
    );
  }

  if (props.page === 'audit') {
    return (
      <DataSection
        eyebrow="Audit"
        title="审计事件"
        icon={ScrollText}
        action="刷新"
        columns={['时间', '操作人', '动作', '对象', '结果']}
        rows={props.data.auditLogs.map((item: AuditLogView) => [item.time, item.actor, item.action, item.target, item.result])}
        loadState={props.loadState}
        signedIn={props.signedIn}
        onRefresh={props.onRefresh}
      />
    );
  }

  return (
    <DataSection
      eyebrow="Settings"
      title="系统参数"
      icon={Settings}
      action="刷新"
      columns={['配置项', '当前值', '作用域', '状态']}
      rows={props.data.settings.map((item: SettingView) => [item.name, item.value, item.scope, item.status])}
      loadState={props.loadState}
      signedIn={props.signedIn}
      onRefresh={props.onRefresh}
      sidePanel={
        <ResourceLifecyclePanel<SettingView>
          title="设置维护"
          resourceLabel="设置"
          emptyLabel="暂无设置"
          resources={props.data.settings.map((item) => item.key)}
          fields={[
            { key: 'name', label: '名称', placeholder: '失败登录阈值' },
            { key: 'value', label: '当前值', placeholder: '5' },
            {
              key: 'scope_type',
              label: '作用域',
              kind: 'select',
              options: [
                { value: '', label: '保持不变' },
                { value: 'SYSTEM', label: '平台级' },
                { value: 'PROJECT', label: '项目级' },
                { value: 'APPLICATION', label: '应用级' },
                { value: 'ENVIRONMENT', label: '环境级' }
              ]
            }
          ]}
          signedIn={props.signedIn}
          canEdit={hasPermission(props.currentUser, 'config:edit')}
          statusOptions={[
            hasPermission(props.currentUser, 'config:edit') ? { value: 'ENABLED', label: '启用设置', icon: Power } : undefined,
            hasPermission(props.currentUser, 'config:edit') ? { value: 'DISABLED', label: '停用设置', icon: Power } : undefined
          ].filter((option): option is StatusOption => Boolean(option))}
          fetchDetail={fetchSetting}
          updateDetail={(resourceKey, draft) => updateSetting(resourceKey, buildSettingUpdate(draft))}
          changeStatus={changeSettingStatus}
          detailTitle={(detail) => detail.name}
          draftFromDetail={(detail) => ({ name: detail.name, value: detail.value })}
          detailRows={(detail) => [
            ['标识', detail.key],
            ['当前值', detail.value],
            ['作用域', detail.scope],
            ['状态', detail.status]
          ]}
          createLabel="新增设置"
          canCreate={hasPermission(props.currentUser, 'config:edit')}
          createFields={[
            { key: 'key', label: '标识', placeholder: 'account.failed_login_limit' },
            { key: 'name', label: '名称', placeholder: '失败登录阈值' },
            { key: 'value', label: '当前值', placeholder: '5' },
            { key: 'scope_type', label: '作用域', placeholder: 'SYSTEM' }
          ]}
          createInitialDraft={{ scope_type: 'SYSTEM' }}
          createDetail={(draft) => createSetting(buildSettingCreate(draft))}
          onChanged={props.onRefresh}
        />
      }
    />
  );
}

function DataSection(props: {
  eyebrow: string;
  title: string;
  action: string;
  icon: LucideIcon;
  createResource?: CreatableManagementResource;
  columns: string[];
  rows: Array<Array<string | number | ReactNode>>;
  loadState: ManagementLoadState;
  signedIn: boolean;
  canCreate?: boolean;
  onCreate?: (resource: CreatableManagementResource, label: string, name: string) => Promise<void>;
  onRefresh: () => void;
  sidePanel?: ReactNode;
}) {
  const Icon = props.icon;
  const [draftName, setDraftName] = useState('');
  const actionDisabled = props.loadState.loading || !props.signedIn;

  async function submitCreate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!props.createResource || !props.onCreate || !draftName.trim()) {
      return;
    }
    await props.onCreate(props.createResource, props.title, draftName);
    setDraftName('');
  }

  return (
    <section className="module-layout">
      <div className="panel module-panel">
        <div className="panel-toolbar">
          <div className="section-heading compact">
            <div className="section-icon">
              <Icon size={20} />
            </div>
            <div>
              <span className="eyebrow">{props.eyebrow}</span>
              <h2>{props.title}</h2>
            </div>
          </div>
          <button
            className="secondary-button"
            type="button"
            disabled={actionDisabled}
            onClick={props.onRefresh}
          >
            刷新
          </button>
        </div>

        {props.createResource && props.canCreate && (
          <form className="quick-create-form" onSubmit={submitCreate}>
            <input
              value={draftName}
              disabled={actionDisabled}
              onChange={(event) => setDraftName(event.target.value)}
              placeholder={`${props.title}名称`}
            />
            <button className="primary-button" type="submit" disabled={actionDisabled || !draftName.trim()}>
              {props.action}
            </button>
          </form>
        )}

        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                {props.columns.map((column) => (
                  <th key={column}>{column}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {props.rows.length > 0 ? (
                props.rows.map((row, rowIndex) => (
                  <tr key={String(row[0] ?? rowIndex)}>
                    {row.map((cell, index) => (
                      <td key={`${props.columns[index]}-${index}`}>
                        {isStatusColumn(props.columns[index]) && isPrimitiveCell(cell) ? (
                          <StatusPill value={String(cell)} />
                        ) : (
                          cell
                        )}
                      </td>
                    ))}
                  </tr>
                ))
              ) : (
                <tr>
                  <td className="table-empty" colSpan={props.columns.length}>
                    {props.signedIn ? (props.loadState.loading ? '加载中' : '暂无数据') : '请先登录'}
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>

      <div className="side-stack">
        {props.sidePanel}
        <div className="panel insight-panel">
          <h2>接口状态</h2>
          <div className="empty-state">
            <LockKeyhole size={22} />
            <div>
              <strong>{props.signedIn ? (props.loadState.loading ? '正在同步' : '已连接管理 API') : '等待登录'}</strong>
              <span>{props.loadState.error ?? `当前列表 ${props.rows.length} 条`}</span>
              {props.loadState.traceId && <span>Trace ID：{props.loadState.traceId}</span>}
            </div>
          </div>
        </div>
        <div className="panel insight-panel">
          <h2>常用操作</h2>
          <div className="quick-actions">
            <button type="button">查看详情</button>
            <button type="button">编辑配置</button>
            <button type="button">查看审计</button>
          </div>
        </div>
      </div>
    </section>
  );
}

function emptyDraft(fields: ResourceEditField[]): ResourceDraft {
  return Object.fromEntries(fields.map((field) => [field.key, '']));
}

function nonBlank(value?: string) {
  const normalized = value?.trim() ?? '';
  return normalized || undefined;
}

function publicModelValue(value?: string) {
  if (value === 'true') return true;
  if (value === 'false') return false;
  return undefined;
}

function buildProjectUpdate(draft: ResourceDraft) {
  return compactPayload({
    name: nonBlank(draft.name),
    sensitivity_level: nonBlank(draft.sensitivity_level),
    allow_public_model: publicModelValue(draft.allow_public_model)
  });
}

function buildDepartmentUpdate(draft: ResourceDraft) {
  return compactPayload({
    name: nonBlank(draft.name)
  });
}

function buildUserUpdate(draft: ResourceDraft) {
  return compactPayload({
    display_name: nonBlank(draft.display_name),
    email: nonBlank(draft.email)
  });
}

function buildApplicationUpdate(draft: ResourceDraft) {
  return compactPayload({
    name: nonBlank(draft.name),
    app_type: nonBlank(draft.app_type),
    default_web_url: nonBlank(draft.default_web_url),
    default_api_base_url: nonBlank(draft.default_api_base_url),
    sensitivity_level: nonBlank(draft.sensitivity_level),
    allow_public_model: publicModelValue(draft.allow_public_model)
  });
}

function buildEnvironmentUpdate(draft: ResourceDraft) {
  return compactPayload({
    name: nonBlank(draft.name),
    env_type: nonBlank(draft.env_type),
    web_url: nonBlank(draft.web_url),
    api_base_url: nonBlank(draft.api_base_url)
  });
}

function buildIntegrationCreate(draft: ResourceDraft) {
  return {
    code: nonBlank(draft.code),
    name: nonBlank(draft.name) ?? '',
    category: nonBlank(draft.category),
    scope: nonBlank(draft.scope)
  };
}

function buildIntegrationUpdate(draft: ResourceDraft) {
  return compactPayload({
    name: nonBlank(draft.name),
    category: nonBlank(draft.category),
    scope: nonBlank(draft.scope)
  });
}

function buildSettingCreate(draft: ResourceDraft) {
  return {
    key: nonBlank(draft.key) ?? '',
    name: nonBlank(draft.name),
    value: nonBlank(draft.value) ?? '',
    scope_type: nonBlank(draft.scope_type)
  };
}

function buildSettingUpdate(draft: ResourceDraft) {
  return compactPayload({
    name: nonBlank(draft.name),
    value: nonBlank(draft.value),
    scope_type: nonBlank(draft.scope_type)
  });
}

function compactPayload<T extends Record<string, string | boolean | undefined>>(payload: T) {
  return Object.fromEntries(Object.entries(payload).filter(([, value]) => value !== undefined));
}

function ResourceLifecyclePanel<T extends { status: string }>(props: {
  title: string;
  resourceLabel: string;
  emptyLabel: string;
  resources: string[];
  fields: ResourceEditField[];
  signedIn: boolean;
  canEdit: boolean;
  statusOptions: StatusOption[];
  fetchDetail: (resourceKey: string) => Promise<{ trace_id: string; data: T }>;
  updateDetail: (resourceKey: string, draft: ResourceDraft) => Promise<unknown>;
  changeStatus: (resourceKey: string, status: string) => Promise<unknown>;
  detailTitle?: (detail: T) => string;
  draftFromDetail?: (detail: T) => ResourceDraft;
  detailRows: (detail: T) => Array<[string, string | number]>;
  createLabel?: string;
  canCreate?: boolean;
  createFields?: ResourceEditField[];
  createInitialDraft?: ResourceDraft;
  createDetail?: (draft: ResourceDraft) => Promise<unknown>;
  onChanged: () => void;
}) {
  const [selectedResource, setSelectedResource] = useState(props.resources[0] ?? '');
  const [detail, setDetail] = useState<T | null>(null);
  const [draft, setDraft] = useState<ResourceDraft>(emptyDraft(props.fields));
  const [createDraft, setCreateDraft] = useState<ResourceDraft>({ ...emptyDraft(props.createFields ?? []), ...(props.createInitialDraft ?? {}) });
  const [state, setState] = useState<{ loading: boolean; error?: string; traceId?: string }>({ loading: false });

  useEffect(() => {
    if (!props.resources.includes(selectedResource)) {
      setSelectedResource(props.resources[0] ?? '');
    }
  }, [props.resources, selectedResource]);

  const reloadDetail = useCallback(async () => {
    if (!props.signedIn || !selectedResource) {
      setDetail(null);
      setDraft(emptyDraft(props.fields));
      setState({ loading: false });
      return;
    }
    setState({ loading: true });
    try {
      const response = await props.fetchDetail(selectedResource);
      setDetail(response.data);
      setDraft({ ...emptyDraft(props.fields), ...(props.draftFromDetail ? props.draftFromDetail(response.data) : {}) });
      setState({ loading: false, traceId: response.trace_id });
    } catch (error: unknown) {
      const message = error instanceof Error ? error.message : '详情加载失败';
      setDetail(null);
      setState({ loading: false, error: message });
    }
  }, [props.fetchDetail, props.fields, props.signedIn, selectedResource]);

  useEffect(() => {
    void reloadDetail();
  }, [reloadDetail]);

  async function submitDetail(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedResource || !props.canEdit) {
      return;
    }
    setState({ loading: true });
    try {
      await props.updateDetail(selectedResource, draft);
      await reloadDetail();
      props.onChanged();
    } catch (error: unknown) {
      const message = error instanceof Error ? error.message : '详情保存失败';
      setState({ loading: false, error: message });
    }
  }

  async function submitStatus(status: string) {
    if (!selectedResource || props.statusOptions.length === 0) {
      return;
    }
    setState({ loading: true });
    try {
      await props.changeStatus(selectedResource, status);
      await reloadDetail();
      props.onChanged();
    } catch (error: unknown) {
      const message = error instanceof Error ? error.message : '状态流转失败';
      setState({ loading: false, error: message });
    }
  }

  async function submitCreate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!props.createDetail || !props.canCreate) {
      return;
    }
    setState({ loading: true });
    try {
      await props.createDetail(createDraft);
      setCreateDraft({ ...emptyDraft(props.createFields ?? []), ...(props.createInitialDraft ?? {}) });
      props.onChanged();
      setState({ loading: false });
    } catch (error: unknown) {
      const message = error instanceof Error ? error.message : '资源登记失败';
      setState({ loading: false, error: message });
    }
  }

  const disabled = !props.signedIn || state.loading || !selectedResource;

  return (
    <div className="panel insight-panel resource-lifecycle-panel">
      <h2>{props.title}</h2>
      <label className="resource-selector">
        <span>{props.resourceLabel}</span>
        <select
          value={selectedResource}
          disabled={!props.signedIn || state.loading || props.resources.length === 0}
          onChange={(event) => setSelectedResource(event.target.value)}
        >
          {props.resources.length === 0 ? (
            <option value="">{props.emptyLabel}</option>
          ) : (
            props.resources.map((resource) => (
              <option key={resource} value={resource}>
                {resource}
              </option>
            ))
          )}
        </select>
      </label>

      {detail ? (
        <div className="resource-summary">
          <strong>{props.detailTitle ? props.detailTitle(detail) : selectedResource}</strong>
          {props.detailRows(detail).map(([label, value]) => (
            <div key={label}>
              <span>{label}</span>
              {label === '状态' ? <StatusPill value={String(value)} /> : <em>{value}</em>}
            </div>
          ))}
        </div>
      ) : (
        <div className="empty-state compact">
          <Pencil size={20} />
          <div>
            <strong>{state.loading ? '正在加载详情' : props.signedIn ? '暂无详情' : '等待登录'}</strong>
            <span>{state.error ?? props.emptyLabel}</span>
          </div>
        </div>
      )}

      {props.canEdit && (
        <form className="resource-edit-form" onSubmit={submitDetail}>
          {props.fields.map((field) => (
            <label key={field.key}>
              <span>{field.label}</span>
              {field.kind === 'select' || field.kind === 'public-model' ? (
                <select
                  value={draft[field.key] ?? ''}
                  disabled={disabled}
                  onChange={(event) => setDraft((current) => ({ ...current, [field.key]: event.target.value }))}
                >
                  {(field.options ?? []).map((option) => (
                    <option key={option.value} value={option.value}>
                      {option.label}
                    </option>
                  ))}
                </select>
              ) : (
                <input
                  value={draft[field.key] ?? ''}
                  disabled={disabled}
                  onChange={(event) => setDraft((current) => ({ ...current, [field.key]: event.target.value }))}
                  placeholder={field.placeholder}
                />
              )}
            </label>
          ))}
          <button className="mini-button" type="submit" disabled={disabled}>
            <Save size={14} />
            保存详情
          </button>
        </form>
      )}

      {props.statusOptions.length > 0 && (
        <div className="resource-status-actions">
          {props.statusOptions.map((option) => {
            const Icon = option.icon;
            return (
              <button
                className="mini-button"
                type="button"
                key={option.value}
                disabled={disabled}
                onClick={() => submitStatus(option.value)}
                title={option.label}
              >
                <Icon size={14} />
                {option.label}
              </button>
            );
          })}
        </div>
      )}

      {props.canCreate && props.createFields && props.createDetail && (
        <form className="resource-edit-form resource-create-form" onSubmit={submitCreate}>
          <strong>{props.createLabel ?? '新增资源'}</strong>
          {props.createFields.map((field) => (
            <label key={field.key}>
              <span>{field.label}</span>
              <input
                value={createDraft[field.key] ?? ''}
                disabled={!props.signedIn || state.loading}
                onChange={(event) => setCreateDraft((current) => ({ ...current, [field.key]: event.target.value }))}
                placeholder={field.placeholder}
              />
            </label>
          ))}
          <button className="mini-button" type="submit" disabled={!props.signedIn || state.loading || !createDraft.name?.trim()}>
            <Save size={14} />
            {props.createLabel ?? '新增资源'}
          </button>
        </form>
      )}

      {state.error && detail && (
        <div className="inline-error">
          <strong>操作失败</strong>
          <span>{state.error}</span>
        </div>
      )}
      {state.traceId && <div className="panel-trace">Trace ID：{state.traceId}</div>}
    </div>
  );
}

function ScopedRolePanel(props: {
  title: string;
  resourceLabel: string;
  emptyLabel: string;
  resources: string[];
  roles: string[];
  signedIn: boolean;
  canManage: boolean;
  fetchMembers: (resourceKey: string) => Promise<{ trace_id: string; data: { items: Array<ScopedUserRoleView | ProjectMemberView> } }>;
  addMember: (resourceKey: string, username: string, roleCode: string) => Promise<unknown>;
  removeMember: (resourceKey: string, username: string) => Promise<unknown>;
  onChanged: () => void;
}) {
  const [selectedResource, setSelectedResource] = useState(props.resources[0] ?? '');
  const [username, setUsername] = useState('');
  const [roleCode, setRoleCode] = useState(props.roles[0] ?? '');
  const [members, setMembers] = useState<Array<ScopedUserRoleView | ProjectMemberView>>([]);
  const [state, setState] = useState<{ loading: boolean; error?: string; traceId?: string }>({ loading: false });

  useEffect(() => {
    if (!props.resources.includes(selectedResource)) {
      setSelectedResource(props.resources[0] ?? '');
    }
  }, [props.resources, selectedResource]);

  useEffect(() => {
    if (!props.roles.includes(roleCode)) {
      setRoleCode(props.roles[0] ?? '');
    }
  }, [props.roles, roleCode]);

  const reloadMembers = useCallback(async () => {
    if (!props.signedIn || !selectedResource) {
      setMembers([]);
      setState({ loading: false });
      return;
    }
    setState({ loading: true });
    try {
      const response = await props.fetchMembers(selectedResource);
      setMembers(response.data.items);
      setState({ loading: false, traceId: response.trace_id });
    } catch (error: unknown) {
      const message = error instanceof Error ? error.message : '授权列表加载失败';
      setMembers([]);
      setState({ loading: false, error: message });
    }
  }, [props.fetchMembers, props.signedIn, selectedResource]);

  useEffect(() => {
    void reloadMembers();
  }, [reloadMembers]);

  async function submitMember(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const normalizedUsername = username.trim();
    if (!selectedResource || !normalizedUsername || !roleCode || !props.canManage) {
      return;
    }
    setState({ loading: true });
    try {
      await props.addMember(selectedResource, normalizedUsername, roleCode);
      setUsername('');
      await reloadMembers();
      props.onChanged();
    } catch (error: unknown) {
      const message = error instanceof Error ? error.message : '授权保存失败';
      setState({ loading: false, error: message });
    }
  }

  async function removeMember(usernameToRemove: string) {
    if (!selectedResource || !props.canManage) {
      return;
    }
    setState({ loading: true });
    try {
      await props.removeMember(selectedResource, usernameToRemove);
      await reloadMembers();
      props.onChanged();
    } catch (error: unknown) {
      const message = error instanceof Error ? error.message : '授权移除失败';
      setState({ loading: false, error: message });
    }
  }

  const disabled = !props.signedIn || state.loading || !selectedResource;

  return (
    <div className="panel insight-panel scoped-role-panel">
      <h2>{props.title}</h2>
      <div className="scoped-role-controls">
        <label>
          <span>{props.resourceLabel}</span>
          <select
            value={selectedResource}
            disabled={!props.signedIn || state.loading || props.resources.length === 0}
            onChange={(event) => setSelectedResource(event.target.value)}
          >
            {props.resources.length === 0 ? (
              <option value="">{props.emptyLabel}</option>
            ) : (
              props.resources.map((resource) => (
                <option key={resource} value={resource}>
                  {resource}
                </option>
              ))
            )}
          </select>
        </label>

        {props.canManage && (
          <form className="scoped-role-form" onSubmit={submitMember}>
            <input
              value={username}
              disabled={disabled}
              onChange={(event) => setUsername(event.target.value)}
              placeholder="用户名"
            />
            <select value={roleCode} disabled={disabled} onChange={(event) => setRoleCode(event.target.value)}>
              {props.roles.map((role) => (
                <option key={role} value={role}>
                  {role}
                </option>
              ))}
            </select>
            <button className="mini-button" type="submit" disabled={disabled || !username.trim()}>
              <UserPlus size={14} />
              添加
            </button>
          </form>
        )}
      </div>

      <div className="scoped-role-list">
        {members.length > 0 ? (
          members.map((member) => (
            <div className="scoped-role-row" key={member.username}>
              <div>
                <strong>{member.display_name || member.username}</strong>
                <span>{member.username}</span>
              </div>
              <StatusPill value={member.role} />
              {props.canManage && (
                <button
                  className="mini-button icon-only"
                  type="button"
                  disabled={disabled}
                  onClick={() => removeMember(member.username)}
                  title="移除授权"
                  aria-label={`移除 ${member.username}`}
                >
                  <UserMinus size={14} />
                </button>
              )}
            </div>
          ))
        ) : (
          <div className="empty-state compact">
            <UsersRound size={20} />
            <div>
              <strong>{state.loading ? '正在同步' : props.signedIn ? '暂无授权' : '等待登录'}</strong>
              <span>{state.error ?? (selectedResource ? `当前${props.resourceLabel}未绑定用户` : props.emptyLabel)}</span>
            </div>
          </div>
        )}
      </div>
      {state.traceId && <div className="panel-trace">Trace ID：{state.traceId}</div>}
    </div>
  );
}

function RoleBindingControls(props: {
  username: string;
  roles: RoleView[];
  loading: boolean;
  signedIn: boolean;
  canAssign: boolean;
  canUnassign: boolean;
  onAction: (username: string, action: UserLifecycleAction, roleCode?: string) => Promise<void>;
}) {
  const [roleCode, setRoleCode] = useState('');
  const disabled = !props.signedIn || props.loading || !roleCode;
  const visible = props.canAssign || props.canUnassign;

  if (!visible) {
    return null;
  }

  return (
    <div className="role-binding-controls">
      <select
        value={roleCode}
        disabled={!props.signedIn || props.loading}
        onChange={(event) => setRoleCode(event.target.value)}
        aria-label={`${props.username} 角色`}
      >
        <option value="">选择角色</option>
        {props.roles.map((role) => (
          <option key={role.code} value={role.code}>
            {role.name}
          </option>
        ))}
      </select>
      {props.canAssign && (
        <button
          className="mini-button icon-only"
          type="button"
          disabled={disabled}
          onClick={() => props.onAction(props.username, 'assign-role', roleCode)}
          title="分配角色"
          aria-label="分配角色"
        >
          <ShieldCheck size={14} />
        </button>
      )}
      {props.canUnassign && (
        <button
          className="mini-button icon-only"
          type="button"
          disabled={disabled}
          onClick={() => props.onAction(props.username, 'unassign-role', roleCode)}
          title="解绑角色"
          aria-label="解绑角色"
        >
          <UsersRound size={14} />
        </button>
      )}
    </div>
  );
}

function ChangePasswordDialog(props: {
  form: PasswordForm;
  state: PasswordDialogState;
  onCancel: () => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
  updateField: <K extends keyof PasswordForm>(key: K, value: PasswordForm[K]) => void;
}) {
  const submitting = props.state.status === 'submitting';

  return (
    <div className="modal-backdrop" role="presentation">
      <section className="modal-panel" role="dialog" aria-modal="true" aria-labelledby="change-password-title">
        <div className="modal-heading">
          <div>
            <span className="eyebrow">Security</span>
            <h2 id="change-password-title">修改密码</h2>
          </div>
          <button className="icon-button" type="button" onClick={props.onCancel} aria-label="关闭" disabled={submitting}>
            ×
          </button>
        </div>

        <form className="modal-form" onSubmit={props.onSubmit}>
          <Field label="当前密码" htmlFor="old-password" required>
            <input
              id="old-password"
              type="password"
              autoComplete="current-password"
              value={props.form.oldPassword}
              disabled={submitting}
              onChange={(event) => props.updateField('oldPassword', event.target.value)}
            />
          </Field>
          <Field label="新密码" htmlFor="new-password" required>
            <input
              id="new-password"
              type="password"
              autoComplete="new-password"
              value={props.form.newPassword}
              disabled={submitting}
              onChange={(event) => props.updateField('newPassword', event.target.value)}
            />
          </Field>
          <Field label="确认新密码" htmlFor="confirm-password" required>
            <input
              id="confirm-password"
              type="password"
              autoComplete="new-password"
              value={props.form.confirmPassword}
              disabled={submitting}
              onChange={(event) => props.updateField('confirmPassword', event.target.value)}
            />
          </Field>

          {props.state.status === 'error' && (
            <div className="notice error">
              <strong>{props.state.code}</strong>
              <span>{props.state.message}</span>
              {props.state.traceId && <span>Trace ID：{props.state.traceId}</span>}
            </div>
          )}

          <div className="modal-actions">
            <button className="secondary-button" type="button" onClick={props.onCancel} disabled={submitting}>
              取消
            </button>
            <button className="primary-button" type="submit" disabled={submitting}>
              <KeyRound size={17} />
              {submitting ? '提交中' : '确认修改'}
            </button>
          </div>
        </form>
      </section>
    </div>
  );
}

function ResetPasswordDialog(props: {
  form: ResetPasswordForm;
  state: PasswordDialogState;
  onCancel: () => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
  updateField: <K extends keyof ResetPasswordForm>(key: K, value: ResetPasswordForm[K]) => void;
}) {
  const submitting = props.state.status === 'submitting';

  return (
    <div className="modal-backdrop" role="presentation">
      <section className="modal-panel" role="dialog" aria-modal="true" aria-labelledby="reset-password-title">
        <div className="modal-heading">
          <div>
            <span className="eyebrow">Account</span>
            <h2 id="reset-password-title">重置密码</h2>
          </div>
          <button className="icon-button" type="button" onClick={props.onCancel} aria-label="关闭" disabled={submitting}>
            ×
          </button>
        </div>

        <form className="modal-form" onSubmit={props.onSubmit}>
          <Field label="目标账号" htmlFor="reset-password-username" required>
            <input id="reset-password-username" value={props.form.username} disabled readOnly />
          </Field>
          <Field label="新密码" htmlFor="reset-new-password" required>
            <input
              id="reset-new-password"
              type="password"
              autoComplete="new-password"
              value={props.form.newPassword}
              disabled={submitting}
              onChange={(event) => props.updateField('newPassword', event.target.value)}
            />
          </Field>
          <Field label="确认新密码" htmlFor="reset-confirm-password" required>
            <input
              id="reset-confirm-password"
              type="password"
              autoComplete="new-password"
              value={props.form.confirmPassword}
              disabled={submitting}
              onChange={(event) => props.updateField('confirmPassword', event.target.value)}
            />
          </Field>

          {props.state.status === 'error' && (
            <div className="notice error">
              <strong>{props.state.code}</strong>
              <span>{props.state.message}</span>
              {props.state.traceId && <span>Trace ID：{props.state.traceId}</span>}
            </div>
          )}

          <div className="modal-actions">
            <button className="secondary-button" type="button" onClick={props.onCancel} disabled={submitting}>
              取消
            </button>
            <button className="primary-button" type="submit" disabled={submitting}>
              <KeyRound size={17} />
              {submitting ? '提交中' : '确认重置'}
            </button>
          </div>
        </form>
      </section>
    </div>
  );
}

function AuthPanel(props: {
  currentUser: CurrentUser | null;
  loginForm: LoginPayload;
  loginState: LoginState;
  onLogin: (event: FormEvent<HTMLFormElement>) => void;
  onLogout: () => void | Promise<void>;
  onChangePassword: () => void | Promise<void>;
  updateLoginField: <K extends keyof LoginPayload>(key: K, value: LoginPayload[K]) => void;
}) {
  if (props.currentUser) {
    return (
      <div className="auth-panel signed-in">
        <div>
          <span>当前用户</span>
          <strong>{props.currentUser.display_name || props.currentUser.username}</strong>
          <em>{props.currentUser.roles.join(' / ') || '未分配角色'}</em>
        </div>
        <button type="button" className="icon-button" onClick={props.onLogout} aria-label="退出登录" title="退出登录">
          <LogOut size={17} />
        </button>
        <button type="button" className="icon-button" onClick={props.onChangePassword} aria-label="修改密码" title="修改密码">
          <KeyRound size={17} />
        </button>
      </div>
    );
  }

  return (
    <form className="auth-panel login-form" onSubmit={props.onLogin}>
      <label>
        <span>账号</span>
        <input
          value={props.loginForm.username}
          onChange={(event) => props.updateLoginField('username', event.target.value)}
          placeholder="admin_user"
        />
      </label>
      <label>
        <span>密码</span>
        <input
          type="password"
          value={props.loginForm.password}
          onChange={(event) => props.updateLoginField('password', event.target.value)}
          placeholder="初始化密码"
        />
      </label>
      <button type="submit" className="icon-button" aria-label="登录" title="登录" disabled={props.loginState.status === 'loading'}>
        <LogIn size={17} />
      </button>
      {props.loginState.status === 'error' && <small>{props.loginState.message}</small>}
    </form>
  );
}

function MetricCard(props: { label: string; value: string; detail: string; icon: LucideIcon }) {
  const Icon = props.icon;
  return (
    <div className="metric-card">
      <div className="metric-icon">
        <Icon size={18} />
      </div>
      <div>
        <span>{props.label}</span>
        <strong>{props.value}</strong>
        <p>{props.detail}</p>
      </div>
    </div>
  );
}

function Field(props: { label: string; htmlFor: string; required?: boolean; children: ReactNode }) {
  return (
    <label className="field" htmlFor={props.htmlFor}>
      <span>
        {props.label}
        {props.required && <b>*</b>}
      </span>
      {props.children}
    </label>
  );
}

function HealthBadge(props: { health: { loading: boolean; data?: HealthResult; error?: string } }) {
  const ok = props.health.data?.status === 'UP';
  return (
    <div className={`health-badge ${ok ? 'ok' : props.health.error ? 'error' : ''}`}>
      {ok ? <CheckCircle2 size={18} /> : <Activity size={18} />}
      <span>{props.health.loading ? '检查服务中' : ok ? '平台服务正常' : '平台服务异常'}</span>
    </div>
  );
}

function StatusItem(props: { label: string; value: string; compact?: boolean }) {
  return (
    <div className={`status-item ${props.compact ? 'compact' : ''}`}>
      <span>{props.label}</span>
      <strong title={props.value}>{props.value}</strong>
    </div>
  );
}

function SubmitNotice(props: { state: SubmitState }) {
  if (props.state.status === 'success') {
    return (
      <div className="notice success">
        <strong>初始化成功</strong>
        <span>用户 ID：{props.state.data.user_id}</span>
        <span>角色：{props.state.data.role}</span>
        <span>Trace ID：{props.state.traceId}</span>
      </div>
    );
  }
  if (props.state.status === 'error') {
    return (
      <div className="notice error">
        <strong>{props.state.code}</strong>
        <span>{props.state.message}</span>
        {props.state.traceId && <span>Trace ID：{props.state.traceId}</span>}
        {props.state.fields?.map((field) => <span key={field}>{field}</span>)}
      </div>
    );
  }
  return null;
}

function StatusPill(props: { value: string }) {
  const positive = ['启用', '同步正常', '进行中', '已接入', '可用', '已连接', '成功'];
  const pending = ['试用', '待确认', '待激活', '规划中', '接入中', '待授权', '只读'];
  const tone = positive.includes(props.value) ? 'positive' : pending.includes(props.value) ? 'pending' : 'neutral';
  return <span className={`status-pill ${tone}`}>{props.value}</span>;
}

function isStatusColumn(column?: string) {
  return column === '状态' || column === '结果';
}

function isPrimitiveCell(cell: string | number | ReactNode): cell is string | number {
  return typeof cell === 'string' || typeof cell === 'number';
}

function countByStatus(items: Array<{ status: string }>, status: string) {
  return items.filter((item) => item.status === status).length;
}

function validateForm(form: BootstrapPayload) {
  const errors: string[] = [];
  if (!form.bootstrap_token.trim()) errors.push('初始化令牌不能为空');
  if (!/^[A-Za-z0-9_-]{3,64}$/.test(form.username)) errors.push('账号需为 3-64 位字母、数字、下划线或中划线');
  if (form.password.length < 10) errors.push('初始密码至少 10 位');
  if (!form.display_name.trim()) errors.push('显示名称不能为空');
  if (form.email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email)) errors.push('邮箱格式不正确');
  return errors;
}
