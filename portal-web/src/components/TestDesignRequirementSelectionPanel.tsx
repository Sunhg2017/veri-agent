import { RefreshCw, Search } from 'lucide-react';
import type { Dispatch, SetStateAction } from 'react';
import type { AssetRequirementView } from '../api/assets';
import type { RequirementFilters } from '../testDesignWorkbenchState';
import { StateLine, type WorkState } from './TestDesignOverviewPanels';
import { emptyRequirementText } from './TestDesignWorkbenchShared';

export function TestDesignRequirementSelectionPanel(props: {
  signedIn: boolean;
  canRead: boolean;
  disabled: boolean;
  loadState: WorkState;
  filters: RequirementFilters;
  initialFilters: RequirementFilters;
  filteredRequirements: AssetRequirementView[];
  selectedRequirementIds: string[];
  onRefresh: () => void;
  onFiltersChange: Dispatch<SetStateAction<RequirementFilters>>;
  onSelectedRequirementIdsChange: Dispatch<SetStateAction<string[]>>;
  onToggleRequirement: (requirementId: string) => void;
}) {
  return (
    <section className="panel">
      <div className="panel-header">
        <div>
          <h2 className="panel-title">需求选择</h2>
          <p className="panel-desc">从 WP3 已入库需求中选择生成范围。</p>
        </div>
        <div className="toolbar-actions">
          <button className="btn btn-secondary btn-sm" type="button" disabled={props.disabled || props.loadState.loading} onClick={props.onRefresh}>
            <RefreshCw size={15} />
            刷新
          </button>
          <button
            className="btn btn-ghost btn-sm"
            type="button"
            disabled={props.disabled || props.loadState.loading}
            onClick={() => props.onSelectedRequirementIdsChange(props.filteredRequirements.map((item) => item.id).filter(Boolean))}
          >
            全选
          </button>
        </div>
      </div>
      <div className="panel-body">
        <div className="asset-filter-bar">
          <label className="field">
            <span className="field-label">项目 ID</span>
            <input
              value={props.filters.projectId}
              onChange={(event) => props.onFiltersChange((current) => ({ ...current, projectId: event.target.value }))}
              placeholder="project UUID"
              disabled={props.disabled}
            />
          </label>
          <label className="field">
            <span className="field-label">状态</span>
            <select
              value={props.filters.status}
              onChange={(event) => props.onFiltersChange((current) => ({ ...current, status: event.target.value }))}
              disabled={props.disabled}
            >
              <option value="">全部</option>
              <option value="APPROVED">APPROVED</option>
              <option value="REVIEWING">REVIEWING</option>
              <option value="DRAFT">DRAFT</option>
            </select>
          </label>
          <label className="field">
            <span className="field-label">关键词</span>
            <input
              value={props.filters.keyword}
              onChange={(event) => props.onFiltersChange((current) => ({ ...current, keyword: event.target.value }))}
              placeholder="标题 / 标签"
              disabled={props.disabled}
            />
          </label>
          <div className="filter-actions">
            <button className="btn btn-secondary btn-sm" type="button" disabled={props.disabled} onClick={() => props.onFiltersChange(props.initialFilters)}>
              <Search size={15} />
              重置
            </button>
          </div>
        </div>

        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th className="table-check-column"></th>
                <th>需求</th>
                <th>优先级</th>
                <th>来源</th>
                <th>标签</th>
              </tr>
            </thead>
            <tbody>
              {props.filteredRequirements.length ? (
                props.filteredRequirements.map((requirement) => (
                  <tr className={props.selectedRequirementIds.includes(requirement.id) ? 'selected-row' : ''} key={requirement.id}>
                    <td>
                      <input
                        aria-label={`选择需求 ${requirement.title}`}
                        type="checkbox"
                        checked={props.selectedRequirementIds.includes(requirement.id)}
                        onChange={() => props.onToggleRequirement(requirement.id)}
                        disabled={props.disabled || !requirement.id}
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
                  <td className="table-empty" colSpan={5}>{emptyRequirementText(props.signedIn, props.canRead, props.loadState.loading)}</td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
        <StateLine state={props.loadState} />
      </div>
    </section>
  );
}
