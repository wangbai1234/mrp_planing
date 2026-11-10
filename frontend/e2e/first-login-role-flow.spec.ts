import { expect, test } from '@playwright/test'

const base = process.env.E2E_BASE_URL
const api = 'http://127.0.0.1:8182/api/v1'

test('首次改密后保留登录状态并按岗位进入工作台', async ({ browser, request }) => {
  test.skip(base !== 'http://127.0.0.1:5192', '只在隔离验收前端运行')
  test.setTimeout(90_000)
  const adminLogin = await request.post(`${api}/auth/login`, {
    data: { username: 'admin', password: process.env.E2E_ADMIN_PASSWORD }
  })
  expect(adminLogin.status()).toBe(200)
  const adminToken = (await adminLogin.json()).data.token as string
  const headers = { Authorization: `Bearer ${adminToken}` }
  const rolesResponse = await request.get(`${api}/roles/active`, { headers })
  expect(rolesResponse.status()).toBe(200)
  const roles = (await rolesResponse.json()).data as { id: number; code: string }[]
  const publishedResponse = await request.get(`${api}/mrp/batches/page?page=1&pageSize=1&status=PUBLISHED`, { headers })
  expect(publishedResponse.status()).toBe(200)
  const publishedBatchId = (await publishedResponse.json()).data.items[0]?.id as number | undefined
  test.skip(!publishedBatchId, '需要已发布批次验证岗位 API')

  for (const role of ['PLANNER', 'MATERIAL']) {
    const roleId = roles.find(item => item.code === role)?.id
    expect(roleId).toBeDefined()
    const username = `first-login-${role.toLowerCase()}-${Date.now()}`
    const created = await request.post(`${api}/users`, {
      headers, data: { username, displayName: username, roleIds: [roleId] }
    })
    expect(created.status()).toBe(200)
    const fixture = (await created.json()).data as {
      user: { id: number }; temporaryPassword: string
    }
    const context = await browser.newContext()
    try {
      const page = await context.newPage()
      await page.goto(`${base}/login`)
      await page.getByPlaceholder('邮箱').fill(username)
      await page.getByPlaceholder('密码').fill(fixture.temporaryPassword)
      await page.getByRole('button', { name: '登录', exact: true }).click()
      const change = page.getByRole('dialog', { name: '首次登录修改密码' })
      await expect(change).toBeVisible()
      const newPassword = `Audit${Date.now()}a1`
      await change.locator('input[type="password"]').nth(0).fill(newPassword)
      await change.locator('input[type="password"]').nth(1).fill(newPassword)
      await change.getByRole('button', { name: '确认', exact: true }).click()

      const ownPage = role === 'PLANNER' ? '/planning' : '/shortage'
      const foreignPage = role === 'PLANNER' ? '/shortage' : '/planning'
      await expect(page).toHaveURL(new RegExp(`${ownPage}$`))
      const token = await page.evaluate(() => localStorage.getItem('mrp_token'))
      expect(token).toBeTruthy()
      const actor = { Authorization: `Bearer ${token}` }
      const ownApi = role === 'PLANNER'
        ? `${api}/mrp/batches/${publishedBatchId}/rolling-grid?view=MACHINE_OUTPUT`
        : `${api}/mrp/batches/${publishedBatchId}/shortage/page`
      const foreignApi = role === 'PLANNER'
        ? `${api}/mrp/batches/${publishedBatchId}/shortage/page`
        : `${api}/mrp/batches/${publishedBatchId}/rolling-grid?view=MACHINE_OUTPUT`
      expect((await request.get(ownApi, { headers: actor })).status()).toBe(200)
      expect((await request.get(foreignApi, { headers: actor })).status()).toBe(403)
      await page.goto(`${base}${foreignPage}`)
      await expect(page).toHaveURL(/\/forbidden\?from=/)
    } finally {
      await context.close()
      const deleted = await request.delete(`${api}/users/${fixture.user.id}`, { headers })
      expect(deleted.status()).toBe(200)
    }
  }
})
