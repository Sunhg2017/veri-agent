import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { SelectField } from './SelectField';

describe('SelectField', () => {
  it('renders label, hint, options and emits selected values', () => {
    const onChange = vi.fn();
    render(
      <SelectField
        hint="Pick one"
        label="Environment"
        options={[
          { label: 'Staging', value: 'staging' },
          { label: 'Production', value: 'prod' }
        ]}
        value=""
        onChange={onChange}
      />
    );

    const select = screen.getByLabelText('Environment');
    expect(screen.getByText('Pick one')).toBeInTheDocument();
    fireEvent.change(select, { target: { value: 'prod' } });
    expect(onChange).toHaveBeenCalledWith('prod', expect.any(Object));
  });

  it('marks invalid fields with accessible error text', () => {
    render(<SelectField error="Required" label="Role" options={[]} required value="" onChange={vi.fn()} />);

    const select = screen.getByLabelText('Role*');
    expect(select).toBeInvalid();
    expect(select).toHaveAccessibleDescription('Required');
  });
});
