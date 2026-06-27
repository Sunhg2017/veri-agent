import { beforeEach, describe, expect, it, vi } from 'vitest';
import { useThemeStore } from './themeStore';

describe('theme store', () => {
  beforeEach(() => {
    window.localStorage.clear();
    vi.spyOn(window, 'matchMedia').mockReturnValue({
      addEventListener: vi.fn(),
      addListener: vi.fn(),
      dispatchEvent: vi.fn(),
      matches: false,
      media: '(prefers-color-scheme: dark)',
      onchange: null,
      removeEventListener: vi.fn(),
      removeListener: vi.fn()
    });
    useThemeStore.setState({ mode: 'system', resolvedMode: 'light' });
  });

  it('toggles resolved theme and persists explicit mode', () => {
    useThemeStore.getState().toggleMode();
    expect(useThemeStore.getState().mode).toBe('dark');
    expect(useThemeStore.getState().resolvedMode).toBe('dark');
    expect(window.localStorage.getItem('veri-agent.theme')).toBe('dark');

    useThemeStore.getState().toggleMode();
    expect(useThemeStore.getState().mode).toBe('light');
    expect(useThemeStore.getState().resolvedMode).toBe('light');
  });
});
