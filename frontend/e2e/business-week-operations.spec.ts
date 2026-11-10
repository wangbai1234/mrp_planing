import { expect, test } from '@playwright/test'

test('业务周期间页面新增、编辑、日志和删除独立月份', async ({ page }) => {
  test.setTimeout(90_000)
  const password = process.env.E2E_ADMIN_PASSWORD
  expect(password, 'E2E_ADMIN_PASSWORD').toBeTruthy()
  await page.goto('/login')
  await page.getByPlaceholder('邮箱').fill('admin')
  await page.getByPlaceholder('密码').fill(password!)
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page).not.toHaveURL(/\/login/)
  await page.goto('/mrp/business-weeks')
  await page.getByRole('button', { name: '新增月份' }).click()
  const dialog = page.getByRole('dialog', { name: '新增月份' })
  await dialog.getByPlaceholder('选择月份').click()
  const picker = page.locator('.el-picker-panel:visible')
  while (!(await picker.innerText()).includes('2028 年')) await picker.getByRole('button', { name: '后一年' }).click()
  await picker.getByText('一月', { exact: true }).click()
  for (let index = 0; index < 4; index++) await dialog.getByRole('button', { name: '新增一周' }).click()
  const ranges = [[1, 7], [8, 14], [15, 21], [22, 28], [29, 31]]
  const rows = dialog.locator('.el-table__body tr')
  for (let index = 0; index < ranges.length; index++) {
    const [start, end] = ranges[index]!
    const inputs = rows.nth(index).locator('input')
    await inputs.nth(0).fill(`2028-01-${String(start).padStart(2, '0')}`)
    await inputs.nth(0).press('Enter')
    await inputs.nth(1).fill(`2028-01-${String(end).padStart(2, '0')}`)
    await inputs.nth(1).press('Enter')
  }
  await dialog.getByRole('button', { name: '保存' }).click()
  await expect(page.getByText('2028-01', { exact: true })).toBeVisible()
  const month = page.locator('.month-block').filter({ hasText: '2028-01' })
  await expect(month.locator('.el-table__body tr')).toHaveCount(5)
  await month.getByRole('button', { name: '查看日志' }).click()
  await expect(page.getByRole('dialog').first()).toBeVisible()
  await page.locator('.el-dialog:visible .el-dialog__headerbtn').click()
  await month.getByRole('button', { name: '编辑' }).click()
  await expect(page.getByRole('dialog', { name: '维护 2028-01' })).toBeVisible()
  await page.getByRole('dialog', { name: '维护 2028-01' }).getByRole('button', { name: '取消' }).click()
  await month.getByRole('button', { name: '删除' }).click()
  await page.getByRole('dialog', { name: '删除业务周' }).getByRole('button', { name: '确定' }).click()
  await expect(month).toHaveCount(0)
})
