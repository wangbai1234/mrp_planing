import { expect, test } from '@playwright/test'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

const base = 'http://127.0.0.1:5192'
const evidence = resolve('../test-kit/2026-10-15/evidence/runs/2026-11-02-positive-chain')

test('经营计划和库存从页面预览确认到正式版本', async ({ page, request }) => {
  const auth = await request.post(base + '/api/v1/auth/login', {
    data: { username: 'admin', password: 'admin123' },
  })
  expect(auth.status()).toBe(200)
  const user = (await auth.json()).data as {
    token: string; userId: number; username: string; displayName: string;
    roles: string[]; permissions: string[]
  }
  const failures: string[] = []
  page.on('pageerror', error => failures.push(error.message))
  page.on('response', response => {
    if (response.url().includes('/api/v1/') && response.status() >= 500) failures.push(response.url())
  })
  await page.goto(base + '/login')
  await page.evaluate(data => {
    localStorage.setItem('mrp_token', data.token)
    localStorage.setItem('mrp_user_info', JSON.stringify({
      id: data.userId, username: data.username, displayName: data.displayName,
      roles: data.roles, permissions: data.permissions,
    }))
  }, user)

  for (const sample of [
    { route: '/forecast', label: '经营计划批量导入', file: 'forecast-current.xlsx', upload: '/forecast-imports' },
    { route: '/inventory', label: '库存批量导入', file: 'inventory-current.xlsx', upload: '/inventory-imports/items' },
  ]) {
    await page.goto(base + sample.route)
    await page.getByRole('button', { name: '导入', exact: true }).click()
    const dialog = page.getByRole('dialog').filter({ hasText: sample.label })
    const uploadResponse = page.waitForResponse(response => response.url().includes(sample.upload)
      && response.request().method() === 'POST' && !response.url().includes('/confirm'))
    await dialog.locator('input[type=file]').setInputFiles({
      name: sample.file,
      mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
      buffer: readFileSync(resolve(evidence, sample.file)),
    })
    const preview = await uploadResponse
    expect(preview.status()).toBe(202)
    const staging = (await preview.json()).data as { taskId: number; errorRows: number; successRows: number }
    expect(staging.errorRows).toBe(0)
    expect(staging.successRows).toBeGreaterThan(0)
    await expect(dialog.getByRole('button', { name: '开始导入' })).toBeEnabled()
    const confirmResponse = page.waitForResponse(response => response.url().includes(sample.upload + '/' + staging.taskId + '/confirm')
      && response.request().method() === 'POST')
    await dialog.getByRole('button', { name: '开始导入' }).click()
    const confirmed = await confirmResponse
    expect(confirmed.status()).toBe(200)
    expect((await confirmed.json()).data).toBeTruthy()
    await expect(dialog).not.toBeVisible()
  }
  expect(failures).toEqual([])
})
