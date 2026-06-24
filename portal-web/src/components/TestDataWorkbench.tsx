import {
  AlertTriangle,
  Archive,
  CheckCircle2,
  Clock3,
  DatabaseZap,
  Download,
  KeyRound,
  ListChecks,
  Play,
  RefreshCw,
  RotateCcw,
  Sparkles,
  ShieldCheck,
  Trash2,
  Upload
} from 'lucide-react';
import { useCallback, useEffect, useMemo, useState, type FormEvent, type ReactNode } from 'react';
import type { CurrentUser } from '../api/auth';
import { ApiError } from '../api/client';
import {
  acquireTestAccountLease,
  addTestPooledAccount,
  archiveTestAccountPool,
  archiveTestDataSet,
  createTestAccountPool,
  createTestDataSet,
  createTestDataTask,
  disableTestAccountPool,
  downloadTestAccountLeaseExport,
  downloadTestDataSetExport,
  exportTestAccountLease,
  exportTestDataSet,
  fetchTestAccountLease,
  fetchTestAccountLeases,
  fetchTestAccountPool,
  fetchTestAccountPools,
  fetchTestDataHealth,
  fetchTestDataSet,
  fetchTestDataSets,
  fetchTestDataTask,
  fetchTestDataTasks,
  generateTestDataRecords,
  importTestDataRecords,
  releaseTestAccountLease,
  renewTestAccountLease,
  retryTestDataTask,
  updateTestAccountPool,
  updateTestDataSet,
  updateTestPooledAccount,
  type TestAccountLease,
  type TestAccountLeaseExport,
  type TestAccountPoolDetail,
  type TestAccountPoolSummary,
  type TestDataHealth,
  type TestDataSetDetail,
  type TestDataSetExport,
  type TestDataSetSummary,
  type TestDataTask,
  type TestPooledAccount
} from '../api/testData';
import { canUseButton, hasPermission } from '../permissions';

type WorkState = {
  loading: boolean;
  error?: string;
  success?: string;
  traceId?: string;
};

type TabKey = 'data-sets' | 'account-pools' | 'leases' | 'tasks';

type DataSetDraft = {
  projectId: string;
  applicationId: string;
  environmentId: string;
  code: string;
  name: string;
  status: string;
  sensitivityLevel: string;
  sourceType: string;
  sourceRefDigest: string;
  schemaText: string;
  cleanupPolicyText: string;
};

type RecordDraft = {
  recordKey: string;
  recordDigest: string;
  maskedSummaryText: string;
  externalRefDigest: string;
  tagsText: string;
};

type GenerateDraft = {
  count: number;
  recordKeyPrefix: string;
  tagsText: string;
};

type PoolDraft = {
  projectId: string;
  applicationId: string;
  environmentId: string;
  code: string;
  name: string;
  status: string;
  defaultTtlSeconds: number;
  leasePolicyText: string;
};

type AccountDraft = {
  accountKey: string;
  displayName: string;
  status: string;
  roleTagsText: string;
  scopeSummaryText: string;
  secretRef: string;
  lastHealthStatus: string;
  lastHealthSummary: string;
};

type LeaseDraft = {
  projectId: string;
  applicationId: string;
  environmentId: string;
  poolId: string;
  roleTagsText: string;
  holderType: string;
  holderRef: string;
  ttlSeconds: number;
  requestKey: string;
  renewTtlSeconds: number;
  releaseReason: string;
  accountStatus: string;
};

type TaskDraft = {
  projectId: string;
  dataSetId: string;
  taskType: string;
  requestKey: string;
  targetRef: string;
  resultSummaryText: string;
  retryRequestKey: string;
};

const tabs: Array<{ key: TabKey; label: string; icon: ReactNode }> = [
  { key: 'data-sets', label: '数据集', icon: <DatabaseZap size={15} /> },
  { key: 'account-pools', label: '账号池', icon: <KeyRound size={15} /> },
  { key: 'leases', label: '租借', icon: <Clock3 size={15} /> },
  { key: 'tasks', label: '清理任务', icon: <ListChecks size={15} /> }
];

const initialDataSetDraft: DataSetDraft = {
  projectId: '',
  applicationId: '',
  environmentId: '',
  code: '',
  name: '',
  status: 'DRAFT',
  sensitivityLevel: 'INTERNAL',
  sourceType: 'MANUAL',
  sourceRefDigest: '',
  schemaText: '{}',
  cleanupPolicyText: '{"mode":"MANUAL"}'
};

const initialRecordDraft: RecordDraft = {
  recordKey: '',
  recordDigest: '',
  maskedSummaryText: '{}',
  externalRefDigest: '',
  tagsText: ''
};

const initialGenerateDraft: GenerateDraft = {
  count: 3,
  recordKeyPrefix: '',
  tagsText: 'smoke'
};

const initialPoolDraft: PoolDraft = {
  projectId: '',
  applicationId: '',
  environmentId: '',
  code: '',
  name: '',
  status: 'DRAFT',
  defaultTtlSeconds: 1800,
  leasePolicyText: '{"maxConcurrentLeases":1}'
};

const initialAccountDraft: AccountDraft = {
  accountKey: '',
  displayName: '',
  status: 'AVAILABLE',
  roleTagsText: '',
  scopeSummaryText: '{}',
  secretRef: '',
  lastHealthStatus: '',
  lastHealthSummary: ''
};

const initialLeaseDraft: LeaseDraft = {
  projectId: '',
  applicationId: '',
  environmentId: '',
  poolId: '',
  roleTagsText: '',
  holderType: 'MANUAL',
  holderRef: '',
  ttlSeconds: 1800,
  requestKey: '',
  renewTtlSeconds: 1800,
  releaseReason: '',
  accountStatus: 'AVAILABLE'
};

const initialTaskDraft: TaskDraft = {
  projectId: '',
  dataSetId: '',
  taskType: 'CLEANUP',
  requestKey: '',
  targetRef: '',
  resultSummaryText: '{}',
  retryRequestKey: ''
};

export function TestDataWorkbench(props: { signedIn: boolean; currentUser: CurrentUser | null }) {
  const canRead = hasPermission(props.currentUser, 'testData:read');
  const canManage = canUseButton(props.currentUser, 'testData:manage');
  const canLease = canUseButton(props.currentUser, 'testData:lease');
  const canCleanup = canUseButton(props.currentUser, 'testData:cleanup');
  const canExport = canUseButton(props.currentUser, 'testData:export');
  const [activeTab, setActiveTab] = useState<TabKey>('data-sets');
  const [health, setHealth] = useState<TestDataHealth | null>(null);
  const [dataSets, setDataSets] = useState<TestDataSetSummary[]>([]);
  const [accountPools, setAccountPools] = useState<TestAccountPoolSummary[]>([]);
  const [leases, setLeases] = useState<TestAccountLease[]>([]);
  const [tasks, setTasks] = useState<TestDataTask[]>([]);
  const [selectedDataSetId, setSelectedDataSetId] = useState('');
  const [selectedPoolId, setSelectedPoolId] = useState('');
  const [selectedAccountId, setSelectedAccountId] = useState('');
  const [selectedLeaseId, setSelectedLeaseId] = useState('');
  const [selectedTaskId, setSelectedTaskId] = useState('');
  const [dataSetDetail, setDataSetDetail] = useState<TestDataSetDetail | null>(null);
  const [poolDetail, setPoolDetail] = useState<TestAccountPoolDetail | null>(null);
  const [leaseDetail, setLeaseDetail] = useState<TestAccountLease | null>(null);
  const [taskDetail, setTaskDetail] = useState<TestDataTask | null>(null);
  const [dataSetExport, setDataSetExport] = useState<TestDataSetExport | null>(null);
  const [leaseExport, setLeaseExport] = useState<TestAccountLeaseExport | null>(null);
  const [dataSetDraft, setDataSetDraft] = useState<DataSetDraft>(initialDataSetDraft);
  const [recordDraft, setRecordDraft] = useState<RecordDraft>(initialRecordDraft);
  const [generateDraft, setGenerateDraft] = useState<GenerateDraft>(initialGenerateDraft);
  const [poolDraft, setPoolDraft] = useState<PoolDraft>(initialPoolDraft);
  const [accountDraft, setAccountDraft] = useState<AccountDraft>(initialAccountDraft);
  const [leaseDraft, setLeaseDraft] = useState<LeaseDraft>(initialLeaseDraft);
  const [taskDraft, setTaskDraft] = useState<TaskDraft>(initialTaskDraft);
  const [loadState, setLoadState] = useState<WorkState>({ loading: false });
  const [dataSetState, setDataSetState] = useState<WorkState>({ loading: false });
  const [poolState, setPoolState] = useState<WorkState>({ loading: false });
  const [leaseState, setLeaseState] = useState<WorkState>({ loading: false });
  const [taskState, setTaskState] = useState<WorkState>({ loading: false });
  const [dataSetExportState, setDataSetExportState] = useState<WorkState>({ loading: false });
  const [leaseExportState, setLeaseExportState] = useState<WorkState>({ loading: false });

  const summary = useMemo(() => {
    const available = accountPools.reduce((total, pool) => total + pool.availableAccountCount, 0);
    const activeLeases = leases.filter((lease) => lease.status === 'ACTIVE').length;
    const failedTasks = tasks.filter((task) => ['FAILED', 'BLOCKED'].includes(task.status)).length;
    return { available, activeLeases, failedTasks };
  }, [accountPools, leases, tasks]);

  const refreshWorkbench = useCallback(async () => {
    if (!props.signedIn || !canRead) {
      resetLoadedData();
      return;
    }
    setLoadState({ loading: true });
    try {
      const [healthResult, dataSetResult, poolResult, leaseResult, taskResult] = await Promise.all([
        fetchTestDataHealth(),
        fetchTestDataSets({ size: 50 }),
        fetchTestAccountPools({ size: 50 }),
        fetchTestAccountLeases({ size: 50 }),
        fetchTestDataTasks({ size: 50 })
      ]);
      setHealth(healthResult.data);
      setDataSets(dataSetResult.data.items);
      setAccountPools(poolResult.data.items);
      setLeases(leaseResult.data.items);
      setTasks(taskResult.data.items);
      setSelectedDataSetId((current) => current || dataSetResult.data.items[0]?.id || '');
      setSelectedPoolId((current) => current || poolResult.data.items[0]?.id || '');
      setSelectedLeaseId((current) => current || leaseResult.data.items[0]?.id || '');
      setSelectedTaskId((current) => current || taskResult.data.items[0]?.id || '');
      setLoadState({ loading: false, traceId: healthResult.trace_id || dataSetResult.trace_id || poolResult.trace_id });
    } catch (error: unknown) {
      setLoadState(errorState(error, '测试数据工作台加载失败'));
    }
  }, [canRead, props.signedIn]);

  const refreshDataSetDetail = useCallback(async (id: string) => {
    if (!id || !canRead) {
      setDataSetDetail(null);
      setDataSetExport(null);
      setDataSetExportState({ loading: false });
      return;
    }
    setDataSetExport(null);
    setDataSetExportState({ loading: false });
    try {
      const result = await fetchTestDataSet(id);
      setDataSetDetail(result.data);
      setDataSetDraft(dataSetDraftFromDetail(result.data));
      setDataSetState({ loading: false, traceId: result.trace_id });
    } catch (error: unknown) {
      setDataSetState(errorState(error, '数据集详情加载失败'));
    }
  }, [canRead]);

  const refreshPoolDetail = useCallback(async (id: string) => {
    if (!id || !canRead) {
      setPoolDetail(null);
      setSelectedAccountId('');
      return;
    }
    try {
      const result = await fetchTestAccountPool(id);
      setPoolDetail(result.data);
      setPoolDraft(poolDraftFromDetail(result.data));
      setSelectedAccountId((current) => current || result.data.accounts[0]?.id || '');
      setPoolState({ loading: false, traceId: result.trace_id });
    } catch (error: unknown) {
      setPoolState(errorState(error, '账号池详情加载失败'));
    }
  }, [canRead]);

  const refreshLeaseDetail = useCallback(async (id: string) => {
    if (!id || !canRead) {
      setLeaseDetail(null);
      setLeaseExport(null);
      setLeaseExportState({ loading: false });
      return;
    }
    setLeaseExport(null);
    setLeaseExportState({ loading: false });
    try {
      const result = await fetchTestAccountLease(id);
      setLeaseDetail(result.data);
      setLeaseState({ loading: false, traceId: result.trace_id });
    } catch (error: unknown) {
      setLeaseState(errorState(error, '租借详情加载失败'));
    }
  }, [canRead]);

  const refreshTaskDetail = useCallback(async (id: string) => {
    if (!id || !canRead) {
      setTaskDetail(null);
      return;
    }
    try {
      const result = await fetchTestDataTask(id);
      setTaskDetail(result.data);
      setTaskState({ loading: false, traceId: result.trace_id });
    } catch (error: unknown) {
      setTaskState(errorState(error, '任务详情加载失败'));
    }
  }, [canRead]);

  useEffect(() => {
    void refreshWorkbench();
  }, [refreshWorkbench]);

  useEffect(() => {
    void refreshDataSetDetail(selectedDataSetId);
  }, [refreshDataSetDetail, selectedDataSetId]);

  useEffect(() => {
    void refreshPoolDetail(selectedPoolId);
  }, [refreshPoolDetail, selectedPoolId]);

  useEffect(() => {
    void refreshLeaseDetail(selectedLeaseId);
  }, [refreshLeaseDetail, selectedLeaseId]);

  useEffect(() => {
    void refreshTaskDetail(selectedTaskId);
  }, [refreshTaskDetail, selectedTaskId]);

  if (!props.signedIn) {
    return <div className="notice warning">请先登录后查看测试数据。</div>;
  }

  if (!canRead) {
    return <div className="notice error">当前账号缺少 testData:read 权限。</div>;
  }

  async function onCreateDataSet(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!canManage) return;
    const parsed = parseJsonFields([
      ['schema', dataSetDraft.schemaText],
      ['cleanupPolicy', dataSetDraft.cleanupPolicyText]
    ]);
    if (!parsed.ok) {
      setDataSetState({ loading: false, error: parsed.message });
      return;
    }
    setDataSetState({ loading: true });
    try {
      const result = await createTestDataSet({
        projectId: dataSetDraft.projectId.trim(),
        applicationId: optionalText(dataSetDraft.applicationId),
        environmentId: optionalText(dataSetDraft.environmentId),
        code: dataSetDraft.code.trim(),
        name: dataSetDraft.name.trim(),
        status: dataSetDraft.status,
        schema: parsed.values.schema,
        sensitivityLevel: dataSetDraft.sensitivityLevel,
        cleanupPolicy: parsed.values.cleanupPolicy,
        sourceType: dataSetDraft.sourceType,
        sourceRefDigest: optionalText(dataSetDraft.sourceRefDigest)
      });
      setSelectedDataSetId(result.data.id);
      setDataSetDetail(result.data);
      setDataSetState({ loading: false, success: '数据集已创建', traceId: result.trace_id });
      await refreshWorkbench();
    } catch (error: unknown) {
      setDataSetState(errorState(error, '数据集创建失败'));
    }
  }

  async function onUpdateDataSet() {
    if (!selectedDataSetId || !canManage) return;
    const parsed = parseJsonFields([
      ['schema', dataSetDraft.schemaText],
      ['cleanupPolicy', dataSetDraft.cleanupPolicyText]
    ]);
    if (!parsed.ok) {
      setDataSetState({ loading: false, error: parsed.message });
      return;
    }
    setDataSetState({ loading: true });
    try {
      const result = await updateTestDataSet(selectedDataSetId, {
        applicationId: optionalText(dataSetDraft.applicationId),
        environmentId: optionalText(dataSetDraft.environmentId),
        name: dataSetDraft.name.trim(),
        status: dataSetDraft.status,
        schema: parsed.values.schema,
        sensitivityLevel: dataSetDraft.sensitivityLevel,
        cleanupPolicy: parsed.values.cleanupPolicy,
        sourceType: dataSetDraft.sourceType,
        sourceRefDigest: optionalText(dataSetDraft.sourceRefDigest)
      });
      setDataSetDetail(result.data);
      setDataSetState({ loading: false, success: '数据集已保存', traceId: result.trace_id });
      await refreshWorkbench();
    } catch (error: unknown) {
      setDataSetState(errorState(error, '数据集保存失败'));
    }
  }

  async function onArchiveDataSet() {
    if (!selectedDataSetId || !canManage) return;
    setDataSetState({ loading: true });
    try {
      const result = await archiveTestDataSet(selectedDataSetId);
      setDataSetDetail(result.data);
      setDataSetState({ loading: false, success: '数据集已归档', traceId: result.trace_id });
      await refreshWorkbench();
    } catch (error: unknown) {
      setDataSetState(errorState(error, '数据集归档失败'));
    }
  }

  async function onImportRecord(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedDataSetId || !canManage) return;
    const parsed = parseJsonFields([['maskedSummary', recordDraft.maskedSummaryText]]);
    if (!parsed.ok) {
      setDataSetState({ loading: false, error: parsed.message });
      return;
    }
    setDataSetState({ loading: true });
    try {
      const result = await importTestDataRecords(selectedDataSetId, {
        records: [{
          recordKey: recordDraft.recordKey.trim(),
          recordDigest: recordDraft.recordDigest.trim(),
          maskedSummary: parsed.values.maskedSummary,
          externalRefDigest: optionalText(recordDraft.externalRefDigest),
          tags: splitList(recordDraft.tagsText)
        }]
      });
      setRecordDraft(initialRecordDraft);
      setDataSetState({ loading: false, success: `记录已导入 ${result.data.importedCount} 条`, traceId: result.trace_id });
      await refreshDataSetDetail(selectedDataSetId);
      await refreshWorkbench();
    } catch (error: unknown) {
      setDataSetState(errorState(error, '记录导入失败'));
    }
  }

  async function onExportDataSet() {
    if (!selectedDataSetId || !canExport || !health?.exportEnabled) return;
    setDataSetExportState({ loading: true });
    try {
      const result = await exportTestDataSet(selectedDataSetId);
      setDataSetExport(result.data);
      setDataSetExportState({ loading: false, success: '脱敏导出摘要已生成', traceId: result.trace_id });
    } catch (error: unknown) {
      setDataSetExportState(errorState(error, '脱敏导出失败'));
    }
  }

  async function onDownloadDataSetExport() {
    if (!selectedDataSetId || !canExport || !health?.exportEnabled) return;
    setDataSetExportState({ loading: true });
    try {
      const result = await downloadTestDataSetExport(selectedDataSetId);
      triggerBrowserDownload(result.blob, result.filename ?? 'wp8-data-set-export.json');
      setDataSetExportState({ loading: false, success: `导出文件已下载 ${result.filename ?? ''}`.trim(), traceId: result.traceId });
    } catch (error: unknown) {
      setDataSetExportState(errorState(error, '脱敏导出文件下载失败'));
    }
  }

  async function onGenerateRecords(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedDataSetId || !canManage) return;
    setDataSetState({ loading: true });
    try {
      const result = await generateTestDataRecords(selectedDataSetId, {
        count: generateDraft.count,
        recordKeyPrefix: optionalText(generateDraft.recordKeyPrefix),
        tags: splitList(generateDraft.tagsText)
      });
      setDataSetState({ loading: false, success: `已生成 ${result.data.generatedCount} 条模拟记录`, traceId: result.trace_id });
      await refreshDataSetDetail(selectedDataSetId);
      await refreshWorkbench();
    } catch (error: unknown) {
      setDataSetState(errorState(error, '模拟记录生成失败'));
    }
  }

  async function onExportLease() {
    if (!selectedLeaseId || !canExport || !health?.exportEnabled) return;
    setLeaseExportState({ loading: true });
    try {
      const result = await exportTestAccountLease(selectedLeaseId);
      setLeaseExport(result.data);
      setLeaseExportState({ loading: false, success: '租借脱敏导出摘要已生成', traceId: result.trace_id });
    } catch (error: unknown) {
      setLeaseExportState(errorState(error, '租借脱敏导出失败'));
    }
  }

  async function onDownloadLeaseExport() {
    if (!selectedLeaseId || !canExport || !health?.exportEnabled) return;
    setLeaseExportState({ loading: true });
    try {
      const result = await downloadTestAccountLeaseExport(selectedLeaseId);
      triggerBrowserDownload(result.blob, result.filename ?? 'wp8-account-lease-export.json');
      setLeaseExportState({ loading: false, success: `租借导出文件已下载 ${result.filename ?? ''}`.trim(), traceId: result.traceId });
    } catch (error: unknown) {
      setLeaseExportState(errorState(error, '租借导出文件下载失败'));
    }
  }

  async function onCreatePool(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!canManage) return;
    const parsed = parseJsonFields([['leasePolicy', poolDraft.leasePolicyText]]);
    if (!parsed.ok) {
      setPoolState({ loading: false, error: parsed.message });
      return;
    }
    setPoolState({ loading: true });
    try {
      const result = await createTestAccountPool({
        projectId: poolDraft.projectId.trim(),
        applicationId: optionalText(poolDraft.applicationId),
        environmentId: optionalText(poolDraft.environmentId),
        code: poolDraft.code.trim(),
        name: poolDraft.name.trim(),
        status: poolDraft.status,
        leasePolicy: parsed.values.leasePolicy,
        defaultTtlSeconds: poolDraft.defaultTtlSeconds
      });
      setSelectedPoolId(result.data.id);
      setSelectedAccountId('');
      setPoolDetail(result.data);
      setPoolState({ loading: false, success: '账号池已创建', traceId: result.trace_id });
      await refreshWorkbench();
    } catch (error: unknown) {
      setPoolState(errorState(error, '账号池创建失败'));
    }
  }

  async function onUpdatePool() {
    if (!selectedPoolId || !canManage) return;
    const parsed = parseJsonFields([['leasePolicy', poolDraft.leasePolicyText]]);
    if (!parsed.ok) {
      setPoolState({ loading: false, error: parsed.message });
      return;
    }
    setPoolState({ loading: true });
    try {
      const result = await updateTestAccountPool(selectedPoolId, {
        applicationId: optionalText(poolDraft.applicationId),
        environmentId: optionalText(poolDraft.environmentId),
        name: poolDraft.name.trim(),
        status: poolDraft.status,
        leasePolicy: parsed.values.leasePolicy,
        defaultTtlSeconds: poolDraft.defaultTtlSeconds
      });
      setPoolDetail(result.data);
      setPoolState({ loading: false, success: '账号池已保存', traceId: result.trace_id });
      await refreshWorkbench();
    } catch (error: unknown) {
      setPoolState(errorState(error, '账号池保存失败'));
    }
  }

  async function onDisablePool() {
    if (!selectedPoolId || !canManage) return;
    setPoolState({ loading: true });
    try {
      const result = await disableTestAccountPool(selectedPoolId);
      setPoolDetail(result.data);
      setPoolState({ loading: false, success: '账号池已禁用', traceId: result.trace_id });
      await refreshWorkbench();
    } catch (error: unknown) {
      setPoolState(errorState(error, '账号池禁用失败'));
    }
  }

  async function onArchivePool() {
    if (!selectedPoolId || !canManage) return;
    setPoolState({ loading: true });
    try {
      const result = await archiveTestAccountPool(selectedPoolId);
      setPoolDetail(result.data);
      setPoolState({ loading: false, success: '账号池已归档', traceId: result.trace_id });
      await refreshWorkbench();
    } catch (error: unknown) {
      setPoolState(errorState(error, '账号池归档失败'));
    }
  }

  async function onSaveAccount(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedPoolId || !canManage) return;
    const parsed = parseJsonFields([['scopeSummary', accountDraft.scopeSummaryText]]);
    if (!parsed.ok) {
      setPoolState({ loading: false, error: parsed.message });
      return;
    }
    setPoolState({ loading: true });
    try {
      const accountFields = {
        displayName: optionalText(accountDraft.displayName),
        status: accountDraft.status,
        roleTags: splitList(accountDraft.roleTagsText),
        scopeSummary: parsed.values.scopeSummary,
        secretRef: optionalText(accountDraft.secretRef),
        lastHealthStatus: optionalText(accountDraft.lastHealthStatus),
        lastHealthSummary: optionalText(accountDraft.lastHealthSummary)
      };
      const result = selectedAccountId
        ? await updateTestPooledAccount(selectedAccountId, accountFields)
        : await addTestPooledAccount(selectedPoolId, {
          accountKey: accountDraft.accountKey.trim(),
          ...accountFields
        });
      setSelectedAccountId(result.data.id);
      setAccountDraft({ ...accountDraftFromAccount(result.data), secretRef: '' });
      setPoolState({ loading: false, success: selectedAccountId ? '账号摘要已更新' : '账号摘要已新增', traceId: result.trace_id });
      await refreshPoolDetail(selectedPoolId);
      await refreshWorkbench();
    } catch (error: unknown) {
      setPoolState(errorState(error, '账号保存失败'));
    }
  }

  async function onAcquireLease(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!canLease) return;
    setLeaseState({ loading: true });
    try {
      const result = await acquireTestAccountLease({
        projectId: leaseDraft.projectId.trim(),
        applicationId: optionalText(leaseDraft.applicationId),
        environmentId: optionalText(leaseDraft.environmentId),
        poolId: leaseDraft.poolId.trim() || selectedPoolId,
        roleTags: splitList(leaseDraft.roleTagsText),
        holderType: leaseDraft.holderType.trim(),
        holderRef: leaseDraft.holderRef.trim(),
        ttlSeconds: leaseDraft.ttlSeconds,
        requestKey: leaseDraft.requestKey.trim()
      });
      setSelectedLeaseId(result.data.id);
      setLeaseDetail(result.data);
      setLeaseState({ loading: false, success: '账号租借已申请', traceId: result.trace_id });
      await refreshWorkbench();
    } catch (error: unknown) {
      setLeaseState(errorState(error, '租借申请失败'));
    }
  }

  async function onRenewLease() {
    if (!selectedLeaseId || !canLease) return;
    setLeaseState({ loading: true });
    try {
      const result = await renewTestAccountLease(selectedLeaseId, { ttlSeconds: leaseDraft.renewTtlSeconds });
      setLeaseDetail(result.data);
      setLeaseState({ loading: false, success: '租借已续期', traceId: result.trace_id });
      await refreshWorkbench();
    } catch (error: unknown) {
      setLeaseState(errorState(error, '租借续期失败'));
    }
  }

  async function onReleaseLease() {
    if (!selectedLeaseId || !canLease) return;
    setLeaseState({ loading: true });
    try {
      const result = await releaseTestAccountLease(selectedLeaseId, {
        releaseReason: optionalText(leaseDraft.releaseReason),
        accountStatus: optionalText(leaseDraft.accountStatus)
      });
      setLeaseDetail(result.data);
      setLeaseState({ loading: false, success: '租借已释放', traceId: result.trace_id });
      await refreshWorkbench();
    } catch (error: unknown) {
      setLeaseState(errorState(error, '租借释放失败'));
    }
  }

  async function onCreateTask(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!canCleanup) return;
    const parsed = parseJsonFields([['resultSummary', taskDraft.resultSummaryText]]);
    if (!parsed.ok) {
      setTaskState({ loading: false, error: parsed.message });
      return;
    }
    setTaskState({ loading: true });
    try {
      const result = await createTestDataTask({
        projectId: taskDraft.projectId.trim(),
        dataSetId: optionalText(taskDraft.dataSetId) ?? optionalText(selectedDataSetId),
        taskType: taskDraft.taskType,
        requestKey: taskDraft.requestKey.trim(),
        targetRef: optionalText(taskDraft.targetRef),
        resultSummary: parsed.values.resultSummary
      });
      setSelectedTaskId(result.data.id);
      setTaskDetail(result.data);
      setTaskState({ loading: false, success: '清理任务已创建', traceId: result.trace_id });
      await refreshWorkbench();
    } catch (error: unknown) {
      setTaskState(errorState(error, '清理任务创建失败'));
    }
  }

  async function onRetryTask() {
    if (!selectedTaskId || !canCleanup) return;
    const parsed = parseJsonFields([['resultSummary', taskDraft.resultSummaryText]]);
    if (!parsed.ok) {
      setTaskState({ loading: false, error: parsed.message });
      return;
    }
    setTaskState({ loading: true });
    try {
      const result = await retryTestDataTask(selectedTaskId, {
        requestKey: optionalText(taskDraft.retryRequestKey),
        resultSummary: parsed.values.resultSummary
      });
      setTaskDetail(result.data);
      setTaskState({ loading: false, success: '清理任务已重试', traceId: result.trace_id });
      await refreshWorkbench();
    } catch (error: unknown) {
      setTaskState(errorState(error, '清理任务重试失败'));
    }
  }

  function resetLoadedData() {
    setHealth(null);
    setDataSets([]);
    setAccountPools([]);
    setLeases([]);
    setTasks([]);
    setDataSetDetail(null);
    setDataSetExport(null);
    setLeaseExport(null);
    setPoolDetail(null);
    setLeaseDetail(null);
    setTaskDetail(null);
    setSelectedDataSetId('');
    setSelectedPoolId('');
    setSelectedAccountId('');
    setSelectedLeaseId('');
    setSelectedTaskId('');
    setLoadState({ loading: false });
    setDataSetExportState({ loading: false });
    setLeaseExportState({ loading: false });
  }

  return (
    <section className="test-data-workbench" data-testid="test-data-workbench">
      <div className="metric-grid test-data-metric-grid">
        <MetricCard label="数据集" value={String(dataSets.length)} icon={<DatabaseZap size={18} />} />
        <MetricCard label="可用账号" value={String(summary.available)} icon={<KeyRound size={18} />} />
        <MetricCard label="ACTIVE 租借" value={String(summary.activeLeases)} icon={<Clock3 size={18} />} />
        <MetricCard label="失败任务" value={String(summary.failedTasks)} icon={<AlertTriangle size={18} />} />
      </div>

      <section className="panel">
        <div className="panel-header">
          <div>
            <div className="panel-title">WP8 控制面策略</div>
            <div className="panel-desc">{health ? `${health.service} · ${health.status}` : loadState.loading ? '加载中' : '未加载'}</div>
          </div>
          <button className="btn btn-ghost btn-sm" type="button" onClick={() => void refreshWorkbench()} disabled={loadState.loading}>
            <RefreshCw size={15} />
            刷新
          </button>
        </div>
        <div className="panel-body compact">
          <StateLine state={loadState} />
          <div className="test-data-policy-grid">
            <PolicyItem label="控制面" value={health?.enabled ? 'ENABLED' : 'DISABLED'} />
            <PolicyItem label="清理执行" value={health?.cleanupEnabled ? 'ENABLED' : 'DISABLED'} />
            <PolicyItem label="脱敏导出" value={health?.exportEnabled ? 'ENABLED' : 'DISABLED'} />
            <PolicyItem label="记录上限" value={String(health?.recordMaxCount ?? 0)} />
            <PolicyItem label="默认 TTL" value={`${health?.defaultLeaseTtlSeconds ?? 0}s`} />
            <PolicyItem label="导出权限" value={canExport ? 'ALLOWED' : 'BLOCKED'} />
          </div>
        </div>
      </section>

      <div className="test-data-tabs" role="tablist" aria-label="测试数据工作台">
        {tabs.map((tab) => (
          <button
            key={tab.key}
            className={activeTab === tab.key ? 'test-data-tab active' : 'test-data-tab'}
            type="button"
            onClick={() => setActiveTab(tab.key)}
            role="tab"
            aria-selected={activeTab === tab.key}
          >
            {tab.icon}
            {tab.label}
          </button>
        ))}
      </div>

      {activeTab === 'data-sets' && renderDataSets()}
      {activeTab === 'account-pools' && renderAccountPools()}
      {activeTab === 'leases' && renderLeases()}
      {activeTab === 'tasks' && renderTasks()}
    </section>
  );

  function renderDataSets() {
    return (
      <section className="test-data-layout">
        <form className="panel" onSubmit={onCreateDataSet}>
          <div className="panel-header">
            <div>
              <div className="panel-title">{selectedDataSetId ? '数据集表单' : '新建数据集'}</div>
              <div className="panel-desc">只维护 schema、摘要和引用 digest</div>
            </div>
            <div className="test-data-panel-actions">
              <button className="btn btn-primary btn-sm" type="submit" disabled={!canManage || dataSetState.loading}>
                <DatabaseZap size={15} />
                创建
              </button>
              <button className="btn btn-secondary btn-sm" type="button" onClick={() => void onUpdateDataSet()} disabled={!selectedDataSetId || !canManage || dataSetState.loading}>
                <ShieldCheck size={15} />
                保存
              </button>
              <button className="btn btn-ghost btn-sm" type="button" onClick={() => void onArchiveDataSet()} disabled={!selectedDataSetId || !canManage || dataSetDetail?.status === 'ARCHIVED'}>
                <Archive size={15} />
                归档
              </button>
            </div>
          </div>
          <div className="panel-body">
            <div className="form-grid">
              <Field label="projectId"><input value={dataSetDraft.projectId} onChange={(event) => setDataSetDraftValue('projectId', event.target.value)} /></Field>
              <Field label="code"><input value={dataSetDraft.code} onChange={(event) => setDataSetDraftValue('code', event.target.value)} /></Field>
              <Field label="名称"><input value={dataSetDraft.name} onChange={(event) => setDataSetDraftValue('name', event.target.value)} /></Field>
              <Field label="状态"><StatusSelect value={dataSetDraft.status} onChange={(value) => setDataSetDraftValue('status', value)} /></Field>
              <Field label="applicationId"><input value={dataSetDraft.applicationId} onChange={(event) => setDataSetDraftValue('applicationId', event.target.value)} /></Field>
              <Field label="environmentId"><input value={dataSetDraft.environmentId} onChange={(event) => setDataSetDraftValue('environmentId', event.target.value)} /></Field>
              <Field label="敏感级别">
                <select value={dataSetDraft.sensitivityLevel} onChange={(event) => setDataSetDraftValue('sensitivityLevel', event.target.value)}>
                  <option value="INTERNAL">INTERNAL</option>
                  <option value="CONFIDENTIAL">CONFIDENTIAL</option>
                  <option value="RESTRICTED">RESTRICTED</option>
                </select>
              </Field>
              <Field label="sourceType">
                <select value={dataSetDraft.sourceType} onChange={(event) => setDataSetDraftValue('sourceType', event.target.value)}>
                  <option value="MANUAL">MANUAL</option>
                  <option value="GENERATED">GENERATED</option>
                  <option value="EXTERNAL_REF">EXTERNAL_REF</option>
                </select>
              </Field>
            </div>
            <Field label="sourceRefDigest"><input value={dataSetDraft.sourceRefDigest} onChange={(event) => setDataSetDraftValue('sourceRefDigest', event.target.value)} /></Field>
            <div className="form-grid">
              <Field label="schema JSON"><textarea value={dataSetDraft.schemaText} onChange={(event) => setDataSetDraftValue('schemaText', event.target.value)} /></Field>
              <Field label="cleanupPolicy JSON"><textarea value={dataSetDraft.cleanupPolicyText} onChange={(event) => setDataSetDraftValue('cleanupPolicyText', event.target.value)} /></Field>
            </div>
            <StateLine state={dataSetState} />
          </div>
        </form>

        <section className="panel">
          <div className="panel-header">
            <div>
              <div className="panel-title">数据集列表</div>
              <div className="panel-desc">{dataSets.length} 条 · 记录摘要不含正文</div>
            </div>
          </div>
          <div className="panel-body compact">
            <div className="table-wrap test-data-table-wrap">
              <table>
                <thead><tr><th>数据集</th><th>项目</th><th>状态</th><th>记录</th></tr></thead>
                <tbody>
                  {dataSets.length ? dataSets.map((item) => (
                    <tr key={item.id} className={selectedDataSetId === item.id ? 'selected-row' : undefined} onClick={() => setSelectedDataSetId(item.id)}>
                      <td><PrimaryText title={item.name} subtitle={item.code} /></td>
                      <td><span className="text-secondary">{item.projectId}</span></td>
                      <td><StatusBadge status={item.status} /></td>
                      <td>{item.recordCount}</td>
                    </tr>
                  )) : (
                    <tr><td className="table-empty" colSpan={4}>{loadState.loading ? '加载中' : '暂无数据集'}</td></tr>
                  )}
                </tbody>
              </table>
            </div>
            <RecordImportForm />
            <RecordGenerateForm />
            <RecordList />
            <DataSetExportPanel />
          </div>
        </section>
      </section>
    );
  }

  function renderAccountPools() {
    return (
      <section className="test-data-layout">
        <form className="panel" onSubmit={onCreatePool}>
          <div className="panel-header">
            <div>
              <div className="panel-title">账号池表单</div>
              <div className="panel-desc">凭据只作为写入输入，列表仅显示 digest</div>
            </div>
            <div className="test-data-panel-actions">
              <button className="btn btn-primary btn-sm" type="submit" disabled={!canManage || poolState.loading}>
                <KeyRound size={15} />
                创建
              </button>
              <button className="btn btn-secondary btn-sm" type="button" onClick={() => void onUpdatePool()} disabled={!selectedPoolId || !canManage || poolState.loading}>
                <ShieldCheck size={15} />
                保存
              </button>
              <button className="btn btn-secondary btn-sm" type="button" onClick={() => void onDisablePool()} disabled={!selectedPoolId || !canManage || poolDetail?.status === 'DISABLED'}>
                <Trash2 size={15} />
                禁用
              </button>
              <button className="btn btn-ghost btn-sm" type="button" onClick={() => void onArchivePool()} disabled={!selectedPoolId || !canManage || poolDetail?.status === 'ARCHIVED'}>
                <Archive size={15} />
                归档
              </button>
            </div>
          </div>
          <div className="panel-body">
            <div className="form-grid">
              <Field label="projectId"><input value={poolDraft.projectId} onChange={(event) => setPoolDraftValue('projectId', event.target.value)} /></Field>
              <Field label="code"><input value={poolDraft.code} onChange={(event) => setPoolDraftValue('code', event.target.value)} /></Field>
              <Field label="名称"><input value={poolDraft.name} onChange={(event) => setPoolDraftValue('name', event.target.value)} /></Field>
              <Field label="状态"><StatusSelect value={poolDraft.status} onChange={(value) => setPoolDraftValue('status', value)} /></Field>
              <Field label="applicationId"><input value={poolDraft.applicationId} onChange={(event) => setPoolDraftValue('applicationId', event.target.value)} /></Field>
              <Field label="environmentId"><input value={poolDraft.environmentId} onChange={(event) => setPoolDraftValue('environmentId', event.target.value)} /></Field>
              <Field label="默认 TTL"><input type="number" min={1} max={86400} value={poolDraft.defaultTtlSeconds} onChange={(event) => setPoolDraftValue('defaultTtlSeconds', Number(event.target.value))} /></Field>
            </div>
            <Field label="leasePolicy JSON"><textarea value={poolDraft.leasePolicyText} onChange={(event) => setPoolDraftValue('leasePolicyText', event.target.value)} /></Field>
            <StateLine state={poolState} />
          </div>
        </form>

        <section className="panel">
          <div className="panel-header">
            <div>
              <div className="panel-title">账号池与账号摘要</div>
              <div className="panel-desc">{accountPools.length} 个池 · selected {shortId(selectedPoolId)}</div>
            </div>
          </div>
          <div className="panel-body compact">
            <div className="test-data-list">
              {accountPools.length ? accountPools.map((pool) => (
                <button key={pool.id} className={selectedPoolId === pool.id ? 'test-data-list-item active' : 'test-data-list-item'} type="button" onClick={() => {
                  setSelectedPoolId(pool.id);
                  setSelectedAccountId('');
                }}>
                  <span><strong>{pool.name}</strong><small>{pool.projectId} · {pool.code}</small></span>
                  <StatusBadge status={pool.status} />
                </button>
              )) : <div className="table-empty">暂无账号池</div>}
            </div>
            <AccountForm />
            <AccountList />
          </div>
        </section>
      </section>
    );
  }

  function renderLeases() {
    return (
      <section className="test-data-layout">
        <form className="panel" onSubmit={onAcquireLease}>
          <div className="panel-header">
            <div>
              <div className="panel-title">申请租借</div>
              <div className="panel-desc">冲突和权限错误会展示 traceId</div>
            </div>
            <button className="btn btn-primary btn-sm" type="submit" disabled={!canLease || leaseState.loading}>
              <Play size={15} />
              申请
            </button>
          </div>
          <div className="panel-body">
            <div className="form-grid">
              <Field label="projectId"><input value={leaseDraft.projectId} onChange={(event) => setLeaseDraftValue('projectId', event.target.value)} /></Field>
              <Field label="poolId"><input value={leaseDraft.poolId || selectedPoolId} onChange={(event) => setLeaseDraftValue('poolId', event.target.value)} /></Field>
              <Field label="holderType"><input value={leaseDraft.holderType} onChange={(event) => setLeaseDraftValue('holderType', event.target.value)} /></Field>
              <Field label="holderRef"><input value={leaseDraft.holderRef} onChange={(event) => setLeaseDraftValue('holderRef', event.target.value)} /></Field>
              <Field label="requestKey"><input value={leaseDraft.requestKey} onChange={(event) => setLeaseDraftValue('requestKey', event.target.value)} /></Field>
              <Field label="ttlSeconds"><input type="number" min={1} max={86400} value={leaseDraft.ttlSeconds} onChange={(event) => setLeaseDraftValue('ttlSeconds', Number(event.target.value))} /></Field>
              <Field label="applicationId"><input value={leaseDraft.applicationId} onChange={(event) => setLeaseDraftValue('applicationId', event.target.value)} /></Field>
              <Field label="environmentId"><input value={leaseDraft.environmentId} onChange={(event) => setLeaseDraftValue('environmentId', event.target.value)} /></Field>
            </div>
            <Field label="roleTags"><input value={leaseDraft.roleTagsText} onChange={(event) => setLeaseDraftValue('roleTagsText', event.target.value)} /></Field>
            <StateLine state={leaseState} />
          </div>
        </form>

        <section className="panel">
          <div className="panel-header">
            <div>
              <div className="panel-title">租借记录</div>
              <div className="panel-desc">{leases.length} 条 · token 仅显示 digest</div>
            </div>
            <div className="test-data-panel-actions">
              <button className="btn btn-secondary btn-sm" type="button" onClick={() => void onRenewLease()} disabled={!selectedLeaseId || !canLease || leaseState.loading}>
                <RotateCcw size={15} />
                续租
              </button>
              <button className="btn btn-ghost btn-sm" type="button" onClick={() => void onReleaseLease()} disabled={!selectedLeaseId || !canLease || leaseState.loading}>
                <CheckCircle2 size={15} />
                释放
              </button>
            </div>
          </div>
          <div className="panel-body compact">
            <div className="form-grid">
              <Field label="续租 TTL"><input type="number" min={1} max={86400} value={leaseDraft.renewTtlSeconds} onChange={(event) => setLeaseDraftValue('renewTtlSeconds', Number(event.target.value))} /></Field>
              <Field label="释放账号状态">
                <select value={leaseDraft.accountStatus} onChange={(event) => setLeaseDraftValue('accountStatus', event.target.value)}>
                  <option value="AVAILABLE">AVAILABLE</option>
                  <option value="LOCKED">LOCKED</option>
                  <option value="DISABLED">DISABLED</option>
                </select>
              </Field>
            </div>
            <Field label="释放原因"><input value={leaseDraft.releaseReason} onChange={(event) => setLeaseDraftValue('releaseReason', event.target.value)} /></Field>
            <div className="test-data-list">
              {leases.length ? leases.map((lease) => (
                <button key={lease.id} className={selectedLeaseId === lease.id ? 'test-data-list-item active' : 'test-data-list-item'} type="button" onClick={() => setSelectedLeaseId(lease.id)}>
                  <span><strong>{shortId(lease.id)}</strong><small>{lease.projectId} · {lease.holderRef} · {formatDateTime(lease.expiresAt)}</small></span>
                  <StatusBadge status={lease.status} />
                </button>
              )) : <div className="table-empty">暂无租借记录</div>}
            </div>
            {leaseDetail && (
              <div className="test-data-summary">
                <SummaryChip label="leaseTokenDigest" value={shortId(leaseDetail.leaseTokenDigest)} />
                <SummaryChip label="account" value={leaseDetail.account?.accountKey ?? shortId(leaseDetail.accountId)} />
                <SummaryChip label="secretDigest" value={shortId(leaseDetail.account?.secretRefDigest)} />
                <SummaryChip label="released" value={formatDateTime(leaseDetail.releasedAt)} />
              </div>
            )}
            <LeaseExportPanel />
          </div>
        </section>
      </section>
    );
  }

  function renderTasks() {
    return (
      <section className="test-data-layout">
        <form className="panel" onSubmit={onCreateTask}>
          <div className="panel-header">
            <div>
              <div className="panel-title">清理任务</div>
              <div className="panel-desc">{health?.cleanupEnabled ? '清理执行已启用' : '清理执行关闭时只记录控制面任务'}</div>
            </div>
            <button className="btn btn-primary btn-sm" type="submit" disabled={!canCleanup || taskState.loading}>
              <ListChecks size={15} />
              创建
            </button>
          </div>
          <div className="panel-body">
            {!health?.cleanupEnabled && (
              <div className="notice warning">cleanupEnabled=false，当前环境不会执行破坏性清理动作。</div>
            )}
            <div className="form-grid">
              <Field label="projectId"><input value={taskDraft.projectId} onChange={(event) => setTaskDraftValue('projectId', event.target.value)} /></Field>
              <Field label="dataSetId"><input value={taskDraft.dataSetId || selectedDataSetId} onChange={(event) => setTaskDraftValue('dataSetId', event.target.value)} /></Field>
              <Field label="taskType">
                <select value={taskDraft.taskType} onChange={(event) => setTaskDraftValue('taskType', event.target.value)}>
                  <option value="PREPARE">PREPARE</option>
                  <option value="REFRESH">REFRESH</option>
                  <option value="CLEANUP">CLEANUP</option>
                  <option value="ROLLBACK">ROLLBACK</option>
                </select>
              </Field>
              <Field label="requestKey"><input value={taskDraft.requestKey} onChange={(event) => setTaskDraftValue('requestKey', event.target.value)} /></Field>
            </div>
            <Field label="targetRef"><input value={taskDraft.targetRef} onChange={(event) => setTaskDraftValue('targetRef', event.target.value)} /></Field>
            <Field label="resultSummary JSON"><textarea value={taskDraft.resultSummaryText} onChange={(event) => setTaskDraftValue('resultSummaryText', event.target.value)} /></Field>
            <StateLine state={taskState} />
          </div>
        </form>

        <section className="panel">
          <div className="panel-header">
            <div>
              <div className="panel-title">任务列表</div>
              <div className="panel-desc">{tasks.length} 条 · 失败摘要可见</div>
            </div>
            <button className="btn btn-secondary btn-sm" type="button" onClick={() => void onRetryTask()} disabled={!selectedTaskId || !canCleanup || taskState.loading}>
              <RotateCcw size={15} />
              重试
            </button>
          </div>
          <div className="panel-body compact">
            <Field label="retry requestKey"><input value={taskDraft.retryRequestKey} onChange={(event) => setTaskDraftValue('retryRequestKey', event.target.value)} /></Field>
            <div className="test-data-list">
              {tasks.length ? tasks.map((task) => (
                <button key={task.id} className={selectedTaskId === task.id ? 'test-data-list-item active' : 'test-data-list-item'} type="button" onClick={() => setSelectedTaskId(task.id)}>
                  <span><strong>{task.taskType}</strong><small>{task.projectId} · {task.requestKey} · trace {task.traceId ?? '-'}</small></span>
                  <StatusBadge status={task.status} />
                </button>
              )) : <div className="table-empty">暂无清理任务</div>}
            </div>
            {taskDetail && (
              <div className="test-data-summary">
                <SummaryChip label="attempt" value={String(taskDetail.attempt)} />
                <SummaryChip label="error" value={taskDetail.errorCode ?? '-'} />
                <SummaryChip label="traceId" value={taskDetail.traceId ?? '-'} />
                <SummaryChip label="finished" value={formatDateTime(taskDetail.finishedAt)} />
              </div>
            )}
            {taskDetail?.errorSummary && <div className="document-state-line error">{taskDetail.errorSummary}</div>}
          </div>
        </section>
      </section>
    );
  }

  function RecordImportForm() {
    return (
      <form className="test-data-subform" onSubmit={onImportRecord}>
        <div className="test-data-subheader">
          <strong>导入记录摘要</strong>
          <button className="btn btn-secondary btn-sm" type="submit" disabled={!selectedDataSetId || !canManage || dataSetState.loading}>
            <Upload size={15} />
            导入
          </button>
        </div>
        <div className="form-grid">
          <Field label="recordKey"><input value={recordDraft.recordKey} onChange={(event) => setRecordDraftValue('recordKey', event.target.value)} /></Field>
          <Field label="recordDigest"><input value={recordDraft.recordDigest} onChange={(event) => setRecordDraftValue('recordDigest', event.target.value)} /></Field>
          <Field label="externalRefDigest"><input value={recordDraft.externalRefDigest} onChange={(event) => setRecordDraftValue('externalRefDigest', event.target.value)} /></Field>
          <Field label="tags"><input value={recordDraft.tagsText} onChange={(event) => setRecordDraftValue('tagsText', event.target.value)} /></Field>
        </div>
        <Field label="maskedSummary JSON"><textarea value={recordDraft.maskedSummaryText} onChange={(event) => setRecordDraftValue('maskedSummaryText', event.target.value)} /></Field>
      </form>
    );
  }

  function RecordGenerateForm() {
    const generatedSource = dataSetDetail?.sourceType === 'GENERATED';
    return (
      <form className="test-data-subform" onSubmit={onGenerateRecords}>
        <div className="test-data-subheader">
          <strong>生成模拟记录</strong>
          <button className="btn btn-secondary btn-sm" type="submit" disabled={!selectedDataSetId || !generatedSource || !canManage || dataSetState.loading}>
            <Sparkles size={15} />
            自动造数
          </button>
        </div>
        {!generatedSource && (
          <div className="table-empty">仅 `GENERATED` 数据集支持自动造数；其他来源类型继续使用导入记录摘要。</div>
        )}
        <div className="form-grid">
          <Field label="count">
            <input
              type="number"
              min={1}
              max={200}
              value={generateDraft.count}
              onChange={(event) => setGenerateDraftValue('count', Number(event.target.value))}
            />
          </Field>
          <Field label="recordKeyPrefix">
            <input
              value={generateDraft.recordKeyPrefix}
              onChange={(event) => setGenerateDraftValue('recordKeyPrefix', event.target.value)}
              placeholder={dataSetDetail ? `${dataSetDetail.code}:gen` : 'dataset:gen'}
            />
          </Field>
          <Field label="tags">
            <input value={generateDraft.tagsText} onChange={(event) => setGenerateDraftValue('tagsText', event.target.value)} />
          </Field>
        </div>
      </form>
    );
  }

  function RecordList() {
    return (
      <div className="test-data-subform">
        <div className="test-data-subheader">
          <strong>记录摘要</strong>
          <span>{dataSetDetail?.records.length ?? 0} 条</span>
        </div>
        <div className="test-data-record-grid">
          {dataSetDetail?.records.length ? dataSetDetail.records.slice(0, 8).map((record) => (
            <div className="test-data-record-card" key={record.id}>
              <strong>{record.recordKey}</strong>
              <span className="mono">{shortId(record.recordDigest)}</span>
              <small>{summaryText(record.maskedSummary)}</small>
            </div>
          )) : <div className="table-empty">暂无记录摘要</div>}
        </div>
      </div>
    );
  }

  function DataSetExportPanel() {
    return (
      <div className="test-data-subform" data-testid="test-data-export-panel">
        <div className="test-data-subheader">
          <strong>脱敏导出摘要</strong>
          <div className="test-data-panel-actions">
            <button
              className="btn btn-secondary btn-sm"
              type="button"
              onClick={() => void onExportDataSet()}
              disabled={!selectedDataSetId || !canExport || !health?.exportEnabled || dataSetExportState.loading}
            >
              <Download size={15} />
              导出摘要
            </button>
            <button
              className="btn btn-ghost btn-sm"
              type="button"
              onClick={() => void onDownloadDataSetExport()}
              disabled={!selectedDataSetId || !canExport || !health?.exportEnabled || dataSetExportState.loading}
            >
              <Download size={15} />
              下载文件
            </button>
          </div>
        </div>
        <StateLine state={dataSetExportState} />
        {dataSetExport ? (
          <>
            <div className="test-data-summary">
              <SummaryChip label="schema" value={dataSetExport.schemaVersion} />
              <SummaryChip label="records" value={String(dataSetExport.recordCount)} />
              <SummaryChip label="fields" value={String(dataSetExport.schemaFieldCount)} />
              <SummaryChip label="sensitive" value={String(dataSetExport.sensitiveFieldCount)} />
              <SummaryChip label="exported" value={formatDateTime(dataSetExport.exportedAt)} />
            </div>
            <div className="test-data-summary">
              {Object.entries(dataSetExport.redactionPolicy).map(([key, value]) => (
                <SummaryChip key={key} label={key} value={String(value)} />
              ))}
            </div>
            <div className="test-data-record-grid test-data-export-grid">
              {dataSetExport.records.length ? dataSetExport.records.slice(0, 8).map((record) => (
                <div className="test-data-record-card" key={`${record.recordKey}-${record.recordDigest}`}>
                  <strong>{record.recordKey}</strong>
                  <span className="mono">{shortId(record.recordDigest)}</span>
                  <small>keys {record.maskedSummaryKeys.join(', ') || '-'}</small>
                  <small>tags {record.tags.join(', ') || '-'}</small>
                </div>
              )) : <div className="table-empty">暂无可导出记录</div>}
            </div>
          </>
        ) : (
          <div className="table-empty">{health?.exportEnabled ? '尚未生成导出摘要' : '当前环境关闭脱敏导出'}</div>
        )}
      </div>
    );
  }

  function LeaseExportPanel() {
    return (
      <div className="test-data-subform" data-testid="test-lease-export-panel">
        <div className="test-data-subheader">
          <strong>租借脱敏导出摘要</strong>
          <div className="test-data-panel-actions">
            <button
              className="btn btn-secondary btn-sm"
              type="button"
              onClick={() => void onExportLease()}
              disabled={!selectedLeaseId || !canExport || !health?.exportEnabled || leaseExportState.loading}
            >
              <Download size={15} />
              导出摘要
            </button>
            <button
              className="btn btn-ghost btn-sm"
              type="button"
              onClick={() => void onDownloadLeaseExport()}
              disabled={!selectedLeaseId || !canExport || !health?.exportEnabled || leaseExportState.loading}
            >
              <Download size={15} />
              下载文件
            </button>
          </div>
        </div>
        <StateLine state={leaseExportState} />
        {leaseExport ? (
          <>
            <div className="test-data-summary">
              <SummaryChip label="schema" value={leaseExport.schemaVersion} />
              <SummaryChip label="status" value={leaseExport.lease.status} />
              <SummaryChip label="holder" value={leaseExport.lease.holderRef} />
              <SummaryChip label="account" value={leaseExport.account.accountKey} />
              <SummaryChip label="exported" value={formatDateTime(leaseExport.exportedAt)} />
            </div>
            <div className="test-data-summary">
              <SummaryChip label="leaseTokenDigest" value={shortId(leaseExport.lease.leaseTokenDigest)} />
              <SummaryChip label="requestDigest" value={shortId(leaseExport.lease.requestDigest)} />
              <SummaryChip label="secretDigest" value={shortId(leaseExport.account.secretRefDigest)} />
              <SummaryChip label="releaseReasonDigest" value={shortId(leaseExport.lease.releaseReasonDigest)} />
            </div>
            <div className="test-data-summary">
              <SummaryChip label="scopeKeys" value={leaseExport.account.scopeSummaryKeys.join(', ') || '-'} />
              <SummaryChip label="leasePolicyKeys" value={leaseExport.pool.leasePolicyKeys.join(', ') || '-'} />
              <SummaryChip label="healthSummary" value={leaseExport.account.lastHealthSummaryPresent ? 'digest only' : '-'} />
            </div>
            <div className="test-data-summary">
              {Object.entries(leaseExport.redactionPolicy).map(([key, value]) => (
                <SummaryChip key={key} label={key} value={String(value)} />
              ))}
            </div>
          </>
        ) : (
          <div className="table-empty">{health?.exportEnabled ? '尚未生成租借导出摘要' : '当前环境关闭脱敏导出'}</div>
        )}
      </div>
    );
  }

  function AccountForm() {
    const selectedAccount = poolDetail?.accounts.find((account) => account.id === selectedAccountId);
    return (
      <form className="test-data-subform" onSubmit={onSaveAccount}>
        <div className="test-data-subheader">
          <strong>{selectedAccount ? '编辑账号摘要' : '新增账号摘要'}</strong>
          <div className="test-data-panel-actions">
            <button className="btn btn-ghost btn-sm" type="button" onClick={() => {
              setSelectedAccountId('');
              setAccountDraft(initialAccountDraft);
            }}>
              <RefreshCw size={15} />
              新增
            </button>
            <button className="btn btn-secondary btn-sm" type="submit" disabled={!selectedPoolId || !canManage || poolState.loading}>
              <ShieldCheck size={15} />
              保存账号
            </button>
          </div>
        </div>
        <div className="form-grid">
          <Field label="accountKey"><input value={accountDraft.accountKey} onChange={(event) => setAccountDraftValue('accountKey', event.target.value)} /></Field>
          <Field label="displayName"><input value={accountDraft.displayName} onChange={(event) => setAccountDraftValue('displayName', event.target.value)} /></Field>
          <Field label="状态">
            <select value={accountDraft.status} onChange={(event) => setAccountDraftValue('status', event.target.value)}>
              <option value="AVAILABLE">AVAILABLE</option>
              <option value="LEASED">LEASED</option>
              <option value="LOCKED">LOCKED</option>
              <option value="DISABLED">DISABLED</option>
            </select>
          </Field>
          <Field label="roleTags"><input value={accountDraft.roleTagsText} onChange={(event) => setAccountDraftValue('roleTagsText', event.target.value)} /></Field>
          <Field label="lastHealthStatus"><input value={accountDraft.lastHealthStatus} onChange={(event) => setAccountDraftValue('lastHealthStatus', event.target.value)} /></Field>
          <Field label="secretRef">
            <input
              type="password"
              autoComplete="off"
              value={accountDraft.secretRef}
              onChange={(event) => setAccountDraftValue('secretRef', event.target.value)}
              placeholder={selectedAccount ? '留空则不替换' : '输入凭据引用'}
            />
          </Field>
        </div>
        <Field label="lastHealthSummary"><input value={accountDraft.lastHealthSummary} onChange={(event) => setAccountDraftValue('lastHealthSummary', event.target.value)} /></Field>
        <Field label="scopeSummary JSON"><textarea value={accountDraft.scopeSummaryText} onChange={(event) => setAccountDraftValue('scopeSummaryText', event.target.value)} /></Field>
      </form>
    );
  }

  function AccountList() {
    return (
      <div className="test-data-record-grid">
        {poolDetail?.accounts.length ? poolDetail.accounts.map((account) => (
          <button
            className={selectedAccountId === account.id ? 'test-data-account-card active' : 'test-data-account-card'}
            key={account.id}
            type="button"
            onClick={() => {
              setSelectedAccountId(account.id);
              setAccountDraft(accountDraftFromAccount(account));
            }}
          >
            <span>
              <strong>{account.displayName || account.accountKey}</strong>
              <small>{account.roleTags.join(', ') || 'no roles'}</small>
            </span>
            <StatusBadge status={account.status} />
            <small className="mono">secret digest {shortId(account.secretRefDigest)}</small>
            <small>{summaryText(account.scopeSummary)}</small>
          </button>
        )) : <div className="table-empty">暂无账号摘要</div>}
      </div>
    );
  }

  function setDataSetDraftValue<K extends keyof DataSetDraft>(key: K, value: DataSetDraft[K]) {
    setDataSetDraft((current) => ({ ...current, [key]: value }));
    setDataSetState({ loading: false });
  }

  function setRecordDraftValue<K extends keyof RecordDraft>(key: K, value: RecordDraft[K]) {
    setRecordDraft((current) => ({ ...current, [key]: value }));
    setDataSetState({ loading: false });
  }

  function setGenerateDraftValue<K extends keyof GenerateDraft>(key: K, value: GenerateDraft[K]) {
    setGenerateDraft((current) => ({ ...current, [key]: value }));
    setDataSetState({ loading: false });
  }

  function setPoolDraftValue<K extends keyof PoolDraft>(key: K, value: PoolDraft[K]) {
    setPoolDraft((current) => ({ ...current, [key]: value }));
    setPoolState({ loading: false });
  }

  function setAccountDraftValue<K extends keyof AccountDraft>(key: K, value: AccountDraft[K]) {
    setAccountDraft((current) => ({ ...current, [key]: value }));
    setPoolState({ loading: false });
  }

  function setLeaseDraftValue<K extends keyof LeaseDraft>(key: K, value: LeaseDraft[K]) {
    setLeaseDraft((current) => ({ ...current, [key]: value }));
    setLeaseState({ loading: false });
  }

  function setTaskDraftValue<K extends keyof TaskDraft>(key: K, value: TaskDraft[K]) {
    setTaskDraft((current) => ({ ...current, [key]: value }));
    setTaskState({ loading: false });
  }
}

function MetricCard(props: { label: string; value: string; icon: ReactNode }) {
  return (
    <div className="metric-card">
      <div className="metric-icon">{props.icon}</div>
      <div className="metric-body">
        <span className="metric-value">{props.value}</span>
        <span className="metric-label">{props.label}</span>
      </div>
    </div>
  );
}

function PolicyItem(props: { label: string; value: string }) {
  return (
    <div className="test-data-policy-item">
      <span>{props.label}</span>
      <strong>{props.value}</strong>
    </div>
  );
}

function Field(props: { label: string; children: ReactNode }) {
  return (
    <label className="field">
      <span className="field-label">{props.label}</span>
      {props.children}
    </label>
  );
}

function StateLine(props: { state: WorkState }) {
  if (props.state.error) {
    return <span className="document-state-line error">{props.state.error}{props.state.traceId ? ` · ${props.state.traceId}` : ''}</span>;
  }
  if (props.state.success) {
    return <span className="document-state-line success">{props.state.success}{props.state.traceId ? ` · ${props.state.traceId}` : ''}</span>;
  }
  if (props.state.traceId) {
    return <span className="document-state-line">Trace ID：{props.state.traceId}</span>;
  }
  return null;
}

function StatusSelect(props: { value: string; onChange: (value: string) => void }) {
  return (
    <select value={props.value} onChange={(event) => props.onChange(event.target.value)}>
      <option value="DRAFT">DRAFT</option>
      <option value="READY">READY</option>
      <option value="DISABLED">DISABLED</option>
      <option value="ARCHIVED">ARCHIVED</option>
    </select>
  );
}

function StatusBadge(props: { status: string }) {
  const status = props.status || 'UNKNOWN';
  const tone = ['FAILED', 'BLOCKED', 'DISABLED', 'ARCHIVED', 'EXPIRED'].includes(status) ? 'danger'
    : ['ACTIVE', 'AVAILABLE', 'SUCCEEDED', 'READY'].includes(status) ? 'success'
      : ['LEASED', 'RUNNING', 'PENDING', 'LOCKED', 'CLEANUP', 'ROLLBACK'].includes(status) ? 'warning'
        : 'neutral';
  return <span className={`status-badge ${tone}`}>{status}</span>;
}

function PrimaryText(props: { title: string; subtitle?: string }) {
  return (
    <span className="test-data-primary-text">
      <strong>{props.title}</strong>
      <small>{props.subtitle ?? '-'}</small>
    </span>
  );
}

function SummaryChip(props: { label: string; value: string }) {
  return <span><strong>{props.label}</strong>{props.value}</span>;
}

function dataSetDraftFromDetail(detail: TestDataSetDetail): DataSetDraft {
  return {
    projectId: detail.projectId,
    applicationId: detail.applicationId ?? '',
    environmentId: detail.environmentId ?? '',
    code: detail.code,
    name: detail.name,
    status: detail.status,
    sensitivityLevel: detail.sensitivityLevel,
    sourceType: detail.sourceType,
    sourceRefDigest: detail.sourceRefDigest ?? '',
    schemaText: jsonText(detail.schema),
    cleanupPolicyText: jsonText(detail.cleanupPolicy)
  };
}

function poolDraftFromDetail(detail: TestAccountPoolDetail): PoolDraft {
  return {
    projectId: detail.projectId,
    applicationId: detail.applicationId ?? '',
    environmentId: detail.environmentId ?? '',
    code: detail.code,
    name: detail.name,
    status: detail.status,
    defaultTtlSeconds: detail.defaultTtlSeconds,
    leasePolicyText: jsonText(detail.leasePolicy)
  };
}

function accountDraftFromAccount(account: TestPooledAccount): AccountDraft {
  return {
    accountKey: account.accountKey,
    displayName: account.displayName ?? '',
    status: account.status,
    roleTagsText: account.roleTags.join(', '),
    scopeSummaryText: jsonText(account.scopeSummary),
    secretRef: '',
    lastHealthStatus: account.lastHealthStatus ?? '',
    lastHealthSummary: account.lastHealthSummary ?? ''
  };
}

function parseJsonFields(fields: Array<[string, string]>): { ok: true; values: Record<string, Record<string, unknown>> } | { ok: false; message: string } {
  const values: Record<string, Record<string, unknown>> = {};
  for (const [key, text] of fields) {
    try {
      const parsed = text.trim() ? JSON.parse(text) : {};
      values[key] = parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed as Record<string, unknown> : {};
    } catch {
      return { ok: false, message: `${key} 必须是合法 JSON object` };
    }
  }
  return { ok: true, values };
}

function errorState(error: unknown, fallback: string): WorkState {
  if (error instanceof ApiError) {
    return { loading: false, error: error.message || fallback, traceId: error.traceId };
  }
  return { loading: false, error: error instanceof Error ? error.message : fallback };
}

function optionalText(value: string) {
  const trimmed = value.trim();
  return trimmed ? trimmed : undefined;
}

function splitList(value: string) {
  return value.split(',').map((item) => item.trim()).filter(Boolean);
}

function jsonText(value: Record<string, unknown>) {
  return JSON.stringify(value ?? {}, null, 2);
}

function summaryText(value: Record<string, unknown>) {
  const entries = Object.entries(value).slice(0, 4);
  if (!entries.length) return 'summary empty';
  return entries.map(([key, item]) => `${key}=${String(item)}`).join(' · ');
}

function shortId(value?: string) {
  return value ? value.slice(0, 10) : '-';
}

function formatDateTime(value?: string) {
  return value ? value.replace('T', ' ').replace('Z', '') : '-';
}

function triggerBrowserDownload(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = filename;
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  URL.revokeObjectURL(url);
}
