import type { TestDesignCandidateView } from './api/testDesign';

export function canPublishTestDesignCandidate(candidate: Pick<TestDesignCandidateView, 'status'>) {
  return candidate.status === 'CONFIRMED' || candidate.status === 'FAILED';
}

export function canReviewTestDesignCandidate(candidate: Pick<TestDesignCandidateView, 'status'>) {
  return ['GENERATED', 'EDITED'].includes(candidate.status);
}

export function canSelectTestDesignCandidate(candidate: Pick<TestDesignCandidateView, 'status'>) {
  return canReviewTestDesignCandidate(candidate) || canPublishTestDesignCandidate(candidate);
}

export function selectedTestDesignReviewCandidates<T extends Pick<TestDesignCandidateView, 'id' | 'status'>>(
  candidates: readonly T[],
  selectedCandidateIds: readonly string[]
) {
  return candidates.filter((candidate) => selectedCandidateIds.includes(candidate.id) && canReviewTestDesignCandidate(candidate));
}

export function selectedTestDesignPublishCandidates<T extends Pick<TestDesignCandidateView, 'id' | 'status'>>(
  candidates: readonly T[],
  selectedCandidateIds: readonly string[]
) {
  return candidates.filter((candidate) => selectedCandidateIds.includes(candidate.id) && canPublishTestDesignCandidate(candidate));
}

export function testDesignPublishTargets<T extends Pick<TestDesignCandidateView, 'id' | 'status'>>(
  candidates: readonly T[],
  selectedCandidateIds: readonly string[]
) {
  const publishableCandidates = candidates.filter(canPublishTestDesignCandidate);
  if (!selectedCandidateIds.length) {
    return publishableCandidates;
  }
  return selectedTestDesignPublishCandidates(candidates, selectedCandidateIds);
}
