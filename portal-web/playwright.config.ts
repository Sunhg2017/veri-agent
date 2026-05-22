import { defineConfig, devices } from '@playwright/test';

const port = Number(process.env.PORTAL_WEB_E2E_PORT ?? 4173);
const host = '127.0.0.1';
const baseURL = `http://${host}:${port}`;
const chromeChannel = process.env.PW_CHROME_CHANNEL?.trim();

export default defineConfig({
  testDir: './e2e',
  testMatch: '**/*.playwright.ts',
  timeout: 30_000,
  expect: {
    timeout: 5_000
  },
  reporter: process.env.CI ? [['dot'], ['html', { open: 'never' }]] : [['list']],
  use: {
    baseURL,
    trace: 'on-first-retry'
  },
  webServer: {
    command: `npm run build && npm run preview -- --host ${host} --port ${port}`,
    url: baseURL,
    reuseExistingServer: !process.env.CI,
    timeout: 120_000
  },
  projects: [
    {
      name: 'chromium',
      use: {
        ...devices['Desktop Chrome'],
        ...(chromeChannel ? { channel: chromeChannel } : {})
      }
    }
  ]
});
