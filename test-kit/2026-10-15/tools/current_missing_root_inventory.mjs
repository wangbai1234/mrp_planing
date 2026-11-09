#!/usr/bin/env node
// B04: a missing machine stock row belongs to material review after scheduling.
import { execFileSync } from 'node:child_process'
import { mkdirSync, writeFileSync } from 'node:fs'
import { resolve } from 'node:path'

const base = 'http://127.0.0.1:8182/api/v1'
const output = resolve(import.meta.dirname, '../evidence/runs/b04-missing-root-' + Date.now())
mkdirSync(output, { recursive: true })
const result = { environment: '8182/3317/6391', status: 'IN_PROGRESS', cases: [] }
let token

async function request(path, method = 'GET', body) {
  const response = await fetch(base + path, {
    method,
    headers: { ...(token ? { Authorization: `Bearer ${token}` } : {}), ...(body ? { 'Content-Type': 'application/json' } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  })
  const payload = await response.json()
  return { http: response.status, data: payload.data, code: payload.code, message: payload.message }
}

function sql(query) {
  return execFileSync('docker', ['exec', '-i', 'mrp-testkit-mysql', 'sh', '-c',
    'MYSQL_PWD="$MYSQL_PASSWORD" mysql --default-character-set=utf8mb4 -N -B -u"$MYSQL_USER" mrp_testkit'],
    { input: query, encoding: 'utf8' }).trim().split('\n').filter(Boolean)
}

function check(ok, message, detail) {
  if (!ok) throw new Error(message + ': ' + JSON.stringify(detail))
}

async function waitTask(batchId) {
  for (let attempt = 0; attempt < 120; attempt++) {
    await new Promise(done => setTimeout(done, 1000))
    const task = await request(`/mrp/batches/${batchId}/calculation-task`)
    if (['SUCCEEDED', 'FAILED'].includes(task.data?.status)) return task
  }
  throw new Error('Worker did not finish within 120 seconds')
}

try {
  const login = await request('/auth/login', 'POST', { username: 'admin', password: 'admin123' })
  check(login.http === 200 && login.data?.token, 'Login failed', login)
  token = login.data.token
  for (const testCase of [
    { name: 'missing', snapshot: 45, expectedOpen: 1 },
    { name: 'explicit-zero', snapshot: 46, expectedOpen: 0 },
  ]) {
    const item = { ...testCase, status: 'IN_PROGRESS' }
    result.cases.push(item)
    let batchId
    try {
      item.balance = sql(`SELECT warehouse_scope,inventory_quality,available_quantity FROM mrp_inventory_balance WHERE inventory_snapshot_id=${testCase.snapshot} AND factory_code='爱培科' AND material_code='6839AA000365';`)
      check(testCase.name === 'missing' ? item.balance.every(row => !row.includes('GOOD')) : item.balance.some(row => row.includes('GOOD\t0.000000')), 'Fixture mismatch', item.balance)
      item.created = await request(`/mrp/batches?factoryCode=ALL&forecastVersionId=38&inventorySnapshotId=${testCase.snapshot}`, 'POST')
      check(item.created.http === 200 && item.created.data?.status === 'DRAFT', 'Create failed', item.created)
      batchId = item.created.data.id
      item.batchId = batchId
      item.precheck = await request(`/mrp/batches/${batchId}/precheck`, 'POST')
      check(item.precheck.http === 200 && item.precheck.data?.status === 'READY_TO_CALCULATE', 'Precheck blocked candidate', item.precheck)
      item.calculation = await request(`/mrp/batches/${batchId}/calculate`, 'POST')
      check(item.calculation.http === 200, 'Schedule submission failed', item.calculation)
      item.scheduleTask = await waitTask(batchId)
      check(item.scheduleTask.data?.status === 'SUCCEEDED', 'Schedule Worker failed', item.scheduleTask)
      item.scheduled = await request(`/mrp/batches/${batchId}`)
      check(item.scheduled.data?.status === 'SCHEDULE_READY', 'Candidate schedule missing', item.scheduled)
      const review = await request(`/mrp/batches/${batchId}/reviews`)
      check(review.http === 200 && review.data?.scheduleFingerprint, 'Schedule fingerprint missing', review)
      item.confirmedSchedule = await request(`/mrp/batches/${batchId}/schedule-confirmation`, 'POST', { fingerprint: review.data.scheduleFingerprint })
      check(item.confirmedSchedule.http === 200 && item.confirmedSchedule.data?.schedule === 'CONFIRMED', 'Schedule confirmation failed', item.confirmedSchedule)
      item.materialTask = await request(`/mrp/batches/${batchId}/material-analysis`, 'POST')
      check(item.materialTask.http === 202, 'Material submission failed', item.materialTask)
      item.materialWorker = await waitTask(batchId)
      check(item.materialWorker.data?.status === 'SUCCEEDED', 'Material Worker failed', item.materialWorker)
      item.analyzed = await request(`/mrp/batches/${batchId}`)
      check(item.analyzed.data?.status === 'MATERIAL_REVIEW', 'Material review state missing', item.analyzed)
      item.rootIssues = sql(`SELECT id,issue_type,status,scope_key,factory_code FROM mrp_decision_issue WHERE batch_id=${batchId} AND issue_type='INVENTORY_MISSING' AND scope_key='6839AA000365' AND factory_code='爱培科';`)
      check(item.rootIssues.filter(row => row.includes('\tOPEN\t')).length === testCase.expectedOpen, 'Missing inventory issue count mismatch', item.rootIssues)
      if (testCase.expectedOpen) {
        const issueId = Number(item.rootIssues[0].split('\t')[0])
        item.confirmBeforeResolve = await request(`/mrp/batches/${batchId}/material-confirmation`, 'POST', { fingerprint: (await request(`/mrp/batches/${batchId}/reviews`)).data.materialFingerprint })
        check(item.confirmBeforeResolve.http === 400, 'Unresolved stock omission did not block confirmation', item.confirmBeforeResolve)
        item.resolved = await request(`/mrp/batches/${batchId}/issues/inventory-missing/resolve`, 'POST', { issueIds: [issueId], reason: 'B04 isolated test: stock row omitted; calculate as zero for this batch' })
        check(item.resolved.http === 200, 'Material controller could not resolve missing inventory', item.resolved)
        item.resolvedIssue = sql(`SELECT status FROM mrp_decision_issue WHERE id=${issueId};`)
        check(item.resolvedIssue[0] === 'RESOLVED', 'Resolution not persisted', item.resolvedIssue)
      }
      item.status = 'PASS'
    } finally {
      if (batchId) {
        item.cancel = await request(`/mrp/batches/${batchId}/cancel`, 'POST')
        check(item.cancel.http === 200 && item.cancel.data?.status === 'CANCELLED', 'Test batch cleanup failed', item.cancel)
      }
      writeFileSync(resolve(output, 'progress.json'), JSON.stringify(result, null, 2))
    }
  }
  result.status = 'PASS'
} catch (error) {
  result.status = 'FAIL'
  result.error = String(error)
} finally {
  writeFileSync(resolve(output, 'result.json'), JSON.stringify(result, null, 2))
  process.stdout.write(JSON.stringify({ evidence: output, status: result.status, cases: result.cases.map(item => ({ name: item.name, status: item.status, batchId: item.batchId })), error: result.error }) + '\n')
  if (result.status !== 'PASS') process.exitCode = 1
}
