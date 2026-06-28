import type { Meta, StoryObj } from '@storybook/react-vite';
import { useState } from 'react';
import { dictionaryLabel } from '../../platform/dictionaries';
import { DataTable, type DataTableColumn, type DataTableSortState } from './DataTable';

type RequirementRow = {
  id: string;
  owner: string;
  priority: 'HIGH' | 'MEDIUM' | 'LOW';
  status: 'APPROVED' | 'DRAFT' | 'REVIEWING';
  title: string;
  updatedAt: string;
};

const columns: Array<DataTableColumn<RequirementRow>> = [
  {
    header: '需求',
    id: 'title',
    render: (row) => (
      <span className="table-primary">
        {row.title}
        <span className="table-secondary">{row.id}</span>
      </span>
    ),
    sortable: true,
    sortValue: (row) => row.title,
    width: '34%'
  },
  {
    header: '负责人',
    id: 'owner',
    sortable: true,
    sortValue: (row) => row.owner,
    width: 160
  },
  {
    header: '优先级',
    id: 'priority',
    render: (row) => <span className={`badge ${row.priority === 'HIGH' ? 'badge-danger' : row.priority === 'MEDIUM' ? 'badge-warning' : 'badge-info'}`}>{dictionaryLabel(row.priority)}</span>,
    sortable: true,
    sortValue: (row) => row.priority,
    width: 130
  },
  {
    header: '状态',
    id: 'status',
    render: (row) => <span className={`status-badge ${row.status === 'APPROVED' ? 'success' : row.status === 'REVIEWING' ? 'warning' : 'neutral'}`}>{dictionaryLabel(row.status)}</span>,
    width: 140
  },
  {
    align: 'right',
    header: '更新时间',
    id: 'updatedAt',
    sortable: true,
    sortValue: (row) => row.updatedAt,
    width: 150
  }
];

const rows: RequirementRow[] = [
  { id: 'REQ-101', owner: '陈嘉', priority: 'HIGH', status: 'REVIEWING', title: '结算审批流程', updatedAt: '2026-06-22' },
  { id: 'REQ-104', owner: '王磊', priority: 'MEDIUM', status: 'DRAFT', title: '发票对账导入', updatedAt: '2026-06-19' },
  { id: 'REQ-117', owner: '李敏', priority: 'LOW', status: 'APPROVED', title: '发布准出看板', updatedAt: '2026-06-24' }
];

const meta = {
  title: 'Components/DataTable',
  component: DataTable<RequirementRow>,
  parameters: {
    docs: {
      description: {
        component: '面向企业工作台的高密表格组件，支持排序、行选择、加载和空状态。'
      }
    }
  },
  tags: ['autodocs']
} satisfies Meta<typeof DataTable<RequirementRow>>;

export default meta;

type Story = StoryObj<typeof meta>;

export const Default: Story = {
  args: {
    columns,
    rows
  },
  render: () => <DataTable ariaLabel="需求评审表格" columns={columns} getRowId={(row) => row.id} rows={rows} />
};

export const SortableAndSelectable: Story = {
  args: {
    columns,
    rows
  },
  render: function SortableAndSelectableStory() {
    const [sort, setSort] = useState<DataTableSortState>({ columnId: 'updatedAt', direction: 'desc' });
    const [selectedRowId, setSelectedRowId] = useState('REQ-101');

    return (
      <DataTable
        ariaLabel="可选择需求评审表格"
        columns={columns}
        getRowId={(row) => row.id}
        onRowClick={(row) => setSelectedRowId(row.id)}
        onSortChange={setSort}
        rows={rows}
        selectedRowId={selectedRowId}
        sort={sort}
        stickyHeader
      />
    );
  }
};

export const Loading: Story = {
  args: {
    columns,
    rows: []
  },
  render: () => <DataTable ariaLabel="加载需求" columns={columns} loading rows={[]} />
};

export const Empty: Story = {
  args: {
    columns,
    rows: []
  },
  render: () => (
    <DataTable
      ariaLabel="空需求表格"
      columns={columns}
      emptyDescription="创建或导入需求后即可开始构建可追溯测试资产。"
      emptyTitle="暂无需求"
      rows={[]}
    />
  )
};
