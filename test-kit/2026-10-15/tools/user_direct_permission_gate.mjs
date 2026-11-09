import { execFileSync } from 'node:child_process'
import { randomBytes, randomUUID } from 'node:crypto'
import { mkdirSync, writeFileSync } from 'node:fs'
import { dirname } from 'node:path'

const container = 'mrp-acceptance-20261104-mysql-1'
const base = 'http://127.0.0.1:8183/api/v1'
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
const username = `UDPG_${suffix}`
const unauthorizedCreated = `UDPG_DENY_${suffix}`
const permittedCreated = `UDPG_ALLOW_${suffix}`
const password = `A1!${randomBytes(24).toString('base64url')}`
const hash = execFileSync('htpasswd', ['-niB', username], { input: `${password}\n`, encoding: 'utf8' }).trim().split(':').slice(1).join(':')
const result = { status: 'RUNNING', at: new Date().toISOString() }
let roleId = 0
let userId = 0

try {
  roleId = Number(sql(`INSERT INTO role(code,name) VALUES('${username}','验收临时角色'); SELECT LAST_INSERT_ID();`))
  sql(`INSERT INTO role_permission(role_id,permission_id)
    SELECT ${roleId},id FROM permission WHERE code='user:update';`)
  userId = Number(sql(`INSERT INTO user(username,password_hash,need_change_password)
    VALUES('${username}','${hash}',0); SELECT LAST_INSERT_ID();`))
  sql(`INSERT INTO user_role(user_id,role_id) VALUES(${userId},${roleId});`)
  const login = await fetch(base + '/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }) })
  const session = await login.json()
  if (login.status !== 200 || !session.data?.token) throw new Error('Login failed')
  const permissionId = Number(sql("SELECT id FROM permission WHERE code='user:assign_permission';"))
  const put = await fetch(`${base}/users/${userId}/permissions`, {
    method: 'PUT', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${session.data.token}` },
    body: JSON.stringify({ permissions: [{ permissionId, effect: 'ALLOW' }] })
  })
  result.putStatus = put.status
  result.granted = Number(sql(`SELECT COUNT(*) FROM user_permission WHERE user_id=${userId} AND permission_id=${permissionId} AND effect='ALLOW';`))
  if (put.status !== 403 || result.granted !== 0) throw new Error('user:update bypassed permission configuration')
  const reset = await fetch(`${base}/users/${userId}/reset-password`, {
    method: 'PUT', headers: { Authorization: `Bearer ${session.data.token}` }
  })
  result.resetStatus = reset.status
  if (reset.status !== 403) throw new Error('user:update bypassed password reset permission')
  const disable = await fetch(`${base}/users/${userId}/status`, {
    method: 'PUT', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${session.data.token}` },
    body: JSON.stringify({ isActive: false })
  })
  result.disableStatus = disable.status
  if (disable.status !== 403) throw new Error('user:update bypassed disable permission')
  sql(`INSERT INTO role_permission(role_id,permission_id)
    SELECT ${roleId},id FROM permission WHERE code='user:enable';`)
  const wrongStatusGrant = await fetch(`${base}/users/${userId}/status`, {
    method: 'PUT', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${session.data.token}` },
    body: JSON.stringify({ isActive: false })
  })
  result.enableCannotDisableStatus = wrongStatusGrant.status
  if (wrongStatusGrant.status !== 403) throw new Error('user:enable bypassed disable permission')
  sql(`INSERT INTO role_permission(role_id,permission_id) VALUES(${roleId},${permissionId});`)
  const allowed = await fetch(`${base}/users/${userId}/permissions`, {
    method: 'PUT', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${session.data.token}` },
    body: JSON.stringify({ permissions: [{ permissionId, effect: 'ALLOW' }] })
  })
  result.configGrantStatus = allowed.status
  result.configGranted = Number(sql(`SELECT COUNT(*) FROM user_permission WHERE user_id=${userId} AND permission_id=${permissionId} AND effect='ALLOW';`))
  if (allowed.status !== 200 || result.configGranted !== 1) throw new Error('user:assign_permission did not allow configuration')

  sql(`INSERT INTO role_permission(role_id,permission_id)
    SELECT ${roleId},id FROM permission WHERE code='user:create';`)
  const adminRoleId = Number(sql("SELECT id FROM role WHERE code='ADMIN';"))
  const unauthorized = await fetch(`${base}/users`, {
    method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${session.data.token}` },
    body: JSON.stringify({ username: unauthorizedCreated, roleIds: [adminRoleId] })
  })
  result.createWithRoleWithoutGrantStatus = unauthorized.status
  result.unauthorizedCreatedCount = Number(sql(`SELECT COUNT(*) FROM user WHERE username='${unauthorizedCreated}';`))
  if (unauthorized.status !== 403 || result.unauthorizedCreatedCount !== 0) {
    throw new Error('user:create bypassed role assignment permission')
  }
  sql(`INSERT INTO role_permission(role_id,permission_id)
    SELECT ${roleId},id FROM permission WHERE code='user:assign_role';`)
  const permitted = await fetch(`${base}/users`, {
    method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${session.data.token}` },
    body: JSON.stringify({ username: permittedCreated, roleIds: [adminRoleId] })
  })
  result.createWithRoleWithGrantStatus = permitted.status
  result.permittedRoleCount = Number(sql(`SELECT COUNT(*) FROM user_role ur JOIN user u ON u.id=ur.user_id
    WHERE u.username='${permittedCreated}' AND ur.role_id=${adminRoleId};`))
  result.status = permitted.status === 200 && result.permittedRoleCount === 1 ? 'PASS' : 'FAIL'
} catch (error) {
  result.status = 'FAIL'
  result.failure = String(error)
} finally {
  if (userId) sql(`DELETE FROM audit_log WHERE operator_id=${userId};
    DELETE FROM user_permission WHERE user_id=${userId};
    DELETE FROM user_role WHERE user_id=${userId}; DELETE FROM user WHERE id=${userId};`)
  sql(`DELETE ur FROM user_role ur JOIN user u ON u.id=ur.user_id
    WHERE u.username IN ('${unauthorizedCreated}','${permittedCreated}');
    DELETE FROM user WHERE username IN ('${unauthorizedCreated}','${permittedCreated}');`)
  if (roleId) sql(`DELETE FROM role_permission WHERE role_id=${roleId}; DELETE FROM role WHERE id=${roleId};`)
  result.remainingUsers = Number(sql(`SELECT COUNT(*) FROM user WHERE username='${username}';`))
  result.remainingRoles = Number(sql(`SELECT COUNT(*) FROM role WHERE code='${username}';`))
  result.remainingCreated = Number(sql(`SELECT COUNT(*) FROM user
    WHERE username IN ('${unauthorizedCreated}','${permittedCreated}');`))
  if (result.remainingUsers || result.remainingRoles || result.remainingCreated) result.status = 'FAIL'
  mkdirSync(dirname(output), { recursive: true })
  writeFileSync(output, JSON.stringify(result, null, 2) + '\n')
}
console.log(JSON.stringify(result))
if (result.status !== 'PASS') process.exitCode = 1
