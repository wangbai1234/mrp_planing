import { defineConfig } from '@playwright/test'

export default defineConfig({
  testDir: '.',
  testMatch: 'role-configuration-acceptance.spec.ts',
  workers: 1,
  retries: 0,
  reporter: 'line',
  outputDir: '../../test-kit/2026-10-15/evidence/e2e-role-configuration-20261105',
  use: {
    baseURL: 'http://127.0.0.1:8183',
    browserName: 'chromium',
    channel: 'chrome',
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure'
  }
})
