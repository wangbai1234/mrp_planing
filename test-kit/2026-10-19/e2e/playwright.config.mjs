import { defineConfig } from '../../../frontend/node_modules/@playwright/test/index.mjs';
export default defineConfig({
  testDir: '.', testMatch: '*.spec.mjs', workers: 1, retries: 0,
  reporter: [['list'], ['html', { outputFolder: '../evidence/browser-report', open: 'never' }]],
  outputDir: '../evidence/browser-results',
  use: { baseURL: 'http://127.0.0.1:8180', browserName: 'chromium', channel: 'chrome', viewport: { width: 1440, height: 1000 }, trace: 'retain-on-failure', screenshot: 'only-on-failure' }
});
