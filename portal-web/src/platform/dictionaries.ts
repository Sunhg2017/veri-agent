import { translate } from './i18n';

export type DictionaryOption<T extends string = string> = {
  disabled?: boolean;
  label: string;
  value: T;
};

const labelKeys: Record<string, string> = {
  ACCEPTED: 'auto.k2671',
  ACTIVE: 'auto.k2676',
  ALL: 'auto.k2711',
  API: 'auto.k2766',
  API_TEST: 'auto.k2744',
  APPROVED: 'auto.k2651',
  ASSISTANT: 'auto.k2823',
  ASYNC: 'auto.k2825',
  ARCHIVE: 'auto.k2788',
  ARCHIVED: 'auto.k2653',
  AUDIT_OUTBOX_REPLAY_ELIGIBLE: 'auto.k2707',
  AVAILABLE: 'auto.k2733',
  AXURE: 'auto.k2777',
  BASELINE_FREEZE: 'auto.k2702',
  BLOCK: 'auto.k2726',
  BLOCK_DOWNSTREAM: 'auto.k2748',
  BLOCKED: 'auto.k2725',
  BOUNDARY: 'auto.k2762',
  BUSINESS_FLOW: 'auto.k2768',
  CANCELED: 'auto.k2661',
  CANCELLED: 'auto.k2661',
  CANDIDATE: 'auto.k2688',
  CASE_DESIGN: 'auto.k2817',
  CHAT: 'auto.k2813',
  CLEANUP: 'auto.k2738',
  CLOSED: 'auto.k2730',
  COMMENT: 'auto.k2714',
  COMPLETED: 'auto.k2785',
  CONFIDENTIAL: 'auto.k2722',
  CONFIRMED: 'auto.k2666',
  CONFIRMED_FLAKY: 'auto.k2686',
  CONFLICT: 'auto.k2780',
  CONFLUENCE: 'auto.k2755',
  CONTINUE: 'auto.k2747',
  CRITICAL: 'auto.k2679',
  CRON: 'auto.k2750',
  CSV: 'auto.k2770',
  CUSTOM_API: 'auto.k2752',
  DEAD: 'auto.k2703',
  DEAD_LETTER: 'auto.k2672',
  DEFECT_TRIAGE: 'auto.k2818',
  DEGRADED: 'auto.k2787',
  DEPRECATED: 'auto.k2677',
  DISABLED: 'auto.k2652',
  DONE: 'auto.k2784',
  DOWN: 'auto.k2728',
  DRAFT: 'auto.k2649',
  DINGTALK: 'auto.k2757',
  DUPLICATE_REVIEW_REQUIRED: 'auto.k2781',
  EMAIL: 'auto.k2709',
  ENABLED: 'auto.k2674',
  ENVIRONMENT: 'auto.k2713',
  ERROR: 'auto.k2783',
  EXCEPTION: 'auto.k2761',
  EXTERNAL_REF: 'auto.k2791',
  EXTERNAL_SHARE: 'auto.k2789',
  EXPIRED: 'auto.k2806',
  FAILED: 'auto.k2657',
  FAIL: 'auto.k2657',
  FAILED_OR_DEAD: 'auto.k2704',
  FAIL_FAST: 'auto.k2746',
  FALLBACK: 'auto.k2727',
  FALLBACK_ONLY: 'auto.k2743',
  FEISHU: 'auto.k2756',
  FIGMA: 'auto.k2775',
  FLAKY_CANDIDATE: 'auto.k2685',
  FROZEN: 'auto.k2690',
  FUNCTIONAL: 'auto.k2760',
  GENERATING: 'auto.k2807',
  GENERATED: 'auto.k2790',
  GENERATION: 'auto.k2803',
  GENERATION_QUEUE_LAG: 'auto.k2705',
  GENERATION_TIMEOUT: 'auto.k2706',
  GOLDEN: 'auto.k2689',
  HIGH: 'auto.k2680',
  IGNORED: 'auto.k2667',
  IMPORT: 'auto.k2692',
  IMPORTED: 'auto.k2695',
  INFO: 'auto.k2731',
  INHERIT: 'auto.k2724',
  INTERNAL: 'auto.k2721',
  INVALID: 'auto.k2782',
  JSON: 'auto.k2771',
  LANHU: 'auto.k2776',
  LEASED: 'auto.k2735',
  LINK_EXISTING: 'auto.k2778',
  LOCAL_ECHO: 'auto.k2717',
  LOCKED: 'auto.k2734',
  LOW: 'auto.k2682',
  MANUAL: 'auto.k2691',
  MANAGED: 'auto.k2810',
  MARKDOWN: 'auto.k2751',
  MATCHED: 'auto.k2809',
  MEDIUM: 'auto.k2681',
  MOCK_FAILURE: 'auto.k2719',
  MODEL_WITH_FALLBACK: 'auto.k2742',
  NONE: 'auto.k2684',
  NOT_REQUIRED: 'auto.k2804',
  OCR: 'auto.k2754',
  OK: 'auto.k2732',
  OPEN: 'auto.k2729',
  OPENAI_COMPATIBLE: 'auto.k2718',
  OPENAPI: 'auto.k2772',
  OPS_CONSOLE: 'auto.k2708',
  PAGE: 'auto.k2767',
  PASS: 'auto.k2792',
  PARTIAL_SUCCESS: 'auto.k2659',
  PASSED: 'auto.k2792',
  PAUSED: 'auto.k2749',
  PDF: 'auto.k2753',
  PENDING: 'auto.k2665',
  PERMISSION: 'auto.k2763',
  PLANNED: 'auto.k2675',
  PLATFORM: 'auto.k2805',
  PLAYWRIGHT_CODEGEN: 'auto.k2793',
  PREPARE: 'auto.k2736',
  PARSED: 'auto.k2808',
  PROCESSING: 'auto.k2669',
  PROCESSED: 'auto.k2670',
  PROJECT: 'auto.k2712',
  PROMPT_CHANGE: 'auto.k2700',
  PUBLIC: 'auto.k2720',
  PUBLISH: 'auto.k2794',
  PUBLISH_FAILED: 'auto.k2668',
  PUBLISH_QUEUE_LAG: 'auto.k2795',
  PUBLISH_QUEUED: 'auto.k2662',
  PUBLISH_TIMEOUT: 'auto.k2796',
  PUBLISHED: 'auto.k2664',
  PUBLISHED_CASE: 'auto.k2694',
  PUBLISHING: 'auto.k2663',
  QUEUED: 'auto.k2655',
  READY: 'auto.k2741',
  REQUIREMENT_PARSE: 'auto.k2816',
  REQUIREMENT_SUMMARY: 'auto.k2819',
  REFRESH: 'auto.k2737',
  REGRESSION: 'auto.k2764',
  REJECTED: 'auto.k2654',
  REMOVED: 'auto.k2678',
  REPLAYED: 'auto.k2673',
  REPLAYING: 'auto.k2786',
  REPORT_HANDOFF: 'auto.k2745',
  REQUIREMENT: 'auto.k2765',
  RESERVED: 'auto.k2797',
  RESOLVED: 'auto.k2811',
  RESTRICTED: 'auto.k2723',
  REVIEW_FEEDBACK: 'auto.k2693',
  REVIEWING: 'auto.k2650',
  ROLE: 'auto.k2716',
  ROLLBACK: 'auto.k2739',
  RUNNING: 'auto.k2656',
  SCHEDULED: 'auto.k2701',
  SELENIUM_IDE: 'auto.k2798',
  SKIPPED: 'auto.k2779',
  SMOKE: 'auto.k2759',
  STATIC_CHECK_FAILED: 'auto.k2812',
  STREAM: 'auto.k2822',
  SYSTEM: 'auto.k2820',
  SUCCEEDED: 'auto.k2658',
  SUCCESS: 'auto.k2658',
  SYNC: 'auto.k2824',
  TEST_CASE: 'auto.k2769',
  TEST_DATA: 'auto.k2814',
  TEXT: 'auto.k2799',
  TIMEOUT: 'auto.k2740',
  UP: 'auto.k0095',
  USER: 'auto.k2821',
  VALID: 'auto.k2800',
  WAIVED: 'auto.k2687',
  WARN: 'auto.k2801',
  WARNING: 'auto.k2801',
  WEBHOOK: 'auto.k2710',
  WORD: 'auto.k2802',
  WORK_ORDER: 'auto.k2715',
  YUQUE: 'auto.k2758'
};

export function dictionaryLabel(value: unknown, fallback = '-') {
  if (value === undefined || value === null || value === '') {
    return fallback;
  }
  const raw = String(value).trim();
  const key = normalizeDictionaryKey(raw);
  const labelKey = labelKeys[key];
  return labelKey ? translate(labelKey) : humanizeDictionaryValue(raw);
}

export function dictionaryOption<T extends string>(value: T, disabled = false): DictionaryOption<T> {
  return {
    disabled,
    label: dictionaryLabel(value),
    value
  };
}

export function dictionaryOptions<T extends string>(values: readonly T[]): Array<DictionaryOption<T>> {
  return values.map((value) => dictionaryOption(value));
}

export function dictionaryListLabel(value: unknown, fallback = '-') {
  if (value === undefined || value === null || value === '') {
    return fallback;
  }
  const items = String(value)
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean);
  if (!items.length) {
    return fallback;
  }
  return items.map((item) => dictionaryLabel(item)).join('、');
}

export function humanizeDictionaryValue(value: string) {
  if (/^[A-Z0-9]{2,8}$/.test(value)) {
    return value;
  }
  return value
    .split(/[_\s-]+/)
    .filter(Boolean)
    .map((part) => part.length <= 3 ? part.toUpperCase() : `${part.charAt(0).toUpperCase()}${part.slice(1).toLowerCase()}`)
    .join(' ');
}

function normalizeDictionaryKey(value: string) {
  return value.trim().replace(/[-\s]+/g, '_').toUpperCase();
}
