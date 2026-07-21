import { useMemo, useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { KeyRound } from 'lucide-react';
import {
  App as AntApp,
  AutoComplete,
  Button,
  DatePicker,
  Form,
  Input,
  Modal,
  Popconfirm,
  Select,
  Table,
  Tag
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import type { Dayjs } from 'dayjs';
import {
  createSecretReference,
  disableSecretReference,
  listSecrets,
  rotateSecretReference,
  type SecretReferenceView
} from '../../api/management';
import { fetchModelProviders } from '../../api/modelAccess';
import { canUseButton } from '../../permissions';
import { StatusBadge, type ManagementPageProps } from '../../components/management/shared';

/**
 * 密钥管理页：维护 secret:// 引用的创建、轮换与禁用。
 * 文案硬编码中文（与导航 'UI E2E' 先例一致），不占用 i18n 生成文件。
 */
export function SecretsPage(props: ManagementPageProps) {
  const { currentUser, signedIn } = props;
  const { message } = AntApp.useApp();
  const queryClient = useQueryClient();
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(20);
  const [search, setSearch] = useState('');
  const [searchInput, setSearchInput] = useState('');
  const [createOpen, setCreateOpen] = useState(false);
  const [rotateTarget, setRotateTarget] = useState<SecretReferenceView | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const canCreate = canUseButton(currentUser, 'secret:create');
  const canRotate = canUseButton(currentUser, 'secret:rotate');
  const canDisable = canUseButton(currentUser, 'secret:disable');

  const secretsQuery = useQuery({
    queryKey: ['management-secrets', page, pageSize, search],
    queryFn: async () => {
      const response = await listSecrets({ index: page, size: pageSize, search });
      return response.data;
    },
    enabled: signedIn,
    placeholderData: (previous) => previous
  });

  async function refresh() {
    await queryClient.invalidateQueries({ queryKey: ['management-secrets'] });
    props.onRefresh();
  }

  async function onDisable(record: SecretReferenceView) {
    setSubmitting(true);
    try {
      await disableSecretReference(record.secretRef);
      void message.success('密钥已禁用');
      await refresh();
    } catch (error: unknown) {
      void message.error(error instanceof Error ? error.message : '操作失败');
    } finally {
      setSubmitting(false);
    }
  }

  const columns: ColumnsType<SecretReferenceView> = useMemo(() => [
    {
      title: '密钥引用',
      dataIndex: 'secretRef',
      key: 'secretRef',
      render: (value: string) => <span className="mono">{value}</span>
    },
    {
      title: '用途',
      dataIndex: 'purpose',
      key: 'purpose',
      width: 170,
      render: (value: string) => <Tag color="blue">{value}</Tag>
    },
    {
      title: '供应商类型',
      dataIndex: 'providerType',
      key: 'providerType',
      width: 150,
      render: (value: string, record) => value || record.providerCode || '-'
    },
    {
      title: '作用域',
      key: 'scope',
      width: 190,
      render: (_, record) => (
        <span>
          <Tag>{record.scopeType}</Tag>
          <span className="mono text-tertiary text-xs">{shortScopeId(record.scopeId)}</span>
        </span>
      )
    },
    {
      title: '脱敏值',
      dataIndex: 'maskedValue',
      key: 'maskedValue',
      width: 120,
      render: (value: string) => <span className="mono">{value || '********'}</span>
    },
    {
      title: '版本',
      dataIndex: 'secretVersion',
      key: 'secretVersion',
      width: 80
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (value: string) => <StatusBadge status={value} />
    },
    {
      title: '过期时间',
      dataIndex: 'expiresAt',
      key: 'expiresAt',
      width: 170,
      render: (value: string) => formatInstant(value)
    },
    {
      title: '操作',
      key: 'actions',
      width: 150,
      render: (_, record) => {
        const active = record.status === 'ACTIVE';
        return (
          <div className="toolbar-actions">
            {canRotate && (
              <Button
                size="small"
                type="link"
                disabled={!active || submitting}
                title={active ? undefined : '仅 ACTIVE 密钥可轮换'}
                onClick={() => setRotateTarget(record)}
              >
                轮换
              </Button>
            )}
            {canDisable && (
              <Popconfirm
                title="禁用密钥"
                description="确认禁用该密钥？禁用后引用它的调用将立即失败。"
                okText="确认"
                cancelText="取消"
                onConfirm={() => void onDisable(record)}
              >
                <Button size="small" type="link" danger disabled={!active || submitting}>
                  禁用
                </Button>
              </Popconfirm>
            )}
          </div>
        );
      }
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  ], [canRotate, canDisable, submitting]);

  const items = secretsQuery.data?.items ?? [];
  const total = secretsQuery.data?.total ?? 0;

  return (
    <div className="content-grid">
      <div className="panel">
        <div className="panel-header">
          <div>
            <div className="management-section-heading">
              <div className="section-icon management-section-icon"><KeyRound size={17} /></div>
              <div>
                <div className="text-tertiary text-xs font-semibold management-eyebrow">SECRET REFERENCES</div>
                <h2 className="panel-title">密钥管理</h2>
              </div>
            </div>
            <p className="text-tertiary text-sm" style={{ margin: '4px 0 0' }}>
              维护 secret:// 密钥引用，模型接入、Webhook 签名等场景按引用安全取用，明文永不回显。
            </p>
          </div>
          <div className="toolbar-actions">
            {canCreate && (
              <Button type="primary" onClick={() => setCreateOpen(true)} disabled={!signedIn}>
                新建密钥
              </Button>
            )}
            <Button onClick={() => void refresh()} disabled={secretsQuery.isFetching}>
              刷新
            </Button>
          </div>
        </div>
        <div className="panel-body">
          <div style={{ marginBottom: 12, maxWidth: 360 }}>
            <Input.Search
              allowClear
              placeholder="搜索密钥引用"
              value={searchInput}
              onChange={(event) => setSearchInput(event.target.value)}
              onSearch={(value) => {
                setPage(0);
                setSearch(value.trim());
              }}
            />
          </div>
          {secretsQuery.isError && (
            <div className="notice error management-notice">
              {secretsQuery.error instanceof Error ? secretsQuery.error.message : '加载失败'}
            </div>
          )}
          <Table<SecretReferenceView>
            rowKey="id"
            size="middle"
            columns={columns}
            dataSource={items}
            loading={secretsQuery.isLoading}
            locale={{ emptyText: '暂无密钥，点击右上角「新建密钥」创建第一个 secret:// 引用。' }}
            pagination={{
              current: page + 1,
              pageSize,
              total,
              showSizeChanger: true,
              showTotal: (count) => `共 ${count} 条`,
              onChange: (nextPage, nextPageSize) => {
                setPage(nextPage - 1);
                setPageSize(nextPageSize);
              }
            }}
          />
        </div>
      </div>

      <CreateSecretModal
        open={createOpen}
        submitting={submitting}
        onCancel={() => setCreateOpen(false)}
        onSubmit={async (values) => {
          setSubmitting(true);
          try {
            await createSecretReference(values);
            void message.success('密钥创建成功');
            setCreateOpen(false);
            await refresh();
          } catch (error: unknown) {
            void message.error(error instanceof Error ? error.message : '操作失败');
            throw error;
          } finally {
            setSubmitting(false);
          }
        }}
      />

      <RotateSecretModal
        target={rotateTarget}
        submitting={submitting}
        onCancel={() => setRotateTarget(null)}
        onSubmit={async (values) => {
          setSubmitting(true);
          try {
            await rotateSecretReference(values);
            void message.success('密钥轮换成功');
            setRotateTarget(null);
            await refresh();
          } catch (error: unknown) {
            void message.error(error instanceof Error ? error.message : '操作失败');
            throw error;
          } finally {
            setSubmitting(false);
          }
        }}
      />
    </div>
  );
}

/* ===================== Create Modal ===================== */

type CreateSecretFormValues = {
  secretRef: string;
  purpose: string;
  scopeType: string;
  scopeId: string;
  value: string;
  secretVersion?: string;
  expiresAt?: Dayjs;
};

const PURPOSE_OPTIONS = ['MODEL_API_KEY', 'WEBHOOK_SIGNING', 'RUNNER_SECRET'].map((value) => ({ value }));
const SCOPE_TYPE_OPTIONS = ['CONFIG', 'PROJECT', 'APPLICATION', 'ENVIRONMENT'].map((value) => ({ value }));

function CreateSecretModal(props: {
  open: boolean;
  submitting: boolean;
  onCancel: () => void;
  onSubmit: (values: {
    secret_ref: string;
    purpose: string;
    scope_type: string;
    scope_id: string;
    secret_value: string;
    secret_version?: string;
    expires_at?: string;
  }) => Promise<void>;
}) {
  const [form] = Form.useForm<CreateSecretFormValues>();
  const scopeType = Form.useWatch('scopeType', form);

  // CONFIG 作用域绑定模型供应商：有 modelAccess:read 权限时拉取下拉列表，失败则回退手动输入 UUID
  const providersQuery = useQuery({
    queryKey: ['model-access-providers-for-secret-scope'],
    queryFn: async () => {
      const response = await fetchModelProviders();
      return response.data;
    },
    enabled: props.open && scopeType === 'CONFIG',
    retry: 0,
    staleTime: 60_000
  });
  const providerOptions = (providersQuery.data ?? []).map((provider) => ({
    value: provider.id,
    label: `${provider.name}（${provider.id.slice(0, 8)}…）`
  }));
  const providerFallbackManual = scopeType === 'CONFIG' && providersQuery.isError;

  async function finish(values: CreateSecretFormValues) {
    try {
      await props.onSubmit({
        secret_ref: values.secretRef.trim(),
        purpose: values.purpose.trim(),
        scope_type: values.scopeType,
        scope_id: values.scopeId.trim(),
        secret_value: values.value,
        secret_version: values.secretVersion?.trim() || undefined,
        expires_at: values.expiresAt ? values.expiresAt.toISOString() : undefined
      });
      form.resetFields();
    } catch {
      // 错误提示由 onSubmit 统一抛出展示，保持弹窗打开便于修正
    }
  }

  return (
    <Modal
      title="新建密钥"
      open={props.open}
      destroyOnHidden
      okText="创建"
      cancelText="取消"
      confirmLoading={props.submitting}
      mask={{ closable: !props.submitting }}
      onOk={() => form.submit()}
      onCancel={props.onCancel}
    >
      <Form<CreateSecretFormValues>
        form={form}
        layout="vertical"
        requiredMark={false}
        initialValues={{ scopeType: 'CONFIG', purpose: 'MODEL_API_KEY', secretVersion: 'v1' }}
        onFinish={(values) => void finish(values)}
      >
        <Form.Item
          name="secretRef"
          label="密钥引用"
          rules={[
            { required: true, message: '请输入密钥引用' },
            { pattern: /^secret:\/\/\S+$/, message: 'secretRef 必须以 secret:// 开头' }
          ]}
        >
          <Input placeholder="secret://model/my-provider-key" className="mono" />
        </Form.Item>
        <Form.Item
          name="purpose"
          label="用途"
          tooltip="模型接入密钥选择 MODEL_API_KEY；也可输入自定义大写用途"
          rules={[
            { required: true, message: '请输入用途' },
            { pattern: /^[A-Z][A-Z0-9_:-]{2,63}$/, message: '用途需为大写字母开头的字母、数字、下划线（如 MODEL_API_KEY）' }
          ]}
        >
          <AutoComplete options={PURPOSE_OPTIONS} placeholder="MODEL_API_KEY" />
        </Form.Item>
        <Form.Item name="scopeType" label="作用域类型" rules={[{ required: true }]}>
          <Select
            options={SCOPE_TYPE_OPTIONS}
            onChange={() => form.setFieldValue('scopeId', '')}
          />
        </Form.Item>
        <Form.Item
          name="scopeId"
          label={scopeType === 'CONFIG' ? '模型供应商' : '作用域 ID'}
          tooltip={scopeType === 'CONFIG' ? '密钥与该供应商一一绑定' : '项目 / 应用 / 环境的 UUID'}
          rules={[
            { required: true, message: '请输入作用域 ID' },
            { pattern: /^[0-9a-fA-F-]{36}$/, message: '作用域 ID 需为 UUID' }
          ]}
        >
          {scopeType === 'CONFIG' && !providerFallbackManual ? (
            <Select
              showSearch
              optionFilterProp="label"
              loading={providersQuery.isLoading}
              options={providerOptions}
              placeholder="选择模型供应商"
              notFoundContent={providersQuery.isLoading ? '加载中...' : '暂无供应商，可改用手动输入'}
            />
          ) : (
            <Input placeholder={scopeType === 'CONFIG' ? '供应商 ID（UUID）' : '作用域 UUID'} className="mono" />
          )}
        </Form.Item>
        {providerFallbackManual && (
          <div className="text-tertiary text-xs" style={{ marginTop: -12, marginBottom: 16 }}>
            无供应商读取权限，请手动输入供应商 ID（UUID）
          </div>
        )}
        <Form.Item
          name="value"
          label="密钥值"
          tooltip="明文仅提交一次用于加密存储，平台永不回显"
          rules={[
            { required: true, message: '请输入密钥值' },
            { min: 8, message: '密钥值至少 8 个字符' }
          ]}
        >
          <Input.Password placeholder="输入明文密钥" autoComplete="new-password" />
        </Form.Item>
        <Form.Item name="secretVersion" label="版本号">
          <Input placeholder="v1" />
        </Form.Item>
        <Form.Item name="expiresAt" label="过期时间（可选）">
          <DatePicker showTime style={{ width: '100%' }} />
        </Form.Item>
      </Form>
    </Modal>
  );
}

/* ===================== Rotate Modal ===================== */

type RotateSecretFormValues = {
  value: string;
  secretVersion?: string;
  expiresAt?: Dayjs;
};

function RotateSecretModal(props: {
  target: SecretReferenceView | null;
  submitting: boolean;
  onCancel: () => void;
  onSubmit: (values: { secret_ref: string; secret_value: string; secret_version?: string; expires_at?: string }) => Promise<void>;
}) {
  const [form] = Form.useForm<RotateSecretFormValues>();

  async function finish(values: RotateSecretFormValues) {
    if (!props.target) {
      return;
    }
    try {
      await props.onSubmit({
        secret_ref: props.target.secretRef,
        secret_value: values.value,
        secret_version: values.secretVersion?.trim() || undefined,
        expires_at: values.expiresAt ? values.expiresAt.toISOString() : undefined
      });
      form.resetFields();
    } catch {
      // 错误提示由 onSubmit 统一抛出展示
    }
  }

  return (
    <Modal
      title="轮换密钥"
      open={Boolean(props.target)}
      destroyOnHidden
      okText="确认轮换"
      cancelText="取消"
      confirmLoading={props.submitting}
      mask={{ closable: !props.submitting }}
      onOk={() => form.submit()}
      onCancel={props.onCancel}
    >
      <p className="text-secondary text-sm">
        当前引用：<span className="mono">{props.target?.secretRef}</span>（当前版本 {props.target?.secretVersion || 'v1'}）
      </p>
      <Form<RotateSecretFormValues> form={form} layout="vertical" requiredMark={false} onFinish={(values) => void finish(values)}>
        <Form.Item
          name="value"
          label="新密钥值"
          rules={[
            { required: true, message: '请输入新密钥值' },
            { min: 8, message: '密钥值至少 8 个字符' }
          ]}
        >
          <Input.Password placeholder="输入新的明文密钥" autoComplete="new-password" />
        </Form.Item>
        <Form.Item name="secretVersion" label="新版本号（留空自动递增）">
          <Input placeholder="v2" />
        </Form.Item>
        <Form.Item name="expiresAt" label="过期时间（可选）">
          <DatePicker showTime style={{ width: '100%' }} />
        </Form.Item>
      </Form>
    </Modal>
  );
}

/* ===================== Helpers ===================== */

function shortScopeId(scopeId: string): string {
  if (!scopeId) {
    return '-';
  }
  return scopeId.length > 13 ? `${scopeId.slice(0, 8)}…${scopeId.slice(-4)}` : scopeId;
}

function formatInstant(value: string | undefined): string {
  if (!value) {
    return '-';
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  const pad = (num: number) => String(num).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
}
