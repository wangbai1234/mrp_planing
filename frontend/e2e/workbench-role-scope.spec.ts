import { test, expect } from '@playwright/test'

test('独立岗位默认可见全部工厂且页面职责隔离', async ({ browser, request }) => {
  const base = 'http://127.0.0.1:8182/api/v1'
  const adminLogin = await request.post(`${base}/auth/login`, {
    data: { username: 'admin', password: process.env.E2E_ADMIN_PASSWORD }
  })
  expect(adminLogin.status()).toBe(200)
  const admin = (await adminLogin.json()).data
  const headers = { Authorization: `Bearer ${admin.token}` }
  const currentResponse = await request.get(`${base}/mrp/batches/current`, { headers })
  expect(currentResponse.status()).toBe(200)
  const currentBatch = (await currentResponse.json()).data as { id: number } | null
  expect(currentBatch?.id, 'current batch').toBeDefined()
  const batchId = currentBatch!.id
  const rolesResponse = await request.get(`${base}/roles/active`, { headers })
  expect(rolesResponse.ok()).toBeTruthy()
  const roles = (await rolesResponse.json()).data as { id: number; code: string }[]
  const accounts: { id: number; username: string; role: string }[] = []

  try {
    for (const role of ['PLANNER', 'MATERIAL']) {
      const roleId = roles.find(item => item.code === role)?.id
      expect(roleId, `${role} role`).toBeDefined()
      const username = `e2e-scope-${role.toLowerCase()}-${Date.now()}`
      const created = await request.post(`${base}/users`, {
        headers, data: { username, displayName: username, roleIds: [roleId] }
      })
      expect(created.ok()).toBeTruthy()
      accounts.push({ id: (await created.json()).data.id, username, role })
    }

    for (const account of accounts) {
      const context = await browser.newContext()
      try {
        const page = await context.newPage()
        await page.goto('/login')
        await page.getByPlaceholder('邮箱').fill(account.username)
        await page.getByPlaceholder('密码').fill(process.env.E2E_ROLE_PASSWORD || '')
        await page.getByRole('button', { name: '登录', exact: true }).click()
        const ownPath = account.role === 'PLANNER' ? '/planning' : '/shortage'
        const foreignPath = account.role === 'PLANNER' ? '/shortage' : '/planning'
        await expect(page).toHaveURL(new RegExp(`${ownPath}$`))
        await expect(page.getByRole('heading', { name: account.role === 'PLANNER' ? '排产计划' : '物料缺料' })).toBeVisible()
        await expect(page.getByText(new RegExp(`(?:排产版本|批次) #${batchId}`)).first()).toBeVisible()
        await expect(page.getByRole('menuitem', { name: account.role === 'PLANNER' ? '物料缺料' : '排产计划' })).toHaveCount(0)

        const token = await page.evaluate(() => localStorage.getItem('mrp_token'))
        expect(token).toBeTruthy()
        const actorHeaders = { Authorization: `Bearer ${token}` }
        const ownApi = account.role === 'PLANNER'
          ? `${base}/mrp/batches/${batchId}/rolling-grid?view=MACHINE_OUTPUT`
          : `${base}/mrp/batches/${batchId}/shortage/page`
        const foreignApi = account.role === 'PLANNER'
          ? `${base}/mrp/batches/${batchId}/shortage/page`
          : `${base}/mrp/batches/${batchId}/rolling-grid?view=MACHINE_OUTPUT`
        expect((await request.get(ownApi, { headers: actorHeaders })).status()).toBe(200)
        expect((await request.get(foreignApi, { headers: actorHeaders })).status()).toBe(403)
        await page.goto(foreignPath)
        await expect(page).toHaveURL(/\/forbidden/)
      } finally {
        await context.close()
      }
    }
  } finally {
    for (const account of accounts) {
      const removed = await request.delete(`${base}/users/${account.id}`, { headers })
      expect(removed.ok(), `remove ${account.role} fixture`).toBeTruthy()
    }
  }
})
