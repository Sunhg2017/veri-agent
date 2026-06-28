import { Checkbox } from 'antd';
import type { ChangeEvent, ReactNode } from 'react';

export type CheckboxControlProps = {
  'aria-describedby'?: string;
  'aria-label'?: string;
  checked?: boolean;
  children?: ReactNode;
  className?: string;
  disabled?: boolean;
  id?: string;
  name?: string;
  onChange?: (event: ChangeEvent<HTMLInputElement>) => void;
};

export function CheckboxControl({
  'aria-describedby': ariaDescribedBy,
  'aria-label': ariaLabel,
  checked,
  children,
  className,
  disabled,
  id,
  name,
  onChange
}: CheckboxControlProps) {
  return (
    <Checkbox
      aria-describedby={ariaDescribedBy}
      aria-label={ariaLabel}
      checked={checked}
      className={['ui-checkbox-control', className].filter(Boolean).join(' ')}
      disabled={disabled}
      id={id}
      name={name}
      onChange={(event) => {
        onChange?.(event as unknown as ChangeEvent<HTMLInputElement>);
      }}
    >
      {children}
    </Checkbox>
  );
}
