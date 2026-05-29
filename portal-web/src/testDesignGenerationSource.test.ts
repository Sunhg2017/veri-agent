import { describe, expect, it } from 'vitest';
import type { TestDesignCandidateView, TestDesignTaskView } from './api/testDesign';
import {
  candidateGenerationSource,
  generationSourceText,
  taskGenerationSource
} from './testDesignGenerationSource';

const baseTask: TestDesignTaskView = {
  id: 'task-1',
  projectId: 'project-1',
  title: 'WP5 生成任务',
  status: 'SUCCEEDED',
  requirementIds: ['req-1'],
  coverageTypes: ['SMOKE'],
  totalRequirements: 1,
  generatedCount: 1,
  confirmedCount: 0,
  publishedCount: 0,
  contextSummary: {}
};

const baseCandidate: TestDesignCandidateView = {
  id: 'cand-1',
  taskId: 'task-1',
  projectId: 'project-1',
  requirementId: 'req-1',
  title: '验证登录核心冒烟流程',
  coverageType: 'SMOKE',
  priority: 'HIGH',
  status: 'GENERATED',
  steps: [],
  tags: ['wp5', 'ai-generated'],
  confidence: 0.86,
  version: 0
};

describe('WP5 generation source helpers', () => {
  it('classifies strict model output from model invocation metadata', () => {
    const source = taskGenerationSource({
      ...baseTask,
      modelInvocationId: 'invoke-1',
      modelProviderName: 'openai',
      modelName: 'gpt-5-mini'
    });

    expect(source).toMatchObject({
      kind: 'MODEL_OUTPUT',
      label: '模型输出',
      detail: 'openai / gpt-5-mini',
      tone: 'success'
    });
    expect(generationSourceText(source)).toBe('模型输出 · openai / gpt-5-mini');
  });

  it('classifies model fallback tasks as template output when the warning is present', () => {
    expect(taskGenerationSource({
      ...baseTask,
      modelName: 'MODEL_WITH_FALLBACK',
      errorMessage: '模型生成失败，已降级规则模板: prompt missing'
    })).toMatchObject({
      kind: 'MODEL_FALLBACK_TEMPLATE',
      label: '模型降级模板',
      tone: 'warning'
    });
  });

  it('keeps rule template candidates separate from model output', () => {
    expect(candidateGenerationSource({
      ...baseCandidate,
      modelName: 'RULE_TEMPLATE'
    })).toMatchObject({
      kind: 'RULE_TEMPLATE',
      label: '规则模板',
      detail: '未调用模型',
      tone: 'neutral'
    });
  });

  it('inherits fallback source from the selected task for template candidates', () => {
    expect(candidateGenerationSource(baseCandidate, {
      ...baseTask,
      modelName: 'MODEL_WITH_FALLBACK',
      errorMessage: '模型生成失败，已降级规则模板'
    })).toMatchObject({
      kind: 'MODEL_FALLBACK_TEMPLATE',
      detail: '模型失败后由规则模板生成'
    });
  });

  it('marks queued model tasks without invocation as pending instead of model output', () => {
    expect(taskGenerationSource({
      ...baseTask,
      status: 'QUEUED',
      modelName: 'MODEL'
    })).toMatchObject({
      kind: 'MODEL_PENDING',
      label: '模型待生成',
      detail: '严格模型模式'
    });
  });
});
