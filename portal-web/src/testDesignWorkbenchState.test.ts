import { describe, expect, it } from 'vitest';
import type { AssetRequirementView } from './api/assets';
import type {
  TestDesignCandidateBatchActionResult,
  TestDesignCandidateView,
  TestDesignPublishRecordView,
  TestDesignTemplateView
} from './api/testDesign';
import {
  applyConflictResolutionRecord,
  conflictResolutionCandidate,
  countByStatus,
  filterCandidates,
  filterRequirements,
  mergeBatchCandidates,
  mergeCandidateCache,
  mergeUpdatedCandidates,
  parseContextAssetIds,
  releaseReadinessReasonCodeValue,
  reportArchiveReasonCodeValue,
  stepsFromDraft,
  stepsToQualityText,
  tagsFromText,
  templateDraftFromView,
  templatePayload
} from './testDesignWorkbenchState';

function requirement(overrides: Partial<AssetRequirementView>): AssetRequirementView {
  return {
    id: 'req-default',
    title: 'Default requirement',
    source: 'MANUAL',
    status: 'APPROVED',
    priority: 'MEDIUM',
    tags: [],
    version: 1,
    ...overrides
  };
}

function candidate(overrides: Partial<TestDesignCandidateView>): TestDesignCandidateView {
  return {
    id: 'candidate-default',
    title: 'Default candidate',
    coverageType: 'FUNCTIONAL',
    priority: 'MEDIUM',
    status: 'GENERATED',
    steps: [],
    tags: [],
    version: 1,
    ...overrides
  };
}

describe('test design workbench state helpers', () => {
  it('filters requirements and candidates by project, status, coverage and keyword', () => {
    const requirements = [
      requirement({ id: 'req-login', projectId: 'P1', title: 'Login flow', acceptanceCriteria: 'OTP accepted', tags: ['auth'] }),
      requirement({ id: 'req-billing', projectId: 'P2', status: 'DRAFT', title: 'Billing flow', tags: ['finance'] })
    ];
    expect(filterRequirements(requirements, { projectId: 'P1', status: 'APPROVED', keyword: 'otp' }))
      .toEqual([requirements[0]]);

    const candidates = [
      candidate({ id: 'case-smoke', status: 'CONFIRMED', coverageType: 'SMOKE', title: 'Login smoke', tags: ['auth'] }),
      candidate({ id: 'case-boundary', status: 'GENERATED', coverageType: 'BOUNDARY', title: 'Billing edge', tags: ['finance'] })
    ];
    expect(filterCandidates(candidates, { status: 'CONFIRMED', coverageType: 'SMOKE', keyword: 'auth' }))
      .toEqual([candidates[0]]);
  });

  it('merges candidate updates without losing unrelated cached selections', () => {
    const original = candidate({ id: 'case-1', title: 'Original', status: 'GENERATED' });
    const unchanged = candidate({ id: 'case-2', title: 'Unchanged', status: 'GENERATED' });
    const updated = candidate({ id: 'case-1', title: 'Updated', status: 'CONFIRMED' });

    expect(mergeUpdatedCandidates([original, unchanged], [updated])).toEqual([updated, unchanged]);
    expect(mergeCandidateCache({ 'case-2': unchanged }, [updated])).toEqual({
      'case-1': updated,
      'case-2': unchanged
    });

    const batchResult: TestDesignCandidateBatchActionResult = {
      action: 'CONFIRM',
      total: 1,
      succeededCount: 1,
      failedCount: 0,
      items: [{ candidateId: 'case-1', result: 'SUCCEEDED', candidate: updated }]
    };
    expect(mergeBatchCandidates([original, unchanged], batchResult)).toEqual([updated, unchanged]);
    expect(countByStatus([updated, unchanged])).toEqual({ CONFIRMED: 1, GENERATED: 1 });
  });

  it('normalizes step, tag and template payload drafts for API writes', () => {
    expect(stepsFromDraft([
      { id: 'step-1', action: ' open page ', expectedResult: ' loaded ', selected: false },
      { id: 'step-2', action: ' ', expectedResult: '', selected: false }
    ])).toEqual([{ action: 'open page', expectedResult: 'loaded' }]);
    expect(stepsToQualityText([{ id: 'step-1', action: 'A ', expectedResult: ' B', selected: false }])).toBe('A => B');
    expect(tagsFromText(' smoke, auth ,, regression ')).toEqual(['smoke', 'auth', 'regression']);
    expect(parseContextAssetIds('api-1, api-2\napi-3，api-4')).toEqual(['api-1', 'api-2', 'api-3', 'api-4']);

    const template = {
      id: 'tpl-1',
      projectId: 'P1',
      name: 'Balanced',
      promptKey: 'wp5',
      promptVersion: 'v1',
      coverageTypes: ['SMOKE'],
      generationStrategy: 'BALANCED',
      coverageStrategy: 'DEFAULT_ORDER',
      caseCountPerRequirement: 2,
      contextDefaults: {
        environmentKey: ' test ',
        contextApiIds: ['api-1', ' api-2 ', ''],
        contextPageIds: ' page-1 ',
        contextFlowIds: null
      },
      enabled: true
    } satisfies TestDesignTemplateView;

    const draft = templateDraftFromView(template);
    expect(draft.contextApiIds).toBe('api-1, api-2');
    expect(templatePayload({ ...draft, contextFlowIds: '', caseCountPerRequirement: '3' }, false)).toMatchObject({
      name: 'Balanced',
      caseCountPerRequirement: 3,
      contextDefaults: {
        environmentKey: 'test',
        contextApiIds: ['api-1', 'api-2'],
        contextPageIds: ['page-1']
      }
    });
  });

  it('keeps conflict resolution records and reason codes deterministic', () => {
    const openConflict: TestDesignPublishRecordView = {
      candidateId: 'case-1',
      candidateVersion: 3,
      title: 'Existing conflict',
      dryRun: false,
      action: 'DUPLICATE_REVIEW_REQUIRED',
      result: 'CONFLICT'
    };
    const resolved: TestDesignPublishRecordView = {
      candidateId: 'case-1',
      candidateVersion: 4,
      title: 'Resolved conflict',
      dryRun: false,
      action: 'LINK_EXISTING',
      result: 'SUCCEEDED',
      assetCaseId: 'asset-1'
    };
    const failed: TestDesignPublishRecordView = {
      candidateId: 'case-2',
      dryRun: false,
      action: 'LINK_EXISTING',
      result: 'FAILED'
    };

    expect(conflictResolutionCandidate(openConflict, new Map())).toEqual({
      id: 'case-1',
      title: 'Existing conflict',
      status: 'CONFIRMED',
      version: 3
    });
    expect(applyConflictResolutionRecord([openConflict], resolved)).toEqual([resolved]);
    expect(applyConflictResolutionRecord([openConflict], failed)).toEqual([failed, openConflict]);
    expect(releaseReadinessReasonCodeValue('UNKNOWN', 'SMOKE_VALIDATION')).toBe('SMOKE_VALIDATION');
    expect(reportArchiveReasonCodeValue('COMPLIANCE_AUDIT', 'RETENTION_POLICY')).toBe('COMPLIANCE_AUDIT');
  });
});
