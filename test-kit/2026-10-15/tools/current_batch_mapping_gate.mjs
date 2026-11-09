#!/usr/bin/env node
// B02: a selected factory without an enabled warehouse mapping cannot create a batch.
import { execFileSync } from 'node:child_process'
import { mkdirSync, writeFileSync } from 'node:fs'
import { resolve } from 'node:path'

const output = resolve(import.meta.dirname, '../evidence/runs/b02-mapping-gate-' + Date.now())
mkdirSync(output, { recursive: true })
const result = { environment: '8182/3317/6391', status: 'IN_PROGRESS' }
let changed = false

function sql(query) {
  return execFileSync('docker', ['exec', '-i', 'mrp-testkit-mysql', 'sh', '-c',
    'MYSQL_PWD="$MYSQL_PASSWORD" mysql --default-character-set=utf8mb4 -N -B -u"$MYSQL_USER" mrp_testkit'],
    { input: query, encoding: 'utf8' }).trim().split('\n').filter(Boolean)
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
  result.originalMapping = sql('SELECT factory_code,warehouse_code,enabled,quality_configured FROM factory_warehouse_mapping WHERE id=2;')
  check(result.originalMapping[0] === '永惠\tUAT_YH\t1\t1', 'Mapping fixture changed', result.originalMapping)
  result.beforeBatchCount = Number(sql('SELECT COUNT(*) FROM mrp_calc_batch;')[0])
  sql('UPDATE factory_warehouse_mapping SET enabled=0 WHERE id=2;')
  changed = true
  const response = await fetch('http://127.0.0.1:8182/api/v1/mrp/batches?factoryCode=ALL&forecastVersionId=38&inventorySnapshotId=46', {
    method: 'POST', headers: { Authorization: `Bearer ${login.data.token}` },
  })
  result.rejected = { http: response.status, body: await response.json() }
  check(response.status === 400 && ['FACTORY_WAREHOUSE_NOT_MAPPED', 'INVENTORY_WAREHOUSE_MISMATCH'].includes(result.rejected.body.error?.code), 'Missing mapping was accepted', result.rejected)
  check(Number(sql('SELECT COUNT(*) FROM mrp_calc_batch;')[0]) === result.beforeBatchCount, 'Missing mapping created a batch')
  result.status = 'PASS'
} catch (error) {
  result.status = 'FAIL'
  result.error = String(error)
} finally {
  try {
    if (changed) sql('UPDATE factory_warehouse_mapping SET enabled=1 WHERE id=2;')
    result.restoredMapping = sql('SELECT factory_code,warehouse_code,enabled,quality_configured FROM factory_warehouse_mapping WHERE id=2;')
    check(result.restoredMapping[0] === result.originalMapping?.[0], 'Mapping fixture was not restored', result.restoredMapping)
  } catch (cleanupError) {
    result.status = 'FAIL'
    result.cleanupError = String(cleanupError)
  }
  writeFileSync(resolve(output, 'result.json'), JSON.stringify(result, null, 2))
  process.stdout.write(JSON.stringify({ evidence: output, status: result.status, error: result.error, cleanupError: result.cleanupError }) + '\n')
  if (result.status !== 'PASS') process.exitCode = 1
}
