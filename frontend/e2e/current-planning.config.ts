import { defineConfig } from '@playwright/test'

export default defineConfig({
  testDir: '.',
  testMatch: ['two-workbench-compact.spec.ts', 'workbench-role-scope.spec.ts', 'current-bom-role-chain.spec.ts', 'current-import-boundary.spec.ts', 'current-import-positive.spec.ts', 'current-import-first-version.spec.ts', 'current-history-scope.spec.ts', 'current-business-week-gate.spec.ts', 'current-batch-source-gate.spec.ts', 'current-linked-transfer.spec.ts', 'current-worker-failure-ui.spec.ts'],
  workers: 1,
  retries: 0,
  reporter: 'line',
  outputDir: '../../test-kit/2026-10-15/evidence/e2e-current-2026-11-02',
  use: {
    baseURL: 'http://127.0.0.1:5192',
    browserName: 'chromium',
    channel: 'chrome',
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure'
  }
})
