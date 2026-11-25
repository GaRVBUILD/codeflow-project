import { expect, test } from '@playwright/test';

test('a user can sign in, create a task, and remove it', async ({ page, request }) => {
  const email = process.env.CODEFLOW_E2E_EMAIL || 'demo@codeflow.local';
  const password = process.env.CODEFLOW_E2E_PASSWORD || 'Demo1234!';
  const apiBase = process.env.CODEFLOW_API_URL || 'http://localhost:8080/api';
  const title = `Browser check ${Date.now()}`;

  await page.goto('/');
  await page.getByLabel('Email address').fill(email);
  await page.getByLabel('Password').fill(password);
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page.getByRole('heading', { name: /Good morning,/ })).toBeVisible();
  await page.getByRole('link', { name: 'My tasks' }).click();
  await expect(page.getByRole('heading', { name: 'My tasks' })).toBeVisible();

  try {
    await page.getByRole('button', { name: 'Add a task' }).click();
    await page.getByLabel('Task name').fill(title);
    await page.getByLabel('Description').fill('Created by the browser end-to-end check.');
    await page.getByLabel('Category').fill('Browser test');
    await page.getByRole('button', { name: 'Create task' }).click();

    const taskRow = page.locator('.task-row').filter({ hasText: title });
    await expect(taskRow).toBeVisible();
    await taskRow.getByRole('button', { name: 'Delete' }).click();
    await expect(page.getByText(title, { exact: true })).toHaveCount(0);
  } finally {
    const token = await page.evaluate(() => localStorage.getItem('cf_token'));
    if (token) {
      const response = await request.get(`${apiBase}/tasks`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      if (response.ok()) {
        const tasks = await response.json();
        const createdTask = tasks.find((task) => task.title === title);
        if (createdTask) {
          await request.delete(`${apiBase}/tasks/${createdTask.id}`, {
            headers: { Authorization: `Bearer ${token}` },
          });
        }
      }
    }
  }
});
