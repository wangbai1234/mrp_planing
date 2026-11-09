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

async function api(path, token, method = 'GET', data) {
  const response = await fetch(base + path, {
    method,
    headers: { Authorization: `Bearer ${token}`, ...(data ? { 'Content-Type': 'application/json' } : {}) },
    body: data ? JSON.stringify(data) : undefined
  })
  return { status: response.status, body: await response.json() }
}

async function login(username, password) {
  const response = await fetch(base + '/auth/login', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password })
  })
  const body = await response.json()
  if (response.status !== 200 || !body.success || !body.data?.token) throw new Error(`Login failed: ${username}`)
  return body.data.token
}

const suffix = randomUUID().replaceAll('-', '').slice(0, 12)
const actorName = `admin002_actor_${suffix}`
const roleCode = `ADMIN002_${suffix}`
const targetName = `admin002_target_${suffix}`
const password = `A1!${randomBytes(24).toString('base64url')}`
const hash = execFileSync('htpasswd', ['-niB', 'fixture'], { input: `${password}\n`, encoding: 'utf8' }).trim().split(':')[1]
const baseline = sql('SELECT status,active_run_id,result_checksum FROM mrp_calc_batch WHERE id=2;')
const permissionCodes = ['role:view', 'role:create', 'role:assign_permission', 'role:delete', 'mrp_factory:view']
const ids = Object.fromEntries(permissionCodes.map(code => [code, Number(sql(`SELECT id FROM permission WHERE code='${code}' AND status='ACTIVE';`))]))
if (Object.values(ids).some(id => !id)) throw new Error('Required permission missing')

const result = { status: 'RUNNING', checkedAt: new Date().toISOString(), assertions: 0, steps: [] }
let actorRoleId = 0
let actorId = 0
let roleId = 0
let targetId = 0
const check = (ok, detail) => { result.assertions++; if (!ok) throw new Error(detail) }

try {
  actorRoleId = Number(sql(`INSERT INTO role(code,name) VALUES('${actorName}','临时角色管理员'); SELECT LAST_INSERT_ID();`))
  sql(`INSERT INTO role_permission(role_id,permission_id) VALUES ${permissionCodes.slice(0, 4).map(code => `(${actorRoleId},${ids[code]})`).join(',')};`)
  actorId = Number(sql(`INSERT INTO user(username,password_hash,need_change_password)
    VALUES('${actorName}','${hash}',0); SELECT LAST_INSERT_ID();`))
  sql(`INSERT INTO user_role(user_id,role_id) VALUES(${actorId},${actorRoleId});`)
  const actorToken = await login(actorName, password)
  const created = await api('/roles', actorToken, 'POST', { code: roleCode, name: '验收临时角色' })
  check(created.status === 200 && created.body.success && created.body.data?.code === roleCode, `create role: ${created.status}`)
  roleId = created.body.data.id
  check(sql(`SELECT COUNT(*) FROM role WHERE id=${roleId} AND code='${roleCode}';`) === '1', 'created role not persisted')
  result.steps.push({ name: 'create', status: created.status, roleId })

  targetId = Number(sql(`INSERT INTO user(username,password_hash,need_change_password)
    VALUES('${targetName}','${hash}',0); SELECT LAST_INSERT_ID();`))
  sql(`INSERT INTO user_role(user_id,role_id) VALUES(${targetId},${roleId});`)
  const targetToken = await login(targetName, password)
  check((await api('/mrp/factories', targetToken)).status === 403, 'target had permission before grant')
  const granted = await api(`/roles/${roleId}/configuration`, actorToken, 'PUT', {
    permissionIds: [ids['mrp_factory:view']], dataScope: { scopeType: 'ALL', factoryCodes: [] }
  })
  check(granted.status === 200 && granted.body.success, `grant: ${granted.status}`)
  const factories = await api('/mrp/factories', targetToken)
  check(factories.status === 200 && factories.body.success && factories.body.data.includes('爱培科'), 'old token did not gain factory access')
  check(sql(`SELECT COUNT(*) FROM role_permission WHERE role_id=${roleId} AND permission_id=${ids['mrp_factory:view']};`) === '1', 'grant not persisted')
  result.steps.push({ name: 'grant', oldTokenStatus: factories.status })

  const revoked = await api(`/roles/${roleId}/configuration`, actorToken, 'PUT', {
    permissionIds: [], dataScope: { scopeType: 'NONE', factoryCodes: [] }
  })
  check(revoked.status === 200 && revoked.body.success, `revoke: ${revoked.status}`)
  check((await api('/mrp/factories', targetToken)).status === 403, 'old token retained revoked permission')
  check(sql(`SELECT COUNT(*) FROM role_permission WHERE role_id=${roleId};`) === '0', 'revoke not persisted')
  result.steps.push({ name: 'revoke', oldTokenStatus: 403 })

  const referenced = await api(`/roles/${roleId}`, actorToken, 'DELETE')
  result.steps.push({ name: 'delete referenced role', status: referenced.status,
    code: referenced.body.error?.code, message: referenced.body.error?.message })
  check(referenced.status === 400 && referenced.body.error?.code === 'MRP_ILLEGAL_STATE' &&
    referenced.body.error?.message?.includes('用户'),
    `referenced delete lacked business rejection: ${referenced.status}/${referenced.body.error?.code}`)
  check(sql(`SELECT COUNT(*) FROM role WHERE id=${roleId};`) === '1', 'referenced role was removed')
  sql(`DELETE FROM user_role WHERE user_id=${targetId} AND role_id=${roleId};`)
  const deleted = await api(`/roles/${roleId}`, actorToken, 'DELETE')
  check(deleted.status === 200 && deleted.body.success, `delete unreferenced role: ${deleted.status}`)
  check(sql(`SELECT COUNT(*) FROM role WHERE id=${roleId};`) === '0', 'role remains after delete')
  check(sql(`SELECT COUNT(*) FROM role_data_scope WHERE role_id=${roleId};`) === '0', 'role scope remains after delete')
  roleId = 0
  result.steps.push({ name: 'delete unreferenced role', status: deleted.status })
  result.status = 'PASS'
} catch (error) {
  result.status = 'FAIL'
  result.failure = String(error)
} finally {
  if (targetId || actorId) {
    const userIds = [targetId, actorId].filter(Boolean).join(',')
    sql(`DELETE FROM audit_log WHERE operator_id IN (${userIds}); DELETE FROM user_role WHERE user_id IN (${userIds});
      DELETE FROM user_permission WHERE user_id IN (${userIds}); DELETE FROM user WHERE id IN (${userIds});`)
  }
  if (roleId || actorRoleId) {
    const roleIds = [roleId, actorRoleId].filter(Boolean).join(',')
    sql(`DELETE FROM role_permission WHERE role_id IN (${roleIds}); DELETE FROM role_data_scope WHERE role_id IN (${roleIds});
      DELETE FROM role WHERE id IN (${roleIds});`)
  }
  result.remainingUsers = Number(sql(`SELECT COUNT(*) FROM user WHERE username IN ('${actorName}','${targetName}');`))
  result.remainingRoles = Number(sql(`SELECT COUNT(*) FROM role WHERE code IN ('${actorName}','${roleCode}');`))
  result.remainingAudit = actorId ? Number(sql(`SELECT COUNT(*) FROM audit_log WHERE operator_id=${actorId};`)) : 0
  result.batchUnchanged = sql('SELECT status,active_run_id,result_checksum FROM mrp_calc_batch WHERE id=2;') === baseline
  if (result.remainingUsers || result.remainingRoles || result.remainingAudit || !result.batchUnchanged) result.status = 'FAIL'
  mkdirSync(dirname(output), { recursive: true })
  writeFileSync(output, JSON.stringify(result, null, 2) + '\n')
}
console.log(JSON.stringify({ status: result.status, assertions: result.assertions, failure: result.failure,
  remainingUsers: result.remainingUsers, remainingRoles: result.remainingRoles, batchUnchanged: result.batchUnchanged }))
if (result.status !== 'PASS') process.exitCode = 1
