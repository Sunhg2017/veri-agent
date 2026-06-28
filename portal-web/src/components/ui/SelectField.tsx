import { Select } from 'antd';
import { useId } from 'react';
import { translate } from '../../platform/i18n';

export interface SelectOption {
  disabled?: boolean;
  label: string;
  value: string;
}

export interface SelectFieldProps {
  disabled?: boolean;
  error?: string;
  hint?: string;
  id?: string;
  label?: string;
  name?: string;
  onChange: (value: string) => void;
  options: readonly SelectOption[];
  placeholder?: string;
  required?: boolean;
  value: string;
}

export function SelectField({
  disabled = false,
  error,
  hint,
  id,
  label,
  onChange,
  options,
  placeholder = translate('auto.k0334'),
  required = false,
  value
}: SelectFieldProps) {
  const generatedId = useId();
  const selectId = id ?? generatedId;
  const hintId = hint ? `${selectId}-hint` : undefined;
  const errorId = error ? `${selectId}-error` : undefined;
  const describedBy = [hintId, errorId].filter(Boolean).join(' ') || undefined;

  return (
    <div className={`field ui-select-field${error ? ' has-error' : ''}`}>
      {label ? (
        <label className="field-label" htmlFor={selectId}>
          {label}
          {required ? <b className="required">*</b> : null}
        </label>
      ) : null}
      <Select
        aria-describedby={describedBy}
        aria-invalid={Boolean(error) || undefined}
        aria-required={required || undefined}
        className="ui-select-control"
        disabled={disabled}
        classNames={{ popup: { root: 'ui-select-dropdown' } }}
        popupMatchSelectWidth
        id={selectId}
        options={[
          { label: placeholder, value: '' },
          ...options
        ]}
        showSearch
        optionFilterProp="label"
        value={value}
        onChange={(nextValue) => onChange(String(nextValue ?? ''))}
      />
      {hint ? <small className="field-hint" id={hintId}>{hint}</small> : null}
      {error ? <small className="field-error" id={errorId}>{error}</small> : null}
    </div>
  );
}
