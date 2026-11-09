import { expect, test } from '@playwright/test'

test('业务日志、标签标题和专用图标', async ({ page }, testInfo) => {
  await page.addInitScript(() => {
    localStorage.setItem('mrp_token', 'browser-test-token')
    localStorage.setItem('mrp_user_info', JSON.stringify({ username: 'tester', displayName: '测试员', permissions: ['mrp_config:view'] }))
  })

  await page.route('**/api/v1/**', async route => {
    const url = new URL(route.request().url())
    let data: unknown = null
    if (url.pathname.endsWith('/auth/me')) data = { permissions: ['mrp_config:view'], roles: ['BUSINESS'] }
    if (url.pathname.endsWith('/mrp/material-policies/page')) {
      data = { items: [{ id: 1, materialCode: 'M001', supplyMode: 'PURCHASED', explosionPolicy: 'STOP' }], total: 1, page: 1, pageSize: 20 }
    }
    if (url.pathname.endsWith('/audit-logs')) {
      const second = url.searchParams.get('page') === '2'
      data = { items: [{ id: second ? 2 : 1, action: second ? 'CREATE' : 'EDIT', resource_type: 'material_policy', resource_id: 'M001',
        operator_id: 7, operator_name: '张三', operator_avatar_url: second ? '/favicon.png' : null,
        created_at: '2026-11-09T10:22:08',
        before_value: second ? null : JSON.stringify({ materialCode: 'M001', supplyMode: 'SELF_MADE', explosionPolicy: 'STOP' }),
        after_value: JSON.stringify({ materialCode: 'M001', supplyMode: second ? 'OUTSOURCED' : 'PURCHASED', explosionPolicy: 'STOP' }) }],
        total: 21, page: second ? 2 : 1, pageSize: 20 }
    }
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ success: true, data }) })
  })

  await page.goto('/mrp/material-policies')
  await expect(page).toHaveTitle('物料策略 | MRP 计划协同')
  await expect(page.locator('link[rel="icon"]')).toHaveAttribute('href', '/favicon.png')
  const icon = await page.request.get('/favicon.png')
  expect(icon.ok()).toBeTruthy()
  expect(icon.headers()['content-type']).toContain('image/png')

  await page.getByRole('button', { name: '查看日志' }).click()
  const dialog = page.getByRole('dialog', { name: /查看日志/ })
  await expect(dialog.getByText('张三')).toBeVisible()
  await expect(dialog.locator('.audit-actor .el-avatar')).toHaveText('张')
  await expect(dialog.getByText('编辑了物料策略')).toBeVisible()
  await expect(dialog.getByText('EDIT')).toHaveCount(0)
  await expect(dialog.getByText('供应方式')).toBeVisible()
  await expect(dialog.getByText('自制')).toBeVisible()
  await expect(dialog.getByText('外购')).toBeVisible()
  await expect(dialog.getByText('explosionPolicy')).toHaveCount(0)
  await page.waitForTimeout(350)
  await page.screenshot({ path: testInfo.outputPath('audit-log-desktop.png'), fullPage: true })

  await page.setViewportSize({ width: 390, height: 844 })
  await expect(dialog).toBeVisible()
  const bounds = await dialog.boundingBox()
  expect(bounds).not.toBeNull()
  expect(bounds!.x).toBeGreaterThanOrEqual(0)
  expect(bounds!.x + bounds!.width).toBeLessThanOrEqual(390)
  expect(await dialog.evaluate(element => element.scrollWidth)).toBeLessThanOrEqual(390)
  await page.screenshot({ path: testInfo.outputPath('audit-log-mobile.png'), fullPage: true })

  await dialog.getByRole('button', { name: '下一页' }).click()
  await expect(dialog.getByText('委外')).toBeVisible()
  await expect(dialog.getByText('新增了物料策略')).toBeVisible()
  await expect(dialog.locator('.audit-arrow')).toHaveCount(0)
  await expect(dialog.locator('.audit-actor .el-avatar img')).toHaveAttribute('src', '/favicon.png')
})
