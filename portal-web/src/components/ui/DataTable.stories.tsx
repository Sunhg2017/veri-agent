import type { Meta, StoryObj } from '@storybook/react-vite';
import { useState } from 'react';
import { DataTable, type DataTableColumn, type DataTableSortState } from './DataTable';

type RequirementRow = {
  id: string;
  owner: string;
  priority: 'High' | 'Medium' | 'Low';
  status: 'Approved' | 'Draft' | 'Reviewing';
  title: string;
  updatedAt: string;
};

const columns: Array<DataTableColumn<RequirementRow>> = [
  {
    header: 'Requirement',
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
    header: 'Owner',
    id: 'owner',
    sortable: true,
    sortValue: (row) => row.owner,
    width: 160
  },
  {
    header: 'Priority',
    id: 'priority',
    render: (row) => <span className={`badge ${row.priority === 'High' ? 'badge-danger' : row.priority === 'Medium' ? 'badge-warning' : 'badge-info'}`}>{row.priority}</span>,
    sortable: true,
    sortValue: (row) => row.priority,
    width: 130
  },
  {
    header: 'Status',
    id: 'status',
    render: (row) => <span className={`status-badge ${row.status === 'Approved' ? 'success' : row.status === 'Reviewing' ? 'warning' : 'neutral'}`}>{row.status}</span>,
    width: 140
  },
  {
    align: 'right',
    header: 'Updated',
    id: 'updatedAt',
    sortable: true,
    sortValue: (row) => row.updatedAt,
    width: 150
  }
];

const rows: RequirementRow[] = [
  { id: 'REQ-101', owner: 'Jia Chen', priority: 'High', status: 'Reviewing', title: 'Checkout approval workflow', updatedAt: '2026-06-22' },
  { id: 'REQ-104', owner: 'Alex Morgan', priority: 'Medium', status: 'Draft', title: 'Invoice reconciliation import', updatedAt: '2026-06-19' },
  { id: 'REQ-117', owner: 'Priya Shah', priority: 'Low', status: 'Approved', title: 'Release readiness dashboard', updatedAt: '2026-06-24' }
];

const meta = {
  title: 'Components/DataTable',
  component: DataTable<RequirementRow>,
  parameters: {
    docs: {
      description: {
        component: 'Dense enterprise table primitive with sorting, row selection, loading and empty states.'
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
  render: () => <DataTable ariaLabel="Requirement review table" columns={columns} getRowId={(row) => row.id} rows={rows} />
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
        ariaLabel="Selectable requirement review table"
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
  render: () => <DataTable ariaLabel="Loading requirements" columns={columns} loading rows={[]} />
};

export const Empty: Story = {
  args: {
    columns,
    rows: []
  },
  render: () => (
    <DataTable
      ariaLabel="Empty requirements"
      columns={columns}
      emptyDescription="Create or import requirements to start building traceable test assets."
      emptyTitle="No requirements yet"
      rows={[]}
    />
  )
};
