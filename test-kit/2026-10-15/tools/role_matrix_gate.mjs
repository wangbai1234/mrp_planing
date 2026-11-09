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

const baseline = sql('SELECT status,active_run_id,result_checksum FROM mrp_calc_batch WHERE id=2;')
const suffix = randomUUID().replaceAll('-', '').slice(0, 12)
const password = `A1!${randomBytes(24).toString('base64url')}`
const hash = execFileSync('htpasswd', ['-niB', 'fixture'], { input: `${password}\n`, encoding: 'utf8' }).trim().split(':')[1]
const roles = sql("SELECT id,code FROM role WHERE code <> 'ADMIN' ORDER BY id;").split('\n').map(row => {
  const [id, code] = row.split('\t')
  return { id: Number(id), code, username: `matrix_${suffix}_${id}`, userId: 0 }
})
const result = { status: 'RUNNING', checkedAt: new Date().toISOString(), batch: 2, roles: [], assertions: 0 }
const scopedUsername = `matrix_scoped_${suffix}`
let scopedRoleId = 0
let scopedUserId = 0
const check = (condition, detail) => { result.assertions++; if (!condition) throw new Error(detail) }
const readCases = [
  { path: '/forecast-versions', authorities: ['forecast:view'] },
  { path: '/inventory-snapshots', authorities: ['inventory:view'] },
  { path: '/mrp/factories', authorities: ['mrp_factory:view', 'mrp_calendar:view', 'mrp_warehouse_mapping:view', 'mrp_batch:list', 'mrp_batch:create'] },
  { path: '/mrp/factory-master', authorities: ['mrp_factory:view'] },
  { path: '/mrp/factory-warehouse-mappings', authorities: ['mrp_warehouse_mapping:view'] },
  { path: '/mrp/production-calendars', authorities: ['mrp_calendar:view'] },
  { path: '/mrp/category-role-versions', authorities: ['mrp_config:view'] },
  { path: '/mrp/production-calendar-versions', authorities: ['mrp_calendar:view'] },
  { path: '/mrp/stock-policy-versions', authorities: ['mrp_stock_policy:view'] },
  { path: '/mrp/business-week-versions', authorities: ['mrp_business_week:view'] },
  { path: '/roles/1/permissions', authorities: ['role:view', 'role:assign_permission'] },
  { path: '/permissions/tree', authorities: ['permission:view', 'role:assign_permission'] }
]
const factoryScopedLists = new Set([
  '/inventory-snapshots', '/mrp/factories', '/mrp/factory-master',
  '/mrp/factory-warehouse-mappings', '/mrp/production-calendars',
  '/mrp/production-calendar-versions', '/mrp/stock-policy-versions'
])
const deniedWriteCases = [
  { path: '/forecast-imports', authority: 'forecast:import' },
  { path: '/forecast-imports/999999/confirm', authority: 'forecast:import_confirm' },
  { path: '/forecast-workspace/draft/999999/publish', authority: 'forecast:publish' },
  { path: '/inventory-imports', authority: 'inventory:import' },
  { path: '/inventory-imports/999999/confirm', authority: 'inventory:import_confirm' },
  { path: '/inventory-imports/items/999999/confirm', authority: 'inventory:import_confirm' },
  { path: '/users', authority: 'user:create' },
  { path: '/users/999999', authority: 'user:update', method: 'PUT' },
  { path: '/users/999999/status', authority: 'user:disable', method: 'PUT', body: { isActive: false } },
  { path: '/users/999999/roles', authority: 'user:assign_role', method: 'PUT' },
  { path: '/users/999999/permissions', authority: 'user:assign_permission', method: 'PUT' },
  { path: '/users/999999/reset-password', authority: 'user:reset_password', method: 'PUT' },
  { path: '/users/999999', authority: 'user:delete', method: 'DELETE' },
  { path: '/mrp/material-policy-versions', authority: 'mrp_config:manage' },
  { path: '/roles', authority: 'role:create' },
  { path: '/roles/999999', authority: 'role:update', method: 'PUT' },
  { path: '/roles/999999/permissions', authority: 'role:assign_permission', method: 'PUT' },
  { path: '/roles/999999/configuration', authority: 'role:assign_permission', method: 'PUT' },
  { path: '/roles/999999', authority: 'role:delete', method: 'DELETE' },
  { path: '/mrp/batches/999999/precheck', authority: 'mrp_batch:precheck' },
  { path: '/mrp/batches/999999/calculate', authority: 'mrp_batch:calculate' },
  { path: '/mrp/batches/999999/publish', authority: 'mrp_batch:publish' },
  { path: '/mrp/batches/999999/revise', authority: 'mrp_batch:publish' },
  { path: '/mrp/batches/999999/transition', authority: 'mrp_batch:publish' },
  { path: '/mrp/batches/999999/overrides', authority: 'mrp_plan:adjust' },
  { path: '/mrp/batches/999999/linked-transfers/preview', authority: 'mrp_plan:adjust' },
  { path: '/mrp/overrides/999999/restore-auto', authority: 'mrp_plan:adjust' },
  { path: '/mrp/batches/999999/material-analysis', authority: 'mrp_shortage:manage' },
  { path: '/mrp/batches/999999/material-confirmation', authority: 'mrp_shortage:confirm' },
  { path: '/mrp/batches/999999/schedule-confirmation', authority: 'mrp_schedule:confirm' },
  { path: '/mrp/batches/999999/issues', authority: 'mrp_decision:manage' },
  { path: '/mrp/batches/999999/execution/confirm', authority: 'mrp_execution:confirm' },
  { path: '/mrp/inventory/allocations/999999/reset', authority: 'mrp_inventory:allocate' },
  { path: '/mrp/stock-policies/999999', authority: 'mrp_stock_policy:manage', method: 'PUT' },
  { path: '/mrp/factory-warehouse-mappings/999999', authority: 'mrp_warehouse_mapping:manage', method: 'PUT' },
  { path: '/mrp/business-week-versions/999999', authority: 'mrp_business_week:manage', method: 'DELETE' }
]

try {
  for (const role of roles) {
    role.userId = Number(sql(`INSERT INTO user(username,password_hash,need_change_password)
      VALUES('${role.username}','${hash}',0); SELECT LAST_INSERT_ID();`))
    sql(`INSERT INTO user_role(user_id,role_id) VALUES(${role.userId},${role.id});`)
  }
  for (const role of roles) {
    const expected = sql(`SELECT DISTINCT p.code FROM permission p
      JOIN role_permission rp ON rp.permission_id=p.id JOIN role r ON r.id=rp.role_id
      WHERE r.id=${role.id} AND r.status='ACTIVE' AND p.status='ACTIVE' ORDER BY p.code;`).split('\n').filter(Boolean)
    const scope = sql(`SELECT GROUP_CONCAT(CONCAT(scope_type,':',IFNULL(scope_value,'')) ORDER BY id)
      FROM role_data_scope WHERE role_id=${role.id};`)
    const token = await login(role.username, password)
    const me = await api('/auth/me', token)
    check(me.status === 200 && me.body.success, `${role.code} /auth/me`)
    const actual = [...me.body.data.permissions]
    const samePermissions = actual.length === expected.length && expected.every(code => actual.includes(code))
    if (!samePermissions) {
      result.permissionDifference = { role: role.code, missing: expected.filter(code => !actual.includes(code)),
        extra: actual.filter(code => !expected.includes(code)) }
    }
    check(samePermissions, `${role.code} effective permissions differ from SQL`)
    const hasBatchPermission = expected.some(code => ['mrp_workspace:view', 'mrp_shortage:view', 'mrp_batch:list'].includes(code))
    const hasAllFactories = scope.split(',').includes('ALL:')
    const batch = await api('/mrp/batches/2', token)
    const expectedStatus = hasBatchPermission && hasAllFactories ? 200 : 403
    check(batch.status === expectedStatus, `${role.code} batch status ${batch.status}, expected ${expectedStatus}`)
    if (expectedStatus === 200) check(batch.body.data?.id === 2 && batch.body.data?.status === 'PUBLISHED', `${role.code} batch data`)
    const rolePage = await api('/roles/1/data-scope', token)
    const canReadRole = expected.includes('role:view') || expected.includes('role:assign_permission')
    check(rolePage.status === (canReadRole ? 200 : 403), `${role.code} role page status`)
    if (!expected.includes('role:assign_permission')) {
      const deniedWrite = await api('/roles/1/configuration', token, 'PUT', { permissionIds: [], dataScope: { scopeType: 'NONE', factoryCodes: [] } })
      check(deniedWrite.status === 403, `${role.code} role configuration write`)
    }
    const endpoints = []
    for (const item of readCases) {
      const response = await api(item.path, token)
      const authorized = item.authorities.some(code => expected.includes(code))
      check(authorized ? [200, 403].includes(response.status) : response.status === 403,
        `${role.code} GET ${item.path}: ${response.status}, authorized=${authorized}`)
      if (factoryScopedLists.has(item.path) && authorized && !hasAllFactories && response.status === 200) {
        check(Array.isArray(response.body.data) && response.body.data.length === 0,
          `${role.code} without factory scope can see ${item.path}`)
      }
      endpoints.push({ method: 'GET', path: item.path, status: response.status, authorized, hasAllFactories,
        rowCount: Array.isArray(response.body.data) ? response.body.data.length : undefined })
    }
    for (const item of deniedWriteCases) {
      if (expected.includes(item.authority)) continue
      const method = item.method || 'POST'
      const response = await api(item.path, token, method, item.body || {})
      check(response.status === 403, `${role.code} ${method} ${item.path}: ${response.status}`)
      endpoints.push({ method, path: item.path, status: response.status, authorized: false })
    }
    result.roles.push({ code: role.code, permissionCount: expected.length, scope, batchStatus: batch.status,
      rolePageStatus: rolePage.status, unauthorizedRoleWriteStatus: expected.includes('role:assign_permission') ? null : 403,
      endpoints })
  }
  scopedRoleId = Number(sql(`INSERT INTO role(code,name) VALUES('${scopedUsername}','限定工厂临时角色'); SELECT LAST_INSERT_ID();`))
  sql(`INSERT INTO role_permission(role_id,permission_id) SELECT ${scopedRoleId},id FROM permission
    WHERE code IN ('mrp_factory:view','mrp_warehouse_mapping:view','mrp_calendar:view','mrp_stock_policy:view');
    INSERT INTO role_data_scope(role_id,scope_type,scope_value) VALUES(${scopedRoleId},'FACTORY','爱培科');`)
  scopedUserId = Number(sql(`INSERT INTO user(username,password_hash,need_change_password)
    VALUES('${scopedUsername}','${hash}',0); SELECT LAST_INSERT_ID();`))
  sql(`INSERT INTO user_role(user_id,role_id) VALUES(${scopedUserId},${scopedRoleId});`)
  const scopedToken = await login(scopedUsername, password)
  result.singleFactory = []
  for (const path of ['/mrp/factories', '/mrp/factory-master',
    '/mrp/factory-warehouse-mappings', '/mrp/production-calendars']) {
    const response = await api(path, scopedToken)
    check(response.status === 200 && Array.isArray(response.body.data), `scoped GET ${path}: ${response.status}`)
    const rows = response.body.data
    check(rows.every(row => (typeof row === 'string' ? row : row.factoryCode) === '爱培科'),
      `scoped GET ${path} returned another factory`)
    result.singleFactory.push({ path, rows: rows.length })
  }
  check(result.singleFactory[0].rows === 1 && result.singleFactory[1].rows === 1,
    'single-factory role did not see its own factory')
  const mappingPage = await api('/mrp/factory-warehouse-mappings/page?pageSize=100', scopedToken)
  check(mappingPage.status === 200 && Array.isArray(mappingPage.body.data?.items), 'scoped mapping page')
  check(mappingPage.body.data.items.every(row => row.factoryCode === '爱培科'), 'scoped mapping page returned another factory')
  const stockPolicies = await api('/mrp/stock-policies', scopedToken)
  check(stockPolicies.status === 200 && Array.isArray(stockPolicies.body.data), 'scoped stock policies')
  const ownWarehouses = sql("SELECT warehouse_code FROM factory_warehouse_mapping WHERE factory_code='爱培科';")
    .split('\n').filter(Boolean)
  check(stockPolicies.body.data.every(row => ownWarehouses.includes(row.warehouseCode)),
    'scoped stock policies returned another warehouse')
  const calendarVersions = await api('/mrp/production-calendar-versions', scopedToken)
  check(calendarVersions.status === 200 && Array.isArray(calendarVersions.body.data), 'scoped calendar versions')
  check(calendarVersions.body.data.every(row => row.factoryCode === '爱培科'),
    'scoped calendar versions returned another factory')
  const foreignCalendar = await api('/mrp/production-calendars/month?factoryCode=永惠&month=2026-11', scopedToken)
  check(foreignCalendar.status === 403, `foreign calendar month: ${foreignCalendar.status}`)
  result.singleFactory.push({ path: '/mrp/factory-warehouse-mappings/page', rows: mappingPage.body.data.items.length,
    total: mappingPage.body.data.total })
  result.singleFactory.push({ path: '/mrp/stock-policies', rows: stockPolicies.body.data.length })
  result.singleFactory.push({ path: '/mrp/production-calendar-versions', rows: calendarVersions.body.data.length })
  result.singleFactory.push({ path: '/mrp/production-calendars/month?factoryCode=永惠&month=2026-11', status: foreignCalendar.status })
  result.status = 'PASS'
} catch (error) {
  result.status = 'FAIL'
  result.failure = String(error)
} finally {
  for (const role of roles) {
    if (!role.userId) continue
    sql(`DELETE FROM audit_log WHERE operator_id=${role.userId};
      DELETE FROM user_role WHERE user_id=${role.userId}; DELETE FROM user WHERE id=${role.userId};`)
  }
  if (scopedUserId) sql(`DELETE FROM audit_log WHERE operator_id=${scopedUserId};
    DELETE FROM user_role WHERE user_id=${scopedUserId}; DELETE FROM user WHERE id=${scopedUserId};`)
  if (scopedRoleId) sql(`DELETE FROM role_permission WHERE role_id=${scopedRoleId};
    DELETE FROM role_data_scope WHERE role_id=${scopedRoleId}; DELETE FROM role WHERE id=${scopedRoleId};`)
  result.remainingUsers = Number(sql(`SELECT COUNT(*) FROM user WHERE username LIKE 'matrix_${suffix}_%';`))
  result.remainingScopedUser = Number(sql(`SELECT COUNT(*) FROM user WHERE username='${scopedUsername}';`))
  result.remainingScopedRole = Number(sql(`SELECT COUNT(*) FROM role WHERE code='${scopedUsername}';`))
  result.remainingAudit = Number(sql(`SELECT COUNT(*) FROM audit_log WHERE operator_id IN
    (${roles.filter(role => role.userId).map(role => role.userId).join(',')});`))
  result.batchUnchanged = sql('SELECT status,active_run_id,result_checksum FROM mrp_calc_batch WHERE id=2;') === baseline
  if (result.remainingUsers !== 0 || result.remainingAudit !== 0 || result.remainingScopedUser !== 0
      || result.remainingScopedRole !== 0 || !result.batchUnchanged) result.status = 'FAIL'
  mkdirSync(dirname(output), { recursive: true })
  writeFileSync(output, JSON.stringify(result, null, 2) + '\n')
}
console.log(JSON.stringify({ status: result.status, assertions: result.assertions, roles: result.roles.length,
  remainingUsers: result.remainingUsers, batchUnchanged: result.batchUnchanged }))
if (result.status !== 'PASS') process.exitCode = 1
