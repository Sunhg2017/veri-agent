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
  Alert,
  Avatar,
  Badge,
  Button,
  Card,
  Descriptions,
  Drawer,
  Dropdown,
  Empty,
  Flex,
  Input,
  Layout,
  List,
  Menu,
  Segmented,
  Space,
  Statistic,
  Table,
  Tabs,
  Tag,
  Typography,
  type MenuProps,
  type TableColumnsType
} from 'antd';
import { useEffect, useMemo, useState, type ReactNode } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useLocation, useNavigate } from 'react-router-dom';
import type { CurrentUser } from '../api/auth';
import { fetchHealth } from '../api/health';
import {
  fetchManagementData,
  type AuditLogView,
  type AuditOutboxView,
  type ManagementData
} from '../api/management';
import {
  fetchDocumentImports,
  fetchDocumentInputHealth,
  fetchDocumentSources,
  fetchWebhookEvents
} from '../api/documentInput';
import {
  fetchAssetApis,
  fetchAssetBusinessFlows,
  fetchAssetHealth,
  fetchAssetPages,
  fetchAssetRequirements,
  fetchAssetTestCases,
  fetchAssetTraceLinks
} from '../api/assets';
import {
  fetchTestDesignHealth,
  fetchTestDesignTasks,
  fetchTestDesignTemplates
} from '../api/testDesign';
import {
  fetchApiAutomationGenerationTasks,
  fetchApiAutomationHealth,
  fetchApiAutomationSpecs
} from '../api/apiAutomation';
import {
  fetchUiE2eBundles,
  fetchUiE2eFlakyMarks,
  fetchUiE2eHealth,
  fetchUiE2eRuns,
  fetchUiE2eScenes
} from '../api/uiE2e';
import {
  fetchExecutionHealth,
  fetchExecutionPlans,
  fetchExecutionRuns
} from '../api/execution';
import {
  fetchTestAccountLeases,
  fetchTestAccountPools,
  fetchTestDataHealth,
  fetchTestDataSets,
  fetchTestDataTasks
} from '../api/testData';
import {
  fetchReportingHealth,
  fetchReports
} from '../api/reports';
import {
  fetchCostAlerts,
  fetchCostReport,
  fetchInvocationSummary,
  fetchInvocations,
  fetchModelAccessHealth,
  fetchModelAccessPolicies,
  fetchModelProviders,
  fetchPrompts
} from '../api/modelAccess';
import {
  fetchNotifications,
  fetchUnreadNotificationCount,
  type UserNotification
} from '../api/notifications';
import { canAccessPage, type PageKey } from '../permissions';
import { dictionaryLabel, dictionaryListLabel } from '../platform/dictionaries';
import { translate } from '../platform/i18n';

const { Header, Sider, Content } = Layout;
const { Text, Title } = Typography;

type FieldConfig = {
  key: string;
  title: string;
  copyable?: boolean;
  ellipsis?: boolean;
  kind?: 'date' | 'dictionary' | 'dictionary-list' | 'money' | 'number' | 'percent' | 'status';
  width?: number;
};

type WorkspaceSection = {
  emptyText?: string;
  fields: FieldConfig[];
  key: string;
  rows: Array<Record<string, unknown>>;
  title: string;
};

type WorkspaceMetric = {
  label: string;
  tone?: 'danger' | 'success' | 'warning';
  value: ReactNode;
};

type WorkspaceData = {
  metrics: WorkspaceMetric[];
  sections: WorkspaceSection[];
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
  const location = useLocation();
  const navigate = useNavigate();
  const activePage = activePageFromPath(location.pathname);
  const [selectedRow, setSelectedRow] = useState<Record<string, unknown> | null>(null);
  const [search, setSearch] = useState('');
  const [sectionKey, setSectionKey] = useState<string>();

  const visiblePages = useMemo(
    () => pageDefinitions.filter((page) => canAccessPage(props.currentUser, page.key)),
    [props.currentUser]
  );
  const activeDefinition = visiblePages.find((page) => page.key === activePage) ?? visiblePages[0] ?? pageDefinitions[0];
  const managementQuery = useQuery({
    enabled: managementPageKeys.includes(activeDefinition.key) || activeDefinition.key === 'overview',
    queryFn: () => fetchManagementData(props.currentUser.permissions ?? []),
    queryKey: ['enterprise-management', props.currentUser.user_id, props.currentUser.permissions]
  });
  const workspaceQuery = useQuery({
    queryFn: () => loadWorkspaceData(activeDefinition.key, managementQuery.data?.data ?? emptyManagementData),
    queryKey: ['enterprise-workspace', activeDefinition.key, managementQuery.dataUpdatedAt],
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

  const workspace = workspaceQuery.data;
  const activeSectionKey = sectionKey && workspace?.sections.some((section) => section.key === sectionKey)
    ? sectionKey
    : workspace?.sections[0]?.key;
  const activeSection = workspace?.sections.find((section) => section.key === activeSectionKey);
  const filteredRows = useMemo(() => filterRows(activeSection?.rows ?? [], search), [activeSection?.rows, search]);
  const menuItems = useMemo(() => buildMenuItems(visiblePages), [visiblePages]);
  const selectedMenuKey = activeDefinition.key;

  useEffect(() => {
    setSearch('');
    setSelectedRow(null);
    setSectionKey(undefined);
  }, [activeDefinition.key]);

  useEffect(() => {
    if (location.pathname === '/' || location.pathname === '') {
      navigate('/overview', { replace: true });
    }
  }, [location.pathname, navigate]);

  return (
    <Layout className="va-console">
      <Sider className="va-console-sider" breakpoint="lg" collapsedWidth={72} width={280}>
        <div className="va-console-brand">
          <div className="va-console-brand-mark">VA</div>
          <div>
            <strong>Veri Agent</strong>
            <span>Enterprise Test Platform</span>
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
          <div className="va-console-page-title">
            <Text type="secondary">{activeDefinition.group}</Text>
            <Text className="va-console-current">{activeDefinition.label}</Text>
          </div>
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
            <Button icon={<ReloadOutlined />} loading={workspaceQuery.isFetching || managementQuery.isFetching} onClick={() => {
              void managementQuery.refetch();
              void workspaceQuery.refetch();
            }}>
              {translate('auto.k2833')}
            </Button>
          </section>

          {workspaceQuery.error ? (
            <Alert
              className="va-console-alert"
              message={translate('auto.k2834')}
              description={workspaceQuery.error instanceof Error ? workspaceQuery.error.message : translate('auto.k2835')}
              type="error"
              showIcon
            />
          ) : null}

          <div className="va-console-metrics">
            {(workspace?.metrics ?? skeletonMetrics).map((metric, index) => (
              <Card key={`${metric.label}-${index}`} loading={workspaceQuery.isLoading}>
                <Statistic
                  title={metric.label}
                  value={typeof metric.value === 'number' || typeof metric.value === 'string' ? metric.value : undefined}
                  valueStyle={metric.tone ? { color: metricColor(metric.tone) } : undefined}
                  formatter={typeof metric.value === 'number' || typeof metric.value === 'string' ? undefined : () => metric.value}
                />
              </Card>
            ))}
          </div>

          <Card className="va-console-workspace" loading={workspaceQuery.isLoading}>
            <Flex className="va-console-toolbar" gap={12} justify="space-between" wrap="wrap">
              <Segmented
                value={activeSectionKey}
                options={(workspace?.sections ?? []).map((section) => ({ label: section.title, value: section.key }))}
                onChange={(value) => setSectionKey(String(value))}
              />
              <Input.Search
                allowClear
                className="va-console-search"
                placeholder={translate('auto.k2836')}
                value={search}
                onChange={(event) => setSearch(event.target.value)}
              />
            </Flex>
            {activeSection ? (
              <Tabs
                activeKey={activeSection.key}
                items={[{
                  key: activeSection.key,
                  label: activeSection.title,
                  children: (
                    <>
                      <Table
                        columns={columnsFor(activeSection)}
                        dataSource={filteredRows}
                        locale={{ emptyText: <Empty description={activeSection.emptyText ?? translate('auto.k2837')} /> }}
                        pagination={{ showSizeChanger: true, showTotal: (total) => translate('auto.k2838', { value0: total }) }}
                        rowKey={(row, index) => rowKey(row, index)}
                        scroll={{ x: 'max-content' }}
                        size="middle"
                        onRow={(row) => ({
                          onClick: () => setSelectedRow(row)
                        })}
                      />
                    </>
                  )
                }]}
              />
            ) : (
              <Empty description={translate('auto.k2839')} />
            )}
          </Card>
        </Content>
      </Layout>
      <DetailDrawer row={selectedRow} onClose={() => setSelectedRow(null)} />
    </Layout>
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

function DetailDrawer(props: { row: Record<string, unknown> | null; onClose: () => void }) {
  const row = props.row;
  return (
    <Drawer destroyOnHidden open={Boolean(row)} size="large" title={translate('auto.k2842')} onClose={props.onClose}>
      {row ? (
        <Descriptions bordered column={1} size="small">
          {Object.entries(row).map(([key, value]) => (
            <Descriptions.Item key={key} label={fieldTitle(key)}>
              {detailValue(value)}
            </Descriptions.Item>
          ))}
        </Descriptions>
      ) : null}
    </Drawer>
  );
}

async function loadWorkspaceData(page: PageKey, managementData: ManagementData): Promise<WorkspaceData> {
  if (managementPageKeys.includes(page)) {
    return managementWorkspace(page, managementData);
  }
  switch (page) {
    case 'overview':
      return overviewWorkspace(managementData);
    case 'document-input':
      return documentInputWorkspace();
    case 'asset-library':
      return assetWorkspace();
    case 'test-design':
      return testDesignWorkspace();
    case 'api-automation':
      return apiAutomationWorkspace();
    case 'ui-e2e':
      return uiE2eWorkspace();
    case 'execution':
      return executionWorkspace();
    case 'test-data':
      return testDataWorkspace();
    case 'reports':
      return reportsWorkspace();
    case 'model-access':
      return modelAccessWorkspace();
    default:
      return overviewWorkspace(managementData);
  }
}

async function overviewWorkspace(managementData: ManagementData): Promise<WorkspaceData> {
  const health = await safe(() => fetchHealth());
  return {
    metrics: [
      { label: translate('auto.k2844'), tone: health?.data.status === 'UP' ? 'success' : 'danger', value: dictionaryLabel(health?.data.status ?? 'UNKNOWN') },
      { label: translate('auto.k2821'), value: managementData.users.length },
      { label: translate('auto.k2845'), value: managementData.projects.length },
      { label: translate('auto.k2846'), value: managementData.applications.length }
    ],
    sections: [
      section('projects', translate('auto.k2845'), managementData.projects, [
        field('name', translate('auto.k2845')),
        field('department', translate('auto.k2927')),
        field('owner', translate('auto.k2929')),
        field('apps', translate('auto.k2846'), 'number'),
        field('status', translate('auto.k2852'), 'status')
      ]),
      section('audit', translate('auto.k2962'), managementData.auditLogs, auditLogFields())
    ]
  };
}

async function documentInputWorkspace(): Promise<WorkspaceData> {
  const [health, sources, imports, webhooks] = await Promise.all([
    safe(() => fetchDocumentInputHealth()),
    safe(() => fetchDocumentSources()),
    safe(() => fetchDocumentImports()),
    safe(() => fetchWebhookEvents({ size: 50 }))
  ]);
  const sourceRows = sources?.data ?? [];
  const importRows = imports?.data.items ?? [];
  const webhookRows = webhooks?.data.items ?? [];
  return {
    metrics: [
      { label: translate('auto.k2847'), tone: health?.data.status === 'UP' ? 'success' : 'danger', value: dictionaryLabel(health?.data.status ?? 'UNKNOWN') },
      { label: translate('auto.k2848'), value: sourceRows.length },
      { label: translate('auto.k2849'), value: importRows.length },
      { label: 'Webhook', value: webhookRows.length }
    ],
    sections: [
      section('sources', translate('auto.k2848'), sourceRows, [
        field('name', translate('auto.k2850')),
        field('sourceType', translate('auto.k2851'), 'dictionary'),
        field('status', translate('auto.k2852'), 'status'),
        field('retentionDays', translate('auto.k2853'), 'number'),
        field('updatedAt', translate('auto.k2854'), 'date')
      ]),
      section('imports', translate('auto.k2849'), importRows, [
        field('id', translate('auto.k2855'), undefined, 240),
        field('sourceCode', translate('auto.k2856')),
        field('status', translate('auto.k2852'), 'status'),
        field('parsedCount', translate('auto.k2857'), 'number'),
        field('createdAt', translate('auto.k2858'), 'date')
      ]),
      section('webhooks', translate('auto.k2859'), webhookRows, [
        field('eventId', translate('auto.k2860'), undefined, 220),
        field('sourceCode', translate('auto.k2856')),
        field('status', translate('auto.k2852'), 'status'),
        field('retryCount', translate('auto.k2861'), 'number'),
        field('createdAt', translate('auto.k2858'), 'date')
      ])
    ]
  };
}

async function assetWorkspace(): Promise<WorkspaceData> {
  const [health, requirements, apis, pages, flows, cases, traces] = await Promise.all([
    safe(() => fetchAssetHealth()),
    safe(() => fetchAssetRequirements({ size: 50 })),
    safe(() => fetchAssetApis({ size: 50 })),
    safe(() => fetchAssetPages({ size: 50 })),
    safe(() => fetchAssetBusinessFlows({ size: 50 })),
    safe(() => fetchAssetTestCases({ size: 50 })),
    safe(() => fetchAssetTraceLinks({ size: 50 }))
  ]);
  return {
    metrics: [
      { label: translate('auto.k2847'), tone: statusTone(health?.data.status), value: dictionaryLabel(health?.data.status ?? 'UNKNOWN') },
      { label: translate('auto.k2862'), value: listRows(requirements?.data).length },
      { label: translate('auto.k2863'), value: listRows(apis?.data).length },
      { label: translate('auto.k2864'), value: listRows(cases?.data).length }
    ],
    sections: [
      section('requirements', translate('auto.k2862'), listRows(requirements?.data), commonAssetFields(translate('auto.k2862'))),
      section('apis', translate('auto.k2863'), listRows(apis?.data), commonAssetFields(translate('auto.k2863'))),
      section('pages', translate('auto.k2865'), listRows(pages?.data), commonAssetFields(translate('auto.k2865'))),
      section('flows', translate('auto.k2866'), listRows(flows?.data), commonAssetFields(translate('auto.k2866'))),
      section('cases', translate('auto.k2867'), listRows(cases?.data), commonAssetFields(translate('auto.k2864'))),
      section('traces', translate('auto.k2868'), listRows(traces?.data), [
        field('sourceType', translate('auto.k2869'), 'dictionary'),
        field('sourceId', translate('auto.k2870')),
        field('targetType', translate('auto.k2871'), 'dictionary'),
        field('targetId', translate('auto.k2872')),
        field('status', translate('auto.k2852'), 'status')
      ])
    ]
  };
}

async function testDesignWorkspace(): Promise<WorkspaceData> {
  const [health, tasks, templates] = await Promise.all([
    safe(() => fetchTestDesignHealth()),
    safe(() => fetchTestDesignTasks({ size: 50 })),
    safe(() => fetchTestDesignTemplates({ size: 50 }))
  ]);
  const taskRows = listRows(tasks?.data);
  const templateRows = listRows(templates?.data);
  return {
    metrics: [
      { label: translate('auto.k2847'), tone: statusTone(health?.data.status), value: dictionaryLabel(health?.data.status ?? 'UNKNOWN') },
      { label: translate('auto.k2873'), value: taskRows.length },
      { label: translate('auto.k2874'), value: templateRows.length },
      { label: translate('auto.k2875'), value: taskRows.filter((row) => String(row.status) === 'RUNNING').length }
    ],
    sections: [
      section('tasks', translate('auto.k2873'), taskRows, [
        field('id', translate('auto.k2876'), undefined, 220),
        field('name', translate('auto.k2850')),
        field('status', translate('auto.k2852'), 'status'),
        field('createdBy', translate('auto.k2877')),
        field('createdAt', translate('auto.k2858'), 'date')
      ]),
      section('templates', translate('auto.k2874'), templateRows, [
        field('id', translate('auto.k2878'), undefined, 220),
        field('name', translate('auto.k2850')),
        field('generationStrategy', translate('auto.k2879'), 'dictionary'),
        field('coverageStrategy', translate('auto.k2880'), 'dictionary'),
        field('status', translate('auto.k2852'), 'status')
      ])
    ]
  };
}

async function apiAutomationWorkspace(): Promise<WorkspaceData> {
  const [health, specs, tasks] = await Promise.all([
    safe(() => fetchApiAutomationHealth()),
    safe(() => fetchApiAutomationSpecs({ size: 50 })),
    safe(() => fetchApiAutomationGenerationTasks({ size: 50 }))
  ]);
  const specRows = listRows(specs?.data);
  const taskRows = listRows(tasks?.data);
  return {
    metrics: [
      { label: translate('auto.k2847'), tone: statusTone(health?.data.status), value: dictionaryLabel(health?.data.status ?? 'UNKNOWN') },
      { label: translate('auto.k2881'), value: specRows.length },
      { label: translate('auto.k2873'), value: taskRows.length },
      { label: translate('auto.k2882'), tone: 'danger', value: taskRows.filter((row) => String(row.status).includes('FAILED')).length }
    ],
    sections: [
      section('specs', translate('auto.k2883'), specRows, [
        field('id', translate('auto.k2884'), undefined, 220),
        field('name', translate('auto.k2850')),
        field('sourceType', translate('auto.k2856'), 'dictionary'),
        field('status', translate('auto.k2852'), 'status'),
        field('updatedAt', translate('auto.k2854'), 'date')
      ]),
      section('tasks', translate('auto.k2873'), taskRows, [
        field('id', translate('auto.k2876'), undefined, 220),
        field('specId', translate('auto.k2884')),
        field('status', translate('auto.k2852'), 'status'),
        field('createdAt', translate('auto.k2858'), 'date')
      ])
    ]
  };
}

async function uiE2eWorkspace(): Promise<WorkspaceData> {
  const [health, scenes, bundles, runs, flaky] = await Promise.all([
    safe(() => fetchUiE2eHealth()),
    safe(() => fetchUiE2eScenes({ size: 50 })),
    safe(() => fetchUiE2eBundles({ size: 50 })),
    safe(() => fetchUiE2eRuns({ size: 50 })),
    safe(() => fetchUiE2eFlakyMarks({ size: 50 }))
  ]);
  return {
    metrics: [
      { label: translate('auto.k2847'), tone: statusTone(health?.data.status), value: dictionaryLabel(health?.data.status ?? 'UNKNOWN') },
      { label: translate('auto.k2885'), value: listRows(scenes?.data).length },
      { label: translate('auto.k2886'), value: listRows(bundles?.data).length },
      { label: translate('auto.k2887'), value: listRows(runs?.data).length }
    ],
    sections: [
      section('scenes', translate('auto.k2885'), listRows(scenes?.data), uiFields(translate('auto.k2885'))),
      section('bundles', translate('auto.k2886'), listRows(bundles?.data), uiFields(translate('auto.k2886'))),
      section('runs', translate('auto.k2887'), listRows(runs?.data), [
        field('id', translate('auto.k2889'), undefined, 220),
        field('status', translate('auto.k2852'), 'status'),
        field('passedSteps', translate('auto.k2890'), 'number'),
        field('failedSteps', translate('auto.k2891'), 'number'),
        field('createdAt', translate('auto.k2858'), 'date')
      ]),
      section('flaky', translate('auto.k2888'), listRows(flaky?.data), [
        field('id', translate('auto.k2892'), undefined, 220),
        field('status', translate('auto.k2852'), 'status'),
        field('reason', translate('auto.k2893')),
        field('updatedAt', translate('auto.k2854'), 'date')
      ])
    ]
  };
}

async function executionWorkspace(): Promise<WorkspaceData> {
  const [health, plans, runs] = await Promise.all([
    safe(() => fetchExecutionHealth()),
    safe(() => fetchExecutionPlans({ size: 50 })),
    safe(() => fetchExecutionRuns({ size: 50 }))
  ]);
  const planRows = listRows(plans?.data);
  const runRows = listRows(runs?.data);
  return {
    metrics: [
      { label: translate('auto.k2847'), tone: statusTone(health?.data.status), value: dictionaryLabel(health?.data.status ?? 'UNKNOWN') },
      { label: translate('auto.k2894'), value: planRows.length },
      { label: translate('auto.k2887'), value: runRows.length },
      { label: translate('auto.k2895'), tone: 'danger', value: runRows.filter((row) => String(row.status).includes('FAILED')).length }
    ],
    sections: [
      section('plans', translate('auto.k2896'), planRows, executionFields(translate('auto.k2894'))),
      section('runs', translate('auto.k2897'), runRows, executionFields(translate('auto.k2887')))
    ]
  };
}

async function testDataWorkspace(): Promise<WorkspaceData> {
  const [health, dataSets, pools, leases, tasks] = await Promise.all([
    safe(() => fetchTestDataHealth()),
    safe(() => fetchTestDataSets({ size: 50 })),
    safe(() => fetchTestAccountPools({ size: 50 })),
    safe(() => fetchTestAccountLeases({ size: 50 })),
    safe(() => fetchTestDataTasks({ size: 50 }))
  ]);
  return {
    metrics: [
      { label: translate('auto.k2847'), tone: statusTone(health?.data.status), value: dictionaryLabel(health?.data.status ?? 'UNKNOWN') },
      { label: translate('auto.k2898'), value: listRows(dataSets?.data).length },
      { label: translate('auto.k2899'), value: listRows(pools?.data).length },
      { label: translate('auto.k2900'), value: listRows(leases?.data).length }
    ],
    sections: [
      section('datasets', translate('auto.k2898'), listRows(dataSets?.data), dataFields(translate('auto.k2898'))),
      section('pools', translate('auto.k2899'), listRows(pools?.data), dataFields(translate('auto.k2899'))),
      section('leases', translate('auto.k2900'), listRows(leases?.data), dataFields(translate('auto.k2900'))),
      section('tasks', translate('auto.k2901'), listRows(tasks?.data), dataFields(translate('auto.k2901')))
    ]
  };
}

async function reportsWorkspace(): Promise<WorkspaceData> {
  const [health, reports] = await Promise.all([
    safe(() => fetchReportingHealth()),
    safe(() => fetchReports({ size: 50 }))
  ]);
  const reportRows = listRows(reports?.data);
  return {
    metrics: [
      { label: translate('auto.k2847'), tone: statusTone(health?.data.status), value: dictionaryLabel(health?.data.status ?? 'UNKNOWN') },
      { label: translate('auto.k2902'), value: reportRows.length },
      { label: translate('auto.k2790'), value: reportRows.filter((row) => String(row.status) === 'GENERATED').length },
      { label: translate('auto.k2903'), tone: 'danger', value: reportRows.filter((row) => String(row.status).includes('FAILED')).length }
    ],
    sections: [
      section('reports', translate('auto.k2902'), reportRows, [
        field('id', translate('auto.k2904'), undefined, 220),
        field('projectId', translate('auto.k2845')),
        field('executionRunId', translate('auto.k2889')),
        field('status', translate('auto.k2852'), 'status'),
        field('generatedAt', translate('auto.k2905'), 'date')
      ])
    ]
  };
}

async function modelAccessWorkspace(): Promise<WorkspaceData> {
  const [health, providers, prompts, policies, invocations, summary, costReport, alerts] = await Promise.all([
    safe(() => fetchModelAccessHealth()),
    safe(() => fetchModelProviders()),
    safe(() => fetchPrompts()),
    safe(() => fetchModelAccessPolicies()),
    safe(() => fetchInvocations({ size: 50 })),
    safe(() => fetchInvocationSummary({})),
    safe(() => fetchCostReport({})),
    safe(() => fetchCostAlerts({}))
  ]);
  const providerRows = providers?.data ?? [];
  const promptRows = prompts?.data ?? [];
  const invocationRows = invocations?.data.items ?? [];
  return {
    metrics: [
      { label: translate('auto.k2847'), tone: statusTone(health?.data.status), value: dictionaryLabel(health?.data.status ?? 'UNKNOWN') },
      { label: translate('auto.k2906'), value: providerRows.length },
      { label: translate('auto.k2907'), value: promptRows.length },
      { label: translate('auto.k2908'), value: summary?.data.total ?? invocationRows.length }
    ],
    sections: [
      section('providers', translate('auto.k2906'), providerRows, [
        field('name', translate('auto.k2906')),
        field('providerType', translate('auto.k2851'), 'dictionary'),
        field('capabilities', translate('auto.k2909'), 'dictionary-list'),
        field('status', translate('auto.k2852'), 'status'),
        field('priority', translate('auto.k2910'), 'number')
      ]),
      section('prompts', translate('auto.k2907'), promptRows, [
        field('promptKey', 'Key'),
        field('name', translate('auto.k2850')),
        field('version', translate('auto.k2911'), 'number'),
        field('status', translate('auto.k2852'), 'status'),
        field('approvalStatus', translate('auto.k2912'), 'status')
      ]),
      section('policies', translate('auto.k2913'), policies?.data ?? [], [
        field('scopeType', translate('auto.k2914'), 'dictionary'),
        field('scopeKey', 'Key'),
        field('enabled', translate('auto.k2915')),
        field('budgetOverrunAction', translate('auto.k2916'), 'dictionary'),
        field('updatedAt', translate('auto.k2854'), 'date')
      ]),
      section('invocations', translate('auto.k2917'), invocationRows, [
        field('id', translate('auto.k2918'), undefined, 220),
        field('providerName', translate('auto.k2906')),
        field('status', translate('auto.k2852'), 'status'),
        field('modelCapability', translate('auto.k2909'), 'dictionary'),
        field('totalCost', translate('auto.k2919'), 'money'),
        field('createdAt', translate('auto.k2858'), 'date')
      ]),
      section('cost', translate('auto.k2920'), costReport?.data.rows ?? [], [
        field('date', translate('auto.k2921')),
        field('projectId', translate('auto.k2845')),
        field('total', translate('auto.k2908'), 'number'),
        field('totalCost', translate('auto.k2919'), 'money')
      ]),
      section('alerts', translate('auto.k2922'), alerts?.data ?? [], [
        field('level', translate('auto.k2923'), 'status'),
        field('projectId', translate('auto.k2845')),
        field('actorService', translate('auto.k2924')),
        field('usageRatio', translate('auto.k2925'), 'percent'),
        field('message', translate('auto.k2926'))
      ])
    ]
  };
}

function managementWorkspace(page: PageKey, data: ManagementData): WorkspaceData {
  const configs: Record<string, WorkspaceSection[]> = {
    organizations: [
      section('departments', translate('auto.k2927'), data.departments, [
        field('name', translate('auto.k2927')),
        field('parent', translate('auto.k2928')),
        field('lead', translate('auto.k2929')),
        field('members', translate('auto.k2930'), 'number'),
        field('status', translate('auto.k2852'), 'status')
      ])
    ],
    users: [
      section('users', translate('auto.k2821'), data.users, [
        field('username', translate('auto.k2931')),
        field('display_name', translate('auto.k2932')),
        field('email', translate('auto.k2933')),
        field('role', translate('auto.k2934')),
        field('status', translate('auto.k2852'), 'status'),
        field('last_seen', translate('auto.k2935'), 'date')
      ])
    ],
    roles: [
      section('roles', translate('auto.k2934'), data.roles, [
        field('code', translate('auto.k2936')),
        field('name', translate('auto.k2850')),
        field('scopeType', translate('auto.k2914'), 'dictionary'),
        field('status', translate('auto.k2852'), 'status')
      ]),
      section('permissions', translate('auto.k2937'), data.permissions, [
        field('code', translate('auto.k2938')),
        field('resourceType', translate('auto.k2939'), 'dictionary'),
        field('action', translate('auto.k2940')),
        field('status', translate('auto.k2852'), 'status')
      ])
    ],
    projects: [
      section('projects', translate('auto.k2845'), data.projects, [
        field('name', translate('auto.k2845')),
        field('department', translate('auto.k2927')),
        field('owner', translate('auto.k2929')),
        field('apps', translate('auto.k2846'), 'number'),
        field('status', translate('auto.k2852'), 'status')
      ])
    ],
    applications: [
      section('applications', translate('auto.k2846'), data.applications, [
        field('name', translate('auto.k2846')),
        field('type', translate('auto.k2851'), 'dictionary'),
        field('owner', translate('auto.k2929')),
        field('version', translate('auto.k2911')),
        field('status', translate('auto.k2852'), 'status')
      ])
    ],
    environments: [
      section('environments', translate('auto.k2941'), data.environments, [
        field('name', translate('auto.k2941')),
        field('cluster', translate('auto.k2942')),
        field('endpoint', translate('auto.k2943')),
        field('status', translate('auto.k2852'), 'status')
      ])
    ],
    integrations: [
      section('integrations', translate('auto.k2944'), data.integrations, [
        field('key', 'Key'),
        field('name', translate('auto.k2850')),
        field('category', translate('auto.k2945'), 'dictionary'),
        field('scope', translate('auto.k2914'), 'dictionary'),
        field('status', translate('auto.k2852'), 'status')
      ])
    ],
    audit: [
      section('auditLogs', translate('auto.k0035'), data.auditLogs as AuditLogView[], auditLogFields()),
      section('auditOutbox', translate('auto.k2946'), data.auditOutbox as AuditOutboxView[], [
        field('id', 'ID', undefined, 220),
        field('traceId', 'Trace ID', undefined, 220),
        field('status', translate('auto.k2852'), 'status'),
        field('retryCount', translate('auto.k2861'), 'number'),
        field('eventAction', translate('auto.k2940')),
        field('createdAt', translate('auto.k2858'), 'date')
      ])
    ],
    settings: [
      section('settings', translate('auto.k0037'), data.settings, [
        field('key', 'Key'),
        field('name', translate('auto.k2850')),
        field('scope', translate('auto.k2914'), 'dictionary'),
        field('status', translate('auto.k2852'), 'status')
      ]),
      section('secrets', translate('auto.k2948'), data.secrets, [
        field('secretRef', 'SecretRef'),
        field('providerCode', 'Provider'),
        field('purpose', translate('auto.k2949'), 'dictionary'),
        field('scopeType', translate('auto.k2914'), 'dictionary'),
        field('status', translate('auto.k2852'), 'status')
      ])
    ]
  };
  const sections = configs[page] ?? configs.projects;
  return {
    metrics: [
      { label: translate('auto.k2950'), value: sections.length },
      { label: translate('auto.k2951'), value: sections.reduce((sum, item) => sum + item.rows.length, 0) },
      { label: translate('auto.k2952'), tone: 'danger', value: sections.flatMap((sectionItem) => sectionItem.rows).filter((row) => String(row.status).includes('FAILED')).length },
      { label: translate('auto.k2915'), tone: 'success', value: sections.flatMap((sectionItem) => sectionItem.rows).filter((row) => ['ACTIVE', 'ENABLED', 'UP'].includes(String(row.status))).length }
    ],
    sections
  };
}

function section(key: string, title: string, rows: unknown, fields: FieldConfig[]): WorkspaceSection {
  return {
    fields,
    key,
    rows: listRows(rows),
    title
  };
}

function field(key: string, title: string, kind?: FieldConfig['kind'], width?: number): FieldConfig {
  return { key, kind, title, width, ellipsis: true };
}

function columnsFor(sectionConfig: WorkspaceSection): TableColumnsType<Record<string, unknown>> {
  return sectionConfig.fields.map((fieldConfig) => ({
    dataIndex: fieldConfig.key,
    ellipsis: fieldConfig.ellipsis ?? true,
    key: fieldConfig.key,
    render: (value: unknown) => cellValue(value, fieldConfig),
    title: fieldConfig.title,
    width: fieldConfig.width
  }));
}

function cellValue(value: unknown, fieldConfig: FieldConfig) {
  if (value === undefined || value === null || value === '') {
    return <Text type="secondary">-</Text>;
  }
  if (fieldConfig.kind === 'status') {
    return <Tag color={tagColor(String(value))}>{dictionaryLabel(value)}</Tag>;
  }
  if (fieldConfig.kind === 'dictionary') {
    return dictionaryLabel(value);
  }
  if (fieldConfig.kind === 'dictionary-list') {
    return dictionaryListLabel(value);
  }
  if (fieldConfig.kind === 'date') {
    return formatDate(value);
  }
  if (fieldConfig.kind === 'money') {
    return Number(value || 0).toLocaleString('zh-CN', { maximumFractionDigits: 4, minimumFractionDigits: 4 });
  }
  if (fieldConfig.kind === 'percent') {
    return `${(Number(value || 0) * 100).toFixed(1)}%`;
  }
  if (typeof value === 'boolean') {
    return value ? translate('auto.k2953') : translate('auto.k2954');
  }
  if (typeof value === 'object') {
    return <Text code>{JSON.stringify(value).slice(0, 120)}</Text>;
  }
  return String(value);
}

function detailValue(value: unknown) {
  if (value === undefined || value === null || value === '') {
    return '-';
  }
  if (typeof value === 'object') {
    return <pre className="va-detail-json">{JSON.stringify(value, null, 2)}</pre>;
  }
  return String(value);
}

function filterRows(rows: Array<Record<string, unknown>>, keyword: string) {
  const normalized = keyword.trim().toLowerCase();
  if (!normalized) {
    return rows;
  }
  return rows.filter((row) => JSON.stringify(row).toLowerCase().includes(normalized));
}

function listRows(input: unknown): Array<Record<string, unknown>> {
  if (!input) {
    return [];
  }
  if (Array.isArray(input)) {
    return input.map(recordRow);
  }
  if (typeof input === 'object') {
    const value = input as Record<string, unknown>;
    if (Array.isArray(value.items)) {
      return value.items.map(recordRow);
    }
    if (Array.isArray(value.rows)) {
      return value.rows.map(recordRow);
    }
  }
  return [];
}

function recordRow(input: unknown): Record<string, unknown> {
  return input && typeof input === 'object' && !Array.isArray(input) ? input as Record<string, unknown> : { value: input };
}

function rowKey(row: Record<string, unknown>, index?: number) {
  return String(row.id ?? row.key ?? row.code ?? row.name ?? row.username ?? row.eventId ?? row.traceId ?? index ?? Math.random());
}

async function safe<T>(loader: () => Promise<T>): Promise<T | undefined> {
  try {
    return await loader();
  } catch {
    return undefined;
  }
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

function tagColor(value: string) {
  const normalized = value.toUpperCase();
  if (['UP', 'OK', 'ACTIVE', 'ENABLED', 'SUCCEEDED', 'SUCCESS', 'PASSED', 'APPROVED', 'READY'].includes(normalized)) return 'success';
  if (['FAILED', 'ERROR', 'DOWN', 'DISABLED', 'REJECTED', 'CRITICAL'].includes(normalized)) return 'error';
  if (['RUNNING', 'PROCESSING', 'PENDING', 'QUEUED', 'DRAFT', 'REVIEWING'].includes(normalized)) return 'processing';
  if (['WARN', 'WARNING', 'BLOCKED', 'OPEN'].includes(normalized)) return 'warning';
  return 'default';
}

function statusTone(value?: string) {
  if (['UP', 'OK', 'ACTIVE', 'ENABLED', 'SUCCEEDED'].includes(String(value))) return 'success';
  if (['DOWN', 'FAILED', 'ERROR', 'CRITICAL'].includes(String(value))) return 'danger';
  return undefined;
}

function metricColor(tone: NonNullable<WorkspaceMetric['tone']>) {
  if (tone === 'success') return '#16a34a';
  if (tone === 'danger') return '#dc2626';
  return '#d97706';
}

function formatDate(value: unknown) {
  const date = new Date(String(value));
  if (Number.isNaN(date.getTime())) {
    return String(value);
  }
  return date.toLocaleString('zh-CN', {
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    month: '2-digit',
    year: 'numeric'
  });
}

function fieldTitle(key: string) {
  return key
    .replace(/([a-z])([A-Z])/g, '$1 $2')
    .replace(/_/g, ' ')
    .replace(/\b\w/g, (letter) => letter.toUpperCase());
}

function auditLogFields(): FieldConfig[] {
  return [
    field('time', translate('auto.k2955'), 'date'),
    field('actor', translate('auto.k2956')),
    field('action', translate('auto.k2940')),
    field('target', translate('auto.k2957')),
    field('result', translate('auto.k2958'), 'status')
  ];
}

function commonAssetFields(name: string): FieldConfig[] {
  return [
    field('id', `${name} ID`, undefined, 220),
    field('title', translate('auto.k2959')),
    field('name', translate('auto.k2850')),
    field('status', translate('auto.k2852'), 'status'),
    field('priority', translate('auto.k2910'), 'dictionary'),
    field('updatedAt', translate('auto.k2854'), 'date')
  ];
}

function uiFields(name: string): FieldConfig[] {
  return [
    field('id', `${name} ID`, undefined, 220),
    field('name', translate('auto.k2850')),
    field('status', translate('auto.k2852'), 'status'),
    field('riskLevel', translate('auto.k2960'), 'dictionary'),
    field('updatedAt', translate('auto.k2854'), 'date')
  ];
}

function executionFields(name: string): FieldConfig[] {
  return [
    field('id', `${name} ID`, undefined, 220),
    field('name', translate('auto.k2850')),
    field('status', translate('auto.k2852'), 'status'),
    field('createdBy', translate('auto.k2877')),
    field('createdAt', translate('auto.k2858'), 'date')
  ];
}

function dataFields(name: string): FieldConfig[] {
  return [
    field('id', `${name} ID`, undefined, 220),
    field('name', translate('auto.k2850')),
    field('status', translate('auto.k2852'), 'status'),
    field('sourceType', translate('auto.k2856'), 'dictionary'),
    field('updatedAt', translate('auto.k2854'), 'date')
  ];
}

function avatarText(user: CurrentUser) {
  return (user.display_name || user.username || 'U').slice(0, 2).toUpperCase();
}

const skeletonMetrics: WorkspaceMetric[] = [
  { label: translate('auto.k2961'), value: '-' },
  { label: translate('auto.k2961'), value: '-' },
  { label: translate('auto.k2961'), value: '-' },
  { label: translate('auto.k2961'), value: '-' }
];
