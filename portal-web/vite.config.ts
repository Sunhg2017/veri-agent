import { loadEnv } from 'vite';
import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

function createManualChunk(id: string) {
  if (!id.includes('/node_modules/')) {
    return undefined;
  }
  if (id.includes('/react/') || id.includes('/react-dom/') || id.includes('/react-router')) {
    return 'react';
  }
  if (id.includes('/@hookform/') || id.includes('/react-hook-form/') || id.includes('/zod/')) {
    return 'forms';
  }
  if (id.includes('/@tanstack/react-query/') || id.includes('/zustand/')) {
    return 'query';
  }
  if (id.includes('/i18next/') || id.includes('/react-i18next/')) {
    return 'i18n';
  }
  if (id.includes('/@radix-ui/')) {
    return 'radix';
  }
  if (id.includes('/dayjs/') || id.includes('/lucide-react/')) {
    return 'vendor';
  }
  return undefined;
}

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, '.', '');
  const apiTarget = env.VITE_API_PROXY_TARGET ?? 'http://localhost:8080';

  return {
    plugins: [react()],
    server: {
      port: 5173,
      proxy: {
        '/api': {
          target: apiTarget,
          changeOrigin: true
        }
      }
    },
    build: {
      rollupOptions: {
        output: {
          manualChunks: createManualChunk
        }
      }
    },
    test: {
      environment: 'jsdom',
      globals: true,
      setupFiles: ['./src/test/setup.ts']
    }
  };
});
