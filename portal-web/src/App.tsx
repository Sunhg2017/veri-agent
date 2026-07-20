import { CheckOutlined, LockOutlined, UserOutlined } from '@ant-design/icons';
import { Alert, App as AntApp, Button, ConfigProvider, Form, Input, Modal, theme } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import type { CurrentUser } from './api/auth';
import {
  changePassword,
  fetchCurrentUser,
  login as loginRequest,
  logout as logoutRequest
} from './api/auth';
import { ApiError, clearAuthToken, getAuthToken, setAuthToken, setRefreshToken, setSessionId } from './api/client';
import { AppRoutes } from './app/router';
import { queryClient } from './platform/queryClient';
import { useAppSessionStore } from './platform/appStore';
import { useThemeStore } from './platform/themeStore';
import { translate } from './platform/i18n';
import { lightThemeConfig } from './theme/themeConfig';

type LoginForm = {
  password: string;
  username: string;
};

type PasswordForm = {
  confirmPassword: string;
  newPassword: string;
  oldPassword: string;
};

export function App() {
  const resolvedThemeMode = useThemeStore((state) => state.resolvedMode);

  return (
    <ConfigProvider
      locale={zhCN}
      theme={
        resolvedThemeMode === 'dark'
          ? { ...lightThemeConfig, algorithm: theme.darkAlgorithm }
          : lightThemeConfig
      }
    >
      <AntApp>
        <AppContent />
      </AntApp>
    </ConfigProvider>
  );
}

function AppContent() {
  const { message, modal } = AntApp.useApp();
  const currentUser = useAppSessionStore((state) => state.currentUser);
  const setCurrentUser = useAppSessionStore((state) => state.setCurrentUser);
  const resolvedThemeMode = useThemeStore((state) => state.resolvedMode);
  const toggleThemeMode = useThemeStore((state) => state.toggleMode);
  const [loginForm] = Form.useForm<LoginForm>();
  const [passwordForm] = Form.useForm<PasswordForm>();
  const [loginError, setLoginError] = useState('');
  const [loginLoading, setLoginLoading] = useState(false);
  const [passwordModalOpen, setPasswordModalOpen] = useState(false);
  const [passwordSubmitting, setPasswordSubmitting] = useState(false);

  const currentUserQuery = useQuery({
    enabled: Boolean(getAuthToken()),
    queryFn: async () => {
      const response = await fetchCurrentUser();
      return response.data;
    },
    queryKey: ['current-user'],
    retry: 0
  });

  useEffect(() => {
    if (currentUserQuery.data) {
      setCurrentUser(currentUserQuery.data);
    }
  }, [currentUserQuery.data, setCurrentUser]);

  useEffect(() => {
    if (currentUserQuery.isError) {
      clearSignedInState(setCurrentUser);
    }
  }, [currentUserQuery.isError, setCurrentUser]);

  useEffect(() => {
    if (currentUser?.must_change_password) {
      setPasswordModalOpen(true);
    }
  }, [currentUser?.must_change_password, currentUser?.user_id]);

  async function handleLogin(values: LoginForm) {
    setLoginError('');
    setLoginLoading(true);
    try {
      const response = await loginRequest({
        password: values.password,
        username: values.username.trim()
      });
      setAuthToken(response.data.access_token);
      setRefreshToken(response.data.refresh_token);
      setSessionId(response.data.session_id);
      const userResponse = await fetchCurrentUser();
      setCurrentUser(userResponse.data);
      queryClient.setQueryData(['current-user'], userResponse.data);
      loginForm.resetFields();
      void message.success(translate('auto.k0050'));
    } catch (error) {
      clearSignedInState(setCurrentUser);
      setLoginError(error instanceof ApiError ? error.message : translate('auto.k2843'));
    } finally {
      setLoginLoading(false);
    }
  }

  async function handleLogout() {
    modal.confirm({
      centered: true,
      content: translate('auto.k0053'),
      okButtonProps: { danger: true },
      okText: translate('auto.k0080'),
      title: translate('auto.k0054'),
      onOk: async () => {
        try {
          await logoutRequest();
        } catch {
          // 忽略登出接口失败，本地会话清理为准
        } finally {
          clearSignedInState(setCurrentUser);
          void message.info(translate('auto.k0055'));
        }
      }
    });
  }

  async function handlePasswordChange(values: PasswordForm) {
    if (values.newPassword !== values.confirmPassword) {
      passwordForm.setFields([{ name: 'confirmPassword', errors: [translate('validation.passwordNewMismatch')] }]);
      return;
    }
    setPasswordSubmitting(true);
    try {
      await changePassword({
        new_password: values.newPassword,
        old_password: values.oldPassword
      });
      passwordForm.resetFields();
      setPasswordModalOpen(false);
      clearSignedInState(setCurrentUser);
      void message.success(translate('auto.k0056'));
    } catch (error) {
      void message.error(error instanceof Error ? error.message : translate('auto.k0057'));
    } finally {
      setPasswordSubmitting(false);
    }
  }

  return currentUser ? (
    <>
      <AppRoutes
        currentUser={currentUser}
        themeMode={resolvedThemeMode}
        onChangePassword={() => setPasswordModalOpen(true)}
        onLogout={() => void handleLogout()}
        onToggleTheme={toggleThemeMode}
      />
      <PasswordModal
        force={Boolean(currentUser.must_change_password)}
        form={passwordForm}
        open={passwordModalOpen}
        submitting={passwordSubmitting}
        onCancel={() => {
          if (!currentUser.must_change_password) {
            setPasswordModalOpen(false);
          }
        }}
        onLogout={() => void handleLogout()}
        onSubmit={(values) => void handlePasswordChange(values)}
      />
    </>
  ) : (
    <LoginPage error={loginError} form={loginForm} loading={loginLoading || currentUserQuery.isLoading} onSubmit={(values) => void handleLogin(values)} />
  );
}

/** 分屏登录页：左侧品牌区（业务流水线可视化）+ 右侧表单 */
function LoginPage(props: {
  error: string;
  form: ReturnType<typeof Form.useForm<LoginForm>>[0];
  loading: boolean;
  onSubmit: (values: LoginForm) => void;
}) {
  return (
    <main className="auth-layout" aria-label="Veri Agent">
      <div className="auth-layout-inner">
        <div className="auth-brand">
          <div className="auth-brand-logo">
            <div className="auth-brand-mark">VA</div>
            <div className="auth-brand-logo-text">
              <span className="auth-brand-name">Veri Agent</span>
              <span className="auth-brand-tag">AI Testing Platform</span>
            </div>
          </div>
          <h1 className="auth-brand-title">AI 驱动的一体化测试平台</h1>
          <p className="auth-brand-desc">
            从需求文档到质量报告，将用例设计、接口与 UI 自动化、执行编排与失败诊断整合为一条智能流水线。
          </p>
          <div className="auth-showcase">
            <div className="auth-pipeline-card">
              <div className="auth-pipeline-head">
                <span className="auth-pipeline-dots" aria-hidden="true"><i /><i /><i /></span>
                <span className="auth-pipeline-name">智能回归流水线 · RUN #1284</span>
                <span className="auth-pipeline-status"><i />运行中</span>
              </div>
              <div className="auth-pipeline-steps">
                <div className="auth-pipeline-step is-done">
                  <span className="auth-pipeline-node"><CheckOutlined /></span>
                  <span className="auth-pipeline-label">需求解析</span>
                </div>
                <div className="auth-pipeline-step is-done">
                  <span className="auth-pipeline-node"><CheckOutlined /></span>
                  <span className="auth-pipeline-label">用例生成</span>
                </div>
                <div className="auth-pipeline-step is-running">
                  <span className="auth-pipeline-node" />
                  <span className="auth-pipeline-label">自动化执行</span>
                </div>
                <div className="auth-pipeline-step">
                  <span className="auth-pipeline-node" />
                  <span className="auth-pipeline-label">智能报告</span>
                </div>
              </div>
              <div className="auth-pipeline-metrics">
                <div className="auth-pipeline-metric">
                  <strong>96.8%</strong>
                  <span>通过率</span>
                </div>
                <div className="auth-pipeline-metric">
                  <strong>1,248</strong>
                  <span>执行用例</span>
                </div>
                <div className="auth-pipeline-metric">
                  <strong>18m 32s</strong>
                  <span>总耗时</span>
                </div>
              </div>
            </div>
            <div className="auth-diag-card">
              <span className="auth-diag-pulse" aria-hidden="true" />
              <div className="auth-diag-text">
                <strong>AI 失败诊断</strong>
                <span>已定位 3 个疑似根因</span>
              </div>
            </div>
          </div>
          <ul className="auth-brand-points">
            <li>AI 用例生成与智能评审</li>
            <li>接口 / UI 自动化执行与编排</li>
            <li>全链路资产追溯与质量洞察</li>
          </ul>
        </div>
        <div className="auth-form-card">
          <div className="auth-form-inner">
          <div className="auth-form-brand">
            <div className="auth-brand-mark">VA</div>
            <span className="auth-brand-name">Veri Agent</span>
          </div>
          <h2 className="auth-form-title">欢迎登录</h2>
          <p className="auth-form-subtitle">{translate('auth.loginSubtitle')}</p>
          {props.error ? <Alert style={{ marginBottom: 20 }} message={props.error} type="error" showIcon /> : null}
          <Form form={props.form} layout="vertical" requiredMark={false} onFinish={props.onSubmit}>
            <Form.Item name="username" label={translate('auth.account')} rules={[{ required: true, message: translate('auth.usernamePlaceholder') }]}>
              <Input autoComplete="username" autoFocus prefix={<UserOutlined />} size="large" />
            </Form.Item>
            <Form.Item name="password" label={translate('auth.password')} rules={[{ required: true, message: translate('auth.passwordPlaceholder') }]}>
              <Input.Password autoComplete="current-password" prefix={<LockOutlined />} size="large" />
            </Form.Item>
            <Button block htmlType="submit" loading={props.loading} size="large" type="primary">
              {translate('auth.login')}
            </Button>
          </Form>
            <div className="auth-form-footer">Veri Agent · AI 驱动的一体化测试平台</div>
          </div>
        </div>
      </div>
    </main>
  );
}

function PasswordModal(props: {
  force: boolean;
  form: ReturnType<typeof Form.useForm<PasswordForm>>[0];
  open: boolean;
  submitting: boolean;
  onCancel: () => void;
  onLogout: () => void;
  onSubmit: (values: PasswordForm) => void;
}) {
  return (
    <Modal
      centered
      closable={!props.force}
      footer={null}
      mask={{ closable: !props.force }}
      open={props.open}
      title={props.force ? translate('auth.passwordInitialTitle') : translate('auth.passwordChangeTitle')}
      onCancel={props.onCancel}
    >
      {props.force ? <Alert style={{ marginBottom: 20 }} message={translate('auth.initialPasswordNotice')} type="warning" showIcon /> : null}
      <Form form={props.form} layout="vertical" requiredMark={false} onFinish={props.onSubmit}>
        <Form.Item name="oldPassword" label={translate('auth.currentPassword')} rules={[{ required: true, message: translate('auth.currentPassword') }]}>
          <Input.Password autoComplete="current-password" />
        </Form.Item>
        <Form.Item name="newPassword" label={translate('auth.passwordResetTitle')} rules={[{ min: 10, message: translate('validation.passwordNewMin') }, { required: true, message: translate('auth.passwordResetTitle') }]}>
          <Input.Password autoComplete="new-password" />
        </Form.Item>
        <Form.Item name="confirmPassword" label={translate('auth.passwordConfirm')} rules={[{ required: true, message: translate('validation.passwordConfirmRequired') }]}>
          <Input.Password autoComplete="new-password" />
        </Form.Item>
        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
          {props.force ? <Button onClick={props.onLogout}>{translate('actions.logout')}</Button> : <Button onClick={props.onCancel}>{translate('actions.cancel')}</Button>}
          <Button htmlType="submit" loading={props.submitting} type="primary">{translate('auth.passwordSubmit')}</Button>
        </div>
      </Form>
    </Modal>
  );
}

function clearSignedInState(setCurrentUser: (user: CurrentUser | null) => void) {
  clearAuthToken();
  setCurrentUser(null);
  queryClient.clear();
}
