import {
  CheckCircle2,
  ClipboardCheck,
  Eye,
  FileText,
  Link2,
  RefreshCw,
  Save,
  Search,
  Send,
  Sparkles,
  XCircle
} from 'lucide-react';
import { useCallback, useEffect, useMemo, useState, type FormEvent, type ReactNode } from 'react';
import type { CurrentUser } from '../api/auth';
import { fetchAssetRequirements, type AssetRequirementView } from '../api/assets';
import {
  TEST_DESIGN_CANDIDATE_STATUSES,
  TEST_DESIGN_COVERAGE_TYPES,
  confirmTestDesignCandidate,
  createTestDesignTask,
  fetchTestDesignHealth,
  fetchTestDesignTask,
  fetchTestDesignTasks,
  ignoreTestDesignCandidate,
  publishTestDesignDryRun,
  publishTestDesignTask,
  rejectTestDesignCandidate,
  testDesignErrorMessage,
  updateTestDesignCandidate,
  type TestDesignCandidateView,
  type TestDesignHealth,
  type TestDesignPublishRecordView,
  type TestDesignPublishResult,
  type TestDesignTaskView
} from '../api/testDesign';
import { canUseButton, hasPermission } from '../permissions';
import {
  validateTestDesignCandidateDraft,
  type TestDesignCandidateDraftQualityIssue
} from '../testDesignQuality';

type WorkState = {
  loading: boolean;
  error?: string;
  success?: string;
  traceId?: string;
};

type RequirementFilters = {
  projectId: string;
  status: string;
  keyword: string;
};

type TaskFilters = {
  projectId: string;
  status: string;
  keyword: string;
};

type CandidateFilters = {
  status: string;
  coverageType: string;
  keyword: string;
};

type GenerationDraft = {
  projectId: string;
  title: string;
  caseCountPerRequirement: string;
  coverageTypes: string[];
};

type CandidateDraft = {
  title: string;
  description: string;
  apiId: string;
  coverageType: string;
  priority: string;
  preconditions: string;
  steps: string;
  expectedResult: string;
  tags: string;
};

const initialFilters: RequirementFilters = {
  projectId: '',
  status: 'APPROVED',
  keyword: ''
};

const initialTaskFilters: TaskFilters = {
  projectId: '',
  status: '',
  keyword: ''
};

const initialCandidateFilters: CandidateFilters = {
  status: '',
  coverageType: '',
  keyword: ''
};

const initialGenerationDraft: GenerationDraft = {
  projectId: '',
  title: '',
  caseCountPerRequirement: '2',
  coverageTypes: ['SMOKE', 'FUNCTIONAL', 'EXCEPTION']
};

export function TestDesignWorkbench(props: { signedIn: boolean; currentUser: CurrentUser | null }) {
  const canRead = hasPermission(props.currentUser, 'testDesign:read');
  const canGenerate = canUseButton(props.currentUser, 'testDesign:generate');
  const canReview = canUseButton(props.currentUser, 'testDesign:review');
  const canPublish = canUseButton(props.currentUser, 'testDesign:publish');

  const [health, setHealth] = useState<TestDesignHealth | null>(null);
  const [requirements, setRequirements] = useState<AssetRequirementView[]>([]);
  const [selectedRequirementIds, setSelectedRequirementIds] = useState<string[]>([]);
  const [tasks, setTasks] = useState<TestDesignTaskView[]>([]);
  const [selectedTaskId, setSelectedTaskId] = useState('');
  const [candidates, setCandidates] = useState<TestDesignCandidateView[]>([]);
  const [selectedCandidateId, setSelectedCandidateId] = useState('');
  const [candidateDraft, setCandidateDraft] = useState<CandidateDraft | null>(null);
  const [filters, setFilters] = useState<RequirementFilters>(initialFilters);
  const [taskFilters, setTaskFilters] = useState<TaskFilters>(initialTaskFilters);
  const [candidateFilters, setCandidateFilters] = useState<CandidateFilters>(initialCandidateFilters);
  const [generationDraft, setGenerationDraft] = useState<GenerationDraft>(initialGenerationDraft);
  const [reviewComment, setReviewComment] = useState('');
  const [publishResult, setPublishResult] = useState<TestDesignPublishResult | null>(null);
  const [selectedPublishCandidateIds, setSelectedPublishCandidateIds] = useState<string[]>([]);
  const [loadState, setLoadState] = useState<WorkState>({ loading: false });
  const [taskState, setTaskState] = useState<WorkState>({ loading: false });
  const [mutationState, setMutationState] = useState<WorkState>({ loading: false });
  const [publishState, setPublishState] = useState<WorkState>({ loading: false });

  const disabled = !props.signedIn || !canRead;
  const selectedTask = tasks.find((task) => task.id === selectedTaskId) ?? null;
  const selectedCandidate = candidates.find((candidate) => candidate.id === selectedCandidateId) ?? null;
  const filteredRequirements = useMemo(() => filterRequirements(requirements, filters), [requirements, filters]);
  const visibleCandidates = useMemo(() => filterCandidates(candidates, candidateFilters), [candidateFilters, candidates]);
  const publishableCandidates = useMemo(
    () => candidates.filter(canPublishCandidate),
    [candidates]
  );
  const selectedPublishableCandidates = useMemo(
    () => publishableCandidates.filter((candidate) => selectedPublishCandidateIds.includes(candidate.id)),
    [publishableCandidates, selectedPublishCandidateIds]
  );
  const publishTargetCandidates = selectedPublishableCandidates.length ? selectedPublishableCandidates : publishableCandidates;
  const statusCounts = useMemo(() => countByStatus(candidates), [candidates]);
  const publishIssueRecords = useMemo(
    () => publishResult?.records.filter(isPublishIssueRecord) ?? [],
    [publishResult]
  );
  const candidateQualityIssues = useMemo(
    () => candidateDraft && selectedCandidate
      ? validateTestDesignCandidateDraft(candidateDraft, {
        currentCandidateId: selectedCandidate.id,
        currentRequirementId: selectedCandidate.requirementId,
        peerCandidates: candidates
      })
      : [],
    [candidateDraft, candidates, selectedCandidate]
  );
  const candidateSaveBlocked = candidateQualityIssues.length > 0;
  const selectedRequirementTitles = useMemo(() => {
    const lookup = new Map(requirements.map((requirement) => [requirement.id, requirement.title]));
    return selectedRequirementIds.map((id) => lookup.get(id) ?? id);
  }, [requirements, selectedRequirementIds]);

  const refreshTaskDetail = useCallback(async (taskId: string) => {
    if (!props.signedIn || !canRead || !taskId) {
      setCandidates([]);
      setSelectedCandidateId('');
      setCandidateDraft(null);
      setTaskState({ loading: false });
      return;
    }

    setTaskState({ loading: true });
    setCandidates([]);
    setSelectedCandidateId('');
    setSelectedPublishCandidateIds([]);
    setCandidateDraft(null);
    setPublishResult(null);
    try {
      const response = await fetchTestDesignTask(taskId);
      const detail = response.data;
      setTasks((current) => upsertTask(current, detail.task));
      setCandidates(detail.candidates);
      setSelectedPublishCandidateIds((current) => current.filter((id) => detail.candidates.some((candidate) => candidate.id === id && canPublishCandidate(candidate))));
      setSelectedCandidateId((current) => {
        if (current && detail.candidates.some((candidate) => candidate.id === current)) {
          return current;
        }
        return detail.candidates[0]?.id ?? '';
      });
      setTaskState({ loading: false, traceId: response.trace_id });
    } catch (error: unknown) {
      setCandidates([]);
      setSelectedCandidateId('');
      setCandidateDraft(null);
      setTaskState({ loading: false, error: testDesignErrorMessage(error, '生成任务详情加载失败') });
    }
  }, [canRead, props.signedIn]);

  const refreshAll = useCallback(async () => {
    if (!props.signedIn || !canRead) {
      setHealth(null);
      setRequirements([]);
      setTasks([]);
      setCandidates([]);
      setSelectedRequirementIds([]);
      setSelectedTaskId('');
      setSelectedCandidateId('');
      setSelectedPublishCandidateIds([]);
      setLoadState({ loading: false });
      setTaskState({ loading: false });
      return;
    }

    setLoadState({ loading: true });
    const [healthResult, requirementResult, taskResult] = await Promise.allSettled([
      fetchTestDesignHealth(),
      fetchAssetRequirements({
        size: 80,
        projectId: filters.projectId,
        status: filters.status,
        keyword: filters.keyword
      }),
      fetchTestDesignTasks({
        size: 20,
        projectId: taskFilters.projectId || filters.projectId,
        status: taskFilters.status,
        keyword: taskFilters.keyword
      })
    ]);

    const errors: string[] = [];
    const traceIds: string[] = [];
    if (healthResult.status === 'fulfilled') {
      setHealth(healthResult.value.data);
      traceIds.push(healthResult.value.trace_id);
    } else {
      errors.push(testDesignErrorMessage(healthResult.reason, '用例生成服务健康检查失败'));
    }

    if (requirementResult.status === 'fulfilled') {
      setRequirements(requirementResult.value.data.items);
      traceIds.push(requirementResult.value.trace_id);
    } else {
      setRequirements([]);
      errors.push(testDesignErrorMessage(requirementResult.reason, '需求列表加载失败'));
    }

    if (taskResult.status === 'fulfilled') {
      setTasks(taskResult.value.data.items);
      traceIds.push(taskResult.value.trace_id);
      setSelectedTaskId((current) => taskResult.value.data.items.some((task) => task.id === current) ? current : taskResult.value.data.items[0]?.id || '');
    } else {
      setTasks([]);
      errors.push(testDesignErrorMessage(taskResult.reason, '生成任务列表加载失败'));
    }

    setLoadState({
      loading: false,
      error: errors.length ? errors.join('；') : undefined,
      traceId: traceIds.find(Boolean)
    });
  }, [canRead, filters.keyword, filters.projectId, filters.status, props.signedIn, taskFilters.keyword, taskFilters.projectId, taskFilters.status]);

  useEffect(() => {
    void refreshAll();
  }, [refreshAll]);

  useEffect(() => {
    void refreshTaskDetail(selectedTaskId);
  }, [refreshTaskDetail, selectedTaskId]);

  useEffect(() => {
    const nextCandidate = candidates.find((candidate) => candidate.id === selectedCandidateId) ?? null;
    setCandidateDraft(nextCandidate ? draftFromCandidate(nextCandidate) : null);
    setReviewComment(nextCandidate?.reviewComment ?? nextCandidate?.rejectedReason ?? nextCandidate?.ignoredReason ?? '');
  }, [candidates, selectedCandidateId]);

  useEffect(() => {
    if (!generationDraft.projectId && filters.projectId) {
      setGenerationDraft((current) => ({ ...current, projectId: filters.projectId }));
    }
  }, [filters.projectId, generationDraft.projectId]);

  useEffect(() => {
    if (!taskFilters.projectId && filters.projectId) {
      setTaskFilters((current) => ({ ...current, projectId: filters.projectId }));
    }
  }, [filters.projectId, taskFilters.projectId]);

  useEffect(() => {
    if (selectedCandidateId && visibleCandidates.some((candidate) => candidate.id === selectedCandidateId)) {
      return;
    }
    setSelectedCandidateId(visibleCandidates[0]?.id ?? '');
  }, [selectedCandidateId, visibleCandidates]);

  function toggleRequirement(id: string) {
    setSelectedRequirementIds((current) => {
      if (current.includes(id)) {
        return current.filter((item) => item !== id);
      }
      return [...current, id];
    });
  }

  function toggleCoverage(type: string) {
    setGenerationDraft((current) => {
      const coverageTypes = current.coverageTypes.includes(type)
        ? current.coverageTypes.filter((item) => item !== type)
        : [...current.coverageTypes, type];
      return { ...current, coverageTypes };
    });
  }

  function togglePublishCandidate(candidateId: string) {
    setSelectedPublishCandidateIds((current) => {
      if (current.includes(candidateId)) {
        return current.filter((item) => item !== candidateId);
      }
      return [...current, candidateId];
    });
  }

  function selectVisiblePublishableCandidates() {
    setSelectedPublishCandidateIds(visibleCandidates.filter(canPublishCandidate).map((candidate) => candidate.id));
  }

  async function createTask(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!props.signedIn) {
      setMutationState({ loading: false, error: '请先登录后再生成用例' });
      return;
    }
    if (!canGenerate) {
      setMutationState({ loading: false, error: '缺少 testDesign:generate 权限' });
      return;
    }
    if (!generationDraft.projectId.trim()) {
      setMutationState({ loading: false, error: '请输入项目 ID' });
      return;
    }
    if (!selectedRequirementIds.length) {
      setMutationState({ loading: false, error: '请至少选择一个需求' });
      return;
    }
    if (!generationDraft.coverageTypes.length) {
      setMutationState({ loading: false, error: '请至少选择一种覆盖类型' });
      return;
    }

    setMutationState({ loading: true });
    try {
      const response = await createTestDesignTask({
        projectId: generationDraft.projectId,
        title: generationDraft.title,
        requirementIds: selectedRequirementIds,
        coverageTypes: generationDraft.coverageTypes,
        caseCountPerRequirement: Number(generationDraft.caseCountPerRequirement) || undefined
      });
      setTasks((current) => upsertTask(current, response.data.task));
      setCandidates(response.data.candidates);
      setSelectedTaskId(response.data.task.id);
      setSelectedCandidateId(response.data.candidates[0]?.id ?? '');
      setSelectedPublishCandidateIds([]);
      setPublishResult(null);
      setMutationState({ loading: false, success: '候选用例已生成', traceId: response.trace_id });
    } catch (error: unknown) {
      setMutationState({ loading: false, error: testDesignErrorMessage(error, '候选用例生成失败') });
    }
  }

  async function saveCandidate() {
    if (!selectedCandidate || !candidateDraft) {
      return;
    }
    if (!canReview) {
      setMutationState({ loading: false, error: '缺少 testDesign:review 权限' });
      return;
    }
    if (candidateSaveBlocked) {
      setMutationState({ loading: false, error: `候选质量门禁不通过：${candidateQualityIssues[0]?.message ?? '请检查字段提示'}` });
      return;
    }

    setMutationState({ loading: true });
    try {
      const response = await updateTestDesignCandidate(selectedCandidate.id, {
        title: candidateDraft.title,
        description: candidateDraft.description,
        apiId: candidateDraft.apiId,
        coverageType: candidateDraft.coverageType,
        priority: candidateDraft.priority,
        preconditions: candidateDraft.preconditions,
        steps: stepsFromText(candidateDraft.steps),
        expectedResult: candidateDraft.expectedResult,
        tags: tagsFromText(candidateDraft.tags),
        version: selectedCandidate.version
      });
      updateCandidateInState(response.data);
      setMutationState({ loading: false, success: '候选用例已保存', traceId: response.trace_id });
    } catch (error: unknown) {
      setMutationState({ loading: false, error: testDesignErrorMessage(error, '候选用例保存失败') });
    }
  }

  async function reviewCandidate(action: 'confirm' | 'reject' | 'ignore') {
    if (!selectedCandidate) {
      return;
    }
    if (!canReview) {
      setMutationState({ loading: false, error: '缺少 testDesign:review 权限' });
      return;
    }

    setMutationState({ loading: true });
    try {
      const payload = { version: selectedCandidate.version, comment: reviewComment, reason: reviewComment };
      const response = action === 'confirm'
        ? await confirmTestDesignCandidate(selectedCandidate.id, payload)
        : action === 'reject'
          ? await rejectTestDesignCandidate(selectedCandidate.id, payload)
          : await ignoreTestDesignCandidate(selectedCandidate.id, payload);
      updateCandidateInState(response.data);
      setMutationState({ loading: false, success: reviewSuccessText(action), traceId: response.trace_id });
    } catch (error: unknown) {
      setMutationState({ loading: false, error: testDesignErrorMessage(error, '候选用例状态更新失败') });
    }
  }

  async function publishTask(dryRun: boolean) {
    if (!selectedTaskId) {
      return;
    }
    if (!canPublish) {
      setPublishState({ loading: false, error: '缺少 testDesign:publish 权限' });
      return;
    }

    setPublishState({ loading: true });
    try {
      const candidateIds = publishTargetCandidates.map((candidate) => candidate.id);
      const response = dryRun
        ? await publishTestDesignDryRun(selectedTaskId, { candidateIds })
        : await publishTestDesignTask(selectedTaskId, { candidateIds });
      setPublishResult(response.data);
      setPublishState({
        loading: false,
        success: dryRun ? '预发布检查已完成' : '已发布到资产库测试用例',
        traceId: response.trace_id
      });
      if (!dryRun) {
        await refreshTaskDetail(selectedTaskId);
      }
    } catch (error: unknown) {
      setPublishState({ loading: false, error: testDesignErrorMessage(error, dryRun ? '预发布检查失败' : '发布失败') });
    }
  }

  function updateCandidateInState(nextCandidate: TestDesignCandidateView) {
    setCandidates((current) => current.map((candidate) => (candidate.id === nextCandidate.id ? nextCandidate : candidate)));
    setSelectedCandidateId(nextCandidate.id);
  }

  return (
    <div className="module-layout">
      <div className="main-stack">
        <div className="metrics-grid">
          <Metric icon={<Sparkles size={20} />} label="服务状态" value={health?.status ?? '-'} desc={health?.generationMode ?? '未加载'} />
          <Metric icon={<FileText size={20} />} label="候选用例" value={String(candidates.length)} desc={`确认 ${statusCounts.CONFIRMED ?? 0} · 待重试 ${statusCounts.FAILED ?? 0}`} />
          <Metric icon={<ClipboardCheck size={20} />} label="已发布" value={String(selectedTask?.publishedCount ?? 0)} desc={selectedTask?.status ?? '-'} />
        </div>

        <section className="panel">
          <div className="panel-header">
            <div>
              <h2 className="panel-title">需求选择</h2>
              <p className="panel-desc">从 WP3 已入库需求中选择生成范围。</p>
            </div>
            <div className="toolbar-actions">
              <button className="btn btn-secondary btn-sm" type="button" disabled={disabled || loadState.loading} onClick={() => void refreshAll()}>
                <RefreshCw size={15} />
                刷新
              </button>
              <button className="btn btn-ghost btn-sm" type="button" disabled={disabled || loadState.loading} onClick={() => setSelectedRequirementIds(filteredRequirements.map((item) => item.id).filter(Boolean))}>
                全选
              </button>
            </div>
          </div>
          <div className="panel-body">
            <div className="asset-filter-bar">
              <label className="field">
                <span className="field-label">项目 ID</span>
                <input value={filters.projectId} onChange={(event) => setFilters((current) => ({ ...current, projectId: event.target.value }))} placeholder="project UUID" disabled={disabled} />
              </label>
              <label className="field">
                <span className="field-label">状态</span>
                <select value={filters.status} onChange={(event) => setFilters((current) => ({ ...current, status: event.target.value }))} disabled={disabled}>
                  <option value="">全部</option>
                  <option value="APPROVED">APPROVED</option>
                  <option value="REVIEWING">REVIEWING</option>
                  <option value="DRAFT">DRAFT</option>
                </select>
              </label>
              <label className="field">
                <span className="field-label">关键词</span>
                <input value={filters.keyword} onChange={(event) => setFilters((current) => ({ ...current, keyword: event.target.value }))} placeholder="标题 / 标签" disabled={disabled} />
              </label>
              <div className="filter-actions">
                <button className="btn btn-secondary btn-sm" type="button" disabled={disabled} onClick={() => setFilters(initialFilters)}>
                  <Search size={15} />
                  重置
                </button>
              </div>
            </div>

            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th style={{ width: 48 }}></th>
                    <th>需求</th>
                    <th>优先级</th>
                    <th>来源</th>
                    <th>标签</th>
                  </tr>
                </thead>
                <tbody>
                  {filteredRequirements.length ? (
                    filteredRequirements.map((requirement) => (
                      <tr className={selectedRequirementIds.includes(requirement.id) ? 'selected-row' : ''} key={requirement.id}>
                        <td>
                          <input
                            aria-label={`选择需求 ${requirement.title}`}
                            type="checkbox"
                            checked={selectedRequirementIds.includes(requirement.id)}
                            onChange={() => toggleRequirement(requirement.id)}
                            disabled={disabled || !requirement.id}
                          />
                        </td>
                        <td>
                          <strong>{requirement.title}</strong>
                          <div className="field-hint">{requirement.id}</div>
                        </td>
                        <td><span className="badge badge-neutral">{requirement.priority}</span></td>
                        <td>{requirement.sourceRef ?? requirement.source}</td>
                        <td>{requirement.tags.join(', ') || '-'}</td>
                      </tr>
                    ))
                  ) : (
                    <tr>
                      <td className="table-empty" colSpan={5}>{emptyRequirementText(props.signedIn, canRead, loadState.loading)}</td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
            <StateLine state={loadState} />
          </div>
        </section>

        <section className="panel">
          <div className="panel-header">
            <div>
              <h2 className="panel-title">候选评审</h2>
              <p className="panel-desc">编辑候选用例并确认，发布后会写入 WP3 测试用例和需求追踪关系。</p>
            </div>
            <StateLine state={taskState} />
          </div>
          <div className="panel-body">
            <div className="asset-filter-bar test-design-candidate-filter">
              <label className="field">
                <span className="field-label">候选状态</span>
                <select value={candidateFilters.status} onChange={(event) => setCandidateFilters((current) => ({ ...current, status: event.target.value }))} disabled={taskState.loading || !candidates.length}>
                  <option value="">全部</option>
                  {TEST_DESIGN_CANDIDATE_STATUSES.map((status) => <option key={status} value={status}>{status}</option>)}
                </select>
              </label>
              <label className="field">
                <span className="field-label">覆盖类型</span>
                <select value={candidateFilters.coverageType} onChange={(event) => setCandidateFilters((current) => ({ ...current, coverageType: event.target.value }))} disabled={taskState.loading || !candidates.length}>
                  <option value="">全部</option>
                  {TEST_DESIGN_COVERAGE_TYPES.map((type) => <option key={type} value={type}>{type}</option>)}
                </select>
              </label>
              <label className="field">
                <span className="field-label">关键词</span>
                <input value={candidateFilters.keyword} onChange={(event) => setCandidateFilters((current) => ({ ...current, keyword: event.target.value }))} placeholder="标题 / 标签 / 错误" disabled={taskState.loading || !candidates.length} />
              </label>
              <div className="filter-actions">
                <button className="btn btn-secondary btn-sm" type="button" disabled={!candidates.length} onClick={() => setCandidateFilters(initialCandidateFilters)}>
                  <Search size={15} />
                  重置
                </button>
                <button className="btn btn-ghost btn-sm" type="button" disabled={!visibleCandidates.some(canPublishCandidate)} onClick={selectVisiblePublishableCandidates}>
                  选中可发布
                </button>
                <button className="btn btn-ghost btn-sm" type="button" disabled={!selectedPublishCandidateIds.length} onClick={() => setSelectedPublishCandidateIds([])}>
                  清空发布
                </button>
              </div>
            </div>
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th style={{ width: 48 }}></th>
                    <th>标题</th>
                    <th>覆盖</th>
                    <th>优先级</th>
                    <th>状态</th>
                    <th>操作</th>
                  </tr>
                </thead>
                <tbody>
                  {visibleCandidates.length ? (
                    visibleCandidates.map((candidate) => (
                      <tr className={candidate.id === selectedCandidateId ? 'selected-row' : ''} key={candidate.id}>
                        <td>
                          <input
                            aria-label={`选择发布候选 ${candidate.title}`}
                            type="checkbox"
                            checked={selectedPublishCandidateIds.includes(candidate.id)}
                            onChange={() => togglePublishCandidate(candidate.id)}
                            disabled={!canPublishCandidate(candidate)}
                          />
                        </td>
                        <td>
                          <strong>{candidate.title}</strong>
                          <div className="field-hint">{candidate.errorMessage ?? candidate.requirementId ?? '-'}</div>
                        </td>
                        <td>{candidate.coverageType}</td>
                        <td>{candidate.priority}</td>
                        <td><CandidateStatus value={candidate.status} /></td>
                        <td>
                          <button className="btn btn-secondary btn-xs" type="button" onClick={() => setSelectedCandidateId(candidate.id)}>
                            <Eye size={14} />
                            查看
                          </button>
                        </td>
                      </tr>
                    ))
                  ) : (
                    <tr>
                      <td className="table-empty" colSpan={6}>{selectedTaskId ? '暂无匹配候选用例' : '请先生成或选择任务'}</td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>

            {candidateDraft && selectedCandidate && (
              <div className="test-design-editor">
                {candidateQualityIssues.length > 0 && (
                  <div className="notice warning test-design-quality-summary">
                    <strong>质量提示</strong>
                    <span>保存前需处理 {candidateQualityIssues.length} 项候选质量问题。</span>
                    <ul className="test-design-quality-list">
                      {candidateQualityIssues.slice(0, 6).map((issue, index) => (
                        <li key={`${issue.field}-${issue.message}-${index}`}>{issue.message}</li>
                      ))}
                    </ul>
                  </div>
                )}
                <div className="asset-form-grid">
                  <label className="field">
                    <span className="field-label">标题</span>
                    <input value={candidateDraft.title} onChange={(event) => setCandidateDraft({ ...candidateDraft, title: event.target.value })} disabled={!canReview || mutationState.loading} />
                    <QualityFieldMessages field="title" issues={candidateQualityIssues} />
                  </label>
                  <label className="field">
                    <span className="field-label">覆盖类型</span>
                    <select value={candidateDraft.coverageType} onChange={(event) => setCandidateDraft({ ...candidateDraft, coverageType: event.target.value })} disabled={!canReview || mutationState.loading}>
                      {TEST_DESIGN_COVERAGE_TYPES.map((type) => <option key={type} value={type}>{type}</option>)}
                    </select>
                    <QualityFieldMessages field="coverageType" issues={candidateQualityIssues} />
                  </label>
                  <label className="field">
                    <span className="field-label">优先级</span>
                    <select value={candidateDraft.priority} onChange={(event) => setCandidateDraft({ ...candidateDraft, priority: event.target.value })} disabled={!canReview || mutationState.loading}>
                      <option value="CRITICAL">CRITICAL</option>
                      <option value="HIGH">HIGH</option>
                      <option value="MEDIUM">MEDIUM</option>
                      <option value="LOW">LOW</option>
                    </select>
                    <QualityFieldMessages field="priority" issues={candidateQualityIssues} />
                  </label>
                </div>
                <div className="asset-form-grid">
                  <label className="field">
                    <span className="field-label">API ID</span>
                    <input value={candidateDraft.apiId} onChange={(event) => setCandidateDraft({ ...candidateDraft, apiId: event.target.value })} disabled={!canReview || mutationState.loading} />
                  </label>
                  <label className="field">
                    <span className="field-label">前置条件</span>
                    <input value={candidateDraft.preconditions} onChange={(event) => setCandidateDraft({ ...candidateDraft, preconditions: event.target.value })} disabled={!canReview || mutationState.loading} />
                    <QualityFieldMessages field="preconditions" issues={candidateQualityIssues} />
                  </label>
                  <label className="field">
                    <span className="field-label">标签</span>
                    <input value={candidateDraft.tags} onChange={(event) => setCandidateDraft({ ...candidateDraft, tags: event.target.value })} disabled={!canReview || mutationState.loading} />
                    <QualityFieldMessages field="tags" issues={candidateQualityIssues} />
                  </label>
                </div>
                <label className="field">
                  <span className="field-label">描述</span>
                  <textarea value={candidateDraft.description} onChange={(event) => setCandidateDraft({ ...candidateDraft, description: event.target.value })} disabled={!canReview || mutationState.loading} />
                  <QualityFieldMessages field="description" issues={candidateQualityIssues} />
                </label>
                <label className="field">
                  <span className="field-label">步骤</span>
                  <textarea value={candidateDraft.steps} onChange={(event) => setCandidateDraft({ ...candidateDraft, steps: event.target.value })} disabled={!canReview || mutationState.loading} />
                  <span className="field-hint">每行一个步骤，可用“操作 =&gt; 期望”格式。</span>
                  <QualityFieldMessages field="steps" issues={candidateQualityIssues} />
                </label>
                <label className="field">
                  <span className="field-label">预期结果</span>
                  <textarea value={candidateDraft.expectedResult} onChange={(event) => setCandidateDraft({ ...candidateDraft, expectedResult: event.target.value })} disabled={!canReview || mutationState.loading} />
                  <QualityFieldMessages field="expectedResult" issues={candidateQualityIssues} />
                </label>
                <label className="field">
                  <span className="field-label">评审意见</span>
                  <input value={reviewComment} onChange={(event) => setReviewComment(event.target.value)} disabled={!canReview || mutationState.loading} />
                </label>
                <div className="toolbar-actions">
                  <button className="btn btn-secondary btn-sm" type="button" disabled={!canReview || mutationState.loading || !candidateDraft.title.trim() || candidateSaveBlocked} onClick={() => void saveCandidate()}>
                    <Save size={15} />
                    保存
                  </button>
                  <button className="btn btn-primary btn-sm" type="button" disabled={!canReview || mutationState.loading} onClick={() => void reviewCandidate('confirm')}>
                    <CheckCircle2 size={15} />
                    确认
                  </button>
                  <button className="btn btn-secondary btn-sm" type="button" disabled={!canReview || mutationState.loading} onClick={() => void reviewCandidate('reject')}>
                    <XCircle size={15} />
                    驳回
                  </button>
                  <button className="btn btn-ghost btn-sm" type="button" disabled={!canReview || mutationState.loading} onClick={() => void reviewCandidate('ignore')}>
                    忽略
                  </button>
                </div>
                <StateLine state={mutationState} />
              </div>
            )}
          </div>
        </section>
      </div>

      <aside className="side-stack">
        <section className="panel">
          <div className="panel-header compact">
            <div>
              <h2 className="panel-title">生成配置</h2>
              <p className="panel-desc">当前选择 {selectedRequirementIds.length} 个需求。</p>
            </div>
          </div>
          <div className="panel-body compact">
            <form className="main-stack" onSubmit={createTask}>
              <label className="field">
                <span className="field-label">项目 ID</span>
                <input value={generationDraft.projectId} onChange={(event) => setGenerationDraft((current) => ({ ...current, projectId: event.target.value }))} placeholder="project UUID" disabled={!canGenerate || mutationState.loading} />
              </label>
              <label className="field">
                <span className="field-label">任务标题</span>
                <input value={generationDraft.title} onChange={(event) => setGenerationDraft((current) => ({ ...current, title: event.target.value }))} placeholder="登录模块用例生成" disabled={!canGenerate || mutationState.loading} />
              </label>
              <label className="field">
                <span className="field-label">每需求用例数</span>
                <input value={generationDraft.caseCountPerRequirement} type="number" min="1" max="6" onChange={(event) => setGenerationDraft((current) => ({ ...current, caseCountPerRequirement: event.target.value }))} disabled={!canGenerate || mutationState.loading} />
              </label>
              <div className="field">
                <span className="field-label">覆盖类型</span>
                <div className="test-design-checks">
                  {TEST_DESIGN_COVERAGE_TYPES.map((type) => (
                    <label key={type}>
                      <input type="checkbox" checked={generationDraft.coverageTypes.includes(type)} onChange={() => toggleCoverage(type)} disabled={!canGenerate || mutationState.loading} />
                      <span>{type}</span>
                    </label>
                  ))}
                </div>
              </div>
              <button className="btn btn-primary" type="submit" disabled={!canGenerate || mutationState.loading || !selectedRequirementIds.length}>
                <Sparkles size={16} />
                生成候选
              </button>
              <StateLine state={mutationState} />
            </form>
          </div>
        </section>

        <section className="panel">
          <div className="panel-header compact">
            <div>
              <h2 className="panel-title">任务</h2>
              <p className="panel-desc">最近 {tasks.length} 个生成任务。</p>
            </div>
          </div>
          <div className="panel-body compact">
            <div className="asset-filter-bar test-design-side-filter">
              <label className="field">
                <span className="field-label">项目</span>
                <input value={taskFilters.projectId} onChange={(event) => setTaskFilters((current) => ({ ...current, projectId: event.target.value }))} placeholder="project UUID" disabled={disabled || loadState.loading} />
              </label>
              <label className="field">
                <span className="field-label">状态</span>
                <select value={taskFilters.status} onChange={(event) => setTaskFilters((current) => ({ ...current, status: event.target.value }))} disabled={disabled || loadState.loading}>
                  <option value="">全部</option>
                  <option value="DRAFT">DRAFT</option>
                  <option value="RUNNING">RUNNING</option>
                  <option value="SUCCEEDED">SUCCEEDED</option>
                  <option value="PARTIAL_SUCCESS">PARTIAL_SUCCESS</option>
                  <option value="FAILED">FAILED</option>
                  <option value="CANCELLED">CANCELLED</option>
                  <option value="PUBLISHING">PUBLISHING</option>
                  <option value="PUBLISHED">PUBLISHED</option>
                </select>
              </label>
              <label className="field">
                <span className="field-label">关键词</span>
                <input value={taskFilters.keyword} onChange={(event) => setTaskFilters((current) => ({ ...current, keyword: event.target.value }))} placeholder="任务标题" disabled={disabled || loadState.loading} />
              </label>
              <button className="btn btn-secondary btn-sm" type="button" disabled={disabled} onClick={() => setTaskFilters(initialTaskFilters)}>
                <Search size={15} />
                重置
              </button>
            </div>
            <div className="quick-actions">
              {tasks.length ? tasks.map((task) => (
                <button key={task.id} type="button" className={task.id === selectedTaskId ? 'active' : ''} onClick={() => setSelectedTaskId(task.id)}>
                  <span>
                    <strong>{task.title}</strong>
                    <em>{task.status} · {task.generatedCount} / {task.confirmedCount}</em>
                  </span>
                </button>
              )) : (
                <div className="notice info">暂无生成任务</div>
              )}
            </div>
          </div>
        </section>

        <section className="panel">
          <div className="panel-header compact">
            <div>
              <h2 className="panel-title">发布</h2>
              <p className="panel-desc">发布范围 {publishTargetCandidates.length} / {publishableCandidates.length} 个候选。</p>
            </div>
          </div>
          <div className="panel-body compact main-stack">
            {selectedPublishableCandidates.length > 0 ? (
              <div className="notice info">已按勾选候选发布；未勾选时默认覆盖全部可发布候选。</div>
            ) : (
              <div className="notice info">当前将覆盖全部可发布候选。</div>
            )}
            <button className="btn btn-secondary" type="button" disabled={!canPublish || taskState.loading || publishState.loading || !selectedTaskId || !publishTargetCandidates.length} onClick={() => void publishTask(true)}>
              <Eye size={16} />
              预发布
            </button>
            <button className="btn btn-primary" type="button" disabled={!canPublish || taskState.loading || publishState.loading || !selectedTaskId || !publishTargetCandidates.length} onClick={() => void publishTask(false)}>
              <Send size={16} />
              发布到资产库
            </button>
            <StateLine state={publishState} />
            {publishResult && (
              <>
                <div className="detail-grid">
                  <Detail label="总数" value={publishResult.total} />
                  <Detail label="创建" value={publishResult.created} />
                  <Detail label="跳过" value={publishResult.skipped} />
                  <Detail label="失败" value={publishResult.failed} />
                  <Detail label="用例" value={publishResult.createdCaseIds.join(', ') || '-'} />
                </div>
                {publishIssueRecords.length > 0 && (
                  <div className="notice warning test-design-publish-issues">
                    {publishIssueRecords.slice(0, 4).map((record) => (
                      <span key={`${record.candidateId}-${record.result}-${record.errorMessage ?? ''}`}>
                        {record.title ?? record.candidateId ?? '-'}：{record.result}{record.errorMessage ? ` · ${record.errorMessage}` : ''}
                      </span>
                    ))}
                  </div>
                )}
                {publishResult.records.length > 0 && (
                  <div className="test-design-publish-records">
                    {publishResult.records.slice(0, 6).map((record) => (
                      <PublishRecordRow key={`${record.candidateId}-${record.action}-${record.result}-${record.assetCaseId ?? ''}`} record={record} />
                    ))}
                  </div>
                )}
              </>
            )}
          </div>
        </section>

        <section className="panel">
          <div className="panel-header compact">
            <div>
              <h2 className="panel-title">范围</h2>
              <p className="panel-desc">本次生成输入。</p>
            </div>
          </div>
          <div className="panel-body compact">
            {selectedRequirementTitles.length ? (
              <div className="test-design-scope">
                {selectedRequirementTitles.map((title) => <span className="badge badge-info" key={title}>{title}</span>)}
              </div>
            ) : (
              <div className="notice info">尚未选择需求</div>
            )}
          </div>
        </section>
      </aside>
    </div>
  );
}

function Metric(props: { icon: ReactNode; label: string; value: string; desc: string }) {
  return (
    <div className="metric-card">
      <div className="metric-icon info">{props.icon}</div>
      <div className="metric-body">
        <span className="metric-label">{props.label}</span>
        <strong className="metric-value">{props.value}</strong>
        <span className="metric-desc">{props.desc}</span>
      </div>
    </div>
  );
}

function Detail(props: { label: string; value: string | number }) {
  return (
    <div className="detail-row">
      <span className="detail-label">{props.label}</span>
      <span className="detail-value">{props.value}</span>
    </div>
  );
}

function CandidateStatus(props: { value: string }) {
  const value = props.value;
  const className = value === 'CONFIRMED' || value === 'PUBLISHED'
    ? 'badge badge-success'
    : value === 'REJECTED' || value === 'FAILED'
      ? 'badge badge-danger'
      : value === 'IGNORED'
        ? 'badge badge-neutral'
        : 'badge badge-warning';
  return <span className={className}>{value}</span>;
}

function PublishRecordRow(props: { record: TestDesignPublishRecordView }) {
  const assetCaseHref = props.record.assetCaseId ? assetCaseTraceHref(props.record.assetCaseId) : '';
  return (
    <div className="test-design-publish-record">
      <span>
        <strong>{props.record.title ?? props.record.candidateId ?? '-'}</strong>
        {assetCaseHref ? (
          <a className="test-design-asset-link" href={assetCaseHref}>
            <Link2 size={13} />
            {props.record.action} · {props.record.assetCaseId}
          </a>
        ) : (
          <em>{props.record.action} · {props.record.requirementId ?? '-'}</em>
        )}
        {props.record.errorMessage && <small>{props.record.errorMessage}</small>}
      </span>
      <PublishResultBadge value={props.record.result} />
    </div>
  );
}

function PublishResultBadge(props: { value: string }) {
  const value = props.value;
  const className = value === 'SUCCEEDED' || value === 'PLANNED' || value === 'READY'
    ? 'badge badge-success'
    : value === 'CONFLICT' || value === 'FAILED' || value === 'DUPLICATE_REVIEW_REQUIRED'
      ? 'badge badge-danger'
      : value === 'SKIPPED' || value === 'LINK_EXISTING'
        ? 'badge badge-neutral'
        : 'badge badge-warning';
  return <span className={className}>{value}</span>;
}

function StateLine(props: { state: WorkState }) {
  if (props.state.loading) {
    return <span className="document-state-line">处理中</span>;
  }
  if (props.state.error) {
    return <span className="document-state-line error">{props.state.error}</span>;
  }
  if (props.state.success) {
    return <span className="document-state-line success">{props.state.success}{props.state.traceId ? ` · ${props.state.traceId}` : ''}</span>;
  }
  if (props.state.traceId) {
    return <span className="document-state-line">Trace ID：{props.state.traceId}</span>;
  }
  return null;
}

function QualityFieldMessages(props: {
  field: TestDesignCandidateDraftQualityIssue['field'];
  issues: TestDesignCandidateDraftQualityIssue[];
}) {
  const fieldIssues = props.issues.filter((issue) => issue.field === props.field);
  if (!fieldIssues.length) {
    return null;
  }
  return (
    <>
      {fieldIssues.map((issue, index) => (
        <span className="field-error" key={`${issue.field}-${issue.message}-${index}`}>{issue.message}</span>
      ))}
    </>
  );
}

function filterRequirements(requirements: AssetRequirementView[], filters: RequirementFilters) {
  const keyword = filters.keyword.trim().toLowerCase();
  return requirements.filter((requirement) => {
    if (filters.projectId.trim() && requirement.projectId !== filters.projectId.trim()) {
      return false;
    }
    if (filters.status.trim() && requirement.status !== filters.status.trim()) {
      return false;
    }
    if (!keyword) {
      return true;
    }
    return [requirement.title, requirement.description, requirement.acceptanceCriteria, requirement.sourceRef, requirement.tags.join(',')]
      .filter(Boolean)
      .some((value) => String(value).toLowerCase().includes(keyword));
  });
}

function filterCandidates(candidates: TestDesignCandidateView[], filters: CandidateFilters) {
  const keyword = filters.keyword.trim().toLowerCase();
  return candidates.filter((candidate) => {
    if (filters.status && candidate.status !== filters.status) {
      return false;
    }
    if (filters.coverageType && candidate.coverageType !== filters.coverageType) {
      return false;
    }
    if (!keyword) {
      return true;
    }
    return [
      candidate.title,
      candidate.description,
      candidate.requirementId,
      candidate.apiId,
      candidate.errorMessage,
      candidate.tags.join(',')
    ]
      .filter(Boolean)
      .some((value) => String(value).toLowerCase().includes(keyword));
  });
}

function canPublishCandidate(candidate: TestDesignCandidateView) {
  return candidate.status === 'CONFIRMED' || candidate.status === 'FAILED';
}

function isPublishIssueRecord(record: TestDesignPublishRecordView) {
  return ['CONFLICT', 'FAILED', 'DUPLICATE_REVIEW_REQUIRED'].includes(record.result) || Boolean(record.errorMessage);
}

function assetCaseTraceHref(assetCaseId: string) {
  return `#asset-library/trace/case/${encodeURIComponent(assetCaseId)}`;
}

function countByStatus(candidates: TestDesignCandidateView[]) {
  return candidates.reduce<Record<string, number>>((counts, candidate) => {
    counts[candidate.status] = (counts[candidate.status] ?? 0) + 1;
    return counts;
  }, {});
}

function upsertTask(current: TestDesignTaskView[], task: TestDesignTaskView) {
  const exists = current.some((item) => item.id === task.id);
  if (!exists) {
    return [task, ...current];
  }
  return current.map((item) => (item.id === task.id ? task : item));
}

function draftFromCandidate(candidate: TestDesignCandidateView): CandidateDraft {
  return {
    title: candidate.title,
    description: candidate.description ?? '',
    apiId: candidate.apiId ?? '',
    coverageType: candidate.coverageType,
    priority: candidate.priority,
    preconditions: candidate.preconditions ?? '',
    steps: candidate.steps.map((step) => `${step.action ?? ''} => ${step.expectedResult ?? ''}`.trim()).join('\n'),
    expectedResult: candidate.expectedResult ?? '',
    tags: candidate.tags.join(', ')
  };
}

function stepsFromText(value: string) {
  return value
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean)
    .map((line) => {
      const [action, expectedResult] = line.split(/\s*=>\s*/, 2);
      return {
        action: action?.trim(),
        expectedResult: expectedResult?.trim()
      };
    });
}

function tagsFromText(value: string) {
  return value
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean);
}

function reviewSuccessText(action: 'confirm' | 'reject' | 'ignore') {
  if (action === 'confirm') {
    return '候选用例已确认';
  }
  if (action === 'reject') {
    return '候选用例已驳回';
  }
  return '候选用例已忽略';
}

function emptyRequirementText(signedIn: boolean, canRead: boolean, loading: boolean) {
  if (!signedIn) {
    return '请先登录';
  }
  if (!canRead) {
    return '缺少 testDesign:read 权限';
  }
  return loading ? '加载中' : '暂无匹配需求';
}
