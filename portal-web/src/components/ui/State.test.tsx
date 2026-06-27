import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { EmptyState, SkeletonBlock, Spinner } from './State';

describe('shared state components', () => {
  it('renders an accessible loading status', () => {
    render(<Spinner label="正在加载数据" />);
    expect(screen.getByRole('status')).toHaveTextContent('正在加载数据');
  });

  it('renders empty copy and optional action', () => {
    render(
      <EmptyState
        title="暂无数据"
        description="创建后将在这里展示。"
        action={<button type="button">创建</button>}
      />
    );
    expect(screen.getByText('暂无数据')).toBeInTheDocument();
    expect(screen.getByText('创建后将在这里展示。')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '创建' })).toBeInTheDocument();
  });

  it('renders requested skeleton rows without exposing noisy text', () => {
    const { container } = render(<SkeletonBlock rows={4} />);
    expect(container.querySelectorAll('.ui-skeleton-line')).toHaveLength(4);
  });
});
