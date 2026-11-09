import { expect, test } from '@playwright/test'
import { execFileSync } from 'node:child_process'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

const base = 'http://127.0.0.1:5192'
const generator = resolve('../test-kit/2026-10-15/tools/generate_current_ui_imports.py')

test('页面首次确认创建经营计划版本和统一库存快照', async ({ page, request }) => {
  const generated = JSON.parse(execFileSync('python3', [generator], { encoding: 'utf8' })) as {
    forecast: string; inventory: string; forecastQty: number; inventoryQty: number
  }
  const auth = await request.post(base + '/api/v1/auth/login', {
    data: { username: 'admin', password: 'admin123' },
  })
  expect(auth.status()).toBe(200)
  const user = (await auth.json()).data as {
    token: string; userId: number; username: string; displayName: string;
    roles: string[]; permissions: string[]
  }
  const headers = { Authorization: 'Bearer ' + user.token }
  const previousForecast = await request.get(base + '/api/v1/forecast-versions/current', { headers })
  const previousInventory = await request.get(base + '/api/v1/inventory-snapshots/page?page=1&pageSize=3', { headers })
  expect(previousForecast.status()).toBe(200)
  expect(previousInventory.status()).toBe(200)
  const forecastBefore = (await previousForecast.json()).data as { id: number }
  const inventoryBefore = (await previousInventory.json()).data as { total: number }
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
    { route: '/forecast', label: '经营计划批量导入', file: generated.forecast, upload: '/forecast-imports' },
    { route: '/inventory', label: '库存批量导入', file: generated.inventory, upload: '/inventory-imports/items' },
  ]) {
    await page.goto(base + sample.route)
    await page.getByRole('button', { name: '导入', exact: true }).click()
    const dialog = page.getByRole('dialog').filter({ hasText: sample.label })
    const uploadResponse = page.waitForResponse(response => response.url().endsWith(sample.upload)
      && response.request().method() === 'POST')
    await dialog.locator('input[type=file]').setInputFiles({
      name: sample.file.split('/').at(-1) ?? 'import.xlsx',
      mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
      buffer: readFileSync(sample.file),
    })
    const preview = await uploadResponse
    expect(preview.status()).toBe(202)
    const staging = (await preview.json()).data as { taskId: number; errorRows: number; successRows: number }
    expect(staging.errorRows).toBe(0)
    expect(staging.successRows).toBeGreaterThan(0)
    const confirmResponse = page.waitForResponse(response => response.url().includes(
      sample.upload + '/' + staging.taskId + '/confirm') && response.request().method() === 'POST')
    await dialog.getByRole('button', { name: '开始导入' }).click()
    expect((await confirmResponse).status()).toBe(200)
    await expect(dialog).not.toBeVisible()
  }

  const forecastAfter = await request.get(base + '/api/v1/forecast-versions/current', { headers })
  const inventoryAfter = await request.get(base + '/api/v1/inventory-snapshots/page?page=1&pageSize=3', { headers })
  const current = (await forecastAfter.json()).data as { id: number }
  const snapshots = (await inventoryAfter.json()).data as { total: number; items: { id: number }[] }
  expect(current.id).not.toBe(forecastBefore.id)
  expect(snapshots.total).toBe(inventoryBefore.total + 1)
  const details = await request.get(base + '/api/v1/forecast-versions/' + current.id, { headers })
  const inventoryDetails = await request.get(base + '/api/v1/inventory-snapshots/' + snapshots.items[0].id, { headers })
  expect(details.status()).toBe(200)
  expect(inventoryDetails.status()).toBe(200)
  const forecast = (await details.json()).data as { recognizedMonths: string[]; items: unknown[] }
  const inventory = (await inventoryDetails.json()).data as { items: unknown[] }
  expect(forecast.recognizedMonths).toHaveLength(6)
  expect(forecast.items).toHaveLength(2)
  expect(inventory.items.length).toBeGreaterThan(0)
  expect(failures).toEqual([])
})
