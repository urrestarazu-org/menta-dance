// Playwright configuration for the BFF accessibility regression checks.
// The suite only talks to an already running BFF (see docs/ACCESSIBILITY.md).
import { defineConfig, devices } from '@playwright/test';

const isCi = Boolean(process.env.CI);

export default defineConfig({
  testDir: './tests',
  // Single worker: the journeys share one demo database and the login rate limiter.
  workers: 1,
  fullyParallel: false,
  retries: isCi ? 1 : 0,
  timeout: 30_000,
  expect: { timeout: 10_000 },
  reporter: isCi
    ? [['list'], ['html', { open: 'never', outputFolder: 'playwright-report' }]]
    : [['list']],
  outputDir: 'test-results',
  use: {
    baseURL: process.env.BASE_URL || 'http://localhost:8080',
    locale: 'es-AR',
    trace: 'retain-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
