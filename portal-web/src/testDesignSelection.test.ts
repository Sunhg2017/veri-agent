import { describe, expect, it } from 'vitest';
import {
  canSelectTestDesignCandidate,
  selectedTestDesignPublishCandidates,
  selectedTestDesignReviewCandidates,
  testDesignPublishTargets
} from './testDesignSelection';

const candidates = [
  { id: 'generated-1', status: 'GENERATED' },
  { id: 'edited-1', status: 'EDITED' },
  { id: 'confirmed-1', status: 'CONFIRMED' },
  { id: 'failed-1', status: 'FAILED' },
  { id: 'published-1', status: 'PUBLISHED' },
  { id: 'ignored-1', status: 'IGNORED' }
];

describe('WP5 test design selection helpers', () => {
  it('allows selecting only candidates that can participate in review or publish actions', () => {
    expect(candidates.filter(canSelectTestDesignCandidate).map((candidate) => candidate.id)).toEqual([
      'generated-1',
      'edited-1',
      'confirmed-1',
      'failed-1'
    ]);
  });

  it('separates selected review candidates from selected publish candidates', () => {
    const selectedIds = ['generated-1', 'edited-1', 'confirmed-1', 'failed-1', 'ignored-1'];

    expect(selectedTestDesignReviewCandidates(candidates, selectedIds).map((candidate) => candidate.id)).toEqual([
      'generated-1',
      'edited-1'
    ]);
    expect(selectedTestDesignPublishCandidates(candidates, selectedIds).map((candidate) => candidate.id)).toEqual([
      'confirmed-1',
      'failed-1'
    ]);
  });

  it('publishes all publishable candidates when nothing is selected', () => {
    expect(testDesignPublishTargets(candidates, []).map((candidate) => candidate.id)).toEqual([
      'confirmed-1',
      'failed-1'
    ]);
  });

  it('publishes only selected publishable candidates when a selection exists', () => {
    expect(testDesignPublishTargets(candidates, ['generated-1', 'failed-1']).map((candidate) => candidate.id)).toEqual(['failed-1']);
  });
});
