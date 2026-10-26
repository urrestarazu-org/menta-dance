// Representative journeys from docs/ACCESSIBILITY.md, scanned with axe-core.
// Requires the stack running with the `demo` profile (public seed data only).
import { expect, test } from '@playwright/test';
import { expectNoBlockingViolations } from './axe.js';

// Demo student from the public seed data (docs/26-LOCAL-DEV-SETUP-HOWTO.md).
// These are documented demo-profile defaults, not real credentials.
const DEMO_STUDENT = { email: 'alumno@menta.dance', password: 'Alumno123!' };

async function scan(page, path, name) {
  await page.goto(path);
  await expect(page.locator('main')).toBeVisible();
  await expectNoBlockingViolations(page, name);
}

// First link on a page whose href matches the pattern (demo ids are generated).
async function firstHref(page, pattern) {
  const hrefs = await page.locator('a[href]').evaluateAll(
    (links) => links.map((a) => a.getAttribute('href')),
  );
  const found = hrefs.find((href) => pattern.test(href));
  expect(found, `no link matching ${pattern} (is the demo profile active?)`).toBeTruthy();
  return found;
}

test.describe('public pages', () => {
  test('home', async ({ page }) => {
    await scan(page, '/', 'home');
  });

  test('classes catalog', async ({ page }) => {
    await scan(page, '/classes', 'classes');
  });

  test('class detail', async ({ page }) => {
    await page.goto('/classes');
    await scan(page, await firstHref(page, /^\/classes\/[^/]+$/), 'class detail');
  });

  test('course detail', async ({ page }) => {
    await page.goto('/');
    await scan(page, await firstHref(page, /^\/courses\/[^/]+$/), 'course detail');
  });

  test('plans', async ({ page }) => {
    await scan(page, '/plans', 'plans');
  });

  test('not found', async ({ page }) => {
    const response = await page.goto('/classes/not-a-uuid');
    expect(response.status()).toBe(404);
    await expect(page.locator('main')).toBeVisible();
    await expectNoBlockingViolations(page, 'not found');
  });
});

test.describe('authentication pages', () => {
  test('login', async ({ page }) => {
    await scan(page, '/login', 'login');
  });

  test('login error state', async ({ page }) => {
    await page.goto('/login');
    // Unknown account on purpose: never lock or rate-limit a real demo user.
    await page.getByLabel('Email', { exact: true }).fill('no-such-user@example.invalid');
    await page.getByLabel('Contraseña', { exact: true }).fill('Wrong-password-1');
    await page.getByRole('button', { name: 'Iniciar Sesión' }).click();
    await expect(page.getByRole('alert')).toBeVisible();
    await expectNoBlockingViolations(page, 'login error');
  });

  test('register', async ({ page }) => {
    await scan(page, '/registro', 'register');
  });

  test('register validation errors', async ({ page }) => {
    await page.goto('/registro');
    // Fake personal data: only the email, birth date and password are wrong on purpose.
    await page.getByLabel('Nombre', { exact: true }).fill('Ana');
    await page.getByLabel('Apellido', { exact: true }).fill('Prueba');
    await page.getByLabel('Email', { exact: true }).fill('not-an-email');
    await page.getByLabel('Teléfono', { exact: true }).fill('+54 9 11 0000 0000');
    await page.getByLabel('Fecha de nacimiento (opcional)', { exact: true }).fill('2999-01-01');
    await page.getByLabel('Contraseña', { exact: true }).fill('short');
    await page.getByRole('button', { name: 'Crear cuenta' }).click();
    await expect(page.locator('[aria-invalid="true"]').first()).toBeVisible();
    // The birth date keeps its format hint and gains the error in the same description (WCAG 3.3.1, 1.3.1).
    await expect(page.locator('#birthDate[aria-invalid="true"]')).toHaveAttribute('aria-describedby', 'birthDate-help');
    await expect(page.locator('#birthDate-help')).toContainText('AAAA-MM-DD');
    await expectNoBlockingViolations(page, 'register errors');
  });

  test('register keeps its input purposes', async ({ page }) => {
    await page.goto('/registro');
    // WCAG 1.3.5: personal-data fields declare what they collect.
    await expect(page.getByLabel('Nombre', { exact: true })).toHaveAttribute('autocomplete', 'given-name');
    await expect(page.getByLabel('Apellido', { exact: true })).toHaveAttribute('autocomplete', 'family-name');
    await expect(page.getByLabel('Teléfono', { exact: true })).toHaveAttribute('autocomplete', 'tel');
    await expect(page.getByLabel('Fecha de nacimiento (opcional)', { exact: true })).toHaveAttribute('autocomplete', 'bday');
    await expectNoBlockingViolations(page, 'register purposes');
  });

  test('password recovery', async ({ page }) => {
    await scan(page, '/recuperar-contrasena', 'password recovery');
  });

  test('resend activation', async ({ page }) => {
    await scan(page, '/reenviar-activacion', 'resend activation');
  });
});

// Legal and contact pages (#650): static text inside the shared layout.
test.describe('legal pages', () => {
  const LEGAL_PAGES = [
    ['/terminos-condiciones', 'terms'],
    ['/politica-privacidad', 'privacy policy'],
    ['/politica-cookies', 'cookies policy'],
    ['/politica-devolucion', 'refund policy'],
    ['/propiedad-intelectual', 'intellectual property'],
    ['/accesibilidad', 'accessibility statement'],
    ['/contacto', 'contact'],
  ];

  for (const [path, name] of LEGAL_PAGES) {
    test(name, async ({ page }) => {
      await scan(page, path, name);
    });
  }
});

test.describe('signed-in student', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/login');
    await page.getByLabel('Email', { exact: true }).fill(DEMO_STUDENT.email);
    await page.getByLabel('Contraseña', { exact: true }).fill(DEMO_STUDENT.password);
    await page.getByRole('button', { name: 'Iniciar Sesión' }).click();
    await expect(page).toHaveURL(/\/dashboard$/);
  });

  test('dashboard', async ({ page }) => {
    await expect(page.locator('main')).toBeVisible();
    await expectNoBlockingViolations(page, 'dashboard');
  });

  test('lesson', async ({ page }) => {
    await page.goto('/');
    await page.goto(await firstHref(page, /^\/courses\/[^/]+$/));
    await page.goto(await firstHref(page, /^\/courses\/[^/]+\/lessons\/[^/]+$/));
    await expect(page.locator('main h1')).toBeVisible();
    await expectNoBlockingViolations(page, 'lesson');
  });
});
