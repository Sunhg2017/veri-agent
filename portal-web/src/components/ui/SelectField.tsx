import { useId, type ChangeEvent } from 'react';
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
  onChange: (value: string, event: ChangeEvent<HTMLSelectElement>) => void;
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
  name,
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
      <select
        aria-describedby={describedBy}
        aria-invalid={Boolean(error) || undefined}
        disabled={disabled}
        id={selectId}
        name={name}
        required={required}
        value={value}
        onChange={(event) => onChange(event.target.value, event)}
      >
        <option value="">{placeholder}</option>
        {options.map((option) => (
          <option disabled={option.disabled} key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>
      {hint ? <small className="field-hint" id={hintId}>{hint}</small> : null}
      {error ? <small className="field-error" id={errorId}>{error}</small> : null}
    </div>
  );
}
