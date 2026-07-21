import {
  ApiOutlined,
  AppstoreOutlined,
  AuditOutlined,
  BankOutlined,
  BarChartOutlined,
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
  SafetyCertificateOutlined,
  SettingOutlined,
  ThunderboltOutlined,
  UserOutlined
} from '@ant-design/icons';
import type { ReactNode } from 'react';
import { canAccessPage, type PageKey } from '../permissions';
import type { CurrentUser } from '../api/auth';
import { translate } from '../platform/i18n';

/**
 * 导航节点：支持分组（group）、页面（page）与带子菜单的页面。
 * path 为路由路径；children 存在时侧边栏渲染为可展开子菜单。
 */
export type NavNode = {
  key: string;
  label: string;
  icon?: ReactNode;
  path?: string;
  pageKey?: PageKey;
  children?: NavNode[];
};

export type NavGroup = {
  key: string;
  label: string;
  items: NavNode[];
};

const assetChildren: NavNode[] = [
  { key: 'asset-requirements', label: translate('nav.assetRequirements'), path: '/asset-library/requirements', pageKey: 'asset-library' },
  { key: 'asset-apis', label: translate('nav.assetApis'), path: '/asset-library/apis', pageKey: 'asset-library' },
  { key: 'asset-pages', label: translate('nav.assetPages'), path: '/asset-library/pages', pageKey: 'asset-library' },
  { key: 'asset-flows', label: translate('nav.assetFlows'), path: '/asset-library/flows', pageKey: 'asset-library' },
  { key: 'asset-cases', label: translate('nav.assetCases'), path: '/asset-library/cases', pageKey: 'asset-library' },
  { key: 'asset-trace', label: translate('nav.assetTrace'), path: '/asset-library/trace', pageKey: 'asset-library' }
];

const testDesignChildren: NavNode[] = [
  { key: 'td-tasks', label: translate('nav.tdTasks'), path: '/test-design/tasks', pageKey: 'test-design' },
  { key: 'td-candidates', label: translate('nav.tdCandidates'), path: '/test-design/candidates', pageKey: 'test-design' },
  { key: 'td-publish', label: translate('nav.tdPublish'), path: '/test-design/publish', pageKey: 'test-design' },
  { key: 'td-quality', label: translate('nav.tdQuality'), path: '/test-design/quality', pageKey: 'test-design' },
  { key: 'td-policies', label: translate('nav.tdPolicies'), path: '/test-design/policies', pageKey: 'test-design' },
  { key: 'td-operations', label: translate('nav.tdOperations'), path: '/test-design/operations', pageKey: 'test-design' }
];

const systemChildren: NavNode[] = [
  { key: 'organizations', label: translate('auto.k2827'), path: '/system/organizations', pageKey: 'organizations' },
  { key: 'users', label: translate('auto.k2828'), path: '/system/users', pageKey: 'users' },
  { key: 'roles', label: translate('auto.k2829'), path: '/system/roles', pageKey: 'roles' },
  { key: 'projects', label: translate('auto.k2830'), path: '/system/projects', pageKey: 'projects' },
  { key: 'applications', label: translate('auto.k0029'), path: '/system/applications', pageKey: 'applications' },
  { key: 'environments', label: translate('auto.k0031'), path: '/system/environments', pageKey: 'environments' },
  { key: 'integrations', label: translate('auto.k0033'), path: '/system/integrations', pageKey: 'integrations' },
  { key: 'secrets', label: '密钥管理', path: '/system/secrets', pageKey: 'secrets' },
  { key: 'audit', label: translate('auto.k0035'), path: '/system/audit', pageKey: 'audit' },
  { key: 'settings', label: translate('auto.k0037'), path: '/system/settings', pageKey: 'settings' }
];

export const navigationGroups: NavGroup[] = [
  {
    key: 'workbench',
    label: translate('nav.workbench'),
    items: [
      { key: 'overview', label: translate('auto.k0001'), icon: <HomeOutlined />, path: '/overview', pageKey: 'overview' }
    ]
  },
  {
    key: 'requirements-assets',
    label: translate('nav.requirementsAssets'),
    items: [
      { key: 'document-input', label: translate('auto.k0003'), icon: <FileTextOutlined />, path: '/document-input', pageKey: 'document-input' },
      { key: 'asset-library', label: translate('auto.k0005'), icon: <BookOutlined />, path: '/asset-library', pageKey: 'asset-library', children: assetChildren }
    ]
  },
  {
    key: 'test-design-group',
    label: translate('auto.k2817'),
    items: [
      { key: 'test-design', label: translate('auto.k2817'), icon: <ThunderboltOutlined />, path: '/test-design', pageKey: 'test-design', children: testDesignChildren }
    ]
  },
  {
    key: 'test-execution',
    label: translate('nav.testExecution'),
    items: [
      { key: 'api-automation', label: translate('auto.k0009'), icon: <ApiOutlined />, path: '/api-automation', pageKey: 'api-automation' },
      { key: 'ui-e2e', label: 'UI E2E', icon: <MonitorOutlined />, path: '/ui-e2e', pageKey: 'ui-e2e' },
      { key: 'execution', label: translate('auto.k0012'), icon: <BranchesOutlined />, path: '/execution', pageKey: 'execution' },
      { key: 'test-data', label: translate('auto.k0014'), icon: <DatabaseOutlined />, path: '/test-data', pageKey: 'test-data' },
      { key: 'reports', label: translate('auto.k2826'), icon: <BarChartOutlined />, path: '/reports', pageKey: 'reports' }
    ]
  },
  {
    key: 'platform',
    label: translate('nav.platform'),
    items: [
      { key: 'model-access', label: translate('auto.k0018'), icon: <CloudServerOutlined />, path: '/model-access', pageKey: 'model-access' },
      {
        key: 'system',
        label: translate('nav.systemManagement'),
        icon: <SettingOutlined />,
        path: '/system',
        children: systemChildren
      }
    ]
  }
];

/** 按权限过滤导航树 */
export function filterNavigationByPermission(user: CurrentUser | null): NavGroup[] {
  const allowed = (node: NavNode): NavNode | null => {
    if (node.pageKey && !canAccessPage(user, node.pageKey)) {
      return null;
    }
    if (node.children) {
      const children = node.children.map(allowed).filter((child): child is NavNode => Boolean(child));
      if (children.length === 0 && !node.pageKey) {
        return null;
      }
      if (children.length === 0 && node.pageKey && !canAccessPage(user, node.pageKey)) {
        return null;
      }
      return { ...node, children };
    }
    return node;
  };

  return navigationGroups
    .map((group) => ({
      ...group,
      items: group.items.map(allowed).filter((item): item is NavNode => Boolean(item))
    }))
    .filter((group) => group.items.length > 0);
}

/** 根据当前路径推导选中的菜单 key 与展开的子菜单 key */
export function resolveMenuState(pathname: string): { selectedKey: string; openKeys: string[] } {
  for (const group of navigationGroups) {
    for (const item of group.items) {
      if (item.children) {
        for (const child of item.children) {
          if (child.path && pathname.startsWith(child.path)) {
            return { selectedKey: child.key, openKeys: [item.key] };
          }
        }
      }
      if (item.path && (pathname === item.path || pathname.startsWith(`${item.path}/`))) {
        return { selectedKey: item.key, openKeys: item.children ? [item.key] : [] };
      }
    }
  }
  return { selectedKey: 'overview', openKeys: [] };
}

/** 根据路径推导面包屑（分组 / 页面 / 子页面） */
export function resolveBreadcrumbs(pathname: string): string[] {
  for (const group of navigationGroups) {
    for (const item of group.items) {
      if (item.children) {
        for (const child of item.children) {
          if (child.path && pathname.startsWith(child.path)) {
            return [group.label, item.label, child.label];
          }
        }
      }
      if (item.path && (pathname === item.path || pathname.startsWith(`${item.path}/`))) {
        return [group.label, item.label];
      }
    }
  }
  return [translate('nav.workbench'), translate('auto.k0001')];
}
