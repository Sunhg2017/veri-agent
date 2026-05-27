import type { TestDesignCandidateBatchActionType, TestDesignCandidateView } from './api/testDesign';

export type TestDesignConfirmationTone = 'info' | 'warning';

export interface TestDesignConfirmationSummary {
  title: string;
  confirmLabel: string;
  tone: TestDesignConfirmationTone;
  details: Array<{ label: string; value: string | number }>;
  warnings: string[];
  candidateTitles: string[];
}

type ConfirmationCandidate = Pick<TestDesignCandidateView, 'id' | 'title' | 'status' | 'version'>;

export function testDesignBatchActionLabel(action: string) {
  if (action === 'CONFIRM') {
    return '确认';
  }
  if (action === 'REJECT') {
    return '驳回';
  }
  if (action === 'IGNORE') {
    return '忽略';
  }
  return action;
}

export function buildTestDesignBatchReviewConfirmation(
  action: TestDesignCandidateBatchActionType,
  candidates: readonly ConfirmationCandidate[],
  reviewComment: string
): TestDesignConfirmationSummary {
  const actionLabel = testDesignBatchActionLabel(action);
  const nonReviewableCount = candidates.filter((candidate) => !['GENERATED', 'EDITED'].includes(candidate.status)).length;
  const warnings = batchReviewWarnings(action, nonReviewableCount);
  return {
    title: `批量${actionLabel}候选`,
    confirmLabel: `确认批量${actionLabel}`,
    tone: action === 'CONFIRM' ? 'info' : 'warning',
    details: [
      { label: '操作', value: actionLabel },
      { label: '候选数', value: candidates.length },
      { label: '评审意见', value: reviewComment.trim() || '无' },
      { label: '版本', value: candidates.map((candidate) => `${candidate.id}@v${candidate.version}`).slice(0, 3).join(', ') || '-' }
    ],
    warnings,
    candidateTitles: candidateTitles(candidates)
  };
}

export function buildTestDesignBatchEditConfirmation(
  candidates: readonly ConfirmationCandidate[],
  changedFields: readonly string[]
): TestDesignConfirmationSummary {
  return {
    title: '确认批量编辑候选',
    confirmLabel: '确认批量编辑',
    tone: 'warning',
    details: [
      { label: '操作', value: '批量字段编辑' },
      { label: '候选数', value: candidates.length },
      { label: '变更字段', value: changedFields.join('；') || '-' },
      { label: '版本', value: candidates.map((candidate) => `${candidate.id}@v${candidate.version}`).slice(0, 3).join(', ') || '-' }
    ],
    warnings: batchEditWarnings(candidates.length, changedFields.length),
    candidateTitles: candidateTitles(candidates)
  };
}

export function buildTestDesignPublishConfirmation(
  dryRun: boolean,
  candidates: readonly ConfirmationCandidate[],
  totalPublishableCandidates: number,
  selectedCandidateCount: number
): TestDesignConfirmationSummary {
  const failedCount = candidates.filter((candidate) => candidate.status === 'FAILED').length;
  const selectedScope = selectedCandidateCount > 0
    ? `${candidates.length} / ${selectedCandidateCount} 个已选候选可发布`
    : '全部可发布候选';
  return {
    title: dryRun ? '确认预发布检查' : '确认发布到资产库',
    confirmLabel: dryRun ? '开始预发布' : '确认发布',
    tone: dryRun && failedCount === 0 ? 'info' : 'warning',
    details: [
      { label: '操作', value: dryRun ? '预发布 dryRun' : '正式发布' },
      { label: '发布范围', value: selectedScope },
      { label: '可发布候选', value: totalPublishableCandidates },
      { label: '待重试候选', value: failedCount }
    ],
    warnings: publishWarnings(dryRun, candidates.length, failedCount),
    candidateTitles: candidateTitles(candidates)
  };
}

function batchReviewWarnings(action: TestDesignCandidateBatchActionType, nonReviewableCount: number) {
  const warnings: string[] = [];
  if (nonReviewableCount > 0) {
    warnings.push(`包含 ${nonReviewableCount} 个当前不可评审候选，提交前请刷新选择。`);
  }
  if (action === 'CONFIRM') {
    warnings.push('确认后候选会进入发布池，请确认标题、步骤和预期结果已完成评审。');
  }
  if (action === 'REJECT') {
    warnings.push('驳回后候选不会进入发布池，后续需要重新编辑或重新生成。');
  }
  if (action === 'IGNORE') {
    warnings.push('忽略后候选不会进入发布池，但仍会保留在任务记录中。');
  }
  return warnings;
}

function batchEditWarnings(candidateCount: number, changedFieldCount: number) {
  const warnings: string[] = [];
  if (!candidateCount) {
    warnings.push('当前没有可批量编辑候选。');
  } else {
    warnings.push('批量编辑会逐条保存候选，并将成功项置为 EDITED。');
  }
  if (!changedFieldCount) {
    warnings.push('尚未选择需要变更的字段。');
  }
  return warnings;
}

function publishWarnings(dryRun: boolean, candidateCount: number, failedCount: number) {
  const warnings: string[] = [];
  if (!candidateCount) {
    warnings.push('当前没有可发布候选。');
  } else if (dryRun) {
    warnings.push('预发布只做 dryRun 检查，不会写入 WP3 资产库。');
  } else {
    warnings.push('正式发布会写入 WP3 测试用例并创建需求追踪关系。');
  }
  if (failedCount > 0) {
    warnings.push(`包含 ${failedCount} 个 FAILED 候选，将作为失败重试范围重新发布。`);
  }
  return warnings;
}

function candidateTitles(candidates: readonly ConfirmationCandidate[]) {
  return candidates.map((candidate) => candidate.title || candidate.id).slice(0, 5);
}
