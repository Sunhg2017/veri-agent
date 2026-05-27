export const TEST_DESIGN_CANDIDATE_PAGE_SIZES = [10, 20, 50] as const;
export const DEFAULT_TEST_DESIGN_CANDIDATE_PAGE_SIZE = 20;

export type PaginatedItems<T> = {
  items: T[];
  index: number;
  size: number;
  total: number;
  totalPages: number;
  start: number;
  end: number;
  hasPrevious: boolean;
  hasNext: boolean;
};

export function paginateItems<T>(
  items: readonly T[],
  pageIndex: number,
  pageSize: number
): PaginatedItems<T> {
  const size = normalizePageSize(pageSize);
  const total = items.length;
  const totalPages = Math.max(1, Math.ceil(total / size));
  const index = clamp(normalizePageIndex(pageIndex), 0, totalPages - 1);
  const startOffset = index * size;
  const pageItems = items.slice(startOffset, startOffset + size);

  return {
    items: pageItems,
    index,
    size,
    total,
    totalPages,
    start: total ? startOffset + 1 : 0,
    end: total ? Math.min(startOffset + pageItems.length, total) : 0,
    hasPrevious: index > 0,
    hasNext: index < totalPages - 1
  };
}

export function pageFromServerItems<T>(
  items: readonly T[],
  pageIndex: number,
  pageSize: number,
  totalItems: number
): PaginatedItems<T> {
  const size = normalizePageSize(pageSize);
  const total = normalizeTotal(totalItems);
  const totalPages = Math.max(1, Math.ceil(total / size));
  const index = clamp(normalizePageIndex(pageIndex), 0, totalPages - 1);
  const pageItems = items.slice(0, size);
  const startOffset = index * size;

  return {
    items: pageItems,
    index,
    size,
    total,
    totalPages,
    start: total && pageItems.length ? startOffset + 1 : 0,
    end: total && pageItems.length ? Math.min(startOffset + pageItems.length, total) : 0,
    hasPrevious: index > 0,
    hasNext: index < totalPages - 1
  };
}

function normalizePageIndex(value: number) {
  return Number.isInteger(value) && value > 0 ? value : 0;
}

function normalizePageSize(value: number) {
  return TEST_DESIGN_CANDIDATE_PAGE_SIZES.includes(value as (typeof TEST_DESIGN_CANDIDATE_PAGE_SIZES)[number])
    ? value
    : DEFAULT_TEST_DESIGN_CANDIDATE_PAGE_SIZE;
}

function clamp(value: number, min: number, max: number) {
  return Math.min(Math.max(value, min), max);
}

function normalizeTotal(value: number) {
  return Number.isFinite(value) && value > 0 ? Math.trunc(value) : 0;
}
