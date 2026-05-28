import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  buildTestDesignTaskIdempotencySignature,
  createTestDesignTaskIdempotencyKey,
  resolveTestDesignTaskIdempotency
} from './testDesignIdempotency';

describe('WP5 task idempotency helpers', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('builds a stable signature from normalized create task inputs', () => {
    const signature = buildTestDesignTaskIdempotencySignature({
      projectId: ' project-1 ',
      title: ' 登录生成 ',
      requirementIds: [' req-1 ', 'req-2'],
      coverageTypes: [' smoke ', 'functional'],
      caseCountPerRequirement: 2
    });

    expect(signature).toBe(JSON.stringify({
      projectId: 'project-1',
      title: '登录生成',
      requirementIds: ['req-1', 'req-2'],
      coverageTypes: ['SMOKE', 'FUNCTIONAL'],
      caseCountPerRequirement: 2
    }));
  });

  it('reuses the key for the same signature and rotates it for a changed payload', () => {
    const createKey = vi.fn()
      .mockReturnValueOnce('wp5:create:first')
      .mockReturnValueOnce('wp5:create:second');

    const first = resolveTestDesignTaskIdempotency(null, 'signature-a', createKey);
    const replay = resolveTestDesignTaskIdempotency(first, 'signature-a', createKey);
    const changed = resolveTestDesignTaskIdempotency(first, 'signature-b', createKey);

    expect(first.key).toBe('wp5:create:first');
    expect(replay).toBe(first);
    expect(changed).toEqual({ signature: 'signature-b', key: 'wp5:create:second' });
    expect(createKey).toHaveBeenCalledTimes(2);
  });

  it('generates backend-safe keys with the WP5 create prefix', () => {
    vi.spyOn(Date, 'now').mockReturnValue(123456789);

    expect(createTestDesignTaskIdempotencyKey(() => 'abc/中文 token=secret')).toBe('wp5:create:21i3v9:abc-x-x');
  });
});
