import { describe, expect, it } from 'vitest';
import { DEFAULT_TEST_DESIGN_CANDIDATE_PAGE_SIZE, paginateItems } from './testDesignPagination';

describe('WP5 test design pagination', () => {
  it('returns the requested candidate page with stable range metadata', () => {
    const page = paginateItems(Array.from({ length: 45 }, (_, index) => `candidate-${index + 1}`), 1, 20);

    expect(page.items).toHaveLength(20);
    expect(page.items[0]).toBe('candidate-21');
    expect(page).toMatchObject({
      index: 1,
      size: 20,
      total: 45,
      totalPages: 3,
      start: 21,
      end: 40,
      hasPrevious: true,
      hasNext: true
    });
  });

  it('clamps out-of-range page indexes to the last available page', () => {
    const page = paginateItems(Array.from({ length: 45 }, (_, index) => index), 99, 20);

    expect(page.index).toBe(2);
    expect(page.items).toEqual([40, 41, 42, 43, 44]);
    expect(page.start).toBe(41);
    expect(page.end).toBe(45);
    expect(page.hasNext).toBe(false);
  });

  it('falls back to the default size for unsupported size values', () => {
    const page = paginateItems(Array.from({ length: 30 }, (_, index) => index), 0, 7);

    expect(page.size).toBe(DEFAULT_TEST_DESIGN_CANDIDATE_PAGE_SIZE);
    expect(page.items).toHaveLength(DEFAULT_TEST_DESIGN_CANDIDATE_PAGE_SIZE);
  });

  it('keeps empty pages renderable without negative ranges', () => {
    const page = paginateItems([], 2, 20);

    expect(page).toMatchObject({
      items: [],
      index: 0,
      total: 0,
      totalPages: 1,
      start: 0,
      end: 0,
      hasPrevious: false,
      hasNext: false
    });
  });
});
