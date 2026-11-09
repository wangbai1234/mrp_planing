import { expect, test } from '@playwright/test'

const api = 'http://127.0.0.1:8182/api/v1'

test('B02/B03: missing preceding business month blocks page and direct batch creation', async ({ page, request }, testInfo) => {
  const login = await request.post(`${api}/auth/login`, {
    data: { username: 'admin', password: process.env.E2E_ADMIN_PASSWORD }
  })
  expect(login.status()).toBe(200)
  const headers = { Authorization: `Bearer ${(await login.json()).data.token}` }
  const month = '2026-10-01'
  const weeksResponse = await request.get(`${api}/mrp/business-weeks?businessMonth=${month}`, { headers })
  expect(weeksResponse.status()).toBe(200)
  const weeks = (await weeksResponse.json()).data as {
    weekCode: string; startDate: string; endDate: string; sortNo: number
  }[]
  expect(weeks.length).toBeGreaterThan(0)
  const beforeResponse = await request.get(`${api}/mrp/batches/page?page=1&pageSize=1`, { headers })
  const beforeTotal = (await beforeResponse.json()).data.total as number
  let removed = false
  try {
    const deletion = await request.delete(`${api}/mrp/business-weeks?businessMonth=${month}`, { headers })
    expect(deletion.status()).toBe(200)
    removed = true

    await page.goto('/login')
    await page.getByPlaceholder('邮箱').fill('admin')
    await page.getByPlaceholder('密码').fill(process.env.E2E_ADMIN_PASSWORD || '')
    await page.getByRole('button', { name: '登录', exact: true }).click()
    await expect(page).toHaveURL(/\/planning$/)
    await page.getByRole('button', { name: '生成排产' }).click()
    const dialog = page.getByRole('dialog', { name: '生成排产' })
    await expect(dialog.getByText(/尚未维护业务周：.*2026-10/)).toBeVisible()
    await expect(dialog.getByRole('button', { name: '确认', exact: true })).toBeDisabled()
    await page.screenshot({ path: testInfo.outputPath('b02-missing-preceding-business-week.png'), fullPage: true })

    const rejected = await request.post(`${api}/mrp/batches?factoryCode=ALL&forecastVersionId=45&inventorySnapshotId=63`, { headers })
    expect(rejected.status()).toBe(400)
    expect((await rejected.json()).error.code).toBe('MISSING_BUSINESS_WEEK')
    const afterResponse = await request.get(`${api}/mrp/batches/page?page=1&pageSize=1`, { headers })
    expect((await afterResponse.json()).data.total).toBe(beforeTotal)
  } finally {
    if (removed) {
      const restored = await request.put(`${api}/mrp/business-weeks`, {
        headers, data: { businessMonth: month, weeks: weeks.map(({ weekCode, startDate, endDate, sortNo }) => ({ weekCode, startDate, endDate, sortNo })) }
      })
      expect(restored.status()).toBe(200)
      const after = await request.get(`${api}/mrp/business-weeks?businessMonth=${month}`, { headers })
      expect((await after.json()).data.map((row: typeof weeks[number]) => [row.weekCode, row.startDate, row.endDate, row.sortNo]))
        .toEqual(weeks.map(row => [row.weekCode, row.startDate, row.endDate, row.sortNo]))
    }
  }
})
