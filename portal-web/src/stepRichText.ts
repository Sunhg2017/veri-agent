export type StepRichTextStyle = 'bold' | 'italic' | 'code' | 'bullet';

export type StepRichTextSegment = {
  kind: 'text' | 'strong' | 'emphasis' | 'code';
  text: string;
};

export type StepRichTextBlock =
  | { kind: 'paragraph'; segments: StepRichTextSegment[] }
  | { kind: 'list'; items: StepRichTextSegment[][] };

export type StepRichTextEdit = {
  value: string;
  selectionStart: number;
  selectionEnd: number;
};

const PLACEHOLDERS: Record<StepRichTextStyle, string> = {
  bold: '重点',
  italic: '强调',
  code: '字段',
  bullet: '步骤说明'
};

export function applyStepRichTextMarkup(
  value: string,
  style: StepRichTextStyle,
  selectionStart = value.length,
  selectionEnd = selectionStart
): StepRichTextEdit {
  const start = clampSelection(selectionStart, value.length);
  const end = clampSelection(selectionEnd, value.length);
  const left = value.slice(0, Math.min(start, end));
  const selected = value.slice(Math.min(start, end), Math.max(start, end));
  const right = value.slice(Math.max(start, end));

  if (style === 'bullet') {
    const insertPrefix = selected || !left.trim() || left.endsWith('\n') ? '' : '\n';
    const replacement = selected
      ? selected
          .split('\n')
          .map((line) => (line.trim() ? `- ${line.replace(/^\s*[-*]\s+/, '')}` : '- '))
          .join('\n')
      : `${insertPrefix}- ${PLACEHOLDERS.bullet}`;
    const nextValue = `${left}${replacement}${right}`;
    const nextStart = left.length + replacement.length;
    return { value: nextValue, selectionStart: nextStart, selectionEnd: nextStart };
  }

  const marker = markerFor(style);
  const text = selected || PLACEHOLDERS[style];
  const replacement = `${marker}${text}${marker}`;
  const nextValue = `${left}${replacement}${right}`;
  const textStart = left.length + marker.length;
  return {
    value: nextValue,
    selectionStart: selected ? left.length : textStart,
    selectionEnd: selected ? left.length + replacement.length : textStart + text.length
  };
}

export function parseStepRichTextBlocks(value: string): StepRichTextBlock[] {
  const blocks: StepRichTextBlock[] = [];
  let listItems: StepRichTextSegment[][] = [];

  function flushList() {
    if (listItems.length) {
      blocks.push({ kind: 'list', items: listItems });
      listItems = [];
    }
  }

  for (const line of value.replace(/\r\n/g, '\n').split('\n')) {
    if (!line.trim()) {
      flushList();
      continue;
    }
    const listMatch = line.match(/^\s*[-*]\s+(.+)$/);
    if (listMatch) {
      listItems.push(parseStepRichTextSegments(listMatch[1]));
      continue;
    }
    flushList();
    blocks.push({ kind: 'paragraph', segments: parseStepRichTextSegments(line.trim()) });
  }

  flushList();
  return blocks;
}

export function parseStepRichTextSegments(value: string): StepRichTextSegment[] {
  const segments: StepRichTextSegment[] = [];
  let index = 0;

  while (index < value.length) {
    const next = nextInlineMarker(value, index);
    if (!next) {
      appendTextSegment(segments, value.slice(index));
      break;
    }
    if (next.start > index) {
      appendTextSegment(segments, value.slice(index, next.start));
    }
    const end = value.indexOf(next.marker, next.start + next.marker.length);
    if (end < 0) {
      appendTextSegment(segments, value.slice(next.start));
      break;
    }
    const text = value.slice(next.start + next.marker.length, end);
    if (text) {
      segments.push({ kind: next.kind, text });
    }
    index = end + next.marker.length;
  }

  return segments.length ? segments : [{ kind: 'text', text: value }];
}

export function moveItemByKey<T>(
  items: readonly T[],
  keySelector: (item: T) => string,
  sourceKey: string,
  targetKey: string
): T[] {
  if (!sourceKey || sourceKey === targetKey) {
    return [...items];
  }
  const fromIndex = items.findIndex((item) => keySelector(item) === sourceKey);
  const toIndex = items.findIndex((item) => keySelector(item) === targetKey);
  if (fromIndex < 0 || toIndex < 0) {
    return [...items];
  }
  const next = [...items];
  const [target] = next.splice(fromIndex, 1);
  next.splice(toIndex, 0, target);
  return next;
}

function markerFor(style: Exclude<StepRichTextStyle, 'bullet'>) {
  if (style === 'bold') {
    return '**';
  }
  if (style === 'italic') {
    return '_';
  }
  return '`';
}

function clampSelection(value: number, length: number) {
  if (!Number.isFinite(value)) {
    return length;
  }
  return Math.max(0, Math.min(length, Math.trunc(value)));
}

function nextInlineMarker(value: string, fromIndex: number) {
  type InlineMarkerCandidate = {
    kind: StepRichTextSegment['kind'];
    marker: string;
    start: number;
  };
  const candidates: InlineMarkerCandidate[] = [
    { kind: 'code', marker: '`', start: value.indexOf('`', fromIndex) },
    { kind: 'strong', marker: '**', start: value.indexOf('**', fromIndex) },
    { kind: 'emphasis', marker: '_', start: value.indexOf('_', fromIndex) }
  ];

  return candidates
    .filter((candidate) => candidate.start >= 0)
    .sort((left, right) => left.start - right.start || right.marker.length - left.marker.length)[0];
}

function appendTextSegment(segments: StepRichTextSegment[], text: string) {
  if (!text) {
    return;
  }
  const previous = segments[segments.length - 1];
  if (previous?.kind === 'text') {
    previous.text += text;
    return;
  }
  segments.push({ kind: 'text', text });
}
