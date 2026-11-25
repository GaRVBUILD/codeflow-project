import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  reporter: 'list',
  use: {
    baseURL: process.env.CODEFLOW_WEB_URL || 'http://localhost:5173',
    trace: 'retain-on-failure',
    ...devices['Desktop Chrome'],
  },
});
