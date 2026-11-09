import { expect, test } from '@playwright/test'
import { execFileSync } from 'node:child_process'

const base = 'http://localhost:5191'

test('隔离库浏览器导入：上传预览、确认与历史版本', async ({ page, request }) => {
  const errors: string[] = []
  page.on('pageerror', error => errors.push(error.message))
  page.on('response', response => { if (response.url().includes('/api/v1/') && response.status() >= 500) errors.push(response.url()) })
  const auth = await request.post(base + '/api/v1/auth/login', { data: { username: 'admin', password: 'admin123' } })
  expect(auth.ok(), await auth.text()).toBeTruthy()
  const user = (await auth.json()).data as { token: string; userId: number; username: string; displayName: string; roles: string[]; permissions: string[] }
  const headers = { Authorization: 'Bearer ' + user.token }
  const before = (await (await request.get(base + '/api/v1/forecast-workspace/current', { headers })).json()).data as { id: number; versionNo: number }
  const material = 'FORECAST-API-1'
  const script = `import io,openpyxl,sys
w=openpyxl.Workbook(); s=w.active
s.append(['工厂','形态','料号','模具','状态','2026-10','2026-11','2026-12','2027-01','2027-02','2027-03','fcst-total'])
s.append(['填写说明'])
s.append(['永惠','整机',sys.argv[1],'验收','正常',11,12,13,14,15,16,81])
b=io.BytesIO(); w.save(b); sys.stdout.buffer.write(b.getvalue())`
  const file = execFileSync('python3', ['-c', script, material])
  await page.goto(base + '/login')
  await page.evaluate(data => {
    localStorage.setItem('mrp_token', data.token)
    localStorage.setItem('mrp_user_info', JSON.stringify({ id: data.userId, username: data.username, displayName: data.displayName, roles: data.roles, permissions: data.permissions }))
  }, user)
  await page.goto(base + '/forecast')
  await page.getByRole('button', { name: '导入', exact: true }).click()
  const dialog = page.getByRole('dialog').filter({ hasText: '经营计划批量导入' })
  await dialog.locator('input[type=file]').setInputFiles({ name: 'forecast-uat.xlsx', mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', buffer: file })
  await expect(dialog.getByText(material).first()).toBeVisible()
  await expect(dialog.getByRole('button', { name: '开始导入' })).toBeEnabled()
  const staging = await request.get(base + '/api/v1/forecast-workspace/draft', { headers })
  expect((await staging.json()).data).toEqual({})
  const beforeConfirm = (await (await request.get(base + '/api/v1/forecast-workspace/current', { headers })).json()).data as { id: number }
  expect(beforeConfirm.id).toBe(before.id)
  await dialog.getByRole('button', { name: '开始导入' }).click()
  await expect(page.locator('.forecast-version-heading').getByText('生效版本 V' + (before.versionNo + 1))).toBeVisible()
  const now = (await (await request.get(base + '/api/v1/forecast-workspace/current', { headers })).json()).data as { id: number; versionNo: number }
  expect(now.versionNo).toBe(before.versionNo + 1)
  const detail = (await (await request.get(base + '/api/v1/forecast-versions/' + now.id + '?page=1&pageSize=20', { headers })).json()).data
  expect(detail.items[0].materialId).toBe(material)
  expect(Object.keys(detail.items[0].months)).toHaveLength(6)
  const old = (await (await request.get(base + '/api/v1/forecast-versions/' + before.id + '?page=1&pageSize=20', { headers })).json()).data
  expect(old.items.length).toBeGreaterThan(0)
  await page.getByRole('button', { name: '查看版本' }).click()
  await expect(page.getByRole('dialog', { name: '版本记录' }).getByText('V' + before.versionNo, { exact: true })).toBeVisible()
  await page.getByRole('dialog', { name: '版本记录' }).getByRole('button', { name: '关闭此对话框' }).click()
  await page.goto(base + '/planning')
  await page.getByRole('button', { name: '生成排产' }).first().click()
  const create = page.getByRole('dialog', { name: '生成排产' })
  await expect(create).toBeVisible()
  await expect(create.locator('.el-loading-mask')).toHaveCount(0)
  const bomStatus = await request.get(base + '/api/v1/mrp/batches/current-bom-status', { headers })
  expect(bomStatus.ok(), await bomStatus.text()).toBeTruthy()
  expect((await bomStatus.json()).data.localCount).toBe(0)
  await expect(create.getByText('加载排产准备数据失败', { exact: false })).toHaveCount(0)
  await expect(create.locator('.el-form-item').filter({ hasText: '经营计划' }).locator('.el-select')).toContainText('V' + now.versionNo)
  await create.getByRole('button', { name: '取消' }).click()
  expect(errors).toEqual([])
})
