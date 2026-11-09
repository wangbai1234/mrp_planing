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

async function request(path, token, method = 'GET') {
  const response = await fetch(base + path, { method, headers: { Authorization: `Bearer ${token}` } })
  return { status: response.status, body: await response.text() }
}

const suffix = randomUUID().replaceAll('-', '').slice(0, 10)
const password = `A1!${randomBytes(24).toString('base64url')}`
const hash = execFileSync('htpasswd', ['-niB', 'fixture'], { input: `${password}\n`, encoding: 'utf8' }).trim().split(':')[1]
const baseline = sql('SELECT status,active_run_id,result_checksum FROM mrp_calc_batch WHERE id=2;')
const cases = [
  { code: `PI_${suffix}`, permission: 'inventory:import', roleId: 0, userId: 0 },
  { code: `PC_${suffix}`, permission: 'inventory:import_confirm', roleId: 0, userId: 0 }
]
const result = { status: 'RUNNING', at: new Date().toISOString(), cases: [] }

try {
  for (const entry of cases) {
    entry.roleId = Number(sql(`INSERT INTO role(code,name) VALUES('${entry.code}','验收临时角色'); SELECT LAST_INSERT_ID();`))
    sql(`INSERT INTO role_permission(role_id,permission_id) SELECT ${entry.roleId},id FROM permission WHERE code='${entry.permission}';
      INSERT INTO role_data_scope(role_id,scope_type) VALUES(${entry.roleId},'ALL');`)
    entry.userId = Number(sql(`INSERT INTO user(username,password_hash,need_change_password)
      VALUES('${entry.code}','${hash}',0); SELECT LAST_INSERT_ID();`))
    sql(`INSERT INTO user_role(user_id,role_id) VALUES(${entry.userId},${entry.roleId});`)
    const login = await fetch(base + '/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: entry.code, password }) })
    const session = await login.json()
    if (login.status !== 200 || !session.data?.token) throw new Error(`${entry.permission} login failed`)
    const endpoints = []
    for (const path of ['/inventory-imports/999999999/confirm', '/inventory-imports/items/999999999/confirm']) {
      const response = await request(path, session.data.token, 'POST')
      endpoints.push({ path, status: response.status, success: response.status < 300 })
    }
    result.cases.push({ permission: entry.permission, endpoints })
  }
  const importOnly = result.cases[0].endpoints
  const confirmOnly = result.cases[1].endpoints
  result.status = importOnly.every(item => item.status === 403) && confirmOnly.every(item => [400, 404].includes(item.status))
    ? 'PASS' : 'FAIL'
} catch (error) {
  result.status = 'FAIL'
  result.failure = String(error)
} finally {
  for (const entry of cases) {
    if (entry.userId) sql(`DELETE FROM audit_log WHERE operator_id=${entry.userId};
      DELETE FROM user_role WHERE user_id=${entry.userId}; DELETE FROM user WHERE id=${entry.userId};`)
    if (entry.roleId) sql(`DELETE FROM role_data_scope WHERE role_id=${entry.roleId};
      DELETE FROM role_permission WHERE role_id=${entry.roleId}; DELETE FROM role WHERE id=${entry.roleId};`)
  }
  result.remainingRoles = Number(sql(`SELECT COUNT(*) FROM role WHERE code LIKE '%${suffix}';`))
  result.remainingUsers = Number(sql(`SELECT COUNT(*) FROM user WHERE username LIKE '%${suffix}';`))
  result.batchUnchanged = sql('SELECT status,active_run_id,result_checksum FROM mrp_calc_batch WHERE id=2;') === baseline
  if (result.remainingRoles || result.remainingUsers || !result.batchUnchanged) result.status = 'FAIL'
  mkdirSync(dirname(output), { recursive: true })
  writeFileSync(output, JSON.stringify(result, null, 2) + '\n')
}
console.log(JSON.stringify({ status: result.status, cases: result.cases, remainingRoles: result.remainingRoles,
  remainingUsers: result.remainingUsers, batchUnchanged: result.batchUnchanged }))
if (result.status !== 'PASS') process.exitCode = 1
