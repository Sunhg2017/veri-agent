import { Input } from 'antd';
import type { ComponentProps } from 'react';

export type TextAreaControlProps = ComponentProps<typeof Input.TextArea>;

export function TextAreaControl({ className, autoSize, ...props }: TextAreaControlProps) {
  return (
    <Input.TextArea
      {...props}
      autoSize={autoSize ?? { minRows: 3, maxRows: 12 }}
      className={['ui-textarea-control', className].filter(Boolean).join(' ')}
    />
  );
}
