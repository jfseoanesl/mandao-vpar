import { Browser, BrowserContext, Page, expect, test } from '@playwright/test';

/**
 * Flujo completo del MVP (plan · Verificación): cliente pide → mandadero con GPS simulado acepta →
 * OTP de inicio → evidencia → OTP de entrega → cliente confirma y califica → admin ve los KPIs.
 * Usa los datos DEMO (SEED_DEMO=true, contraseña Mandao2026).
 */
const PASSWORD = 'Mandao2026';
const CLIENT = '3002000001';
const COURIER = '3001000002'; // Luz Daza, ubicada en el centro de Valledupar
const ADMIN = '3000000000';
const CENTER = { latitude: 10.4631, longitude: -73.2532 };

async function session(browser: Browser, phone: string, gps = false): Promise<{ ctx: BrowserContext; page: Page }> {
  const ctx = await browser.newContext(gps ? { geolocation: CENTER, permissions: ['geolocation'] } : {});
  const page = await ctx.newPage();
  await page.goto('/entrar');
  await page.locator('input[name=phone]').fill(phone);
  await page.locator('input[name=password]').fill(PASSWORD);
  await page.getByRole('button', { name: 'Entrar' }).click();
  await page.waitForURL(url => !url.pathname.startsWith('/entrar'));
  return { ctx, page };
}

test('mandado de punta a punta con evidencias y OTP', async ({ browser }) => {
  const marker = `E2E ${Date.now()}: lleva estas llaves a la portería`;

  // 0. Limpieza: el admin cancela mandados E2E que hayan quedado abiertos por corridas fallidas
  //    (un mandadero solo puede tener un mandado activo).
  const admin = await session(browser, ADMIN);
  const { accessToken: adminToken } = await (await admin.page.request.post('/api/auth/refresh')).json();
  const auth = { Authorization: `Bearer ${adminToken}` };
  const leftovers = await (await admin.page.request.get('/api/admin/errands', { headers: auth })).json();
  for (const e of leftovers) {
    if (e.description?.startsWith('E2E ') && !['COMPLETED', 'RATED', 'CANCELLED', 'FAILED'].includes(e.status)) {
      await admin.page.request.post(`/api/admin/errands/${e.id}/cancel`, { headers: auth, data: { reason: 'Limpieza E2E' } });
    }
  }

  // 1. El mandadero se conecta (GPS simulado en el centro).
  const courier = await session(browser, COURIER, true);
  await expect(courier.page).toHaveURL(/\/mandadero/);
  const connect = courier.page.getByRole('button', { name: 'Conectarme', exact: true });
  const disconnect = courier.page.getByRole('button', { name: 'Desconectarme', exact: true });
  await expect(connect.or(disconnect)).toBeVisible();
  if (await connect.isVisible()) await connect.click();
  await expect(disconnect).toBeVisible();

  // 2. El cliente pide un envío marcando dos puntos en el mapa y ve la cotización.
  const client = await session(browser, CLIENT);
  await client.page.goto('/app/nuevo/ENVIO');
  await client.page.locator('textarea[name=description]').fill(marker);
  const map = client.page.locator('app-map .map').first();
  // Regresión: el mapa base debe cargar (CSP connect-src + service worker).
  await expect(map.locator('img.leaflet-tile-loaded').first()).toBeVisible({ timeout: 20_000 });
  await map.click({ position: { x: 120, y: 120 } });
  await map.click({ position: { x: 220, y: 200 } });
  await expect(client.page.locator('.kv.total')).toContainText('$', { timeout: 20_000 });
  await client.page.getByRole('button', { name: 'Pedir mandado' }).click();
  await client.page.waitForURL(/\/app\/mandados\/[0-9a-f-]{36}$/);

  // 3. El mandadero ve la oportunidad y la acepta (primero gana).
  const card = courier.page.locator('app-opportunity-card', { hasText: marker });
  await expect(async () => {
    await courier.page.getByRole('button', { name: 'Actualizar' }).click();
    await expect(card).toBeVisible({ timeout: 3_000 });
  }).toPass({ timeout: 60_000 });
  await card.getByRole('button', { name: 'Aceptar mandado' }).click();
  await courier.page.waitForURL(/\/mandadero\/mandados\//);
  await courier.page.getByRole('button', { name: 'Voy en camino' }).click();

  // 4. OTP de inicio: el cliente lo ve, el mandadero lo digita.
  await client.page.reload();
  const startOtp = (await client.page.locator('.otp-box .code').first().innerText()).trim();
  expect(startOtp).toMatch(/^\d{4}$/);
  await courier.page.locator('input[name=startOtp]').fill(startOtp);
  await courier.page.getByRole('button', { name: 'Iniciar mandado' }).click();

  // 5. Evidencia (nota) y OTP de entrega.
  await courier.page.getByRole('button', { name: '📝 Nota' }).click();
  await courier.page.locator('textarea[name=note]').fill('Entregado en portería a Don José.');
  await courier.page.getByRole('button', { name: 'Guardar nota' }).click();
  await expect(courier.page.locator('app-evidence-list')).toContainText('Don José');

  await client.page.reload();
  await expect(client.page.locator('app-evidence-list')).toContainText('Don José');
  const endOtp = (await client.page.locator('.otp-box', { hasText: 'entrega' }).locator('.code').innerText()).trim();
  await courier.page.locator('input[name=endOtp]').fill(endOtp);
  await courier.page.getByRole('button', { name: 'Confirmar entrega' }).click();
  await expect(courier.page.getByText('Entregado. Esperando confirmación')).toBeVisible();

  // 6. El cliente confirma y califica.
  await client.page.reload();
  await client.page.getByRole('button', { name: 'Sí, confirmar mandado' }).click();
  await client.page.getByRole('button', { name: '5 estrellas' }).click();
  await client.page.getByRole('button', { name: /Rápido/ }).click();
  await client.page.getByRole('button', { name: 'Enviar calificación' }).click();
  await expect(client.page.getByText('Tu calificación:')).toBeVisible();

  // 7. El admin ve el mandado y los KPIs del piloto.
  await admin.page.goto('/admin');
  await expect(admin.page.getByText('Validación del piloto')).toBeVisible();
  await expect(admin.page.getByText('Mandados por día').first()).toBeVisible();
  await admin.page.goto('/admin/mandados');
  await expect(admin.page.locator('table')).toContainText(marker.slice(0, 20));

  // 8. Autorización (anti-IDOR / por rol): el cliente no entra al panel admin ni a la API admin.
  //    Va en este mismo test para no superar el límite de 5 logins/min por IP.
  const { accessToken } = await (await client.page.request.post('/api/auth/refresh')).json();
  const res = await client.page.request.get('/api/admin/kpis', { headers: { Authorization: `Bearer ${accessToken}` } });
  expect(res.status()).toBe(403);
  await client.page.goto('/admin');
  await expect(client.page).not.toHaveURL(/\/admin/);

  // 9. AC-001-13/14: un usuario creado por el admin (administrador de empresa) debe aceptar los términos al entrar.
  const bossPhone = '3' + String(Date.now()).slice(-9);
  const created = await admin.page.request.post('/api/admin/companies', {
    headers: auth,
    data: { name: `Empresa E2E ${bossPhone}`, plan: 'EMPRESAS', adminFullName: 'Laura Prueba', adminPhone: bossPhone, adminPassword: PASSWORD },
  });
  expect(created.status()).toBe(201);
  const boss = await session(browser, bossPhone);
  await expect(boss.page).toHaveURL(/\/aceptar-terminos/);
  await expect(boss.page.getByRole('button', { name: 'Aceptar y continuar' })).toBeDisabled();
  await boss.page.locator('input[name=accept]').check();
  await boss.page.getByRole('button', { name: 'Aceptar y continuar' }).click();
  await expect(boss.page).toHaveURL(/\/app/);
  await expect(boss.page.getByText('¿Qué necesitas hacer hoy?')).toBeVisible();

  await Promise.all([courier.ctx.close(), client.ctx.close(), admin.ctx.close(), boss.ctx.close()]);
});


test('términos y condiciones visibles y enlazados desde el registro', async ({ page }) => {
  await page.goto('/registro');
  await page.getByRole('link', { name: 'términos y condiciones' }).first().click();
  const terms = page.url().includes('/terminos') ? page : await page.context().waitForEvent('page');
  await expect(terms.getByRole('heading', { name: 'Términos y condiciones', level: 1 })).toBeVisible();
  await expect(terms.getByText(/Versión \d+\.\d+/)).toBeVisible();
  await terms.getByRole('link', { name: '7. Cancelaciones y mandados no completados' }).click();
  await expect(terms).toHaveURL(/#cancelaciones$/);
});
