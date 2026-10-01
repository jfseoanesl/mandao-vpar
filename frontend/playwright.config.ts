import { defineConfig, devices } from '@playwright/test';

/**
 * E2E contra el stack levantado (docker compose con SEED_DEMO=true).
 *   BASE_URL=http://localhost:8088 npx playwright test
 */
export default defineConfig({
  testDir: './e2e',
  timeout: 120_000,
  expect: { timeout: 15_000 },
  fullyParallel: false,
  workers: 1,
  retries: process.env['CI'] ? 1 : 0,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: process.env['BASE_URL'] ?? 'http://localhost:8088',
    // Para correr contra https://<IP-del-PC>:8443 (CA local de Caddy): IGNORE_HTTPS_ERRORS=1
    ignoreHTTPSErrors: !!process.env['IGNORE_HTTPS_ERRORS'],
    locale: 'es-CO',
    timezoneId: 'America/Bogota',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'mobile-chrome', use: { ...devices['Pixel 7'] } }],
});
