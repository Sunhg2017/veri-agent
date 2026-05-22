import {
  Activity,
  CheckCircle2,
  Eye,
  FileText,
  History,
  ListChecks,
  Pencil,
  RefreshCw,
  RotateCcw,
  Save,
  Send,
  Settings,
  Upload,
  Webhook,
  XCircle
} from 'lucide-react';
import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react';
import type { CurrentUser } from '../api/auth';
import {
  batchDocumentCandidateAction,
  confirmDocumentCandidate,
  createDocumentImport,
  createDocumentImportFile,
  createDocumentSource,
  documentInputErrorMessage,
  documentSourceTypeOptions,
  fetchDocumentFieldMapping,
  fetchDocumentCandidates,
  fetchDocumentImport,
  fetchDocumentImports,
  fetchDocumentInputHealth,
  fetchDocumentPublishRecords,
  fetchDocumentSourceHealth,
  fetchDocumentSources,
  fetchWebhookEvent,
  fetchWebhookEvents,
  ignoreDocumentCandidate,
  isReservedSourceType,
  publishDocumentImport,
  replayWebhookEvent,
  sourceTypeLabel,
  updateDocumentCandidate,
  updateDocumentFieldMapping,
  updateDocumentSource,
  type DocumentCandidateBatchAction,
  type DocumentCandidateFilters,
  type DocumentCandidatePayload,
  type DocumentCandidateView,
  type DocumentImportView,
  type DocumentInputHealth,
  type DocumentPublishRecordView,
  type DocumentPublishView,
  type DocumentSourcePayload,
  type DocumentSourceHealthView,
  type DocumentSourceType,
  type DocumentSourceView,
  type WebhookEventFilters,
  type WebhookEventView
} from '../api/documentInput';
import { hasPermission } from '../permissions';

type WorkState = {
  loading: boolean;
  error?: string;
  success?: string;
  traceId?: string;
};

type SourceDraft = {
  sourceCode: string;
  defaultProjectId: string;
  name: string;
  sourceType: DocumentSourceType;
  status: string;
  mappingId: string;
  secretRef: string;
  eventVersion: string;
  mappingVersion: string;
  endpointUrl: string;
  description: string;
};

type ImportDraft = {
  projectId: string;
  title: string;
  sourceType: DocumentSourceType;
  sourceRef: string;
  sourceUrl: string;
  sourceId: string;
  mappingId: string;
  content: string;
};

type CandidateDraft = {
  title: string;
  description: string;
  priority: string;
  acceptanceCriteria: string;
  tags: string;
  ignoreReason: string;
};

type CandidateFilterState = {
  status: string;
  sourceRef: string;
  keyword: string;
};

type EventFilters = {
  sourceId: string;
  sourceCode: string;
  eventType: string;
  status: string;
  receivedFrom: string;
  receivedTo: string;
};

const initialSourceDraft: SourceDraft = {
  sourceCode: '',
  defaultProjectId: '',
  name: '',
  sourceType: 'TEXT',
  status: '',
  mappingId: '',
  secretRef: '',
  eventVersion: '1.0',
  mappingVersion: '',
  endpointUrl: '',
  description: ''
};

const initialImportDraft: ImportDraft = {
  projectId: '',
  title: '',
  sourceType: 'MARKDOWN',
  sourceRef: '',
  sourceUrl: '',
  sourceId: '',
  mappingId: '',
  content: ''
};

const initialEventFilters: EventFilters = {
  sourceId: '',
  sourceCode: '',
  eventType: '',
  status: '',
  receivedFrom: '',
  receivedTo: ''
};

const initialCandidateFilters: CandidateFilterState = {
  status: '',
  sourceRef: '',
  keyword: ''
};

export function DocumentInputConsole(props: { signedIn: boolean; currentUser: CurrentUser | null }) {
  const [health, setHealth] = useState<DocumentInputHealth | null>(null);
  const [sources, setSources] = useState<DocumentSourceView[]>([]);
  const [sourceHealth, setSourceHealth] = useState<Record<string, DocumentSourceHealthView>>({});
  const [imports, setImports] = useState<DocumentImportView[]>([]);
  const [selectedImportId, setSelectedImportId] = useState('');
  const [importDetail, setImportDetail] = useState<DocumentImportView | null>(null);
  const [candidates, setCandidates] = useState<DocumentCandidateView[]>([]);
  const [candidateDrafts, setCandidateDrafts] = useState<Record<string, CandidateDraft>>({});
  const [selectedCandidateIds, setSelectedCandidateIds] = useState<string[]>([]);
  const [batchIgnoreReason, setBatchIgnoreReason] = useState('');
  const [candidateFilters, setCandidateFilters] = useState<CandidateFilterState>(initialCandidateFilters);
  const [candidateState, setCandidateState] = useState<WorkState>({ loading: false });
  const [publishingState, setPublishingState] = useState<WorkState>({ loading: false });
  const [publishPreview, setPublishPreview] = useState<DocumentPublishView | null>(null);
  const [publishRecords, setPublishRecords] = useState<DocumentPublishRecordView[]>([]);
  const [publishRecordState, setPublishRecordState] = useState<WorkState>({ loading: false });
  const [eventFilters, setEventFilters] = useState<EventFilters>(initialEventFilters);
  const [webhookEvents, setWebhookEvents] = useState<WebhookEventView[]>([]);
  const [selectedEventId, setSelectedEventId] = useState('');
  const [selectedEvent, setSelectedEvent] = useState<WebhookEventView | null>(null);
  const [eventState, setEventState] = useState<WorkState>({ loading: false });
  const [eventDetailState, setEventDetailState] = useState<WorkState>({ loading: false });
  const [replayState, setReplayState] = useState<WorkState>({ loading: false });
  const [sourceDraft, setSourceDraft] = useState<SourceDraft>(initialSourceDraft);
  const [editingSourceId, setEditingSourceId] = useState('');
  const [importDraft, setImportDraft] = useState<ImportDraft>(initialImportDraft);
  const [importFile, setImportFile] = useState<File | null>(null);
  const [mappingText, setMappingText] = useState('{\n  "titlePath": "title",\n  "descriptionPath": "description",\n  "priorityPath": "priority",\n  "acceptanceCriteriaPath": "acceptanceCriteria",\n  "tagsPath": "tags"\n}');
  const [loadState, setLoadState] = useState<WorkState>({ loading: false });
  const [sourceState, setSourceState] = useState<WorkState>({ loading: false });
  const [sourceHealthState, setSourceHealthState] = useState<WorkState>({ loading: false });
  const [importState, setImportState] = useState<WorkState>({ loading: false });
  const [mappingState, setMappingState] = useState<WorkState>({ loading: false });
  const [detailState, setDetailState] = useState<WorkState>({ loading: false });
  const [lastImportResult, setLastImportResult] = useState<DocumentImportView | null>(null);

  const sourceSummary = useMemo(() => {
    const reserved = sources.filter((source) => isReservedSourceType(source.sourceType)).length;
    const enabled = sources.length - reserved;
    return { enabled, reserved };
  }, [sources]);

  const refreshAll = useCallback(async () => {
    if (!props.signedIn) {
      setHealth(null);
      setSources([]);
      setSourceHealth({});
      setImports([]);
      setImportDetail(null);
      setCandidates([]);
      setSelectedCandidateIds([]);
      setPublishPreview(null);
      setPublishRecords([]);
      setWebhookEvents([]);
      setSelectedEvent(null);
      setSelectedEventId('');
      setSelectedImportId('');
      setLoadState({ loading: false });
      return;
    }

    setLoadState({ loading: true });
    const [healthResult, sourceResult, mappingResult, importResult, eventResult] = await Promise.allSettled([
      fetchDocumentInputHealth(),
      fetchDocumentSources(),
      fetchDocumentFieldMapping(),
      fetchDocumentImports(),
      fetchWebhookEvents(buildEventFilters(eventFilters))
    ]);

    const errors: string[] = [];
    const traceIds: string[] = [];

    if (healthResult.status === 'fulfilled') {
      setHealth(healthResult.value.data);
      traceIds.push(healthResult.value.trace_id);
    } else {
      errors.push(errorMessage(healthResult.reason, '文档输入健康检查失败'));
    }

    if (sourceResult.status === 'fulfilled') {
      setSources(sourceResult.value.data);
      traceIds.push(sourceResult.value.trace_id);
    } else {
      errors.push(errorMessage(sourceResult.reason, '文档源加载失败'));
    }

    if (mappingResult.status === 'fulfilled') {
      setMappingText(JSON.stringify(mappingResult.value.data ?? {}, null, 2));
      traceIds.push(mappingResult.value.trace_id);
    } else {
      errors.push(errorMessage(mappingResult.reason, '字段映射加载失败'));
    }

    if (importResult.status === 'fulfilled') {
      setImports(importResult.value.data.items);
      traceIds.push(importResult.value.trace_id);
    } else {
      errors.push(errorMessage(importResult.reason, '导入历史加载失败'));
    }

    if (eventResult.status === 'fulfilled') {
      setWebhookEvents(eventResult.value.data.items);
      traceIds.push(eventResult.value.trace_id);
    } else {
      errors.push(errorMessage(eventResult.reason, 'Webhook 事件加载失败'));
    }

    setLoadState({
      loading: false,
      error: errors.length ? errors.join('；') : undefined,
      traceId: traceIds.find(Boolean)
    });
  }, [eventFilters, props.signedIn]);

  useEffect(() => {
    void refreshAll();
  }, [refreshAll]);

  useEffect(() => {
    if (!selectedImportId || !props.signedIn) {
      setImportDetail(null);
      setCandidates([]);
      setCandidateDrafts({});
      setSelectedCandidateIds([]);
      setPublishPreview(null);
      setPublishRecords([]);
      setDetailState({ loading: false });
      setCandidateState({ loading: false });
      setPublishRecordState({ loading: false });
      return;
    }

    let active = true;
    setDetailState({ loading: true });
    setCandidateState({ loading: true });
    setPublishRecordState({ loading: true });
    setSelectedCandidateIds([]);
    setPublishPreview(null);
    Promise.allSettled([
      fetchDocumentImport(selectedImportId),
      fetchDocumentCandidates(selectedImportId, buildCandidateFilters(candidateFilters)),
      fetchDocumentPublishRecords(selectedImportId)
    ])
      .then(([detailResult, candidateResult, publishRecordResult]) => {
        if (!active) return;
        if (detailResult.status === 'fulfilled') {
          setImportDetail(detailResult.value.data);
          setDetailState({ loading: false, traceId: detailResult.value.trace_id });
        } else {
          setImportDetail(null);
          setDetailState({ loading: false, error: errorMessage(detailResult.reason, '导入详情加载失败') });
        }
        if (candidateResult.status === 'fulfilled') {
          setCandidates(candidateResult.value.data.items);
          setCandidateDrafts(candidateDraftMap(candidateResult.value.data.items));
          setCandidateState({ loading: false, traceId: candidateResult.value.trace_id });
        } else {
          setCandidates([]);
          setCandidateDrafts({});
          setCandidateState({ loading: false, error: errorMessage(candidateResult.reason, '候选需求加载失败') });
        }
        if (publishRecordResult.status === 'fulfilled') {
          setPublishRecords(publishRecordResult.value.data.items);
          setPublishRecordState({ loading: false, traceId: publishRecordResult.value.trace_id });
        } else {
          setPublishRecords([]);
          setPublishRecordState({ loading: false, error: errorMessage(publishRecordResult.reason, '发布记录加载失败') });
        }
      })
      .catch((error: unknown) => {
        if (!active) return;
        setImportDetail(null);
        setCandidates([]);
        setPublishRecords([]);
        setDetailState({ loading: false, error: errorMessage(error, '导入详情加载失败') });
        setCandidateState({ loading: false, error: errorMessage(error, '候选需求加载失败') });
        setPublishRecordState({ loading: false, error: errorMessage(error, '发布记录加载失败') });
      });

    return () => {
      active = false;
    };
  }, [props.signedIn, selectedImportId, candidateFilters]);

  useEffect(() => {
    if (!selectedEventId || !props.signedIn) {
      setSelectedEvent(null);
      setEventDetailState({ loading: false });
      return;
    }

    let active = true;
    setEventDetailState({ loading: true });
    fetchWebhookEvent(selectedEventId)
      .then((response) => {
        if (!active) return;
        setSelectedEvent(response.data);
        setEventDetailState({ loading: false, traceId: response.trace_id });
      })
      .catch((error: unknown) => {
        if (!active) return;
        setSelectedEvent(null);
        setEventDetailState({ loading: false, error: errorMessage(error, '事件详情加载失败') });
      });

    return () => {
      active = false;
    };
  }, [props.signedIn, selectedEventId]);

  async function reloadSources() {
    const response = await fetchDocumentSources();
    setSources(response.data);
    return response.trace_id;
  }

  async function reloadImports() {
    const response = await fetchDocumentImports();
    setImports(response.data.items);
    return response.trace_id;
  }

  async function reloadCandidates(importId = selectedImportId) {
    if (!importId) return '';
    const response = await fetchDocumentCandidates(importId, buildCandidateFilters(candidateFilters));
    setCandidates(response.data.items);
    setCandidateDrafts(candidateDraftMap(response.data.items));
    setSelectedCandidateIds((current) => {
      const availableIds = new Set(response.data.items.map((candidate) => candidate.id));
      return current.filter((candidateId) => availableIds.has(candidateId));
    });
    return response.trace_id;
  }

  async function refreshCandidates() {
    if (!selectedImportId) return;
    setCandidateState({ loading: true });
    try {
      const traceId = await reloadCandidates(selectedImportId);
      setCandidateState({ loading: false, traceId });
    } catch (error: unknown) {
      setCandidateState({ loading: false, error: errorMessage(error, '候选需求加载失败') });
    }
  }

  async function reloadPublishRecords(importId = selectedImportId) {
    if (!importId) return '';
    const response = await fetchDocumentPublishRecords(importId);
    setPublishRecords(response.data.items);
    return response.trace_id;
  }

  async function reloadWebhookEvents() {
    const response = await fetchWebhookEvents(buildEventFilters(eventFilters));
    setWebhookEvents(response.data.items);
    return response.trace_id;
  }

  async function refreshWebhookEvents() {
    if (!props.signedIn) {
      setEventState({ loading: false, error: '请先登录后再查看事件日志' });
      return;
    }
    setEventState({ loading: true });
    try {
      const traceId = await reloadWebhookEvents();
      setEventState({ loading: false, success: '事件日志已刷新', traceId });
    } catch (error: unknown) {
      setEventState({ loading: false, error: errorMessage(error, '事件日志刷新失败') });
    }
  }

  async function checkSourceHealth(source: DocumentSourceView) {
    if (!props.signedIn || !source.id) {
      setSourceHealthState({ loading: false, error: '请先选择文档源' });
      return;
    }

    setSourceHealthState({ loading: true });
    try {
      const response = await fetchDocumentSourceHealth(source.id);
      setSourceHealth((current) => ({ ...current, [source.id]: response.data }));
      setSourceHealthState({ loading: false, success: `${source.sourceCode ?? source.title} 健康检查完成`, traceId: response.trace_id });
    } catch (error: unknown) {
      setSourceHealthState({ loading: false, error: errorMessage(error, '文档源健康检查失败') });
    }
  }

  async function submitSource(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!props.signedIn) {
      setSourceState({ loading: false, error: '请先登录后再维护文档源' });
      return;
    }
    if (!canManageSources) {
      setSourceState({ loading: false, error: '缺少 requirementInput:manage 权限' });
      return;
    }
    if (!sourceDraft.sourceCode.trim() || !sourceDraft.name.trim()) {
      setSourceState({ loading: false, error: 'sourceCode 和名称不能为空' });
      return;
    }

    setSourceState({ loading: true });
    const payload: DocumentSourcePayload = {
      sourceCode: sourceDraft.sourceCode.trim(),
      name: sourceDraft.name.trim(),
      sourceType: sourceDraft.sourceType,
      status: sourceDraft.status.trim() || undefined,
      defaultProjectId: sourceDraft.defaultProjectId.trim() || undefined,
      mappingId: sourceDraft.mappingId.trim() || undefined,
      secretRef: sourceDraft.secretRef.trim() || undefined,
      eventVersion: sourceDraft.eventVersion.trim() || undefined,
      mappingVersion: sourceDraft.mappingVersion.trim() || undefined,
      endpointUrl: sourceDraft.endpointUrl.trim() || undefined,
      description: sourceDraft.description.trim() || undefined
    };

    try {
      const response = editingSourceId
        ? await updateDocumentSource(editingSourceId, payload)
        : await createDocumentSource(payload);
      const traceId = (await reloadSources()) || response.trace_id;
      setEditingSourceId('');
      setSourceDraft(initialSourceDraft);
      setSourceState({ loading: false, success: editingSourceId ? '文档源已更新' : '文档源已创建', traceId });
    } catch (error: unknown) {
      setSourceState({ loading: false, error: errorMessage(error, '文档源保存失败') });
    }
  }

  async function submitImport(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!props.signedIn) {
      setImportState({ loading: false, error: '请先登录后再发起导入' });
      return;
    }
    if (!canImportDocument) {
      setImportState({ loading: false, error: '缺少 requirementInput:import 权限' });
      return;
    }
    if (!importDraft.projectId.trim()) {
      setImportState({ loading: false, error: 'projectId 不能为空' });
      return;
    }
    if (!importDraft.content.trim() && !importFile) {
      setImportState({ loading: false, error: '请选择文件或粘贴待解析的内容' });
      return;
    }

    setImportState({ loading: true });
    try {
      const basePayload = {
        projectId: importDraft.projectId.trim(),
        title: importDraft.title.trim() || undefined,
        sourceType: importDraft.sourceType,
        sourceRef: importDraft.sourceRef.trim() || undefined,
        sourceUrl: importDraft.sourceUrl.trim() || undefined,
        sourceId: importDraft.sourceId.trim() || undefined,
        mappingId: importDraft.mappingId.trim() || undefined
      };
      const response = importFile
        ? await createDocumentImportFile({ ...basePayload, file: importFile })
        : await createDocumentImport({ ...basePayload, content: importDraft.content });
      const traceId = (await reloadImports()) || response.trace_id;
      setLastImportResult(response.data);
      setSelectedImportId(response.data.id);
      setImportState({ loading: false, success: '导入任务已提交', traceId });
    } catch (error: unknown) {
      try {
        await reloadImports();
      } catch {
        // Import failure detail is still available in the API error shown below.
      }
      setImportState({ loading: false, error: errorMessage(error, '导入提交失败') });
    }
  }

  async function submitMapping(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!props.signedIn) {
      setMappingState({ loading: false, error: '请先登录后再保存字段映射' });
      return;
    }
    if (!canManageSources) {
      setMappingState({ loading: false, error: '缺少 requirementInput:manage 权限' });
      return;
    }

    let parsed: unknown;
    try {
      parsed = JSON.parse(mappingText || '{}');
    } catch {
      setMappingState({ loading: false, error: '字段映射必须是合法 JSON' });
      return;
    }

    setMappingState({ loading: true });
    try {
      const response = await updateDocumentFieldMapping(parsed);
      setMappingState({ loading: false, success: '字段映射已保存', traceId: response.trace_id });
    } catch (error: unknown) {
      setMappingState({ loading: false, error: errorMessage(error, '字段映射保存失败') });
    }
  }

  async function saveCandidate(candidateId: string) {
    const draft = candidateDrafts[candidateId];
    if (!props.signedIn || !draft) {
      setCandidateState({ loading: false, error: '请先登录后再维护候选需求' });
      return;
    }
    if (!canReviewCandidates) {
      setCandidateState({ loading: false, error: '缺少 requirementInput:candidate_review 权限' });
      return;
    }
    if (!draft.title.trim()) {
      setCandidateState({ loading: false, error: '候选标题不能为空' });
      return;
    }

    const payload: DocumentCandidatePayload = {
      title: draft.title.trim(),
      description: draft.description.trim() || undefined,
      priority: draft.priority.trim() || undefined,
      acceptanceCriteria: draft.acceptanceCriteria.trim() || undefined,
      tags: tagsFromText(draft.tags),
      version: candidates.find((candidate) => candidate.id === candidateId)?.version
    };

    setCandidateState({ loading: true });
    try {
      const response = await updateDocumentCandidate(candidateId, payload);
      setCandidates((current) => current.map((candidate) => (candidate.id === candidateId ? response.data : candidate)));
      setCandidateDrafts((current) => ({ ...current, [candidateId]: candidateDraftFromView(response.data, current[candidateId]?.ignoreReason) }));
      setCandidateState({ loading: false, success: '候选需求已保存', traceId: response.trace_id });
    } catch (error: unknown) {
      setCandidateState({ loading: false, error: errorMessage(error, '候选需求保存失败') });
    }
  }

  async function confirmCandidate(candidateId: string) {
    if (!props.signedIn) {
      setCandidateState({ loading: false, error: '请先登录后再确认候选需求' });
      return;
    }
    if (!canReviewCandidates) {
      setCandidateState({ loading: false, error: '缺少 requirementInput:candidate_review 权限' });
      return;
    }

    setCandidateState({ loading: true });
    try {
      const response = await confirmDocumentCandidate(
        candidateId,
        candidates.find((candidate) => candidate.id === candidateId)?.version
      );
      setCandidates((current) => current.map((candidate) => (candidate.id === candidateId ? response.data : candidate)));
      setCandidateDrafts((current) => ({ ...current, [candidateId]: candidateDraftFromView(response.data, current[candidateId]?.ignoreReason) }));
      setCandidateState({ loading: false, success: '候选需求已确认', traceId: response.trace_id });
    } catch (error: unknown) {
      setCandidateState({ loading: false, error: errorMessage(error, '候选需求确认失败') });
    }
  }

  async function ignoreCandidate(candidateId: string) {
    const reason = candidateDrafts[candidateId]?.ignoreReason.trim() || '';
    if (!props.signedIn) {
      setCandidateState({ loading: false, error: '请先登录后再忽略候选需求' });
      return;
    }
    if (!canReviewCandidates) {
      setCandidateState({ loading: false, error: '缺少 requirementInput:candidate_review 权限' });
      return;
    }
    if (!reason) {
      setCandidateState({ loading: false, error: '忽略候选需求需要填写原因' });
      return;
    }

    setCandidateState({ loading: true });
    try {
      const response = await ignoreDocumentCandidate(
        candidateId,
        reason,
        candidates.find((candidate) => candidate.id === candidateId)?.version
      );
      setCandidates((current) => current.map((candidate) => (candidate.id === candidateId ? response.data : candidate)));
      setCandidateDrafts((current) => ({ ...current, [candidateId]: candidateDraftFromView(response.data) }));
      setCandidateState({ loading: false, success: '候选需求已忽略', traceId: response.trace_id });
    } catch (error: unknown) {
      setCandidateState({ loading: false, error: errorMessage(error, '候选需求忽略失败') });
    }
  }

  async function batchCandidates(action: DocumentCandidateBatchAction) {
    if (!props.signedIn) {
      setCandidateState({ loading: false, error: '请先登录后再批量处理候选需求' });
      return;
    }
    if (!canReviewCandidates) {
      setCandidateState({ loading: false, error: '缺少 requirementInput:candidate_review 权限' });
      return;
    }
    if (selectedCandidateIds.length === 0) {
      setCandidateState({ loading: false, error: '请先选择候选需求' });
      return;
    }
    if (action === 'IGNORE' && !batchIgnoreReason.trim()) {
      setCandidateState({ loading: false, error: '批量忽略需要填写原因' });
      return;
    }

    setCandidateState({ loading: true });
    try {
      const targets = selectedCandidateIds.map((candidateId) => ({
        id: candidateId,
        version: candidates.find((candidate) => candidate.id === candidateId)?.version
      }));
      const response = await batchDocumentCandidateAction(action, targets, action === 'IGNORE' ? batchIgnoreReason : undefined);
      const traceId = (await reloadCandidates(selectedImportId)) || response.trace_id;
      setSelectedCandidateIds([]);
      if (action === 'IGNORE') {
        setBatchIgnoreReason('');
      }
      setCandidateState({
        loading: false,
        success: `批量${action === 'CONFIRM' ? '确认' : '忽略'}完成：${response.data.succeededCount} 成功，${response.data.failedCount} 失败`,
        traceId
      });
    } catch (error: unknown) {
      setCandidateState({ loading: false, error: errorMessage(error, '候选需求批量处理失败') });
    }
  }

  async function publishImport(dryRun: boolean) {
    if (!props.signedIn || !selectedImportId) {
      setPublishingState({ loading: false, error: '请先选择导入记录' });
      return;
    }
    if (!canPublishCandidates) {
      setPublishingState({ loading: false, error: '缺少 requirementInput:publish 权限' });
      return;
    }

    setPublishingState({ loading: true });
    try {
      const response = await publishDocumentImport(selectedImportId, {
        dryRun,
        candidateIds: selectedCandidateIds.length ? selectedCandidateIds : undefined
      });
      setPublishPreview(response.data);
      if (dryRun) {
        setPublishingState({ loading: false, success: '发布预检完成', traceId: response.trace_id });
        return;
      }
      const importView = importViewFromPublish(response.data);
      setImportDetail(importView);
      setLastImportResult(importView);
      await reloadImports();
      const candidateTraceId = await reloadCandidates(selectedImportId);
      const recordTraceId = await reloadPublishRecords(selectedImportId);
      setPublishingState({ loading: false, success: '导入候选已发布', traceId: recordTraceId || candidateTraceId || response.trace_id });
    } catch (error: unknown) {
      setPublishingState({ loading: false, error: errorMessage(error, '导入发布失败') });
    }
  }

  async function replaySelectedEvent() {
    if (!props.signedIn || !selectedEventId) {
      setReplayState({ loading: false, error: '请先选择事件' });
      return;
    }
    if (!canReplayWebhook) {
      setReplayState({ loading: false, error: '缺少 requirementInput:webhook_replay 权限' });
      return;
    }

    setReplayState({ loading: true });
    try {
      const response = await replayWebhookEvent(selectedEventId);
      setSelectedEvent(response.data);
      await reloadWebhookEvents();
      setReplayState({ loading: false, success: '事件已提交重放', traceId: response.trace_id });
    } catch (error: unknown) {
      setReplayState({ loading: false, error: errorMessage(error, '事件重放失败') });
    }
  }

  function editSource(source: DocumentSourceView) {
    setEditingSourceId(source.id);
    setSourceDraft({
      sourceCode: source.sourceCode ?? '',
      defaultProjectId: source.projectId ?? '',
      name: source.title,
      sourceType: source.sourceType,
      status: source.status ?? '',
      mappingId: source.mappingId ?? '',
      secretRef: source.secretRef ?? '',
      eventVersion: source.eventVersion ?? '1.0',
      mappingVersion: source.mappingVersion ?? '',
      endpointUrl: source.sourceUrl ?? '',
      description: source.description ?? ''
    });
    setSourceState({ loading: false });
  }

  function resetSourceDraft() {
    setEditingSourceId('');
    setSourceDraft(initialSourceDraft);
    setSourceState({ loading: false });
  }

  function toggleCandidateSelection(candidateId: string) {
    setSelectedCandidateIds((current) => (
      current.includes(candidateId)
        ? current.filter((selectedId) => selectedId !== candidateId)
        : [...current, candidateId]
    ));
  }

  function toggleAllCandidates() {
    const candidateIds = candidates.map((candidate) => candidate.id).filter(Boolean);
    setSelectedCandidateIds((current) => (current.length === candidateIds.length ? [] : candidateIds));
  }

  const disabled = !props.signedIn || loadState.loading;
  const canManageSources = hasPermission(props.currentUser, 'requirementInput:manage');
  const canImportDocument = hasPermission(props.currentUser, 'requirementInput:import');
  const canReviewCandidates = hasPermission(props.currentUser, 'requirementInput:candidate_review');
  const canPublishCandidates = hasPermission(props.currentUser, 'requirementInput:publish');
  const canReplayWebhook = hasPermission(props.currentUser, 'requirementInput:webhook_replay');
  const sourceDisabled = disabled || !canManageSources;
  const importDisabled = disabled || !canImportDocument;
  const candidateDisabled = disabled || !canReviewCandidates;
  const importTypeReserved = isReservedSourceType(importDraft.sourceType);
  const sourceTypeReserved = isReservedSourceType(sourceDraft.sourceType);
  const allCandidatesSelected = candidates.length > 0 && selectedCandidateIds.length === candidates.length;
  const importPayloadReady = importDraft.content.trim().length > 0 || importFile !== null;

  return (
    <section className="document-input-layout">
      <div className="document-main-stack">
        <section className="panel module-panel document-panel">
          <div className="panel-toolbar">
            <div className="section-heading compact">
              <div className="section-icon">
                <Webhook size={20} />
              </div>
              <div>
                <span className="eyebrow">Sources</span>
                <h2>文档源管理</h2>
              </div>
            </div>
            <button className="secondary-button" type="button" disabled={!props.signedIn || loadState.loading} onClick={refreshAll}>
              <RefreshCw size={16} />
              刷新
            </button>
          </div>

          <form className="document-form" onSubmit={submitSource}>
            <div className="document-form-grid">
              <label className="field" htmlFor="source-project-id">
                <span>defaultProjectId</span>
                <input
                  id="source-project-id"
                  value={sourceDraft.defaultProjectId}
                  disabled={sourceDisabled || sourceState.loading}
                  onChange={(event) => setSourceDraft((current) => ({ ...current, defaultProjectId: event.target.value }))}
                  placeholder="proj-payments"
                />
              </label>
              <label className="field" htmlFor="source-title">
                <span>名称<b>*</b></span>
                <input
                  id="source-title"
                  value={sourceDraft.name}
                  disabled={sourceDisabled || sourceState.loading}
                  onChange={(event) => setSourceDraft((current) => ({ ...current, name: event.target.value }))}
                  placeholder="支付需求入口"
                />
              </label>
              <label className="field" htmlFor="source-type">
                <span>sourceType</span>
                <select
                  id="source-type"
                  value={sourceDraft.sourceType}
                  disabled={sourceDisabled || sourceState.loading}
                  onChange={(event) => setSourceDraft((current) => ({ ...current, sourceType: event.target.value as DocumentSourceType }))}
                >
                  {documentSourceTypeOptions.map((option) => (
                    <option key={option.value} value={option.value}>
                      {option.label}{option.reserved ? ' / 预留' : ''}
                    </option>
                  ))}
                </select>
                <small>{sourceTypeReserved ? '该类型为预留/未启用，仍可保存配置。' : '当前类型可直接接入。'}</small>
              </label>
              <label className="field" htmlFor="source-status">
                <span>status</span>
                <select
                  id="source-status"
                  value={sourceDraft.status}
                  disabled={sourceDisabled || sourceState.loading}
                  onChange={(event) => setSourceDraft((current) => ({ ...current, status: event.target.value }))}
                >
                  <option value="">后端默认</option>
                  <option value="ENABLED">ENABLED</option>
                  <option value="DISABLED">DISABLED</option>
                  <option value="PLANNED">PLANNED</option>
                </select>
              </label>
              <label className="field" htmlFor="source-code">
                <span>sourceCode<b>*</b></span>
                <input
                  id="source-code"
                  value={sourceDraft.sourceCode}
                  disabled={sourceDisabled || sourceState.loading}
                  onChange={(event) => setSourceDraft((current) => ({ ...current, sourceCode: event.target.value }))}
                  placeholder="payment-docs"
                />
              </label>
              <label className="field" htmlFor="source-mapping-id">
                <span>mappingId</span>
                <input
                  id="source-mapping-id"
                  value={sourceDraft.mappingId}
                  disabled={sourceDisabled || sourceState.loading}
                  onChange={(event) => setSourceDraft((current) => ({ ...current, mappingId: event.target.value }))}
                  placeholder="默认字段映射"
                />
              </label>
              <label className="field" htmlFor="source-secret-ref">
                <span>secretRef</span>
                <input
                  id="source-secret-ref"
                  value={sourceDraft.secretRef}
                  disabled={sourceDisabled || sourceState.loading}
                  onChange={(event) => setSourceDraft((current) => ({ ...current, secretRef: event.target.value }))}
                  placeholder="secret://wp4/payment-docs"
                />
              </label>
              <label className="field" htmlFor="source-event-version">
                <span>eventVersion</span>
                <select
                  id="source-event-version"
                  value={sourceDraft.eventVersion}
                  disabled={sourceDisabled || sourceState.loading}
                  onChange={(event) => setSourceDraft((current) => ({ ...current, eventVersion: event.target.value }))}
                >
                  <option value="1.0">1.0</option>
                </select>
              </label>
              <label className="field" htmlFor="source-mapping-version">
                <span>mappingVersion</span>
                <input
                  id="source-mapping-version"
                  value={sourceDraft.mappingVersion}
                  disabled={sourceDisabled || sourceState.loading}
                  onChange={(event) => setSourceDraft((current) => ({ ...current, mappingVersion: event.target.value }))}
                  placeholder="default"
                />
              </label>
              <label className="field" htmlFor="source-url">
                <span>endpointUrl</span>
                <input
                  id="source-url"
                  value={sourceDraft.endpointUrl}
                  disabled={sourceDisabled || sourceState.loading}
                  onChange={(event) => setSourceDraft((current) => ({ ...current, endpointUrl: event.target.value }))}
                  placeholder="https://docs.example.test/spec"
                />
              </label>
              <label className="field" htmlFor="source-description">
                <span>description</span>
                <input
                  id="source-description"
                  value={sourceDraft.description}
                  disabled={sourceDisabled || sourceState.loading}
                  onChange={(event) => setSourceDraft((current) => ({ ...current, description: event.target.value }))}
                  placeholder="入口用途或字段说明"
                />
              </label>
            </div>
            <div className="document-actions">
              <button
                className="primary-button"
                type="submit"
                disabled={sourceDisabled || sourceState.loading || !sourceDraft.sourceCode.trim() || !sourceDraft.name.trim()}
              >
                <Save size={16} />
                {editingSourceId ? '保存文档源' : '新增文档源'}
              </button>
              <button className="secondary-button" type="button" disabled={sourceState.loading} onClick={resetSourceDraft}>
                取消编辑
              </button>
              <StateLine state={sourceState} />
            </div>
          </form>

          <div className="table-wrap document-source-table">
            <table>
              <thead>
                <tr>
                  <th>标题</th>
                  <th>默认项目</th>
                  <th>类型</th>
                  <th>Endpoint</th>
                  <th>Webhook</th>
                  <th>状态</th>
                  <th>健康</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                {sources.length > 0 ? (
                  sources.map((source) => {
                    const healthView = sourceHealth[source.id];
                    return (
                      <tr key={source.id || source.title}>
                        <td>
                          <strong className="table-primary">{source.title}</strong>
                          {source.sourceCode && <span className="table-secondary">{source.sourceCode}</span>}
                        </td>
                        <td>{source.projectId ?? '-'}</td>
                        <td>
                          <SourceTypeBadge type={source.sourceType} />
                        </td>
                        <td>{source.sourceUrl || '-'}</td>
                        <td>
                          {source.sourceType === 'CUSTOM_API' ? (
                            <div className="source-health-cell">
                              <span>{source.eventVersion ?? '1.0'}</span>
                              <span>{source.secretRef ? 'secretRef 已配置' : 'secretRef 未配置'}</span>
                              {source.mappingVersion && <span>{source.mappingVersion}</span>}
                            </div>
                          ) : (
                            <span className="table-secondary">-</span>
                          )}
                        </td>
                        <td>
                          <DocumentStatusPill value={sourceStatus(source)} />
                        </td>
                        <td>
                          {healthView ? (
                            <div className="source-health-cell">
                              <DocumentStatusPill value={sourceHealthStatus(healthView)} />
                              {healthView.message && <span>{healthView.message}</span>}
                            </div>
                          ) : (
                            <span className="table-secondary">未检查</span>
                          )}
                        </td>
                        <td>
                          <div className="document-actions">
                            <button
                              className="mini-button"
                              type="button"
                              disabled={!props.signedIn || sourceHealthState.loading || !source.id}
                              onClick={() => checkSourceHealth(source)}
                            >
                              <Activity size={14} />
                              检查
                            </button>
                            <button
                              className="mini-button"
                              type="button"
                            disabled={!props.signedIn || !canManageSources || sourceState.loading || !source.id}
                              onClick={() => editSource(source)}
                            >
                              <Pencil size={14} />
                              编辑
                            </button>
                          </div>
                        </td>
                      </tr>
                    );
                  })
                ) : (
                  <tr>
                    <td className="table-empty" colSpan={8}>
                      {props.signedIn ? (loadState.loading ? '加载中' : '暂无文档源') : '请先登录'}
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
          <StateLine state={sourceHealthState} />
        </section>

        <section className="panel module-panel document-panel">
          <div className="section-heading">
            <div className="section-icon">
              <Upload size={20} />
            </div>
            <div>
              <span className="eyebrow">Import</span>
              <h2>文本 / Word / PDF / OCR 导入</h2>
            </div>
          </div>

          <form className="document-form" onSubmit={submitImport}>
            <div className="document-form-grid">
              <label className="field" htmlFor="import-project-id">
                <span>projectId<b>*</b></span>
                <input
                  id="import-project-id"
                  value={importDraft.projectId}
                  disabled={importDisabled || importState.loading}
                  onChange={(event) => setImportDraft((current) => ({ ...current, projectId: event.target.value }))}
                  placeholder="proj-payments"
                />
              </label>
              <label className="field" htmlFor="import-title">
                <span>标题</span>
                <input
                  id="import-title"
                  value={importDraft.title}
                  disabled={importDisabled || importState.loading}
                  onChange={(event) => setImportDraft((current) => ({ ...current, title: event.target.value }))}
                  placeholder="支付需求 Markdown 导入"
                />
              </label>
              <label className="field" htmlFor="import-source-type">
                <span>sourceType</span>
                <select
                  id="import-source-type"
                  value={importDraft.sourceType}
                  disabled={importDisabled || importState.loading}
                  onChange={(event) => setImportDraft((current) => ({ ...current, sourceType: event.target.value as DocumentSourceType }))}
                >
                  {documentSourceTypeOptions.map((option) => (
                    <option key={option.value} value={option.value}>
                      {option.label}{option.reserved ? ' / 预留' : ''}
                    </option>
                  ))}
                </select>
                <small>{importTypeReserved ? '该类型为预留/未启用，提交后以后端能力为准。' : '可提交纯文本、base64 或 data URL。'}</small>
              </label>
              <label className="field" htmlFor="import-source-ref">
                <span>sourceRef</span>
                <input
                  id="import-source-ref"
                  value={importDraft.sourceRef}
                  disabled={importDisabled || importState.loading}
                  onChange={(event) => setImportDraft((current) => ({ ...current, sourceRef: event.target.value }))}
                  placeholder="PRD-2026-001"
                />
              </label>
              <label className="field" htmlFor="import-source-url">
                <span>sourceUrl</span>
                <input
                  id="import-source-url"
                  value={importDraft.sourceUrl}
                  disabled={importDisabled || importState.loading}
                  onChange={(event) => setImportDraft((current) => ({ ...current, sourceUrl: event.target.value }))}
                  placeholder="https://docs.example.test/spec"
                />
              </label>
              <label className="field" htmlFor="import-source-id">
                <span>sourceId</span>
                <input
                  id="import-source-id"
                  value={importDraft.sourceId}
                  disabled={importDisabled || importState.loading}
                  onChange={(event) => setImportDraft((current) => ({ ...current, sourceId: event.target.value }))}
                  placeholder="uuid"
                />
              </label>
              <label className="field" htmlFor="import-mapping-id">
                <span>mappingId</span>
                <input
                  id="import-mapping-id"
                  value={importDraft.mappingId}
                  disabled={importDisabled || importState.loading}
                  onChange={(event) => setImportDraft((current) => ({ ...current, mappingId: event.target.value }))}
                  placeholder="默认字段映射"
                />
              </label>
            </div>
            <label className="field document-content-field" htmlFor="import-content">
              <span>content</span>
              <textarea
                id="import-content"
                value={importDraft.content}
                disabled={importDisabled || importState.loading || importFile !== null}
                onChange={(event) => setImportDraft((current) => ({ ...current, content: event.target.value }))}
                placeholder="# 需求标题&#10;&#10;- 用户可以...&#10;- 系统需要...&#10;&#10;也可以选择真实 Word/PDF/图片文件上传。"
              />
            </label>
            <label className="field document-upload-field" htmlFor="import-file">
              <span>真实文件上传</span>
              <input
                id="import-file"
                type="file"
                disabled={importDisabled || importState.loading || importDraft.content.trim().length > 0}
                accept=".txt,.md,.doc,.docx,.pdf,image/*,application/pdf,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                onChange={(event) => setImportFile(event.target.files?.[0] ?? null)}
              />
              <small>
                {importFile
                  ? `${importFile.name} · ${formatBytes(importFile.size)}`
                  : '选择文件后将按 multipart/form-data 上传。'}
              </small>
            </label>
            <div className="document-actions">
              <button
                className="primary-button"
                type="submit"
                disabled={
                  importDisabled ||
                  importState.loading ||
                  !importDraft.projectId.trim() ||
                  !importPayloadReady
                }
              >
                <Upload size={16} />
                发起导入
              </button>
              {importFile && (
                <button
                  className="secondary-button"
                  type="button"
                  disabled={importState.loading}
                  onClick={() => setImportFile(null)}
                >
                  <XCircle size={16} />
                  清除文件
                </button>
              )}
              <StateLine state={importState} />
            </div>
          </form>

          {lastImportResult && (
            <div className="document-result-strip">
              <DocumentStatusPill value={lastImportResult.status} />
              <span>createdRequirements：{lastImportResult.createdRequirements}</span>
              <span>requirementCount：{lastImportResult.requirementCount}</span>
              {lastImportResult.errorMessage && <FailureHint message={lastImportResult.errorMessage} />}
            </div>
          )}
        </section>

        <section className="panel module-panel document-panel">
          <div className="section-heading">
            <div className="section-icon">
              <Settings size={20} />
            </div>
            <div>
              <span className="eyebrow">Field Mapping</span>
              <h2>字段映射</h2>
            </div>
          </div>
          <form className="document-form" onSubmit={submitMapping}>
            <label className="field document-content-field" htmlFor="field-mapping">
              <span>mapping JSON</span>
              <textarea
                id="field-mapping"
                value={mappingText}
                disabled={sourceDisabled || mappingState.loading}
                onChange={(event) => {
                  setMappingText(event.target.value);
                  setMappingState({ loading: false });
                }}
                spellCheck={false}
              />
            </label>
            <div className="document-actions">
              <button className="primary-button" type="submit" disabled={sourceDisabled || mappingState.loading}>
                <Save size={16} />
                保存字段映射
              </button>
              <StateLine state={mappingState} />
            </div>
          </form>
        </section>
      </div>

      <aside className="side-stack document-side-stack">
        <section className="panel insight-panel">
          <h2>WP4 接口状态</h2>
          <div className="document-health-grid">
            <StatusMetric label="服务" value={health?.service ?? 'document-input'} />
            <StatusMetric label="状态" value={health?.status ?? (props.signedIn ? '等待响应' : '等待登录')} pill />
            <StatusMetric label="可用源" value={String(sourceSummary.enabled)} />
            <StatusMetric label="预留源" value={String(sourceSummary.reserved)} />
            <StatusMetric label="输入开关" value={health?.inputEnabled === false ? 'OFF' : 'ON'} pill />
            <StatusMetric label="Webhook" value={health?.webhookEnabled === false ? 'OFF' : 'ON'} pill />
            <StatusMetric label="模型解析" value={health?.modelParseEnabled ? 'ON' : 'OFF'} pill />
            <StatusMetric label="Payload" value={formatBytes(health?.webhookMaxPayloadBytes)} />
            <StatusMetric label="导入上限" value={formatBytes(health?.importMaxContentBytes)} />
            <StatusMetric label="文件上限" value={formatBytes(health?.documentBinaryMaxBytes)} />
            <StatusMetric label="OCR" value={health?.ocrConfigured ? 'READY' : 'OFF'} pill />
            <StatusMetric label="OCR Worker" value={health?.ocrWorkerMode ?? '-'} pill />
            <StatusMetric label="OCR worker endpoint" value={health?.ocrRemoteWorkerConfigured ? 'ON' : 'OFF'} pill />
            <StatusMetric label="OCR worker token" value={health?.ocrWorkerTokenConfigured ? 'SET' : 'OFF'} pill />
            <StatusMetric label="OCR 本地执行" value={health?.ocrLocalCommandExecutionAllowed ? 'ON' : 'OFF'} pill />
            <StatusMetric label="OCR fallback" value={health?.ocrLocalCommandFallbackEnabled ? 'ON' : 'OFF'} pill />
            <StatusMetric label="OCR 并发" value={`${health?.ocrAvailablePermits ?? '-'} / ${health?.ocrMaxConcurrentProcesses ?? '-'}`} />
            <StatusMetric label="OCR 超时" value={health?.ocrTimeoutSeconds ? `${health.ocrTimeoutSeconds}s` : '-'} />
            <StatusMetric label="SecretProvider" value={health?.externalSecretProvider?.status ?? '-'} pill />
            <StatusMetric label="Secret 缓存" value={health?.webhookSecretCacheEnabled ? 'ON' : 'OFF'} pill />
            <StatusMetric label="缓存 TTL" value={secondsLabel(health?.webhookSecretCacheTtlSeconds)} />
            <StatusMetric label="轮换窗口" value={secondsLabel(health?.webhookSecretRotationOverlapSeconds)} />
            <StatusMetric label="缓存数" value={String(health?.webhookSecretCacheSize ?? '-')} />
            <StatusMetric label="批量上限" value={String(health?.batchActionLimit ?? '-')} />
          </div>
          {loadState.error && (
            <div className="inline-error">
              <strong>同步失败</strong>
              <span>{loadState.error}</span>
            </div>
          )}
          {loadState.traceId && <div className="panel-trace">Trace ID：{loadState.traceId}</div>}
        </section>

        <section className="panel insight-panel document-history-panel">
          <div className="panel-title-row">
            <h2>导入历史</h2>
            <History size={18} />
          </div>
          <div className="document-history-list">
            {imports.length > 0 ? (
              imports.map((item) => (
                <button
                  className={`document-history-row ${selectedImportId === item.id ? 'active' : ''}`}
                  type="button"
                  key={item.id || `${item.title}-${item.createdAt}`}
                  disabled={!item.id}
                  onClick={() => setSelectedImportId(item.id)}
                >
                  <span>
                    <strong>{item.title}</strong>
                    <em>{item.projectId ?? '-'} · {sourceTypeLabel(item.sourceType)}</em>
                  </span>
                  <span>
                    <DocumentStatusPill value={item.status} />
                    <em>{item.requirementCount} 条</em>
                  </span>
                </button>
              ))
            ) : (
              <div className="empty-state compact">
                <History size={20} />
                <div>
                  <strong>{props.signedIn ? '暂无导入历史' : '等待登录'}</strong>
                  <span>{props.signedIn ? '导入完成后会显示状态和需求数量' : '登录后加载导入记录'}</span>
                </div>
              </div>
            )}
          </div>
        </section>

        <section className="panel insight-panel">
          <div className="panel-title-row">
            <h2>导入详情</h2>
            <div className="panel-title-actions">
              <button className="mini-button" type="button" disabled={!props.signedIn || !canPublishCandidates || !selectedImportId || publishingState.loading} onClick={() => publishImport(true)}>
                <Eye size={14} />
                Dry Run
              </button>
              <button className="mini-button" type="button" disabled={!props.signedIn || !canPublishCandidates || !selectedImportId || publishingState.loading} onClick={() => publishImport(false)}>
                <Send size={14} />
                发布
              </button>
              <FileText size={18} />
            </div>
          </div>
          {importDetail ? (
            <div className="document-detail-list">
              <strong>{importDetail.title}</strong>
              <div>
                <span>状态</span>
                <DocumentStatusPill value={importDetail.status} />
              </div>
              <div>
                <span>createdRequirements</span>
                <em>{importDetail.createdRequirements}</em>
              </div>
              <div>
                <span>requirementCount</span>
                <em>{importDetail.requirementCount}</em>
              </div>
              <div>
                <span>来源</span>
                <em>{importDetail.sourceUrl || importDetail.sourceRef || sourceTypeLabel(importDetail.sourceType)}</em>
              </div>
              {importDetail.errorMessage && (
                <div>
                  <span>错误</span>
                  <FailureHint message={importDetail.errorMessage} />
                </div>
              )}
              {importDetail.requirements && importDetail.requirements.length > 0 && (
                <div className="document-requirement-preview">
                  <span>解析需求</span>
                  {importDetail.requirements.slice(0, 5).map((requirement, index) => (
                    <em key={requirement.id ?? `${requirement.title}-${index}`}>
                      {[requirement.title ?? requirement.id ?? `需求 ${index + 1}`, requirement.parseSource].filter(Boolean).join(' · ')}
                    </em>
                  ))}
                </div>
              )}
              {publishPreview && (
                <div className="document-publish-summary">
                  <strong>{publishPreview.dryRun ? '发布预检' : '发布结果'}</strong>
                  <div className="document-publish-metrics">
                    <StatusMetric label="计划创建" value={String(publishPreview.plannedCreateCount)} />
                    <StatusMetric label="计划更新" value={String(publishPreview.plannedUpdateCount)} />
                    <StatusMetric label="已关联" value={String(publishPreview.linkedExistingCount)} />
                    <StatusMetric label="冲突" value={String(publishPreview.conflictCount)} />
                    <StatusMetric label="跳过" value={String(publishPreview.skippedCount)} />
                    <StatusMetric label="失败" value={String(publishPreview.publishFailedCount)} />
                  </div>
                  {selectedCandidateIds.length > 0 && <span className="table-secondary">已按 {selectedCandidateIds.length} 个候选项过滤</span>}
                  {publishPreview.records.length > 0 && (
                    <div className="document-publish-records compact">
                      {publishPreview.records.slice(0, 5).map((record) => (
                        <PublishRecordRow key={`${record.candidateId}-${record.action}-${record.result}`} record={record} />
                      ))}
                    </div>
                  )}
                </div>
              )}
              {publishRecords.length > 0 && (
                <div className="document-publish-records">
                  <strong>发布记录</strong>
                  {publishRecords.slice(0, 8).map((record) => (
                    <PublishRecordRow key={`${record.candidateId}-${record.version}`} record={record} />
                  ))}
                </div>
              )}
              {detailState.traceId && <div className="panel-trace">Trace ID：{detailState.traceId}</div>}
              <StateLine state={publishingState} />
              <StateLine state={publishRecordState} />
            </div>
          ) : (
            <div className="empty-state compact">
              <FileText size={20} />
              <div>
                <strong>{detailState.loading ? '正在加载详情' : '未选择导入'}</strong>
                <span>{detailState.error ?? '从导入历史中选择一条记录查看详情'}</span>
              </div>
            </div>
          )}
        </section>

        <section className="panel insight-panel document-candidate-panel">
          <div className="panel-title-row">
            <h2>候选需求</h2>
            <span className="document-count-badge">{candidates.length}</span>
          </div>
          {selectedImportId && (
            <div className="document-candidate-toolbar candidate-filter-toolbar">
              <select
                value={candidateFilters.status}
                disabled={candidateState.loading}
                onChange={(event) => setCandidateFilters((current) => ({ ...current, status: event.target.value }))}
                aria-label="候选状态筛选"
              >
                <option value="">全部状态</option>
                <option value="PENDING">PENDING</option>
                <option value="CONFIRMED">CONFIRMED</option>
                <option value="IGNORED">IGNORED</option>
                <option value="PUBLISHED">PUBLISHED</option>
                <option value="PUBLISH_FAILED">PUBLISH_FAILED</option>
              </select>
              <input
                type="text"
                value={candidateFilters.sourceRef}
                disabled={candidateState.loading}
                onChange={(event) => setCandidateFilters((current) => ({ ...current, sourceRef: event.target.value }))}
                placeholder="sourceRef"
              />
              <input
                type="text"
                value={candidateFilters.keyword}
                disabled={candidateState.loading}
                onChange={(event) => setCandidateFilters((current) => ({ ...current, keyword: event.target.value }))}
                placeholder="标题 / 描述 / 外部ID"
              />
              <button
                className="mini-button"
                type="button"
                disabled={candidateState.loading}
                onClick={() => void refreshCandidates()}
              >
                <RefreshCw size={14} />
                刷新
              </button>
              <button
                className="mini-button"
                type="button"
                disabled={candidateState.loading}
                onClick={() => setCandidateFilters(initialCandidateFilters)}
              >
                <XCircle size={14} />
                清空
              </button>
            </div>
          )}
          {candidates.length > 0 && (
            <div className="document-candidate-toolbar">
              <label className="candidate-select" htmlFor="candidate-select-all">
                <input
                  id="candidate-select-all"
                  type="checkbox"
                  checked={allCandidatesSelected}
                  disabled={candidateDisabled || candidateState.loading}
                  onChange={() => toggleAllCandidates()}
                />
                <span>当前页</span>
              </label>
              <span className="table-secondary">已选 {selectedCandidateIds.length}</span>
              <input
                value={batchIgnoreReason}
                disabled={candidateDisabled || candidateState.loading}
                onChange={(event) => setBatchIgnoreReason(event.target.value)}
                placeholder="批量忽略原因"
              />
              <button
                className="mini-button"
                type="button"
                disabled={candidateDisabled || candidateState.loading || selectedCandidateIds.length === 0}
                onClick={() => batchCandidates('CONFIRM')}
              >
                <ListChecks size={14} />
                批量确认
              </button>
              <button
                className="mini-button"
                type="button"
                disabled={candidateDisabled || candidateState.loading || selectedCandidateIds.length === 0 || !batchIgnoreReason.trim()}
                onClick={() => batchCandidates('IGNORE')}
              >
                <XCircle size={14} />
                批量忽略
              </button>
            </div>
          )}
          <div className="document-candidate-list">
            {candidates.length > 0 ? (
              candidates.map((candidate) => {
                const draft = candidateDrafts[candidate.id] ?? candidateDraftFromView(candidate);
                return (
                  <article className="document-candidate-card" key={candidate.id}>
                    <div className="document-candidate-heading">
                      <label className="candidate-select" htmlFor={`candidate-select-${candidate.id}`}>
                        <input
                          id={`candidate-select-${candidate.id}`}
                          type="checkbox"
                          checked={selectedCandidateIds.includes(candidate.id)}
                          disabled={candidateDisabled || candidateState.loading}
                          onChange={() => toggleCandidateSelection(candidate.id)}
                        />
                        <DocumentStatusPill value={candidate.status} />
                      </label>
                      {typeof candidate.confidence === 'number' && <span>{Math.round(candidate.confidence * 100)}%</span>}
                    </div>
                    <label className="field" htmlFor={`candidate-title-${candidate.id}`}>
                      <span>标题</span>
                      <input
                        id={`candidate-title-${candidate.id}`}
                        value={draft.title}
                        disabled={candidateDisabled || candidateState.loading}
                        onChange={(event) => updateCandidateDraft(candidate.id, { title: event.target.value })}
                      />
                    </label>
                    <label className="field" htmlFor={`candidate-priority-${candidate.id}`}>
                      <span>优先级</span>
                      <input
                        id={`candidate-priority-${candidate.id}`}
                        value={draft.priority}
                        disabled={candidateDisabled || candidateState.loading}
                        onChange={(event) => updateCandidateDraft(candidate.id, { priority: event.target.value })}
                        placeholder="HIGH / MEDIUM / LOW"
                      />
                    </label>
                    <label className="field" htmlFor={`candidate-description-${candidate.id}`}>
                      <span>描述</span>
                      <textarea
                        id={`candidate-description-${candidate.id}`}
                        className="compact-textarea"
                        value={draft.description}
                        disabled={candidateDisabled || candidateState.loading}
                        onChange={(event) => updateCandidateDraft(candidate.id, { description: event.target.value })}
                      />
                    </label>
                    <label className="field" htmlFor={`candidate-acceptance-${candidate.id}`}>
                      <span>验收标准</span>
                      <textarea
                        id={`candidate-acceptance-${candidate.id}`}
                        className="compact-textarea"
                        value={draft.acceptanceCriteria}
                        disabled={candidateDisabled || candidateState.loading}
                        onChange={(event) => updateCandidateDraft(candidate.id, { acceptanceCriteria: event.target.value })}
                      />
                    </label>
                    <label className="field" htmlFor={`candidate-tags-${candidate.id}`}>
                      <span>标签</span>
                      <input
                        id={`candidate-tags-${candidate.id}`}
                        value={draft.tags}
                        disabled={candidateDisabled || candidateState.loading}
                        onChange={(event) => updateCandidateDraft(candidate.id, { tags: event.target.value })}
                        placeholder="auth, mobile"
                      />
                    </label>
                    {(candidate.sourceRef ||
                      candidate.sourceFragment ||
                      candidate.externalRequirementId ||
                      candidate.parseSource ||
                      candidate.modelInvocationId ||
                      candidate.modelProviderName ||
                      candidate.modelName ||
                      candidate.assetRequirementId ||
                      candidate.ignoredReason ||
                      candidate.confirmedBy ||
                      candidate.confirmedAt ||
                      typeof candidate.version === 'number' ||
                      candidate.errorMessage) && (
                      <div className="document-candidate-meta">
                        {candidate.sourceRef && <span>sourceRef：{candidate.sourceRef}</span>}
                        {candidate.parseSource && <span>parseSource：{candidate.parseSource}</span>}
                        {candidate.modelInvocationId && <span>modelInvocationId：{candidate.modelInvocationId}</span>}
                        {candidate.modelProviderName && <span>modelProviderName：{candidate.modelProviderName}</span>}
                        {candidate.modelName && <span>modelName：{candidate.modelName}</span>}
                        {candidate.externalRequirementId && <span>externalRequirementId：{candidate.externalRequirementId}</span>}
                        {candidate.assetRequirementId && <span>assetRequirementId：{candidate.assetRequirementId}</span>}
                        {typeof candidate.version === 'number' && <span>version：{candidate.version}</span>}
                        {candidate.confirmedBy && <span>confirmedBy：{candidate.confirmedBy}</span>}
                        {candidate.confirmedAt && <span>confirmedAt：{candidate.confirmedAt}</span>}
                        {candidate.ignoredReason && <span>ignoredReason：{candidate.ignoredReason}</span>}
                        {candidate.sourceFragment && <span>{candidate.sourceFragment}</span>}
                        {candidate.errorMessage && <FailureHint message={candidate.errorMessage} />}
                      </div>
                    )}
                    <label className="field" htmlFor={`candidate-ignore-${candidate.id}`}>
                      <span>忽略原因</span>
                      <input
                        id={`candidate-ignore-${candidate.id}`}
                        value={draft.ignoreReason}
                        disabled={candidateDisabled || candidateState.loading}
                        onChange={(event) => updateCandidateDraft(candidate.id, { ignoreReason: event.target.value })}
                        placeholder="重复、非需求、需人工拆分"
                      />
                    </label>
                    <div className="document-actions candidate-actions">
                      <button className="mini-button" type="button" disabled={candidateDisabled || candidateState.loading || !draft.title.trim()} onClick={() => saveCandidate(candidate.id)}>
                        <Save size={14} />
                        保存
                      </button>
                      <button className="mini-button" type="button" disabled={candidateDisabled || candidateState.loading} onClick={() => confirmCandidate(candidate.id)}>
                        <CheckCircle2 size={14} />
                        确认
                      </button>
                      <button className="mini-button" type="button" disabled={candidateDisabled || candidateState.loading} onClick={() => ignoreCandidate(candidate.id)}>
                        <XCircle size={14} />
                        忽略
                      </button>
                    </div>
                  </article>
                );
              })
            ) : (
              <div className="empty-state compact">
                <FileText size={20} />
                <div>
                  <strong>{candidateState.loading ? '正在加载候选需求' : '暂无候选需求'}</strong>
                  <span>{candidateState.error ?? '选择导入记录后会显示解析候选'}</span>
                </div>
              </div>
            )}
          </div>
          <StateLine state={candidateState} />
        </section>

        <section className="panel insight-panel document-webhook-panel">
          <div className="panel-title-row">
            <h2>Webhook 事件</h2>
            <button className="mini-button" type="button" disabled={!props.signedIn || eventState.loading} onClick={refreshWebhookEvents}>
              <RefreshCw size={14} />
              刷新
            </button>
          </div>
          <div className="webhook-filter-grid">
            <label className="field" htmlFor="webhook-source-id-filter">
              <span>sourceId</span>
              <input
                id="webhook-source-id-filter"
                value={eventFilters.sourceId}
                disabled={!props.signedIn || eventState.loading}
                onChange={(event) => setEventFilters((current) => ({ ...current, sourceId: event.target.value }))}
                placeholder="uuid"
              />
            </label>
            <label className="field" htmlFor="webhook-source-filter">
              <span>sourceCode</span>
              <input
                id="webhook-source-filter"
                value={eventFilters.sourceCode}
                disabled={!props.signedIn || eventState.loading}
                onChange={(event) => setEventFilters((current) => ({ ...current, sourceCode: event.target.value }))}
                placeholder="payment-docs"
              />
            </label>
            <label className="field" htmlFor="webhook-event-type-filter">
              <span>eventType</span>
              <input
                id="webhook-event-type-filter"
                value={eventFilters.eventType}
                disabled={!props.signedIn || eventState.loading}
                onChange={(event) => setEventFilters((current) => ({ ...current, eventType: event.target.value }))}
                placeholder="requirement.created"
              />
            </label>
            <label className="field" htmlFor="webhook-status-filter">
              <span>status</span>
              <select
                id="webhook-status-filter"
                value={eventFilters.status}
                disabled={!props.signedIn || eventState.loading}
                onChange={(event) => setEventFilters((current) => ({ ...current, status: event.target.value }))}
              >
                <option value="">全部</option>
                <option value="FAILED">FAILED</option>
                <option value="PROCESSED">PROCESSED</option>
                <option value="ACCEPTED">ACCEPTED</option>
                <option value="REJECTED">REJECTED</option>
                <option value="DEAD_LETTER">DEAD_LETTER</option>
                <option value="REPLAYED">REPLAYED</option>
              </select>
            </label>
          </div>
          <div className="webhook-event-list">
            {webhookEvents.length > 0 ? (
              webhookEvents.map((event) => (
                <button
                  className={`webhook-event-row ${selectedEventId === event.id ? 'active' : ''}`}
                  type="button"
                  key={event.id}
                  disabled={!event.id}
                  onClick={() => setSelectedEventId(event.id)}
                >
                  <span>
                    <strong>{event.eventType ?? event.eventId ?? event.id}</strong>
                    <em>{event.sourceCode ?? '-'} · retry {event.retryCount}</em>
                  </span>
                  <DocumentStatusPill value={event.status} />
                </button>
              ))
            ) : (
              <div className="empty-state compact">
                <Webhook size={20} />
                <div>
                  <strong>{eventState.loading ? '正在加载事件' : '暂无事件日志'}</strong>
                  <span>{eventState.error ?? 'Webhook 收到事件后会显示状态和重放入口'}</span>
                </div>
              </div>
            )}
          </div>
          {selectedEvent && (
            <div className="webhook-event-detail">
              <div>
                <span>eventId</span>
                <em>{selectedEvent.eventId ?? selectedEvent.id}</em>
              </div>
              <div>
                <span>sourceId</span>
                <em>{selectedEvent.sourceId ?? '-'}</em>
              </div>
              <div>
                <span>importId</span>
                <em>{selectedEvent.importId ?? '-'}</em>
              </div>
              <div>
                <span>idempotencyKey</span>
                <em>{selectedEvent.idempotencyKey ?? '-'}</em>
              </div>
              <div>
                <span>signatureStatus</span>
                <DocumentStatusPill value={selectedEvent.signatureStatus ?? 'UNKNOWN'} />
              </div>
              <div>
                <span>receivedAt</span>
                <em>{selectedEvent.receivedAt ?? '-'}</em>
              </div>
              <div>
                <span>processedAt</span>
                <em>{selectedEvent.processedAt ?? '-'}</em>
              </div>
              <div>
                <span>replayBy</span>
                <em>{selectedEvent.replayBy ?? '-'}</em>
              </div>
              <div>
                <span>replayAt</span>
                <em>{selectedEvent.replayAt ?? '-'}</em>
              </div>
              <div>
                <span>replayTraceId</span>
                <em>{selectedEvent.replayTraceId ?? '-'}</em>
              </div>
              {selectedEvent.payloadDigest && (
                <div>
                  <span>payloadDigest</span>
                  <em>{selectedEvent.payloadDigest}</em>
                </div>
              )}
              {selectedEvent.errorMessage && (
                <div>
                  <span>错误</span>
                  <FailureHint message={selectedEvent.errorMessage} />
                </div>
              )}
            <button className="mini-button" type="button" disabled={!props.signedIn || !canReplayWebhook || replayState.loading} onClick={replaySelectedEvent}>
                <RotateCcw size={14} />
                重放事件
              </button>
            </div>
          )}
          <StateLine state={eventState} />
          <StateLine state={eventDetailState} />
          <StateLine state={replayState} />
        </section>
      </aside>
    </section>
  );

  function updateCandidateDraft(candidateId: string, patch: Partial<CandidateDraft>) {
    setCandidateDrafts((current) => ({
      ...current,
      [candidateId]: {
        ...(current[candidateId] ?? candidateDraftFromView(candidates.find((candidate) => candidate.id === candidateId))),
        ...patch
      }
    }));
  }
}

function candidateDraftFromView(candidate?: DocumentCandidateView, ignoreReason = ''): CandidateDraft {
  return {
    title: candidate?.title ?? '',
    description: candidate?.description ?? '',
    priority: candidate?.priority ?? '',
    acceptanceCriteria: candidate?.acceptanceCriteria ?? '',
    tags: candidate?.tags.join(', ') ?? '',
    ignoreReason: ignoreReason || candidate?.ignoredReason || ''
  };
}

function candidateDraftMap(candidates: DocumentCandidateView[]) {
  return Object.fromEntries(candidates.map((candidate) => [candidate.id, candidateDraftFromView(candidate)]));
}

function tagsFromText(value: string) {
  return value
    .split(',')
    .map((tag) => tag.trim())
    .filter(Boolean);
}

function buildEventFilters(filters: EventFilters): WebhookEventFilters {
  return {
    index: 0,
    size: 20,
    sourceId: filters.sourceId.trim() || undefined,
    sourceCode: filters.sourceCode.trim() || undefined,
    eventType: filters.eventType.trim() || undefined,
    status: filters.status.trim() || undefined,
    receivedFrom: filters.receivedFrom.trim() || undefined,
    receivedTo: filters.receivedTo.trim() || undefined
  };
}

function buildCandidateFilters(filters: CandidateFilterState): DocumentCandidateFilters {
  return {
    index: 0,
    size: 20,
    status: filters.status.trim() || undefined,
    sourceRef: filters.sourceRef.trim() || undefined,
    keyword: filters.keyword.trim() || undefined
  };
}

function importViewFromPublish(publish: DocumentPublishView): DocumentImportView {
  return {
    id: publish.importId || publish.id,
    projectId: publish.projectId,
    title: publish.title,
    sourceType: publish.sourceType,
    sourceRef: publish.sourceRef,
    sourceUrl: publish.sourceUrl,
    status: publish.status,
    createdRequirements: publish.totalCreated,
    requirementCount: publish.totalParsed,
    errorMessage: publish.errorMessage,
    createdAt: publish.createdAt,
    updatedAt: publish.updatedAt
  };
}

function sourceHealthStatus(health: DocumentSourceHealthView) {
  if (health.ready) {
    return 'UP';
  }
  if (!health.dataFlowSupported) {
    return 'PLANNED';
  }
  if (health.lastEventStatus === 'FAILED' || health.lastErrorMessage) {
    return 'DEGRADED';
  }
  return health.sourceStatus ?? 'DOWN';
}

function formatBytes(value?: number) {
  if (!value || !Number.isFinite(value)) {
    return '-';
  }
  if (value >= 1024 * 1024) {
    return `${Math.round(value / 1024 / 1024)} MB`;
  }
  if (value >= 1024) {
    return `${Math.round(value / 1024)} KB`;
  }
  return `${value} B`;
}

function secondsLabel(value?: number) {
  if (value === undefined || value === null || !Number.isFinite(value)) {
    return '-';
  }
  return `${value}s`;
}

function PublishRecordRow(props: { record: DocumentPublishRecordView }) {
  return (
    <div className="publish-record-row">
      <span>
        <strong>{props.record.title}</strong>
        <em>{props.record.action} · {props.record.candidateId}</em>
      </span>
      <DocumentStatusPill value={props.record.result || props.record.candidateStatus} />
      {props.record.diffSummary && <em>差异：{props.record.diffSummary}</em>}
      {props.record.errorMessage && <FailureHint message={props.record.errorMessage} />}
    </div>
  );
}

function FailureHint(props: { message: string }) {
  const formatted = documentInputErrorMessage(new Error(props.message), props.message);
  return <em className="document-failure-hint">{formatted}</em>;
}

function SourceTypeBadge(props: { type: DocumentSourceType }) {
  return (
    <span className={`source-type-badge ${isReservedSourceType(props.type) ? 'reserved' : 'ready'}`}>
      {sourceTypeLabel(props.type)}
      <em>{isReservedSourceType(props.type) ? '预留/未启用' : '可用'}</em>
    </span>
  );
}

function DocumentStatusPill(props: { value: string }) {
  const normalized = props.value.toUpperCase();
  const positive = ['UP', 'ON', 'OK', 'SUCCESS', 'SUCCEEDED', 'COMPLETED', 'DONE', 'ENABLED', 'ACTIVE', 'CONFIRMED', 'PUBLISHED', 'VALID', '可用', '成功'];
  const pending = ['PENDING', 'RUNNING', 'PROCESSING', 'QUEUED', 'DRAFT', 'RESERVED', 'REPLAYING', 'PLANNED', 'DEGRADED', '预留/未启用'];
  const negative = ['DOWN', 'OFF', 'FAILED', 'ERROR', 'DISABLED', 'CANCELED', 'IGNORED', 'INVALID', 'CONFLICT', '异常', '失败'];
  const tone = positive.includes(normalized) || positive.includes(props.value)
    ? 'positive'
    : pending.includes(normalized) || pending.includes(props.value)
      ? 'pending'
      : negative.includes(normalized) || negative.includes(props.value)
        ? 'negative'
        : 'neutral';
  return <span className={`status-pill ${tone}`}>{props.value}</span>;
}

function StatusMetric(props: { label: string; value: string; pill?: boolean }) {
  return (
    <div className="status-item">
      <span>{props.label}</span>
      {props.pill ? <DocumentStatusPill value={props.value} /> : <strong>{props.value}</strong>}
    </div>
  );
}

function StateLine(props: { state: WorkState }) {
  if (props.state.loading) {
    return <span className="document-state-line">提交中</span>;
  }
  if (props.state.error) {
    return <span className="document-state-line error">{props.state.error}</span>;
  }
  if (props.state.success) {
    return (
      <span className="document-state-line success">
        {props.state.success}
        {props.state.traceId ? ` · ${props.state.traceId}` : ''}
      </span>
    );
  }
  return null;
}

function sourceStatus(source: DocumentSourceView) {
  if (source.status) {
    return source.status;
  }
  if (source.enabled === false || isReservedSourceType(source.sourceType)) {
    return '预留/未启用';
  }
  return '可用';
}

function errorMessage(error: unknown, fallback: string) {
  return documentInputErrorMessage(error, fallback);
}
