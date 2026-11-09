import { execFileSync } from 'node:child_process'
import { randomBytes, randomUUID } from 'node:crypto'
import { mkdirSync, writeFileSync } from 'node:fs'
import { dirname } from 'node:path'

const container = 'mrp-acceptance-20261104-mysql-1'
const base = 'http://127.0.0.1:8183/api/v1'
const output = process.argv[2]
if (!output || !output.endsWith('.json')) throw new Error('Pass a new result.json path')
const project = execFileSync('docker', ['inspect', '-f', '{{index .Config.Labels "com.docker.compose.project"}}', container], { encoding: 'utf8' }).trim()
if (project !== 'mrp-acceptance-20261104') throw new Error(`Unexpected project: ${project}`)

function sql(query) {
  return execFileSync('docker', ['exec', '-i', container, 'sh', '-c',
    'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot --default-character-set=utf8mb4 -N -B mrp_acceptance'],
  { input: query, encoding: 'utf8' }).trim()
}

async function api(path, token, method = 'GET', body) {
  const response = await fetch(base + path, {
    method,
    headers: { Authorization: `Bearer ${token}`, ...(body ? { 'Content-Type': 'application/json' } : {}) },
    body: body ? JSON.stringify(body) : undefined
  })
  return { status: response.status, body: await response.json() }
}

async function login(username, password) {
  const response = await fetch(base + '/auth/login', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password })
  })
  const body = await response.json()
  if (response.status !== 200 || !body.success || !body.data?.token) throw new Error(`Login failed for ${username}`)
  return body.data.token
}

const suffix = randomUUID().replaceAll('-', '').slice(0, 12)
const password = `A1!${randomBytes(24).toString('base64url')}`
const hash = execFileSync('htpasswd', ['-niB', 'fixture'], { input: `${password}\n`, encoding: 'utf8' }).trim().split(':')[1]
const actorName = `positive_actor_${suffix}`
const targetName = `positive_target_${suffix}`
const baseline = sql('SELECT status,active_run_id,result_checksum FROM mrp_calc_batch WHERE id=2;')
const permissionId = Number(sql("SELECT id FROM permission WHERE code='mrp_factory:view' AND status='ACTIVE';"))
const assignId = Number(sql("SELECT id FROM permission WHERE code='role:assign_permission' AND status='ACTIVE';"))
if (!permissionId || !assignId) throw new Error('Required permissions missing')
const result = { status: 'RUNNING', checkedAt: new Date().toISOString(), assertions: 0, steps: [] }
let actorRoleId = 0
let targetRoleId = 0
let actorId = 0
let targetId = 0
const check = (condition, detail) => { result.assertions++; if (!condition) throw new Error(detail) }

try {
  actorRoleId = Number(sql(`INSERT INTO role(code,name) VALUES('${actorName}','临时配置操作者'); SELECT LAST_INSERT_ID();`))
  targetRoleId = Number(sql(`INSERT INTO role(code,name) VALUES('${targetName}','临时目标角色'); SELECT LAST_INSERT_ID();`))
  sql(`INSERT INTO role_permission(role_id,permission_id) VALUES(${actorRoleId},${assignId});`)
  actorId = Number(sql(`INSERT INTO user(username,password_hash,need_change_password)
    VALUES('${actorName}','${hash}',0); SELECT LAST_INSERT_ID();`))
  targetId = Number(sql(`INSERT INTO user(username,password_hash,need_change_password)
    VALUES('${targetName}','${hash}',0); SELECT LAST_INSERT_ID();`))
  sql(`INSERT INTO user_role(user_id,role_id) VALUES(${actorId},${actorRoleId}),(${targetId},${targetRoleId});`)

  const actorToken = await login(actorName, password)
  const targetToken = await login(targetName, password)
  const configPath = `/roles/${targetRoleId}/configuration`
  const factoriesPath = '/mrp/factories'
  const payload = (permissionIds, scopeType, factoryCodes = []) => ({
    permissionIds, dataScope: { scopeType, factoryCodes }
  })
  check((await api(`/roles/${targetRoleId}/permissions`, actorToken)).status === 200, 'actor cannot read role permissions')
  check((await api('/permissions/tree', actorToken)).status === 200, 'actor cannot read permission tree')
  check((await api(configPath, targetToken, 'PUT', payload([permissionId], 'ALL'))).status === 403,
    'target can configure its own role')
  check((await api(factoriesPath, targetToken)).status === 403, 'target can read factories before grant')

  const invalid = await api(configPath, actorToken, 'PUT', payload([permissionId], 'FACTORY', ['不存在工厂']))
  check(invalid.status >= 400 && invalid.status < 500, `invalid factory accepted: ${invalid.status}`)
  check(sql(`SELECT COUNT(*) FROM role_permission WHERE role_id=${targetRoleId};`) === '0', 'invalid update wrote permissions')
  check(sql(`SELECT COUNT(*) FROM role_data_scope WHERE role_id=${targetRoleId};`) === '0', 'invalid update wrote scope')
  result.steps.push({ name: 'invalid configuration rollback', status: invalid.status })

  for (const [scopeType, codes, expectedFactories] of [
    ['FACTORY', ['爱培科'], ['爱培科']],
    ['ALL', [], ['爱培科', '永惠']],
    ['NONE', [], []]
  ]) {
    const saved = await api(configPath, actorToken, 'PUT', payload([permissionId], scopeType, codes))
    check(saved.status === 200 && saved.body.success, `save ${scopeType}: ${saved.status}`)
    const granted = await api(factoriesPath, targetToken)
    check(granted.status === 200 && granted.body.success, `old token cannot read after ${scopeType}: ${granted.status}`)
    check(JSON.stringify([...granted.body.data].sort()) === JSON.stringify([...expectedFactories].sort()),
      `${scopeType} returned unexpected factory content`)
    const scope = await api(`/roles/${targetRoleId}/data-scope`, actorToken)
    check(scope.status === 200 && scope.body.data?.scopeType === scopeType, `${scopeType} configuration not readable`)
    check(sql(`SELECT COUNT(*) FROM role_permission WHERE role_id=${targetRoleId} AND permission_id=${permissionId};`) === '1',
      `${scopeType} permission not persisted`)
    result.steps.push({ name: scopeType, status: saved.status, visibleFactories: granted.body.data })
  }

  const revoked = await api(configPath, actorToken, 'PUT', payload([], 'NONE'))
  check(revoked.status === 200 && revoked.body.success, `revoke: ${revoked.status}`)
  check((await api(factoriesPath, targetToken)).status === 403, 'old token retained revoked permission')
  check(sql(`SELECT COUNT(*) FROM role_permission WHERE role_id=${targetRoleId};`) === '0', 'revoke not persisted')
  result.steps.push({ name: 'revoke on old token', status: 403 })
  result.status = 'PASS'
} catch (error) {
  result.status = 'FAIL'
  result.failure = String(error)
} finally {
  if (actorId || targetId) {
    const ids = [actorId, targetId].filter(Boolean).join(',')
    sql(`DELETE FROM audit_log WHERE operator_id IN (${ids}); DELETE FROM user_role WHERE user_id IN (${ids});
      DELETE FROM user_permission WHERE user_id IN (${ids}); DELETE FROM user WHERE id IN (${ids});`)
  }
  if (actorRoleId || targetRoleId) {
    const ids = [actorRoleId, targetRoleId].filter(Boolean).join(',')
    sql(`DELETE FROM role_permission WHERE role_id IN (${ids}); DELETE FROM role_data_scope WHERE role_id IN (${ids});
      DELETE FROM role WHERE id IN (${ids});`)
  }
  result.remainingUsers = Number(sql(`SELECT COUNT(*) FROM user WHERE username IN ('${actorName}','${targetName}');`))
  result.remainingRoles = Number(sql(`SELECT COUNT(*) FROM role WHERE code IN ('${actorName}','${targetName}');`))
  result.remainingAudit = actorId ? Number(sql(`SELECT COUNT(*) FROM audit_log WHERE operator_id=${actorId};`)) : 0
  result.batchUnchanged = sql('SELECT status,active_run_id,result_checksum FROM mrp_calc_batch WHERE id=2;') === baseline
  if (result.remainingUsers || result.remainingRoles || result.remainingAudit || !result.batchUnchanged) result.status = 'FAIL'
  mkdirSync(dirname(output), { recursive: true })
  writeFileSync(output, JSON.stringify(result, null, 2) + '\n')
}
console.log(JSON.stringify({ status: result.status, assertions: result.assertions, remainingUsers: result.remainingUsers,
  remainingRoles: result.remainingRoles, batchUnchanged: result.batchUnchanged }))
if (result.status !== 'PASS') process.exitCode = 1
