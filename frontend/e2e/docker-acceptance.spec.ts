import { test, expect } from '@playwright/test'

test('隔离 Docker 已发布整机与原材料缺料且负荷文字不越界', async ({ page }) => {
  await page.goto('/login')
  await page.getByPlaceholder('邮箱').fill('admin')
  const password = process.env.MRP_ACCEPTANCE_ADMIN_PASSWORD
  if (!password) throw new Error('MRP_ACCEPTANCE_ADMIN_PASSWORD is required')
  await page.getByPlaceholder('密码').fill(password)
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await page.waitForURL(url => !url.pathname.includes('/login'))

  await page.goto('/planning')
  await expect(page.getByText('已发布', { exact: true })).toBeVisible()
  await expect(page.getByText('排产版本 #2', { exact: false })).toBeVisible()
  await expect(page.getByText('6839AA000365', { exact: true })).toHaveCount(2)
  const cells = page.locator('.cell-load')
  await expect(cells.first()).toBeVisible()
  for (const width of [1440, 1920]) {
    await page.setViewportSize({ width, height: width === 1440 ? 900 : 1080 })
    const overflow = await cells.evaluateAll(elements => elements.slice(0, 12).flatMap((button, index) => {
      const cell = button.getBoundingClientRect()
      return [...button.querySelectorAll('.cell-line, small')].flatMap(node => {
        const text = node.getBoundingClientRect()
        return text.left < cell.left - 1 || text.right > cell.right + 1
          || text.top < cell.top - 1 || text.bottom > cell.bottom + 1
          || node.scrollWidth > node.clientWidth + 1 ? [`${index}: ${node.textContent}`] : []
      })
    }))
    expect(overflow, `${width}px 负荷格越界`).toEqual([])
  }

  await page.goto('/shortage')
  await expect(page.getByRole('heading', { name: '物料缺料' })).toBeVisible()
  await expect(page.getByText('批次 #2 · run #2', { exact: false })).toBeVisible()
  await expect(page.getByRole('button', { name: '受影响整机' })).toHaveCount(6)
  for (const code of ['11520471', '25900077', '43111794']) {
    await expect(page.getByRole('cell', { name: code, exact: true })).toHaveCount(2)
  }
})
