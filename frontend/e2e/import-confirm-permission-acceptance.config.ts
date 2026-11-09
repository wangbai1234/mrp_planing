import { defineConfig } from '@playwright/test'

export default defineConfig({
  testDir: '.',
  testMatch: 'import-confirm-permission-acceptance.spec.ts',
  workers: 1,
  retries: 0,
  reporter: 'line',
  outputDir: '../../test-kit/2026-10-15/evidence/e2e-import-confirm-permission-retest-20261106',
  use: {
    baseURL: 'http://127.0.0.1:8183',
    browserName: 'chromium',
    channel: 'chrome',
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure'
  }
})
