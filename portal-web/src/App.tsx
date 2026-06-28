import { LockOutlined, UserOutlined } from '@ant-design/icons';
import { Alert, App as AntApp, Button, Card, ConfigProvider, Form, Input, Modal, Typography, theme } from 'antd';
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
import { EnterpriseConsole } from './components/EnterpriseConsole';
import { queryClient } from './platform/queryClient';
import { useAppSessionStore } from './platform/appStore';
import { useThemeStore } from './platform/themeStore';
import { translate } from './platform/i18n';

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
      theme={{
        algorithm: resolvedThemeMode === 'dark' ? theme.darkAlgorithm : theme.defaultAlgorithm,
        token: {
          borderRadius: 6,
          colorPrimary: '#0f766e',
          fontFamily: "'PingFang SC', 'Microsoft YaHei', 'Noto Sans SC', -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif"
        }
      }}
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
          // Ignore logout endpoint failures; local session cleanup is authoritative for the UI.
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
      <EnterpriseConsole
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

function LoginPage(props: {
  error: string;
  form: ReturnType<typeof Form.useForm<LoginForm>>[0];
  loading: boolean;
  onSubmit: (values: LoginForm) => void;
}) {
  return (
    <main className="va-login-page" aria-label="Veri Agent">
      <Card className="va-login-card">
        <div className="va-login-brand">
          <div className="va-login-mark">VA</div>
          <Typography.Title level={2}>Veri Agent</Typography.Title>
          <Typography.Text type="secondary">{translate('app.enterpriseEdition')}</Typography.Text>
        </div>
        {props.error ? <Alert className="va-login-alert" message={props.error} type="error" showIcon /> : null}
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
      </Card>
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
      {props.force ? <Alert className="va-login-alert" message={translate('auth.initialPasswordNotice')} type="warning" showIcon /> : null}
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
        <div className="va-modal-actions">
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
