import type { TestDesignCandidateBatchActionType, TestDesignCandidateView, TestDesignPublishRecordView } from './api/testDesign';
import { translate } from './platform/i18n';

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
type ConflictCandidate = Pick<TestDesignCandidateView, 'id' | 'title' | 'status' | 'version'>;
type ConflictResolutionConfirmationItem = {
  candidate: ConflictCandidate;
  record: Pick<TestDesignPublishRecordView, 'assetCaseId' | 'errorMessage'>;
};

export function testDesignBatchActionLabel(action: string) {
  if (action === 'CONFIRM') {
    return translate('auto.k0807');
  }
  if (action === 'REJECT') {
    return translate('auto.k0214');
  }
  if (action === 'IGNORE') {
    return translate('auto.k0808');
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
    title: translate('auto.k2044', { value0: actionLabel }),
    confirmLabel: translate('auto.k2045', { value0: actionLabel }),
    tone: action === 'CONFIRM' ? 'info' : 'warning',
    details: [
      { label: translate('auto.k0249'), value: actionLabel },
      { label: translate('auto.k2046'), value: candidates.length },
      { label: translate('auto.k1357'), value: reviewComment.trim() || translate('auto.k2047') },
      { label: translate('auto.k0178'), value: candidates.map((candidate) => `${candidate.id}@v${candidate.version}`).slice(0, 3).join(', ') || '-' }
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
    title: translate('auto.k2048'),
    confirmLabel: translate('auto.k2049'),
    tone: 'warning',
    details: [
      { label: translate('auto.k0249'), value: translate('auto.k1331') },
      { label: translate('auto.k2046'), value: candidates.length },
      { label: translate('auto.k2050'), value: changedFields.join('；') || '-' },
      { label: translate('auto.k0178'), value: candidates.map((candidate) => `${candidate.id}@v${candidate.version}`).slice(0, 3).join(', ') || '-' }
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
    ? translate('auto.k2051', { value0: candidates.length, value1: selectedCandidateCount })
    : translate('auto.k2052');
  return {
    title: dryRun ? translate('auto.k2053') : translate('auto.k2054'),
    confirmLabel: dryRun ? translate('auto.k2055') : translate('auto.k2056'),
    tone: dryRun && failedCount === 0 ? 'info' : 'warning',
    details: [
      { label: translate('auto.k0249'), value: dryRun ? translate('auto.k2057') : translate('auto.k2058') },
      { label: translate('auto.k1548'), value: selectedScope },
      { label: translate('auto.k2059'), value: totalPublishableCandidates },
      { label: translate('auto.k2060'), value: failedCount }
    ],
    warnings: publishWarnings(dryRun, candidates.length, failedCount),
    candidateTitles: candidateTitles(candidates)
  };
}

export function buildTestDesignConflictResolutionConfirmation(
  candidate: ConflictCandidate,
  record: TestDesignPublishRecordView,
  reason: string,
  comment: string
): TestDesignConfirmationSummary {
  return {
    title: translate('auto.k2061'),
    confirmLabel: translate('auto.k2062'),
    tone: 'warning',
    details: [
      { label: translate('auto.k0249'), value: translate('auto.k2063') },
      { label: translate('auto.k1428'), value: `${candidate.title || candidate.id}@v${candidate.version}` },
      { label: translate('auto.k2064'), value: record.assetCaseId ?? '-' },
      { label: translate('auto.k1367'), value: reason.trim() || translate('auto.k2065') },
      { label: translate('auto.k1370'), value: comment.trim() || translate('auto.k2047') }
    ],
    warnings: conflictResolutionWarnings(record),
    candidateTitles: candidateTitles([candidate])
  };
}

export function buildTestDesignBatchConflictResolutionConfirmation(
  items: readonly ConflictResolutionConfirmationItem[],
  reason: string,
  comment: string
): TestDesignConfirmationSummary {
  const targetCaseIds = Array.from(new Set(items.map((item) => item.record.assetCaseId).filter(Boolean)));
  return {
    title: translate('auto.k2066'),
    confirmLabel: translate('auto.k2067'),
    tone: 'warning',
    details: [
      { label: translate('auto.k0249'), value: translate('auto.k2068') },
      { label: translate('auto.k2069'), value: items.length },
      { label: translate('auto.k2064'), value: targetCaseIds.slice(0, 3).join(', ') || '-' },
      { label: translate('auto.k1367'), value: reason.trim() || translate('auto.k2065') },
      { label: translate('auto.k1370'), value: comment.trim() || translate('auto.k2047') }
    ],
    warnings: batchConflictResolutionWarnings(items),
    candidateTitles: candidateTitles(items.map((item) => item.candidate))
  };
}

function batchReviewWarnings(action: TestDesignCandidateBatchActionType, nonReviewableCount: number) {
  const warnings: string[] = [];
  if (nonReviewableCount > 0) {
    warnings.push(translate('auto.k2070', { value0: nonReviewableCount }));
  }
  if (action === 'CONFIRM') {
    warnings.push(translate('auto.k2071'));
  }
  if (action === 'REJECT') {
    warnings.push(translate('auto.k2072'));
  }
  if (action === 'IGNORE') {
    warnings.push(translate('auto.k2073'));
  }
  return warnings;
}

function batchEditWarnings(candidateCount: number, changedFieldCount: number) {
  const warnings: string[] = [];
  if (!candidateCount) {
    warnings.push(translate('auto.k2074'));
  } else {
    warnings.push(translate('auto.k2075'));
  }
  if (!changedFieldCount) {
    warnings.push(translate('auto.k2076'));
  }
  return warnings;
}

function publishWarnings(dryRun: boolean, candidateCount: number, failedCount: number) {
  const warnings: string[] = [];
  if (!candidateCount) {
    warnings.push(translate('auto.k2077'));
  } else if (dryRun) {
    warnings.push(translate('auto.k2078'));
  } else {
    warnings.push(translate('auto.k2079'));
  }
  if (failedCount > 0) {
    warnings.push(translate('auto.k2080', { value0: failedCount }));
  }
  return warnings;
}

function batchConflictResolutionWarnings(items: readonly ConflictResolutionConfirmationItem[]) {
  const warnings = [
    translate('auto.k2081')
  ];
  const conflictSummaryCount = items.filter((item) => Boolean(item.record.errorMessage)).length;
  if (conflictSummaryCount > 0) {
    warnings.push(translate('auto.k2082', { value0: conflictSummaryCount }));
  } else {
    warnings.push(translate('auto.k2083'));
  }
  return warnings;
}

function conflictResolutionWarnings(record: TestDesignPublishRecordView) {
  const warnings = [
    translate('auto.k2084')
  ];
  if (record.errorMessage) {
    warnings.push(translate('auto.k2085', { value0: record.errorMessage }));
  } else {
    warnings.push(translate('auto.k2086'));
  }
  return warnings;
}

function candidateTitles(candidates: readonly ConfirmationCandidate[]) {
  return candidates.map((candidate) => candidate.title || candidate.id).slice(0, 5);
}
