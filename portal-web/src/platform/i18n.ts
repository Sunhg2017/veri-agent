import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';

export const resources = {
  zh: {
    translation: {
      app: {
        name: 'Veri Agent',
        subtitle: '测试平台',
        enterpriseEdition: '企业版'
      },
      actions: {
        cancel: '取消',
        confirm: '确认',
        docs: '文档',
        logout: '退出',
        retry: '重试',
        submit: '提交',
        theme: '主题'
      },
      auth: {
        account: '账号',
        currentPassword: '当前密码',
        initialPasswordNotice: '首次登录或密码已被管理员重置，请设置新密码后继续使用。',
        login: '登 录',
        loginSubtitle: '测试平台 · 请登录',
        password: '密码',
        passwordChangeTitle: '修改密码',
        passwordConfirm: '确认新密码',
        passwordInitialTitle: '请修改初始密码',
        passwordPlaceholder: '请输入密码',
        passwordResetTitle: '重置密码',
        passwordSubmit: '确认修改',
        resetSubmit: '确认重置',
        usernamePlaceholder: '请输入用户名',
        validating: '正在验证登录状态...'
      },
      nav: {
        skip: '跳到主内容'
      },
      validation: {
        loginRequired: '请输入账号和密码',
        passwordComplete: '请填写完整密码信息',
        passwordConfirmRequired: '请填写新密码和确认密码',
        passwordMismatch: '两次输入的密码不一致',
        passwordNewMismatch: '两次输入的新密码不一致',
        passwordMin: '密码至少 10 位',
        passwordNewMin: '新密码至少 10 位',
        passwordSame: '新密码不能与旧密码相同'
      }
    }
  },
  en: {
    translation: {
      app: {
        name: 'Veri Agent',
        subtitle: 'Testing Platform',
        enterpriseEdition: 'Enterprise'
      },
      actions: {
        cancel: 'Cancel',
        confirm: 'Confirm',
        docs: 'Docs',
        logout: 'Logout',
        retry: 'Retry',
        submit: 'Submit',
        theme: 'Theme'
      },
      auth: {
        account: 'Account',
        currentPassword: 'Current password',
        initialPasswordNotice: 'Your initial or reset password must be changed before continuing.',
        login: 'Sign in',
        loginSubtitle: 'Testing platform · Sign in',
        password: 'Password',
        passwordChangeTitle: 'Change password',
        passwordConfirm: 'Confirm password',
        passwordInitialTitle: 'Change initial password',
        passwordPlaceholder: 'Enter password',
        passwordResetTitle: 'Reset password',
        passwordSubmit: 'Change password',
        resetSubmit: 'Reset password',
        usernamePlaceholder: 'Enter username',
        validating: 'Validating session...'
      },
      nav: {
        skip: 'Skip to content'
      },
      validation: {
        loginRequired: 'Enter account and password',
        passwordComplete: 'Complete all password fields',
        passwordConfirmRequired: 'Enter and confirm the new password',
        passwordMismatch: 'Passwords do not match',
        passwordNewMismatch: 'New passwords do not match',
        passwordMin: 'Password must be at least 10 characters',
        passwordNewMin: 'New password must be at least 10 characters',
        passwordSame: 'New password must differ from the old password'
      }
    }
  }
} as const;

if (!i18n.isInitialized) {
  void i18n
    .use(initReactI18next)
    .init({
      fallbackLng: 'zh',
      interpolation: { escapeValue: false },
      lng: window.localStorage.getItem('veri-agent.locale') ?? 'zh',
      resources
    });
}

export { i18n };
