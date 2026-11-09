import { test, expect } from '@playwright/test'
import { execFileSync } from 'node:child_process'
import { randomBytes, randomUUID } from 'node:crypto'

const container = 'mrp-acceptance-20261104-mysql-1'
const project = execFileSync('docker', ['inspect', '-f', '{{index .Config.Labels "com.docker.compose.project"}}', container], { encoding: 'utf8' }).trim()
if (project !== 'mrp-acceptance-20261104') throw new Error(`Unexpected Docker project: ${project}`)

function sql(query: string) {
  return execFileSync('docker', ['exec', '-i', container, 'sh', '-c',
    'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot --default-character-set=utf8mb4 -N -B mrp_acceptance'],
  { input: query, encoding: 'utf8' }).trim()
}

test('角色页配置功能权限和工厂范围并在刷新后保留', async ({ page, request }, testInfo) => {
  const suffix = randomUUID().replaceAll('-', '').slice(0, 12)
  const username = `role_ui_${suffix}`
  const password = `A1!${randomBytes(24).toString('base64url')}`
  const hash = execFileSync('htpasswd', ['-niB', username], { input: `${password}\n`, encoding: 'utf8' }).trim().split(':').slice(1).join(':')
  let userId = 0
  let roleId = 0
  try {
    userId = Number(sql(`INSERT INTO user(username,password_hash,display_name,need_change_password)
      VALUES('${username}','${hash}','Role UI acceptance',0); SELECT LAST_INSERT_ID();`))
    sql(`INSERT INTO user_role(user_id,role_id) SELECT ${userId},id FROM role WHERE code='ADMIN';`)

    await page.goto('/login')
    await page.getByPlaceholder('邮箱').fill(username)
    await page.getByPlaceholder('密码').fill(password)
    await page.getByRole('button', { name: '登录', exact: true }).click()
    await page.waitForURL(url => !url.pathname.includes('/login'))
    const token = await page.evaluate(() => localStorage.getItem('mrp_token'))
    expect(token).toBeTruthy()

    const created = await request.post('/api/v1/roles', {
      headers: { Authorization: `Bearer ${token}` },
      data: { code: `ROLEACC_${suffix}`, name: '验收临时角色', description: '隔离角色页面验收' }
    })
    expect(created.status()).toBe(200)
    roleId = (await created.json()).data.id
    expect(roleId).toBeGreaterThan(0)

    await page.goto(`/admin/roles/${roleId}/permissions`)
    await expect(page.getByRole('heading', { name: '权限配置' })).toBeVisible()
    await expect(page.getByText('角色: 验收临时角色')).toBeVisible()
    await page.getByPlaceholder('搜索权限').fill('user:assign_permission')
    await expect(page.getByText('配置用户直授权限 user:assign_permission', { exact: true })).toBeVisible()
    await page.getByPlaceholder('搜索权限').fill('user:reset_password')
    await expect(page.getByText('重置用户密码 user:reset_password', { exact: true })).toBeVisible()
    await page.getByText('指定工厂', { exact: true }).click()
    await page.getByText('爱培科', { exact: true }).click()
    await page.getByPlaceholder('搜索权限').fill('schedule:view')
    await page.getByText('schedule:view', { exact: true }).click()
    await page.getByRole('button', { name: '保存' }).click()
    await expect(page.getByText('保存成功')).toBeVisible()

    await page.reload()
    await expect(page.getByRole('radio', { name: '指定工厂' })).toBeChecked()
    await expect(page.getByRole('checkbox', { name: '爱培科' })).toBeChecked()
    await page.getByPlaceholder('搜索权限').fill('schedule:view')
    await expect(page.getByRole('checkbox', { name: /schedule:view/ })).toBeChecked()
    await page.screenshot({ path: testInfo.outputPath('role-configuration.png'), fullPage: true })

    const scope = await request.get(`/api/v1/roles/${roleId}/data-scope`, { headers: { Authorization: `Bearer ${token}` } })
    expect(scope.status()).toBe(200)
    expect((await scope.json()).data).toEqual({ scopeType: 'FACTORY', factoryCodes: ['爱培科'] })
    const saved = await request.get(`/api/v1/roles/${roleId}/permissions`, { headers: { Authorization: `Bearer ${token}` } })
    expect(saved.status()).toBe(200)
    const viewId = Number(sql("SELECT id FROM permission WHERE code='schedule:view';"))
    expect((await saved.json()).data).toContain(viewId)
  } finally {
    if (roleId) {
      sql(`DELETE FROM audit_log WHERE resource_type='ROLE' AND resource_id='${roleId}';
        DELETE FROM role_permission WHERE role_id=${roleId};
        DELETE FROM role_data_scope WHERE role_id=${roleId};
        DELETE FROM role WHERE id=${roleId};`)
    }
    if (userId) {
      sql(`DELETE FROM audit_log WHERE operator_id=${userId};
        DELETE FROM user_role WHERE user_id=${userId};
        DELETE FROM user WHERE id=${userId};`)
    }
  }
})

test('普通用户编辑权限不显示重置密码操作', async ({ page }, testInfo) => {
  const suffix = randomUUID().replaceAll('-', '').slice(0, 12)
  const username = `user_ui_${suffix}`
  const password = `A1!${randomBytes(24).toString('base64url')}`
  const hash = execFileSync('htpasswd', ['-niB', username], { input: `${password}\n`, encoding: 'utf8' }).trim().split(':').slice(1).join(':')
  const roleId = Number(sql(`INSERT INTO role(code,name) VALUES('UI_${suffix}','用户页面临时角色'); SELECT LAST_INSERT_ID();`))
  let userId = 0
  try {
    sql(`INSERT INTO role_permission(role_id,permission_id)
      SELECT ${roleId},id FROM permission WHERE code IN ('user:view','user:update','role:view');`)
    userId = Number(sql(`INSERT INTO user(username,password_hash,need_change_password)
      VALUES('${username}','${hash}',0); SELECT LAST_INSERT_ID();`))
    sql(`INSERT INTO user_role(user_id,role_id) VALUES(${userId},${roleId});`)
    await page.goto('/login')
    await page.getByPlaceholder('邮箱').fill(username)
    await page.getByPlaceholder('密码').fill(password)
    await page.getByRole('button', { name: '登录', exact: true }).click()
    await page.waitForURL(url => !url.pathname.includes('/login'))
    await expect.poll(() => page.evaluate(() => localStorage.getItem('mrp_token'))).toBeTruthy()
    await page.goto('/admin/users')
    await expect(page.getByRole('heading', { name: '用户管理' })).toBeVisible()
    await expect(page.getByRole('button', { name: '编辑' }).first()).toBeVisible()
    await expect(page.getByRole('button', { name: '重置密码' })).toHaveCount(0)
    await page.screenshot({ path: testInfo.outputPath('user-edit-no-reset.png'), fullPage: true })
  } finally {
    if (userId) sql(`DELETE FROM audit_log WHERE operator_id=${userId};
      DELETE FROM user_role WHERE user_id=${userId}; DELETE FROM user WHERE id=${userId};`)
    sql(`DELETE FROM role_permission WHERE role_id=${roleId}; DELETE FROM role WHERE id=${roleId};`)
  }
})
