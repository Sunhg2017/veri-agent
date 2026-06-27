export type AssetVersionDiffRow = {
  path: string;
  before: unknown;
  after: unknown;
  tone: 'added' | 'removed' | 'changed';
};

type UnknownRecord = Record<string, unknown>;

const BEFORE_KEYS = ['before', 'beforeValue', 'old', 'from', 'previous'];
const AFTER_KEYS = ['after', 'afterValue', 'new', 'to', 'current'];

export function assetVersionDiffRows(diff: unknown, limit = 80): AssetVersionDiffRow[] {
  const rows: AssetVersionDiffRow[] = [];
  collectDiffRows(diff, '', rows, limit);
  return rows;
}

export function formatAssetVersionDiffValue(value: unknown): string {
  if (value === undefined || value === null || value === '') {
    return '-';
  }
  if (typeof value === 'string') {
    return value;
  }
  if (typeof value === 'number' || typeof value === 'boolean') {
    return String(value);
  }
  if (Array.isArray(value)) {
    const stepLines = compactStepLines(value);
    if (stepLines.length) {
      return stepLines.join('\n');
    }
  }
  try {
    return JSON.stringify(value, null, 2);
  } catch {
    return String(value);
  }
}

function collectDiffRows(value: unknown, path: string, rows: AssetVersionDiffRow[], limit: number) {
  if (rows.length >= limit) {
    return;
  }
  if (!isRecord(value)) {
    if (path) {
      rows.push({ path, before: undefined, after: value, tone: 'changed' });
    }
    return;
  }

  const beforeKey = BEFORE_KEYS.find((key) => Object.prototype.hasOwnProperty.call(value, key));
  const afterKey = AFTER_KEYS.find((key) => Object.prototype.hasOwnProperty.call(value, key));
  if (beforeKey || afterKey) {
    const before = beforeKey ? value[beforeKey] : undefined;
    const after = afterKey ? value[afterKey] : undefined;
    rows.push({
      path: path || 'root',
      before,
      after,
      tone: before === undefined ? 'added' : after === undefined ? 'removed' : 'changed'
    });
    return;
  }

  if (Object.prototype.hasOwnProperty.call(value, 'changed') && path) {
    rows.push({
      path,
      before: undefined,
      after: summarizeChangedRecord(value),
      tone: 'changed'
    });
    return;
  }

  for (const [key, child] of Object.entries(value)) {
    collectDiffRows(child, path ? `${path}.${key}` : key, rows, limit);
    if (rows.length >= limit) {
      break;
    }
  }
}

function compactStepLines(value: unknown[]) {
  if (!value.length || !value.every(isRecord)) {
    return [];
  }
  const allLookLikeSteps = value.every((item) => 'action' in item || 'expectedResult' in item || 'expected_result' in item);
  if (!allLookLikeSteps) {
    return [];
  }
  return value.map((item, index) => {
    const action = stringValue(item.action);
    const expectedResult = stringValue(item.expectedResult ?? item.expected_result);
    return `${index + 1}. ${action || '-'} => ${expectedResult || '-'}`;
  });
}

function summarizeChangedRecord(value: UnknownRecord) {
  return Object.entries(value)
    .map(([key, child]) => `${key}=${formatAssetVersionDiffValue(child)}`)
    .join(', ');
}

function isRecord(value: unknown): value is UnknownRecord {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function stringValue(value: unknown) {
  return typeof value === 'string' ? value.trim() : '';
}
