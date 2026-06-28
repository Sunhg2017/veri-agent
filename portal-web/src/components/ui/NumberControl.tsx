import { InputNumber } from 'antd';
import type { ChangeEvent } from 'react';

type NumberValue = number | string;

export type NumberControlProps = {
  'aria-label'?: string;
  className?: string;
  disabled?: boolean;
  id?: string;
  max?: NumberValue;
  min?: NumberValue;
  name?: string;
  onChange?: (event: ChangeEvent<HTMLInputElement>) => void;
  placeholder?: string;
  step?: NumberValue;
  value?: NumberValue;
};

export function NumberControl({
  className,
  disabled,
  id,
  max,
  min,
  name,
  onChange,
  placeholder,
  step,
  value,
  'aria-label': ariaLabel
}: NumberControlProps) {
  return (
    <InputNumber
      aria-label={ariaLabel}
      className={['ui-number-control', className].filter(Boolean).join(' ')}
      disabled={disabled}
      id={id}
      max={numberBoundary(max)}
      min={numberBoundary(min)}
      name={name}
      placeholder={placeholder}
      step={step}
      value={numberValue(value)}
      onChange={(nextValue) => {
        onChange?.(createNumberChangeEvent(nextValue, id, name));
      }}
    />
  );
}

function numberBoundary(value: NumberValue | undefined) {
  if (value === undefined || value === '') {
    return undefined;
  }
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : undefined;
}

function numberValue(value: NumberValue | undefined) {
  if (value === undefined || value === '') {
    return null;
  }
  return value;
}

function createNumberChangeEvent(value: number | string | null, id?: string, name?: string): ChangeEvent<HTMLInputElement> {
  const target = { id, name, value: value === null ? '' : String(value) };
  return {
    currentTarget: target,
    target
  } as ChangeEvent<HTMLInputElement>;
}
