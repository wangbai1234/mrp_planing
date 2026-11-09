import { expect, test } from '@playwright/test'
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

test('upload grant opens import dialogs without granting confirmation', async ({ page, request }, testInfo) => {
  const suffix = randomUUID().replaceAll('-', '').slice(0, 12)
  const username = `import_ui_${suffix}`
  const password = `A1!${randomBytes(24).toString('base64url')}`
  const hash = execFileSync('htpasswd', ['-niB', username], { input: `${password}\n`, encoding: 'utf8' }).trim().split(':').slice(1).join(':')
  let userId = 0
  let roleId = 0
  const unexpectedResponses: string[] = []
  page.on('response', response => {
    if (response.url().includes('/api/v1/') && response.status() >= 403) {
      unexpectedResponses.push(`${response.status()} ${new URL(response.url()).pathname}`)
    }
  })
  try {
    roleId = Number(sql(`INSERT INTO role(code,name) VALUES('ICUI_${suffix}','验收临时角色'); SELECT LAST_INSERT_ID();`))
    sql(`INSERT INTO role_permission(role_id,permission_id)
      SELECT ${roleId},id FROM permission WHERE code IN
      ('forecast:view','forecast:version_view','forecast:import','inventory:view','inventory:import','mrp_factory:view');
      INSERT INTO role_data_scope(role_id,scope_type) VALUES(${roleId},'ALL');`)
    userId = Number(sql(`INSERT INTO user(username,password_hash,need_change_password)
      VALUES('${username}','${hash}',0); SELECT LAST_INSERT_ID();`))
    sql(`INSERT INTO user_role(user_id,role_id) VALUES(${userId},${roleId});`)

    const login = await request.post('/api/v1/auth/login', { data: { username, password } })
    expect(login.status()).toBe(200)
    const user = (await login.json()).data
    expect(user.permissions).toContain('inventory:import')
    expect(user.permissions).not.toContain('inventory:import_confirm')
    expect(user.permissions).not.toContain('forecast:import_confirm')
    await page.goto('/login')
    await page.evaluate(data => {
      localStorage.setItem('mrp_token', data.token)
      localStorage.setItem('mrp_user_info', JSON.stringify({
        id: data.userId, username: data.username, displayName: data.displayName,
        roles: data.roles, permissions: data.permissions
      }))
    }, user)

    for (const [path, title] of [['/inventory', '库存批量导入'], ['/forecast', '经营计划批量导入']]) {
      await page.goto(path)
      await page.getByRole('button', { name: '导入', exact: true }).click()
      const dialog = page.getByRole('dialog').filter({ hasText: title })
      await expect(dialog).toBeVisible()
      await expect(dialog.getByRole('button', { name: '开始导入' })).toHaveCount(0)
      await dialog.getByRole('button', { name: '取消' }).scrollIntoViewIfNeeded()
      await page.screenshot({ path: testInfo.outputPath(`${path.slice(1)}-upload-only.png`), fullPage: true })
    }
    expect(unexpectedResponses).toEqual([])
  } finally {
    if (userId) sql(`DELETE FROM audit_log WHERE operator_id=${userId};
      DELETE FROM user_role WHERE user_id=${userId}; DELETE FROM user WHERE id=${userId};`)
    if (roleId) sql(`DELETE FROM role_data_scope WHERE role_id=${roleId};
      DELETE FROM role_permission WHERE role_id=${roleId}; DELETE FROM role WHERE id=${roleId};`)
  }
})
