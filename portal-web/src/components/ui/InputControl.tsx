import { Input } from 'antd';
import type { ComponentProps } from 'react';

export type InputControlProps = ComponentProps<typeof Input>;

export function InputControl({ className, ...props }: InputControlProps) {
  return (
    <Input
      {...props}
      className={['ui-input-control', className].filter(Boolean).join(' ')}
    />
  );
}
