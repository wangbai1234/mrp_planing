import { test, expect } from '@playwright/test'
import { execFileSync } from 'node:child_process'
import { randomBytes, randomUUID } from 'node:crypto'

function sql(query: string) {
  return execFileSync('docker', ['exec', '-i', 'mrp-testkit-mysql', 'sh', '-c',
    'MYSQL_PWD="$MYSQL_PASSWORD" mysql --default-character-set=utf8mb4 -u"$MYSQL_USER" -N -B mrp_testkit'],
  { input: query, encoding: 'utf8' }).trim()
}

test('新代码 API 的整机与物料页面读取已有批次', async ({ page, request }, testInfo) => {
  const suffix = randomUUID().replaceAll('-', '').slice(0, 12)
  const username = `frontier_ui_${suffix}`
  const password = `A1!${randomBytes(24).toString('base64url')}`
  const hash = execFileSync('htpasswd', ['-niB', 'fixture'], { input: `${password}\n`, encoding: 'utf8' }).trim().split(':')[1]
  const errors: string[] = []
  const apiFailures: string[] = []
  page.on('pageerror', error => errors.push(error.message))
  page.on('console', message => { if (message.type() === 'error') errors.push(message.text()) })
  page.on('response', response => {
    if (response.url().includes('/api/v1/') && response.status() >= 500)
      apiFailures.push(`${response.status()} ${response.url()}`)
  })
  let userId = 0
  try {
    userId = Number(sql(`INSERT INTO user(username,password_hash,need_change_password)
      VALUES('${username}','${hash}',0); SELECT LAST_INSERT_ID();`))
    sql(`INSERT INTO user_role(user_id,role_id) SELECT ${userId},id FROM role WHERE code='ADMIN';`)
    await page.goto('/login')
    await page.getByPlaceholder('邮箱').fill(username)
    await page.getByPlaceholder('密码').fill(password)
    await page.getByRole('button', { name: '登录', exact: true }).click()
    await page.waitForURL(url => !url.pathname.includes('/login'))
    const token = await page.evaluate(() => localStorage.getItem('mrp_token'))
    expect(token).toBeTruthy()
    const batch = await request.get('/api/v1/mrp/batches/7', { headers: { Authorization: `Bearer ${token}` } })
    expect(batch.status()).toBe(200)
    expect((await batch.json()).data.id).toBe(7)

    await page.goto('/planning/7')
    await expect(page.getByText('排产版本 #7', { exact: false }).first()).toBeVisible()
    await expect(page.locator('.cell-load').first()).toBeVisible()
    await page.screenshot({ path: testInfo.outputPath('planning-7.png'), fullPage: true })

    await page.goto('/shortage/7')
    await expect(page.getByRole('heading', { name: '物料缺料' })).toBeVisible()
    await expect(page.getByText('批次 #7', { exact: false }).first()).toBeVisible()
    await expect(page.getByRole('columnheader', { name: '末级需求物料' })).toBeVisible({ timeout: 15000 })
    await expect(page.getByRole('button', { name: '受影响整机' }).first()).toBeVisible({ timeout: 15000 })
    await expect(page.locator('.card .el-loading-mask')).toHaveCount(0, { timeout: 15000 })
    await page.screenshot({ path: testInfo.outputPath('shortage-7.png'), fullPage: true })
    expect(errors).toEqual([])
    expect(apiFailures).toEqual([])
  } finally {
    if (userId) {
      sql(`DELETE FROM audit_log WHERE operator_id=${userId}; DELETE FROM user_role WHERE user_id=${userId};
        DELETE FROM user WHERE id=${userId};`)
      expect(Number(sql(`SELECT COUNT(*) FROM user WHERE id=${userId};`))).toBe(0)
    }
  }
})
