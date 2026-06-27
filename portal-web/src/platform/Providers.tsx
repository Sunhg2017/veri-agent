import { QueryClientProvider } from '@tanstack/react-query';
import { useEffect, type ReactNode } from 'react';
import { HashRouter } from 'react-router-dom';
import { i18n } from './i18n';
import { queryClient } from './queryClient';
import { useThemeStore } from './themeStore';
import { ConfirmDialogProvider } from '../components/ui/ConfirmDialog';

export function AppProviders({ children }: { children: ReactNode }) {
  const mode = useThemeStore((state) => state.mode);
  const resolvedMode = useThemeStore((state) => state.resolvedMode);
  const syncSystemTheme = useThemeStore((state) => state.syncSystemTheme);

  useEffect(() => {
    document.documentElement.dataset.theme = resolvedMode;
    document.documentElement.dataset.themeMode = mode;
  }, [mode, resolvedMode]);

  useEffect(() => {
    const media = window.matchMedia('(prefers-color-scheme: dark)');
    media.addEventListener('change', syncSystemTheme);
    return () => media.removeEventListener('change', syncSystemTheme);
  }, [syncSystemTheme]);

  useEffect(() => {
    const syncLocale = () => window.localStorage.setItem('veri-agent.locale', i18n.language);
    i18n.on('languageChanged', syncLocale);
    return () => i18n.off('languageChanged', syncLocale);
  }, []);

  return (
    <QueryClientProvider client={queryClient}>
      <HashRouter>
        <ConfirmDialogProvider>
          {children}
        </ConfirmDialogProvider>
      </HashRouter>
    </QueryClientProvider>
  );
}
