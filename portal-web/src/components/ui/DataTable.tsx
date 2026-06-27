import { ArrowDown, ArrowUp, ChevronsUpDown } from 'lucide-react';
import { useMemo, useState, type ReactNode } from 'react';
import { translate } from '../../platform/i18n';
import { EmptyState, SkeletonBlock } from './State';

export type DataTableSortDirection = 'asc' | 'desc';

export interface DataTableColumn<T> {
  align?: 'left' | 'center' | 'right';
  className?: string;
  header: ReactNode;
  id: string;
  render?: (row: T, rowIndex: number) => ReactNode;
  sortable?: boolean;
  sortValue?: (row: T) => number | string | null | undefined;
  width?: number | string;
}

export interface DataTableSortState {
  columnId: string;
  direction: DataTableSortDirection;
}

export interface DataTableProps<T> {
  ariaLabel?: string;
  columns: Array<DataTableColumn<T>>;
  emptyDescription?: string;
  emptyTitle?: string;
  getRowId?: (row: T, rowIndex: number) => string;
  loading?: boolean;
  minWidth?: number | string;
  onRowClick?: (row: T, rowIndex: number) => void;
  onSortChange?: (sort: DataTableSortState) => void;
  rows: readonly T[];
  selectedRowId?: string;
  sort?: DataTableSortState;
  stickyHeader?: boolean;
}

export function DataTable<T>({
  ariaLabel,
  columns,
  emptyDescription,
  emptyTitle = translate('auto.k0328'),
  getRowId,
  loading = false,
  minWidth = 720,
  onRowClick,
  onSortChange,
  rows,
  selectedRowId,
  sort,
  stickyHeader = false
}: DataTableProps<T>) {
  const [internalSort, setInternalSort] = useState<DataTableSortState | undefined>(sort);
  const sortState = sort ?? internalSort;
  const sortedRows = useMemo(() => sortRows(rows, columns, sortState), [columns, rows, sortState]);

  function toggleSort(column: DataTableColumn<T>) {
    if (!column.sortable) return;
    const nextDirection: DataTableSortDirection = sortState?.columnId === column.id && sortState.direction === 'asc' ? 'desc' : 'asc';
    const nextSort = { columnId: column.id, direction: nextDirection };
    setInternalSort(nextSort);
    onSortChange?.(nextSort);
  }

  return (
    <div className="table-wrap ui-data-table-wrap">
      <table aria-label={ariaLabel} className="ui-data-table" style={{ minWidth }}>
        <thead className={stickyHeader ? 'ui-data-table-sticky' : undefined}>
          <tr>
            {columns.map((column) => (
              <th
                className={column.align ? `text-${column.align}` : undefined}
                key={column.id}
                scope="col"
                style={column.width ? { width: column.width } : undefined}
              >
                {column.sortable ? (
                  <button
                    aria-label={translate('auto.k2607', { value0: headerText(column.header) })}
                    className="ui-data-table-sort"
                    type="button"
                    onClick={() => toggleSort(column)}
                  >
                    <span>{column.header}</span>
                    {sortState?.columnId === column.id ? (
                      sortState.direction === 'asc' ? <ArrowUp size={14} /> : <ArrowDown size={14} />
                    ) : (
                      <ChevronsUpDown size={14} />
                    )}
                  </button>
                ) : (
                  column.header
                )}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {loading ? (
            <tr>
              <td className="table-empty ui-data-table-state" colSpan={columns.length}>
                <SkeletonBlock rows={3} />
              </td>
            </tr>
          ) : sortedRows.length ? (
            sortedRows.map((row, rowIndex) => {
              const rowId = getRowId?.(row, rowIndex) ?? String(rowIndex);
              return (
                <tr
                  aria-selected={selectedRowId === rowId || undefined}
                  className={selectedRowId === rowId ? 'selected-row' : undefined}
                  key={rowId}
                  onClick={onRowClick ? () => onRowClick(row, rowIndex) : undefined}
                  tabIndex={onRowClick ? 0 : undefined}
                  onKeyDown={onRowClick ? (event) => {
                    if (event.key === 'Enter' || event.key === ' ') {
                      event.preventDefault();
                      onRowClick(row, rowIndex);
                    }
                  } : undefined}
                >
                  {columns.map((column) => (
                    <td className={cellClassName(column)} key={column.id}>
                      {column.render ? column.render(row, rowIndex) : defaultCellValue(row, column.id)}
                    </td>
                  ))}
                </tr>
              );
            })
          ) : (
            <tr>
              <td className="table-empty ui-data-table-state" colSpan={columns.length}>
                <EmptyState title={emptyTitle} description={emptyDescription} />
              </td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  );
}

function sortRows<T>(rows: readonly T[], columns: Array<DataTableColumn<T>>, sortState?: DataTableSortState) {
  if (!sortState) return [...rows];
  const column = columns.find((item) => item.id === sortState.columnId);
  if (!column?.sortValue) return [...rows];
  return [...rows].sort((left, right) => {
    const result = compareValues(column.sortValue?.(left), column.sortValue?.(right));
    return sortState.direction === 'asc' ? result : -result;
  });
}

function compareValues(left: number | string | null | undefined, right: number | string | null | undefined) {
  if (left == null && right == null) return 0;
  if (left == null) return -1;
  if (right == null) return 1;
  if (typeof left === 'number' && typeof right === 'number') return left - right;
  return String(left).localeCompare(String(right), undefined, { numeric: true, sensitivity: 'base' });
}

function defaultCellValue<T>(row: T, columnId: string) {
  if (row && typeof row === 'object' && columnId in row) {
    const value = (row as Record<string, unknown>)[columnId];
    return value == null || value === '' ? '-' : String(value);
  }
  return '-';
}

function cellClassName<T>(column: DataTableColumn<T>) {
  return [column.align ? `text-${column.align}` : '', column.className ?? ''].filter(Boolean).join(' ') || undefined;
}

function headerText(header: ReactNode) {
  return typeof header === 'string' || typeof header === 'number' ? String(header) : translate('auto.k0182');
}
