import {
  Activity,
  CheckCircle2,
  Eye,
  FileText,
  History,
  ListChecks,
  Pencil,
  Plus,
  RefreshCw,
  RotateCcw,
  Save,
  Send,
  Settings,
  Upload,
  Webhook,
  XCircle
} from 'lucide-react';
import { Drawer } from 'antd';
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
import { dictionaryLabel, fieldLabel } from '../platform/dictionaries';
import { translate } from '../platform/i18n';

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

const ASYNC_IMPORT_STATUSES = new Set(['MODEL_PARSE_QUEUED', 'MODEL_PARSE_RUNNING', 'PUBLISH_QUEUED', 'PUBLISHING']);
const ASYNC_WEBHOOK_STATUSES = new Set(['ACCEPTED', 'PROCESSING']);
const EVENT_POLL_INTERVAL_MS = 1500;

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
  const [sourceDrawerOpen, setSourceDrawerOpen] = useState(false);
  const [importDraft, setImportDraft] = useState<ImportDraft>(initialImportDraft);
  const [importDrawerOpen, setImportDrawerOpen] = useState(false);
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
      errors.push(errorMessage(healthResult.reason, translate('auto.k0672')));
    }

    if (sourceResult.status === 'fulfilled') {
      setSources(sourceResult.value.data);
      traceIds.push(sourceResult.value.trace_id);
    } else {
      errors.push(errorMessage(sourceResult.reason, translate('auto.k0673')));
    }

    if (mappingResult.status === 'fulfilled') {
      setMappingText(JSON.stringify(mappingResult.value.data ?? {}, null, 2));
      traceIds.push(mappingResult.value.trace_id);
    } else {
      errors.push(errorMessage(mappingResult.reason, translate('auto.k0674')));
    }

    if (importResult.status === 'fulfilled') {
      setImports(importResult.value.data.items);
      traceIds.push(importResult.value.trace_id);
    } else {
      errors.push(errorMessage(importResult.reason, translate('auto.k0675')));
    }

    if (eventResult.status === 'fulfilled') {
      setWebhookEvents(eventResult.value.data.items);
      traceIds.push(eventResult.value.trace_id);
    } else {
      errors.push(errorMessage(eventResult.reason, translate('auto.k0676')));
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
          setDetailState({ loading: false, error: errorMessage(detailResult.reason, translate('auto.k0677')) });
        }
        if (candidateResult.status === 'fulfilled') {
          setCandidates(candidateResult.value.data.items);
          setCandidateDrafts(candidateDraftMap(candidateResult.value.data.items));
          setCandidateState({ loading: false, traceId: candidateResult.value.trace_id });
        } else {
          setCandidates([]);
          setCandidateDrafts({});
          setCandidateState({ loading: false, error: errorMessage(candidateResult.reason, translate('auto.k0678')) });
        }
        if (publishRecordResult.status === 'fulfilled') {
          setPublishRecords(publishRecordResult.value.data.items);
          setPublishRecordState({ loading: false, traceId: publishRecordResult.value.trace_id });
        } else {
          setPublishRecords([]);
          setPublishRecordState({ loading: false, error: errorMessage(publishRecordResult.reason, translate('auto.k0679')) });
        }
      })
      .catch((error: unknown) => {
        if (!active) return;
        setImportDetail(null);
        setCandidates([]);
        setPublishRecords([]);
        setDetailState({ loading: false, error: errorMessage(error, translate('auto.k0677')) });
        setCandidateState({ loading: false, error: errorMessage(error, translate('auto.k0678')) });
        setPublishRecordState({ loading: false, error: errorMessage(error, translate('auto.k0679')) });
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
        setEventDetailState({ loading: false, error: errorMessage(error, translate('auto.k0680')) });
      });

    return () => {
      active = false;
    };
  }, [props.signedIn, selectedEventId]);

  useEffect(() => {
    if (!selectedImportId || !props.signedIn || !isAsyncImportStatus(importDetail?.status)) {
      return;
    }

    let active = true;
    const interval = window.setInterval(() => {
      Promise.allSettled([
        fetchDocumentImport(selectedImportId),
        fetchDocumentCandidates(selectedImportId, buildCandidateFilters(candidateFilters)),
        fetchDocumentPublishRecords(selectedImportId),
        fetchDocumentImports()
      ])
        .then(([detailResult, candidateResult, publishRecordResult, importResult]) => {
          if (!active) return;
          if (detailResult.status === 'fulfilled') {
            setImportDetail(detailResult.value.data);
            setLastImportResult((current) => current?.id === detailResult.value.data.id ? detailResult.value.data : current);
            setDetailState((current) => ({ ...current, traceId: detailResult.value.trace_id }));
          }
          if (candidateResult.status === 'fulfilled') {
            setCandidates(candidateResult.value.data.items);
            setCandidateDrafts(candidateDraftMap(candidateResult.value.data.items));
          }
          if (publishRecordResult.status === 'fulfilled') {
            setPublishRecords(publishRecordResult.value.data.items);
            setPublishRecordState((current) => ({ ...current, traceId: publishRecordResult.value.trace_id }));
          }
          if (importResult.status === 'fulfilled') {
            setImports(importResult.value.data.items);
          }
        })
        .catch(() => {
          // Keep the last visible state; manual refresh still reports detailed errors.
        });
    }, EVENT_POLL_INTERVAL_MS);

    return () => {
      active = false;
      window.clearInterval(interval);
    };
  }, [candidateFilters, importDetail?.status, props.signedIn, selectedImportId]);

  useEffect(() => {
    if (!selectedEventId || !props.signedIn || !isAsyncWebhookStatus(selectedEvent?.status)) {
      return;
    }

    let active = true;
    const interval = window.setInterval(() => {
      Promise.allSettled([
        fetchWebhookEvent(selectedEventId),
        fetchWebhookEvents(buildEventFilters(eventFilters))
      ])
        .then(([detailResult, listResult]) => {
          if (!active) return;
          if (detailResult.status === 'fulfilled') {
            setSelectedEvent(detailResult.value.data);
            setEventDetailState((current) => ({ ...current, traceId: detailResult.value.trace_id }));
          }
          if (listResult.status === 'fulfilled') {
            setWebhookEvents(listResult.value.data.items);
            setEventState((current) => ({ ...current, traceId: listResult.value.trace_id }));
          }
        })
        .catch(() => {
          // Polling is best-effort; the refresh button keeps explicit error reporting.
        });
    }, EVENT_POLL_INTERVAL_MS);

    return () => {
      active = false;
      window.clearInterval(interval);
    };
  }, [eventFilters, props.signedIn, selectedEvent?.status, selectedEventId]);

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
      setCandidateState({ loading: false, error: errorMessage(error, translate('auto.k0678')) });
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
      setEventState({ loading: false, error: translate('auto.k0681') });
      return;
    }
    setEventState({ loading: true });
    try {
      const traceId = await reloadWebhookEvents();
      setEventState({ loading: false, success: translate('auto.k0682'), traceId });
    } catch (error: unknown) {
      setEventState({ loading: false, error: errorMessage(error, translate('auto.k0683')) });
    }
  }

  async function checkSourceHealth(source: DocumentSourceView) {
    if (!props.signedIn || !source.id) {
      setSourceHealthState({ loading: false, error: translate('auto.k0684') });
      return;
    }

    setSourceHealthState({ loading: true });
    try {
      const response = await fetchDocumentSourceHealth(source.id);
      setSourceHealth((current) => ({ ...current, [source.id]: response.data }));
      setSourceHealthState({ loading: false, success: translate('auto.k0685', { value0: source.sourceCode ?? source.title }), traceId: response.trace_id });
    } catch (error: unknown) {
      setSourceHealthState({ loading: false, error: errorMessage(error, translate('auto.k0686')) });
    }
  }

  async function submitSource(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!props.signedIn) {
      setSourceState({ loading: false, error: translate('auto.k0687') });
      return;
    }
    if (!canManageSources) {
      setSourceState({ loading: false, error: translate('auto.k0688') });
      return;
    }
    if (!sourceDraft.sourceCode.trim() || !sourceDraft.name.trim()) {
      setSourceState({ loading: false, error: translate('auto.k0689') });
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
      setSourceDrawerOpen(false);
      setSourceState({ loading: false, success: editingSourceId ? translate('auto.k0690') : translate('auto.k0691'), traceId });
    } catch (error: unknown) {
      setSourceState({ loading: false, error: errorMessage(error, translate('auto.k0692')) });
    }
  }

  async function submitImport(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!props.signedIn) {
      setImportState({ loading: false, error: translate('auto.k0693') });
      return;
    }
    if (!canImportDocument) {
      setImportState({ loading: false, error: translate('auto.k0694') });
      return;
    }
    if (!importDraft.projectId.trim()) {
      setImportState({ loading: false, error: translate('auto.k0695') });
      return;
    }
    if (!importDraft.content.trim() && !importFile) {
      setImportState({ loading: false, error: translate('auto.k0696') });
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
      setImportDrawerOpen(false);
      setImportState({ loading: false, success: translate('auto.k0697'), traceId });
    } catch (error: unknown) {
      try {
        await reloadImports();
      } catch {
        // Import failure detail is still available in the API error shown below.
      }
      setImportState({ loading: false, error: errorMessage(error, translate('auto.k0698')) });
    }
  }

  async function submitMapping(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!props.signedIn) {
      setMappingState({ loading: false, error: translate('auto.k0699') });
      return;
    }
    if (!canManageSources) {
      setMappingState({ loading: false, error: translate('auto.k0688') });
      return;
    }

    let parsed: unknown;
    try {
      parsed = JSON.parse(mappingText || '{}');
    } catch {
      setMappingState({ loading: false, error: translate('auto.k0700') });
      return;
    }

    setMappingState({ loading: true });
    try {
      const response = await updateDocumentFieldMapping(parsed);
      setMappingState({ loading: false, success: translate('auto.k0701'), traceId: response.trace_id });
    } catch (error: unknown) {
      setMappingState({ loading: false, error: errorMessage(error, translate('auto.k0702')) });
    }
  }

  async function saveCandidate(candidateId: string) {
    const draft = candidateDrafts[candidateId];
    if (!props.signedIn || !draft) {
      setCandidateState({ loading: false, error: translate('auto.k0703') });
      return;
    }
    if (!canReviewCandidates) {
      setCandidateState({ loading: false, error: translate('auto.k0704') });
      return;
    }
    if (!draft.title.trim()) {
      setCandidateState({ loading: false, error: translate('auto.k0705') });
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
      setCandidateState({ loading: false, success: translate('auto.k0706'), traceId: response.trace_id });
    } catch (error: unknown) {
      setCandidateState({ loading: false, error: errorMessage(error, translate('auto.k0707')) });
    }
  }

  async function confirmCandidate(candidateId: string) {
    if (!props.signedIn) {
      setCandidateState({ loading: false, error: translate('auto.k0708') });
      return;
    }
    if (!canReviewCandidates) {
      setCandidateState({ loading: false, error: translate('auto.k0704') });
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
      setCandidateState({ loading: false, success: translate('auto.k0709'), traceId: response.trace_id });
    } catch (error: unknown) {
      setCandidateState({ loading: false, error: errorMessage(error, translate('auto.k0710')) });
    }
  }

  async function ignoreCandidate(candidateId: string) {
    const reason = candidateDrafts[candidateId]?.ignoreReason.trim() || '';
    if (!props.signedIn) {
      setCandidateState({ loading: false, error: translate('auto.k0711') });
      return;
    }
    if (!canReviewCandidates) {
      setCandidateState({ loading: false, error: translate('auto.k0704') });
      return;
    }
    if (!reason) {
      setCandidateState({ loading: false, error: translate('auto.k0712') });
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
      setCandidateState({ loading: false, success: translate('auto.k0713'), traceId: response.trace_id });
    } catch (error: unknown) {
      setCandidateState({ loading: false, error: errorMessage(error, translate('auto.k0714')) });
    }
  }

  async function batchCandidates(action: DocumentCandidateBatchAction) {
    if (!props.signedIn) {
      setCandidateState({ loading: false, error: translate('auto.k0715') });
      return;
    }
    if (!canReviewCandidates) {
      setCandidateState({ loading: false, error: translate('auto.k0704') });
      return;
    }
    if (selectedCandidateIds.length === 0) {
      setCandidateState({ loading: false, error: translate('auto.k0716') });
      return;
    }
    if (action === 'IGNORE' && !batchIgnoreReason.trim()) {
      setCandidateState({ loading: false, error: translate('auto.k0717') });
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
        success: translate('auto.k0718', { value0: action === 'CONFIRM' ? translate('auto.k0807') : translate('auto.k0808'), value1: response.data.succeededCount, value2: response.data.failedCount }),
        traceId
      });
    } catch (error: unknown) {
      setCandidateState({ loading: false, error: errorMessage(error, translate('auto.k0719')) });
    }
  }

  async function publishImport(dryRun: boolean) {
    if (!props.signedIn || !selectedImportId) {
      setPublishingState({ loading: false, error: translate('auto.k0720') });
      return;
    }
    if (!canPublishCandidates) {
      setPublishingState({ loading: false, error: translate('auto.k0721') });
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
        setPublishingState({ loading: false, success: translate('auto.k0722'), traceId: response.trace_id });
        return;
      }
      const importView = importViewFromPublish(response.data);
      setImportDetail(importView);
      setLastImportResult(importView);
      await reloadImports();
      const candidateTraceId = await reloadCandidates(selectedImportId);
      const recordTraceId = await reloadPublishRecords(selectedImportId);
      setPublishingState({ loading: false, success: translate('auto.k0723'), traceId: recordTraceId || candidateTraceId || response.trace_id });
    } catch (error: unknown) {
      setPublishingState({ loading: false, error: errorMessage(error, translate('auto.k0724')) });
    }
  }

  async function replaySelectedEvent() {
    if (!props.signedIn || !selectedEventId) {
      setReplayState({ loading: false, error: translate('auto.k0725') });
      return;
    }
    if (!canReplayWebhook) {
      setReplayState({ loading: false, error: translate('auto.k0726') });
      return;
    }

    setReplayState({ loading: true });
    try {
      const response = await replayWebhookEvent(selectedEventId);
      setSelectedEvent(response.data);
      await reloadWebhookEvents();
      setReplayState({ loading: false, success: translate('auto.k0727'), traceId: response.trace_id });
    } catch (error: unknown) {
      setReplayState({ loading: false, error: errorMessage(error, translate('auto.k0728')) });
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
    setSourceDrawerOpen(true);
  }

  function openCreateSourceDrawer() {
    setEditingSourceId('');
    setSourceDraft(initialSourceDraft);
    setSourceState({ loading: false });
    setSourceDrawerOpen(true);
  }

  function resetSourceDraft() {
    setEditingSourceId('');
    setSourceDraft(initialSourceDraft);
    setSourceState({ loading: false });
    setSourceDrawerOpen(false);
  }

  function openImportDrawer() {
    setImportState({ loading: false });
    setImportDrawerOpen(true);
  }

  function closeImportDrawer() {
    setImportDrawerOpen(false);
    setImportState({ loading: false });
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
                <span className="eyebrow">{translate('auto.k0729')}</span>
                <h2>{translate('auto.k0729')}</h2>
              </div>
            </div>
            <div className="panel-toolbar-actions">
              <button
                className="primary-button"
                type="button"
                disabled={sourceDisabled || sourceState.loading}
                onClick={openCreateSourceDrawer}
              >
                <Plus size={16} />
                {translate('auto.k0738')}
              </button>
              <button className="secondary-button" type="button" disabled={!props.signedIn || loadState.loading} onClick={refreshAll}>
                <RefreshCw size={16} />
                {translate('auto.k0170')}</button>
            </div>
          </div>

          <Drawer
            className="document-source-drawer"
            destroyOnHidden
            maskClosable={!sourceState.loading}
            open={sourceDrawerOpen}
            placement="right"
            title={editingSourceId ? translate('auto.k0737') : translate('auto.k0738')}
            width={720}
            onClose={() => {
              if (!sourceState.loading) {
                resetSourceDraft();
              }
            }}
          >
          <form className="document-form document-drawer-form" onSubmit={submitSource}>
            <div className="document-form-grid">
              <label className="field" htmlFor="source-project-id">
                <span>{fieldLabel('defaultProjectId')}</span>
                <input
                  id="source-project-id"
                  value={sourceDraft.defaultProjectId}
                  disabled={sourceDisabled || sourceState.loading}
                  onChange={(event) => setSourceDraft((current) => ({ ...current, defaultProjectId: event.target.value }))}
                  placeholder="proj-payments"
                />
              </label>
              <label className="field" htmlFor="source-title">
                <span>{translate('auto.k0177')}<b>*</b></span>
                <input
                  id="source-title"
                  value={sourceDraft.name}
                  disabled={sourceDisabled || sourceState.loading}
                  onChange={(event) => setSourceDraft((current) => ({ ...current, name: event.target.value }))}
                  placeholder={translate('auto.k0730')}
                />
              </label>
              <label className="field" htmlFor="source-type">
                <span>{fieldLabel('sourceType')}</span>
                <select
                  id="source-type"
                  value={sourceDraft.sourceType}
                  disabled={sourceDisabled || sourceState.loading}
                  onChange={(event) => setSourceDraft((current) => ({ ...current, sourceType: event.target.value as DocumentSourceType }))}
                >
                  {documentSourceTypeOptions.map((option) => (
                    <option key={option.value} value={option.value}>
                      {option.label}{option.reserved ? translate('auto.k0731') : ''}
                    </option>
                  ))}
                </select>
                <small>{sourceTypeReserved ? translate('auto.k0732') : translate('auto.k0733')}</small>
              </label>
              <label className="field" htmlFor="source-status">
                <span>{fieldLabel('status')}</span>
                <select
                  id="source-status"
                  value={sourceDraft.status}
                  disabled={sourceDisabled || sourceState.loading}
                  onChange={(event) => setSourceDraft((current) => ({ ...current, status: event.target.value }))}
                >
                  <option value="">{translate('auto.k0734')}</option>
                  <option value="ENABLED">{dictionaryLabel('ENABLED')}</option>
                  <option value="DISABLED">{dictionaryLabel('DISABLED')}</option>
                  <option value="PLANNED">{dictionaryLabel('PLANNED')}</option>
                </select>
              </label>
              <label className="field" htmlFor="source-code">
                <span>{fieldLabel('sourceCode')}<b>*</b></span>
                <input
                  id="source-code"
                  value={sourceDraft.sourceCode}
                  disabled={sourceDisabled || sourceState.loading}
                  onChange={(event) => setSourceDraft((current) => ({ ...current, sourceCode: event.target.value }))}
                  placeholder="payment-docs"
                />
              </label>
              <label className="field" htmlFor="source-mapping-id">
                <span>{fieldLabel('mappingId')}</span>
                <input
                  id="source-mapping-id"
                  value={sourceDraft.mappingId}
                  disabled={sourceDisabled || sourceState.loading}
                  onChange={(event) => setSourceDraft((current) => ({ ...current, mappingId: event.target.value }))}
                  placeholder={translate('auto.k0735')}
                />
              </label>
              <label className="field" htmlFor="source-secret-ref">
                <span>{fieldLabel('secretRef')}</span>
                <input
                  id="source-secret-ref"
                  value={sourceDraft.secretRef}
                  disabled={sourceDisabled || sourceState.loading}
                  onChange={(event) => setSourceDraft((current) => ({ ...current, secretRef: event.target.value }))}
                  placeholder="secret://wp4/payment-docs"
                />
              </label>
              <label className="field" htmlFor="source-event-version">
                <span>{fieldLabel('eventVersion')}</span>
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
                <span>{fieldLabel('mappingVersion')}</span>
                <input
                  id="source-mapping-version"
                  value={sourceDraft.mappingVersion}
                  disabled={sourceDisabled || sourceState.loading}
                  onChange={(event) => setSourceDraft((current) => ({ ...current, mappingVersion: event.target.value }))}
                  placeholder="default"
                />
              </label>
              <label className="field" htmlFor="source-url">
                <span>{fieldLabel('endpointUrl')}</span>
                <input
                  id="source-url"
                  value={sourceDraft.endpointUrl}
                  disabled={sourceDisabled || sourceState.loading}
                  onChange={(event) => setSourceDraft((current) => ({ ...current, endpointUrl: event.target.value }))}
                  placeholder="https://docs.example.test/spec"
                />
              </label>
              <label className="field" htmlFor="source-description">
                <span>{fieldLabel('description')}</span>
                <input
                  id="source-description"
                  value={sourceDraft.description}
                  disabled={sourceDisabled || sourceState.loading}
                  onChange={(event) => setSourceDraft((current) => ({ ...current, description: event.target.value }))}
                  placeholder={translate('auto.k0736')}
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
                {editingSourceId ? translate('auto.k0737') : translate('auto.k0738')}
              </button>
              <button className="secondary-button" type="button" disabled={sourceState.loading} onClick={resetSourceDraft}>
                <XCircle size={16} />
                {translate('auto.k0739')}</button>
              <StateLine state={sourceState} />
            </div>
          </form>
          </Drawer>

          <div className="table-wrap document-source-table">
            <table>
              <thead>
                <tr>
                  <th>{translate('auto.k0440')}</th>
                  <th>{translate('auto.k0740')}</th>
                  <th>{translate('auto.k0286')}</th>
                  <th>{fieldLabel('endpoint')}</th>
                  <th>{fieldLabel('webhook')}</th>
                  <th>{translate('auto.k0182')}</th>
                  <th>{translate('auto.k0741')}</th>
                  <th>{translate('auto.k0249')}</th>
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
                              <span>{source.secretRef ? translate('auto.k0742') : translate('auto.k0743')}</span>
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
                            <span className="table-secondary">{translate('auto.k0744')}</span>
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
                              {translate('auto.k0745')}</button>
                            <button
                              className="mini-button"
                              type="button"
                            disabled={!props.signedIn || !canManageSources || sourceState.loading || !source.id}
                              onClick={() => editSource(source)}
                            >
                              <Pencil size={14} />
                              {translate('auto.k0746')}</button>
                          </div>
                        </td>
                      </tr>
                    );
                  })
                ) : (
                  <tr>
                    <td className="table-empty" colSpan={8}>
                      {props.signedIn ? (loadState.loading ? translate('auto.k0168') : translate('auto.k0747')) : translate('auto.k0454')}
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
          <StateLine state={sourceHealthState} />
        </section>

        <section className="panel module-panel document-panel">
          <div className="panel-toolbar">
            <div className="section-heading compact">
              <div className="section-icon">
                <Upload size={20} />
              </div>
              <div>
                <span className="eyebrow">{translate('auto.k0748')}</span>
                <h2>{translate('auto.k0748')}</h2>
              </div>
            </div>
            <div className="panel-toolbar-actions">
              <button className="primary-button" type="button" disabled={importDisabled || importState.loading} onClick={openImportDrawer}>
                <Upload size={16} />
                {translate('auto.k0755')}
              </button>
            </div>
          </div>

          <Drawer
            className="document-import-drawer"
            destroyOnHidden
            maskClosable={!importState.loading}
            open={importDrawerOpen}
            placement="right"
            title={translate('auto.k0748')}
            width={760}
            onClose={() => {
              if (!importState.loading) {
                closeImportDrawer();
              }
            }}
          >
          <form className="document-form document-drawer-form" onSubmit={submitImport}>
            <div className="document-form-grid">
              <label className="field" htmlFor="import-project-id">
                <span>{fieldLabel('projectId')}<b>*</b></span>
                <input
                  id="import-project-id"
                  value={importDraft.projectId}
                  disabled={importDisabled || importState.loading}
                  onChange={(event) => setImportDraft((current) => ({ ...current, projectId: event.target.value }))}
                  placeholder="proj-payments"
                />
              </label>
              <label className="field" htmlFor="import-title">
                <span>{translate('auto.k0440')}</span>
                <input
                  id="import-title"
                  value={importDraft.title}
                  disabled={importDisabled || importState.loading}
                  onChange={(event) => setImportDraft((current) => ({ ...current, title: event.target.value }))}
                  placeholder={translate('auto.k0749')}
                />
              </label>
              <label className="field" htmlFor="import-source-type">
                <span>{fieldLabel('sourceType')}</span>
                <select
                  id="import-source-type"
                  value={importDraft.sourceType}
                  disabled={importDisabled || importState.loading}
                  onChange={(event) => setImportDraft((current) => ({ ...current, sourceType: event.target.value as DocumentSourceType }))}
                >
                  {documentSourceTypeOptions.map((option) => (
                    <option key={option.value} value={option.value}>
                      {option.label}{option.reserved ? translate('auto.k0731') : ''}
                    </option>
                  ))}
                </select>
                <small>{importTypeReserved ? translate('auto.k0750') : translate('auto.k0751')}</small>
              </label>
              <label className="field" htmlFor="import-source-ref">
                <span>{fieldLabel('sourceRef')}</span>
                <input
                  id="import-source-ref"
                  value={importDraft.sourceRef}
                  disabled={importDisabled || importState.loading}
                  onChange={(event) => setImportDraft((current) => ({ ...current, sourceRef: event.target.value }))}
                  placeholder="PRD-2026-001"
                />
              </label>
              <label className="field" htmlFor="import-source-url">
                <span>{fieldLabel('sourceUrl')}</span>
                <input
                  id="import-source-url"
                  value={importDraft.sourceUrl}
                  disabled={importDisabled || importState.loading}
                  onChange={(event) => setImportDraft((current) => ({ ...current, sourceUrl: event.target.value }))}
                  placeholder="https://docs.example.test/spec"
                />
              </label>
              <label className="field" htmlFor="import-source-id">
                <span>{fieldLabel('sourceId')}</span>
                <input
                  id="import-source-id"
                  value={importDraft.sourceId}
                  disabled={importDisabled || importState.loading}
                  onChange={(event) => setImportDraft((current) => ({ ...current, sourceId: event.target.value }))}
                  placeholder="uuid"
                />
              </label>
              <label className="field" htmlFor="import-mapping-id">
                <span>{fieldLabel('mappingId')}</span>
                <input
                  id="import-mapping-id"
                  value={importDraft.mappingId}
                  disabled={importDisabled || importState.loading}
                  onChange={(event) => setImportDraft((current) => ({ ...current, mappingId: event.target.value }))}
                  placeholder={translate('auto.k0735')}
                />
              </label>
            </div>
            <label className="field document-content-field" htmlFor="import-content">
              <span>{fieldLabel('content')}</span>
              <textarea
                id="import-content"
                value={importDraft.content}
                disabled={importDisabled || importState.loading || importFile !== null}
                onChange={(event) => setImportDraft((current) => ({ ...current, content: event.target.value }))}
                placeholder={translate('auto.k0752')}
              />
            </label>
            <label className="field document-upload-field" htmlFor="import-file">
              <span>{translate('auto.k0753')}</span>
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
                  : translate('auto.k0754')}
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
                {translate('auto.k0755')}</button>
              {importFile && (
                <button
                  className="secondary-button"
                  type="button"
                  disabled={importState.loading}
                  onClick={() => setImportFile(null)}
                >
                  <XCircle size={16} />
                  {translate('auto.k0756')}</button>
              )}
              <StateLine state={importState} />
            </div>
          </form>
          </Drawer>

          {lastImportResult && (
            <div className="document-result-strip">
              <DocumentStatusPill value={lastImportResult.status} />
              <span>{fieldLabel('createdRequirements')}：{lastImportResult.createdRequirements}</span>
              <span>{fieldLabel('requirementCount')}：{lastImportResult.requirementCount}</span>
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
              <span className="eyebrow">{translate('auto.k0757')}</span>
              <h2>{translate('auto.k0757')}</h2>
            </div>
          </div>
          <form className="document-form" onSubmit={submitMapping}>
            <label className="field document-content-field" htmlFor="field-mapping">
              <span>{fieldLabel('mapping JSON')}</span>
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
                {translate('auto.k0758')}</button>
              <StateLine state={mappingState} />
            </div>
          </form>
        </section>
      </div>

      <aside className="side-stack document-side-stack">
        <section className="panel insight-panel">
          <h2>{translate('auto.k0759')}</h2>
          <div className="document-health-grid">
            <StatusMetric label={translate('auto.k0427')} value={health?.service ?? 'document-input'} />
            <StatusMetric label={translate('auto.k0182')} value={health?.status ?? (props.signedIn ? translate('auto.k0428') : translate('auto.k0429'))} pill />
            <StatusMetric label={translate('auto.k0760')} value={String(sourceSummary.enabled)} />
            <StatusMetric label={translate('auto.k0761')} value={String(sourceSummary.reserved)} />
            <StatusMetric label={translate('auto.k0762')} value={health?.inputEnabled === false ? 'OFF' : 'ON'} pill />
            <StatusMetric label={fieldLabel('webhook')} value={health?.webhookEnabled === false ? 'OFF' : 'ON'} pill />
            <StatusMetric label={translate('auto.k0763')} value={health?.modelParseEnabled ? 'ON' : 'OFF'} pill />
            <StatusMetric label={fieldLabel('payload')} value={formatBytes(health?.webhookMaxPayloadBytes)} />
            <StatusMetric label={translate('auto.k0764')} value={formatBytes(health?.importMaxContentBytes)} />
            <StatusMetric label={translate('auto.k0765')} value={formatBytes(health?.documentBinaryMaxBytes)} />
            <StatusMetric label="OCR" value={health?.ocrConfigured ? 'READY' : 'OFF'} pill />
            <StatusMetric label="OCR Worker" value={health?.ocrWorkerMode ?? '-'} pill />
            <StatusMetric label="OCR Worker 端点" value={health?.ocrRemoteWorkerConfigured ? 'ON' : 'OFF'} pill />
            <StatusMetric label="OCR Worker Token" value={health?.ocrWorkerTokenConfigured ? 'SET' : 'OFF'} pill />
            <StatusMetric label={translate('auto.k0766')} value={health?.ocrLocalCommandExecutionAllowed ? 'ON' : 'OFF'} pill />
            <StatusMetric label="OCR 兜底" value={health?.ocrLocalCommandFallbackEnabled ? 'ON' : 'OFF'} pill />
            <StatusMetric label={translate('auto.k0767')} value={`${health?.ocrAvailablePermits ?? '-'} / ${health?.ocrMaxConcurrentProcesses ?? '-'}`} />
            <StatusMetric label={translate('auto.k0768')} value={health?.ocrTimeoutSeconds ? `${health.ocrTimeoutSeconds}s` : '-'} />
            <StatusMetric label={fieldLabel('secretProvider')} value={health?.externalSecretProvider?.status ?? '-'} pill />
            <StatusMetric label={translate('auto.k0769')} value={health?.webhookSecretCacheEnabled ? 'ON' : 'OFF'} pill />
            <StatusMetric label={translate('auto.k0770')} value={secondsLabel(health?.webhookSecretCacheTtlSeconds)} />
            <StatusMetric label={translate('auto.k0771')} value={secondsLabel(health?.webhookSecretRotationOverlapSeconds)} />
            <StatusMetric label={translate('auto.k0772')} value={String(health?.webhookSecretCacheSize ?? '-')} />
            <StatusMetric label={translate('auto.k0773')} value={String(health?.batchActionLimit ?? '-')} />
          </div>
          {loadState.error && (
            <div className="inline-error">
              <strong>{translate('auto.k0148')}</strong>
              <span>{loadState.error}</span>
            </div>
          )}
          {loadState.traceId && <div className="panel-trace">Trace ID：{loadState.traceId}</div>}
        </section>

        <section className="panel insight-panel document-history-panel">
          <div className="panel-title-row">
            <h2>{translate('auto.k0774')}</h2>
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
                    <em>{item.requirementCount} {translate('auto.k0181')}</em>
                  </span>
                </button>
              ))
            ) : (
              <div className="empty-state compact">
                <History size={20} />
                <div>
                  <strong>{props.signedIn ? translate('auto.k0775') : translate('auto.k0429')}</strong>
                  <span>{props.signedIn ? translate('auto.k0776') : translate('auto.k0777')}</span>
                </div>
              </div>
            )}
          </div>
        </section>

        <section className="panel insight-panel">
          <div className="panel-title-row">
            <h2>{translate('auto.k0778')}</h2>
            <div className="panel-title-actions">
              <button className="mini-button" type="button" disabled={!props.signedIn || !canPublishCandidates || !selectedImportId || publishingState.loading} onClick={() => publishImport(true)}>
                <Eye size={14} />
                {fieldLabel('dryRun')}
              </button>
              <button className="mini-button" type="button" disabled={!props.signedIn || !canPublishCandidates || !selectedImportId || publishingState.loading} onClick={() => publishImport(false)}>
                <Send size={14} />
                {translate('auto.k0779')}</button>
              <FileText size={18} />
            </div>
          </div>
          {importDetail ? (
            <div className="document-detail-list">
              <strong>{importDetail.title}</strong>
              <div>
                <span>{translate('auto.k0182')}</span>
                <DocumentStatusPill value={importDetail.status} />
              </div>
              <div>
                <span>{fieldLabel('createdRequirements')}</span>
                <em>{importDetail.createdRequirements}</em>
              </div>
              <div>
                <span>{fieldLabel('requirementCount')}</span>
                <em>{importDetail.requirementCount}</em>
              </div>
              <div>
                <span>{translate('auto.k0179')}</span>
                <em>{importDetail.sourceUrl || importDetail.sourceRef || sourceTypeLabel(importDetail.sourceType)}</em>
              </div>
              {importDetail.errorMessage && (
                <div>
                  <span>{translate('auto.k0780')}</span>
                  <FailureHint message={importDetail.errorMessage} />
                </div>
              )}
              {importDetail.requirements && importDetail.requirements.length > 0 && (
                <div className="document-requirement-preview">
                  <span>{translate('auto.k0781')}</span>
                  {importDetail.requirements.slice(0, 5).map((requirement, index) => (
                    <em key={requirement.id ?? `${requirement.title}-${index}`}>
                      {[requirement.title ?? requirement.id ?? translate('auto.k0782', { value0: index + 1 }), requirement.parseSource].filter(Boolean).join(' · ')}
                    </em>
                  ))}
                </div>
              )}
              {publishPreview && (
                <div className="document-publish-summary">
                  <strong>{publishPreview.dryRun ? translate('auto.k0783') : translate('auto.k0784')}</strong>
                  <div className="document-publish-metrics">
                    <StatusMetric label={translate('auto.k0785')} value={String(publishPreview.plannedCreateCount)} />
                    <StatusMetric label={translate('auto.k0786')} value={String(publishPreview.plannedUpdateCount)} />
                    <StatusMetric label={translate('auto.k0787')} value={String(publishPreview.linkedExistingCount)} />
                    <StatusMetric label={translate('auto.k0788')} value={String(publishPreview.conflictCount)} />
                    <StatusMetric label={translate('auto.k0789')} value={String(publishPreview.skippedCount)} />
                    <StatusMetric label={translate('auto.k0369')} value={String(publishPreview.publishFailedCount)} />
                  </div>
                  {selectedCandidateIds.length > 0 && <span className="table-secondary">{translate('auto.k0790')}{selectedCandidateIds.length} {translate('auto.k0791')}</span>}
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
                  <strong>{translate('auto.k0792')}</strong>
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
                <strong>{detailState.loading ? translate('auto.k0437') : translate('auto.k0793')}</strong>
                <span>{detailState.error ?? translate('auto.k0794')}</span>
              </div>
            </div>
          )}
        </section>

        <section className="panel insight-panel document-candidate-panel">
          <div className="panel-title-row">
            <h2>{translate('auto.k0795')}</h2>
            <span className="document-count-badge">{candidates.length}</span>
          </div>
          {selectedImportId && (
            <div className="document-candidate-toolbar candidate-filter-toolbar">
              <select
                value={candidateFilters.status}
                disabled={candidateState.loading}
                onChange={(event) => setCandidateFilters((current) => ({ ...current, status: event.target.value }))}
                aria-label={translate('auto.k0796')}
              >
                <option value="">{translate('auto.k0367')}</option>
                <option value="PENDING">{dictionaryLabel('PENDING')}</option>
                <option value="CONFIRMED">{dictionaryLabel('CONFIRMED')}</option>
                <option value="IGNORED">{dictionaryLabel('IGNORED')}</option>
                <option value="PUBLISH_QUEUED">{dictionaryLabel('PUBLISH_QUEUED')}</option>
                <option value="PUBLISHING">{dictionaryLabel('PUBLISHING')}</option>
                <option value="PUBLISHED">{dictionaryLabel('PUBLISHED')}</option>
                <option value="PUBLISH_FAILED">{dictionaryLabel('PUBLISH_FAILED')}</option>
              </select>
              <input
                type="text"
                value={candidateFilters.sourceRef}
                disabled={candidateState.loading}
                onChange={(event) => setCandidateFilters((current) => ({ ...current, sourceRef: event.target.value }))}
                placeholder={fieldLabel('sourceRef')}
              />
              <input
                type="text"
                value={candidateFilters.keyword}
                disabled={candidateState.loading}
                onChange={(event) => setCandidateFilters((current) => ({ ...current, keyword: event.target.value }))}
                placeholder={translate('auto.k0797')}
              />
              <button
                className="mini-button"
                type="button"
                disabled={candidateState.loading}
                onClick={() => void refreshCandidates()}
              >
                <RefreshCw size={14} />
                {translate('auto.k0170')}</button>
              <button
                className="mini-button"
                type="button"
                disabled={candidateState.loading}
                onClick={() => setCandidateFilters(initialCandidateFilters)}
              >
                <XCircle size={14} />
                {translate('auto.k0416')}</button>
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
                <span>{translate('auto.k0798')}</span>
              </label>
              <span className="table-secondary">{translate('auto.k0799')}{selectedCandidateIds.length}</span>
              <input
                value={batchIgnoreReason}
                disabled={candidateDisabled || candidateState.loading}
                onChange={(event) => setBatchIgnoreReason(event.target.value)}
                placeholder={translate('auto.k0800')}
              />
              <button
                className="mini-button"
                type="button"
                disabled={candidateDisabled || candidateState.loading || selectedCandidateIds.length === 0}
                onClick={() => batchCandidates('CONFIRM')}
              >
                <ListChecks size={14} />
                {translate('auto.k0801')}</button>
              <button
                className="mini-button"
                type="button"
                disabled={candidateDisabled || candidateState.loading || selectedCandidateIds.length === 0 || !batchIgnoreReason.trim()}
                onClick={() => batchCandidates('IGNORE')}
              >
                <XCircle size={14} />
                {translate('auto.k0802')}</button>
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
                      <span>{translate('auto.k0440')}</span>
                      <input
                        id={`candidate-title-${candidate.id}`}
                        value={draft.title}
                        disabled={candidateDisabled || candidateState.loading}
                        onChange={(event) => updateCandidateDraft(candidate.id, { title: event.target.value })}
                      />
                    </label>
                    <label className="field" htmlFor={`candidate-priority-${candidate.id}`}>
                      <span>{translate('auto.k0419')}</span>
                      <input
                        id={`candidate-priority-${candidate.id}`}
                        value={draft.priority}
                        disabled={candidateDisabled || candidateState.loading}
                        onChange={(event) => updateCandidateDraft(candidate.id, { priority: event.target.value })}
                        placeholder="HIGH / MEDIUM / LOW"
                      />
                    </label>
                    <label className="field" htmlFor={`candidate-description-${candidate.id}`}>
                      <span>{translate('auto.k0443')}</span>
                      <textarea
                        id={`candidate-description-${candidate.id}`}
                        className="compact-textarea"
                        value={draft.description}
                        disabled={candidateDisabled || candidateState.loading}
                        onChange={(event) => updateCandidateDraft(candidate.id, { description: event.target.value })}
                      />
                    </label>
                    <label className="field" htmlFor={`candidate-acceptance-${candidate.id}`}>
                      <span>{translate('auto.k0650')}</span>
                      <textarea
                        id={`candidate-acceptance-${candidate.id}`}
                        className="compact-textarea"
                        value={draft.acceptanceCriteria}
                        disabled={candidateDisabled || candidateState.loading}
                        onChange={(event) => updateCandidateDraft(candidate.id, { acceptanceCriteria: event.target.value })}
                      />
                    </label>
                    <label className="field" htmlFor={`candidate-tags-${candidate.id}`}>
                      <span>{translate('auto.k0803')}</span>
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
                        {candidate.sourceRef && <span>{fieldLabel('sourceRef')}：{candidate.sourceRef}</span>}
                        {candidate.parseSource && <span>{fieldLabel('parseSource')}：{candidate.parseSource}</span>}
                        {candidate.modelInvocationId && <span>{fieldLabel('modelInvocationId')}：{candidate.modelInvocationId}</span>}
                        {candidate.modelProviderName && <span>{fieldLabel('modelProviderName')}：{candidate.modelProviderName}</span>}
                        {candidate.modelName && <span>{fieldLabel('modelName')}：{candidate.modelName}</span>}
                        {candidate.externalRequirementId && <span>{fieldLabel('externalRequirementId')}：{candidate.externalRequirementId}</span>}
                        {candidate.assetRequirementId && <span>{fieldLabel('assetRequirementId')}：{candidate.assetRequirementId}</span>}
                        {typeof candidate.version === 'number' && <span>{fieldLabel('version')}：{candidate.version}</span>}
                        {candidate.confirmedBy && <span>{fieldLabel('confirmedBy')}：{candidate.confirmedBy}</span>}
                        {candidate.confirmedAt && <span>{fieldLabel('confirmedAt')}：{candidate.confirmedAt}</span>}
                        {candidate.ignoredReason && <span>{fieldLabel('ignoredReason')}：{candidate.ignoredReason}</span>}
                        {candidate.sourceFragment && <span>{candidate.sourceFragment}</span>}
                        {candidate.errorMessage && <FailureHint message={candidate.errorMessage} />}
                      </div>
                    )}
                    <label className="field" htmlFor={`candidate-ignore-${candidate.id}`}>
                      <span>{translate('auto.k0804')}</span>
                      <input
                        id={`candidate-ignore-${candidate.id}`}
                        value={draft.ignoreReason}
                        disabled={candidateDisabled || candidateState.loading}
                        onChange={(event) => updateCandidateDraft(candidate.id, { ignoreReason: event.target.value })}
                        placeholder={translate('auto.k0805')}
                      />
                    </label>
                    <div className="document-actions candidate-actions">
                      <button className="mini-button" type="button" disabled={candidateDisabled || candidateState.loading || !draft.title.trim()} onClick={() => saveCandidate(candidate.id)}>
                        <Save size={14} />
                        {translate('auto.k0495', { value0: translate('auto.k0795') })}</button>
                      <button className="mini-button" type="button" disabled={candidateDisabled || candidateState.loading} onClick={() => confirmCandidate(candidate.id)}>
                        <CheckCircle2 size={14} />
                        {translate('auto.k0807')}</button>
                      <button className="mini-button" type="button" disabled={candidateDisabled || candidateState.loading} onClick={() => ignoreCandidate(candidate.id)}>
                        <XCircle size={14} />
                        {translate('auto.k0808')}</button>
                    </div>
                  </article>
                );
              })
            ) : (
              <div className="empty-state compact">
                <FileText size={20} />
                <div>
                  <strong>{candidateState.loading ? translate('auto.k0809') : translate('auto.k0810')}</strong>
                  <span>{candidateState.error ?? translate('auto.k0811')}</span>
                </div>
              </div>
            )}
          </div>
          <StateLine state={candidateState} />
        </section>

        <section className="panel insight-panel document-webhook-panel">
          <div className="panel-title-row">
            <h2>{translate('auto.k0812')}</h2>
            <button className="mini-button" type="button" disabled={!props.signedIn || eventState.loading} onClick={refreshWebhookEvents}>
              <RefreshCw size={14} />
              {translate('auto.k0170')}</button>
          </div>
          <div className="webhook-filter-grid">
            <label className="field" htmlFor="webhook-source-id-filter">
              <span>{fieldLabel('sourceId')}</span>
              <input
                id="webhook-source-id-filter"
                value={eventFilters.sourceId}
                disabled={!props.signedIn || eventState.loading}
                onChange={(event) => setEventFilters((current) => ({ ...current, sourceId: event.target.value }))}
                placeholder="uuid"
              />
            </label>
            <label className="field" htmlFor="webhook-source-filter">
              <span>{fieldLabel('sourceCode')}</span>
              <input
                id="webhook-source-filter"
                value={eventFilters.sourceCode}
                disabled={!props.signedIn || eventState.loading}
                onChange={(event) => setEventFilters((current) => ({ ...current, sourceCode: event.target.value }))}
                placeholder="payment-docs"
              />
            </label>
            <label className="field" htmlFor="webhook-event-type-filter">
              <span>{fieldLabel('eventType')}</span>
              <input
                id="webhook-event-type-filter"
                value={eventFilters.eventType}
                disabled={!props.signedIn || eventState.loading}
                onChange={(event) => setEventFilters((current) => ({ ...current, eventType: event.target.value }))}
                placeholder="requirement.created"
              />
            </label>
            <label className="field" htmlFor="webhook-status-filter">
              <span>{fieldLabel('status')}</span>
              <select
                id="webhook-status-filter"
                value={eventFilters.status}
                disabled={!props.signedIn || eventState.loading}
                onChange={(event) => setEventFilters((current) => ({ ...current, status: event.target.value }))}
              >
                <option value="">{translate('auto.k0195')}</option>
                <option value="FAILED">{dictionaryLabel('FAILED')}</option>
                <option value="PROCESSING">{dictionaryLabel('PROCESSING')}</option>
                <option value="PROCESSED">{dictionaryLabel('PROCESSED')}</option>
                <option value="ACCEPTED">{dictionaryLabel('ACCEPTED')}</option>
                <option value="REJECTED">{dictionaryLabel('REJECTED')}</option>
                <option value="DEAD_LETTER">{dictionaryLabel('DEAD_LETTER')}</option>
                <option value="REPLAYED">{dictionaryLabel('REPLAYED')}</option>
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
                    <em>{event.sourceCode ?? '-'} · {fieldLabel('retryCount')} {event.retryCount}</em>
                  </span>
                  <DocumentStatusPill value={event.status} />
                </button>
              ))
            ) : (
              <div className="empty-state compact">
                <Webhook size={20} />
                <div>
                  <strong>{eventState.loading ? translate('auto.k0813') : translate('auto.k0814')}</strong>
                  <span>{eventState.error ?? translate('auto.k0815')}</span>
                </div>
              </div>
            )}
          </div>
          {selectedEvent && (
            <div className="webhook-event-detail">
              <div>
                <span>{fieldLabel('eventId')}</span>
                <em>{selectedEvent.eventId ?? selectedEvent.id}</em>
              </div>
              <div>
                <span>{fieldLabel('sourceId')}</span>
                <em>{selectedEvent.sourceId ?? '-'}</em>
              </div>
              <div>
                <span>{fieldLabel('importId')}</span>
                <em>{selectedEvent.importId ?? '-'}</em>
              </div>
              <div>
                <span>{fieldLabel('idempotencyKey')}</span>
                <em>{selectedEvent.idempotencyKey ?? '-'}</em>
              </div>
              <div>
                <span>{fieldLabel('signatureStatus')}</span>
                <DocumentStatusPill value={selectedEvent.signatureStatus ?? 'UNKNOWN'} />
              </div>
              <div>
                <span>{fieldLabel('receivedAt')}</span>
                <em>{selectedEvent.receivedAt ?? '-'}</em>
              </div>
              <div>
                <span>{fieldLabel('processedAt')}</span>
                <em>{selectedEvent.processedAt ?? '-'}</em>
              </div>
              <div>
                <span>{fieldLabel('replayBy')}</span>
                <em>{selectedEvent.replayBy ?? '-'}</em>
              </div>
              <div>
                <span>{fieldLabel('replayAt')}</span>
                <em>{selectedEvent.replayAt ?? '-'}</em>
              </div>
              <div>
                <span>{fieldLabel('replayTraceId')}</span>
                <em>{selectedEvent.replayTraceId ?? '-'}</em>
              </div>
              {selectedEvent.payloadDigest && (
                <div>
                  <span>{fieldLabel('payloadDigest')}</span>
                  <em>{selectedEvent.payloadDigest}</em>
                </div>
              )}
              {selectedEvent.errorMessage && (
                <div>
                  <span>{translate('auto.k0780')}</span>
                  <FailureHint message={selectedEvent.errorMessage} />
                </div>
              )}
            <button className="mini-button" type="button" disabled={!props.signedIn || !canReplayWebhook || replayState.loading} onClick={replaySelectedEvent}>
                <RotateCcw size={14} />
                {translate('auto.k0816')}</button>
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

function isAsyncImportStatus(status?: string) {
  return status ? ASYNC_IMPORT_STATUSES.has(status.toUpperCase()) : false;
}

function isAsyncWebhookStatus(status?: string) {
  return status ? ASYNC_WEBHOOK_STATUSES.has(status.toUpperCase()) : false;
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
      {props.record.diffSummary && <em>{translate('auto.k0817')}{props.record.diffSummary}</em>}
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
      <em>{isReservedSourceType(props.type) ? translate('auto.k0818') : translate('auto.k0819')}</em>
    </span>
  );
}

function DocumentStatusPill(props: { value: string }) {
  const normalized = props.value.toUpperCase();
  const positive = ['UP', 'ON', 'OK', 'SUCCESS', 'SUCCEEDED', 'COMPLETED', 'DONE', 'ENABLED', 'ACTIVE', 'CONFIRMED', 'PUBLISHED', 'VALID', translate('auto.k0819'), translate('auto.k0368')];
  const pending = [
    'PENDING',
    'RUNNING',
    'PROCESSING',
    'QUEUED',
    'MODEL_PARSE_QUEUED',
    'MODEL_PARSE_RUNNING',
    'PUBLISH_QUEUED',
    'PUBLISHING',
    'DRAFT',
    'RESERVED',
    'REPLAYING',
    'PLANNED',
    'DEGRADED',
    translate('auto.k0818')
  ];
  const negative = ['DOWN', 'OFF', 'FAILED', 'ERROR', 'DISABLED', 'CANCELED', 'IGNORED', 'INVALID', 'CONFLICT', translate('auto.k0094'), translate('auto.k0369')];
  const tone = positive.includes(normalized) || positive.includes(props.value)
    ? 'positive'
    : pending.includes(normalized) || pending.includes(props.value)
      ? 'pending'
      : negative.includes(normalized) || negative.includes(props.value)
        ? 'negative'
        : 'neutral';
  return <span className={`status-pill ${tone}`} title={props.value}>{dictionaryLabel(props.value)}</span>;
}

function StatusMetric(props: { label: string; value: string; pill?: boolean }) {
  return (
    <div className="status-item">
      <span>{fieldLabel(props.label)}</span>
      {props.pill ? <DocumentStatusPill value={props.value} /> : <strong>{props.value}</strong>}
    </div>
  );
}

function StateLine(props: { state: WorkState }) {
  if (props.state.loading) {
    return <span className="document-state-line">{translate('auto.k0820')}</span>;
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
    return translate('auto.k0818');
  }
  return translate('auto.k0819');
}

function errorMessage(error: unknown, fallback: string) {
  return documentInputErrorMessage(error, fallback);
}
