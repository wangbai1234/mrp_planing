import { execFileSync } from 'node:child_process'
import { readFileSync, mkdirSync, writeFileSync } from 'node:fs'
import { dirname } from 'node:path'

const output = process.argv[2]
const batchId = Number(process.argv[3])
if (!output?.endsWith('.json') || !Number.isSafeInteger(batchId) || batchId <= 0)
  throw new Error('Pass a new result.json path and temporary batch ID')
const base = 'http://127.0.0.1:8184/api/v1'
const result = { status: 'RUNNING', batchId, startedAt: new Date().toISOString(), samples: [] }
let monitor

function sql(query) {
  return execFileSync('docker', ['exec', '-i', 'mrp-testkit-mysql', 'sh', '-c',
    'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u"$MYSQL_USER" --default-character-set=utf8mb4 -N -B mrp_testkit'],
  { input: query, encoding: 'utf8', maxBuffer: 256 * 1024 * 1024 }).trim()
}

function sample() {
  try {
    const raw = execFileSync('docker', ['stats', '--no-stream', '--format', '{{.MemUsage}}|{{.CPUPerc}}',
      'mrp-bigcalc-worker-20261109'], { encoding: 'utf8' }).trim()
    result.samples.push({ at: new Date().toISOString(), raw })
  } catch (error) { result.sampleError = String(error) }
}

async function api(path, token, method = 'GET', data) {
  const response = await fetch(base + path, { method,
    headers: { Authorization: `Bearer ${token}`, ...(data ? { 'Content-Type': 'application/json' } : {}) },
    body: data ? JSON.stringify(data) : undefined, signal: AbortSignal.timeout(180000) })
  const body = await response.json()
  if (!response.ok || !body.success) throw new Error(`${method} ${path}: HTTP ${response.status}, ${body.error?.code ?? 'unknown'}`)
  return body.data
}

try {
  const initial = sql(`SELECT status,active_run_id FROM mrp_calc_batch WHERE id=${batchId};`)
  if (!initial.startsWith('SCHEDULE_READY\t')) throw new Error(`Unexpected batch state: ${initial}`)
  result.initial = initial
  const password = readFileSync('/tmp/mrp-testkit-admin-final-20261105.secret', 'utf8').trim()
  const response = await fetch(base + '/auth/login', { method: 'POST',
    headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username: 'admin', password }) })
  const login = await response.json()
  if (!response.ok || !login.success || !login.data?.token) throw new Error('Testkit admin login failed')
  const token = login.data.token
  const review = await api(`/mrp/batches/${batchId}/reviews`, token)
  if (!review.scheduleFingerprint) throw new Error('No schedule fingerprint')
  const confirmed = await api(`/mrp/batches/${batchId}/schedule-confirmation`, token, 'POST',
    { fingerprint: review.scheduleFingerprint })
  result.schedule = { before: review.schedule, after: confirmed.schedule }
  if (confirmed.schedule !== 'CONFIRMED') throw new Error('Schedule confirmation did not persist')
  sample()
  monitor = setInterval(sample, 2000)
  const started = Date.now()
  const task = await api(`/mrp/batches/${batchId}/material-analysis`, token, 'POST')
  result.taskId = task.id
  let taskState = ''
  while (Date.now() - started < 900000) {
    taskState = sql(`SELECT status,IFNULL(result_resource_id,0),IFNULL(error_code,'') FROM calc_task WHERE id=${result.taskId};`)
    if (taskState.startsWith('SUCCEEDED\t') || taskState.startsWith('FAILED\t')) break
    await new Promise(resolve => setTimeout(resolve, 2000))
  }
  result.calculation = { elapsedMs: Date.now() - started, taskState }
  clearInterval(monitor)
  monitor = undefined
  sample()
  result.batchState = sql(`SELECT status,active_run_id FROM mrp_calc_batch WHERE id=${batchId};`)
  result.requirements = sql(`SELECT COUNT(*),MAX(level_no),
    COALESCE(SUM(gross_qty<>inventory_used_qty+net_qty),0),
    COALESCE(SUM(inventory_before_qty<>inventory_used_qty+inventory_after_qty),0),
    COALESCE(SUM(inventory_used_qty),0) FROM mrp_requirement_detail WHERE batch_id=${batchId};`)
  result.facts = sql(`SELECT (SELECT COUNT(*) FROM mrp_terminal_demand WHERE batch_id=${batchId}),
    (SELECT COUNT(*) FROM mrp_daily_plan_detail WHERE batch_id=${batchId}),
    (SELECT COUNT(*) FROM mrp_inventory_pool WHERE batch_id=${batchId});`)
  if (!taskState.startsWith('SUCCEEDED\t') || !result.batchState.startsWith('MATERIAL_REVIEW\t'))
    throw new Error(`Material analysis did not finish: ${taskState}, ${result.batchState}`)
  if (result.requirements.split('\t').slice(2, 4).some(value => value !== '0'))
    throw new Error('Requirement conservation mismatch')
  result.status = 'PASS_LIMITED'
} catch (error) {
  result.status = 'FAIL'
  result.failure = String(error)
} finally {
  if (monitor) clearInterval(monitor)
  result.finishedAt = new Date().toISOString()
  mkdirSync(dirname(output), { recursive: true })
  writeFileSync(output, JSON.stringify(result, null, 2) + '\n')
}
console.log(JSON.stringify({ status: result.status, batchId, taskId: result.taskId,
  calculation: result.calculation, batchState: result.batchState,
  requirements: result.requirements, facts: result.facts, failure: result.failure }))
if (result.status === 'FAIL') process.exitCode = 1
