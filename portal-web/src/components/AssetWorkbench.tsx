import {
  Archive,
  CheckCircle2,
  ClipboardList,
  Eye,
  FilePlus2,
  FileText,
  GitBranch,
  Link2,
  Pencil,
  RefreshCw,
  Save,
  Search,
  Send,
  Upload,
  XCircle,
  type LucideIcon
} from 'lucide-react';
import { Drawer } from 'antd';
import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react';
import type { CurrentUser } from '../api/auth';
import {
  ASSET_API_METHODS,
  ASSET_API_STATUSES,
  ASSET_REQUIREMENT_PRIORITIES,
  ASSET_REQUIREMENT_SOURCES,
  ASSET_REQUIREMENT_STATUSES,
  createAssetApi,
  createAssetRequirement,
  fetchAssetApi,
  fetchAssetApiVersions,
  fetchAssetApis,
  fetchAssetHealth,
  fetchAssetRequirement,
  fetchAssetRequirements,
  fetchAssetRequirementVersions,
  fetchRequirementTraceLinks,
  rollbackAssetApiVersion,
  rollbackAssetRequirementVersion,
  updateAssetApi,
  updateAssetRequirement,
  type AssetApiFilters,
  type AssetApiPayload,
  type AssetApiView,
  type AssetHealth,
  type AssetRequirementFilters,
  type AssetRequirementPayload,
  type AssetRequirementView,
  type AssetVersionHistoryView,
  type TraceLinkView
} from '../api/assets';
import { hasPermission } from '../permissions';
import { AssetCaseWorkbench } from './AssetCaseWorkbench';
import { AssetImportExportPanel } from './AssetImportExportPanel';
import { AssetStructuredWorkbench, type AssetNavigationKey } from './AssetStructuredWorkbench';
import { AssetTraceWorkbench } from './AssetTraceWorkbench';
import { AssetVersionHistoryPanel } from './AssetVersionHistoryPanel';
import { dictionaryLabel, fieldLabel } from '../platform/dictionaries';
import { translate } from '../platform/i18n';

type WorkState = {
  loading: boolean;
  error?: string;
  success?: string;
  traceId?: string;
};

type RequirementDraft = {
  projectId: string;
  title: string;
  description: string;
  source: string;
  sourceRef: string;
  sourceUrl: string;
  acceptanceCriteria: string;
  status: string;
  priority: string;
  tags: string;
};

type RequirementFilters = {
  projectId: string;
  status: string;
  source: string;
  sourceRef: string;
  keyword: string;
};

type ApiDraft = {
  projectId: string;
  summary: string;
  description: string;
  httpMethod: string;
  path: string;
  version: string;
  requestSchema: string;
  responseSchema: string;
  status: string;
};

type ApiFilters = {
  projectId: string;
  status: string;
  source: string;
  method: string;
  keyword: string;
};

type AssetDrawer = 'requirement-create' | 'requirement-edit' | 'api-create' | 'api-edit' | null;

const initialRequirementDraft: RequirementDraft = {
  projectId: '',
  title: '',
  description: '',
  source: 'MANUAL',
  sourceRef: '',
  sourceUrl: '',
  acceptanceCriteria: '',
  status: 'DRAFT',
  priority: 'MEDIUM',
  tags: ''
};

const initialFilters: RequirementFilters = {
  projectId: '',
  status: '',
  source: '',
  sourceRef: '',
  keyword: ''
};

const initialApiDraft: ApiDraft = {
  projectId: '',
  summary: '',
  description: '',
  httpMethod: 'GET',
  path: '',
  version: '',
  requestSchema: '',
  responseSchema: '',
  status: 'ACTIVE'
};

const initialApiFilters: ApiFilters = {
  projectId: '',
  status: '',
  source: '',
  method: '',
  keyword: ''
};

const assetTabs = [
  { key: 'requirements', label: translate('auto.k0133'), icon: FileText, enabled: true },
  { key: 'apis', label: 'API', icon: Link2, enabled: true },
  { key: 'pages', label: translate('auto.k0134'), icon: ClipboardList, enabled: true },
  { key: 'flows', label: translate('auto.k0135'), icon: GitBranch, enabled: true },
  { key: 'cases', label: translate('auto.k0136'), icon: CheckCircle2, enabled: true },
  { key: 'trace', label: translate('auto.k0618'), icon: GitBranch, enabled: true }
] as const satisfies readonly { key: AssetNavigationKey; label: string; icon: LucideIcon; enabled: boolean }[];

type AssetTabKey = (typeof assetTabs)[number]['key'];

const statusTransitionMap: Record<string, string[]> = {
  DRAFT: ['REVIEWING'],
  REVIEWING: ['DRAFT', 'APPROVED'],
  APPROVED: ['DEPRECATED'],
  DEPRECATED: ['DEPRECATED']
};

const statusActionLabel: Record<string, string> = {
  DRAFT: translate('auto.k0619'),
  REVIEWING: translate('auto.k0212'),
  APPROVED: translate('auto.k0620'),
  DEPRECATED: translate('auto.k0621')
};

const apiStatusTransitionMap: Record<string, string[]> = {
  ACTIVE: ['ACTIVE', 'DEPRECATED'],
  DEPRECATED: ['DEPRECATED', 'REMOVED'],
  REMOVED: ['REMOVED']
};

export function AssetWorkbench(props: { signedIn: boolean; currentUser: CurrentUser | null }) {
  const canReadAssets = hasPermission(props.currentUser, 'asset:read');
  const canManageAssets = hasPermission(props.currentUser, 'asset:manage');
  const canReviewAssets = hasPermission(props.currentUser, 'asset:review');
  const initialHash = assetLocationFromHash();
  const [activeTab, setActiveTab] = useState<AssetTabKey>(initialHash.tab);
  const [health, setHealth] = useState<AssetHealth | null>(null);
  const [requirements, setRequirements] = useState<AssetRequirementView[]>([]);
  const [filters, setFilters] = useState<RequirementFilters>(initialFilters);
  const [selectedRequirementId, setSelectedRequirementId] = useState(initialHash.tab === 'requirements' ? initialHash.id : '');
  const [selectedRequirement, setSelectedRequirement] = useState<AssetRequirementView | null>(null);
  const [traceLinks, setTraceLinks] = useState<TraceLinkView[]>([]);
  const [createDraft, setCreateDraft] = useState<RequirementDraft>(initialRequirementDraft);
  const [editDraft, setEditDraft] = useState<RequirementDraft>(initialRequirementDraft);
  const [loadState, setLoadState] = useState<WorkState>({ loading: false });
  const [detailState, setDetailState] = useState<WorkState>({ loading: false });
  const [createState, setCreateState] = useState<WorkState>({ loading: false });
  const [mutationState, setMutationState] = useState<WorkState>({ loading: false });
  const [requirementVersions, setRequirementVersions] = useState<AssetVersionHistoryView[]>([]);
  const [requirementVersionState, setRequirementVersionState] = useState<WorkState>({ loading: false });
  const [apis, setApis] = useState<AssetApiView[]>([]);
  const [apiFilters, setApiFilters] = useState<ApiFilters>(initialApiFilters);
  const [selectedApiId, setSelectedApiId] = useState(initialHash.tab === 'apis' ? initialHash.id : '');
  const [selectedApi, setSelectedApi] = useState<AssetApiView | null>(null);
  const [apiCreateDraft, setApiCreateDraft] = useState<ApiDraft>(initialApiDraft);
  const [apiEditDraft, setApiEditDraft] = useState<ApiDraft>(initialApiDraft);
  const [apiLoadState, setApiLoadState] = useState<WorkState>({ loading: false });
  const [apiDetailState, setApiDetailState] = useState<WorkState>({ loading: false });
  const [apiCreateState, setApiCreateState] = useState<WorkState>({ loading: false });
  const [apiMutationState, setApiMutationState] = useState<WorkState>({ loading: false });
  const [apiVersions, setApiVersions] = useState<AssetVersionHistoryView[]>([]);
  const [apiVersionState, setApiVersionState] = useState<WorkState>({ loading: false });
  const [openDrawer, setOpenDrawer] = useState<AssetDrawer>(null);

  const refreshRequirements = useCallback(async () => {
    if (!props.signedIn || !canReadAssets) {
      setHealth(null);
      setRequirements([]);
      setSelectedRequirement(null);
      setTraceLinks([]);
      setLoadState({ loading: false });
      return;
    }

    setLoadState({ loading: true });
    const [healthResult, requirementResult] = await Promise.allSettled([
      fetchAssetHealth(),
      fetchAssetRequirements(buildRequirementFilters(filters))
    ]);

    const errors: string[] = [];
    const traceIds: string[] = [];

    if (healthResult.status === 'fulfilled') {
      setHealth(healthResult.value.data);
      traceIds.push(healthResult.value.trace_id);
    } else {
      errors.push(errorMessage(healthResult.reason, translate('auto.k0392')));
    }

    if (requirementResult.status === 'fulfilled') {
      setRequirements(requirementResult.value.data.items);
      traceIds.push(requirementResult.value.trace_id);
    } else {
      setRequirements([]);
      errors.push(errorMessage(requirementResult.reason, translate('auto.k0527')));
    }

    setLoadState({
      loading: false,
      error: errors.length ? errors.join('；') : undefined,
      traceId: traceIds.find(Boolean)
    });
  }, [canReadAssets, filters, props.signedIn]);

  useEffect(() => {
    if (activeTab === 'requirements') {
      void refreshRequirements();
    }
  }, [activeTab, refreshRequirements]);

  useEffect(() => {
    function syncAssetFromHash() {
      const nextLocation = assetLocationFromHash();
      setActiveTab(nextLocation.tab);
      if (nextLocation.tab === 'requirements') {
        setSelectedRequirementId(nextLocation.id);
      }
      if (nextLocation.tab === 'apis') {
        setSelectedApiId(nextLocation.id);
      }
    }

    window.addEventListener('hashchange', syncAssetFromHash);
    return () => window.removeEventListener('hashchange', syncAssetFromHash);
  }, []);

  useEffect(() => {
    if (activeTab === 'requirements' && !selectedRequirementId && requirements[0]?.id) {
      setSelectedRequirementId(requirements[0].id);
    }
  }, [activeTab, requirements, selectedRequirementId]);

  const reloadRequirementDetail = useCallback(async () => {
    if (activeTab !== 'requirements' || !props.signedIn || !canReadAssets || !selectedRequirementId) {
      setSelectedRequirement(null);
      setTraceLinks([]);
      setDetailState({ loading: false });
      setRequirementVersions([]);
      setRequirementVersionState({ loading: false });
      return;
    }

    setDetailState({ loading: true });
    const [detailResult, linkResult] = await Promise.allSettled([
      fetchAssetRequirement(selectedRequirementId),
      fetchRequirementTraceLinks(selectedRequirementId)
    ]);

    const errors: string[] = [];
    const traceIds: string[] = [];

    if (detailResult.status === 'fulfilled') {
      setSelectedRequirement(detailResult.value.data);
      setEditDraft(requirementDraftFromView(detailResult.value.data));
      upsertRequirement(setRequirements, detailResult.value.data);
      traceIds.push(detailResult.value.trace_id);
    } else {
      setSelectedRequirement(null);
      errors.push(errorMessage(detailResult.reason, translate('auto.k0622')));
    }

    if (linkResult.status === 'fulfilled') {
      setTraceLinks(linkResult.value.data.items);
      traceIds.push(linkResult.value.trace_id);
    } else {
      setTraceLinks([]);
      errors.push(errorMessage(linkResult.reason, translate('auto.k0531')));
    }

    setDetailState({
      loading: false,
      error: errors.length ? errors.join('；') : undefined,
      traceId: traceIds.find(Boolean)
    });
  }, [activeTab, canReadAssets, props.signedIn, selectedRequirementId]);

  useEffect(() => {
    void reloadRequirementDetail();
  }, [reloadRequirementDetail]);

  const reloadRequirementVersions = useCallback(
    async (targetId = selectedRequirementId) => {
      if (activeTab !== 'requirements' || !props.signedIn || !canReadAssets || !targetId) {
        setRequirementVersions([]);
        setRequirementVersionState({ loading: false });
        return;
      }

      setRequirementVersionState({ loading: true });
      try {
        const response = await fetchAssetRequirementVersions(targetId);
        setRequirementVersions(response.data);
        setRequirementVersionState({ loading: false, traceId: response.trace_id });
      } catch (error: unknown) {
        setRequirementVersions([]);
        setRequirementVersionState({ loading: false, error: errorMessage(error, translate('auto.k0396')) });
      }
    },
    [activeTab, canReadAssets, props.signedIn, selectedRequirementId]
  );

  useEffect(() => {
    void reloadRequirementVersions();
  }, [reloadRequirementVersions]);

  async function rollbackRequirement(version: number) {
    if (!selectedRequirement) {
      return;
    }
    if (!props.signedIn) {
      setRequirementVersionState({ loading: false, error: translate('auto.k0397') });
      return;
    }
    if (!canManageAssets) {
      setRequirementVersionState({ loading: false, error: translate('auto.k0398') });
      return;
    }
    setRequirementVersionState({ loading: true });
    try {
      const response = await rollbackAssetRequirementVersion(selectedRequirement.id, version, translate('auto.k0399', { value0: version }));
      setSelectedRequirement(response.data);
      setEditDraft(requirementDraftFromView(response.data));
      upsertRequirement(setRequirements, response.data);
      setRequirementVersionState({ loading: false, traceId: response.trace_id });
      void reloadRequirementVersions(response.data.id);
    } catch (error: unknown) {
      setRequirementVersionState({ loading: false, error: errorMessage(error, translate('auto.k0623')) });
    }
  }

  const refreshApis = useCallback(async () => {
    if (!props.signedIn || !canReadAssets) {
      setHealth(null);
      setApis([]);
      setSelectedApi(null);
      setApiLoadState({ loading: false });
      return;
    }

    setApiLoadState({ loading: true });
    const [healthResult, apiResult] = await Promise.allSettled([
      fetchAssetHealth(),
      fetchAssetApis(buildApiFilters(apiFilters))
    ]);

    const errors: string[] = [];
    const traceIds: string[] = [];

    if (healthResult.status === 'fulfilled') {
      setHealth(healthResult.value.data);
      traceIds.push(healthResult.value.trace_id);
    } else {
      errors.push(errorMessage(healthResult.reason, translate('auto.k0392')));
    }

    if (apiResult.status === 'fulfilled') {
      setApis(apiResult.value.data.items);
      traceIds.push(apiResult.value.trace_id);
    } else {
      setApis([]);
      errors.push(errorMessage(apiResult.reason, translate('auto.k0528')));
    }

    setApiLoadState({
      loading: false,
      error: errors.length ? errors.join('；') : undefined,
      traceId: traceIds.find(Boolean)
    });
  }, [apiFilters, canReadAssets, props.signedIn]);

  useEffect(() => {
    if (activeTab === 'apis') {
      void refreshApis();
    }
  }, [activeTab, refreshApis]);

  useEffect(() => {
    if (activeTab === 'apis' && !selectedApiId && apis[0]?.id) {
      setSelectedApiId(apis[0].id);
    }
  }, [activeTab, apis, selectedApiId]);

  const reloadApiDetail = useCallback(async () => {
    if (activeTab !== 'apis' || !props.signedIn || !canReadAssets || !selectedApiId) {
      setSelectedApi(null);
      setApiDetailState({ loading: false });
      return;
    }

    setApiDetailState({ loading: true });
    try {
      const response = await fetchAssetApi(selectedApiId);
      setSelectedApi(response.data);
      setApiEditDraft(apiDraftFromView(response.data));
      upsertApi(setApis, response.data);
      setApiDetailState({ loading: false, traceId: response.trace_id });
    } catch (error: unknown) {
      setSelectedApi(null);
      setApiDetailState({ loading: false, error: errorMessage(error, translate('auto.k0624')) });
    }
  }, [activeTab, canReadAssets, props.signedIn, selectedApiId]);

  useEffect(() => {
    void reloadApiDetail();
  }, [reloadApiDetail]);

  const reloadApiVersions = useCallback(
    async (targetId = selectedApiId) => {
      if (activeTab !== 'apis' || !props.signedIn || !canReadAssets || !targetId) {
        setApiVersions([]);
        setApiVersionState({ loading: false });
        return;
      }

      setApiVersionState({ loading: true });
      try {
        const response = await fetchAssetApiVersions(targetId);
        setApiVersions(response.data);
        setApiVersionState({ loading: false, traceId: response.trace_id });
      } catch (error: unknown) {
        setApiVersions([]);
        setApiVersionState({ loading: false, error: errorMessage(error, translate('auto.k0396')) });
      }
    },
    [activeTab, canReadAssets, props.signedIn, selectedApiId]
  );

  useEffect(() => {
    void reloadApiVersions();
  }, [reloadApiVersions]);

  const visibleRequirements = useMemo(() => filterRequirements(requirements, filters), [filters, requirements]);
  const statusCounts = useMemo(() => countRequirementsByStatus(requirements), [requirements]);
  const visibleApis = useMemo(() => filterApis(apis, apiFilters), [apiFilters, apis]);
  const apiStatusCounts = useMemo(() => countApisByStatus(apis), [apis]);
  const activeLoadState = activeTab === 'apis' ? apiLoadState : loadState;
  const disabled = !props.signedIn || !canReadAssets || activeLoadState.loading;
  const createDisabled = disabled || createState.loading || !canManageAssets;
  const editDisabled = disabled || mutationState.loading || !canManageAssets || !selectedRequirement;
  const reviewDisabled = disabled || mutationState.loading || !canReviewAssets || !selectedRequirement;
  const apiDisabled = !props.signedIn || !canReadAssets || apiLoadState.loading;
  const apiCreateDisabled = apiDisabled || apiCreateState.loading || !canManageAssets;
  const apiEditDisabled = apiDisabled || apiMutationState.loading || !canManageAssets || !selectedApi;

  function selectTab(tabKey: AssetTabKey) {
    const tab = assetTabs.find((item) => item.key === tabKey);
    if (!tab?.enabled) {
      return;
    }
    setActiveTab(tabKey);
    const selectedId = tabKey === 'requirements' ? selectedRequirementId : tabKey === 'apis' ? selectedApiId : '';
    const targetHash = selectedId
      ? `#asset-library/${tabKey}/${encodeURIComponent(selectedId)}`
      : `#asset-library/${tabKey}`;
    if (window.location.hash !== targetHash) {
      window.location.hash = targetHash;
    }
  }

  function selectRequirement(requirementId: string) {
    if (!requirementId) {
      return;
    }
    setActiveTab('requirements');
    setSelectedRequirementId(requirementId);
    const targetHash = `#asset-library/requirements/${encodeURIComponent(requirementId)}`;
    if (window.location.hash !== targetHash) {
      window.location.hash = targetHash;
    }
  }

  function selectApi(apiId: string) {
    if (!apiId) {
      return;
    }
    setActiveTab('apis');
    setSelectedApiId(apiId);
    const targetHash = `#asset-library/apis/${encodeURIComponent(apiId)}`;
    if (window.location.hash !== targetHash) {
      window.location.hash = targetHash;
    }
  }

  function openCreateRequirementDrawer() {
    setCreateDraft(initialRequirementDraft);
    setCreateState({ loading: false });
    setOpenDrawer('requirement-create');
  }

  function openEditRequirementDrawer() {
    if (!selectedRequirement) {
      return;
    }
    setEditDraft(requirementDraftFromView(selectedRequirement));
    setMutationState({ loading: false });
    setOpenDrawer('requirement-edit');
  }

  function openCreateApiDrawer() {
    setApiCreateDraft(initialApiDraft);
    setApiCreateState({ loading: false });
    setOpenDrawer('api-create');
  }

  function openEditApiDrawer() {
    if (!selectedApi) {
      return;
    }
    setApiEditDraft(apiDraftFromView(selectedApi));
    setApiMutationState({ loading: false });
    setOpenDrawer('api-edit');
  }

  if (activeTab === 'pages' || activeTab === 'flows') {
    return (
      <AssetStructuredWorkbench
        activeTab={activeTab}
        currentUser={props.currentUser}
        onSelectTab={selectTab}
        signedIn={props.signedIn}
        tabs={assetTabs}
      />
    );
  }

  if (activeTab === 'cases') {
    return (
      <AssetCaseWorkbench
        currentUser={props.currentUser}
        onSelectTab={selectTab}
        signedIn={props.signedIn}
        tabs={assetTabs}
      />
    );
  }

  if (activeTab === 'trace') {
    return (
      <AssetTraceWorkbench
        currentUser={props.currentUser}
        onSelectTab={selectTab}
        signedIn={props.signedIn}
        tabs={assetTabs}
      />
    );
  }

  async function submitCreate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!props.signedIn) {
      setCreateState({ loading: false, error: translate('auto.k0625') });
      return;
    }
    if (!canManageAssets) {
      setCreateState({ loading: false, error: translate('auto.k0398') });
      return;
    }
    if (!createDraft.projectId.trim() || !createDraft.title.trim()) {
      setCreateState({ loading: false, error: translate('auto.k0402') });
      return;
    }

    setCreateState({ loading: true });
    try {
      const response = await createAssetRequirement(draftToCreatePayload(createDraft));
      setCreateDraft(initialRequirementDraft);
      setSelectedRequirement(response.data);
      setSelectedRequirementId(response.data.id);
      setEditDraft(requirementDraftFromView(response.data));
      upsertRequirement(setRequirements, response.data);
      setOpenDrawer(null);
      setCreateState({ loading: false, success: translate('auto.k0626'), traceId: response.trace_id });
      void reloadRequirementVersions(response.data.id);
      if (response.data.id) {
        selectRequirement(response.data.id);
      }
    } catch (error: unknown) {
      setCreateState({ loading: false, error: errorMessage(error, translate('auto.k0627')) });
    }
  }

  async function submitEdit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedRequirement) {
      return;
    }
    if (!props.signedIn) {
      setMutationState({ loading: false, error: translate('auto.k0628') });
      return;
    }
    if (!canManageAssets) {
      setMutationState({ loading: false, error: translate('auto.k0398') });
      return;
    }
    if (!editDraft.title.trim()) {
      setMutationState({ loading: false, error: translate('auto.k0406') });
      return;
    }

    setMutationState({ loading: true });
    try {
      const response = await updateAssetRequirement(selectedRequirement.id, draftToUpdatePayload(selectedRequirement, editDraft));
      setSelectedRequirement(response.data);
      setEditDraft(requirementDraftFromView(response.data));
      upsertRequirement(setRequirements, response.data);
      setOpenDrawer(null);
      setMutationState({ loading: false, success: translate('auto.k0629'), traceId: response.trace_id });
      void reloadRequirementVersions(response.data.id);
    } catch (error: unknown) {
      setMutationState({ loading: false, error: errorMessage(error, translate('auto.k0630')) });
    }
  }

  async function changeStatus(nextStatus: string) {
    if (!selectedRequirement) {
      return;
    }
    if (!props.signedIn) {
      setMutationState({ loading: false, error: translate('auto.k0631') });
      return;
    }
    if (!canReviewAssets) {
      setMutationState({ loading: false, error: translate('auto.k0407') });
      return;
    }

    setMutationState({ loading: true });
    try {
      const response = await updateAssetRequirement(
        selectedRequirement.id,
        draftToUpdatePayload(selectedRequirement, requirementDraftFromView(selectedRequirement), nextStatus)
      );
      setSelectedRequirement(response.data);
      setEditDraft(requirementDraftFromView(response.data));
      upsertRequirement(setRequirements, response.data);
      setMutationState({ loading: false, success: translate('auto.k0632', { value0: response.data.status }), traceId: response.trace_id });
      void reloadRequirementVersions(response.data.id);
    } catch (error: unknown) {
      setMutationState({ loading: false, error: errorMessage(error, translate('auto.k0633')) });
    }
  }

  async function submitCreateApi(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!props.signedIn) {
      setApiCreateState({ loading: false, error: translate('auto.k0634') });
      return;
    }
    if (!canManageAssets) {
      setApiCreateState({ loading: false, error: translate('auto.k0398') });
      return;
    }
    if (!apiCreateDraft.projectId.trim() || !apiCreateDraft.summary.trim() || !apiCreateDraft.path.trim()) {
      setApiCreateState({ loading: false, error: translate('auto.k0635') });
      return;
    }

    setApiCreateState({ loading: true });
    try {
      const response = await createAssetApi(apiDraftToCreatePayload(apiCreateDraft));
      setApiCreateDraft(initialApiDraft);
      setSelectedApi(response.data);
      setSelectedApiId(response.data.id);
      setApiEditDraft(apiDraftFromView(response.data));
      upsertApi(setApis, response.data);
      setOpenDrawer(null);
      setApiCreateState({ loading: false, success: translate('auto.k0636'), traceId: response.trace_id });
      void reloadApiVersions(response.data.id);
      if (response.data.id) {
        selectApi(response.data.id);
      }
    } catch (error: unknown) {
      setApiCreateState({ loading: false, error: errorMessage(error, translate('auto.k0637')) });
    }
  }

  async function submitEditApi(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedApi) {
      return;
    }
    if (!props.signedIn) {
      setApiMutationState({ loading: false, error: translate('auto.k0638') });
      return;
    }
    if (!canManageAssets) {
      setApiMutationState({ loading: false, error: translate('auto.k0398') });
      return;
    }
    if (!apiEditDraft.summary.trim() || !apiEditDraft.path.trim()) {
      setApiMutationState({ loading: false, error: translate('auto.k0639') });
      return;
    }

    setApiMutationState({ loading: true });
    try {
      const response = await updateAssetApi(selectedApi.id, apiDraftToUpdatePayload(selectedApi, apiEditDraft));
      setSelectedApi(response.data);
      setApiEditDraft(apiDraftFromView(response.data));
      upsertApi(setApis, response.data);
      setOpenDrawer(null);
      setApiMutationState({ loading: false, success: translate('auto.k0640'), traceId: response.trace_id });
      void reloadApiVersions(response.data.id);
    } catch (error: unknown) {
      setApiMutationState({ loading: false, error: errorMessage(error, translate('auto.k0641')) });
    }
  }

  async function rollbackApi(version: number) {
    if (!selectedApi) {
      return;
    }
    if (!props.signedIn) {
      setApiVersionState({ loading: false, error: translate('auto.k0397') });
      return;
    }
    if (!canManageAssets) {
      setApiVersionState({ loading: false, error: translate('auto.k0398') });
      return;
    }
    setApiVersionState({ loading: true });
    try {
      const response = await rollbackAssetApiVersion(selectedApi.id, version, translate('auto.k0399', { value0: version }));
      setSelectedApi(response.data);
      setApiEditDraft(apiDraftFromView(response.data));
      upsertApi(setApis, response.data);
      setApiVersionState({ loading: false, traceId: response.trace_id });
      void reloadApiVersions(response.data.id);
    } catch (error: unknown) {
      setApiVersionState({ loading: false, error: errorMessage(error, translate('auto.k0642')) });
    }
  }

  return (
    <section className="asset-workbench-layout">
      <div className="asset-main-stack">
        <section className="panel module-panel asset-panel">
          <div className="panel-toolbar">
            <div className="section-heading compact">
              <div className="section-icon">
                <Archive size={20} />
              </div>
              <div>
                <span className="eyebrow">{translate('auto.k0039')}</span>
                <h2>{translate('auto.k0005')}</h2>
              </div>
            </div>
            <button
              className="secondary-button"
              type="button"
              disabled={!props.signedIn || !canReadAssets || activeLoadState.loading}
              onClick={activeTab === 'apis' ? refreshApis : refreshRequirements}
            >
              <RefreshCw size={16} />
              {translate('auto.k0170')}</button>
          </div>

          <div className="asset-tab-strip" aria-label={translate('auto.k0413')}>
            {assetTabs.map((tab) => {
              const Icon = tab.icon;
              return (
                <button
                  className={`asset-tab ${activeTab === tab.key ? 'active' : ''}`}
                  type="button"
                  key={tab.key}
                  disabled={!tab.enabled}
                  onClick={() => selectTab(tab.key)}
                  title={tab.label}
                >
                  <Icon size={15} />
                  <span>{tab.label}</span>
                </button>
              );
            })}
          </div>

          {activeTab === 'requirements' ? (
            <>
          <form className="asset-filter-bar" onSubmit={(event) => event.preventDefault()}>
            <label className="field" htmlFor="asset-filter-project">
              <span>{fieldLabel('projectId')}</span>
              <input
                id="asset-filter-project"
                value={filters.projectId}
                disabled={disabled}
                onChange={(event) => setFilters((current) => ({ ...current, projectId: event.target.value }))}
                placeholder="proj-payments"
              />
            </label>
            <label className="field" htmlFor="asset-filter-status">
              <span>{fieldLabel('status')}</span>
              <select
                id="asset-filter-status"
                value={filters.status}
                disabled={disabled}
                onChange={(event) => setFilters((current) => ({ ...current, status: event.target.value }))}
              >
                <option value="">{translate('auto.k0367')}</option>
                {ASSET_REQUIREMENT_STATUSES.map((status) => (
                  <option key={status} value={status}>{dictionaryLabel(status)}</option>
                ))}
              </select>
            </label>
            <label className="field" htmlFor="asset-filter-source">
              <span>{fieldLabel('source')}</span>
              <select
                id="asset-filter-source"
                value={filters.source}
                disabled={disabled}
                onChange={(event) => setFilters((current) => ({ ...current, source: event.target.value }))}
              >
                <option value="">{translate('auto.k0414')}</option>
                {ASSET_REQUIREMENT_SOURCES.map((source) => (
                  <option key={source} value={source}>{dictionaryLabel(source)}</option>
                ))}
              </select>
            </label>
            <label className="field" htmlFor="asset-filter-source-ref">
              <span>{fieldLabel('sourceRef')}</span>
              <input
                id="asset-filter-source-ref"
                value={filters.sourceRef}
                disabled={disabled}
                onChange={(event) => setFilters((current) => ({ ...current, sourceRef: event.target.value }))}
                placeholder="PRD-2026-001"
              />
            </label>
            <label className="field" htmlFor="asset-filter-keyword">
              <span>{fieldLabel('keyword')}</span>
              <input
                id="asset-filter-keyword"
                value={filters.keyword}
                disabled={disabled}
                onChange={(event) => setFilters((current) => ({ ...current, keyword: event.target.value }))}
                placeholder={translate('auto.k0415')}
              />
            </label>
            <div className="asset-filter-actions">
              <button className="mini-button" type="button" disabled={disabled} onClick={refreshRequirements}>
                <Search size={14} />
                {translate('auto.k0372')}</button>
              <button className="mini-button" type="button" disabled={disabled} onClick={() => setFilters(initialFilters)}>
                <XCircle size={14} />
                {translate('auto.k0416')}</button>
            </div>
          </form>

          <div className="table-wrap asset-table-wrap">
            <table>
              <thead>
                <tr>
                  <th>{translate('auto.k0133')}</th>
                  <th>{translate('auto.k0176')}</th>
                  <th>{translate('auto.k0182')}</th>
                  <th>{translate('auto.k0419')}</th>
                  <th>{translate('auto.k0179')}</th>
                  <th>{translate('auto.k0421')}</th>
                  <th>{translate('auto.k0249')}</th>
                </tr>
              </thead>
              <tbody>
                {visibleRequirements.length > 0 ? (
                  visibleRequirements.map((requirement) => (
                    <tr
                      className={selectedRequirementId === requirement.id ? 'selected-row' : ''}
                      key={requirement.id || requirement.title}
                    >
                      <td>
                        <strong className="table-primary">{requirement.title}</strong>
                        <span className="table-secondary">{requirement.id || '-'}</span>
                      </td>
                      <td>{requirement.projectId ?? '-'}</td>
                      <td>
                        <AssetStatusPill value={requirement.status} />
                      </td>
                      <td>{requirement.priority}</td>
                      <td>
                        <div className="asset-source-cell">
                          <span>{requirement.source}</span>
                          <em>{requirement.sourceRef ?? '-'}</em>
                        </div>
                      </td>
                      <td>{formatDate(requirement.updatedAt ?? requirement.createdAt)}</td>
                      <td>
                        <button className="mini-button" type="button" onClick={() => selectRequirement(requirement.id)} disabled={!requirement.id}>
                          <Eye size={14} />
                          {translate('auto.k0333')}</button>
                      </td>
                    </tr>
                  ))
                ) : (
                  <tr>
                    <td className="table-empty" colSpan={7}>
                      {props.signedIn ? (loadState.loading ? translate('auto.k0168') : translate('auto.k0643')) : translate('auto.k0454')}
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
          <StateLine state={loadState} />
            </>
          ) : (
            <>
              <form className="asset-filter-bar api-filter-bar" onSubmit={(event) => event.preventDefault()}>
                <label className="field" htmlFor="asset-api-filter-project">
                  <span>{fieldLabel('projectId')}</span>
                  <input
                    id="asset-api-filter-project"
                    value={apiFilters.projectId}
                    disabled={apiDisabled}
                    onChange={(event) => setApiFilters((current) => ({ ...current, projectId: event.target.value }))}
                    placeholder="proj-payments"
                  />
                </label>
                <label className="field" htmlFor="asset-api-filter-status">
                  <span>{fieldLabel('status')}</span>
                  <select
                    id="asset-api-filter-status"
                    value={apiFilters.status}
                    disabled={apiDisabled}
                    onChange={(event) => setApiFilters((current) => ({ ...current, status: event.target.value }))}
                  >
                    <option value="">{translate('auto.k0367')}</option>
                    {ASSET_API_STATUSES.map((status) => (
                      <option key={status} value={status}>{dictionaryLabel(status)}</option>
                    ))}
                  </select>
                </label>
                <label className="field" htmlFor="asset-api-filter-method">
                  <span>{fieldLabel('method')}</span>
                  <select
                    id="asset-api-filter-method"
                    value={apiFilters.method}
                    disabled={apiDisabled}
                    onChange={(event) => setApiFilters((current) => ({ ...current, method: event.target.value }))}
                  >
                    <option value="">{translate('auto.k0644')}</option>
                    {ASSET_API_METHODS.map((method) => (
                      <option key={method} value={method}>
                        {method}
                      </option>
                    ))}
                  </select>
                </label>
                <label className="field" htmlFor="asset-api-filter-source">
                  <span>{fieldLabel('source')}</span>
                  <input
                    id="asset-api-filter-source"
                    value={apiFilters.source}
                    disabled={apiDisabled}
                    onChange={(event) => setApiFilters((current) => ({ ...current, source: event.target.value }))}
                    placeholder="MANUAL / OPENAPI"
                  />
                </label>
                <label className="field" htmlFor="asset-api-filter-keyword">
                  <span>{fieldLabel('keyword')}</span>
                  <input
                    id="asset-api-filter-keyword"
                    value={apiFilters.keyword}
                    disabled={apiDisabled}
                    onChange={(event) => setApiFilters((current) => ({ ...current, keyword: event.target.value }))}
                    placeholder={translate('auto.k0645')}
                  />
                </label>
                <div className="asset-filter-actions">
                  <button className="mini-button" type="button" disabled={apiDisabled} onClick={refreshApis}>
                    <Search size={14} />
                    {translate('auto.k0372')}</button>
                  <button className="mini-button" type="button" disabled={apiDisabled} onClick={() => setApiFilters(initialApiFilters)}>
                    <XCircle size={14} />
                    {translate('auto.k0416')}</button>
                </div>
              </form>

              <div className="table-wrap asset-table-wrap">
                <table>
                  <thead>
                    <tr>
                      <th>API</th>
                      <th>{translate('auto.k0176')}</th>
                      <th>{translate('auto.k0646')}</th>
                      <th>{translate('auto.k0182')}</th>
                      <th>{translate('auto.k0179')}</th>
                      <th>{translate('auto.k0421')}</th>
                      <th>{translate('auto.k0249')}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {visibleApis.length > 0 ? (
                      visibleApis.map((api) => (
                        <tr className={selectedApiId === api.id ? 'selected-row' : ''} key={api.id || `${api.httpMethod}-${api.path}`}>
                          <td>
                            <strong className="table-primary">{api.summary}</strong>
                            <span className="table-secondary">{api.code ?? api.id ?? '-'}</span>
                          </td>
                          <td>{api.projectId ?? '-'}</td>
                          <td>
                            <span className="asset-method-path">
                              <strong>{api.httpMethod}</strong>
                              <em>{api.path}</em>
                            </span>
                          </td>
                          <td>
                            <AssetStatusPill value={api.status} />
                          </td>
                          <td>
                            <div className="asset-source-cell">
                              <span>{api.source ?? '-'}</span>
                              <em>{api.sourceRef ?? '-'}</em>
                            </div>
                          </td>
                          <td>{formatDate(api.updatedAt ?? api.createdAt)}</td>
                          <td>
                            <button className="mini-button" type="button" onClick={() => selectApi(api.id)} disabled={!api.id}>
                              <Eye size={14} />
                              {translate('auto.k0333')}</button>
                          </td>
                        </tr>
                      ))
                    ) : (
                      <tr>
                        <td className="table-empty" colSpan={7}>
                          {props.signedIn ? (apiLoadState.loading ? translate('auto.k0168') : translate('auto.k0647')) : translate('auto.k0454')}
                        </td>
                      </tr>
                    )}
                  </tbody>
                </table>
              </div>
              <StateLine state={apiLoadState} />
            </>
          )}
        </section>

        <section className="panel module-panel asset-panel">
          {activeTab === 'requirements' ? (
            <>
          <div className="panel-toolbar">
            <div className="section-heading compact">
              <div className="section-icon">
                <FilePlus2 size={20} />
              </div>
              <div>
                <span className="eyebrow">{translate('auto.k0174')}</span>
                <h2>{translate('auto.k0648')}</h2>
              </div>
            </div>
            <button className="primary-button" type="button" disabled={createDisabled} onClick={openCreateRequirementDrawer}>
              <FilePlus2 size={16} />
              {translate('auto.k0651')}</button>
          </div>

          <StateLine state={createState} />
          <Drawer
            className="asset-form-drawer"
            destroyOnHidden
            maskClosable={!createState.loading}
            open={openDrawer === 'requirement-create'}
            placement="right"
            title={translate('auto.k0648')}
            width={760}
            onClose={() => {
              if (!createState.loading) {
                setOpenDrawer(null);
              }
            }}
          >
          <form className="asset-form document-drawer-form" onSubmit={submitCreate}>
            <div className="asset-form-grid">
              <label className="field" htmlFor="asset-create-project">
                <span>{fieldLabel('projectId')}<b>*</b></span>
                <input
                  id="asset-create-project"
                  value={createDraft.projectId}
                  disabled={createDisabled}
                  onChange={(event) => setCreateDraft((current) => ({ ...current, projectId: event.target.value }))}
                  placeholder="proj-payments"
                />
              </label>
              <label className="field" htmlFor="asset-create-title">
                <span>{translate('auto.k0440')}<b>*</b></span>
                <input
                  id="asset-create-title"
                  value={createDraft.title}
                  disabled={createDisabled}
                  onChange={(event) => setCreateDraft((current) => ({ ...current, title: event.target.value }))}
                  placeholder={translate('auto.k0649')}
                />
              </label>
              <label className="field" htmlFor="asset-create-priority">
                <span>{fieldLabel('priority')}</span>
                <select
                  id="asset-create-priority"
                  value={createDraft.priority}
                  disabled={createDisabled}
                  onChange={(event) => setCreateDraft((current) => ({ ...current, priority: event.target.value }))}
                >
                  {ASSET_REQUIREMENT_PRIORITIES.map((priority) => (
                    <option key={priority} value={priority}>{dictionaryLabel(priority)}</option>
                  ))}
                </select>
              </label>
              <label className="field" htmlFor="asset-create-source">
                <span>{fieldLabel('source')}</span>
                <select
                  id="asset-create-source"
                  value={createDraft.source}
                  disabled={createDisabled}
                  onChange={(event) => setCreateDraft((current) => ({ ...current, source: event.target.value }))}
                >
                  {ASSET_REQUIREMENT_SOURCES.map((source) => (
                    <option key={source} value={source}>{dictionaryLabel(source)}</option>
                  ))}
                </select>
              </label>
              <label className="field" htmlFor="asset-create-source-ref">
                <span>{fieldLabel('sourceRef')}</span>
                <input
                  id="asset-create-source-ref"
                  value={createDraft.sourceRef}
                  disabled={createDisabled}
                  onChange={(event) => setCreateDraft((current) => ({ ...current, sourceRef: event.target.value }))}
                  placeholder="MANUAL-1"
                />
              </label>
              <label className="field" htmlFor="asset-create-source-url">
                <span>{fieldLabel('sourceUrl')}</span>
                <input
                  id="asset-create-source-url"
                  value={createDraft.sourceUrl}
                  disabled={createDisabled}
                  onChange={(event) => setCreateDraft((current) => ({ ...current, sourceUrl: event.target.value }))}
                  placeholder="https://docs.example.test/prd"
                />
              </label>
            </div>
            <label className="field" htmlFor="asset-create-description">
              <span>{translate('auto.k0443')}</span>
              <textarea
                id="asset-create-description"
                className="compact-textarea"
                value={createDraft.description}
                disabled={createDisabled}
                onChange={(event) => setCreateDraft((current) => ({ ...current, description: event.target.value }))}
              />
            </label>
            <label className="field" htmlFor="asset-create-acceptance">
              <span>{translate('auto.k0650')}</span>
              <textarea
                id="asset-create-acceptance"
                className="compact-textarea"
                value={createDraft.acceptanceCriteria}
                disabled={createDisabled}
                onChange={(event) => setCreateDraft((current) => ({ ...current, acceptanceCriteria: event.target.value }))}
              />
            </label>
            <label className="field" htmlFor="asset-create-tags">
              <span>{fieldLabel('tags')}</span>
              <input
                id="asset-create-tags"
                value={createDraft.tags}
                disabled={createDisabled}
                onChange={(event) => setCreateDraft((current) => ({ ...current, tags: event.target.value }))}
                placeholder="auth, mobile"
              />
            </label>
            <div className="document-actions">
              <button
                className="primary-button"
                type="submit"
                disabled={createDisabled || !createDraft.projectId.trim() || !createDraft.title.trim()}
              >
                    <Save size={16} />
                    {translate('auto.k0651')}</button>
              <button className="secondary-button" type="button" disabled={createState.loading} onClick={() => setOpenDrawer(null)}>
                {translate('actions.cancel')}</button>
            </div>
            <StateLine state={createState} />
          </form>
          </Drawer>
            </>
          ) : (
            <>
              <div className="panel-toolbar">
                <div className="section-heading">
                  <div className="section-icon">
                    <FilePlus2 size={20} />
                  </div>
                  <div>
                    <span className="eyebrow">{translate('auto.k0174')}</span>
                    <h2>{translate('auto.k0652')}</h2>
                  </div>
                </div>
                <button
                  className="primary-button"
                  type="button"
                  disabled={apiCreateDisabled}
                  onClick={openCreateApiDrawer}
                >
                  <FilePlus2 size={16} />
                  {translate('auto.k0656')}</button>
              </div>

              <StateLine state={apiCreateState} />
              <Drawer
                className="asset-form-drawer"
                destroyOnHidden
                maskClosable={!apiCreateState.loading}
                open={openDrawer === 'api-create'}
                placement="right"
                title={translate('auto.k0652')}
                width={800}
                onClose={() => {
                  if (!apiCreateState.loading) {
                    setOpenDrawer(null);
                  }
                }}
              >
              <form className="asset-form document-drawer-form" onSubmit={submitCreateApi}>
                <div className="asset-form-grid">
                  <label className="field" htmlFor="asset-api-create-project">
                    <span>{fieldLabel('projectId')}<b>*</b></span>
                    <input
                      id="asset-api-create-project"
                      value={apiCreateDraft.projectId}
                      disabled={apiCreateDisabled}
                      onChange={(event) => setApiCreateDraft((current) => ({ ...current, projectId: event.target.value }))}
                      placeholder="proj-payments"
                    />
                  </label>
                  <label className="field" htmlFor="asset-api-create-summary">
                    <span>{translate('auto.k0177')}<b>*</b></span>
                    <input
                      id="asset-api-create-summary"
                      value={apiCreateDraft.summary}
                      disabled={apiCreateDisabled}
                      onChange={(event) => setApiCreateDraft((current) => ({ ...current, summary: event.target.value }))}
                      placeholder={translate('auto.k0655')}
                    />
                  </label>
                  <label className="field" htmlFor="asset-api-create-method">
                    <span>{fieldLabel('method')}</span>
                    <select
                      id="asset-api-create-method"
                      value={apiCreateDraft.httpMethod}
                      disabled={apiCreateDisabled}
                      onChange={(event) => setApiCreateDraft((current) => ({ ...current, httpMethod: event.target.value }))}
                    >
                      {ASSET_API_METHODS.map((method) => (
                        <option key={method} value={method}>
                          {method}
                        </option>
                      ))}
                    </select>
                  </label>
                  <label className="field" htmlFor="asset-api-create-path">
                    <span>{fieldLabel('path')}<b>*</b></span>
                    <input
                      id="asset-api-create-path"
                      value={apiCreateDraft.path}
                      disabled={apiCreateDisabled}
                      onChange={(event) => setApiCreateDraft((current) => ({ ...current, path: event.target.value }))}
                      placeholder="/api/orders"
                    />
                  </label>
                  <label className="field" htmlFor="asset-api-create-status">
                    <span>{fieldLabel('status')}</span>
                    <select
                      id="asset-api-create-status"
                      value={apiCreateDraft.status}
                      disabled={apiCreateDisabled}
                      onChange={(event) => setApiCreateDraft((current) => ({ ...current, status: event.target.value }))}
                    >
                      {ASSET_API_STATUSES.map((status) => (
                        <option key={status} value={status}>{dictionaryLabel(status)}</option>
                      ))}
                    </select>
                  </label>
                  <label className="field" htmlFor="asset-api-create-version">
                    <span>{fieldLabel('version')}</span>
                    <input
                      id="asset-api-create-version"
                      value={apiCreateDraft.version}
                      disabled={apiCreateDisabled}
                      onChange={(event) => setApiCreateDraft((current) => ({ ...current, version: event.target.value }))}
                      placeholder="1.0.0"
                    />
                  </label>
                </div>
                <label className="field" htmlFor="asset-api-create-description">
                  <span>{translate('auto.k0443')}</span>
                  <textarea
                    id="asset-api-create-description"
                    className="compact-textarea"
                    value={apiCreateDraft.description}
                    disabled={apiCreateDisabled}
                    onChange={(event) => setApiCreateDraft((current) => ({ ...current, description: event.target.value }))}
                  />
                </label>
                <div className="asset-schema-grid">
                  <label className="field" htmlFor="asset-api-create-request-schema">
                    <span>{fieldLabel('requestSchema')}</span>
                    <textarea
                      id="asset-api-create-request-schema"
                      className="compact-textarea schema-textarea"
                      value={apiCreateDraft.requestSchema}
                      disabled={apiCreateDisabled}
                      onChange={(event) => setApiCreateDraft((current) => ({ ...current, requestSchema: event.target.value }))}
                    />
                  </label>
                  <label className="field" htmlFor="asset-api-create-response-schema">
                    <span>{fieldLabel('responseSchema')}</span>
                    <textarea
                      id="asset-api-create-response-schema"
                      className="compact-textarea schema-textarea"
                      value={apiCreateDraft.responseSchema}
                      disabled={apiCreateDisabled}
                      onChange={(event) => setApiCreateDraft((current) => ({ ...current, responseSchema: event.target.value }))}
                    />
                  </label>
                </div>
                <div className="document-actions">
                  <button
                    className="primary-button"
                    type="submit"
                    disabled={
                      apiCreateDisabled ||
                      !apiCreateDraft.projectId.trim() ||
                      !apiCreateDraft.summary.trim() ||
                      !apiCreateDraft.path.trim()
                    }
                  >
                    <Save size={16} />
                    {translate('auto.k0656')}</button>
                  <button className="secondary-button" type="button" disabled={apiCreateState.loading} onClick={() => setOpenDrawer(null)}>
                    {translate('actions.cancel')}</button>
                </div>
                <StateLine state={apiCreateState} />
              </form>
              </Drawer>
            </>
          )}
        </section>
      </div>

      <aside className="side-stack asset-side-stack">
        <section className="panel insight-panel">
          <h2>{translate('auto.k0426')}</h2>
          <div className="document-health-grid">
            <StatusMetric label={translate('auto.k0427')} value={health?.service ?? 'asset-service'} />
            <StatusMetric label={translate('auto.k0182')} value={health?.status ?? (props.signedIn ? translate('auto.k0428') : translate('auto.k0429'))} pill />
            {activeTab === 'requirements' ? (
              <>
                <StatusMetric label={translate('auto.k0657')} value={String(requirements.length)} />
                <StatusMetric label="DRAFT" value={String(statusCounts.DRAFT ?? 0)} />
                <StatusMetric label="REVIEWING" value={String(statusCounts.REVIEWING ?? 0)} />
                <StatusMetric label="APPROVED" value={String(statusCounts.APPROVED ?? 0)} />
              </>
            ) : (
              <>
                <StatusMetric label={translate('auto.k0658')} value={String(apis.length)} />
                <StatusMetric label="ACTIVE" value={String(apiStatusCounts.ACTIVE ?? 0)} />
                <StatusMetric label="DEPRECATED" value={String(apiStatusCounts.DEPRECATED ?? 0)} />
                <StatusMetric label="REMOVED" value={String(apiStatusCounts.REMOVED ?? 0)} />
              </>
            )}
          </div>
          {activeLoadState.error && (
            <div className="inline-error">
              <strong>{translate('auto.k0148')}</strong>
              <span>{activeLoadState.error}</span>
            </div>
          )}
        </section>

        <AssetImportExportPanel
          assetTypes={activeTab === 'apis' ? ['API'] : ['REQUIREMENT']}
          currentUser={props.currentUser}
          onImported={activeTab === 'apis' ? refreshApis : refreshRequirements}
          signedIn={props.signedIn}
        />

        <section className="panel insight-panel asset-detail-panel">
          {activeTab === 'requirements' ? (
            <>
          <div className="panel-title-row">
            <h2>{translate('auto.k0659')}</h2>
            {selectedRequirement && <AssetStatusPill value={selectedRequirement.status} />}
          </div>

          {selectedRequirement ? (
            <div className="asset-detail-stack">
              <div className="resource-summary">
                <strong>{selectedRequirement.title}</strong>
                <div>
                  <span>{fieldLabel('projectId')}</span>
                  <em>{selectedRequirement.projectId ?? '-'}</em>
                </div>
                <div>
                  <span>{fieldLabel('priority')}</span>
                  <em>{selectedRequirement.priority}</em>
                </div>
                <div>
                  <span>{fieldLabel('version')}</span>
                  <em>v{selectedRequirement.version || '-'}</em>
                </div>
                <div>
                  <span>{fieldLabel('id')}</span>
                  <em>{selectedRequirement.id}</em>
                </div>
                <div>
                  <span>{fieldLabel('createdAt')}</span>
                  <em>{formatDate(selectedRequirement.createdAt)}</em>
                </div>
              </div>

              <div className="asset-source-trace">
                <strong>{translate('auto.k0660')}</strong>
                <div>
                  <span>{fieldLabel('source')}</span>
                  <em>{selectedRequirement.source}</em>
                </div>
                <div>
                  <span>{fieldLabel('sourceRef')}</span>
                  <em>{selectedRequirement.sourceRef ?? '-'}</em>
                </div>
                <div>
                  <span>{fieldLabel('sourceUrl')}</span>
                  {selectedRequirement.sourceUrl ? (
                    <a href={selectedRequirement.sourceUrl} target="_blank" rel="noreferrer">
                      {selectedRequirement.sourceUrl}
                    </a>
                  ) : (
                    <em>-</em>
                  )}
                </div>
                <div>
                  <span>{fieldLabel('acceptanceCriteria')}</span>
                  <em>{selectedRequirement.acceptanceCriteria ?? '-'}</em>
                </div>
              </div>

              {canManageAssets && (
                <button className="mini-button" type="button" onClick={openEditRequirementDrawer} disabled={editDisabled}>
                  <Pencil size={14} />
                  {translate('auto.k0661')}</button>
              )}
              <Drawer
                className="asset-form-drawer"
                destroyOnHidden
                maskClosable={!mutationState.loading}
                open={openDrawer === 'requirement-edit'}
                placement="right"
                title={translate('auto.k0661')}
                width={700}
                onClose={() => {
                  if (!mutationState.loading) {
                    setOpenDrawer(null);
                  }
                }}
              >
              <form className="resource-edit-form asset-edit-form document-drawer-form" onSubmit={submitEdit}>
                <label>
                  <span>{translate('auto.k0440')}</span>
                  <input
                    value={editDraft.title}
                    disabled={editDisabled}
                    onChange={(event) => setEditDraft((current) => ({ ...current, title: event.target.value }))}
                  />
                </label>
                <label>
                  <span>{fieldLabel('priority')}</span>
                  <select
                    value={editDraft.priority}
                    disabled={editDisabled}
                    onChange={(event) => setEditDraft((current) => ({ ...current, priority: event.target.value }))}
                  >
                    {ASSET_REQUIREMENT_PRIORITIES.map((priority) => (
                      <option key={priority} value={priority}>{dictionaryLabel(priority)}</option>
                    ))}
                  </select>
                </label>
                <label>
                  <span>{translate('auto.k0443')}</span>
                  <textarea
                    className="compact-textarea"
                    value={editDraft.description}
                    disabled={editDisabled}
                    onChange={(event) => setEditDraft((current) => ({ ...current, description: event.target.value }))}
                  />
                </label>
                <label>
                  <span>{fieldLabel('tags')}</span>
                  <input
                    value={editDraft.tags}
                    disabled={editDisabled}
                    onChange={(event) => setEditDraft((current) => ({ ...current, tags: event.target.value }))}
                  />
                </label>
                {canManageAssets && (
                  <button className="mini-button" type="submit" disabled={editDisabled || !editDraft.title.trim()}>
                    <Save size={14} />
                    {translate('auto.k0661')}</button>
                )}
                <button className="mini-button" type="button" disabled={mutationState.loading} onClick={() => setOpenDrawer(null)}>
                  {translate('actions.cancel')}</button>
                <StateLine state={mutationState} />
              </form>
              </Drawer>

              {canReviewAssets && (
                <div className="asset-status-flow">
                  {nextStatuses(selectedRequirement.status).map((status) => (
                    <button
                      className="mini-button"
                      type="button"
                      key={status}
                      disabled={reviewDisabled}
                      onClick={() => changeStatus(status)}
                    >
                      <Send size={14} />
                      {statusActionLabel[status] ?? status}
                    </button>
                  ))}
                </div>
              )}

              <div className="asset-trace-links">
                <div className="panel-title-row">
                  <strong>{translate('auto.k0662')}</strong>
                  <span className="document-count-badge">{traceLinks.length}</span>
                </div>
                {traceLinks.length > 0 ? (
                  traceLinks.map((link) => (
                    <div className="trace-link-row" key={link.id || `${link.apiId}-${link.pageId}-${link.flowId}-${link.caseId}`}>
                      <span>
                        <strong>{link.apiId ?? 'API -'}</strong>
                        <em>{[link.pageId ? `Page ${link.pageId}` : '', link.flowId ? `Flow ${link.flowId}` : '', link.caseId ? `Case ${link.caseId}` : 'Case -'].filter(Boolean).join(' · ')}</em>
                      </span>
                      <em>{formatDate(link.createdAt)}</em>
                    </div>
                  ))
                ) : (
                  <div className="empty-state compact">
                    <Link2 size={20} />
                  <div>
                    <strong>{detailState.loading ? translate('auto.k0663') : translate('auto.k0664')}</strong>
                    <span>{detailState.error ?? translate('auto.k0665')}</span>
                  </div>
                </div>
                )}
              </div>

              <AssetVersionHistoryPanel
                currentVersion={selectedRequirement.version}
                disabled={disabled}
                items={requirementVersions}
                onRollback={(version) => void rollbackRequirement(version)}
                onRefresh={() => void reloadRequirementVersions(selectedRequirement.id)}
                state={requirementVersionState}
              />

              <StateLine state={mutationState} />
              <StateLine state={detailState} />
            </div>
          ) : (
            <div className="empty-state compact">
              <Pencil size={20} />
              <div>
                <strong>{detailState.loading ? translate('auto.k0437') : props.signedIn ? translate('auto.k0666') : translate('auto.k0429')}</strong>
                <span>{detailState.error ?? translate('auto.k0667')}</span>
              </div>
            </div>
          )}
            </>
          ) : (
            <>
              <div className="panel-title-row">
                <h2>{translate('auto.k0668')}</h2>
                {selectedApi && <AssetStatusPill value={selectedApi.status} />}
              </div>

              {selectedApi ? (
                <div className="asset-detail-stack">
                  <div className="resource-summary">
                    <strong>{selectedApi.summary}</strong>
                    <div>
                      <span>{fieldLabel('projectId')}</span>
                      <em>{selectedApi.projectId ?? '-'}</em>
                    </div>
                    <div>
                      <span>{fieldLabel('method')}</span>
                      <em>{selectedApi.httpMethod}</em>
                    </div>
                    <div>
                      <span>{fieldLabel('path')}</span>
                      <em>{selectedApi.path}</em>
                    </div>
                    <div>
                      <span>{fieldLabel('code')}</span>
                      <em>{selectedApi.code ?? '-'}</em>
                    </div>
                    <div>
                      <span>{fieldLabel('id')}</span>
                      <em>{selectedApi.id}</em>
                    </div>
                    <div>
                      <span>{fieldLabel('createdAt')}</span>
                      <em>{formatDate(selectedApi.createdAt)}</em>
                    </div>
                  </div>

                  <div className="asset-source-trace">
                    <strong>{translate('auto.k0660')}</strong>
                    <div>
                      <span>{fieldLabel('source')}</span>
                      <em>{selectedApi.source ?? '-'}</em>
                    </div>
                    <div>
                      <span>{fieldLabel('sourceRef')}</span>
                      <em>{selectedApi.sourceRef ?? '-'}</em>
                    </div>
                    <div>
                      <span>{fieldLabel('version')}</span>
                      <em>{selectedApi.version ?? '-'}</em>
                    </div>
                    <div>
                      <span>{fieldLabel('updatedAt')}</span>
                      <em>{formatDate(selectedApi.updatedAt)}</em>
                    </div>
                    <div>
                      <span>{fieldLabel('description')}</span>
                      <em>{selectedApi.description ?? '-'}</em>
                    </div>
                  </div>

                  <div className="asset-schema-preview">
                    <strong>{fieldLabel('requestSchema')}</strong>
                    <pre>{formatSchema(selectedApi.requestSchema)}</pre>
                  </div>
                  <div className="asset-schema-preview">
                    <strong>{fieldLabel('responseSchema')}</strong>
                    <pre>{formatSchema(selectedApi.responseSchema)}</pre>
                  </div>

                  {canManageAssets && (
                    <button className="mini-button" type="button" onClick={openEditApiDrawer} disabled={apiEditDisabled}>
                      <Pencil size={14} />
                      {translate('auto.k0669')}</button>
                  )}
                  <Drawer
                    className="asset-form-drawer"
                    destroyOnHidden
                    maskClosable={!apiMutationState.loading}
                    open={openDrawer === 'api-edit'}
                    placement="right"
                    title={translate('auto.k0669')}
                    width={760}
                    onClose={() => {
                      if (!apiMutationState.loading) {
                        setOpenDrawer(null);
                      }
                    }}
                  >
                  <form className="resource-edit-form asset-edit-form document-drawer-form" onSubmit={submitEditApi}>
                    <label>
                      <span>{translate('auto.k0177')}</span>
                      <input
                        value={apiEditDraft.summary}
                        disabled={apiEditDisabled}
                        onChange={(event) => setApiEditDraft((current) => ({ ...current, summary: event.target.value }))}
                      />
                    </label>
                    <label>
                      <span>{fieldLabel('method')}</span>
                      <select
                        value={apiEditDraft.httpMethod}
                        disabled={apiEditDisabled}
                        onChange={(event) => setApiEditDraft((current) => ({ ...current, httpMethod: event.target.value }))}
                      >
                        {ASSET_API_METHODS.map((method) => (
                          <option key={method} value={method}>
                            {method}
                          </option>
                        ))}
                      </select>
                    </label>
                    <label>
                      <span>{fieldLabel('path')}</span>
                      <input
                        value={apiEditDraft.path}
                        disabled={apiEditDisabled}
                        onChange={(event) => setApiEditDraft((current) => ({ ...current, path: event.target.value }))}
                      />
                    </label>
                    <label>
                      <span>{fieldLabel('status')}</span>
                      <select
                        value={apiEditDraft.status}
                        disabled={apiEditDisabled}
                        onChange={(event) => setApiEditDraft((current) => ({ ...current, status: event.target.value }))}
                      >
                        {apiStatusOptions(selectedApi.status).map((status) => (
                          <option key={status} value={status}>{dictionaryLabel(status)}</option>
                        ))}
                      </select>
                    </label>
                    <label>
                      <span>{fieldLabel('version')}</span>
                      <input
                        value={apiEditDraft.version}
                        disabled={apiEditDisabled}
                        onChange={(event) => setApiEditDraft((current) => ({ ...current, version: event.target.value }))}
                      />
                    </label>
                    <label>
                      <span>{translate('auto.k0443')}</span>
                      <textarea
                        className="compact-textarea"
                        value={apiEditDraft.description}
                        disabled={apiEditDisabled}
                        onChange={(event) => setApiEditDraft((current) => ({ ...current, description: event.target.value }))}
                      />
                    </label>
                    <label>
                      <span>{fieldLabel('requestSchema')}</span>
                      <textarea
                        className="compact-textarea schema-textarea"
                        value={apiEditDraft.requestSchema}
                        disabled={apiEditDisabled}
                        onChange={(event) => setApiEditDraft((current) => ({ ...current, requestSchema: event.target.value }))}
                      />
                    </label>
                    <label>
                      <span>{fieldLabel('responseSchema')}</span>
                      <textarea
                        className="compact-textarea schema-textarea"
                        value={apiEditDraft.responseSchema}
                        disabled={apiEditDisabled}
                        onChange={(event) => setApiEditDraft((current) => ({ ...current, responseSchema: event.target.value }))}
                      />
                    </label>
                    {canManageAssets && (
                      <button
                        className="mini-button"
                        type="submit"
                        disabled={apiEditDisabled || !apiEditDraft.summary.trim() || !apiEditDraft.path.trim()}
                      >
                        <Save size={14} />
                        {translate('auto.k0669')}</button>
                    )}
                    <button className="mini-button" type="button" disabled={apiMutationState.loading} onClick={() => setOpenDrawer(null)}>
                      {translate('actions.cancel')}</button>
                    <StateLine state={apiMutationState} />
                  </form>
                  </Drawer>

                  <AssetVersionHistoryPanel
                    currentVersion={apiVersions[0]?.version}
                    disabled={apiDisabled}
                    items={apiVersions}
                    onRollback={(version) => void rollbackApi(version)}
                    onRefresh={() => void reloadApiVersions(selectedApi.id)}
                    state={apiVersionState}
                  />

                  <StateLine state={apiMutationState} />
                  <StateLine state={apiDetailState} />
                </div>
              ) : (
                <div className="empty-state compact">
                  <Pencil size={20} />
                  <div>
                    <strong>{apiDetailState.loading ? translate('auto.k0437') : props.signedIn ? translate('auto.k0670') : translate('auto.k0429')}</strong>
                    <span>{apiDetailState.error ?? translate('auto.k0671')}</span>
                  </div>
                </div>
              )}
            </>
          )}
        </section>
      </aside>
    </section>
  );
}

function assetLocationFromHash(): { tab: AssetTabKey; id: string } {
  const parts = window.location.hash.replace(/^#\/?/, '').split('/');
  if (parts[0] !== 'asset-library') {
    return { tab: 'requirements', id: '' };
  }
  const requestedTab = assetTabs.find((tab) => tab.key === parts[1] && tab.enabled)?.key ?? 'requirements';
  return { tab: requestedTab, id: parts[2] ? decodeURIComponent(parts[2]) : '' };
}

function buildRequirementFilters(filters: RequirementFilters): AssetRequirementFilters {
  return {
    size: 50,
    projectId: filters.projectId,
    status: filters.status,
    keyword: filters.keyword,
    source: filters.source,
    sourceRef: filters.sourceRef
  };
}

function buildApiFilters(filters: ApiFilters): AssetApiFilters {
  return {
    size: 50,
    projectId: filters.projectId,
    status: filters.status,
    keyword: filters.keyword,
    source: filters.source
  };
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
    if (filters.source.trim() && requirement.source !== filters.source.trim()) {
      return false;
    }
    if (filters.sourceRef.trim() && !(requirement.sourceRef ?? '').includes(filters.sourceRef.trim())) {
      return false;
    }
    if (!keyword) {
      return true;
    }
    return [
      requirement.title,
      requirement.description,
      requirement.acceptanceCriteria,
      requirement.sourceRef,
      requirement.projectId,
      requirement.tags.join(',')
    ]
      .filter(Boolean)
      .some((value) => String(value).toLowerCase().includes(keyword));
  });
}

function filterApis(apis: AssetApiView[], filters: ApiFilters) {
  const keyword = filters.keyword.trim().toLowerCase();
  return apis.filter((api) => {
    if (filters.projectId.trim() && api.projectId !== filters.projectId.trim()) {
      return false;
    }
    if (filters.status.trim() && api.status !== filters.status.trim()) {
      return false;
    }
    if (filters.method.trim() && api.httpMethod !== filters.method.trim()) {
      return false;
    }
    if (filters.source.trim() && (api.source ?? '').toLowerCase() !== filters.source.trim().toLowerCase()) {
      return false;
    }
    if (!keyword) {
      return true;
    }
    return [api.summary, api.description, api.code, api.path, api.sourceRef, api.projectId, api.httpMethod]
      .filter(Boolean)
      .some((value) => String(value).toLowerCase().includes(keyword));
  });
}

function countRequirementsByStatus(requirements: AssetRequirementView[]) {
  return requirements.reduce<Record<string, number>>((counts, requirement) => {
    counts[requirement.status] = (counts[requirement.status] ?? 0) + 1;
    return counts;
  }, {});
}

function countApisByStatus(apis: AssetApiView[]) {
  return apis.reduce<Record<string, number>>((counts, api) => {
    counts[api.status] = (counts[api.status] ?? 0) + 1;
    return counts;
  }, {});
}

function requirementDraftFromView(requirement: AssetRequirementView): RequirementDraft {
  return {
    projectId: requirement.projectId ?? '',
    title: requirement.title,
    description: requirement.description ?? '',
    source: requirement.source,
    sourceRef: requirement.sourceRef ?? '',
    sourceUrl: requirement.sourceUrl ?? '',
    acceptanceCriteria: requirement.acceptanceCriteria ?? '',
    status: requirement.status,
    priority: requirement.priority,
    tags: requirement.tags.join(', ')
  };
}

function apiDraftFromView(api: AssetApiView): ApiDraft {
  return {
    projectId: api.projectId ?? '',
    summary: api.summary,
    description: api.description ?? '',
    httpMethod: api.httpMethod || 'GET',
    path: api.path,
    version: api.version ?? '',
    requestSchema: api.requestSchema ?? '',
    responseSchema: api.responseSchema ?? '',
    status: api.status
  };
}

function draftToCreatePayload(draft: RequirementDraft): AssetRequirementPayload {
  return {
    projectId: draft.projectId,
    title: draft.title,
    description: draft.description,
    source: draft.source,
    sourceRef: draft.sourceRef,
    sourceUrl: draft.sourceUrl,
    acceptanceCriteria: draft.acceptanceCriteria,
    status: draft.status,
    priority: draft.priority,
    tags: tagsFromText(draft.tags)
  };
}

function apiDraftToCreatePayload(draft: ApiDraft): AssetApiPayload {
  return {
    projectId: draft.projectId,
    summary: draft.summary,
    description: draft.description,
    httpMethod: draft.httpMethod,
    path: draft.path,
    version: draft.version,
    requestSchema: draft.requestSchema,
    responseSchema: draft.responseSchema,
    status: draft.status
  };
}

function draftToUpdatePayload(
  current: AssetRequirementView,
  draft: RequirementDraft,
  status = draft.status || current.status
): AssetRequirementPayload {
  return {
    title: draft.title || current.title,
    description: draft.description,
    status,
    priority: draft.priority || current.priority,
    tags: tagsFromText(draft.tags)
  };
}

function apiDraftToUpdatePayload(current: AssetApiView, draft: ApiDraft): AssetApiPayload {
  return {
    summary: draft.summary || current.summary,
    description: draft.description,
    httpMethod: draft.httpMethod || current.httpMethod,
    path: draft.path || current.path,
    version: draft.version,
    requestSchema: draft.requestSchema,
    responseSchema: draft.responseSchema,
    status: draft.status || current.status
  };
}

function tagsFromText(value: string) {
  return value
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean);
}

function nextStatuses(status: string) {
  return statusTransitionMap[status] ?? ['REVIEWING'];
}

function apiStatusOptions(status: string) {
  return apiStatusTransitionMap[status] ?? ASSET_API_STATUSES;
}

function upsertRequirement(
  setter: (updater: (current: AssetRequirementView[]) => AssetRequirementView[]) => void,
  requirement: AssetRequirementView
) {
  setter((current) => {
    const existing = current.findIndex((item) => item.id === requirement.id);
    if (existing < 0) {
      return [requirement, ...current];
    }
    return current.map((item) => (item.id === requirement.id ? requirement : item));
  });
}

function upsertApi(
  setter: (updater: (current: AssetApiView[]) => AssetApiView[]) => void,
  api: AssetApiView
) {
  setter((current) => {
    const existing = current.findIndex((item) => item.id === api.id);
    if (existing < 0) {
      return [api, ...current];
    }
    return current.map((item) => (item.id === api.id ? api : item));
  });
}

function formatDate(value?: string) {
  if (!value) {
    return '-';
  }
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
}

function formatSchema(value?: string) {
  if (!value?.trim()) {
    return '-';
  }
  try {
    return JSON.stringify(JSON.parse(value), null, 2);
  } catch {
    return value;
  }
}

function errorMessage(error: unknown, fallback: string) {
  if (error instanceof Error && error.message) {
    return error.message;
  }
  return fallback;
}

function StateLine(props: { state: WorkState }) {
  if (props.state.loading) {
    return <span className="document-state-line">{translate('auto.k0458')}</span>;
  }
  if (props.state.error) {
    return <span className="document-state-line error">{props.state.error}</span>;
  }
  if (props.state.success) {
    return (
      <span className="document-state-line success">
        {props.state.success}{props.state.traceId ? ` · ${props.state.traceId}` : ''}
      </span>
    );
  }
  if (props.state.traceId) {
    return <span className="document-state-line">Trace ID：{props.state.traceId}</span>;
  }
  return null;
}

function StatusMetric(props: { label: string; value: string; pill?: boolean }) {
  return (
    <div className="status-item">
      <span>{fieldLabel(props.label)}</span>
      {props.pill ? <AssetStatusPill value={props.value} /> : <strong>{props.value}</strong>}
    </div>
  );
}

function AssetStatusPill(props: { value: string }) {
  const value = props.value || 'UNKNOWN';
  const normalized = value.toUpperCase();
  const tone = ['APPROVED', 'ACTIVE', 'UP', 'ON', 'IMPORT', 'MANUAL'].includes(normalized)
    ? 'positive'
    : ['DRAFT', 'REVIEWING', 'MEDIUM', 'HIGH', 'CRITICAL'].includes(normalized)
      ? 'pending'
      : ['DEPRECATED', 'REMOVED', 'FAILED', 'DOWN', 'OFF', 'ERROR'].includes(normalized)
        ? 'negative'
        : 'neutral';
  return <span className={`status-pill ${tone}`} title={value}>{dictionaryLabel(value)}</span>;
}
