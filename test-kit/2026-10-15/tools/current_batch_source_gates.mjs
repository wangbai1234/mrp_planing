#!/usr/bin/env node
// B02: verify source prerequisites against the isolated API and restore the BOM fixture.
import { execFileSync } from 'node:child_process'
import { mkdirSync, writeFileSync } from 'node:fs'
import { resolve } from 'node:path'

const output = resolve(import.meta.dirname, '../evidence/runs/b02-source-gates-' + Date.now())
mkdirSync(output, { recursive: true })
const backup = 'b02_bom_backup_' + Date.now()
const result = { environment: '8182/3317/6391', status: 'IN_PROGRESS' }
let token
let batchId
let backedUp = false
let emptied = false

function sql(query) {
  return execFileSync('docker', ['exec', '-i', 'mrp-testkit-mysql', 'sh', '-c',
    'MYSQL_PWD="$MYSQL_PASSWORD" mysql --default-character-set=utf8mb4 -N -B -u"$MYSQL_USER" mrp_testkit'],
    { input: query, encoding: 'utf8' }).trim().split('\n').filter(Boolean)
}

async function request(path, method = 'GET') {
  const response = await fetch('http://127.0.0.1:8182/api/v1' + path, {
    method, headers: token ? { Authorization: `Bearer ${token}` } : {},
  })
  return { http: response.status, body: await response.json() }
}

function check(condition, label, detail) {
  if (!condition) throw new Error(label + ': ' + JSON.stringify(detail))
}

try {
  const response = await fetch('http://127.0.0.1:8182/api/v1/auth/login', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: 'admin', password: 'admin123' }),
  })
  const login = await response.json()
  check(response.status === 200 && login.data?.token, 'Login failed', login)
  token = login.data.token
  result.beforeBatchCount = Number(sql('SELECT COUNT(*) FROM mrp_calc_batch;')[0])
  result.absentFactoryDemand = await request('/mrp/batches?factoryCode=%E6%B0%B8%E6%83%A0&forecastVersionId=43&inventorySnapshotId=46', 'POST')
  check(result.absentFactoryDemand.http === 400 && result.absentFactoryDemand.body.error?.code === 'NO_FACTORY_FORECAST', 'No-demand factory was accepted', result.absentFactoryDemand)
  check(Number(sql('SELECT COUNT(*) FROM mrp_calc_batch;')[0]) === result.beforeBatchCount, 'No-demand request created a batch')

  result.bomCount = Number(sql('SELECT COUNT(*) FROM bom_detail WHERE org_name="上海七十迈数字科技有限公司";')[0])
  check(result.bomCount > 0, 'No current BOM fixture', result.bomCount)
  sql(`CREATE TABLE ${backup} LIKE bom_detail; INSERT INTO ${backup} SELECT * FROM bom_detail WHERE org_name='上海七十迈数字科技有限公司';`)
  backedUp = true
  check(Number(sql(`SELECT COUNT(*) FROM ${backup};`)[0]) === result.bomCount, 'BOM backup incomplete')
  sql("DELETE FROM bom_detail WHERE org_name='上海七十迈数字科技有限公司';")
  emptied = true
  check(Number(sql('SELECT COUNT(*) FROM bom_detail WHERE org_name="上海七十迈数字科技有限公司";')[0]) === 0, 'BOM source still populated')
  result.currentBom = await request('/mrp/batches/current-bom-status')
  check(result.currentBom.http === 200 && result.currentBom.body.data?.localCount === 0, 'BOM status did not show empty', result.currentBom)
  result.noBomCreate = await request('/mrp/batches?factoryCode=ALL&forecastVersionId=38&inventorySnapshotId=46', 'POST')
  check(result.noBomCreate.http === 400 && JSON.stringify(result.noBomCreate.body).includes('尚无上海组织'), 'Missing BOM did not block creation', result.noBomCreate)
  check(Number(sql('SELECT COUNT(*) FROM mrp_calc_batch;')[0]) === result.beforeBatchCount, 'Missing BOM created a batch')
  sql(`INSERT INTO bom_detail SELECT * FROM ${backup};`)
  check(Number(sql('SELECT COUNT(*) FROM bom_detail WHERE org_name="上海七十迈数字科技有限公司";')[0]) === result.bomCount, 'BOM restore count mismatch')
  emptied = false
  sql(`DROP TABLE ${backup};`)
  backedUp = false

  result.oneFactoryWithoutDemand = await request('/mrp/batches?factoryCode=ALL&forecastVersionId=43&inventorySnapshotId=46', 'POST')
  check(result.oneFactoryWithoutDemand.http === 200 && result.oneFactoryWithoutDemand.body.data?.status === 'DRAFT', 'One empty factory incorrectly blocked ALL batch', result.oneFactoryWithoutDemand)
  batchId = result.oneFactoryWithoutDemand.body.data.id
  result.batchId = batchId
  result.status = 'PASS'
} catch (error) {
  result.status = 'FAIL'
  result.error = String(error)
} finally {
  try {
    if (emptied) {
      sql(`INSERT INTO bom_detail SELECT * FROM ${backup};`)
      check(Number(sql('SELECT COUNT(*) FROM bom_detail WHERE org_name="上海七十迈数字科技有限公司";')[0]) === result.bomCount, 'BOM source restore mismatch')
    }
    if (backedUp) sql(`DROP TABLE ${backup};`)
    if (batchId) result.cancel = await request(`/mrp/batches/${batchId}/cancel`, 'POST')
    result.restoredBomCount = Number(sql('SELECT COUNT(*) FROM bom_detail WHERE org_name="上海七十迈数字科技有限公司";')[0])
    check(result.restoredBomCount === result.bomCount, 'BOM source not restored', result.restoredBomCount)
    if (batchId) check(result.cancel.http === 200 && result.cancel.body.data?.status === 'CANCELLED', 'Test batch not cancelled', result.cancel)
  } catch (cleanupError) {
    result.status = 'FAIL'
    result.cleanupError = String(cleanupError)
  }
  writeFileSync(resolve(output, 'result.json'), JSON.stringify(result, null, 2))
  process.stdout.write(JSON.stringify({ evidence: output, status: result.status, batchId, error: result.error, cleanupError: result.cleanupError }) + '\n')
  if (result.status !== 'PASS') process.exitCode = 1
}
