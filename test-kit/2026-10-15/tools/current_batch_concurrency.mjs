#!/usr/bin/env node
// B16: concurrent requests share one batch, one calculation and one effective run.
import { execFileSync } from 'node:child_process'
import { mkdirSync, writeFileSync } from 'node:fs'
import { resolve } from 'node:path'

const output = resolve(import.meta.dirname, '../evidence/runs/b16-concurrency-' + Date.now())
mkdirSync(output, { recursive: true })
const result = { environment: '8182/3317/6391', status: 'IN_PROGRESS' }
let token
let secondToken
let batchId

function sql(query) {
  return execFileSync('docker', ['exec', '-i', 'mrp-testkit-mysql', 'sh', '-c',
    'MYSQL_PWD="$MYSQL_PASSWORD" mysql --default-character-set=utf8mb4 -N -B -u"$MYSQL_USER" mrp_testkit'],
    { input: query, encoding: 'utf8' }).trim().split('\n').filter(Boolean)
}

async function request(path, method = 'GET', body, bearer = token) {
  const response = await fetch('http://127.0.0.1:8182/api/v1' + path, {
    method,
    headers: { Authorization: `Bearer ${bearer}`, ...(body ? { 'Content-Type': 'application/json' } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  })
  return { http: response.status, body: await response.json() }
}

function check(condition, label, detail) {
  if (!condition) throw new Error(label + ': ' + JSON.stringify(detail))
}

async function waitTask(id) {
  for (let attempt = 0; attempt < 90; attempt++) {
    await new Promise(done => setTimeout(done, 1000))
    const task = await request(`/mrp/batches/${id}/calculation-task`)
    if (['SUCCEEDED', 'FAILED'].includes(task.body.data?.status)) return task
  }
  throw new Error('Worker did not finish within 90 seconds')
}

try {
  const loginResponse = await fetch('http://127.0.0.1:8182/api/v1/auth/login', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: 'admin', password: 'admin123' }),
  })
  const login = await loginResponse.json()
  check(loginResponse.status === 200 && login.data?.token, 'Login failed', login)
  token = login.data.token
  await new Promise(done => setTimeout(done, 1100))
  const secondResponse = await fetch('http://127.0.0.1:8182/api/v1/auth/login', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: 'admin', password: 'admin123' }),
  })
  const secondLogin = await secondResponse.json()
  check(secondResponse.status === 200 && secondLogin.data?.token, 'Second login failed', secondLogin)
  secondToken = secondLogin.data.token
  result.independentSessions = token !== secondToken
  check(result.independentSessions, 'Login sessions were not independent')
  const createPath = '/mrp/batches?factoryCode=ALL&forecastVersionId=38&inventorySnapshotId=46'
  result.created = await request(createPath, 'POST')
  check(result.created.http === 200, 'Create failed', result.created)
  batchId = result.created.body.data.id
  result.batchId = batchId
  result.secondCreate = await request(createPath, 'POST')
  check(result.secondCreate.http === 409 && result.secondCreate.body.error?.code === 'ACTIVE_BATCH_EXISTS', 'Second active batch was accepted', result.secondCreate)
  result.parallelPrecheck = await Promise.all([
    request(`/mrp/batches/${batchId}/precheck`, 'POST', undefined, token),
    request(`/mrp/batches/${batchId}/precheck`, 'POST', undefined, secondToken),
  ])
  check(result.parallelPrecheck.filter(item => item.http === 200).length === 1
    && result.parallelPrecheck.filter(item => item.http === 400).length === 1, 'Precheck race did not serialize', result.parallelPrecheck)
  result.parallelCalculate = await Promise.all([
    request(`/mrp/batches/${batchId}/calculate`, 'POST', undefined, token),
    request(`/mrp/batches/${batchId}/calculate`, 'POST', undefined, secondToken),
  ])
  check(result.parallelCalculate.filter(item => item.http === 200).length === 1
    && result.parallelCalculate.filter(item => item.http === 400).length === 1, 'Calculation race did not serialize', result.parallelCalculate)
  result.worker = await waitTask(batchId)
  check(result.worker.body.data?.status === 'SUCCEEDED', 'Worker failed', result.worker)
  result.batch = await request(`/mrp/batches/${batchId}`)
  check(result.batch.body.data?.status === 'SCHEDULE_READY', 'Candidate schedule missing', result.batch)
  result.calculationTasks = sql(`SELECT id,status FROM calc_task WHERE task_type='MRP_CALCULATION' AND JSON_EXTRACT(input_payload,'$.batchId')=${batchId} ORDER BY id;`)
  result.runs = sql(`SELECT id,status FROM mrp_calc_run WHERE batch_id=${batchId} ORDER BY id;`)
  check(result.calculationTasks.length === 1 && result.runs.length === 1, 'Duplicate task or run', { tasks: result.calculationTasks, runs: result.runs })
  const review = await request(`/mrp/batches/${batchId}/reviews`)
  const fingerprint = review.body.data?.scheduleFingerprint
  check(typeof fingerprint === 'string' && fingerprint.length === 64, 'Schedule fingerprint missing', review)
  result.parallelConfirm = await Promise.all([
    request(`/mrp/batches/${batchId}/schedule-confirmation`, 'POST', { fingerprint }, token),
    request(`/mrp/batches/${batchId}/schedule-confirmation`, 'POST', { fingerprint }, secondToken),
  ])
  check(result.parallelConfirm.every(item => item.http === 200 && item.body.data?.schedule === 'CONFIRMED'), 'Concurrent confirmation failed', result.parallelConfirm)
  result.confirmations = sql(`SELECT review_type,run_id,schedule_fingerprint FROM mrp_workbench_review WHERE batch_id=${batchId} ORDER BY id;`)
  check(result.confirmations.every(row => row === `SCHEDULE\t${result.batch.body.data.activeRunId}\t${fingerprint}`), 'Confirmation bound to wrong run or fingerprint', result.confirmations)
  result.status = result.confirmations.length === 1 ? 'PASS' : 'FAIL'
  if (result.status === 'FAIL') result.error = `Concurrent confirmation wrote ${result.confirmations.length} duplicate reviews`
} catch (error) {
  result.status = 'FAIL'
  result.error = String(error)
} finally {
  try {
    if (batchId) result.cancel = await request(`/mrp/batches/${batchId}/cancel`, 'POST')
    if (batchId) check(result.cancel.http === 200 && result.cancel.body.data?.status === 'CANCELLED', 'Test batch not cancelled', result.cancel)
  } catch (cleanupError) {
    result.status = 'FAIL'
    result.cleanupError = String(cleanupError)
  }
  writeFileSync(resolve(output, 'result.json'), JSON.stringify(result, null, 2))
  process.stdout.write(JSON.stringify({ evidence: output, status: result.status, batchId, error: result.error, cleanupError: result.cleanupError }) + '\n')
  if (result.status !== 'PASS') process.exitCode = 1
}
