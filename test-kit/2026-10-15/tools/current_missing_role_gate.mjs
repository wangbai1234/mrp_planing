#!/usr/bin/env node
// B04: isolate a disabled finished-machine role and restore the published fixture.
import { execFileSync } from 'node:child_process'
import { mkdirSync, writeFileSync } from 'node:fs'
import { resolve } from 'node:path'

const output = resolve(import.meta.dirname, '../evidence/runs/b04-role-gate-' + Date.now())
mkdirSync(output, { recursive: true })
const result = { environment: '8182/3317/6391', status: 'IN_PROGRESS' }
let token
let batchId
let changed = false

function sql(query) {
  return execFileSync('docker', ['exec', '-i', 'mrp-testkit-mysql', 'sh', '-c',
    'MYSQL_PWD="$MYSQL_PASSWORD" mysql --default-character-set=utf8mb4 -N -B -u"$MYSQL_USER" mrp_testkit'],
    { input: query, encoding: 'utf8' }).trim().split('\n').filter(Boolean)
}

async function request(path, method = 'GET', body) {
  const response = await fetch('http://127.0.0.1:8182/api/v1' + path, {
    method,
    headers: { ...(token ? { Authorization: `Bearer ${token}` } : {}), ...(body ? { 'Content-Type': 'application/json' } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  })
  const payload = await response.json()
  return { http: response.status, data: payload.data, code: payload.code, message: payload.message }
}

function check(condition, label, detail) {
  if (!condition) throw new Error(label + ': ' + JSON.stringify(detail))
}

try {
  const login = await request('/auth/login', 'POST', { username: 'admin', password: 'admin123' })
  check(login.http === 200 && login.data?.token, 'Login failed', login)
  token = login.data.token
  result.originalRole = sql('SELECT role,enabled FROM mrp_category_role_detail WHERE id=2208 AND version_id=9 AND category_code="09002";')
  check(result.originalRole.length === 1 && result.originalRole[0] === 'FINISHED_GOOD\t1', 'Role fixture changed', result.originalRole)
  sql('UPDATE mrp_category_role_detail SET enabled=0 WHERE id=2208 AND version_id=9 AND category_code="09002";')
  changed = true
  result.created = await request('/mrp/batches?factoryCode=ALL&forecastVersionId=38&inventorySnapshotId=46&categoryRoleVersionId=9', 'POST')
  check(result.created.http === 200 && result.created.data?.status === 'DRAFT', 'Create failed', result.created)
  batchId = result.created.data.id
  result.batchId = batchId
  result.precheck = await request(`/mrp/batches/${batchId}/precheck`, 'POST')
  check(result.precheck.http === 200 && result.precheck.data?.status === 'WAITING_DECISION', 'Role did not block precheck', result.precheck)
  result.issues = sql(`SELECT id,issue_type,status,scope_key,factory_code FROM mrp_decision_issue WHERE batch_id=${batchId} ORDER BY id;`)
  check(result.issues.length === 2 && result.issues.every(row => row.includes('\tCATEGORY_ROLE_DISABLED\tOPEN\t6839AA000365\t')), 'Role issues not traced by factory', result.issues)
  result.calculateWhileOpen = await request(`/mrp/batches/${batchId}/calculate`, 'POST')
  check(result.calculateWhileOpen.http === 400, 'Open role issue allowed calculation', result.calculateWhileOpen)
  for (const row of result.issues) {
    const issueId = Number(row.split('\t')[0])
    const resolved = await request(`/mrp/batches/issues/${issueId}/resolve?decisionCode=MANUAL_CONFIRM&reason=B04%20isolated%20role%20gate`, 'POST')
    check(resolved.http === 200, 'Role issue resolution failed', resolved)
  }
  result.afterResolution = await request(`/mrp/batches/${batchId}`)
  check(result.afterResolution.data?.status === 'READY_TO_CALCULATE', 'Resolved role issue did not release precheck', result.afterResolution)
  result.resolvedIssues = sql(`SELECT issue_type,status,factory_code FROM mrp_decision_issue WHERE batch_id=${batchId} ORDER BY id;`)
  check(result.resolvedIssues.length === 2 && result.resolvedIssues.every(row => row.includes('\tRESOLVED\t')), 'Role decisions not persisted', result.resolvedIssues)
  result.status = 'PASS'
} catch (error) {
  result.status = 'FAIL'
  result.error = String(error)
} finally {
  try {
    if (batchId) result.cancel = await request(`/mrp/batches/${batchId}/cancel`, 'POST')
    if (changed) sql('UPDATE mrp_category_role_detail SET enabled=1 WHERE id=2208 AND version_id=9 AND category_code="09002";')
    result.restoredRole = sql('SELECT role,enabled FROM mrp_category_role_detail WHERE id=2208 AND version_id=9 AND category_code="09002";')
    check(result.restoredRole[0] === result.originalRole?.[0], 'Role fixture was not restored', result.restoredRole)
    if (batchId) check(result.cancel.http === 200 && result.cancel.data?.status === 'CANCELLED', 'Batch cleanup failed', result.cancel)
  } catch (cleanupError) {
    result.status = 'FAIL'
    result.cleanupError = String(cleanupError)
  }
  writeFileSync(resolve(output, 'result.json'), JSON.stringify(result, null, 2))
  process.stdout.write(JSON.stringify({ evidence: output, status: result.status, batchId, error: result.error, cleanupError: result.cleanupError }) + '\n')
  if (result.status !== 'PASS') process.exitCode = 1
}
