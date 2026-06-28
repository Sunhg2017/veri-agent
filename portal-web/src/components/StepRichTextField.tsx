import { Bold, Code2, Italic, List } from 'lucide-react';
import {
  parseStepRichTextBlocks,
  type StepRichTextBlock,
  type StepRichTextSegment,
  type StepRichTextStyle
} from '../stepRichText';
import { translate } from '../platform/i18n';
import { TextAreaControl } from './ui';

export function StepRichTextField(props: {
  disabled: boolean;
  id: string;
  label: string;
  onChange: (value: string) => void;
  onFormat: (style: StepRichTextStyle) => void;
  placeholder?: string;
  value: string;
}) {
  const blocks = parseStepRichTextBlocks(props.value);
  return (
    <div className="field step-rich-text-field">
      <div className="step-rich-text-label-row">
        <label htmlFor={props.id}>{props.label}</label>
        <div className="step-rich-text-toolbar" aria-label={translate('auto.k1195', { value0: props.label })}>
          <button className="mini-button icon-only" type="button" title={translate('auto.k1196')} disabled={props.disabled} onClick={() => props.onFormat('bold')}>
            <Bold size={13} />
          </button>
          <button className="mini-button icon-only" type="button" title={translate('auto.k1197')} disabled={props.disabled} onClick={() => props.onFormat('italic')}>
            <Italic size={13} />
          </button>
          <button className="mini-button icon-only" type="button" title={translate('auto.k1198')} disabled={props.disabled} onClick={() => props.onFormat('code')}>
            <Code2 size={13} />
          </button>
          <button className="mini-button icon-only" type="button" title={translate('auto.k1199')} disabled={props.disabled} onClick={() => props.onFormat('bullet')}>
            <List size={13} />
          </button>
        </div>
      </div>
      <TextAreaControl
        id={props.id}
        className="compact-textarea"
        value={props.value}
        disabled={props.disabled}
        onChange={(event) => props.onChange(event.target.value)}
        placeholder={props.placeholder}
      />
      <div className="step-rich-text-preview" aria-label={translate('auto.k1200', { value0: props.label })}>
        {blocks.length ? blocks.map((block, index) => <StepRichTextBlockView block={block} key={`${props.id}-preview-${index}`} />) : <em>{translate('auto.k1201')}</em>}
      </div>
    </div>
  );
}

function StepRichTextBlockView(props: { block: StepRichTextBlock }) {
  if (props.block.kind === 'list') {
    return (
      <ul>
        {props.block.items.map((item, index) => (
          <li key={index}>{renderRichTextSegments(item)}</li>
        ))}
      </ul>
    );
  }
  return <p>{renderRichTextSegments(props.block.segments)}</p>;
}

function renderRichTextSegments(segments: StepRichTextSegment[]) {
  return segments.map((segment, index) => {
    const key = `${segment.kind}-${index}-${segment.text}`;
    if (segment.kind === 'strong') {
      return <strong key={key}>{segment.text}</strong>;
    }
    if (segment.kind === 'emphasis') {
      return <em key={key}>{segment.text}</em>;
    }
    if (segment.kind === 'code') {
      return <code key={key}>{segment.text}</code>;
    }
    return <span key={key}>{segment.text}</span>;
  });
}
