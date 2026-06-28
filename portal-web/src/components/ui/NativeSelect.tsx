import { Select } from 'antd';
import { Children, Fragment, isValidElement, type ChangeEvent, type ReactElement, type ReactNode } from 'react';

type NativeSelectOption = {
  disabled?: boolean;
  label: ReactNode;
  searchLabel: string;
  value: string;
};

export type NativeSelectProps = {
  'aria-describedby'?: string;
  'aria-invalid'?: boolean | 'false' | 'true';
  'aria-label'?: string;
  className?: string;
  children?: ReactNode;
  disabled?: boolean;
  defaultValue?: number | string;
  id?: string;
  name?: string;
  onChange?: (event: ChangeEvent<HTMLSelectElement>) => void;
  placeholder?: string;
  required?: boolean;
  title?: string;
  value?: number | string;
};

export function NativeSelect({
  children,
  className,
  defaultValue,
  disabled,
  id,
  name,
  onChange,
  placeholder,
  required,
  title,
  value,
  'aria-describedby': ariaDescribedBy,
  'aria-invalid': ariaInvalid,
  'aria-label': ariaLabel
}: NativeSelectProps) {
  const options = optionsFromChildren(children);
  const selectValue = value === undefined ? normalizeValue(defaultValue) : normalizeValue(value);

  return (
    <Select
      aria-describedby={ariaDescribedBy}
      aria-invalid={ariaInvalid}
      aria-label={ariaLabel}
      aria-required={required || undefined}
      className={['ui-native-select', className].filter(Boolean).join(' ')}
      defaultValue={value === undefined ? selectValue : undefined}
      disabled={disabled}
      filterOption={(input, option) => {
        const searchLabel = String(option?.searchLabel ?? option?.label ?? '').toLowerCase();
        return searchLabel.includes(input.toLowerCase());
      }}
      getPopupContainer={selectPopupContainer}
      id={id}
      optionFilterProp="searchLabel"
      options={options}
      placeholder={placeholder}
      classNames={{ popup: { root: 'ui-native-select-dropdown' } }}
      popupMatchSelectWidth
      showSearch
      title={title}
      value={value === undefined ? undefined : selectValue}
      onChange={(nextValue) => {
        onChange?.(createSelectChangeEvent(String(nextValue ?? ''), id, name));
      }}
    />
  );
}

function normalizeValue(value: number | string | readonly string[] | undefined) {
  if (Array.isArray(value)) {
    return value[0] === undefined ? '' : String(value[0]);
  }
  return value === undefined ? undefined : String(value);
}

function optionsFromChildren(children: ReactNode) {
  const options: NativeSelectOption[] = [];

  function visit(nodes: ReactNode) {
    Children.forEach(nodes, (child) => {
      if (!isValidElement(child)) {
        return;
      }

      if (child.type === Fragment) {
        visit((child.props as { children?: ReactNode }).children);
        return;
      }

      if (child.type !== 'option') {
        return;
      }

      const option = child as ReactElement<{
        children?: ReactNode;
        disabled?: boolean;
        value?: number | string;
      }>;
      const label = option.props.children ?? '';
      const value = option.props.value === undefined ? textFromNode(label) : String(option.props.value);
      options.push({
        disabled: option.props.disabled,
        label,
        searchLabel: textFromNode(label),
        value
      });
    });
  }

  visit(children);
  return options;
}

function textFromNode(node: ReactNode): string {
  if (node === null || node === undefined || typeof node === 'boolean') {
    return '';
  }
  if (typeof node === 'string' || typeof node === 'number') {
    return String(node);
  }
  if (Array.isArray(node)) {
    return node.map(textFromNode).join('');
  }
  if (isValidElement(node)) {
    return textFromNode((node.props as { children?: ReactNode }).children);
  }
  return '';
}

function createSelectChangeEvent(value: string, id?: string, name?: string): ChangeEvent<HTMLSelectElement> {
  const target = { id, name, value };
  return {
    currentTarget: target,
    target
  } as ChangeEvent<HTMLSelectElement>;
}

function selectPopupContainer(triggerNode: HTMLElement) {
  return (triggerNode.closest('.ant-drawer-content') as HTMLElement | null) ?? document.body;
}
