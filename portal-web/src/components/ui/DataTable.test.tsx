import { fireEvent, render, screen, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { DataTable, type DataTableColumn } from './DataTable';

type Row = { id: string; name: string; score: number };

const columns: Array<DataTableColumn<Row>> = [
  { id: 'name', header: 'Name', sortable: true, sortValue: (row) => row.name },
  { id: 'score', header: 'Score', align: 'right', render: (row) => row.score, sortable: true, sortValue: (row) => row.score }
];

const rows: Row[] = [
  { id: 'a', name: 'Delta', score: 4 },
  { id: 'b', name: 'Alpha', score: 9 }
];

describe('DataTable', () => {
  it('renders rows and sorts by a sortable column', () => {
    render(<DataTable ariaLabel="Scores" columns={columns} getRowId={(row) => row.id} rows={rows} />);

    expect(screen.getAllByRole('row')).toHaveLength(3);
    fireEvent.click(screen.getByRole('button', { name: '按Name排序' }));

    const bodyRows = screen.getAllByRole('row').slice(1);
    expect(within(bodyRows[0]).getByText('Alpha')).toBeInTheDocument();
    expect(within(bodyRows[1]).getByText('Delta')).toBeInTheDocument();
  });

  it('renders loading and empty states', () => {
    const { rerender } = render(<DataTable ariaLabel="Scores" columns={columns} loading rows={[]} />);
    expect(document.querySelectorAll('.ui-skeleton-line')).toHaveLength(3);

    rerender(<DataTable ariaLabel="Scores" columns={columns} emptyTitle="No scores" rows={[]} />);
    expect(screen.getByText('No scores')).toBeInTheDocument();
  });

  it('supports selected rows and keyboard row activation', () => {
    const onRowClick = vi.fn();
    render(
      <DataTable
        ariaLabel="Scores"
        columns={columns}
        getRowId={(row) => row.id}
        rows={rows}
        selectedRowId="b"
        onRowClick={onRowClick}
      />
    );

    const alphaRow = screen.getByText('Alpha').closest('tr');
    expect(alphaRow).toHaveClass('selected-row');
    fireEvent.keyDown(alphaRow!, { key: 'Enter' });
    expect(onRowClick).toHaveBeenCalledWith(rows[1], 1);
  });
});
