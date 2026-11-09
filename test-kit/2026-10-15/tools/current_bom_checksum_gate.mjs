#!/usr/bin/env node
// B15: current BOM mutation invalidates a frozen draft until its source is restored.
import { execFileSync } from 'node:child_process'
import { mkdirSync, writeFileSync } from 'node:fs'
import { resolve } from 'node:path'

const output = resolve(import.meta.dirname, '../evidence/runs/b15-bom-checksum-' + Date.now())
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

async function request(path, method = 'GET') {
  const response = await fetch('http://127.0.0.1:8182/api/v1' + path, {
    method, headers: { Authorization: `Bearer ${token}` },
  })
  return { http: response.status, body: await response.json() }
}

function check(condition, label, detail) {
  if (!condition) throw new Error(label + ': ' + JSON.stringify(detail))
}

try {
  const loginResponse = await fetch('http://127.0.0.1:8182/api/v1/auth/login', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: 'admin', password: 'admin123' }),
  })
  const login = await loginResponse.json()
  check(loginResponse.status === 200 && login.data?.token, 'Login failed', login)
  token = login.data.token
  result.originalParentQty = sql('SELECT parent_qty FROM bom_detail WHERE id=1 AND org_name="上海七十迈数字科技有限公司";')[0]
  check(Boolean(result.originalParentQty), 'BOM fixture missing')
  const created = await request('/mrp/batches?factoryCode=ALL&forecastVersionId=38&inventorySnapshotId=46', 'POST')
  check(created.http === 200, 'Create failed', created)
  batchId = created.body.data.id
  result.batchId = batchId
  result.frozenChecksum = created.body.data.bomSourceChecksum
  sql('UPDATE bom_detail SET parent_qty=parent_qty+1 WHERE id=1 AND org_name="上海七十迈数字科技有限公司";')
  changed = true
  result.blockedPrecheck = await request(`/mrp/batches/${batchId}/precheck`, 'POST')
  check(result.blockedPrecheck.http === 400 && JSON.stringify(result.blockedPrecheck.body).includes('BOM管理数据已重新同步'), 'Changed BOM did not block precheck', result.blockedPrecheck)
  result.duringChange = await request(`/mrp/batches/${batchId}`)
  check(result.duringChange.body.data?.status === 'DRAFT'
    && result.duringChange.body.data?.bomSourceChecksum === result.frozenChecksum, 'Frozen draft changed after BOM edit', result.duringChange)
  check(sql(`SELECT COUNT(*) FROM mrp_calc_run WHERE batch_id=${batchId};`)[0] === '0', 'BOM conflict produced a run')
  sql(`UPDATE bom_detail SET parent_qty=${result.originalParentQty} WHERE id=1 AND org_name='上海七十迈数字科技有限公司';`)
  changed = false
  result.recoveredPrecheck = await request(`/mrp/batches/${batchId}/precheck`, 'POST')
  check(result.recoveredPrecheck.http === 200 && result.recoveredPrecheck.body.data?.status === 'READY_TO_CALCULATE', 'Restored BOM did not release precheck', result.recoveredPrecheck)
  check(result.recoveredPrecheck.body.data?.bomSourceChecksum === result.frozenChecksum, 'Frozen checksum changed after restore')
  result.status = 'PASS'
} catch (error) {
  result.status = 'FAIL'
  result.error = String(error)
} finally {
  try {
    if (changed) sql(`UPDATE bom_detail SET parent_qty=${result.originalParentQty} WHERE id=1 AND org_name='上海七十迈数字科技有限公司';`)
    if (batchId) result.cancel = await request(`/mrp/batches/${batchId}/cancel`, 'POST')
    result.restoredParentQty = sql('SELECT parent_qty FROM bom_detail WHERE id=1;')[0]
    check(result.restoredParentQty === result.originalParentQty, 'BOM source not restored')
    if (batchId) check(result.cancel.http === 200 && result.cancel.body.data?.status === 'CANCELLED', 'Test batch not cancelled', result.cancel)
  } catch (cleanupError) {
    result.status = 'FAIL'
    result.cleanupError = String(cleanupError)
  }
  writeFileSync(resolve(output, 'result.json'), JSON.stringify(result, null, 2))
  process.stdout.write(JSON.stringify({ evidence: output, status: result.status, batchId, error: result.error, cleanupError: result.cleanupError }) + '\n')
  if (result.status !== 'PASS') process.exitCode = 1
}
