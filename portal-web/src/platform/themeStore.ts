import { create } from 'zustand';

export type ThemeMode = 'light' | 'dark' | 'system';
export type ResolvedThemeMode = 'light' | 'dark';

const THEME_STORAGE_KEY = 'veri-agent.theme';

function readInitialMode(): ThemeMode {
  const value = window.localStorage.getItem(THEME_STORAGE_KEY);
  return value === 'light' || value === 'dark' || value === 'system' ? value : 'system';
}

function resolveTheme(mode: ThemeMode): ResolvedThemeMode {
  if (mode === 'system') {
    return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
  }
  return mode;
}

interface ThemeState {
  mode: ThemeMode;
  resolvedMode: ResolvedThemeMode;
  setMode: (mode: ThemeMode) => void;
  syncSystemTheme: () => void;
  toggleMode: () => void;
}

export const useThemeStore = create<ThemeState>((set, get) => ({
  mode: readInitialMode(),
  resolvedMode: resolveTheme(readInitialMode()),
  setMode: (mode) => {
    window.localStorage.setItem(THEME_STORAGE_KEY, mode);
    set({ mode, resolvedMode: resolveTheme(mode) });
  },
  syncSystemTheme: () => {
    const { mode } = get();
    if (mode === 'system') {
      set({ resolvedMode: resolveTheme(mode) });
    }
  },
  toggleMode: () => {
    const nextMode: ThemeMode = get().resolvedMode === 'dark' ? 'light' : 'dark';
    window.localStorage.setItem(THEME_STORAGE_KEY, nextMode);
    set({ mode: nextMode, resolvedMode: nextMode });
  }
}));
