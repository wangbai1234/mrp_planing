import { defineConfig } from '@playwright/test'

export default defineConfig({
  testDir: '.',
  testMatch: 'frontier-new-code.spec.ts',
  workers: 1,
  retries: 0,
  reporter: 'line',
  outputDir: '../../test-kit/2026-10-15/evidence/e2e-frontier-new-code-data-ready-20261109',
  use: {
    baseURL: 'http://127.0.0.1:5181',
    browserName: 'chromium',
    channel: 'chrome',
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure'
  }
})
