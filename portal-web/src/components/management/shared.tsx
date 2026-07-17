import { type LucideIcon } from 'lucide-react';
import { Drawer } from 'antd';
import { useEffect, useState } from 'react';
import type * as React from 'react';
import type { CurrentUser } from '../../api/auth';
import {
  fetchRole,
  runEnvironmentConnectivityCheck,
  type AuditOutboxFilters,
  type CreatableManagementResource,
  type EnvironmentConnectivityCheckView,
  type ManagementData,
  type PageResponse as ManagementPageResponse,
  type PermissionView,
  type ProjectMemberView,
  type RoleDetailView,
  type RoleView,
  type ScopedUserRoleView
} from '../../api/management';
import { type PageKey, type UserLifecycleAction } from '../../permissions';
import { dictionaryLabel } from '../../platform/dictionaries';
import { translate } from '../../platform/i18n';
import { InputControl, SelectControl } from '../ui';

export function optionalBoolean(value: string | undefined): boolean | undefined {
  if (value === 'true') return true;
  if (value === 'false') return false;
  return undefined;
}

/* ===================== Shared Props ===================== */

export interface ManagementPageProps {
  page: PageKey;
  data: ManagementData;
  loadState: { loading: boolean; error?: string };
  signedIn: boolean;
  currentUser: CurrentUser | null;
  onCreate: (resource: CreatableManagementResource, label: string, name: string) => Promise<void>;
  onUserLifecycleAction: (username: string, action: UserLifecycleAction, roleCode?: string) => Promise<void>;
  onResetPassword: (username: string) => void;
  auditExportState: { loading: boolean; error?: string };
  onAuditExport: () => Promise<void>;
  auditOutboxFilters: AuditOutboxFilters;
  auditOutboxLoad: { loading: boolean; error?: string };
  onAuditOutboxFiltersChange: (filters: AuditOutboxFilters) => void;
  onAuditOutboxRefresh: (filters?: AuditOutboxFilters) => Promise<void>;
  onRefresh: () => void;
}

/* ===================== Status Badge ===================== */

export function StatusBadge({ status }: { status: string }) {
  const map: Record<string, string> = {
    [translate('auto.k0375')]: 'badge-success',
    [translate('auto.k0380')]: 'badge-success',
    'ENABLED': 'badge-success',
    'ACTIVE': 'badge-success',
    [translate('auto.k0377')]: 'badge-success',
    [translate('auto.k0065')]: 'badge-success',
    [translate('auto.k0250')]: 'badge-success',
    [translate('auto.k0066')]: 'badge-success',
    [translate('auto.k0095')]: 'badge-success',
    'DISABLED': 'badge-warning',
    [translate('auto.k0253')]: 'badge-danger',
    [translate('auto.k0067')]: 'badge-danger',
    [translate('auto.k0252')]: 'badge-danger',
    [translate('auto.k0068')]: 'badge-danger',
    'PREPARING': 'badge-warning',
    'ARCHIVED': 'badge-neutral',
    [translate('auto.k2605')]: 'badge-warning',
    'DISCONNECTED': 'badge-warning',
    'DRAFT': 'badge-warning'
  };
  const cls = map[status] || 'badge-neutral';
  return <span className={`badge ${cls}`} title={status}>{dictionaryLabel(status)}</span>;
}

/* ===================== DataSection (table + create + side panel) ===================== */

export interface DataSectionProps {
  eyebrow: string;
  title: string;
  icon: LucideIcon;
  action: string;
  createResource?: CreatableManagementResource;
  columns: string[];
  rows: (string | number | React.ReactNode)[][];
  loadState: { loading: boolean; error?: string };
  signedIn: boolean;
  canCreate?: boolean;
  onCreate?: (resource: CreatableManagementResource, label: string, name: string) => Promise<void>;
  onRefresh?: () => void;
  sidePanel?: React.ReactNode;
}

export function DataSection(props: DataSectionProps) {
  const [quickCreateValue, setQuickCreateValue] = useState('');
  const [createOpen, setCreateOpen] = useState(false);
  const Icon = props.icon;
  const quickCreatePlaceholder = translate('auto.k0327', {
    value0: props.action
      .replace(translate('auto.k0894'), '')
      .replace(translate('auto.k0862'), '')
      .replace(translate('auto.k2606'), '')
      || translate('auto.k0177')
  });

  async function submitQuickCreate(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!props.onCreate || !props.createResource || !quickCreateValue.trim()) {
      return;
    }
    await props.onCreate(props.createResource, props.action, quickCreateValue);
    setQuickCreateValue('');
    setCreateOpen(false);
  }

  return (
    <div className="content-grid">
      <div className="panel">
        <div className="panel-header">
          <div>
            <div className="management-section-heading">
              <div className="section-icon management-section-icon"><Icon size={17} /></div>
              <div>
                <div className="text-tertiary text-xs font-semibold management-eyebrow">{props.eyebrow}</div>
                <h2 className="panel-title">{props.title}</h2>
              </div>
            </div>
          </div>
          <div className="toolbar-actions management-toolbar-actions">
            {props.canCreate && props.onCreate && (
              <button
                className="btn btn-primary btn-sm"
                disabled={!props.signedIn || props.loadState.loading}
                type="button"
                onClick={() => setCreateOpen(true)}
              >
                {props.action}
              </button>
            )}
            {props.onRefresh && (
              <button className="btn btn-secondary btn-sm" onClick={props.onRefresh} disabled={props.loadState.loading}>
                {translate('auto.k0170')}</button>
            )}
          </div>
        </div>
        <div className="panel-body">
          {props.loadState.error && (
            <div className="notice error management-notice">{props.loadState.error}</div>
          )}
          {props.rows.length === 0 && !props.loadState.loading ? (
            <div className="empty-state">
              <div className="empty-state-icon"><Icon size={32} opacity={0.4} /></div>
              <strong>{translate('auto.k0328')}</strong>
              <span>{translate('auto.k0329')}{props.title}{translate('auto.k0330')}</span>
            </div>
          ) : (
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>{props.columns.map((col) => <th key={col}>{col}</th>)}</tr>
                </thead>
                <tbody>
                  {props.loadState.loading ? (
                    <tr><td colSpan={props.columns.length}><div className="skeleton skeleton-text management-skeleton-row" /></td></tr>
                  ) : (
                    props.rows.map((row, idx) => (
                      <tr key={idx}>{row.map((cell, ci) => <td key={ci}>{cell}</td>)}</tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </div>
      {props.canCreate && props.onCreate && (
        <Drawer
          className="management-create-drawer"
          destroyOnHidden
          footer={null}
          maskClosable={!props.loadState.loading}
          open={createOpen}
          placement="right"
          title={props.action}
          width={420}
          onClose={() => {
            if (!props.loadState.loading) {
              setCreateOpen(false);
            }
          }}
        >
          <form className="document-form document-drawer-form" onSubmit={(event) => void submitQuickCreate(event)}>
            <label className="field">
              <span>{translate('auto.k0177')}<b>*</b></span>
              <InputControl
                autoFocus
                type="text"
                placeholder={quickCreatePlaceholder}
                value={quickCreateValue}
                onChange={(event) => setQuickCreateValue(event.target.value)}
                disabled={!props.signedIn || props.loadState.loading}
              />
            </label>
            <div className="document-actions">
              <button
                className="btn btn-primary btn-sm"
                disabled={!props.signedIn || props.loadState.loading || !quickCreateValue.trim()}
                type="submit"
              >
                {props.action}
              </button>
              <button className="btn btn-secondary btn-sm" type="button" disabled={props.loadState.loading} onClick={() => setCreateOpen(false)}>
                {translate('auto.k0739')}
              </button>
            </div>
          </form>
        </Drawer>
      )}

      {props.sidePanel && (
        <div className="side-stack">
          {props.sidePanel}
        </div>
      )}
    </div>
  );
}

/* ===================== Resource Lifecycle Panel ===================== */

export interface StatusOption {
  value: string;
  label: string;
  icon: LucideIcon;
}

export interface ResourceLifecyclePanelProps<T> {
  title: string;
  resourceLabel: string;
  emptyLabel: string;
  resources: string[];
  fields: { key: string; label: string; placeholder?: string; kind?: 'text' | 'select' | 'public-model'; options?: Array<{ value: string; label: string }> }[];
  signedIn: boolean;
  canEdit: boolean;
  statusOptions: StatusOption[];
  fetchDetail: (key: string) => Promise<{ data: T }>;
  updateDetail: (resourceKey: string, draft: Record<string, string>) => Promise<unknown>;
  changeStatus: (resourceKey: string, status: string) => Promise<unknown>;
  detailTitle: (detail: T) => string;
  draftFromDetail: (detail: T) => Record<string, string>;
  detailRows: (detail: T) => Array<[string, string | number | React.ReactNode | null | undefined]>;
  onChanged: () => void;
}

export function ResourceLifecyclePanel<T extends object>(props: ResourceLifecyclePanelProps<T>) {
  const [selectedKey, setSelectedKey] = useState('');
  const [detail, setDetail] = useState<T | null>(null);
  const [editDraft, setEditDraft] = useState<Record<string, string> | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!selectedKey) { setDetail(null); setEditDraft(null); return; }
    setLoading(true);
    setError('');
    props.fetchDetail(selectedKey)
      .then((r) => { setDetail(r.data); setEditDraft(props.draftFromDetail(r.data)); setLoading(false); })
      .catch((err: unknown) => { setError(err instanceof Error ? err.message : translate('auto.k0049')); setLoading(false); });
  }, [selectedKey, props]);

  async function save() {
    if (!selectedKey || !editDraft) return;
    setLoading(true);
    setError('');
    try {
      await props.updateDetail(selectedKey, editDraft);
      setDetail(null); setEditDraft(null); setSelectedKey('');
      props.onChanged();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : translate('auto.k0331'));
      setLoading(false);
    }
  }

  async function changeStatus(status: string) {
    if (!selectedKey) return;
    setLoading(true);
    setError('');
    try {
      await props.changeStatus(selectedKey, status);
      setDetail(null); setEditDraft(null); setSelectedKey('');
      props.onChanged();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : translate('auto.k0332'));
      setLoading(false);
    }
  }

  return (
    <div className="panel">
      <div className="panel-body">
        <div className="management-side-heading">
          <div className="text-tertiary text-xs font-semibold">{translate('auto.k0333')}</div>
          <h3 className="panel-title">{props.title}</h3>
        </div>

        <div className="field management-select-field">
          <SelectControl
            value={selectedKey}
            onChange={(e) => setSelectedKey(e.target.value)}
            disabled={!props.signedIn}
          >
            <option value="">{translate('auto.k0334')}{props.resourceLabel}...</option>
            {props.resources.map((r) => (
              <option key={r} value={r}>{r}</option>
            ))}
          </SelectControl>
        </div>

        {loading && <div className="skeleton skeleton-text" />}
        {error && <div className="notice error management-notice-sm">{error}</div>}

        {detail && !loading && (
          <>
            <div className="divider" />
            <div className="detail-grid">
              {props.detailRows(detail).map(([label, value]) => (
                <div className="detail-row" key={label}>
                  <span className="detail-label">{label}</span>
                  <span className="detail-value">{value || '-'}</span>
                </div>
              ))}
            </div>

            {props.canEdit && editDraft && (
              <>
                <div className="divider" />
                <div className="management-edit-form">
                  {props.fields.map((field) => (
                    <div className="field" key={field.key}>
                      <label className="field-label">{field.label}</label>
                      {field.kind === 'select' || field.kind === 'public-model' ? (
                        <SelectControl
                          value={editDraft[field.key] ?? ''}
                          onChange={(e) => setEditDraft((d) => d ? { ...d, [field.key]: e.target.value } : d)}
                        >
                          {(field.options ?? []).map((opt) => (
                            <option key={opt.value} value={opt.value}>{opt.label}</option>
                          ))}
                        </SelectControl>
                      ) : (
                        <InputControl
                          type="text"
                          value={editDraft[field.key] ?? ''}
                          onChange={(e) => setEditDraft((d) => d ? { ...d, [field.key]: e.target.value } : d)}
                          placeholder={field.placeholder}
                        />
                      )}
                    </div>
                  ))}
                  <button className="btn btn-primary btn-sm" onClick={save} disabled={loading}>
                    {loading ? translate('auto.k0335') : translate('auto.k0336')}
                  </button>
                </div>
              </>
            )}

            {props.statusOptions.length > 0 && (
              <>
                <div className="divider" />
                <div className="management-status-actions">
                  {props.statusOptions.map((opt) => {
                    const OptIcon = opt.icon;
                    return (
                      <button key={opt.value} className="btn btn-secondary btn-sm" onClick={() => changeStatus(opt.value)} disabled={loading}>
                        <OptIcon size={14} />{opt.label}
                      </button>
                    );
                  })}
                </div>
              </>
            )}
          </>
        )}
      </div>
    </div>
  );
}

/* ===================== Role Binding Controls ===================== */

export function RoleBindingControls(props: {
  username: string;
  roles: RoleView[];
  loading: boolean;
  signedIn: boolean;
  canAssign: boolean;
  canUnassign: boolean;
  onAction: (username: string, action: UserLifecycleAction, roleCode?: string) => Promise<void>;
}) {
  const [selectedRole, setSelectedRole] = useState('');
  if (!props.canAssign && !props.canUnassign) return null;
  return (
    <div className="role-binding-controls">
      {props.canAssign && (
        <>
          <SelectControl
            value={selectedRole}
            onChange={(e) => setSelectedRole(e.target.value)}
            disabled={!props.signedIn}
          >
            <option value="">{translate('auto.k0247')}</option>
            {props.roles.map((r) => <option key={r.code} value={r.code}>{r.name}</option>)}
          </SelectControl>
          <button
            className="btn btn-xs btn-secondary"
            disabled={!props.signedIn || props.loading || !selectedRole}
            onClick={() => { props.onAction(props.username, 'assign-role', selectedRole); setSelectedRole(''); }}
          >
            {translate('auto.k0337')}</button>
        </>
      )}
      {props.canUnassign && selectedRole && (
        <button
          className="btn btn-xs btn-secondary"
          disabled={!props.signedIn || props.loading}
          onClick={() => { props.onAction(props.username, 'unassign-role', selectedRole); setSelectedRole(''); }}
        >
          {translate('auto.k0338')}</button>
      )}
    </div>
  );
}

/* ===================== Scoped Role Panel ===================== */

export type ScopedMemberView = ScopedUserRoleView | ProjectMemberView;
export type ScopedMembersPayload = ScopedMemberView[] | ManagementPageResponse<ScopedMemberView>;

export function scopedMembers(payload: ScopedMembersPayload): ScopedMemberView[] {
  return Array.isArray(payload) ? payload : payload.items;
}

export function ScopedRolePanel(props: {
  title: string;
  resourceLabel: string;
  emptyLabel: string;
  resources: string[];
  roles: string[];
  signedIn: boolean;
  canManage: boolean;
  fetchMembers: (key: string) => Promise<{ data: ScopedMembersPayload }>;
  addMember: (resourceKey: string, username: string, roleCode: string) => Promise<unknown>;
  removeMember: (resourceKey: string, username: string) => Promise<unknown>;
  onChanged: () => void;
}) {
  const [selectedKey, setSelectedKey] = useState('');
  const [members, setMembers] = useState<ScopedMemberView[]>([]);
  const [username, setUsername] = useState('');
  const [roleCode, setRoleCode] = useState(props.roles[0] ?? '');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!selectedKey) { setMembers([]); return; }
    setLoading(true);
    setError('');
    props.fetchMembers(selectedKey)
      .then((r) => { setMembers(scopedMembers(r.data)); setLoading(false); })
      .catch((err: unknown) => { setError(err instanceof Error ? err.message : translate('auto.k0049')); setLoading(false); });
  }, [selectedKey, props]);

  async function add() {
    if (!selectedKey || !username.trim()) return;
    setLoading(true);
    setError('');
    try {
      await props.addMember(selectedKey, username.trim(), roleCode || props.roles[0] || '');
      setUsername('');
      props.onChanged();
      const r = await props.fetchMembers(selectedKey);
      setMembers(scopedMembers(r.data));
      setLoading(false);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : translate('auto.k0339'));
      setLoading(false);
    }
  }

  async function remove(username: string) {
    if (!selectedKey) return;
    setLoading(true);
    setError('');
    try {
      await props.removeMember(selectedKey, username);
      props.onChanged();
      const r = await props.fetchMembers(selectedKey);
      setMembers(scopedMembers(r.data));
      setLoading(false);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : translate('auto.k0340'));
      setLoading(false);
    }
  }

  return (
    <div className="panel">
      <div className="panel-body">
        <div className="management-side-heading">
          <div className="text-tertiary text-xs font-semibold management-eyebrow">{translate('auto.k0235')}</div>
          <h3 className="panel-title management-side-title">{props.title}</h3>
        </div>

        <div className="field management-select-field">
          <SelectControl
            value={selectedKey}
            onChange={(e) => setSelectedKey(e.target.value)}
            disabled={!props.signedIn}
            className="management-full-control"
          >
            <option value="">{translate('auto.k0334')}{props.resourceLabel}...</option>
            {props.resources.map((r) => <option key={r} value={r}>{r}</option>)}
          </SelectControl>
        </div>

        {error && <div className="notice error management-notice-sm">{error}</div>}

        {selectedKey && (
          <>
            {loading ? (
              <div className="skeleton skeleton-text" />
            ) : members.length === 0 ? (
              <div className="empty-state management-empty-compact">
                <span>{translate('auto.k0341')}</span>
              </div>
            ) : (
              <div className="management-item-list">
                {members.map((m) => (
                  <div key={m.username} className="management-list-row">
                    <div>
                      <div className="management-primary-text">{m.display_name || m.username}</div>
                      <div className="text-tertiary text-xs">{roleDisplayLabel(m.role)}</div>
                    </div>
                    {props.canManage && (
                      <button className="btn btn-xs btn-secondary" onClick={() => remove(m.username)} disabled={loading}>{translate('auto.k0342')}</button>
                    )}
                  </div>
                ))}
              </div>
            )}

            {props.canManage && (
              <div className="management-member-add-grid">
                <InputControl
                  type="text"
                  placeholder={translate('auto.k0343')}
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  className="management-compact-control"
                />
                <SelectControl
                  value={roleCode}
                  onChange={(e) => setRoleCode(e.target.value)}
                  className="management-compact-control"
                >
                  {props.roles.map((role) => <option key={role} value={role}>{roleDisplayLabel(role)}</option>)}
                </SelectControl>
                <button className="btn btn-primary btn-sm" onClick={add} disabled={loading || !username.trim()}>{translate('auto.k0344')}</button>
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
}

/* ===================== Environment Connectivity Panel ===================== */

export function EnvironmentConnectivityPanel(props: {
  resources: string[];
  signedIn: boolean;
  canRun: boolean;
  onChanged: () => void;
}) {
  const [selectedKey, setSelectedKey] = useState('');
  const [result, setResult] = useState<EnvironmentConnectivityCheckView | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => { setResult(null); setError(''); }, [selectedKey]);

  async function check() {
    if (!selectedKey) return;
    setLoading(true);
    setError('');
    try {
      const r = await runEnvironmentConnectivityCheck(selectedKey);
      setResult(r.data);
      setLoading(false);
      props.onChanged();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : translate('auto.k0345'));
      setLoading(false);
    }
  }

  return (
    <div className="panel">
      <div className="panel-body">
        <div className="management-side-heading">
          <div className="text-tertiary text-xs font-semibold management-eyebrow">{translate('auto.k0346')}</div>
          <h3 className="panel-title management-side-title">{translate('auto.k0347')}</h3>
        </div>

        <div className="field management-select-field">
          <SelectControl
            value={selectedKey}
            onChange={(e) => setSelectedKey(e.target.value)}
            disabled={!props.signedIn}
            className="management-full-control"
          >
            <option value="">{translate('auto.k0348')}</option>
            {props.resources.map((r) => <option key={r} value={r}>{r}</option>)}
          </SelectControl>
        </div>

        {props.canRun && selectedKey && (
          <button className="btn btn-primary btn-sm management-full-action" onClick={check} disabled={loading}>
            {loading ? translate('auto.k0349') : translate('auto.k0350')}
          </button>
        )}

        {error && <div className="notice error management-notice-sm">{error}</div>}

        {result && (
          <div className="management-result-card">
            <div className="management-result-head">
              <StatusBadge status={result.status} />
              <span className="text-tertiary text-xs">{result.latencyMs ?? '-'}ms</span>
            </div>
            <p className="text-secondary text-sm management-wrap-text">{result.message}</p>
          </div>
        )}
      </div>
    </div>
  );
}

/* ===================== Role Definition Panel ===================== */

export function RoleDefinitionPanel(props: {
  roles: RoleView[];
  permissions: PermissionView[];
  signedIn: boolean;
  currentUser: CurrentUser | null;
  onChanged: () => void;
}) {
  const [selectedCode, setSelectedCode] = useState('');
  const [detail, setDetail] = useState<RoleDetailView | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const rolePermissions = detail?.permissionCodes ?? detail?.permission_codes ?? [];

  useEffect(() => {
    if (!selectedCode) { setDetail(null); return; }
    setLoading(true);
    setError('');
    fetchRole(selectedCode)
      .then((r) => { setDetail(r.data); setLoading(false); })
      .catch((err: unknown) => { setError(err instanceof Error ? err.message : translate('auto.k0049')); setLoading(false); });
  }, [selectedCode]);

  return (
    <div className="panel">
      <div className="panel-body">
        <div className="management-side-heading">
          <div className="text-tertiary text-xs font-semibold management-eyebrow">{translate('auto.k0262')}</div>
          <h3 className="panel-title management-side-title">{translate('auto.k0351')}</h3>
        </div>

        <div className="field management-select-field">
          <SelectControl
            value={selectedCode}
            onChange={(e) => setSelectedCode(e.target.value)}
            disabled={!props.signedIn}
            className="management-full-control"
          >
            <option value="">{translate('auto.k0352')}</option>
            {props.roles.map((r) => <option key={r.code} value={r.code}>{r.name} ({r.code})</option>)}
          </SelectControl>
        </div>

        {loading && <div className="skeleton skeleton-text" />}
        {error && <div className="notice error management-notice-sm">{error}</div>}

        {detail && !loading && (
          <>
            <div className="divider" />
            <div className="detail-grid">
              <div className="detail-row">
                <span className="detail-label">{translate('auto.k0177')}</span>
                <span className="detail-value">{detail.name}</span>
              </div>
              <div className="detail-row">
                <span className="detail-label">{translate('auto.k0353')}</span>
                <span className="detail-value">{detail.code}</span>
              </div>
              <div className="detail-row">
                <span className="detail-label">{translate('auto.k0182')}</span>
                <span className="detail-value"><StatusBadge status={detail.status} /></span>
              </div>
              {detail.description && (
                <div className="detail-row">
                  <span className="detail-label">{translate('auto.k0264')}</span>
                  <span className="detail-value">{detail.description}</span>
                </div>
              )}
            </div>

            <div className="divider" />
            <div className="text-tertiary text-xs font-semibold management-permission-heading">{translate('auto.k0354')}{rolePermissions.length})</div>
            {rolePermissions.length > 0 ? (
              <div className="management-chip-list">
                {rolePermissions.map((p: string) => (
                  <span key={p} className="badge badge-info">{p}</span>
                ))}
              </div>
            ) : (
              <div className="text-tertiary text-sm">{translate('auto.k0355')}</div>
            )}
          </>
        )}
      </div>
    </div>
  );
}

/* ===================== Role Helpers ===================== */

export function roleScope(role: RoleView): string {
  if (role.scope_type) {
    return dictionaryLabel(role.scope_type);
  }
  return dictionaryLabel('PLATFORM');
}

export function roleDescription(role: RoleView): string {
  return role.description || '';
}

export function roleDisplayLabel(role: string) {
  const normalized = role
    .replace(/([a-z0-9])([A-Z])/g, '$1_$2')
    .replace(/[-\s]+/g, '_')
    .toUpperCase();
  return dictionaryLabel(normalized, role);
}
