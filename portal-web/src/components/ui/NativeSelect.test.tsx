import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { NativeSelect } from './NativeSelect';

describe('NativeSelect', () => {
  it('renders Ant Design select popup and preserves native-style change events', async () => {
    const onChange = vi.fn();

    render(
      <NativeSelect aria-label="Status" value="" onChange={onChange}>
        <option value="">全部状态</option>
        <option value="ENABLED">启用</option>
        <option value="DISABLED">停用</option>
      </NativeSelect>
    );

    expect(document.querySelector('select')).not.toBeInTheDocument();
    const select = screen.getByLabelText('Status');
    fireEvent.mouseDown(select);
    fireEvent.click(await screen.findByText('启用'));

    expect(onChange).toHaveBeenCalledTimes(1);
    expect(onChange.mock.calls[0][0].target.value).toBe('ENABLED');
  });
});
