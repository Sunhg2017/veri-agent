import { Spin } from 'antd';
import { lazy, Suspense, type ReactNode } from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';
import { ConsoleLayout } from '../layouts/ConsoleLayout';
import type { CurrentUser } from '../api/auth';
import { withWorkbenchRoute } from '../pages/workbenchRoute';
import { OverviewPageRoute } from '../pages/overview/OverviewPageRoute';
import { SystemPageRoute } from '../pages/system/SystemPageRoute';

/**
 * 集中式路由表。
 * - 顶层路径与历史保持一致，e2e smoke 测试无需修改
 * - 各工作台 React.lazy 按需加载，缩小首屏体积
 * - 旧管理路径（/users 等）重定向到 /system/*
 */

const DocumentInputConsole = lazyWorkbench(() => import('../components/DocumentInputConsole').then((m) => ({ default: m.DocumentInputConsole })));
const AssetWorkbench = lazyWorkbench(() => import('../components/AssetWorkbench').then((m) => ({ default: m.AssetWorkbench })));
const TestDesignWorkbench = lazyWorkbench(() => import('../components/TestDesignWorkbench').then((m) => ({ default: m.TestDesignWorkbench })));
const ApiAutomationWorkbench = lazyWorkbench(() => import('../components/ApiAutomationWorkbench').then((m) => ({ default: m.ApiAutomationWorkbench })));
const UiE2eWorkbench = lazyWorkbench(() => import('../components/UiE2eWorkbench').then((m) => ({ default: m.UiE2eWorkbench })));
const ExecutionWorkbench = lazyWorkbench(() => import('../components/ExecutionWorkbench').then((m) => ({ default: m.ExecutionWorkbench })));
const TestDataWorkbench = lazyWorkbench(() => import('../components/TestDataWorkbench').then((m) => ({ default: m.TestDataWorkbench })));
const ReportsWorkbench = lazyWorkbench(() => import('../components/ReportsWorkbench').then((m) => ({ default: m.ReportsWorkbench })));
const ModelAccessConsole = lazyWorkbench(() => import('../components/ModelAccessConsole').then((m) => ({ default: m.ModelAccessConsole })));

function lazyWorkbench(factory: Parameters<typeof lazy>[0]) {
  const Component = lazy(factory);
  return withWorkbenchRoute(Component);
}

function PageLoading() {
  return (
    <div className="page-loading">
      <Spin size="large" />
    </div>
  );
}

function lazyPage(node: ReactNode) {
  return <Suspense fallback={<PageLoading />}>{node}</Suspense>;
}

export function AppRoutes(props: {
  currentUser: CurrentUser;
  onChangePassword: () => void;
  onLogout: () => void;
  onToggleTheme: () => void;
  themeMode: 'dark' | 'light';
}) {
  return (
    <Routes>
      <Route
        element={
          <ConsoleLayout
            currentUser={props.currentUser}
            themeMode={props.themeMode}
            onChangePassword={props.onChangePassword}
            onLogout={props.onLogout}
            onToggleTheme={props.onToggleTheme}
          />
        }
      >
        <Route index element={<Navigate to="/overview" replace />} />
        <Route path="overview" element={<OverviewPageRoute />} />
        <Route path="document-input" element={<Navigate to="/document-input/import" replace />} />
        <Route path="document-input/*" element={lazyPage(<DocumentInputConsole />)} />
        <Route path="asset-library" element={<Navigate to="/asset-library/requirements" replace />} />
        <Route path="asset-library/*" element={lazyPage(<AssetWorkbench />)} />
        <Route path="test-design" element={<Navigate to="/test-design/tasks" replace />} />
        <Route path="test-design/*" element={lazyPage(<TestDesignWorkbench />)} />
        <Route path="api-automation" element={<Navigate to="/api-automation/cases" replace />} />
        <Route path="api-automation/*" element={lazyPage(<ApiAutomationWorkbench />)} />
        <Route path="ui-e2e" element={<Navigate to="/ui-e2e/cases" replace />} />
        <Route path="ui-e2e/*" element={lazyPage(<UiE2eWorkbench />)} />
        <Route path="execution" element={<Navigate to="/execution/plans" replace />} />
        <Route path="execution/*" element={lazyPage(<ExecutionWorkbench />)} />
        <Route path="test-data" element={<Navigate to="/test-data/accounts" replace />} />
        <Route path="test-data/*" element={lazyPage(<TestDataWorkbench />)} />
        <Route path="reports" element={<Navigate to="/reports/list" replace />} />
        <Route path="reports/*" element={lazyPage(<ReportsWorkbench />)} />
        <Route path="model-access" element={<Navigate to="/model-access/providers" replace />} />
        <Route path="model-access/*" element={lazyPage(<ModelAccessConsole />)} />
        <Route path="system" element={<Navigate to="/system/users" replace />} />
        <Route path="system/organizations" element={<SystemPageRoute page="organizations" />} />
        <Route path="system/users" element={<SystemPageRoute page="users" />} />
        <Route path="system/roles" element={<SystemPageRoute page="roles" />} />
        <Route path="system/projects" element={<SystemPageRoute page="projects" />} />
        <Route path="system/applications" element={<SystemPageRoute page="applications" />} />
        <Route path="system/environments" element={<SystemPageRoute page="environments" />} />
        <Route path="system/integrations" element={<SystemPageRoute page="integrations" />} />
        <Route path="system/secrets" element={<SystemPageRoute page="secrets" />} />
        <Route path="system/audit" element={<SystemPageRoute page="audit" />} />
        <Route path="system/settings" element={<SystemPageRoute page="settings" />} />
        {/* 旧路径兼容重定向 */}
        <Route path="organizations" element={<Navigate to="/system/organizations" replace />} />
        <Route path="users" element={<Navigate to="/system/users" replace />} />
        <Route path="roles" element={<Navigate to="/system/roles" replace />} />
        <Route path="projects" element={<Navigate to="/system/projects" replace />} />
        <Route path="applications" element={<Navigate to="/system/applications" replace />} />
        <Route path="environments" element={<Navigate to="/system/environments" replace />} />
        <Route path="integrations" element={<Navigate to="/system/integrations" replace />} />
        <Route path="audit" element={<Navigate to="/system/audit" replace />} />
        <Route path="settings" element={<Navigate to="/system/settings" replace />} />
        <Route path="*" element={<Navigate to="/overview" replace />} />
      </Route>
    </Routes>
  );
}
